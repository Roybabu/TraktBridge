import pathlib
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
props = dict(line.split("=", 1) for line in (root / "version.properties").read_text().splitlines() if "=" in line)
assert props["VERSION_NAME"] == "0.99.0"
assert int(props["VERSION_CODE"]) == 111
a = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
app = manifest.find("application")
assert app.get(a + "allowBackup") == "false"
assert app.get(a + "usesCleartextTraffic") == "false"
allowed = {".MainActivity": None, ".PlaybackListener": "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", ".TitleAccessibility": "android.permission.BIND_ACCESSIBILITY_SERVICE", ".TrackingTile": "android.permission.BIND_QUICK_SETTINGS_TILE"}
for component in app:
    if component.tag not in ("activity", "service", "receiver", "provider", "activity-alias"):
        continue
    name = component.get(a + "name")
    if component.get(a + "exported") == "true":
        assert name in allowed, f"Unexpected exported component: {name}"
        if allowed[name]:
            assert component.get(a + "permission") == allowed[name], name
    else:
        assert component.get(a + "exported") == "false", name
assert set(c.get(a+"name") for c in app if c.get(a+"exported")=="true") == set(allowed)
permissions = {p.get(a + "name") for p in manifest.findall("uses-permission")}
assert not permissions.intersection({"android.permission.READ_LOGS", "android.permission.QUERY_ALL_PACKAGES", "android.permission.MANAGE_EXTERNAL_STORAGE"})
print("Release candidate manifest, permissions and version checks passed.")
