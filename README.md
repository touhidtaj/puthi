<div align="center">

# পুঁথি · Puthi

**A quiet place to read.** A distraction-free offline PDF reader for Android.

</div>

Puthi finds the PDFs on your phone, shows them as a bookshelf, and opens them full-screen. It supports highlights, notes, page remarks, English / বাংলা / العربية, and exporting notes to Word or PDF. It requests no internet permission.

## How this repository works

The app is a minimal Android WebView shell (plain Java, no libraries) that loads a single-page reader from its assets. The APK is built automatically by GitHub Actions. There is nothing to install on a computer.

| File | Purpose |
|---|---|
| `app/src/main/assets/index.html` | The reader (all of the interface) |
| `app/src/main/java/com/puthi/reader/MainActivity.java` | Android shell: file access, scanning, immersive mode |
| `.github/workflows/build-apk.yml` | Builds the APK (Actions → Build APK → Run workflow) |
| `.github/workflows/make-key.yml` | One-time creation of the signing key |

## Building

1. Open the **Actions** tab, choose **Build APK**, press **Run workflow**.
2. After about two minutes, download `puthi-apk` from the run's Artifacts.

Releases are signed with a permanent key stored in the repository's secrets (`PUTHI_KEYSTORE_B64`, `PUTHI_KS_PASS`, `PUTHI_KEY_PASS`, `PUTHI_KEY_ALIAS`). Keep a private backup of the key file. Without it, installed copies can never be updated.

## Credits

Designed and built by **Touhidul Islam** · [facebook.com/TouhidDesigner](https://www.facebook.com/TouhidDesigner/)

Uses [PDF.js](https://mozilla.github.io/pdf.js/) (Apache-2.0) and the Inter and Noto fonts (SIL Open Font License).

© 2026 Touhidul Islam. All rights reserved.
