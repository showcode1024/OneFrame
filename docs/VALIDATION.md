# 0.1.0 历史验证记录

本记录保留早期 Prism 原型的名称、产物文件名及校验值；当前产品名为 OneFrame。

日期：2026-10-27（Asia/Singapore）。版本：0.1.0-alpha。

## 构建与检查

- Windows / JDK 17；Gradle 8.13；AGP 8.13.2；compileSdk / targetSdk 36。
- `assembleDebug`、`assembleDebugAndroidTest` 成功。
- 15 项 JVM 单元测试通过，0 跳过：Emby 8 项、播放策略 4 项、流转发与凭据隔离 3 项。
- 4 项 Android instrumentation 测试通过，0 跳过：主页/本地链接入口、P5、P8.1、P8.4。
- Android Lint：0 errors / 17 warnings。警告主要是依赖有新版本及 KTX 写法建议；完整报告在本机 `app/build/reports/lint-results-debug.html`。
- 最终 APK 通过 `apksigner verify` 和 `zipalign -c -P 16 4`。
- APK 的 22 个 native ELF 库，PT_LOAD 最小对齐均为 16384 字节。
- `tools/build.ps1` 使用项目自带 Gradle Wrapper 构建流程已执行成功。

## 设备与杜比播放验证

测试环境为 Pixel_9_Pro 命名的 AVD，实际是 Android 17 / API 37 x86_64 模拟器，使用 SwiftShader。为测试降低到 720×1280 / density 280；这不是实体 Pixel 手机的画质或性能结果。

三种测试均强制 CPU 解码、要求实际 `current-vo = gpu-next`，并检测到对应杜比 Profile。测试读取真实 native 播放状态，不使用模拟的播放器状态。Native 日志也确认 HEVC software decoding、Dolby Vision 帧信息与 libplacebo 渲染路径。源码中的截图来自实际设备屏幕。

| Dolby 官方测试文件 | 识别结果 | 解码/渲染 | 实际画面 |
|---|---|---|---|
| `color_pattern_24_dvhe_05_1920x1080.mp4` | Profile 5 | CPU / gpu-next | 有色彩图案输出 |
| `color_pattern_24_dvhe_081_1920x1080.mp4` | Profile 8 | CPU / gpu-next | 有色彩图案输出 |
| `color_pattern_24_dvhe_084_1920x1080.mp4` | Profile 8 | CPU / gpu-next | 有色彩图案输出 |

容器标记的 Profile 数字均为 8 时，测试中的 8.1 / 8.4 来自各自的官方源文件。输出目标明确设为 BT.709 / BT.1886 / 100 nits，并关闭目标色彩空间提示，避免将 SDR 输出宣称为原生 Dolby Vision 显示。

官方样片地址：

- <https://ott.dolby.com/OnDelKits/DolbyVision_OSS_Media_Kit/clear/color_pattern_24_dvhe_05_1920x1080.mp4>
- <https://ott.dolby.com/OnDelKits/DolbyVision_OSS_Media_Kit/clear/color_pattern_24_dvhe_081_1920x1080.mp4>
- <https://ott.dolby.com/OnDelKits/DolbyVision_OSS_Media_Kit/clear/color_pattern_24_dvhe_084_1920x1080.mp4>

样片未打入 App APK。下载与推送步骤见 `tools/test-device.ps1`。

## Emby 验证

MockWebServer 验证了反向代理路径、密码 JSON 转义、Token 请求头、搜索编码、登录重定向拒绝、媒体项与媒体源 ID 区分、禁止静默转码以及进度 Tick 单位。流转发器另外验证了 Range、跨站不传 Token、无效本机路径拒绝。

目前没有真实 Emby 服务器账号，没有宣称已经完成真实服务器联调。需要后续在真实服务器上验证库浏览、字幕、续播以及观看状态。

## 尚未证明或尚未实现

- 未做标定显示器对照、逐像素参考比较、Dolby 认证或端到端颜色精度验收。测试通过代表路径与样片输出可用，不代表所有杜比片源均能正确还原。
- 未完成实体手机/平板、4K、高码率、长时间温度功耗、弱网和各种 GPU 驱动的验证。模拟器的 GPU 软件渲染发生掉帧，不能据此推断实体设备性能。界面显示的 fps 是源帧率，不是实测屏幕呈现帧率。
- Profile 7 FEL 完整增强层重建未实现/未验收。
- Atmos 对象解码与空间渲染未实现。当前音频路径为普通声道软件解码后输出双声道；官方色彩测试片本身没有音轨，因此这些杜比画面测试不构成音频验证。
- 未完成真实 Emby、多账号、多服务器、外置字幕发现、可选播放版本、离线下载、后台播放和画中画。

## 本次产物

- APK：`artifacts/PrismPlayer-0.1.0-alpha.apk`，Android 开发签名。
- APK SHA-256：`2204eb52bde8c6e4f33ce2674248ea01daa50379080391690c95ac2372e9387f`。
- 原生 AAR `dev.jdtech.mpv:libmpv:1.0.0` SHA-256：`df146592480fc8418415a06b1f1a1d6318b0088e21f52254b0e9a82b61ca8fa2`。
- 样片与 APK 校验值：`artifacts/checksums.json`。
- 界面与播放截图：`docs/screenshots/`。
