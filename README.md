# 小d播放器

一个简洁的 Android 本地音乐播放器，只做最核心的听歌体验：播放、暂停、下一首，随机 / 列表循环 / 单曲循环，自动搜索歌词，外加一个可拖动的系统悬浮窗。

- 包名：`com.xiaod.player`
- 最低版本：Android 7.0（API 24），适配到 Android 14（targetSdk 34）
- 技术：Java + AndroidX，进程内单例 `PlayerManager`（MediaPlayer + Handler，支持多监听）

## 功能

- 播放 / 暂停、下一首
- 播放模式切换：列表循环、随机播放、单曲循环
- 手动扫描本地音乐（启动不自动扫描），只收录 **.mp3** 文件
- 自动排除手机内置品牌主题曲：歌名或文件名包含“荣耀”“主题曲”的一律不收
- 歌曲列表左滑或右滑显示“删除”，可移除单曲（只移除列表，不删文件）
- 自动搜索歌词；当前唱到的那一句固定停在歌词区垂直中线
- 歌手 / 专辑为未知时不显示，界面更干净
- 系统悬浮窗：显示当前歌曲名 + 暂停/继续 + 下一首，可拖动、可关闭

## 歌词搜索

本地 MP3 常见命名为 `歌唱者－歌名.mp3`，因此歌词模块会先从文件名/标题中拆出歌手与歌名（支持 `－ — – ｜ | -` 等分隔符，自动去掉扩展名和 `01.` 式音轨号），再结构化搜索：

1. **QQ 音乐**（主源）：搜索 “歌手 歌名”，按歌名/歌手/时长打分择优，取时间轴歌词（LRC）。
2. **LRCLIB**（兜底）：免费开放歌词库，覆盖外语歌。

同步歌词优先；无时间轴时按纯文本逐行展示；“纯音乐，请欣赏”这类占位歌词会被过滤。

## 悬浮窗

首次点“悬浮窗”会申请“显示在其他应用上层”权限（`SYSTEM_ALERT_WINDOW`），授权后自动开启。
悬浮窗由 `FloatingPlayerService`（前台服务，`mediaPlayback` 类型）承载：

- 标题行按住可拖动，停在哪里就留在哪里
- `播放/暂停` 与 `下一首` 直接操作同一份 PlayerManager
- 右上角 `×` 关闭悬浮窗（回到 App 也可以关）

## 构建

需要 JDK 17 与 Android SDK（platform 34、build-tools 34.0.0）：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

仓库内置 `.github/workflows/build.yml`，推送到 `main` 或手动触发即可在 Actions 里拿到 APK artifact。

## 说明

- 仓库不包含二进制 `gradle-wrapper.jar`，从 GitHub 拉下来后请用本地 Gradle 或 Android Studio 同步（Android Studio 会自动补齐 wrapper）。
- 歌词版权归原作者与各音乐平台所有，仅用于个人本地播放展示。
