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

P1-04 属于共享协议行为修正，完成后必须由分析审查窗口独立复审。只有 P1-04 通过且
P1 全部门槛再次核对通过，才能记录 P1 关闭；这不等于用户验收，也不授权进入 P2。
