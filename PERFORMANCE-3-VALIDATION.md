# WEST Smooth Experience 3.0 — Performance verification

This is an additive change on top of WEST's previous UI optimizations and Moodspace crash fix. It does **not** claim measured FPS before an Android device run.

## Source changes

- `MoodspaceScreen`: artwork prefetching moved from each card's `LaunchedEffect` to a single viewport-aware controller, disabled during fling; requests are cancelled on rapid scrolling and bounded by two concurrent fetches in the existing `MusicBrowseCache`.
- `MoodArtworkPrefetchPolicy`: pure, tested row/window selection and hard request budget.
- `MusicBrowseCache`: warm-start category/artwork getters avoid reload flicker on tab return, and 64 fixed mutex stripes replace an unbounded clearing registry that could allow duplicate network lookups.
- `QuickPicks`: playback callbacks moved out of the whole Home feed into the visible Recents and Now Playing components; prepared Recents queue is memoized and rebuilt only when the list changes.
- `BrowseChrome`: `rememberSaveableStateHolder` restores per-tab saved state without keeping multiple heavy tabs composed concurrently.
- `Player`: 500ms player progress state is read by the draw modifier and expanded controls instead of unconditionally in the parent composable.
- `MusicBars`: infinite equalizer motion stops on reduced-motion / low-RAM devices; the equalizer remains visible as static bars.

## Test commands (Android SDK + Gradle distribution required)

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
adb logcat -s WEST_Performance:I WEST_Moodspace:W
```

To compare WEST vs BitChord on the **same** refresh-rate device, in the same power/thermal state, and using identical input actions:

```sh
adb shell dumpsys gfxinfo it.vfsfitvnm.vimusic reset
# Browse Home, switch to Moodspace, fling cards and play/seek.
adb shell dumpsys gfxinfo it.vfsfitvnm.vimusic framestats
```

The instrumented `FrameTimingScrollSmokeTest` logs real `p50`, `p95` frame duration and frames exceeding 16.67 ms on API 24+ devices; results are telemetry, not guaranteed frame-rate claims. Search crash regression, Moodspace Hanukkah case, Home/Moodspace navigation, Smart Queue, onboarding and library should pass before releasing an APK.

---

# WEST Home Smooth Experience 4.0 — implementation and validation

Only Home UI rendering, Home related-feed caching, artwork policy and their tests
were changed. This is a *source-level* performance optimization, not a claim
that a target FPS was observed on a real device.

## Changes

1. `HomeSongRow.kt` renders Home song rows with exactly the same existing
   `SongItem` layout and clipping, but with small stable Coil requests using
   `SmoothArtwork`. The global player and non-Home `SongItem` behavior is
   unchanged. The old `Utils.thumbnail` forced a minimum 800-pixel Google
   image even for a row's tiny artwork.
2. `HomeArtworkPolicy.kt` limits Home image dimensions to 96–768 pixels and
   canonicalizes Google Music thumbnail URLs for better cache reuse.
   `SmoothArtwork` uses these sizes for all its already-bounded uses.
3. `HomeRelatedFeedCache.kt` caches the two-stage YouTube Music Related feed
   for ten minutes (short negative cache on failures), coalesces simultaneous
   fetches, and lets warm feeds appear immediately on Home revisits.
4. `QuickPicks.kt` cancels superseded feed loads, ignores duplicate Room
   Trending rows by song ID, precomputes stable album/artist/playlist shelf
   lists, preserves all user actions and Smart Queue playback, and replaces
   the continuously animating Home loading shimmer with static placeholders.
5. `TasteDiscoverySection.kt`, `HomeFreshDiscoveries.kt`, and
   `IntelligenceSection.kt` reuse Home-specific small thumbnails while keeping
   personalized discovery and play-next behavior.

## Check locally

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

The existing `FrameTimingScrollSmokeTest` records Home/Moodspace frame times.
Compare the previous and new builds on the *same physical device*, release
mode where possible, matching network state and cache warmth. Measure p50,
p95, janky-frame counts and memory during rapid Home scrolling and repeated
Home/Moodspace switches. The Android SDK and Gradle 7.4 distribution are not
available in the current working environment; the above commands have **not**
passed here. Do not publish an APK as verified until CI and device tests pass.

## Preserved

Moodspace and Hanukkah guard logic, Search and Search crash fixes, Media3
Smart Queue and queue settings, personalization, developer credits, Git
history, and the original `README.md` bytes.
