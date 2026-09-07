# P1-01 实施审查与修正任务

审查日期：2026-09-07。角色：专职分析与审查；未修改应用代码，未启动子 Agent。建议配置 Astra／中，实际窗口模型、档位及服务商未核实。

首轮结论（历史；最新结论见末尾复审记录）：**审查未通过，返回 P1-01-R1；P1 未完成，未进入 P2，用户未验收。** 构建及现有测试通过，但额外跨端检查发现输入校验缺陷及未决兼容差异。

## 范围与基准

- 原始任务以 [交接中的 P1-01 提示词](handoff.md) 为准，是工程、教学周／单双周、版本 1 解码和业务校验的最小基础，不是完整领域层或整个 App。
- 实施范围：`9b521db^..9b521db`，实际父提交 `0a498b9`。原提示词写 `78e362e`，中间 `f6a920a`、`0a498b9` 仅补充实施交接及已确认参数，没有其他应用实施提交。
- 候选 `9b521db` 只新增 `Android/` 下 20 个文件，包含 Wrapper/JAR、构建配置、Manifest/资源、Compose 入口、领域及解码实现、3 个测试类。已阅读完整文本 diff，核对 Wrapper 配置和执行权限，并通过实际构建验证 Wrapper/JAR 可执行；未作独立二进制供应链审计。
- 后续 `85e3234` 仅改 `AGENTS.md`、交接、计划和文档测试，未纳入实施范围。审查开始 HEAD 为该提交，分支 `codex/ios-ui-redesign-demo`。
- iOS 初始基准沿用交接中的 `dabdc2e`；其后至 `85e3234` 的 `ios/Shared`、`ios/QingKeSchedule/Domain`、`ios/QingKeSchedule/Transfer` 无提交差异。
- 两处原有未提交改动仍在：`project.pbxproj` 增加开发团队及调整 Debug/Release Bundle Identifier；`QingKeSchedule.xcscheme` 移除一个 TestableReference 的 `parallelizable="NO"` 并调整 XML 换行。均保留，不纳入审查文档提交。不据此判断这些配置正确或 iOS 测试通过。

## 实现核对

| 项目 | 结果与边界 |
| --- | --- |
| 工程 | 单 app、Kotlin/Compose、包名 `com.qingke.schedule`、minSdk 26，Java/Kotlin JVM 17；固定 AGP 8.8.2、Gradle 8.10.2、Kotlin 2.0.21、Compose BOM 2024.12.01、serialization 1.7.3。MainActivity 只是文本入口，符合最小骨架范围 |
| 教学周／单双周 | LocalDate 和周一对齐，与 iOS 公历本地日期语义对应；保持学期外周数；安排先筛范围再筛教学周奇偶。已有周一／周日、学期前后、跨年测试；未单独测试时区到 LocalDate 的系统适配，当前亦未实现该适配 |
| DTO／普通业务校验 | 原字段名、必填 semester 可显式 null、无学期无课程、1–52 周、1–20 节、非空 ID/名称、颜色、节次时间和安排范围基本对应 Swift；日期和解码边界存在下列问题 |
| 输入上限 | 字节和 InputStream 两入口拒绝超过 5 × 1,048,576 字节；流有界累积，未依赖文件大小元数据。恰好上限且内容有效、分块流等仍应补测 |
| 共享 fixtures | 直接读取共享 manifest，3 个有效／7 个无效样例全覆盖，并检查清单与目录一致。有效样例仅断言 schemaVersion，缺少完整字段保真和往返断言 |
| 其他领域功能 | 今日发生/排序/分钟状态、冲突、教学日历和通知规划尚未实现；不能因此宣称 A02/A03/A05/A07/A08 完成，也不能把这些全算作 P1-01 漏做。按后续依赖拆分任务 |

## 阻塞发现

### R1：整数类型边界与 iOS／协议不一致（优先级 P1）

位置：[ScheduleDataDecoder.kt](../../Android/app/src/main/java/com/qingke/schedule/transfer/ScheduleDataDecoder.kt) 第 44、49 行及 DTO 的所有 Int 字段。

对有效 fixture 将 `schemaVersion` 改为字符串 `"1"`，或将 `semester.totalWeeks` 改为 `"18"`，安卓均接受，iOS `previewImport` 均拒绝。`intOrNull` 和当前树解码路径没有保证 JSON token 为数值；`isLenient=false` 不能代替字段类型验证。这允许协议非法数据进入领域层。反向把版本写为数值 `1.0`，iOS 接受，安卓却报 UnsupportedSchemaVersion；数学上为整数的 JSON 数值不应误判未知版本。

修正：所有整数位置统一检查数值 token、精确整数值及范围，拒绝字符串、布尔、小数非整数和溢出，不使用截断或有精度损失的转换；补测 `1`、`1.0`、指数记法、超大数及嵌套字段。整数数值表示按 Swift 实测兼容；如进一步遇到 schema 与 Swift 的真实矛盾，记录后交回分析窗口。

### R2：非法 UTF-8 被静默替换并接受（优先级 P1）

位置：[ScheduleDataDecoder.kt](../../Android/app/src/main/java/com/qingke/schedule/transfer/ScheduleDataDecoder.kt) 第 38 行。

把有效 fixture 学期 ID 字符串中的内容替换成字节 `0xFF`：安卓接受，iOS 拒绝。`decodeToString()` 默认替换非法序列，会把损坏 ID 或课程文字改写成替代字符后导入。

修正：采用严格 UTF-8 解码，失败映射为既有明确导入错误；字节和流入口都补损坏单字节、截断多字节及合法中文测试，确保不静默改写。

### R3：年份 0000 被安卓接受（优先级 P2）

位置：[ScheduleRules.kt](../../Android/app/src/main/java/com/qingke/schedule/domain/ScheduleRules.kt) 第 27–30 行。

`startDate="0000-01-01"` 在 Java LocalDate 中有效，但 iOS 基准本地日期校验拒绝，当前安卓随之接受 iOS 无法导入的学期。修正本地日期输入校验以拒绝年份 0000，保留正常闰年／无效日期边界；不扩展到修改 iOS。其他极早历史日期的历法差异尚未全面探测，不声称日期域已穷尽验证。

## 兼容差异与待验证项

1. **未知字段决定待确认。** 第 27 行 `ignoreUnknownKeys=false` 在顶层和嵌套拒绝未知字段，符合 schema 的 `additionalProperties:false`，但 iOS 实际接受。原任务未授权选择冲突行为；已有测试把拒绝固定下来，不等于用户决定。建议严格 schema 并明确跨端容忍度差异，这是待审阅建议，不能视为已确认。R1/R2/R3 可先修，Terra 不自行切换策略或修改共享 schema/iOS。
2. **重复 ID／节次顺序已实测。** 重复一门课程（连同其课程/安排 ID）两端均接受；保留时间递增、反转节次编号，两端均接受。Swift 和 Android 当前都不检查全局 ID 唯一或编号递增；不可把建议加严当成既定规则。后续存储需专门设计并验证，当前探针不等于存储无风险。
3. **SDK 选择证据不足。** 当前临时 SDK 只装 android-35，compileSdk/targetSdk 均为 35；这证明本机已安装组合可构建，不能证明落实了“环境可用的最新稳定版本”的选型核查。官方 [AGP 8.8 兼容说明](https://developer.android.com/build/releases/agp-8-8-0-release-notes?hl=en) 说明其最高支持 API 35；[Android 16 官方页面](https://developer.android.com/about/versions/16) 已有 SDK 获取入口。需执行者提供稳定渠道可用 SDK/工具兼容矩阵和选择理由，不能仅以自己安装了 35 为证。本审查未运行远程 sdkmanager 清单，也未决定升级到具体 API；不要求把所有依赖更新到最新。
4. **缺少可持久复现说明。** 未提交个人绝对路径是正确的，但仓库无 Android 构建 README。默认环境没有 ANDROID_HOME/ANDROID_SDK_ROOT/local.properties，直接构建失败；临时目录可能被清理。需提供通用 JDK/SDK 安装及路径配置、稳定依赖依据、构建/测试命令与结果位置。
5. **安装／启动未验证。** 本轮 adb 设备列表为空，没有模拟器或真机启动证据。P1 阶段写明“可构建启动”，故即便 P1-01 修正通过也不能直接跳 P2。下一次应先收齐 P1 的启动及工具链证据，必要时单列 P1-02 验证任务，由用户交给执行窗口。
6. **测试深度仍不足。** 有效 fixture 完整字段、编码往返/null 保留、缺字段/错类型矩阵、空/重复节次与数量上下界、非法时间和倒置安排范围、恰好 5 MiB 等应补测。现有测试通过只覆盖当前 12 项，不能替代跨端行为检查。

## 本轮实际验证

| 检查 | 实际结果 |
| --- | --- |
| `cd Android && ./gradlew assembleDebug test --rerun-tasks` | 失败：SDK location not found；未执行测试 |
| `cd Android && ANDROID_HOME=/tmp/qingke-android-sdk-1788767128 ./gradlew assembleDebug test --rerun-tasks` | BUILD SUCCESSFUL，68 个任务全部执行；debug APK 生成；debug/release 各 12 项，失败/错误/跳过均 0。报告时间 2026-09-07 10:11:54/55 UTC |
| 构建限制 | JDK 17.0.20.1；SDK platform 35 revision 2、build-tools 35.0.0。有无法 strip `libandroidx.graphics.path.so` 的提示，原样打包，非构建失败；未做 release APK、Windows、离线全新依赖缓存或 APK 位级重现验证 |
| `python3 docs/tests/android-contract-review-probe.py` | 实际运行下表 10 个输入；调用新构建 Android decoder 和原始 Swift `previewImport`。Swift 源码在 macOS 编译，非 iOS App 构建或设备测试。脚本退出 0 仅表示探针执行完毕，不表示跨端一致 |
| `/tmp/qingke-android-sdk-1788767128/platform-tools/adb devices -l` | 无连接设备；没有安装和启动应用 |
| iOS 全量构建/单元/UI/真机，Android UI/真机，Web | 本轮未运行 |

探针基于共享 `complete-schedule.json` 每次单独变异；具体生成与调用命令见 [探针脚本](../tests/android-contract-review-probe.py)。每列“接受/拒绝”均为本轮观测，不是新产品决策。

| 输入 | Android | Swift previewImport |
| --- | --- | --- |
| 原有效 fixture | 接受 | 接受 |
| schemaVersion 字符串 `"1"` | 接受 | 拒绝 |
| totalWeeks 字符串 `"18"` | 接受 | 拒绝 |
| schemaVersion 数值 `1.0` | 拒绝 | 接受 |
| 顶层 extra | 拒绝 | 接受 |
| semester 内 extra | 拒绝 | 接受 |
| 重复整门课程及其 ID | 接受 | 接受 |
| 时间仍递增、节次编号反转 | 接受 | 接受 |
| 学期 ID 含非法 UTF-8 0xFF | 接受 | 拒绝 |
| 开始日期 0000-01-01 | 接受 | 拒绝 |

## 给 Terra 的修正提示词（历史，不要重复执行）

```text
任务编号与阶段：P1-01-R1，工程与规则基础审查修正；不进入 P2。
角色：执行；只实施此次修正，不启动子 Agent。
建议模型／思考档位：Terra／中。
理由：问题已定位到小范围解码/日期规则及测试；涉及跨端类型边界，需要中档。建议不代表实际配置已核实，由用户在窗口选择，不自行切换模型/档位/服务商。
代码基准：实施 9b521db，后续文档 85e3234 及最新审查文档提交（由 git log 查询）；分支 codex/ios-ui-redesign-demo。开始先核对 git status --short --branch、git log 和实际 diff。
必读：AGENTS.md、docs/Android/handoff.md、product-baseline.md、technical-design.md、implementation-plan.md、p1-01-review.md；ios/Shared/schema 与 fixtures；iOS Domain 三文件及 Transfer/ScheduleDataTransfer.swift。
目标：修复审查 R1/R2/R3，增强与 A10 基础协议及 A06 日期校验相关的契约测试，补可复现工程说明。
允许修改：Android/**（包括新增 Android/README.md 和 Android 内测试）；不改 ios/**、web/**、共享协议或 fixtures、docs/**。交接和审查文档由分析窗口维护。
保留两处 iOS 工程配置未提交改动，不覆盖/还原/暂存；同工作区只有一个写入者，不把其他窗口改动纳入提交。
实施要求：
1. 全部整数字段严格检查 JSON 数值 token、整数值和范围，拒绝数值字符串/布尔/非整数/溢出；正常整数表示（含 1.0/指数形式）参考 Swift 实测，避免截断、浮点精度丢失及把合法版本报未知。补顶层和嵌套字段测试。
2. 严格解码 UTF-8，失败映射为明确导入错误；字节和流均拒绝损坏输入，不静默替换文字/ID。
3. 本地日期拒绝年份 0000，补闰年、无效日期和教学周边界回归。
4. 补有效共享 fixture 完整字段与往返断言、semester:null 编码保留、必填/类型矩阵、节次/安排边界和恰好输入上限测试；共享样例继续直接读取原 manifest。新增 Android 测试不得复制并修改共享协议来迁就实现。
5. 未知字段的 schema/Swift 分歧尚待决定：本任务保留现有策略，记录差异，不把它宣称为已批准；重复 ID/节次编号顺序也不自行加严。若出现其他真实冲突，提供最小样例与结果，交回分析窗口。
6. 在 Android/README.md 记录通用 JDK/SDK/路径配置、固定版本及官方兼容依据、构建/测试命令和报告位置；核查稳定渠道可用 SDK，说明 API 35 是否满足已确认 D02。若存在工具链升级需要，先回传具体组合和影响，本轮不擅自大范围升级。
验收：实际运行 ./gradlew assembleDebug、./gradlew test（配置真实 SDK）；补测应先复现原缺陷再验证修复。记录 JDK/SDK/依赖版本、命令、测试数量、日志及 APK 位置。可运行文档探针对照，但它不是通过断言；工具链变动若导致探针版本不匹配，回传分析窗口更新。
若有已授权可用模拟器/设备，补个人 debug 安装/启动证据；没有则明确未运行、P1 启动仍待验证，不声称完整 P1 通过。
不增加 Room/DataStore、业务页面、提醒、发布签名；不扩展 D01/D03/D04。
交付：修改摘要、原缺陷复现与修复测试证据、未决差异、SDK 核查结果、设备验证状态；仅暂存本任务 Android 文件并独立 Git commit，回传提交范围和剩余改动。由用户交给分析窗口复审，不自动推进下一阶段。
```

## 文档状态校正

原 product-baseline 的“待用户审阅／尚未开展实现”及 technical-design 的“待审阅／没有 Wrapper”等已经落后于实施及用户确认，本轮同步修正状态，不改变功能基准。D02 包名/minSdk 和 D04 个人 debug 范围早已确认；目标机型、正式发行、D01 和 D03 不因此自动解决。

## P1-01-R1 复审记录

复审日期：2026-09-07。复审范围为 `3924d26` 相对其父提交的 6 个 `Android/**` 文件；未修改应用代码。

结论：**R1/R2/R3 修正通过；P1-01-R1 审查通过，但 P1 阶段仍未完成。**

- `ScheduleDataDecoder` 现对全部整数字段检查 JSON 数值 token、数学整数性和 `Int` 范围；字符串、布尔、非整数和溢出均拒绝，`1.0` 与指数形式按 Swift 实测接受。
- 字节和流入口均采用严格 UTF-8，损坏字节及截断多字节输入拒绝；合法中文保留。
- 年份 `0000` 被拒绝，闰年与无效日期、教学周边界测试已补齐。
- 共享 fixture 代表字段保真、编码往返、显式 `semester: null`、缺字段/错类型、边界数量、5 MiB 上限均有测试。
- 跨端探针复跑：R1/R2/R3 案例 Android 与 Swift 结果一致；未知字段 Android 拒绝而 Swift 接受，仍按未决兼容差异保留，不视为产品决定。重复 ID 与节次编号顺序亦未擅自加严。

独立验证：

| 检查 | 结果 |
| --- | --- |
| `ANDROID_HOME=/tmp/qingke-android-sdk-1788767128 ./gradlew assembleDebug test --rerun-tasks` | `BUILD SUCCESSFUL`；68 tasks；Debug/Release 各 21 项 JVM 测试，失败/错误/跳过均为 0 |
| `python3 docs/tests/android-contract-review-probe.py` | 执行成功；R1/R2/R3 修正案例与 Swift 结果一致；未知字段差异仍存在 |
| `adb devices -l` | 无连接设备，未安装/启动验证 |
| SDK/工具链 | 可在本机临时 SDK 35 构建；README 已记录 AGP 8.8.2/Gradle 8.10.2/API 35，但尚未证明 API 35 满足“实施环境可用的最新稳定 SDK” |

剩余门槛：

1. 需要在授权模拟器或真机完成 debug 安装与启动，并记录设备/API 证据。
2. 需要补充或明确当前实施环境的稳定 SDK 核查；在此之前不能宣称 D02 的“最新稳定版本”已满足。
3. 未知字段严格拒绝与 Swift 宽容接受的差异等待产品决定；不阻塞本次修正通过，但影响后续 P4 契约。

因此不进入 P2。下一项应为 P1-02“工具链与启动验证”，仍属 P1。

补充限制：测试对代表字段及往返作了断言，尚非逐个课程/安排字段的穷尽检查；本轮未提供修正前逐项新增测试失败日志，仅有首轮探针和本轮复跑证据。README 已承认 API 35 不能证明满足 D02；其中 API 36.1/AGP 9 的升级建议未独立核实，不作为已批准升级组合。P1-02 先收集稳定渠道清单及官方兼容依据，回传最小升级方案，本任务不实施升级。README 现为英文，下一次更新应按最新规则改为中文。
