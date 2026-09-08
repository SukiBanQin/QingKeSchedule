# P1-03 API 37 工具链升级验证记录

验证日期：2026-09-08。角色：执行；未启动子 Agent。任务从 `ec236b9` 开始，
授权提交为 `274f3b9`，分支为 `Android`。本轮仅升级已授权工具链、维护构建／
验证脚本和中文说明，不进入 P2，不修改 iOS、Web、共享协议、业务规则或应用 Kotlin
源码。

## 实施结果

- `compileSdk`／`targetSdk` 升至 37，显式使用 Build Tools 36.0.0；`minSdk` 26、
  `com.qingke.schedule` applicationId 和 namespace 保持不变。
- AGP 升至 9.4.0，Wrapper 升至 Gradle 9.6.0，构建 JVM 保持 JDK 17。
- 按 AGP 9 built-in Kotlin 迁移要求移除 `org.jetbrains.kotlin.android`；Compose
  compiler 与 serialization 插件均调整为 2.2.10，与 AGP 9.4.0 的运行时 KGP
  对齐。Compose BOM 2024.12.01、kotlinx serialization JSON 1.7.3 和其他应用依赖
  未升级。
- AGP 9 默认只启用被测试构建类型的单元测试。第一次请求
  `testReleaseUnitTest` 时任务不存在；为满足本任务 Debug／Release 双变体验证要求，
  在 `gradle.properties` 设置
  `android.onlyEnableUnitTestForTheTestedBuildType=false`，没有改变应用运行行为。
- AGP 9 改变 Kotlin 类输出目录，因此跨端探针优先读取
  `intermediates/built_in_kotlinc`，并保留旧目录回退；探针使用的 Kotlin stdlib
  版本同步为 2.2.10。APK 预检脚本同步检查 Build Tools 36.0.0 和 targetSdk 37。

官方依据见 `Android/README.md`：AGP 9.4 兼容表列出最高 API 37、Gradle 9.6.0、
Build Tools 36.0.0 和 JDK 17；built-in Kotlin 与 Compose compiler 迁移按 Android
Developers 官方说明实施。版本声明本身不作为构建通过证据。

## 构建环境

- 宿主：Mac OS X 26.6.2，aarch64。
- JDK：Microsoft OpenJDK 17.0.20.1，aarch64。
- `./gradlew --version`：Gradle 9.6.0；Gradle 自身报告嵌入 Kotlin 2.3.21。
  该字段不是 AGP built-in Kotlin 的 KGP 版本，不与工程插件 2.2.10 混用。
- 临时 SDK：Platform Tools、`platforms;android-37.0` revision 2、
  `build-tools;36.0.0`；构建时通过有效的 `ANDROID_HOME`／`ANDROID_SDK_ROOT`
  指向该 SDK，未提交机器相关 `local.properties`。

## 构建、测试与静态检查

在 `Android/` 下、JDK 17 和上述实际 SDK 中执行：

```bash
./gradlew --version
./gradlew clean assembleDebug assembleRelease \
  testDebugUnitTest testReleaseUnitTest --rerun-tasks --no-daemon --console=plain
./gradlew --offline clean assembleDebug assembleRelease \
  testDebugUnitTest testReleaseUnitTest --rerun-tasks --no-daemon --console=plain
./gradlew lintDebug --rerun-tasks --no-daemon --console=plain
```

结果：

- 在线 clean 构建通过，`BUILD SUCCESSFUL in 3m 18s`，100 个任务执行。
- 缓存就绪后的离线 clean 构建通过，`BUILD SUCCESSFUL in 15s`，100 个任务执行。
- Debug 和 Release 各 20 项 JVM 测试通过，失败、错误、跳过均为 0；构成为
  `ScheduleRulesTest` 4 项、`ScheduleValidatorTest` 5 项、
  `ScheduleDataDecoderTest` 11 项。
- `lintDebug` 通过，29 个任务执行；HTML 报告：
  `Android/app/build/reports/lint-results-debug.html`。
- 构建仅出现原生库 `libandroidx.graphics.path.so` 无法 strip、按原样打包的警告；
  Debug／Release APK 均正常生成，没有因此调整依赖。
- APK 预检通过：包名 `com.qingke.schedule`、minSdk 26、targetSdk 37。

产物与报告：

- Debug APK：`Android/app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`Android/app/build/outputs/apk/release/app-release-unsigned.apk`
- JVM HTML：`Android/app/build/reports/tests/testDebugUnitTest/index.html` 和
  `Android/app/build/reports/tests/testReleaseUnitTest/index.html`
- JVM XML：`Android/app/build/test-results/testDebugUnitTest/` 和
  `Android/app/build/test-results/testReleaseUnitTest/`
- Lint HTML：`Android/app/build/reports/lint-results-debug.html`

## 跨端契约探针

执行 `python3 docs/tests/android-contract-review-probe.py`，16 个输入案例均实际调用
升级后 Android decoder 和原 Swift `previewImport`。逐例核对结果：合法整数指数、
小数版本、重复课程 ID、反序节次编号和有效样例两端均接受；非法 UTF-8、四种非法
JSON 数字、字符串整数、年份 0000 两端均拒绝。Android 保持拒绝顶层／嵌套未知
字段，而 Swift 接受，这是已知未决策略；本轮没有修改或宣称批准。探针退出 0 仅
表示执行完成，以上逐例结果才是本次记录。

## API 37 ARM64 设备验证

已从稳定渠道安装 Emulator 37.1.11 和
`system-images;android-37.0;google_apis;arm64-v8a` revision 6，创建 AVD
`qingke-api37-arm`。模拟器找到正确 ARM64 API 37 镜像，但当前 aarch64 宿主报告
`hvf is not enabled`，随后 QEMU 报 `mprotect failed: Permission denied`。默认启动
由 hang detector 终止；显式 `-accel off` 时 ADB 曾进入 `device`，但
`sys.boot_completed` 未返回 1，未完成开机。

最小错误输出与尝试命令保存在
[API 37 模拟器错误证据](evidence/p1-03-api37-emulator-error-20260908.txt)。因此本轮
未执行 APK 安装、两次冷启动、Activity／进程检查或应用 logcat 崩溃检查；不得用
历史 API 35 证据替代。目标版本没有降级，但 P1-03 设备门槛未通过。

## P1-03-R1 宿主设备复验与 Android 17 行为核对

2026-09-08 的 P1-03-R1 从专项复审基准
`9f60df0d723fc0e5282906901f434018f6bbcd5a` 开始。为避免在原受限执行进程中反复
启动 QEMU，改由宿主 Terminal 用独立端口 5556 正常启动 `qingke-api37-arm`。ADB
依次观察到 `offline`、`device`，并在短暂可用时读取到：

- `ro.build.version.sdk=37`
- `ro.product.cpu.abi=arm64-v8a`
- `sys.boot_completed` 为空，不是验收要求的 `1`

随后 AVD/QEMU 退出，ADB 不再列出 `emulator-5556`。这次没有重现原有的 HVF 或
`mprotect` 文本，但同样没有得到可用的 API 37 设备，不能视为设备验收通过。完整
命令和输出在 [R1 宿主启动证据](evidence/p1-03-r1-api37-host-attempt-20260908.txt)。

按要求重新执行：

```bash
cd Android
ANDROID_HOME=<API 37 SDK> ANDROID_SDK_ROOT=<API 37 SDK> \
  ./gradlew assembleDebug --rerun-tasks --no-daemon --console=plain
ANDROID_HOME=<API 37 SDK> ANDROID_SDK_ROOT=<API 37 SDK> \
  bash scripts/p1-02-apk-check.sh
```

结果：`assembleDebug` 成功，38 个任务执行；仅有既有
`libandroidx.graphics.path.so` 无法 strip、按原样打包的提示。APK 预检通过，包名为
`com.qingke.schedule`、minSdk 26、targetSdk 37。因为没有 `sys.boot_completed=1` 的
API 37 设备，未安装 APK，也未执行两次 `am force-stop`／显式 `MainActivity` 冷启动、
`dumpsys` resumed 检查、`pidof`、截图和清空后的应用 logcat；这些均保持未运行。

### Android 17 行为逐项适用性

资料来源为 Android Developers 的[全部应用行为变化](https://developer.android.com/about/versions/17/behavior-changes-all)、[目标 Android 17 的行为变化](https://developer.android.com/about/versions/17/behavior-changes-17)及[迁移说明](https://developer.android.com/about/versions/17/migration)。迁移说明要求在 Android 17 设备／模拟器安装并走通应用流程，且分别审查全部应用与目标版本行为；构建成功不能替代该步骤。

当前 Android 平台界面入口和平台能力实现仅为 `MainActivity` 的 Compose
`MaterialTheme`、`Surface`、`Text`，manifest 仅声明 launcher Activity；工程还包含
领域模型、规则、校验和 JSON 解码源码。已检查全部项目自编写 Kotlin 源码与 manifest，
没有发现本轮 Android 17 行为涉及的平台 API 调用。以下“未使用”是项目自编写代码的
静态检查结论，不表示未来 P2/P3/P4 实现已经验证，也不覆盖现有依赖层运行行为。

当前 APK 已含 Compose／AndroidX 等依赖，且构建输出已确认打包
`lib/*/libandroidx.graphics.path.so`。项目没有自行编写 JNI、`System.load()`、动态 native
加载、`MessageQueue` 私有反射或修改 static final 字段的代码；但这些结论不能排除依赖层
的 `MessageQueue`、反射和原生库兼容性。它们仍须由 API 37 安装启动和清空后的 logcat
验证，不能推迟为“以后引入第三方 SDK”才需检查。

| 官方变化 | 当前 P1 判断 | 本轮验证或后续门槛 |
| --- | --- | --- |
| 全部应用：应用内存限制 | 此限制适用于所有 Android 17 应用；项目自编写代码无已知高内存流程 | API 37 未开机，未执行 memory limiter 压力测试；现有依赖运行与后续图片/列表/存储均需建立内存基线。 |
| 全部应用：WebOTP/SMS OTP 保护 | 无 `READ_SMS`、SMS Receiver 或 OTP 流程 | 暂不适用；若加入登录/OTP，采用 SMS Retriever/User Consent 并复核。 |
| 全部应用：明文网络迁移提醒 | 无网络请求或 network security config | 暂不适用；网络能力加入时按配置复核。 |
| 全部应用：隐式 URI 授权诊断 | 无 `ACTION_SEND`、`ACTION_SEND_MULTIPLE`、相机或 `Uri` 分享 | 暂不适用；P4 导入导出／分享实现时以显式 grant 和 StrictMode 检查。 |
| 全部应用：Keystore 50,000 key 限额 | 无 Keystore 使用 | 暂不适用；凭据或加密存储引入时复核。 |
| 全部应用：跨 profile loopback | 无 loopback/LAN 通信或多 profile 支持 | 暂不适用；网络/企业 profile 支持时复核。 |
| 全部应用：旋转后 IME 可见性 | 无可编辑控件、输入法显式控制或 configuration 处理 | 暂不适用；P3 表单/编辑页面需在旋转后验证。 |
| 全部应用：pointer capture 触控板相对事件 | 无 pointer capture | 暂不适用；如新增桌面化拖拽交互再验证。 |
| 全部应用：后台音频硬化 | 无音频 API、前台服务或提醒音 | 暂不适用；D03/提醒阶段验证 exact alarm 与前台服务边界。 |
| 全部应用：Bluetooth 自动重新配对 | 无 Bluetooth 权限、Receiver 或连接 | 暂不适用；硬件功能不在 P1 范围。 |
| target 37：RemoteViews bitmap/icon 限制 | 无 App Widget、RemoteViews 或通知大图 | 暂不适用；通知/小组件阶段验证大小和崩溃处理。 |
| target 37：lock-free `MessageQueue` | target 37 会启用；项目自编写代码无 `MessageQueue` 私有字段/方法反射 | 现有 Compose／AndroidX 依赖仍待 API 37 安装启动、logcat 和 non-SDK 检查复核。 |
| target 37：static final 不可修改 | 项目自编写代码无反射/JNI 修改 static final 字段 | 现有依赖层仍待 API 37 安装启动和 logcat 验证；未来不得依赖此类实现。 |
| target 37：CJK 物理键盘辅助功能 | 无 TextField、custom InputConnection 或自发 accessibility event | 暂不适用；P3 输入控件使用标准 Compose/Android 文本组件并补辅助功能测试。 |
| target 37：ECH 和局域网权限 | 无 TLS、HTTP client、LAN 扫描或设备连接 | 暂不适用；网络/LAN 功能阶段按权限或系统 picker 验证。 |
| target 37：物理键盘密码显示、标准 SMS OTP | 无密码输入、SMS 权限或 OTP 流程 | 暂不适用；认证功能引入时复核。 |
| target 37：BAL、CT、动态 native DCL | 项目自编写代码无后台 Activity/IntentSender、网络证书、JNI、`System.load()` 或动态 native 加载 | APK 已含依赖原生库，仍待 API 37 安装启动和 logcat；分享、通知跳转、网络和 native 依赖阶段分别继续验证。 |
| target 37：Contacts CP2、Content Capture | 无 Contacts 查询或 `setContentCaptureEnabled(false)` | 暂不适用；若出现敏感内容，设计时评估 FLAG_SECURE。 |
| target 37：大屏方向/可调整大小/宽高比 | 未在 manifest 设置方向、可调整大小或宽高比限制 | 这是当前 manifest／UI 层最直接的变化，但不是唯一需设备验证的项；API 37 未开机，未取得渲染截图。P3 页面完成后以 phone/tablet/旋转视觉用例验证。 |
| target 37：Bluetooth RFCOMM `read()` 返回 -1 | 无 BluetoothSocket | 暂不适用；硬件通信若引入，读循环显式处理 -1。 |

### P1-03-R2 单次设备尝试

从 R2 基准 `a16a52bbfba109aecc629ff19d8697ca2b1e9ca4` 重新构建 Debug APK（38 个任务）
并运行 APK 预检，包名 `com.qingke.schedule`、minSdk 26、targetSdk 37 均通过。随后在
宿主 Terminal 用标准 AVD 命令和独立端口 5558 **仅启动一次** API 37 ARM64
`qingke-api37-arm`。设备先持续 `offline`，短暂为 `device`，但
`sys.boot_completed` 仍为空，随后 QEMU/ADB 条目退出。完整命令和观察记录见
[R2 宿主启动证据](evidence/p1-03-r2-api37-host-attempt-20260908.txt)。

本轮没有获得 `sys.boot_completed=1`，也没有再次启动 AVD。因此没有安装 APK、两次
`force-stop` 后显式 `MainActivity` 冷启动、resumed／可见检查、`pidof`、截图或清空后的
应用 logcat；历史 API 35 设备未参与。结论：项目自编写源码的静态检查没有发现已知
高风险 Android 17 调用，但应用内存限制、新 `MessageQueue` 和现有依赖层兼容性仍待
API 37 真实运行验证；这不是 API 37 应用通过结论。

## 范围与下一步

本轮没有修改应用 Kotlin 源码、iOS、Web、共享 schema／fixtures、未知字段策略、
重复 ID 或节次顺序，也没有新增业务功能或进入 P2。工具链和主机侧验证已经专项复审；
P1-03-R2 仍需在可正常完成 API 37 开机的 ARM64 或等效设备补做安装、两次冷启动、
Activity／进程、截图和无崩溃 logcat。在该设备证据补齐并通过独立复审前，不声明
P1-03、P1 或用户验收完成。P1-03-R2 完成设备证据后仍需要独立专项复审。
