# P1-03 API 37 工具链升级验证记录

## P1-03-R4 本机模拟器恢复（2026-09-08，最新状态）

本轮由分析审查窗口按用户新增授权修复项目所需本机环境，基准 `4ee8919`，
分支 `Android`，开始工作区干净。没有修改应用代码、构建依赖或产品决定。
**本机 API 37 ARM64 运行证据已补齐，待独立复审；不进入 P2，用户未验收。**
本节取代历史“只能换真机／另一宿主”的下一步要求，历史失败观察仍保留。

### 根因与修复

AVD 登记文件 `/Users/takagisan/.android/qingke-api37-r3-avd/qingke-api37-r3-arm.ini`
实际含 `target=android-0`；稳定版与预览版启动日志均将镜像误识别为 `API level: 3`，
不启用 HVF，随后出现 `mprotect failed: Permission denied`。备份到
`/tmp/qingke-api37-r3-arm-before-target-fix.ini` 后，仅将此参数改为
`target=android-37`。同一 SDK、同一镜像、原稳定版 Emulator 37.1.11.0 随即识别
API 37，QEMU 参数出现 `-enable-hvf`，约 15 秒取得 `sys.boot_completed=1`。
此处只是修正宿主 AVD 登记，不修改镜像内 Android 版本；ADB 实测 SDK 37 和
`arm64-v8a`。错误登记由哪个历史命令产生尚未复现，不能断言是官方已确认缺陷。

此前按用户授权开启 `DevToolsSecurity`，中文“开发者工具”内终端开关开启；
单独开启后仍失败，未证明它是必要条件，本轮保持已授权的开启状态。没有关闭 SIP、
Gatekeeper 或重签 Emulator。清理了本任务遗留、反复崩溃重启的
`com.qingke.api37.emulator` launchctl 作业。预览版 37.2.7 校验成功但同样失败，
隔离保存在 `/Users/takagisan/Library/Android/emulator-preview-37.2.7`，没有替换稳定 SDK。

公开同类记录已实际读取：
[DataDog #3606](https://github.com/DataDog/dd-sdk-android/pull/3606) 报告 API 37、macOS
Sonoma 上相同 HVF／mprotect 文本；[Jerico #57](https://github.com/Appnova-EU-OU/jerico/issues/57)
也有相同文本。它们仅证明相同症状曾出现，不证明本机也是权限或 macOS 兼容问题。
此前先断言系统兼容问题及“预览版很可能修复”的说法缺乏依据，本轮予以更正。

### 实测命令与证据

```bash
export ANDROID_HOME=/Users/takagisan/Library/Android/sdk-qingke-api37
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd
"$ANDROID_HOME/emulator/emulator" -avd qingke-api37-r3-arm \
  -no-snapshot -no-window -gpu software -port 5586 -verbose
# 在另一个终端执行；仅当下面返回 1 才安装
"$ANDROID_HOME/platform-tools/adb" -s emulator-5586 shell getprop sys.boot_completed
cd /Users/takagisan/课表软件/Android
./gradlew assembleDebug test --no-daemon --console=plain
bash scripts/p1-02-apk-check.sh
```

- 构建命令通过，70 个任务 up-to-date；读取既有报告 Debug／Release 各 20 项，
  failures/errors/skipped 为 0。本轮未重新执行 JVM 测试；既有 clean／离线验证沿用已复审记录。
- APK 包名 `com.qingke.schedule`，minSdk 26、targetSdk 37；`adb install -r` 成功。
- 清空 `logcat -b all` 后，两次 `am force-stop` 和 `am start -W` 均为
  `Status: ok`、`LaunchState: COLD`，耗时 607／691 ms；PID 分别为 4283／4341。
- 两次 `dumpsys activity activities` 均显示 MainActivity resumed、visible；两张
  1080×1920 截图显示“轻课”文字骨架。文字靠近状态栏，未做完整视觉／安全区域验收。
- 清空后的完整 logcat 未匹配 FATAL EXCEPTION、ANR in、am_anr、am_crash 或 Fatal signal；
  存在 `vendor.mesa.virtgpu.kumquat` 读取权限警告，不能写成“没有任何警告”。
- 之后用 `adb emu kill` 正常关闭，再用同一稳定版带窗口启动，约 27 秒再次开机成功，
  APK 无需重装，MainActivity 冷启动成功（3325 ms）。本机当前保留可见窗口。

提交的文本证据仅规范行尾空白，原始输出保留在 `/tmp/qingke-api37-targetfix-evidence`。
文档验证 25 项、既有文档及布局测试通过；暂存检查发现日志原始行尾空白后已规范并复验。

提交证据：[启动前后对照](evidence/p1-03-r4-target-fix/startup-comparison.txt)、
[构建输出](evidence/p1-03-r4-target-fix/build.txt)、
[设备命令与结果](evidence/p1-03-r4-target-fix/device-validation.txt)、
[第一次截图](evidence/p1-03-r4-target-fix/cold-start-1.png)、
[第二次截图](evidence/p1-03-r4-target-fix/cold-start-2.png)、
[清空后的 logcat](evidence/p1-03-r4-target-fix/logcat.txt)、
[结构化摘要](evidence/p1-03-r4-target-fix/result.json)。APK SHA256 在设备证据和摘要中。

后续正常启动去掉 `-no-window` 即可，不需要 Android Studio。当前窗口由临时
`/tmp/com.qingke.api37.verified.plist` 启动，作业 `com.qingke.api37.verified` 不自动重启、
不安装为登录启动项；停止可用 `adb -s emulator-5586 emu kill`，不要重复启动同一 AVD。

新增文档验证检查真实证据中的两次冷启动、API/ABI、Activity、PID、PNG 尺寸、日志边界
与 APK 摘要关联。P1-03-R4 环境修复与运行证据仍需独立复审；本窗口没有实施应用代码。
不将本次最小启动扩展为 A01—A11、存储、通知、内存压力或完整依赖兼容测试通过。
未知字段策略仍未决定，不自动安排 P2。


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

## P1-03-R3 持久 API 37 设备环境恢复尝试

2026-09-08，按 R3 范围将已验证的官方 API 37 SDK 复制到持久目录
`/Users/takagisan/Library/Android/sdk-qingke-api37`，并在
`/Users/takagisan/.android/qingke-api37-r3-avd` 建立干净的
`qingke-api37-r3-arm` AVD。组件为 Emulator 37.1.11.0、Platform Tools 37.0.1、
Platform 37.0 revision 2、Build Tools 36.0.0 和 API 37 ARM64 Google APIs image revision 6；
宿主为 arm64，`emulator-check accel` 退出 0 且报告 Hypervisor.Framework 可用。

只使用官方 `emulator/emulator` 启动器，未直接运行内部 QEMU，也未影响既有 API 35
`emulator-5554`。默认启动（端口 5570）在限定 30 秒观察中始终没有 ADB 设备且进程退出；
随后一次 `-no-snapshot -no-window -gpu software` 对照（端口 5572）同样未取得设备，前台
最小日志仍有 `hvf is not enabled on this aarch64 host` 与
`qemu_mprotect__osdep: mprotect failed: Permission denied`。完整命令、ADB 状态和最小日志见
[R3 持久 SDK 诊断证据](evidence/p1-03-r3-api37-persistent-sdk-attempt-20260908.txt)。

因此 R3 没有获得 API 37 `sys.boot_completed=1`；没有读取 R3 的 API/ABI、安装 APK、两次
`force-stop` 后显式启动、检查 MainActivity resumed/可见或 `pidof`、保存截图、或取得清空后
无应用崩溃/ANR logcat。API 35 历史结果没有替代本轮证据。当前 Mac 仍被环境阻塞，下一步
须改用 API 37 ARM64 真机或另一台可正常启动 API 37 的宿主；P1-03、P1 和用户验收仍未完成，
不进入 P2，且本轮未发现或修改应用代码缺陷。
