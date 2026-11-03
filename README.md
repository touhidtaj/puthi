# পুঁথি (Puthi) — Android

A tiny WebView shell around the Puthi PDF reader. No dependencies, no internet permission.

## Build the APK on GitHub (no Android Studio needed)
1. Create a new GitHub repo and upload everything in this folder (keep `.github/`).
2. **Actions → Build APK → Run workflow.**
3. When it finishes, download `puthi-apk` from the run's *Artifacts* → `puthi-v1.0.apk`.

## Signing (do this once, keep the key forever)
Easiest, no terminal: set the secrets `PUTHI_KS_PASS` (same value as `PUTHI_KEY_PASS`, 12+ characters) and `PUTHI_KEY_ALIAS` (e.g. `puthi`), run **Actions → Create signing key (run once)**, download the `puthi-signing-key` artifact right away, back up `puthi.jks` in two places, paste the text from `puthi-keystore-base64.txt` into a new secret `PUTHI_KEYSTORE_B64`, then delete the artifact and re-run *Build APK*.

Manual alternative:
Without secrets the APK is signed with a throwaway debug key — installable, but you can never update it
over a different build. For a real release:
```
keytool -genkeypair -v -keystore puthi.jks -alias puthi -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 puthi.jks      # paste into the secret below
```
Repo → Settings → Secrets → Actions: `PUTHI_KEYSTORE_B64`, `PUTHI_KS_PASS`, `PUTHI_KEY_ALIAS`, `PUTHI_KEY_PASS`.
**Back up puthi.jks. Lose it and users must uninstall to update.**

Push a tag like `v1.0` to also attach the APK to a GitHub Release (use this link on the website).

## Updating the web app
Run `python3 build_android.py` in the main project; it regenerates `app/src/main/assets/`. Bump `versionCode` in `app/build.gradle`.

## Permissions
- **All files access** (Android 11+): powers "Scan this phone". Sideloaded APKs are fine; Google Play needs a special declaration.
- Manual pick (files / folder) works without it.
- Android 10: scan is unavailable; use manual pick. Android 11+ blocks choosing the Downloads root as a folder — pick files, or use "Scan this phone".
- Removable SD cards are not scanned automatically.
