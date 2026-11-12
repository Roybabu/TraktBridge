#!/usr/bin/env python3
"""Bounded authenticated TCP rendezvous. Forwards only opaque device TLS bytes."""
import asyncio
import base64
import hashlib
import hmac
import os
import re
import time

SECRET = os.environ.get("TRAKTBRIDGE_RELAY_SECRET", "")
if not re.fullmatch(r"[A-Za-z0-9_-]{43}", SECRET):
    raise SystemExit("Set TRAKTBRIDGE_RELAY_SECRET to a random 32-byte URL-safe base64 secret")
KEY = base64.urlsafe_b64decode(SECRET + "=")
PENDING = {}
NONCES = {}
LIMIT = asyncio.Semaphore(128)

def valid(line):
    try:
        version, route, role, stamp, nonce, tag = line.decode("ascii").strip().split(" ")
        now = int(time.time())
        if version != "TB1" or not re.fullmatch(r"[0-9a-f]{64}", route) or role not in ("0", "1"):
            return None
        if abs(now - int(stamp)) > 60 or not re.fullmatch(r"[A-Za-z0-9_-]{22}", nonce):
            return None
        if not re.fullmatch(r"[A-Za-z0-9_-]{43}", tag):
            return None
        body = " ".join((version, route, role, stamp, nonce)).encode("ascii")
        expected = base64.urlsafe_b64encode(hmac.digest(KEY, body, "sha256")).rstrip(b"=").decode()
        if not hmac.compare_digest(expected, tag):
            return None
        for old, expiry in list(NONCES.items()):
            if expiry < now:
                NONCES.pop(old, None)
        if nonce in NONCES or len(NONCES) >= 8192:
            return None
        NONCES[nonce] = now + 120
        return route, int(role)
    except (ValueError, UnicodeError):
        return None

async def pipe(reader, writer):
    while True:
        data = await reader.read(16384)
        if not data:
            return
        writer.write(data)
        await writer.drain()

async def handle(reader, writer):
    if LIMIT.locked():
        writer.close()
        return
    async with LIMIT:
        key = None
        waiter = None
        try:
            line = await asyncio.wait_for(reader.readline(), 4)
            if len(line) > 240:
                return
            pair = valid(line)
            if pair is None:
                return
            route, role = pair
            key = (route, role)
            opposite = (route, 1-role)
            other = PENDING.pop(opposite, None)
            if other is not None:
                other_reader, other_writer, other_waiter = other
                try:
                    writer.write(b"\x01")
                    other_writer.write(b"\x01")
                    await writer.drain()
                    await other_writer.drain()
                    async def exchange():
                        tasks = [asyncio.create_task(pipe(reader, other_writer)), asyncio.create_task(pipe(other_reader, writer))]
                        try:
                            await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
                        finally:
                            for task in tasks:
                                task.cancel()
                            await asyncio.gather(*tasks, return_exceptions=True)
                    await asyncio.wait_for(exchange(), 15)
                finally:
                    if not other_waiter.done():
                        other_waiter.set_result(None)
                    other_writer.close()
            elif key not in PENDING and len(PENDING) < 64:
                waiter = asyncio.get_running_loop().create_future()
                PENDING[key] = (reader, writer, waiter)
                await asyncio.wait_for(asyncio.shield(waiter), 30)
        except (asyncio.TimeoutError, ConnectionError, ValueError, asyncio.IncompleteReadError):
            pass
        finally:
            if waiter is not None and PENDING.get(key, (None,None,None))[2] is waiter:
                PENDING.pop(key, None)
            writer.close()
            try:
                await writer.wait_closed()
            except (ConnectionError, OSError):
                pass

async def main():
    server = await asyncio.start_server(handle, os.getenv("RELAY_BIND", "0.0.0.0"), int(os.getenv("RELAY_PORT", "8765")), limit=512)
    async with server:
        await server.serve_forever()

if __name__ == "__main__":
    asyncio.run(main())
