# P1-03 API 37 工具链升级专项复审

复审日期：2026-09-08。角色：分析审查；未启动子 Agent，未修改应用代码。
复审对象为提交 `7d34c78b77462178ce2119c2fb21952ce187d18b`，提交范围
`7d34c78^..7d34c78`；开始基准 `ec236b9`、授权提交 `274f3b9` 均是其祖先。

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
