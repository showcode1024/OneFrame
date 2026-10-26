# 0.1.1-alpha 验证记录

日期：2026-10-27（Asia/Singapore）。

## 用户提供的卡顿证据

MatePad 11，设备标识 DBY-W09，Android API 31；经 Emby 播放 3840×2160 HEVC、Dolby Vision Profile 7。旧版诊断为 CPU 软件解码 / gpu-next / SDR BT.709，累计呈现丢帧 754。

23.976 是源帧率，不是屏幕实际呈现帧率。旧诊断没有采样时长、缓冲状态或解码丢帧计数，因此不能用 754 单独断定瓶颈完全在 CPU，也不能排除 GPU 和网络因素。当前首先处理全 CPU 解码负载与状态采样，并提供更多定位信息。

## 本次变化

- 新安装或未明确设置过解码选项时，不再默认强制 CPU。自动模式请求 `mediacodec-copy,auto-copy`，尝试系统解码底层视频，保留供软件颜色处理使用的帧路径。
- 原有明确选择保留；设置开关修改后需重开视频。自动路径失败仍可能回退 CPU。系统解码不等于已证明某个硬件加速器被使用，尤其是模拟器可能只有系统软件 Codec。
- P5 在系统解码路径下若丢失预期杜比色彩标记，会请求回退 CPU；这只是保护检查，不能替代全部元数据及颜色精度验证。
- 将每秒原生状态轮询移至独立 HandlerThread；原生读取、控制和销毁通过同一锁保护，Activity 销毁时结束线程。
- 诊断增加：实际解码 API、请求策略、解码丢帧、等待缓冲状态、估计缓冲秒数、播放位置、采集时间、设备声明的 HEVC Main10 解码能力。
- 输出目标仍为 SDR / BT.709。未加入 HDR10 输出、P7 FEL 完整重建或 Atmos 对象渲染。

## 验证

- `assembleDebug`、`assembleDebugAndroidTest` 成功。
- 16 项 JVM 单元测试通过：Emby 8、播放策略 5、流转发 3。
- 5 项 Android instrumentation 测试通过：主页/本地入口、强制 CPU 下的 P5/P8.1/P8.4，以及自动策略下经 HTTP 流转发播放真实 P5 样片。
- Android Lint：0 errors / 18 warnings。主要为依赖更新与 KTX/HandlerThread 写法建议；没有跳过检查或添加 Lint baseline。
- 设备测试环境仍是 Android 17 / API 37 x86_64 AVD，SwiftShader，非实体 MatePad。自动模式测试验证请求策略、实际播放、元数据识别与状态报告，**不要求模拟器具备硬件解码器，也不宣称在该环境验证了硬解提速**。
- 没有连接用户的真实 Emby 服务器，没有重新取得用户视频或设备性能数据。

## 在 MatePad 上确认

覆盖安装同签名 0.1.1 APK，关闭“强制 CPU 软件解码”，退出播放后重开同一段视频。

观察“解码”是否仍为 CPU。播放卡顿时复制诊断；间隔一段时间再复制一次，可以结合采集时间计算呈现/解码丢帧增量。`bufferedSecondsEstimate` 只是 mpv 的近似值，缺失时不按零缓存处理。

若仍为 CPU，检查设备的 HEVC Main10 声明与实际启用 API；声明支持不保证 copy-back 路径或当前码流的层级、位深与分辨率都能使用。若系统解码已经启用仍卡顿，继续检查 GPU 渲染、复制开销和网络供数，不能提前声称已修复。

Profile 7 仍需要独立的完整播放验收，尤其是 FEL；此版本不将基础层显示计作完整 Dolby Vision 支持。
