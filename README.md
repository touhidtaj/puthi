<div align="center">

# পুঁথি · Puthi

**A quiet place to read.** A distraction-free offline PDF reader for Android.

</div>

Puthi finds the PDFs on your phone, shows them as a bookshelf, and opens them full-screen. It supports highlights, notes, page remarks, English / বাংলা / العربية, and exporting notes to Word or PDF. It requests no internet permission.

## Your privacy, and how to check it yourself

পুঁথি is built so that your books **cannot** leave your phone, not merely so that it promises they won't. Every claim below can be checked.

| Claim | Why it is true | How you can check |
|---|---|---|
| **It cannot use the internet.** | The app does not ask for the INTERNET permission, so Android itself blocks any network connection. The reader also switches off web loading inside the app. | Open the file `app/src/main/AndroidManifest.xml` in the app repository: the list of permissions has no INTERNET. A free permission-viewer app, such as the open-source App Manager, shows the same for the installed APK. (Android's own Permissions screen does not list Internet for any app, so the absence there proves nothing.) |
| **No tracking, accounts or adverts.** | There is no analytics code, no login and no ad code. The only web address in the app is the link to the creator's Facebook page. It opens in your browser, and only when you tap it. | Read `app/src/main/assets/index.html` in this repository. |
| **Your files stay where they are.** | The app reads your PDFs in place. Your notes and highlights are stored in the app's private storage on your phone. | Clear the app's data or uninstall, and they are gone. They exist nowhere else. |
| **Nothing is sent in the background.** | With no internet permission, there is nothing to send with. | Settings → Network & internet → Data usage (the name varies by phone) should show 0 for পুঁথি. |
| **The app cannot be backed up to a cloud.** | Android backup is switched off for this app. | Open the app's manifest file in this repository: `allowBackup="false"`. |

### The one permission worth understanding

পুঁথি asks for **"All files access"**. It is needed for one reason: to find the PDFs scattered across your phone's folders. Android offers no narrower way to scan for them. The app uses it only to list and read PDF files, and it can't send anything anywhere. If you prefer, you can refuse it and open PDFs one at a time with "Open file" instead.

### Where the APK comes from

The APK is built automatically by GitHub from the public source code, using a published recipe (`.github/workflows/build-apk.yml`). No one assembles it by hand. To confirm your download is the genuine one, compare its SHA-256 checksum with the one listed here for the current version. A file manager or a "hash checker" app on your phone can show it.

```
Current version: SHA-256 will be listed here after each release
```

### What this does not promise

পুঁথি is a personal project, not an audited product, and it is not on the Play Store. It is installed from outside the store, which is why Android shows a warning for any such app. Only install it from the links on this page and the creator's own pages. If you ever see পুঁথি offered elsewhere, treat it as a copy and don't trust it.

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
