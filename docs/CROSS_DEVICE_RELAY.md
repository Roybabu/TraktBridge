# Optional cross-network relay

Local Wi-Fi remains the default. No public relay is bundled or operated by TraktBridge.
Deploy the included Python 3 relay on a trusted host with a reachable TCP port.

1. Generate a random deployment secret: `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'`
2. Set the secret in the server environment as TRAKTBRIDGE_RELAY_SECRET. Do not commit it.
3. Run `python3 relay/server.py` (default port 8765). Use a firewall and service supervisor.
4. Pair your devices normally. Enter the same host, port and secret on each device in Settings → Configure optional cloud relay.
5. Enable cross-device sharing and start tracking on each device.

The relay authenticates short-lived rendezvous requests using HMAC, pairs opposite roles, and forwards opaque mutually authenticated device TLS traffic. It can observe IP addresses, timing, opaque routing identifiers and TLS handshake metadata. It does not decrypt playback, store history, proxy video or use Trakt credentials. No remote commands over the relay in this version. Up to eight paired peers per device use relay synchronization. Keep server access restricted; rotate the deployment secret if exposed. Clock synchronization is required.

This implementation needs real-world connectivity, TLS interoperability and abuse-load testing before stable release.
