# Release setup

Create the permanent key once on a trusted machine with Java 17 (or reuse an existing TraktBridge release key). Do not use a debug keystore.

```sh
keytool -genkeypair -v -keystore traktbridge-release.jks -storetype JKS -alias traktbridge -keyalg RSA -keysize 3072 -validity 10000
keytool -list -v -keystore traktbridge-release.jks -alias traktbridge
base64 -w 0 traktbridge-release.jks > traktbridge-release.b64
```

On macOS use `base64 -i traktbridge-release.jks -o traktbridge-release.b64` instead. Store the key and passwords in a secure offline backup. Do not upload the key or passwords to the repository or share them in chat.

In Settings > Secrets and variables > Actions, create repository secrets:

- TRAKT_KEYSTORE_BASE64: base64-encoded keystore
- TRAKT_STORE_PASSWORD: keystore password
- TRAKT_KEY_ALIAS: traktbridge (or your chosen alias)
- TRAKT_KEY_PASSWORD: private-key password

Under Variables, create TRAKT_SIGNING_SHA256 with the SHA256 certificate fingerprint printed by keytool (colons are accepted). This is public certificate metadata, not a secret.

Update version.properties for every release; increase VERSION_CODE monotonically and set VERSION_NAME. After CI passes on main:

```sh
git tag v0.8.0
git push origin v0.8.0
```

Alternatively create the matching tag using GitHub's Releases UI and run the signed workflow against it. Do not create a published release manually first: the workflow creates it. A failed or interrupted release may require inspecting/removing a partially created release before rerunning. A release must never replace a previously published version with different bytes.

Download the signed APK under Releases and compare its SHA256 checksum. Android updates require the same package, signing certificate and a nondecreasing version code. A prior debug install with a different certificate must be uninstalled before the first signed install; this deletes local data. Subsequent signed releases using the same key preserve app data. Never lose the key.

Manual smoke tests: sign in, select a video app, grant notification access, play a video, review the detected session, match it, approve a single test upload, verify the exact timestamp in Trakt, then check a retry does not add a second entry. Device-specific playback and live OAuth/API behavior are not covered by the JVM tests.

## Stable debug APKs

Debug signing uses a dedicated permanent key, separate from release signing. Configure repository secrets `TRAKT_DEBUG_KEYSTORE_BASE64` (base64 JKS) and `TRAKT_DEBUG_PASSWORD` (store and key password). The JKS alias is `traktbridge-debug`. CI pins the public SHA-256 certificate fingerprint `6a973921ec132f1a6ebfee39e7f8567a4eaa0f1d3a51fda9a246dc86c8c518af` in `.github/workflows/android.yml` and verifies the signed APK before publication. Main-branch builds fail rather than distributing incompatible APKs if credentials are missing or the certificate differs. Pull requests validate without signing secrets. Existing installations signed with a different certificate require a one-time migration; uninstalling erases local data. Keep the signing key and password private.
