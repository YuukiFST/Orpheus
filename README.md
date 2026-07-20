<p align="center">
  <img src="assets/orpheus_header.png" alt="Orpheus — open-source music player for Android"/>
</p>

<p align="center">
  <a href="https://github.com/YuukiFST/Orpheus/releases/latest">
    <img src="https://img.shields.io/github/v/release/YuukiFST/Orpheus?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest release">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3 license">
</p>

<p align="center">
  <img src="assets/screenshot1.jpeg" alt="Orpheus home screen" width="205"/>
  <img src="assets/screenshot2.jpeg" alt="Orpheus now playing screen" width="205"/>
  <img src="assets/screenshot3.jpeg" alt="Orpheus library screen" width="205"/>
  <img src="assets/screenshot4.jpeg" alt="Orpheus lyrics screen" width="205"/>
</p>

## What is Orpheus?

Orpheus is a GPLv3 Android music player based on [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS).

The difference: you can **listen to music from YouTube without ads** — search, play, download, and add tracks to playlists alongside your local library.

Package: `com.yuukifst.orpheus`

## Download

**GitHub Releases** (recommended for testing):

https://github.com/YuukiFST/Orpheus/releases

Pick the APK for your phone:

- Most phones: `Orpheus-<version>-arm64-v8a.apk`
- Older 32-bit devices: `Orpheus-<version>-armeabi-v7a.apk`

**Obtainium** (auto-updates from GitHub):

1. Install [Obtainium](https://github.com/ImranR98/Obtainium)
2. Add `https://github.com/YuukiFST/Orpheus`

Release APKs use the naming pattern `Orpheus-<version>-<abi>.apk`.

## Legal Disclaimer

Orpheus is provided for educational and personal-use purposes only. Orpheus is not affiliated with, endorsed by, or sponsored by YouTube or Google. You are solely responsible for how you use the application and for compliance with applicable terms of service and laws in your jurisdiction.

## Build (Nix)

```bash
export NIXPKGS_ALLOW_UNFREE=1
export NIXPKGS_ACCEPT_ANDROID_SDK_LICENSE=1
nix develop --impure
# seed SDK once (see flake shellHook), then:
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
nix build .#default --impure
./result/bin/orpheus-android-fhs ./gradlew assembleDebug -Porcheus.disableReleaseSigning=true
```

## License

Orpheus is licensed under the [GNU General Public License v3.0](LICENSE). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party components.
