# Fabians App Store

Privacy-focussed opensource slopware made in the Netherlands

> ℹ️ The programs I publish are always free and open source. I generate them with AI for no other reason than that I need and use the app myself. I publish them so anyone who wants to can use them too. Because I don't know anything about programming, I probably won't accept merge requests, but feel free to fork the project and make something beautiful out of it.

## Get started
1. On your Android phone, open **https://fabian42069.codeberg.page/fabians-app-store/** and tap **Download Fabians App Store**.
2. Open the downloaded file and tap **Install**. If your phone asks, allow installing apps from this source.
3. Open **Fabians App Store** and tap an app to install it.

### Already use F-Droid?
Add the store as a repository in F-Droid (Settings → Repositories → +):

```
https://fabian42069.codeberg.page/fabians-app-store/repo?fingerprint=2853411A91130DE71960D81040B6A992682C9DB1D67D39DD093A6031910D7625
```

## Apps in the store
- **Fabians App Store**: the store itself, so it can update itself
- **Weegdagboek**: a simple daily weight diary
- **Fairtaler Translate**: a private translator with extra help for Japanese

<details>
<summary>For developers: how the store works</summary>

- `android/`: the store app (a small WebView app; the screen is `android/app/src/main/assets/index.html`).
- `catalog.json`: the list of apps with their names, descriptions, icons and APKs.
- `tools/build-repo.mjs`: builds `site/` with an F-Droid compatible repository (`repo/index-v1.json`, signed `repo/index-v1.jar`).
- `tools/publish.ps1`: uploads `site/` to Codeberg Pages.

All APKs must be signed with the same key as the store, otherwise installed apps can't be updated.

```
cd android && ./gradlew assembleDebug && cd ..
node tools/build-repo.mjs
powershell -File tools/publish.ps1
```
</details>

## License
GPL-3.0-or-later
