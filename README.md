# VIGI NVR TV

**An unofficial, open-source Android TV app for TP-Link VIGI NVRs.**

[![Build and test](https://github.com/muhaym/vigi-nvr-tv/actions/workflows/build.yml/badge.svg)](https://github.com/muhaym/vigi-nvr-tv/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Watch grouped cameras, browse recordings and use supported camera controls from a TV remote. Native Android Views and LibVLC; no browser wrapper, laptop or HDMI source required. Independent of TP-Link and VideoLAN. VIGI and TP-Link are their owners' trademarks.

**Beta software.** Tested with one VIGI recorder / TV combination. Broader compatibility is a development target, not a claim that every model or firmware has been tested. Standalone camera connections and universal ONVIF discovery are not implemented.

## What it does

- Organise cameras into your own groups, with fixed **1 / 4 / 8 / 16** layouts.
- Rotate individual cameras or pages every **5 / 15 / 30 / 60 seconds**, silently. Remote activity postpones cycling; opening a camera pauses it.
- Open full-resolution live video and recorded playback with a remote-controlled timeline. In live single-camera view, Left / Right switches through the selected group; Up / Down reaches the controls.
- Talk using a **USB microphone**: conversation mode or hold / tap push-to-talk, where supported by the NVR and camera.
- Digital zoom and movement around the picture; optical lens zoom where the recorder reports support.
- Start / stop a camera's supported siren and light through a confirmed manual alarm action.
- Protect viewing with a private remote pattern; save the recorder account with Android Keystore encryption.
- Discover camera names through the NVR, or manually configure channels **1–96**. Configure non-default HTTPS, RTSP and native-talk ports.

TP-Link ID sign-in, bound-device cloud discovery and cloud relay streaming are **not implemented**; cloud-bound recorders can still connect over LAN / VPN. See [cloud access status](docs/CLOUD-ACCESS.md).

One active recorder is supported. Every camera connection goes **through that recorder**, including talk and controls. Four individual streams use software decoding to avoid scarce TV hardware decoder slots; 8 / 16 layouts require a separately configured Channel-Zero combined preview. Its image is shared across tiles, so detail is lower.

## Get started

Use an Android TV / Google TV device running Android 8.0 (API 26) or newer, on the same reachable LAN or VPN as the recorder.

1. Build and install your own APK using the steps below. This initial public repository is a **source release**; a public APK release awaits the native-library distribution checklist.
2. Choose a private remote pattern. Setup then asks for the recorder address; an IP, hostname or HTTPS browser address is accepted.
3. Compare the recorder certificate fingerprint with a trusted administrator before choosing **Trust recorder**. Only then does the sign-in screen ask for the recorder username and password. Use the recorder account, not a TP-Link ID.
4. Choose **Connect** to discover cameras automatically. **Advanced options** on the address step contains an optional recorder name, manual camera-count fallback and port overrides. Back from sign-in clears the password and returns to the address step.
5. Create groups from **Workspace → Arrange camera groups**. Select a layout and optional cycle interval. Press OK on a camera for playback, sound, talk, zoom and alarms.
6. Selecting 8 or 16 opens **Overview settings** when combined preview has not been configured. Enable it on the recorder separately, then match its grid in **Arrange overview** by choosing camera names for each tile. Unused tiles are labelled **Empty**; changes remain a draft until Save. The selected layout opens after a valid mapping is saved. The app never changes recorder settings automatically.
7. After adding a camera to the NVR, choose **Workspace → Refresh camera list**. Existing groups are preserved. If the channel table changed, individual feeds remain available while **Overview settings · review needed** lets you check combined-preview positions before returning to 8 / 16 layouts.

The remote's voice-search microphone is generally unavailable to ordinary apps; use a working USB microphone for talk. Video currently uses unencrypted RTSP over TCP. Keep the recorder on a trusted LAN or VPN. See [security and privacy](SECURITY.md).

## Build and verify

Install JDK 17+ and Android SDK platform 36 / build tools 35.0.0. Set `ANDROID_HOME` or an untracked `local.properties` containing `sdk.dir=/your/android/sdk`.

```sh
./gradlew :core:check :core:jacocoTestReport :app:assembleDebug :app:lintDebug
python3 tools/check_source.py
```

Use `gradlew.bat` on Windows. Protocol / domain tests can run without an Android SDK using `./gradlew :core:test :core:jacocoTestReport --configure-on-demand`. Coverage HTML is in `core/build/reports/jacoco/test/html/index.html`. CI runs tests, coverage, the Android build, lint and a source-privacy gate.

Debug builds use local Android debug signing. Release builds are unsigned (`./gradlew :app:assembleRelease`); supply your own signing process. Never commit a signing key. APKs include ARMv7, ARM64 and x86_64 LibVLC libraries. The app ID remains `com.fantacode.vigitv` for existing installation compatibility.

Dependencies have fixed versions and committed SHA-256 verification metadata; the Gradle wrapper distribution and wrapper JAR are verified. This is not a bit-for-bit reproducibility claim.

## Compatibility and project documentation

- [Compatibility matrix and limits](docs/COMPATIBILITY.md)
- [Architecture and protocol boundaries](docs/ARCHITECTURE.md)
- [Testing and coverage](docs/TESTING.md)
- [Release checklist](docs/RELEASE.md)
- [Changes](CHANGELOG.md), [contributing](CONTRIBUTING.md), [security reporting](SECURITY.md)

Recordings depend on NVR storage and account permissions. Camera support for audio, optical zoom and alarm outputs varies. Multiple saved recorders, clip export, recording-availability search, IPv6 and localisation are future work.

## License

Project-authored code is [MIT licensed](LICENSE). Dependencies retain their own licences; see [third-party notices](THIRD-PARTY-NOTICES.md). LibVLC's LGPL obligations require corresponding native sources / build information and applicable relinking materials before public binary redistribution. This repository does not include proprietary vendor clients, private recorder profiles, certificates, credentials or camera footage.
