# P1-04-IOS-SYNC 独立复审

复审日期：2026-09-08。角色：分析与审查窗口；未修改应用代码，未进入 P2。

## 范围与结论

复审对象为 `IOS` 分支提交 `0a3252d3d1edcc50a36d07a57b005c628e43579a`，基准为
`c11bd2bc7d5ac6669800b560e2371cbf208ae75c`。提交已推送 `origin/IOS`，本地 HEAD 与远端
一致，工作区干净。

实际 diff 仅包含：

- `ios/QingKeSchedule/Transfer/ScheduleDataTransfer.swift`
- `ios/QingKeScheduleTests/ScheduleDataTransferTests.swift`

将该 diff 与已审查来源提交 `4b7ff3e8ed1f990ad15ebb5348cba3a01ca145dd` 的两个文件补丁逐字比较，结果一致。

结论：**P1-04-IOS-SYNC 通过独立复审，无修正任务。**

## 契约核对

- `previewImport` 先读取并判断 `schemaVersion`，只有版本 1 才递归检查未知字段。
- 顶层、`semester`、`period`、`course`、`courseSchedule` 五层允许键与
  `ios/Shared/schedule-data.schema.json` 的 `additionalProperties: false` 对象字段一致。
- 任一层出现未知字段均返回 `ScheduleDataTransferError.malformedFile`。
- 不支持版本与未知字段同时出现时，仍优先返回 `unsupportedSchemaVersion`。
- `semester: null`、有效 fixture、既有数值词法、UTF-8、日期和业务校验逻辑未被同步提交改变。
- 新增 7 项 `ScheduleDataTransferTests` 分别覆盖五层未知字段拒绝、空学期与完整输入继续接受、
  不支持版本错误优先级；未发现越界改动。

## 独立验证

| 检查 | 结果 |
| --- | --- |
| `bash ios/scripts/ios-test.sh` | `TEST SUCCEEDED`；iPhone 17 Pro、iOS 26.5 Simulator；共 93 项通过，失败 0，跳过 0；xcresult：`ios/.build/ios/Logs/Test/Test-QingKeSchedule-2026.09.08_19-48-09-+0800.xcresult` |
| `git diff --check c11bd2b..0a3252d` | 通过 |
| 提交范围核对 | 仅上述两个 iOS 文件；未修改 `project.pbxproj`、`QingKeSchedule.xcscheme`、Android、Web、共享 schema/fixtures 或 P2 |
| `4b7ff3e` 来源补丁与本次补丁比较 | `cmp` 一致 |

本任务未运行 Android 构建、跨端探针或 Android 文档测试，因为它们不在 IOS 同步授权范围内。
这些未运行项不影响本次同步复审结论，也不能替代 Android 分支既有审查证据。

## 后续状态

P1-04 的 iOS 修正现已存在于 `Android` 和 `IOS` 两个开发分支，并分别完成对应复审。
这只完成 P1-04-IOS-SYNC，不等于用户验收或自动进入 P2。下一步应由分析窗口单独核对
P1 阶段全部关闭条件和两分支一致性；在该核对完成前不进入 P2。
