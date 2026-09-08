# P1-03 API 37 工具链升级专项复审

复审日期：2026-09-08。角色：分析审查；未启动子 Agent，未修改应用代码。
复审对象为提交 `7d34c78b77462178ce2119c2fb21952ce187d18b`，提交范围
`7d34c78^..7d34c78`；开始基准 `ec236b9`、授权提交 `274f3b9` 均是其祖先。

最新状态以文末 P1-03-R1 独立复审为准：R1 设备失败记录有效，但 Android 17 依赖层
边界表述需修正，API 37 设备门槛仍未满足；下一步为 P1-03-R2。

## 结论

工具链改动和主机侧验证通过专项复审，没有发现构建配置或业务回归缺陷：API 37.0、
AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0 与 JDK 17 的组合符合官方兼容表；
AGP 9 built-in Kotlin 迁移、Compose compiler／serialization plugin 2.2.10 配对以及
Debug／Release 双变体测试配置均合理且范围最小。

P1-03 整体暂不通过，须完成 P1-03-R1 后再复审：本轮没有 API 37 设备安装与启动证据，
且现有说明没有记录 `targetSdk` 37 对应 Android 17 行为变化与当前骨架的适用性检查。
前者是授权任务的明确设备门槛；后者来自 D02 对目标版本行为变化的要求。两项都属于
验收证据缺口，不构成已发现的应用代码缺陷。P1-03、P1 和用户验收均未完成，不进入 P2。

## 提交范围

`git show --name-only 7d34c78^..7d34c78` 与执行报告一致，共 14 个文件：

- 8 个 Android 工具链、Wrapper、README 和 APK 检查脚本文件；
- 1 个 API 37 模拟器错误证据；
- 3 个 Android 交接／验证文档；
- 2 个文档与跨端探针脚本。

提交没有修改 `Android/app/src/main/**/*.kt`、iOS、Web、共享 schema／fixtures、未知字段
策略、重复 ID 或节次顺序，也没有进入 P2。`gradle-wrapper.jar` 已用 Gradle 9.6.0
在临时空工程中重新生成并逐字节比较，SHA-256 均为
`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`。

## 配置与官方依据核对

- [AGP 9.4 发布说明](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
  给出的兼容组合为最高 API 37、Gradle 9.6.0、Build Tools 36.0.0、JDK 17；提交与之相符。
- [built-in Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)
  要求 AGP 9 工程移除 `org.jetbrains.kotlin.android`；提交已经移除并使用新的 `kotlin {}` DSL。
- [AGP 9.0 发布说明](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
  记录 AGP 9 的运行时 KGP 2.2.10，以及
  `android.onlyEnableUnitTestForTheTestedBuildType` 默认从 `false` 改为 `true`。工程为了继续
  生成 Debug／Release 两个 JVM 测试任务而显式设为 `false`，只改变测试任务生成范围，
  没有改变应用运行行为。
- [Compose compiler 设置说明](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
  要求 Compose compiler plugin 与 Kotlin 版本匹配。Compose compiler 和 serialization
  plugin 均为 2.2.10；依赖解析确认 AGP 的 KGP 为 2.2.10，应用运行时
  `kotlin-stdlib` 也解析为 2.2.10。`./gradlew --version` 显示的 Kotlin 2.3.21 是 Gradle
  自身嵌入版本，不是本工程的 KGP。
- Compose BOM 2024.12.01、kotlinx serialization JSON 1.7.3 和其他应用依赖未升级，
  符合授权中的最小变更边界。

## 独立验证

复审使用 Microsoft OpenJDK 17.0.20.1 和临时 SDK
`/tmp/qingke-api37-sdk-iJNEtV`。SDK 实际包含 `platforms;android-37.0` revision 2、
`build-tools;36.0.0`、Platform Tools 37.0.1、Emulator 37.1.11，以及
`system-images;android-37.0;google_apis;arm64-v8a` revision 6。

| 检查 | 独立结果 |
| --- | --- |
| `./gradlew --version` | Gradle 9.6.0；Launcher／Daemon JVM 为 Microsoft JDK 17.0.20.1 |
| 在线 clean Debug／Release 构建和双变体测试 | 通过；100 个任务执行；Debug／Release 各 20 项，失败／错误／跳过均为 0 |
| 缓存就绪后的 `--offline` clean 构建和双变体测试 | 通过；100 个任务执行；双变体各 20 项 |
| `./gradlew lintDebug --rerun-tasks --no-daemon --console=plain` | 通过；29 个任务执行 |
| `ANDROID_HOME=<临时 SDK> bash Android/scripts/p1-02-apk-check.sh` | 通过；包名 `com.qingke.schedule`、minSdk 26、targetSdk 37 |
| `python3 docs/tests/android-contract-review-probe.py` | 16 例实际执行并逐项核对；结果与执行记录一致，未知字段差异保持未决 |
| 文档与布局测试 | `android-documentation.test.py` 17 项、`documentation.test.sh`、`repository-layout.test.sh` 均通过 |
| 空白与范围检查 | 候选提交 `git diff --check` 通过；复验完成后工作区仍干净 |

Debug／Release APK、双变体测试报告和 lint 报告均已生成。构建只有既有
`libandroidx.graphics.path.so` 无法 strip、按原样打包提示，不影响结果。本轮未运行
Windows 或远端 CI；它们不是当前授权的独立门槛。执行者的完整构建控制台临时日志未
提交到仓库，本轮没有依赖这些临时日志，而是独立重跑并读取重新生成的 XML／HTML 报告。

## 设备阻塞复核

复审再次用同一 API 37 ARM64 system image 启动独立 AVD。模拟器识别了正确镜像，但
输出 `hvf is not enabled on this aarch64 host`，随后反复出现
`qemu_mprotect__osdep: mprotect failed: Permission denied`；ADB 在 30 秒内持续为
`offline`，`sys.boot_completed` 没有返回 1。SDK 35 与 SDK 37 所用 Emulator 37.1.11
及 QEMU 二进制哈希一致，`emulator -accel-check` 又报告宿主支持 Hypervisor.Framework；
这些现象支持“当前进程环境无法使用所需虚拟化／内存保护能力”的判断，与执行者保存的
最小错误证据一致。

因此该阻塞被准确界定为当前审查环境限制，不能据此认定 APK 有启动缺陷；但环境限制也
不能替代设备验收。未执行的项目仍是：API 37 设备开机完成、APK 安装、两次冷启动、
Activity／进程检查和清空后无崩溃 logcat。历史 API 35 证据没有被用作本轮证据。

## P1-03-R1 修正要求

1. 在能正常完成 API 37 开机的 ARM64 模拟器或等效 API 37 设备上，记录设备 API、ABI
   与 `sys.boot_completed`；安装从待审提交或其纯文档后继提交构建的 debug APK。
2. 清空相关 logcat 后执行两次 `force-stop` 冷启动，确认 `MainActivity` 可见、应用进程
   存在，并保存无应用崩溃的 logcat 与必要截图／命令输出。若发现实际崩溃，先回传复现，
   不在证据任务中扩展修改应用代码。
3. 依据 Android Developers 的
   [Android 17 全部应用行为变化](https://developer.android.com/about/versions/17/behavior-changes-all)、
   [以 Android 17 为目标的行为变化](https://developer.android.com/about/versions/17/behavior-changes-17)
   和[迁移说明](https://developer.android.com/about/versions/17/migration)，逐项判断与当前
   P1 骨架是否相关，并在 `Android/README.md` 与 P1-03 验证记录中留下简明结论；不能用
   “能构建”替代目标版本运行行为检查。
4. 只维护 P1-03 设备证据、README、验证／交接／审查文档及对应文档测试；不修改应用
   Kotlin 源码，不进入 P2，不扩展 D01／D03／D04。验证通过后创建独立提交并推送
   `Android`，再申请 P1-03-R1 复审。

## 2026-09-08 P1-03-R1 独立复审

复审范围为 `3ab9d4d^..3ab9d4d`，基准为
`9f60df0d723fc0e5282906901f434018f6bbcd5a`。实际只修改任务报告所列的 5 个 README、
验证／交接／证据和文档测试文件；没有修改应用 Kotlin 源码、构建配置、依赖、Wrapper、
iOS、Web、共享 schema／fixtures 或 P2 内容。提交在 `Android` 分支，复审开始时本地
HEAD 与 `origin/Android` 均为
`3ab9d4dc3e26fedd9f3aaec08e282549921864da`，工作区干净。

### 结论

R1 对设备失败的记录通过复审：它明确区分“ADB 短暂进入 device 并能读取 API／ABI”与
“系统完成开机”，没有把空的 `sys.boot_completed`、未安装 APK 或未运行冷启动写成通过，
也没有据此声称应用存在缺陷。复审环境再次启动同一 API 37 ARM64 AVD，仍出现
`hvf is not enabled` 和反复的 `qemu_mprotect__osdep: mprotect failed: Permission denied`；
30 秒内 ADB 持续 `offline`，所以本轮同样不能补做设备验收。

Android 17 表格覆盖了官方“全部应用”和“target 37”页面当前列出的行为变化，且多数
功能级“未使用／后续复核”判断与 manifest 及项目源码一致。不过有两处表述会过度缩小
当前运行时范围，故行为适用性记录尚不能通过，需 P1-03-R2 修正文档并继续补设备证据：

1. `p1-03-validation.md` 写“当前源码范围仅为 MainActivity”，但仓库还包含已构建进 APK
   的领域、校验和 JSON 解码源码。它们没有调用相关 Android 平台能力，但不能从源码范围
   中删除；应改为“当前 Android 平台界面入口／平台能力实现仅有 MainActivity”，并说明
   领域与解码代码已检查、未使用这些平台 API。
2. README 写当前骨架“没有 JNI／动态代码”，表格又把 `MessageQueue` 风险推迟到“第三方
   SDK 引入时”。实际 APK 已含 Compose／AndroidX 等依赖，并打包
   `lib/*/libandroidx.graphics.path.so`；第三方库已经存在。没有项目自编写的 JNI、
   `System.load()` 或 `MessageQueue` 私有反射可以成立，但不能据此排除依赖层的
   MessageQueue、static final 反射或原生库运行风险。官方迁移说明也要求在 Android 17
   设备上测试现有库与 SDK。应把这些项标为“项目源码未发现高风险用法，依赖层仍待 API 37
   安装启动与 logcat 验证”，并删除“大屏是唯一直接相关变化”的绝对说法。

以上是文档准确性和证据边界问题，没有发现需要修改应用代码的缺陷。API 37 设备仍未
完成 `sys.boot_completed=1`、APK 安装、两次冷启动、Activity／进程、截图及无崩溃
logcat，因此 P1-03-R1、P1-03、P1 和用户验收均未通过，不进入 P2。

### 独立验证

| 检查 | 结果 |
| --- | --- |
| `git show 3ab9d4d^..3ab9d4d` 与受保护路径 diff | 5 个获准文件；应用源码／构建配置／iOS／Web／共享协议无差异 |
| `./gradlew assembleDebug --rerun-tasks --no-daemon --console=plain` | 通过；38 个任务执行；只有既有 `libandroidx.graphics.path.so` 无法 strip 提示 |
| `bash Android/scripts/p1-02-apk-check.sh` | 通过；`com.qingke.schedule`、minSdk 26、targetSdk 37 |
| Android 17 官方页面逐项复核 | 行为标题覆盖完整；发现依赖层范围表述过窄，不接受为最终适用性结论 |
| API 37 ARM64 AVD | 复审环境仍受 HVF／QEMU 权限限制，ADB 持续 offline；设备检查未运行 |
| 文档、布局及空白检查 | `android-documentation.test.py` 19 项、两个 shell 测试和各项 `git diff --check` 均通过 |

### P1-03-R2 修正要求

1. 只修正 `Android/README.md` 和 `docs/Android/p1-03-validation.md` 中上述源码／依赖边界；
   保持行为变化清单、设备失败事实和“未运行”结论，不修改应用代码或构建配置。
2. 在可正常启动的 API 37 ARM64 模拟器或等效 API 37 设备继续补齐原设备门槛。必须取得
   `sys.boot_completed=1`，再安装当前 Debug APK，清空应用相关 logcat，并完成两次
   `force-stop` 后显式启动；保存 MainActivity resumed／可见、`pidof`、截图和无应用崩溃
   证据。若仍无可用设备，如实记录新的环境结果，不宣称 P1-03 通过。
3. 更新交接和对应文档测试，只提交本任务文件并推送 `Android`。完成后再次申请专项复审；
   不进入 P2，不扩展 D01／D03／D04，不修改未知字段、重复 ID 或节次顺序。
