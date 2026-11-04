 <div align="center">
  <img src="./app/src/main/logo.png" width="128" height="128" alt="WEST logo" />
  <h1>WEST</h1>
  <p><b>Straight Outta The Cloud.</b></p>
  <p>An independently branded Android music player for YouTube Music, reworked from <a href="https://github.com/vfsfitvnm/ViMusic">ViMusic</a>.</p>
  <p>
    <img alt="Status: beta" src="https://img.shields.io/badge/status-beta-4f6fff" />
    <img alt="License: GPL-3.0" src="https://img.shields.io/badge/license-GPL--3.0-blue" />
    <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android%205.0%2B-3ddc84" />
    <img alt="Language: Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white" />
  </p>
</div>

---

## About

WEST is a modified version of [ViMusic](https://github.com/vfsfitvnm/ViMusic), originally developed by [vfsfitvnm](https://github.com/vfsfitvnm) and its contributors.

The project started with a practical goal: address parts of the original application that had become unreliable, particularly music playback and the loading of album and artist pages.

WEST builds on that foundation with a reworked YouTube Music playback-resolution flow, updated InnerTube parsers and models, Android compatibility changes, and its own application branding.

It is developed as an independently branded Android application using Kotlin and Jetpack Compose.

**WEST is beta software.** It is under active development and has not been tested on every device or Android version. Playback availability depends on external services and may vary.

> WEST is a derivative work of ViMusic. Original ViMusic code remains the work of its respective authors and contributors. WEST and its modifications are distributed under GPL-3.0. See [License and Attribution](#license-and-attribution).

## What WEST Changes

Compared with the ViMusic v0.5.4 codebase used as its foundation, WEST introduces changes across playback, browsing, Android integration, and application identity.

| Area | Changes |
|---|---|
| Playback resolution | Reworked InnerTube player request flow. Uses a VisionOS client first, with Android VR fallback and visitor-data refresh handling. |
| Browsing | Updated InnerTube parsers and response models for albums, artists, playlists, lyrics, and related music. |
| Android compatibility | Updated dynamic broadcast receiver registration, media playback foreground-service permission, and Android SDK configuration. |
| Application identity | WEST branding, launcher icons, logo, About screen, and release configuration. |
| Analytics | Aptabase-based application telemetry. See [Privacy](#privacy) for details. |
| CI | GitHub Actions workflow for building and uploading the debug APK. |

These changes are based on a comparison of the WEST repository with the upstream ViMusic v0.5.4 codebase. WEST retains substantial inherited functionality and components, alongside modifications to selected files and modules.

## Features

The following features are present in the current source code. Many originate from ViMusic and are not necessarily individually re-tested in every WEST release.

### Music Discovery
- Search YouTube Music for songs, albums, artists, videos, and playlists.
- Browse album and artist pages.
- Explore playlists and related music.
- Open supported YouTube and YouTube Music links.

### Playback
- Background playback.
- Playback notifications and media controls.
- Persistent playback queue.
- Queue and playlist reordering.
- Audio caching for repeat playback.
- Sleep timer.
- Skip silence.
- Loudness normalization.

### Library and Personalization
- Local playlists.
- Bookmarked artists and albums.
- Lyrics, including synchronized lyrics.
- Lyrics editing.
- Light and dark themes.
- Dynamic theme support.

### Android Integration
- Android MediaSession integration.
- Media browser service.
- Android Auto media-browser support.
- Background media playback through Android services.

## Download

<p align="center">
  <a href="https://github.com/iaaryans/WEST/releases">
    <img src="https://img.shields.io/badge/Download-Latest%20Release-4f6fff?style=for-the-badge&logo=android" alt="Download WEST" />
  </a>
</p>

Download WEST from the official GitHub Releases page:

**[GitHub Releases — WEST](https://github.com/iaaryans/WEST/releases)**

### Release Information

| Detail | Value |
|---|---|
| APK | `West-Release.apk` |
| Reported file size | 2.6 MB |
| SHA-256 | `948068e841ec5decb87ab8fb3140ea7810529201351e6ace90102391dae4d532` |
| Source tag | [`v1.0.0`](https://github.com/iaaryans/WEST/tree/v1.0.0) |

Verify the downloaded APK using:

```bash
sha256sum West-Release.apk
```

On Windows PowerShell:

```powershell
Get-FileHash .\West-Release.apk -Algorithm SHA256
```

Compare the output with the SHA-256 value above before installing.

**Installation note:** If you install the APK directly, Android may ask you to allow installation from your browser or file manager.

## Privacy

WEST does not currently include an account system or advertisements.

### Local Data
The application uses local persistence for data such as:
- Playlists and bookmarks.
- Playback queue.
- Cached audio.
- Locally stored music-related information.

### External Services

Music discovery, metadata, and playback requests use YouTube Music and related external services.

### Analytics

The current implementation integrates [Aptabase](https://aptabase.com) for application telemetry.

Events currently include:
- `app_started`
- `song_played`

The telemetry implementation can include:
- Device model.
- Android version.
- Application version and build information.
- Locale.
- Debug status.
- Randomly generated session identifier.

Analytics initialization is implemented in `MainApplication.kt`, and the analytics implementation is available at:

[`WestAnalytics.kt`](./app/src/main/kotlin/it/vfsfitvnm/vimusic/utils/WestAnalytics.kt)

**Important:** The current inspected implementation does not expose a user-facing analytics opt-out setting. The analytics key is configured in application source code; removing or disabling analytics initialization requires a source-code change and a new build.

The project should not be described as telemetry-free or fully anonymous while these events are enabled.

## Build From Source

Want to explore the code or build WEST yourself? Follow these steps.

### Requirements

- Android Studio.
- JDK 17.
- Android SDK.
- Android SDK Build Tools 34.0.0.
- Git.

### Clone the Repository

```bash
git clone https://github.com/iaaryans/WEST.git

cd WEST
```

### Build Debug APK

**Windows:**

```powershell
gradlew.bat assembleDebug
```

**Linux / macOS:**

```bash
./gradlew assembleDebug
```

### Build Release APK

```bash
./gradlew assembleRelease
```

The generated APKs are located under:

```text
app/build/outputs/apk/
```

### Release Signing

Release signing is configured in `app/build.gradle.kts`.

The build configuration supports the following environment variables:

```text
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

Before producing a release build:

1. Configure your own release keystore.
2. Set the signing credentials through environment variables or a secure CI secret store.
3. Remove insecure hardcoded fallback credentials from the build configuration.
4. Never commit your keystore or passwords to Git.

**Security note:** The current build configuration contains fallback signing credentials. Review and replace those defaults before using it for a production release.

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Playback | AndroidX Media3 / ExoPlayer |
| Database | Room |
| Networking | Ktor and custom HTTP networking |
| Asynchronous Processing | Kotlin Coroutines |
| Image Loading | Coil |
| Music Integration | YouTube Music InnerTube |
| Lyrics Provider | Kugou |
| Build System | Gradle |
| CI | GitHub Actions |

## Project Structure

```text
WEST/
│
├── app/
│   ├── Android application
│   ├── Jetpack Compose UI
│   ├── PlayerService
│   ├── MediaSession integration
│   ├── Room database
│   └── Application logic
│
├── innertube/
│   ├── YouTube Music InnerTube client
│   ├── Player requests
│   ├── Browse and search requests
│   ├── Response parsers
│   └── Music metadata models
│
├── kugou/
│   └── Lyrics provider integration
│
├── compose-persist/
│   └── Compose persistence utilities
│
├── compose-reordering/
│   └── Drag-and-drop reordering
│
├── compose-routing/
│   └── Navigation utilities
│
└── ktor-client-brotli/
    └── Brotli support for Ktor
```

## Playback Architecture

WEST uses a custom playback-resolution flow to retrieve playable audio streams.

```text
       User selects a song
                |
                v
          PlayerService
                |
                v
        Media3 ExoPlayer
                |
                v
       ResolvingDataSource
                |
                v
         InnerTube Player
                |
                v
        VisionOS Client
                |
         Request fails?
                |
                v
       Android VR Fallback
                |
                v
       Audio Format Selection
                |
                v
         ExoPlayer Playback
```

The resolver requests playback information through YouTube Music's InnerTube interface, processes the response, and supplies the selected audio stream to ExoPlayer.

The implementation uses alternative client configurations to improve playback compatibility.

**Technical limitation:** YouTube Music's InnerTube interface is undocumented and subject to change. Changes on the service side can affect playback, metadata retrieval, or browsing functionality.

## Android Application Identity

The current Kotlin package and Android application ID remain:

```text
it.vfsfitvnm.vimusic
```

This identifier is inherited from ViMusic.

As a result, WEST may conflict with an existing ViMusic installation, particularly when the installed applications are signed with different keys.

A WEST-specific application ID migration is a potential future improvement.

## Continuous Integration

WEST includes a GitHub Actions workflow that builds the Android debug APK.

The current workflow:
1. Checks out the repository.
2. Sets up JDK 17.
3. Executes the Gradle debug build.
4. Uploads the generated APK as a workflow artifact.

The existing workflow validates debug compilation. It does not currently provide comprehensive automated testing, lint validation, or release-build verification.

## Roadmap

Potential development priorities:

- [ ] Introduce a WEST-specific Android application ID.
- [ ] Upgrade Android SDK and dependency versions.
- [ ] Improve playback resolver reliability and error diagnostics.
- [ ] Expand automated unit and integration tests.
- [ ] Add lint and static-analysis checks to CI.
- [ ] Improve service and coroutine lifecycle management.
- [ ] Introduce user-facing analytics controls.
- [ ] Improve release signing and build security.
- [ ] Expand developer documentation.

## Contributing

Contributions, bug reports, and suggestions are welcome.

### How to Contribute

1. Fork the repository.
2. Create a feature or fix branch:

   ```bash
   git checkout -b fix/short-description
   ```

3. Make your changes and keep them focused.
4. Build and test your changes.
5. Mention the device and Android version used during testing.
6. Submit a pull request with a clear description.

By contributing, you agree that your contributions will be distributed under the project's GPL-3.0 license.

## License and Attribution

WEST is distributed under the **GNU General Public License v3.0 (GPL-3.0)**.

See the [`LICENSE`](./LICENSE) file for the complete license text.

### Original Project

WEST is a derivative work based on [ViMusic](https://github.com/vfsfitvnm/ViMusic), originally developed by vfsfitvnm and its contributors.

The original authors retain their respective copyrights in the original code. WEST's modifications and development are maintained by Aaryan, with project changes documented in the [Git commit history](https://github.com/iaaryans/WEST/commits/main).

WEST does not claim ownership of the original ViMusic code or third-party components.

### Disclaimer

WEST is an independent community-developed project.

It is not affiliated with, funded, authorized, or endorsed by YouTube, YouTube Music, Google LLC, Kugou, or the original ViMusic maintainers.

All third-party names and trademarks belong to their respective owners.

WEST does not host or distribute music content. Music discovery and playback depend on external services and their availability.

Users are responsible for ensuring that their use of the application complies with applicable laws and the terms of the services they access.

---

<div align="center">
  <h3>WEST</h3>
  <p><b>Straight Outta The Cloud.</b></p>
  <p>Built with Kotlin. Driven by curiosity.</p>
  <a href="https://github.com/iaaryans/WEST">GitHub Repository</a>
</div>
