# MusicSquare

[English](README.md) | [中文](README.zh-CN.md)

原生 Android 音乐搜索与播放客户端。

支持在网易云、QQ 音乐、酷我、JOOX 等多音源间关键词搜索并在应用内播放。基于 Kotlin、Jetpack Compose 与 Media3 构建。

## 功能特性

- 多音源搜索（网易云 / QQ / 酷我 / JOOX），支持加载更多
- 全屏正在播放页、歌词同步、播放模式（列表循环 / 单曲循环 / 随机）
- 播放队列、收藏、自建歌单（支持 JSON 导入 / 导出）
- 底部导航：发现 / 我的 / 设置
- 后台播放与系统媒体通知栏控制

## 环境要求

- JDK 17+
- Android SDK（compileSdk 35，minSdk 26）
- Android Studio Hedgehog 及以上，或命令行 Gradle

## 构建与运行

```bash
cd android
# 首次使用：复制并填写本机 SDK 路径
cp local.properties.example local.properties

./gradlew :app:assembleDebug
# 或安装到已连接的设备：
./gradlew :app:installDebug
```

Debug 安装包路径：

`android/app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
android/
├── app/src/main/java/com/musicsquare/app/
│   ├── data/          # 数据模型、音源 API、仓库
│   ├── player/        # Media3 播放服务
│   └── ui/            # Compose 界面
├── gradlew
└── local.properties.example
```

## 许可证

Apache License 2.0。仅供学习与演示使用；音乐版权归各平台及原作者所有。
