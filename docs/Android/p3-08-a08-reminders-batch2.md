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

## 测试

- Debug／Release JVM 各 **231 tests、0 failures／errors／skipped**（第二批新增 11 条
  `ScheduleViewModelReminderTest`：偏好写盘后协调、关闭时 `cancelAll` 与部分失败呈现、预设与自定义提前量、
  非法分钟拒绝、草稿不触发而提交后触发、写入失败不回滚、日历写入触发、能力与文案分支、诊断与快照失败）。
- API 37 ARM64 `connectedDebugAndroidTest` **159 tests、0 failures／errors／skipped**（第二批新增 11 条）：
  - `ReminderSettingsTest`（8）：关闭时隐藏提前量、开启后的预设与 D03 说明、预设与自定义芯片的路由与选中态、
    权限被拒时的显式操作、渠道关闭时的入口、状态文案（已安排／降级／为空／失败重试）。
  - `ReminderSettingsFlowTest`（3）：真实 Room／DataStore／协调器／AlarmManager 下，设置页开关 → 登记窗口 →
    关闭 → 全部取消；在页面上改提前量 → 注册表 `fireAt` 按 30 分钟重排、自定义 +1；能力行跟随真实平台状态
    （常规运行走已授权分支，预撤销权限的专门运行走拒绝分支）。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （全部为既有依赖版本、图标与工具链提示）；文档测试 71 tests OK 与两个文档脚本、`git diff --check` 通过。

## 证据与限制

`docs/Android/evidence/p3-08-a08-reminders-batch2/`：已授权权限的真实设置页截图、预先 `pm revoke` 后的真实
设置页截图、专门运行记录（`OK (3 tests)`／`OK (1 test)`／`OK (1 test)`、appops 状态 `ignore`）与 README。

**本环境无法验证，不得声称通过**：真实系统权限弹窗的人工观感（自动化只断言拒绝后的界面状态）；真实重启后的
BOOT_COMPLETED 投递；系统投递的时间／时区／包替换／精确权限广播；Doze／休眠唤醒与精确／非精确真实投递时间；
用户可见提醒通知的观感验收。

## 未纳入本批（A08 仍未完成）

超出 14 天滚动窗口的兜底再排程（窗口仍由触发与各重建入口推进）；A10 文件迁移与 A11；P3-07 用户验收。

## 准确状态

A08 第二批已实现并自测（JVM 231、设备 159、lint 0 errors／24 warnings、截图与专门运行记录齐备）；
Sol 独立复审发现「通知权限／渠道恢复后只刷新状态、不重新安排提醒」的阻断，**第二批未通过技术复审，待 R1，
用户验收未进行**。详情见 [第二批独立技术复审](p3-08-a08-reminders-batch2-review.md)。A08 第一批（含 R1／R2）
已通过 Sol 独立技术复审；窗口兜底、A10／A11、
整个 P3 与完整 App 仍未完成。P3-06-R7 与 P3-04-R8（含各自 R1／R2）的既有复审与验收结论不变；
**P3-07-R1 新增警告框与文案仍待用户验收**。
