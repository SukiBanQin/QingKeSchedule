# P1-03 API 37 工具链升级专项复审

最新结论（2026-09-08）：P1-03-R4 的 AVD 根因修复、API 37 ARM64 设备证据和应用
最小启动证据已通过独立专项复审，P1-03 授权范围完成。P1 阶段关闭、未知字段策略和
用户验收不在本次结论内，不进入 P2。详见文末 R4 复审及
[独立证据](evidence/p1-03-r4-review-20260908.txt)。

复审日期：2026-09-08。角色：分析审查；未启动子 Agent，未修改应用代码。
复审对象为提交 `7d34c78b77462178ce2119c2fb21952ce187d18b`，提交范围
`7d34c78^..7d34c78`；开始基准 `ec236b9`、授权提交 `274f3b9` 均是其祖先。

最新状态以文末 P1-03-R2 独立专项复审为准：R2 的源码／依赖层边界修正和设备失败
记录通过复审，但 API 37 设备门槛仍未满足；下一步为 P1-03-R3 设备环境恢复与运行验证。

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

## 2026-09-08 P1-03-R2 独立专项复审

复审范围为 `2e5b8ec^..2e5b8ec`，基准为
`a16a52bbfba109aecc629ff19d8697ca2b1e9ca4`。实际提交与执行报告一致，只修改
`Android/README.md`、R2 设备证据、交接、P1-03 验证记录和文档测试共 5 个文件；没有
修改应用 Kotlin 源码、构建配置、依赖、Wrapper、iOS、Web、共享 schema／fixtures 或
P2 内容。复审开始时本地 `Android`、HEAD 与 `origin/Android` 均为
`2e5b8ecd4a479d2f7d4d6978fd4123f5a071b584`，工作区干净。

### 结论

R2 的文档修正范围通过专项复审。README 与验证记录已经明确区分平台入口、全部项目
自编写源码和现有 Compose／AndroidX 依赖；不再把领域／解码源码排除在项目范围外，也
没有把源码静态检查写成依赖层或 API 37 运行通过。APK 中
`lib/*/libandroidx.graphics.path.so` 的说明经独立读取构建产物确认，应用内存限制、
target 37 的新 `MessageQueue` 及依赖层运行风险仍明确保留为设备待验项。

R2 设备证据也如实记录为一次失败尝试：ADB 曾短暂进入 `device`，但
`sys.boot_completed` 为空后模拟器退出；没有读取本轮 SDK／ABI、安装 APK 或执行冷启动、
Activity／进程、截图和应用 logcat。历史 API 35 结果没有混入本轮。因此没有发现需要
继续修改 R2 文档或应用代码的问题。

P1-03 整体仍不通过。设备门槛是原授权任务的验收条件，环境失败只能说明检查无法完成，
不能替代 API 37 应用运行证据。P1、P1-03 和用户验收均未完成，不进入 P2。

### 用户截图与独立设备诊断

用户提供的 09:36 桌面截图补充了两类信息：正常模拟器窗口出现
`qemu_mprotect__osdep: mprotect failed: Permission denied`、快照读取失败和
DisplaySurfaceGL 创建失败；macOS 再打开的窗口则直接执行内部
`qemu-system-aarch64`，报告找不到 `@rpath/libandroid-emu-tracing.dylib`。复审检查确认
该动态库实际位于 Emulator 包的 `lib64` 目录，右侧错误来自绕过 `emulator` 启动器，
不能据此判定 SDK 缺包，更与 APK 无关。

复审环境中 `emulator-check accel` 返回 0 并报告 Hypervisor.Framework 可用，
`kern.hv_support=1`，QEMU 签名也包含 JIT 和 hypervisor 权限。随后使用官方 `emulator`
启动器，以 `-no-snapshot -no-window -gpu software -accel on` 排除快照和宿主图形路径后，
ADB 连续 8 次、每隔 5 秒仍为 `offline`；日志同时出现 `hvf is not enabled on this
aarch64 host` 和反复的 `mprotect failed`。达到限定时间后只终止本次端口 5560 的进程，
没有操作既有 API 35 模拟器。完整最小记录见
[R2 独立启动诊断](evidence/p1-03-r2-review-api37-attempt-20260908.txt)。

这些结果把问题进一步限定在当前宿主进程／模拟器环境，但还不足以断定是 macOS、临时
SDK 位置、Emulator 版本或启动进程权限中的哪一项。复审没有安装 APK，故仍没有应用层
崩溃或兼容失败证据。

### 独立验证

| 检查 | 结果 |
| --- | --- |
| `git show 2e5b8ec^..2e5b8ec` 与受保护路径 diff | 仅 5 个获准文件；应用源码／构建配置／iOS／Web／共享协议无差异 |
| R1 文档问题复核 | 已准确区分项目源码和现有依赖层；原两项问题均修正 |
| `./gradlew assembleDebug --rerun-tasks --no-daemon --console=plain` | 独立通过；38 个任务执行；仅有既有 native 库无法 strip 提示 |
| `bash Android/scripts/p1-02-apk-check.sh` | 独立通过；`com.qingke.schedule`、minSdk 26、targetSdk 37 |
| APK 内容 | 确认 4 个 ABI 均含 `libandroidx.graphics.path.so`，与 R2 说明一致 |
| 双变体 JVM 测试、离线构建、`lintDebug`、16 例跨端探针 | 本轮未重复；R2 未改应用或构建配置，沿用 `7d34c78` 已完成的独立专项复审证据，不写成本轮新执行 |
| API 37 ARM64 AVD | 官方启动器、禁快照、无窗口、软件图形后仍连续 8 次 `offline`；设备门槛未完成 |
| `python3 docs/tests/android-documentation.test.py` | 22 项通过 |
| 既有文档、布局与空白检查 | `documentation.test.sh`、`repository-layout.test.sh`、工作区与暂存区 `git diff --check` 均通过 |

### P1-03-R3 设备环境任务

1. 不再直接执行 `emulator/qemu/darwin-aarch64/qemu-system-aarch64`；只使用官方
   `emulator/emulator` 启动器或 Android Studio Device Manager。优先在持久 SDK 目录重新
   安装当前稳定 Emulator 与 API 37 ARM64 system image，并新建无历史快照的 AVD，避免
   临时目录和旧 AVD 状态继续混淆诊断。
2. 启动前记录 Emulator 版本、`emulator -accel-check`、宿主架构和 AVD image；先用默认
   图形启动，失败时只做 `-no-snapshot` 与官方支持的 `-gpu software` 对照。保留每种启动
   的命令、退出码和最小错误日志，不反复无界重试。
3. 只有取得 `sys.boot_completed=1` 后，才安装当前 Debug APK，清空应用相关 logcat，
   完成两次 `force-stop` 后显式启动；保存 MainActivity resumed／可见、`pidof`、截图及
   无应用崩溃 logcat。若同一 Mac 仍失败，改用等效 API 37 ARM64 真机或另一台可运行
   API 37 的宿主，不降级到 API 35 充当证据。
4. 只维护设备环境、P1-03 证据、验证／交接文档和对应文档测试；不修改应用 Kotlin、
   构建配置、依赖、iOS、Web 或共享协议，不进入 P2。完成后提交并推送 `Android`，再申请
   P1-03-R3 专项复审。

## 2026-09-08 P1-03-R3 专项复审

复审范围为 `23743be^..23743be`，实际提交 4 个文件：持久 SDK／干净 AVD 失败证据、
P1-03 验证记录、交接记录和安卓文档测试。复审开始时分支为 `Android`，本地 HEAD 与
`origin/Android` 均为 `23743bee9c49eac6f95c414780acb02f136273e4`，工作区干净；未修改应用
代码或构建配置。

### 结论

**P1-03-R3 的范围和失败证据记录通过专项复审；API 37 设备启动门槛仍未通过，P1-03、P1
和用户验收继续未完成，不进入 P2。**

- 持久 SDK、组件版本、Apple Silicon 宿主、Hypervisor.Framework 检查、独立 AVD 根目录和
  ARM64 API 37 image 均有具体记录。
- 默认图形启动和 `-no-snapshot -no-window -gpu software` 对照均限定在 30 秒内；十次
  ADB 探测均为设备不存在，进程退出。软件图形日志中的 `hvf is not enabled on this
  aarch64 host` 与 `qemu_mprotect__osdep: mprotect failed: Permission denied` 被原样记录，
  没有把它们解释成 APK 或业务代码错误。
- 既有 API 35 `emulator-5554` 未被操作；本轮没有伪造 API/ABI、安装、启动、Activity、进程、
  截图或应用崩溃证据，也没有用 API 35 历史结果替代 API 37 验收。
- 独立复跑 `android-documentation.test.py` 24 项、既有文档和布局检查、`git diff --check`
  及本地/远端 HEAD 一致性均通过。应用 Gradle 测试本轮未重跑，符合本次仅改文档和环境证据
  的范围；既有构建证据仍按原记录引用。

### 剩余门槛

需要 API 37 ARM64 真机或另一台可正常完成 API 37 开机的宿主，取得
`sys.boot_completed=1` 后再安装当前 Debug APK，完成两次 `force-stop` 后冷启动、
MainActivity／进程检查、截图和清空后的无崩溃 `logcat`。在此之前不关闭 P1-03，不进入 P2。
D02 最新稳定 SDK 组合和未知字段策略仍保持未决。

## 2026-09-08 P1-03-R4 独立专项复审

复审范围为 `729fbaa^..729fbaa`，基准为 `4ee8919`。实际提交共 15 个文件：4 份
P1-03／交接／实施计划文档、1 份文档测试，以及 R4 证据目录中的 10 个文本或 PNG 文件。
没有修改 Android 应用源码、构建配置、依赖、Wrapper、iOS、Web、共享 schema／fixtures、
业务策略或 P2 内容。复审开始时本地 `Android`、HEAD 与 `origin/Android` 均为
`729fbaa5fda460c547d2df47e38ec111a2dc2a15`，工作区干净。

### 结论

**P1-03-R4 通过独立专项复审，P1-03 的工具链升级与 API 37 设备运行门槛完成。**

AVD 根因可独立复现：修正前备份和当前登记只有 `target=android-0` 改为
`target=android-37` 一行差异。复审在临时登记目录中把只读副本恢复为 `android-0`，同一
SDK／AVD 再次被识别为 API 3，不启用 HVF 并出现 `mprotect failed`；当前登记则在独立
端口被识别为 API 37，QEMU 带 `-enable-hvf`，约 13 秒完成启动。这个受控对照支持
“错误 target 登记导致此前本机失败”的结论。错误登记最初由哪个命令产生仍未复现，文档
没有擅自归因为官方缺陷，边界正确。

执行提交的设备证据互相一致：APK SHA-256 与当前产物相同，两次冷启动均为
`Status: ok`／`LaunchState: COLD`，PID 分别为 4283 和 4341；两份 Activity 输出均显示
MainActivity resumed／visible。两张有效的 1080×1920 PNG 都显示“轻课”骨架，且验证
记录如实指出文字贴近状态栏、未进行完整视觉或安全区域验收。清空后的 logcat 没有匹配
应用 FATAL EXCEPTION、ANR、am_crash、am_anr 或 Fatal signal，但保留了模拟器图形权限
警告，没有夸大成“无任何警告”。

复审开始时 `emulator-5586` 已不在线，因此没有沿用执行者所述“当前可见窗口”。复审用
当前登记在端口 5588 再次启动，约 16 秒取得 `sys.boot_completed=1`，实测 API 37、
`arm64-v8a`；重新安装同一 APK 后，独立冷启动为 `Status: ok`／`LaunchState: COLD`，
MainActivity resumed／visible，PID 为 3155，独立截图显示“轻课”，清空后的 logcat 同样
没有匹配应用崩溃或 ANR。复验后已关闭端口 5588 的模拟器。

### 独立验证

| 检查 | 结果 |
| --- | --- |
| `git show 729fbaa^..729fbaa` 与受保护路径 diff | 15 个获准文档／证据文件；应用源码、构建配置、iOS、Web、共享协议无差异 |
| AVD 登记与受控对照 | `android-0` 可复现 API 3／HVF 关闭／`mprotect`；`android-37` 可复现 API 37／`-enable-hvf`／正常开机 |
| 提交设备证据 | API 37、ARM64、安装成功、两次不同 PID 冷启动、resumed／visible、两张截图及无应用崩溃／ANR logcat 一致 |
| 审查侧设备复验 | 端口 5588 独立开机、安装与一次冷启动通过；Activity、PID、截图和清空后 logcat 通过 |
| `./gradlew assembleDebug test --rerun-tasks --no-daemon --console=plain` | 通过；70 个任务实际执行；Debug／Release 各 20 项，失败／错误／跳过均为 0 |
| APK 元数据 | 通过；`com.qingke.schedule`、minSdk 26、targetSdk 37，SHA-256 与提交证据一致 |
| `android-documentation.test.py` | 通过；25 项 |
| `documentation.test.sh`、`repository-layout.test.sh` | 通过 |
| `git diff --check` | 通过 |

### 限制与后续边界

- 本结论只覆盖 P1-03 已授权的工具链、Android 17 静态适用性说明和 API 37 最小设备
  运行门槛，不把“轻课”骨架启动扩大为 A01—A11、完整页面、存储、通知、多尺寸、内存
  压力或视觉／安全区域验收。
- 系统开发者模式仍保持用户授权的开启状态；它单独未解决问题，也未证明为必要条件。
  隔离的 Emulator 37.2.7 预览版没有参与成功证据，项目仍使用稳定版 37.1.11.0。
- P1-03 可以关闭；P1 阶段和用户验收不随本次专项审查自动完成。未知字段 Android 严格、
  Swift 宽容的差异仍需产品决定。本轮没有 P2 授权，不生成或执行 P2 任务。
