# OneFrame 0.1.2-alpha 验证记录

日期：2026-10-27（Asia/Singapore）。

## 本次变更

- 应用名称、主页、设置、播放诊断、Emby 客户端标识、系统媒体会话、工程及构建产物统一使用 OneFrame。
- Kotlin 命名空间改为 `io.github.showcode1024.oneframe`，主界面和播放界面的组件路径同步迁移。
- 为支持覆盖安装，安装标识继续使用 `dev.prism.player`，本地偏好和 Android Keystore 别名保持兼容。版本号 0.1.2-alpha / versionCode 3。
- 仓库包含可复现的 Gradle Wrapper、Windows 构建/设备测试脚本，以及 GitHub Actions 构建、单元测试和 Lint 工作流。CI 使用固定提交版本的第三方 Actions。
- 该版本没有改变解码、色彩映射或音频功能范围。

## 本地验证

- `assembleDebug`、`assembleDebugAndroidTest` 成功。
- 16 项单元测试通过，0 跳过。
- 5 项 Android instrumentation 测试通过，包含 OneFrame 主页名称断言、P5/P8.1/P8.4 软件播放，以及自动策略下经流转发播放 P5。
- Android Lint：0 errors / 18 warnings，未添加忽略错误的 baseline。
- APK 包信息确认：`application-label = OneFrame`，主界面为 `io.github.showcode1024.oneframe.MainActivity`。
- 同一模拟器上通过 `adb install -r` 覆盖早期安装成功。
- APK 通过签名校验与 16 KiB ZIP 对齐校验。
- 测试环境为 Android 17 / API 37 x86_64 AVD、SwiftShader，不是实体 MatePad。模拟器冷启动时第一次 instrumentation 进程被终止；系统稳定后完整重跑 5 项测试通过，未为此改动或放宽测试断言。

本地 APK：`artifacts/OneFrame-0.1.2-alpha.apk`。

SHA-256：`3c7bb4d5b7bf8a8b9b498235d74a68104825484670e979b62d9efcf06d6afea4`。

## 仍然适用的限制

画面仍输出 SDR / BT.709；HDR10 输出、P7 FEL 完整重建、Atmos 对象渲染尚未实现。当前仓库不能据此宣称所有杜比片源可正确还原或实体平板 4K 性能已通过验收。

真实 Emby 服务器和 MatePad 11 的性能仍需联调，前序证据和性能策略见 [0.1.1 记录](VALIDATION-0.1.1.md)。

CI 生成的临时签名 APK 只用于测试；覆盖升级早期本地版本需要匹配签名。仓库不会提交私钥、本地 SDK 配置、账号会话或 Gradle 缓存。
