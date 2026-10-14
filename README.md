# MusicSquare

[English](README.md) | [中文](README.zh-CN.md)

Native Android music search & playback client.

Supports keyword search and in-app playback across Netease, QQ Music, Kuwo, and JOOX. Built with Kotlin, Jetpack Compose, and Media3.

## Features

- Multi-source search (Netease / QQ / Kuwo / JOOX) with load-more
- Full-screen Now Playing, lyrics sync, play modes (list / single / shuffle)
- Queue sheet, favorites, custom playlists (import/export JSON)
- Bottom tabs: Discover / Library / Settings
- Background playback with media notification controls

## Requirements

- JDK 17+
- Android SDK (compileSdk 35, minSdk 26)
- Android Studio Hedgehog+ or command-line Gradle

## Build & run

```bash
cd android
# first time: copy and edit SDK path
cp local.properties.example local.properties

./gradlew :app:assembleDebug
# or install to a connected device:
./gradlew :app:installDebug
```

Debug APK:

`android/app/build/outputs/apk/debug/app-debug.apk`

## Project layout

```
android/
├── app/src/main/java/com/musicsquare/app/
│   ├── data/          # models, API sources, repository
│   ├── player/        # Media3 playback service
│   └── ui/            # Compose screens
├── gradlew
└── local.properties.example
```

## License

Apache License 2.0. For learning/demo use only; music copyrights belong to the original platforms and rights holders.
