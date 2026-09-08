# 安卓项目当前交接状态

## P1-04 未知字段决定与实施授权（最新，2026-09-08）

用户已确认版本 1 课表 JSON 的顶层、semester、period、course 和 course schedule 对象
均严格拒绝协议未声明字段。共享 schema 和 Android 当前实现已经严格；iOS
`ScheduleDataTransfer.previewImport` 因默认 `JSONDecoder` 行为仍会忽略未知字段，需在
P1-04 统一。这样可以避免未知数据被静默接受后在再次导出时无提示丢失。

P1-04 已获实施授权，基准为本次决定文档提交，分支继续使用 `Android`。允许修改必要的
iOS 导入源码和测试、跨端探针以及本任务文档／验证；不修改 Android 业务行为、共享
schema 字段集合、重复 ID、节次顺序、D01／D03 或 UI，不进入 P2。详细范围与验收见
[P1-04 任务记录](p1-04-unknown-fields.md)。该任务涉及跨端协议，实施后必须独立复审；
通过后再单独核对 P1 是否关闭，并安排将修正同步到 `IOS` 分支。

## P1-03-R4 最新专项复审状态（优先于下方历史，2026-09-08）

用户已授权继续在本机修复项目所需模拟器环境。基准 `4ee8919`，分支 `Android`，
开始工作区干净；本轮只维护文档和证据，不修改应用代码，不启动子 Agent。
已定位 AVD 登记 `target=android-0` 导致误识别 API 3、HVF 未启用；修正为
`target=android-37` 后，原稳定版 Emulator 37.1.11.0 成功完成 API 37 ARM64 开机。
安装 APK、两次冷启动、Activity／进程、截图和清空后无崩溃／ANR logcat 已实测，
关闭并带窗口再次开机也成功。此前“必须换真机或宿主”的限制已解除。

**运行证据已补齐并通过独立专项复审，P1-03 授权范围完成；P1 阶段关闭与用户验收
不在本次结论内，不进入 P2。**
详见 [R4 修复和运行记录](p1-03-validation.md) 的首节；下方旧设备阻塞和换宿主建议
仅作历史。未知字段策略现已确认并纳入 P1-04；D01／D03 和正式发行决定不变。本轮构建 70 项 up-to-date，
读取既有双变体各 20 项报告，不冒充重新执行 JVM 测试。
系统开发者模式按用户授权已开启，但单独开启未解决问题；未证明其必要性。
预览版仅隔离保留，实际成功使用稳定版，未修改项目依赖。
独立复审用临时只读登记副本重现 `android-0` 的 API 3／HVF／`mprotect` 失败，并用当前
`android-37` 登记在端口 5588 再次完成 API 37 开机、安装和冷启动；70 个 Gradle 任务
通过 `--rerun-tasks` 实际执行，双变体各 20 项测试通过。详见
[R4 独立证据](evidence/p1-03-r4-review-20260908.txt)。无需 Terra 继续排查环境；未知字段
已决定为两端严格拒绝，下一项为 P1-04，本轮不实施 P2。本次审查提交从交付消息或 git log 查询。


## 当前流程更新（优先于历史配置）

2026-09-08 用户确认试行“执行为主、分析按需、关键点审查”，规则见根目录 AGENTS.md 和实施计划。普通任务由执行窗口完成分析、实现、测试、交接状态维护、提交和推送；低风险任务不强制独立审查。复杂实施可直接建议 Sol／高，Astra 按需；分析窗口仍不修改应用代码。先观察 3–5 个实际任务的返工、交接及可获得的用量，再调整，不承诺最省。官方和中转站共用规则，用户只复制必要的完整交接块。

P1-03 的历史过程包括实施提交 `7d34c78`、R2 文档修正 `2e5b8ec` 和 R3 环境失败记录 `23743be`；这些步骤当时仍缺 API 37 设备证据。此后 R4 已定位 AVD 登记问题并补齐设备运行证据，最新结论以本文首节和 [P1-03 专项复审](p1-03-review.md) 为准。

## 当前分支安排（优先于下方历史记录）

2026-09-08 用户确认使用 `Android`、`IOS` 两条开发分支和 `main` 稳定分支。当前工作目录留在 `Android`，所有安卓窗口接手先核对这一分支。旧 `codex/ios-ui-redesign-demo` 保留在 `8791dbe` 作为历史，不再用于日常开发。

建分支前已确认工作区干净、远程没有同名新分支；`git diff --exit-code bef808b HEAD -- ios` 通过，当前 iOS 文件与最后 iOS 提交一致。两个新分支从包含本轮规则的同一完整快照起步，均保留全部目录和历史，避免 iOS 分支丢失新的协作／同步规则。分支规则提交 `c11bd2b` 已上传 `origin/Android`；此操作不代表任一应用新增验收通过。

本轮新增分支规则验证；文档测试和远程核对结果在最终交付报告。后续每次提交推送对应开发分支，稳定阶段合并 `main` 需明确安排。下方旧分支名与旧推送范围只作历史，不作为当前操作指令。

## GitHub 同步交接（历史）

2026-09-08 用户确认正式仓库为 [QingKeSchedule](https://github.com/SukiBanQin/QingKeSchedule)，常规交付需要提交并推送同名任务分支。当前分支 `codex/ios-ui-redesign-demo`，原 `origin` 指向旧名 `School_timetable`，本轮更新为指定仓库（保留 SSH 443 传输方式）。

同步前本地 HEAD 为 `e4df5fb`；远程仅有 `main`，头提交 `552c045`，与本地比较为远程独有 1、本地独有 67 个提交。因此本轮上传开发分支，不覆盖或合并 `main`，不强制推送。新增开发分支的实际推送结果及本轮提交编号见最终交付消息，接手时用 `git ls-remote origin refs/heads/codex/ios-ui-redesign-demo` 核对，不把上传计划当作已成功。

本轮仅修改同步规则及文档验证，未重新验收应用。文档验证结果在交付时报告。

## 本轮协作配置更新

2026-09-08 用户确认：默认分析审查模型改为 Sol（日常中、正式审查高），执行仍默认 Terra；Astra 仅作为疑难问题升级选项。窗口和交接按角色命名，具体建议见实施计划。本轮仅维护文档与对应验证，不修改应用代码、不重做无关历史审查，不切换实际模型设置。当前实际窗口模型和档位未核实。P1-01-R2 实施提交 `985cdd6` 已由分析审查窗口独立复核通过；下方上一轮“待执行／待审查 R2”的描述属于历史，勿重复派发。旧审查记录里的 Astra 名称保留作历史，不要求接手者继续使用 Astra。

本轮文档验证：文档测试 16 项、既有文档和布局测试、工作区与暂存补丁检查通过。P1-01-R2 有独立应用验证；D02 授权只代表允许开始 P1-03，不等于升级已经完成、整个 P1 通过或用户验收通过。

## 最近更新与阅读入口

更新时间：2026-09-08。P1-01-R2 与 P1-03-R4 均已通过独立复审；API 37 模拟器环境阻塞已解除。用户已确认版本 1 未知字段两端严格拒绝，下一步为 P1-04；P1-04 通过后再核对 P1 是否关闭，不进入 P2，用户未验收。详见 [P1-04 任务记录](p1-04-unknown-fields.md)。

先读 [根目录规则](../../AGENTS.md)，再按任务阅读 [功能对照](product-baseline.md)、[技术方案](technical-design.md)、[实施计划](implementation-plan.md)。接手时检查 `git status --short`、`git branch --show-current` 和 `git log -5 --oneline`，不能只信文档中的状态。

## 已确认决定

- Android 首版复现当前 iOS 已有功能、业务行为和视觉风格，系统交互按安卓方式实现。
- `Android/` 存放安卓代码，`docs/Android/` 存放本文档，保持大小写一致。
- 官方／中转站各一个分析审查窗口（默认 Sol）和执行窗口（默认 Terra），采用人工提示词交接；Luna 按需增加，暂不启用自动委派。
- 分析窗口不修改应用代码；本轮用户明确要求写文档，因此允许文档和相关验证改动。
- 延续每次改动更新测试、验证并创建 Git commit 的仓库要求。
- 用户已确认直接交接：模型最终回复末尾给出唯一一段完整中文交接块，标明接收窗口和建议档位；用户只复制，不查找提示词或整理结果。执行窗口默认维护任务状态，分析审查窗口在需要审查时维护结论，具体任务的更窄授权优先。

## 建议与待决定事项

- Kotlin + Compose、Room、DataStore 和 Mac 主力开发是已提出的建议；本轮确认 P1 采用 Kotlin + Compose 单 app 工程，Room/DataStore 仍按阶段计划在 P2 接入。
- D01：是否让备份携带教学日历设置并同步扩展 iOS 协议；现有版本 1 不携带这些设置。
- D02：已确认包名 `com.qingke.schedule`、最低 API 26。P1-03 已采用 API 37.0 + AGP 9.4.0 + Gradle 9.6.0 + Build Tools 36.0.0 + JDK 17；保持 minSdk 26，工具链、Android 17 适用性和 API 37 设备运行门槛均已通过专项复审。API 37.1／37.2 没有本轮核实的完整官方兼容映射，不纳入此次升级。
- D03：精确提醒不可用时的降级方式与文案尚未确定。
- D04：已确认 P1 仅做个人安装验证和可复现 debug 构建，不配置商店签名；正式发布范围仍未确定。
- 用户已确认版本 1 未知字段两端严格拒绝。下一步为 P1-04 跨端协议修正，建议执行窗口使用 Sol／高；任务涉及 Swift 导入边界、递归对象键和跨端探针，完成后必须独立复审。实际窗口模型和档位未核实，不要替用户变更设置。

## 当前代码与既有改动

初始 iOS 参考提交：`dabdc2eae41143b1288ae5f9ba5eabcebce6de5a`。本次文档提交另从交付消息或 Git 历史查询，不使用自引用提交编号。

开始建档时已有以下未提交文件，后已按用户授权独立提交：

- `ios/QingKeSchedule.xcodeproj/project.pbxproj`
- `ios/QingKeSchedule.xcodeproj/xcshareddata/xcschemes/QingKeSchedule.xcscheme`

历史窗口在 `525910f` 后按用户授权将两处 iOS 工程配置单独提交为 `bef808b`。本轮开始 HEAD 为 `23743be`、分支为 `Android`，本地与 `origin/Android` 一致，工作区干净；相关 iOS 领域/传输与共享协议未改动。

首轮审查时已核对：分支 `codex/ios-ui-redesign-demo`，开始 HEAD `85e3234`。当时两文件为未提交改动，后由用户授权提交为 `bef808b`；pbxproj 为开发团队和包标识设置，scheme 为测试并行属性及 XML 换行。它们没有进入本轮文档暂存范围。相关 iOS 源码与共享协议自初始基准以来无提交变化。后续接手仍需重新核对现场。源码阅读不代表已执行 iOS 构建、模拟器、UI 或真机验收。

## 已完成与未完成

- 文档已由用户确认；P1-01 实施提交 `9b521db` 已逐项核对。实际父提交 `0a498b9`，前置 `f6a920a`／`0a498b9` 是交接和参数确认文档；后续 `85e3234` 是流程文档，不是实现内容。
- 已实现并复跑测试：单 app Compose 工程、教学周／单双周、最小 DTO／校验和共享 fixture 解码。未实现完整存储、状态、页面和系统能力，A01—A11 未完整验收。
- 首轮已审查，结论未通过：数值字符串被接受／整数数值表示误拒绝、非法 UTF-8 被替换后接受、年份 0000 与 iOS 不一致。具体位置、影响和复现见 [审查记录](p1-01-review.md)。
- 已确认决定：版本 1 未知字段按共享 schema 严格拒绝；Android 已符合，Swift 导入边界待 P1-04 修正。重复 ID／节次编号顺序实测两端均宽容，不自行加严。
- 修正提交 `3924d26`、`985cdd6` 及 P1-03-R4 均已通过独立复审。版本 1 未知字段策略已决定为两端严格拒绝，iOS 导入边界待 P1-04 实施和复审；P1 尚未关闭，不进入 P2。
- 本轮同步纠正功能基线和技术方案中“待确认／没有 Wrapper”等过时状态，不修改 iOS 基准或擅自扩大协议。D01、D03、正式发行和目标机型仍待后续决定。

## 验证记录

2026-09-07 首轮审查独立执行；以下应用结果属于修正前，不能作为 `3924d26` 已通过的证据。完整命令与探针结果见 [审查记录](p1-01-review.md)。

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| 默认 `./gradlew assembleDebug test --rerun-tasks` | 失败 | SDK location not found，测试未启动 |
| 设置实际临时 ANDROID_HOME 后同命令 | 通过 | 68 个任务全部执行；debug APK；debug/release 各 12 项 JVM 测试，无失败/错误/跳过 |
| `python3 docs/tests/android-contract-review-probe.py` | 已执行，发现差异 | 10 个同输入案例调用 Android decoder 和 macOS 编译的原 Swift previewImport；执行成功不代表兼容通过 |
| adb 设备清单 | 无连接设备 | 未安装或启动应用 |
| `python3 docs/tests/android-documentation.test.py` | 通过，7 项测试 | 文档结构、链接、阶段、基准和新增审查交接约束 |
| `bash docs/tests/documentation.test.sh` | 通过 | 既有文档约束回归 |
| `bash docs/tests/repository-layout.test.sh` | 通过 | 既有仓库布局回归 |
| `git diff --check`、`git diff --cached --check` | 通过 | 工作区与本任务暂存文档补丁空白检查 |

未运行：Android 安装/UI/模拟器/真机、iOS 完整构建/测试/设备、Web 测试。未做 Windows 或全新依赖缓存复现。临时 SDK 路径只用于记录此次实测，不能当作长期环境配置。文档验证通过不代表 P1-01 审查通过。

P1-02-R2 已取得 API 35 ARM64 设备安装、两次冷启动、Activity、截图和 logcat 证据。该证据不替代 P1-03 所需的 API 37 设备证据；P1-03 主机侧专项复审结果见 [独立记录](p1-03-review.md)。

### P1-01-R2 独立复审证据

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| `./gradlew clean assembleDebug test --no-daemon --console=plain`（显式配置临时 SDK/JDK 17） | 通过 | 69 个任务执行；Debug/Release 各 20 项，失败／错误／跳过均为 0 |
| `python3 docs/tests/android-contract-review-probe.py` | 通过并人工核对结果 | 16 例；四种非法数字两端均拒绝，合法指数两端均接受；未知字段差异保持未决 |
| 修复前后临时探针日志 | 存在且一致 | diff 只显示四种非法数字的 Android 结果由接受变为拒绝；临时日志不替代本轮独立探针 |
| APK 元数据检查 | 通过 | `com.qingke.schedule`、minSdk 26、targetSdk 35 |
| 本轮设备／升级工具链 | 未运行 | API 35 ARM64 启动沿用既有已复审证据；未运行 AGP 9.4、CI、Windows、iOS 全量或真机测试 |

### P1-03 独立专项复审证据

复审范围为 `7d34c78^..7d34c78`，实际 14 个文件，与执行报告一致；没有应用 Kotlin
源码、iOS、Web 或共享 schema／fixtures 改动。执行者未提交完整构建控制台日志，复审
没有把其自述当作证据，而是重新运行可用检查并读取新生成的报告。

| 检查 | 独立结果 |
| --- | --- |
| 官方兼容与依赖解析 | API 37／AGP 9.4.0／Gradle 9.6.0／Build Tools 36.0.0／JDK 17 符合兼容表；KGP、Compose compiler、serialization plugin 均为 2.2.10 |
| 在线与离线 clean 双变体构建／测试 | 均通过；各 100 个任务；Debug／Release 各 20 项，无失败／错误／跳过 |
| `lintDebug` 与 APK 元数据 | 均通过；包名正确，minSdk 26、targetSdk 37 |
| 跨端探针 | 16 例实际执行并逐项核对；结果与执行记录一致，未知字段差异保持未决 |
| API 37 ARM64 模拟器 | R2 复审用官方启动器禁快照、无窗口、软件图形后仍复现 HVF 未启用与 QEMU `mprotect` 拒绝，ADB 持续 offline；判定为当前环境限制，设备门槛仍缺证据 |
| 结论 | 工具链主机侧及 R2 源码／依赖边界通过专项复审；P1-03 整体暂不通过，下一步为 P1-03-R3 设备环境恢复与运行验证 |

完整命令、范围和修正要求见 [P1-03 专项复审](p1-03-review.md)。

## P1 实施准备记录（历史）

以下保留进入 P1 前的讨论依据；D02 和首轮 D04 的后续确认见本节末尾，旧建议不是需要重复询问的事项：

- P1 工程建议先采用 Kotlin + Compose + 单 `app` 模块，领域与 JSON 契约先不依赖 Android UI；Room、DataStore 在 P2 再接入。这样首个提交可独立验证构建、日期规则和共享协议。
- D02 建议首轮以当前可获得的 Android Studio/SDK 稳定组合为准，最低版本优先选择 API 26（Android 8.0），目标版本使用安装环境可用的最新稳定 API；包名建议暂用 `com.qingke.schedule`。这些参数影响 Gradle 配置和真机覆盖，需用户确认后锁定。
- D03 不阻塞 P1 领域与协议测试；提醒实现前建议采用“降级为系统允许的非精确提醒，并在设置页明确提示可能延迟”的方案，具体文案留待提醒阶段确认。
- D04 不阻塞 P1；P1 只产出可复现的 debug 构建和测试，不配置商店签名或发布流水线。
- D01 不阻塞 P1；P1 仅兼容现有版本 1 JSON，不修改 iOS、共享 schema 或备份协议。

已收到的 P1 参数确认：

用户已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；P1 仅做个人安装验证（debug/reproducible build），不做商店发布签名。上述决定不等同于 D01、D03 或 D04 的完整产品决定。

## 给 Terra 的第一项实施提示词（历史，不要重复执行）

```text
任务编号与阶段：P1-01，工程与规则基础
角色：执行；仅实施本次范围，不完成整个 App。
建议模型／思考档位：Terra／中（本轮补充建议，不代表此前执行实际设置）。
选择理由：涉及工程、稳定依赖和基础规则测试，需要中档起步。
目标：在 Android/ 建立可构建的 Kotlin Android 工程（Compose、单 app 模块），固定经过验证的稳定依赖；实现与 Android UI 无关的最小领域/JSON 契约骨架，覆盖教学周计算、单双周判断、版本 1 数据解码与业务校验，并接入共享 fixtures。
代码基准／当前分支：基于提交 78e362e；当前分支 codex/ios-ui-redesign-demo。开始前运行 git status --short --branch、git log -5 --oneline，保留两处既有 iOS 工程配置改动，不覆盖、不暂存、不提交它们。
必读文档与参考源码：AGENTS.md；docs/Android/handoff.md；docs/Android/product-baseline.md；docs/Android/technical-design.md；docs/Android/implementation-plan.md；ios/Shared/schedule-data.schema.json；ios/Shared/fixtures/manifest.json 及其 fixtures。
允许修改的路径：Android/**；必要的 Android 测试与构建配置仅限 Android/**。不得修改 ios/**、web/**、共享 schema/fixtures 或 docs/Android/**（交接记录由分析窗口维护）。
已确认决定及不能自行决定的差异：目标是复现 iOS；P1 不扩展 D01 协议。D02 已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；D04 当前仅限个人安装 debug 验证。不得自行扩展 D01、D03 或 D04 的范围。
验收条件：./gradlew assembleDebug 可复现通过；./gradlew test 通过；JVM 测试覆盖教学周边界、单双周、版本/必填字段/颜色/日期时间/课程安排校验、semester:null，以及有效和无效共享 fixtures；未知版本、非法业务数据和超出输入上限按文档拒绝。提供实际命令与输出摘要。
交付：修改摘要、测试文件和结果、构建命令和结果、提交编号、未完成项/限制、下一步建议。只暂存 Android/** 并创建一个独立 Git commit；完成后把提交号和证据回传，并说明交接记录待分析窗口更新。
```

## 下一步

P1-03-R4 已通过专项复审，P1-03 无后续修正任务。用户已决定两端严格拒绝版本 1
未知字段；下一步由执行窗口完成 P1-04，并回传独立复审。P1-04 通过后再单独核对 P1 阶段
是否关闭。本轮没有 P2 授权，不自动派发或实施 P2。

### P1-03 已授权范围（历史，已实施）

- 目标组合：`compileSdk`／`targetSdk` 37（API 37.0）、AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0、JDK 17；`minSdk` 26、包名和 namespace 保持不变。
- 处理 AGP 9 built-in Kotlin 迁移，按官方迁移方式调整 `org.jetbrains.kotlin.android`；保留并实测 Kotlin serialization 与 `org.jetbrains.kotlin.plugin.compose`。插件版本必须与 AGP 内置 Kotlin 实际兼容，不能只保留 2.0.21 后假定成功。
- Compose BOM 2024.12.01 和其他 AndroidX 依赖默认保持；只有构建或运行证据证明必须升级时才做最小调整并说明依据。不得借机修改业务规则、JSON 未知字段策略、iOS、Web、共享 schema/fixtures、Room/DataStore 或页面功能。
- 允许维护本任务 `docs/Android/handoff.md` 及必要文档验证；允许修改 Android 工具链和构建文件、Wrapper、`Android/README.md`，以及为验证真实工具链回归所必需的 Android 测试。主应用 Kotlin 源码只有在工具链编译迁移确实要求时才能最小修改，并须单独说明原因和行为不变证据。
- 验收至少包括：核对官方兼容资料与稳定 SDK 包；`./gradlew --version`；JDK 17 下 clean Debug/Release 构建和双变体 JVM 测试；一次缓存就绪后的离线 clean 构建；16 例跨端契约探针；APK 元数据确认 minSdk 26、targetSdk 37；API 37 ARM64 模拟器或等效设备安装、两次冷启动、Activity/进程和无崩溃 logcat。运行 `lintDebug` 并记录结果。
- 若 API 37 system image、AGP/Kotlin 插件或设备环境不可用，保留最小错误证据并回传，不降级目标、不把 API 35 历史证据冒充本轮通过。只提交本任务改动并推送 `Android`；执行窗口回传提交范围、完整命令、测试数量、设备信息、限制和复审请求。
- P1-03 完成后只申请复审，不进入 P2，不宣称 P1 或用户验收通过。

## 后续更新约定

每次阶段结束更新本文件的当前状态，保留决定理由及相关提交；较旧细节可从 Git 查阅，不无限追加聊天。执行者给出提交编号、测试证据、未完成项；审查者记录检查范围和发现。重要决定或切换服务前及时保存，即使任务尚未完成，也如实记录最后操作和未提交内容。


## D02 工具链兼容矩阵核查（历史任务，已完成记录）

由用户授权在 iOS 配置提交 `bef808b` 后，下一步交给 Terra 只核查稳定 SDK 与构建工具兼容组合，不修改应用代码或依赖。未知字段策略仍待产品决定。


## 2026-09-08 独立复核证据

- 实际 `55eff97` 只新增 D02 文档；后续 `5613f23`／`398fa43` 已有两次复审，本轮未把它们当作独立证据。AGP 9.4 参数核对正确；AGP 9.1.1 也支持 API 37.0，已消除“最低要求”的误解。
- `sdkmanager --list --channel=0` 退出 0，除交接列出的平台外还看到无 beta 后缀的 android-37.2；[清单节选](evidence/d02-sdk-list-20260908.txt) 已保存。37.1／37.2 的 AGP 映射未核实，未把包可见性写成项目兼容。
- 当前 API 35 工程 `assembleDebug test --rerun-tasks --console=plain` 独立通过，68 个任务执行，debug/release 各 21 项，无失败／错误／跳过。扩展 16 例跨端探针已运行，确认 4 种非法数字仍被 Android 接受；应用审查未通过。
- 本轮未做升级构建、CI、Windows、设备/iOS 全量复测；沿用已明确来源的 API 35 历史启动证据，不冒充本轮执行。
- 文档验证：`android-documentation.test.py` 11 项通过、`documentation.test.sh` 和 `repository-layout.test.sh` 通过，`git diff --check`／`git diff --cached --check` 通过。只提交本轮文档与相关验证。
