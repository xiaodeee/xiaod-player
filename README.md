# 小d播放器 (XiaoD Player)

一个轻量、深色风格的 Android 本地音乐播放器，支持手动扫描本地 MP3、滑动删除歌曲与自动搜索歌词。

- 包名：`com.xiaod.player`
- 版本：1.1（versionCode 2）
- 最低系统：Android 8.0（API 24），targetSdk 34
- 语言：Java + AndroidX

## 功能

| 功能 | 说明 |
| --- | --- |
| 基础播放 | 播放 / 暂停、上一首 / 下一首、拖动进度条。播放超过 3 秒时“上一首”先回到本曲开头 |
| 播放模式 | 列表循环（默认）→ 随机播放 → 单曲循环，点左侧胶囊按钮循环切换 |
| 手动扫描 | 点右上角“扫描本地音乐”，从系统媒体库读取音频；**只收录 MP3**（文件名 .mp3 或 MIME audio/mpeg），不做后台监听 |
| 滑动删除 | 歌曲列表左滑或右滑，显示红色“删除”背景，松手即从列表移除；删的是当前歌曲时自动切到下一首（正在播则继续播） |
| 当前歌曲 | 顶部显示歌名、歌手、专辑；列表中当前播放歌曲以绿色高亮 |
| 自动歌词 | 切歌后按歌名 + 歌手 + 时长自动到 LRCLIB 搜索歌词，优先带时间轴的同步歌词，逐行高亮并自动滚动；没有时间轴时按纯文本展示 |
| 歌词面板 | 点“歌词”标题行展开 / 收起；展开时歌词区占 2/3 高度、字号放大（当前行 20sp），歌曲列表缩为 1/3，可随时收起恢复列表 |

## 使用

1. 首次打开会请求读取音频权限（Android 13+ 为 `READ_MEDIA_AUDIO`，更早版本为 `READ_EXTERNAL_STORAGE`）。
2. 授权后自动扫描一次；之后可随时点右上角重新扫描（只扫描 .mp3 文件）。
3. 点列表中的任意歌曲开始播放；左/右滑任一首可删除。
4. 点左侧模式胶囊切换：列表循环 / 随机播放 / 单曲循环。
5. 歌词会在换歌时自动获取，需要联网。

## 构建

### Android Studio（推荐）

1. `File → Open` 选择本目录。
2. 等待 Gradle Sync 完成（AGP 8.2.2 + Gradle 8.2 + JDK 17）。
3. 连接手机或启动模拟器，点 Run 即可安装。

### 命令行

```bash
# 需要 JDK 17 与 Android SDK（platform 34、build-tools 34.0.0）
export ANDROID_HOME=$HOME/android-sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions

仓库内置 `.github/workflows/build.yml`，推送到 `main` 或手动触发 `workflow_dispatch` 后，
可在 Actions 页面下载 `xiaod-player-debug` 构建产物（app-debug.apk）。

## 目录结构

```
app/src/main/java/com/xiaod/player/
├── MainActivity.java      # 界面、权限、扫描、滑动删除、歌词高亮
├── PlayerManager.java     # 播放核心：播放/暂停/上下曲/三种模式/进度回调/移除歌曲
├── MusicScanner.java      # MediaStore 本地音乐扫描（仅 MP3）
├── LyricsFetcher.java     # LRCLIB 歌词搜索（同步歌词优先）
├── LrcParser.java         # LRC 解析与按时间定位当前行
├── Song.java              # 歌曲数据模型
├── SongAdapter.java       # 歌曲列表
└── LyricsAdapter.java     # 歌词列表
```

## 说明与边界

- 播放基于 `MediaPlayer`，播放逻辑放在应用进程内的单例 `PlayerManager` 中。
  应用被系统回收或长时间切到后台时，播放可能停止；如需熄屏后台播放，可后续接入
  `MediaSessionService` / 前台服务。
- 歌词来自 [LRCLIB](https://lrclib.net)（免费开放歌词库，无需 Key），匹配依赖歌曲元数据；
  本地文件没有内嵌正确歌名/歌手时可能搜不到歌词。
- 删除仅从本次播放列表中移除，不会删除手机里的音乐文件；重新扫描会重新出现。
- 扫描只读取系统媒体库已收录的音频，刚拷贝进手机的文件可能需要系统媒体扫描后才出现。
