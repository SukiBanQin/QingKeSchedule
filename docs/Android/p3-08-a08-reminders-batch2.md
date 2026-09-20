# P3-08／A08 上课提醒第二批：设置页、权限流程与提交后重算（实施记录）

任务编号：**P3-08／A08（第二批）**。范围：设置页「04 上课提醒」、POST_NOTIFICATIONS 显式权限流程、
提醒偏好保存后协调、课程／学期／节次／教学日历成功保存或删除后的自动重算、关闭提醒时取消全部提醒、
能力状态呈现与对应测试与设备证据。

- 代码基准：分支 `Android`，开始基准 `a05402f`（= `origin/Android`，第一批与 R1／R2 已通过 Sol 独立技术
  复审）。本轮只新增实现提交，未改写历史、未强推、未合并 `main`。
- 边界：未改 iOS／Web／Room schema／共享 JSON schema 或版本 1；**未新增或改写 DataStore 用户偏好键**（
  `remindersEnabled`／`reminderLeadMinutes`／`usesCustomLeadTime` 第一批已存在，本批只接入 UI 与流程）；
  未实施窗口兜底、A10／A11；未处理 P3-07 用户验收。提交号见交付消息或 `git log`。

## 行为（对齐 iOS 与 D03）

- 设置页第 04 节「上课提醒」：开关、预设「准时／提前 5／10／15／30 分」与「自定义…」（1–180 分钟步进）、
  通知权限／提醒渠道／精确闹钟三行能力状态、状态行与失败重试提示。
- 状态文案状态机对齐 iOS `reminderStatusMessage`：有诊断 → 「课表已保存，但提醒更新失败：…」；关闭 →
  「提醒已关闭」；权限未开启 → 「系统通知权限未开启，提醒不会投递」；渠道不可用 → 「提醒渠道不可用，提醒
  不会投递」；没有待安排 → 「当前没有待安排的课程提醒」；否则「已安排最近 N 条课程提醒（可能部分延迟）」。
- 权限：只有用户主动开启开关或点击「开启通知权限」才申请 POST_NOTIFICATIONS，且仅在权限尚未授予时；拒绝后
  不轮询、不重复弹窗，课表与设置继续可用，并提供「系统通知设置」入口。
- D03：精确能力不可用时提醒仍按非精确安排，页面明确显示「精确闹钟不可用，提醒将按非精确方式安排并在通知里
  标注「（可能延迟）」。」并提供系统「精确闹钟设置」入口；不申请、不声明 `USE_EXACT_ALARM`。
- 协调：提醒偏好保存成功后触发协调——开启用 `reconcile`，关闭用 `cancelAll` 取消全部已登记提醒；课程、
  学期与节次、教学日历成功保存或删除后自动重算；协调只读取已提交数据，不监听草稿；提醒失败只作为诊断呈现，
  不回滚课表或偏好。
- 关闭时的部分失败：`cancelAll` 返回的 `activeAlarms`／`failed` 直接驱动页面提示「N 条提醒未能安排，
  将在下次重建时重试。」，不把失败报告成已取消。

## 代码结构

| 关注点 | 实现 |
| --- | --- |
| 平台读取 | `NotificationPresenter.isChannelReady()`：只读取渠道状态、不创建渠道；`AndroidNotificationPresenter` 用 `getNotificationChannel` 实现 |
| 状态快照 | `CourseReminderCoordinator.snapshot()` 返回 `ReminderStatusSnapshot(availability, activeAlarms)`，不创建渠道、不写注册表 |
| 协调接缝 | 新增 `ReminderControl` 接口（`snapshot`／`reconcile`／`cancelAll`），生产实现仍是 `CourseReminderCoordinator`，ViewModel 依赖该接缝 |
| 状态 | `ReminderUiState`：偏好、三项能力、活动数、`degraded`、失败数、诊断，以及 `statusMessage`／`asksForNotificationPermission`／`retriesLater`／`showsInexactNote` |
| ViewModel | `ScheduleViewModel.reminderUi` 与 `setRemindersEnabled`／`setReminderLeadMinutes`／`refreshReminderStatus`；学期、课程与日历写入成功后 `reconcileReminders(...)`；`Factory` 注入生产协调器 |
| 页面 | `QingKeApp` 的 `ReminderSettingsSection`（终端样式：预设芯片、自定义步进器、能力行、显式操作按钮），只在正式设置页渲染，首次设置页不出现 |
| 生命周期 | `repeatOnLifecycle(STARTED)` 时刷新提醒状态，从系统设置返回后即可看到最新权限／渠道／精确状态 |

## R1 返修：能力恢复后重新协调

Sol 对第二批（`4d979fe`／`123303c`）的独立复审未通过，阻断为：缺少通知权限时打开提醒，首次
`reconcile` 可能在用户允许权限前完成并保持零闹钟，而权限结果回调与从系统通知／渠道设置返回都只执行只读的
`refreshReminderStatus()`，页面会显示已授权但未来课程仍没有提醒。本轮逐条收口：

| 复审要求 | 处理 | 代码／测试 |
| --- | --- | --- |
| 权限请求成功或从系统通知／渠道／精确闹钟设置返回且提醒仍开启时，幂等执行 `reconcile` 并用结果更新 UI | 新增能力恢复入口 `ScheduleViewModel.recoverReminderCapabilities()`：提醒开启且能力可用时用 `reconcile` 的结果更新页面；权限结果回调在授予时调用它、拒绝时只刷新 | `ScheduleViewModel`；`ScheduleViewModelReminderTest.capabilityRecoveryAfterThePermissionGrantReconcilesAndRefreshesTheActiveSet` |
| 从系统设置返回的恢复路径，不能只更新文字 | 页面「系统通知设置」「精确闹钟设置」按钮先 `markSystemSettingsHandoff()`；`onForegroundResumed()` 在 handoff 后执行恢复、普通恢复仍只读 | `ScheduleViewModel`／`QingKeApp`；`returningFromSystemSettingsRecoversTheWindowInsteadOfOnlyRepaintingIt`（断言注册表 generation 增长、AlarmManager 与注册表一致、普通恢复不再重排） |
| 权限仍被拒绝时只刷新状态，不得自动重复弹窗 | 恢复入口在权限仍缺失时只发布能力快照，不调用任何平台写操作；发起申请仍只有页面开关与「开启通知权限」两个显式入口 | `capabilityRecoveryWhileThePermissionIsStillMissingOnlyRefreshesTheStatus`；按钮路由由 `theSystemSettingsActionsMarkTheHandoffBeforeLeavingTheApp` 断言 |
| 设备回归：从预撤销权限开始，授权后不重启、不再次修改设置即登记提醒 | `grantingThePermissionWithoutRestartingRegistersTheAlarms`：预撤销权限 → 打开偏好（注册表与 AlarmManager 均空）→ UiAutomation 授权 → 恢复入口 → 注册表非空且 AlarmManager 登记数与注册表一致；常规已授权运行走幂等分支 | 设备证据 `docs/Android/evidence/p3-08-a08-reminders-batch2/` 的 `settings-reminders-recovered-after-grant.png` 与运行记录 |

说明：在设备上用 `appops set SCHEDULE_EXACT_ALARM` 撤销精确能力会让系统立刻杀死应用进程（logcat
`lost permission to set exact alarms` → `Killing … schedule_exact_alarm revoked`），所以设备用例不翻转该 appop；
精确能力的恢复路径由 JVM 用例与第一批 `ReminderPlatformTest` 覆盖。

## 测试

- Debug／Release JVM 各 **235 tests、0 failures／errors／skipped**（第二批 R0 新增 11 条，R1 再新增 4 条
  `ScheduleViewModelReminderTest`：偏好写盘后协调、关闭时 `cancelAll` 与部分失败呈现、预设与自定义提前量、
  非法分钟拒绝、草稿不触发而提交后触发、写入失败不回滚、日历写入触发、能力与文案分支、诊断与快照失败）。
- API 37 ARM64 `connectedDebugAndroidTest` **162 tests、0 failures／errors／skipped**（第二批 R0 新增 11 条，R1 再新增 3 条）：
  - `ReminderSettingsTest`（9）：关闭时隐藏提前量、开启后的预设与 D03 说明、预设与自定义芯片的路由与选中态、
    权限被拒时的显式操作、渠道关闭时的入口、状态文案（已安排／降级／为空／失败重试），以及「系统通知设置」按钮在
    离开应用前先记录 handoff（用不启动系统的 Context 包装断言路由）。
  - `ReminderSettingsFlowTest`（5）：真实 Room／DataStore／协调器／AlarmManager 下，设置页开关 → 登记窗口 →
    关闭 → 全部取消；在页面上改提前量 → 注册表 `fireAt` 按 30 分钟重排、自定义 +1；能力行跟随真实平台状态；
    R1 新增「预撤销权限 → 授权 → 恢复登记」（不重启、不再改设置）与「从系统设置返回的恢复」（generation 增长、
    闹钟仍在、普通恢复不重排）。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （全部为既有依赖版本、图标与工具链提示）；文档测试 71 tests OK 与两个文档脚本、`git diff --check` 通过。

## 证据与限制

`docs/Android/evidence/p3-08-a08-reminders-batch2/`：已授权权限的真实设置页截图、预先 `pm revoke` 后的真实
设置页截图、R1 新增的「授权后恢复登记」真实截图（与已授权截图字节相同，证明恢复后的状态一致）、专门运行记录
（`OK (3 tests)`／`OK (1 test)`／`OK (1 test)` 与 R1 的 `OK (1 test)`、appops 状态 `ignore`）与 README。

**本环境无法验证，不得声称通过**：真实系统权限弹窗的人工观感（自动化只断言拒绝后的界面状态）；真实重启后的
BOOT_COMPLETED 投递；系统投递的时间／时区／包替换／精确权限广播；Doze／休眠唤醒与精确／非精确真实投递时间；
用户可见提醒通知的观感验收。

## 未纳入本批（A08 仍未完成）

超出 14 天滚动窗口的兜底再排程（窗口仍由触发与各重建入口推进）；A10 文件迁移与 A11；P3-07 用户验收。

## 准确状态

A08 第二批已实现（`4d979fe`／`123303c`）并自测；Sol 独立复审发现「通知权限／渠道恢复后只刷新状态、
不重新安排提醒」的阻断，R1 已补齐能力恢复入口（权限结果成功或从系统通知／渠道／精确闹钟设置返回时幂等
`reconcile`）与设备回归并自测通过（JVM 235、设备 162、lint 0 errors／24 warnings、证据目录已更新）；
**R1 已通过 Sol 独立技术复审，用户验收未进行**。Sol 独立复跑 ViewModel Reminder Debug／Release 各 15 项、
API 37 ARM64 设置页 9 项与真实流程 5 项，均为 0 failures／errors／skipped。详情见
[第二批 R0／R1 独立技术复审](p3-08-a08-reminders-batch2-review.md)。A08 第一批（含 R1／R2）
已通过 Sol 独立技术复审；窗口兜底、A10／A11、
整个 P3 与完整 App 仍未完成。P3-06-R7 与 P3-04-R8（含各自 R1／R2）的既有复审与验收结论不变；
**P3-07-R1 新增警告框与文案仍待用户验收**。
