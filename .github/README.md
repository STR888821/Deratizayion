# Deratization — Android music player

Version: **0.3.0 source milestone**

A local-first Android music player built with Kotlin, Jetpack Compose, Media3 ExoPlayer and Room. Visual direction: dark UI, neon pink/violet gradients, animated procedural fallback artwork.

## Features included in this source project

- Scans local music indexed by Android MediaStore.
- Android 13+ `READ_MEDIA_AUDIO` permission and legacy read permission on Android 8–12.
- Search by title, artist and album.
- Sorting by title, artist, duration and date added.
- Playback queue, play/pause, next/previous, shuffled “Моя волна”.
- “Моя волна” can use the full library or a selected playlist.
- Playlists stored locally in Room: create, delete, add tracks, remove tracks and play a playlist queue.
- Listening history stored in Room, latest 200 entries, clear-history action.
- Reads embedded audio artwork when available; animated gradient pattern is used when no artwork exists.
- System audio equalizer effect with band sliders where the Android device exposes the effect.
- Import of **direct audio file URLs** (HTTP/HTTPS, MP3/M4A/AAC/OGG/WAV/FLAC/OPUS, max 150 MB) into the app-private music folder. Imported tracks are discovered on subsequent library refreshes.

## Important limits

- A URL importer is not a universal downloader for every website. A YouTube/VK/SoundCloud page URL is not a direct audio file and is intentionally rejected. Supporting those services requires their official APIs or another legally authorized download mechanism; this project does not bypass DRM, access controls, or service terms.
- Some devices do not expose an Android system equalizer or may limit its available bands.
- Playback runs through Media3 ExoPlayer, but a foreground media service, media notification, Bluetooth/headset controls, and Android Auto integration are future hardening tasks.
- The project has **not been built or run in this environment**, because an Android SDK/Gradle installation is unavailable here. Treat this as a complete source package that must be synced and validated in Android Studio, not as a verified APK.

## Build in Android Studio

1. Install Android Studio and Android SDK Platform 35.
2. Open the extracted `Deratization` folder (the folder containing `settings.gradle.kts`).
3. Allow Gradle sync and install any requested build components.
4. Run the `app` configuration on an Android 8.0+ device or emulator.
5. Grant audio-library permission when asked.

Required JDK: 17. Gradle plugins and libraries are declared in the Kotlin Gradle build files.

## Architecture

- `MainActivity.kt`: UI, MediaStore repository, Media3 player, equalizer UI, direct-audio import, playlist/history screens.
- `DeratizationDatabase.kt`: Room entities and DAOs for playlists, playlist membership and listening history.
- `app/src/main/AndroidManifest.xml`: permissions and app entry point.

## Suggested next production-hardening tasks

1. Add an AndroidX MediaSessionService with media notification and background playback.
2. Add instrumentation/unit tests and run Gradle assemble/debug.
3. Add album/artist-specific browsing and metadata editing.
4. Add a queue screen and favorite-track persistence.
5. Add provider-specific imports only through supported APIs and permitted flows.
