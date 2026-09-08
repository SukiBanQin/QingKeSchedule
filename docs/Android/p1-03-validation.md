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

## 范围与下一步

本轮没有修改应用 Kotlin 源码、iOS、Web、共享 schema／fixtures、未知字段策略、
重复 ID 或节次顺序，也没有新增业务功能或进入 P2。工具链和主机侧验证需要
独立专项复审；API 37 设备验证仍需在可启用 Apple Virtualization/HVF 的环境、Android
Studio Device Manager，或等效 API 37 ARM64 设备上补做。在该证据补齐并通过复审
前，不声明 P1-03、P1 或用户验收完成。
