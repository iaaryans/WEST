<div align="center">
    <img src="./app/src/main/ic_launcher-playstore.png" width="128" height="128" style="display: block; margin: 0 auto; border-radius: 28px;" />
    <h1>West</h1>
    <p>A modernized, bug-fixed, and actively maintained Android music player for streaming from YouTube Music</p>
    <p><b>A revived and improved fork of <a href="https://github.com/vfsfitvnm/ViMusic">ViMusic</a></b></p>
</div>

---

<p align="center">
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="30%" />
  <img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="30%" />
</p>

## 📌 About West (ViMusic Fixed Edition)

**West** is an actively maintained continuation and enhanced fork of the original [ViMusic](https://github.com/vfsfitvnm/ViMusic) project by [vfsfitvnm](https://github.com/vfsfitvnm). 

Due to recent upstream YouTube Music API changes and modern Android security requirements, the original ViMusic experienced broken album/artist loading and launch crashes on newer Android versions. **West** addresses all of these issues while preserving the lightweight, minimalist, and ad-free experience.

### 🛠️ Key Fixes & Enhancements in West:
- ⚡ **Fixed Album & Artist Loading:** Rewrote InnerTube parser (`twoColumnBrowseResultsRenderer` and responsive item renderers) to resolve the issue where albums and artist music showed `"Unknown"` or failed to load.
- 🛡️ **Android 14+ Crash Fix:** Implemented `RECEIVER_NOT_EXPORTED` across all dynamic broadcast receivers (`PlayerService`, `InvincibleService`), preventing `SecurityException` crashes on Android 13/14+.
- 🎨 **Ultra HD High-Resolution Cover Art:** Upgraded image pipeline and thumbnail rewriting to render crisp, uncompressed 800x800 album art instead of blurry low-res thumbnails.
- 🚀 **Modern Toolchain:** Upgraded to **targetSdk 34**, Kotlin 1.9+, Gradle 8+, and JDK 17 with automated GitHub Actions CI.

---

## ✨ Features

- 🎵 **Unlimited Streaming**: Stream songs and videos directly from YouTube Music.
- ⚡ **Lightning Fast & Lightweight**: Zero telemetry, no ads, minimal battery consumption.
- 🎨 **Ultra HD Album Art**: Razor-sharp high-resolution cover artwork and fluid animations.
- 🌙 **Modern UI**: Light, Dark, and Dynamic Material You theming.
- 💾 **Smart Offline Caching**: Automatic caching of audio chunks for seamless offline playback.
- 🔍 **Full Exploration**: Search songs, albums, artists, videos, and playlists with instant suggestions.
- 🎤 **Synchronized Lyrics**: Fetch, view, and edit real-time synchronized and plain lyrics.
- 📻 **Custom Playlists & Queue**: Local playlists management, drag-and-drop queue reordering, and persistent state.
- 🚗 **Android Auto & Background Play**: Reliable background audio service with lockscreen controls.
- ⏱️ **Audio Tools**: Audio normalization (replay gain), sleep timer, and silence skipping.

---

## 🛠️ Building From Source

### Prerequisites
- **JDK 17** (Ensure `JAVA_HOME` points to Java 17)
- **Android SDK** (API 34, Build Tools 34.0.0)

### Clone & Build
```bash
git clone https://github.com/iaaryans/WEST.git
cd WEST

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```
The compiled APK will be located in `app/build/outputs/apk/`.

---

## 🤝 Contributing & Collaboration

We welcome open-source contributions, bug reports, and pull requests!
1. Fork the repository
2. Create your feature branch (`git checkout -b feature/awesome-feature`)
3. Commit your changes (`git commit -m 'Add awesome feature'`)
4. Push to the branch (`git push origin feature/awesome-feature`)
5. Open a Pull Request

---

## 🙏 Credits & Acknowledgments

- **Original ViMusic Author:** Deep gratitude to [vfsfitvnm](https://github.com/vfsfitvnm) and all the original contributors of [ViMusic](https://github.com/vfsfitvnm/ViMusic) for building such an incredible foundation.
- [**YouTube-Internal-Clients**](https://github.com/zerodytrash/YouTube-Internal-Clients): Discovery of internal YouTube API endpoints.
- [**ionicons**](https://github.com/ionic-team/ionicons): Beautiful vector icons by Ionic.
- [**Flaticon**](https://www.flaticon.com/authors/ilham-fitrotul-hayat): App icon inspiration.

---

## 📜 Disclaimer & Licensing

### Disclaimer
This project is an independent open-source music player developed for educational and research purposes. It is not affiliated with, authorized, maintained, sponsored, or endorsed by YouTube, Google LLC, or any of their affiliates. All trademarks, service marks, and trade names are the property of their respective owners.

### License
This project is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**, preserving the original license and terms of ViMusic. See the [LICENSE](LICENSE) file for the full license text.
