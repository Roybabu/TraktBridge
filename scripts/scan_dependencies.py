import json
import pathlib
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
def scan():
    coordinates = sorted(set((ROOT / "resolved-dependencies.txt").read_text().splitlines()))
    if not coordinates or len(coordinates) > 10000:
        raise ValueError("Invalid dependency inventory")
    queries = []
    for coordinate in coordinates:
        group, artifact, version = coordinate.split(":")
        queries.append({"package": {"ecosystem": "Maven", "name": group + ":" + artifact}, "version": version})
    findings = {}
    for offset in range(0, len(queries), 100):
        pending = list(enumerate(queries[offset:offset + 100], offset))
        for _ in range(20):
            if not pending:
                break
            request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                data=json.dumps({"queries": [q for _, q in pending]}).encode(),
                headers={"Content-Type": "application/json"})
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(request, timeout=45) as response:
                        data = json.loads(response.read(8_000_001))
                    break
                except (urllib.error.URLError, TimeoutError):
                    if attempt == 2:
                        raise
                    time.sleep(2 ** attempt)
            results = data["results"]
            if len(results) != len(pending):
                raise ValueError("Incomplete OSV response")
            next_pending = []
            for (idx, query), result in zip(pending, results):
                for vuln in result.get("vulns", []):
                    findings.setdefault(coordinates[idx], set()).add(vuln["id"])
                if result.get("next_page_token"):
                    next_pending.append((idx, dict(query, page_token=result["next_page_token"])))
            pending = next_pending
        if pending:
            raise ValueError("OSV pagination limit exceeded")
    report = {"packages_scanned": len(coordinates), "findings": {k: sorted(v) for k, v in findings.items()}}
    (ROOT / "vulnerability-report.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    if findings:
        raise SystemExit("Known vulnerabilities require review")
if __name__ == "__main__":
    scan()
