# P1-04 跨端未知字段一致性

## 状态与产品决定

2026-09-08，用户确认版本 1 课表 JSON 采用严格未知字段策略：顶层以及学期、节次、
课程和课程安排对象出现协议未声明字段时，Android 与 iOS 均拒绝导入。此决定与共享
schema 中各对象的 `additionalProperties: false` 一致。

本决定用于避免新版或异常文件中的字段被旧版静默忽略，并在再次导出时无提示丢失。
未来如需扩展协议，应明确更新 schema／版本和迁移规则，不通过继续忽略未知字段实现。

当前状态是：Android `ScheduleDataDecoder` 已严格拒绝未知字段，共享 schema 也已声明
严格；iOS `ScheduleDataTransfer.previewImport` 先用 `JSONSerialization` 读取版本，再用
默认 `JSONDecoder` 解码，而 Swift `Codable` 默认忽略未知键。因此 P1-04 的实际应用
修正集中在 iOS 导入边界。代码存在不代表已经实施或审查通过。

## 实施边界

- 在 iOS 课表文件导入入口对版本 1 的对象键做递归白名单检查：
  - 顶层：`schemaVersion`、`semester`、`courses`、`updatedAt`；
  - 学期：`id`、`name`、`startDate`、`totalWeeks`、`periods`；
  - 节次：`number`、`startTime`、`endTime`；
  - 课程：`id`、`name`、`teacher`、`color`、`schedules`；
  - 课程安排：`id`、`dayOfWeek`、`startPeriod`、`endPeriod`、`startWeek`、
    `endWeek`、`repeat`、`classroom`。
- 未知字段按现有导入错误体系归为 `ScheduleDataTransferError.malformedFile`；
  未知 `schemaVersion` 仍优先返回 `unsupportedSchemaVersion`。
- `semester: null`、有效共享 fixtures、5 MiB 限制、严格 UTF-8、数值词法、年份和
  业务校验、重复 ID 及节次顺序现状保持不变。
- 不修改版本 1 schema 的字段集合，不扩展 D01，不处理 D03，不接入 Room／DataStore，
  不进入 P2，也不借机修改 UI。
- 该任务是为 Android P1 收口所需的跨端协议修正，获准在 `Android` 分支修改必要的
  iOS 导入源码和测试；完成并复审后，再安排把同一跨端修正同步到 `IOS` 分支。

## 验收要求

1. iOS 单元测试分别覆盖顶层、semester、period、course 和 course schedule 未知字段，
   均断言导入失败；有效输入和 `semester: null` 继续通过。
2. 扩展并运行 `docs/tests/android-contract-review-probe.py`，逐项确认上述未知字段案例
   Android 与 Swift 均拒绝；脚本退出 0 仍只表示执行完成，需记录逐例结果。
3. 运行相关 iOS 测试和构建；若没有可用 iOS Simulator，必须明确标注未执行，不能用
   macOS `swiftc` 探针冒充 iOS App 测试通过。
4. 为跨端探针重新构建 Android decoder，并运行相关 Android 单元测试，确认原严格行为
   和其他协议边界没有回归。
5. 运行文档验证及工作区／暂存区空白检查，只提交本任务文件并推送 `Android`。

## 实施与验证记录

2026-09-08 从基准 `cb7323fb6b4726d58896242d0c7ed6faedac4ac7` 实施。iOS
`previewImport` 保持先读取并验证 `schemaVersion`，只有确认版本 1 后才递归检查顶层、
semester、period、course 和 course schedule 对象键；任一未知字段映射为
`ScheduleDataTransferError.malformedFile`。DTO、导出字段、共享 schema/fixtures、Android
decoder、重复 ID 和节次顺序均未修改。

`ScheduleDataTransferTests` 新增 7 项独立用例：五层未知字段分别拒绝、`semester:null`
与完整有效输入继续接受、不支持版本与未知字段并存时仍返回
`unsupportedSchemaVersion(2)`。`bash ios/scripts/ios-test.sh` 使用 iPhone 17 Pro、iOS 26.5
Simulator 完整通过：93 项通过，失败/跳过均为 0；xcresult 位于
`ios/.build/ios/Logs/Test/Test-QingKeSchedule-2026.09.08_18-02-46-+0800.xcresult`。

API 37/JDK 17 下 Android `assembleDebug test --rerun-tasks` 通过，70 个任务实际执行；
Debug/Release JVM 各 20 项通过，失败/错误/跳过均为 0。跨端探针扩展至 19 例并实际逐项核对：
`unknown-field`、`nested-unknown`、`period-unknown`、`course-unknown`、
`course-schedule-unknown` 均被 Android 与 Swift 拒绝；合法完整输入、合法整数指数、重复 ID
和反序节次现状保持接受，其他既有非法案例保持拒绝。探针中的 Swift 结果来自 macOS
`swiftc`，只作为跨端导入边界补充；iOS App 测试证据来自上述 Simulator 测试。

## 独立专项复审结论

2026-09-08，分析审查窗口依据实施基准 `cb7323fb6b4726d58896242d0c7ed6faedac4ac7`
独立核对提交 `4b7ff3e8ed1f990ad15ebb5348cba3a01ca145dd`。实际 diff 仅包含本任务列出的
6 个文件，未发现 Android 应用、构建依赖、共享 schema/fixtures、Web、P2 或未授权 iOS UI 改动。

复审逐项解析 `ios/Shared/schedule-data.schema.json`，确认顶层、semester、period、course、
courseSchedule 五层允许字段集合与 `hasOnlyVersion1Fields` 完全一致。`previewImport` 先读取并
判断 `schemaVersion`，再执行白名单检查；不支持版本与未知字段并存时仍优先返回
`unsupportedSchemaVersion`。`semester: null`、完整有效 fixture、合法指数、重复课程 ID、反序节次、
非法 UTF-8、非法数字词法、年份 0000 和业务校验结果均与既有记录一致。

独立执行跨端探针，19 例结果与交付记录一致：五层未知字段 Android/Swift 均拒绝，其余案例保持原有结果。
独立执行 API 37/JDK 17 下 `assembleDebug test --rerun-tasks --no-daemon --console=plain`，70 个任务成功，
Debug/Release JVM 各 20 项通过；独立执行 `bash ios/scripts/ios-test.sh`，iPhone 17 Pro、iOS 26.5 Simulator
共 93 项通过，失败和跳过均为 0。文档测试 27 项、`documentation.test.sh`、`repository-layout.test.sh`、
`git diff --check` 均通过。

复审结论：**P1-04 通过独立专项复审，无阻塞或修正任务。** 本结论只覆盖版本 1 未知字段跨端一致性，
不代表 P1 阶段关闭、完整应用完成或用户验收，也不授权自动进入 P2。后续应另行核对 P1 全部交付门槛，
并按用户安排将同一跨端修正同步到 `IOS` 分支。
