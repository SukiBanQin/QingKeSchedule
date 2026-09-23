# P3-08／A08 上课提醒第三批：14 天滚动窗口兜底（实施记录）

任务编号：**P3-08／A08（第三批）**。范围：内部维护闹钟（窗口兜底），用于消除「连续超过 14 天没有课程提醒触发、
用户也不打开 App 时滚动窗口不再前进」的残留限制。

- 代码基准：分支 `Android`，开始基准 `c4b3a4b`（= `origin/Android`；A08 第一／二批已通过 Sol 独立技术复审，
  第二批设置页与交互已通过用户验收）。本轮只新增实现提交，未改写历史、未强推、未合并 `main`。
- 边界：未改 iOS／Web／Room schema／DataStore 用户偏好键／共享 JSON schema 与版本 1；未新增用户界面或权限
  申请；未推进 A10／A11；未处理 P3-07 用户验收。提交号见交付消息或 `git log`。

## 设计

| 关注点 | 实现 |
| --- | --- |
| 固定、无冲突身份 | 独立接收器 `ReminderMaintenanceReceiver`、独立 action `com.qingke.schedule.action.REMINDER_MAINTENANCE`、独立常量 requestCode `MAINTENANCE_REQUEST_CODE = 1`，并以固定 `Intent.data = qingke://reminder-maintenance/window` 定位；课程提醒仍使用 `qingke://reminder/...` + `CourseReminderReceiver`，两者不可能互相替换或取消 |
| 周期与依据 | 周期 = **窗口的一半**（14 天窗口 → 7 天，`ReminderMaintenance.nextFireAt(moment, window)`）。一次 reconcile 只覆盖「从该时刻起一个窗口」，所以唤醒晚于窗口就可能已经错过；取一半即保证窗口最多衰减一半，同时把唤醒次数压到最低 |
| 幂等重排 | 每次 `reconcile` 都用同一 PendingIntent 身份重排下一次兜底（`setAndAllowWhileIdle`），因此课程触发、维护触发、应用启动、课表／偏好保存、重启、包替换、时间／时区与精确权限变化后的既有重建都会顺带续期 |
| 与课程提醒隔离 | 兜底不写入提醒注册表、不参与 `activeCount`／`degraded`，也不由 `deliver` 投递；`ReminderStatusSnapshot.activeAlarms` 仍只来自注册表 |
| 精确能力 | 兜底只用于重新规划，因此**不使用精确闹钟 API**、不要求 `SCHEDULE_EXACT_ALARM`；D03 的精确／非精确区分仍只作用于课程提醒 |
| 取消条件 | 关闭提醒（`reconcile` 或 `cancelAll`）或投递能力不可用（通知权限缺失、渠道不可用）时取消兜底；能力恢复后由协调再次建立 |
| 失败诊断 | 兜底调度／取消失败以固定身份 `ReminderMaintenance.URI` 进入既有 `ReminderReconciliation.failed`（设置页的失败提示会显示出来），不影响课表、偏好与课程提醒注册表的最终活动集合 |

## 测试

- Debug／Release JVM 各 **241 tests、0 failures／errors／skipped**（第三批新增 6 条
  `CourseReminderCoordinatorTest`）：

  1. `theMaintenanceAlarmIsArmedHalfAWindowAhead`：14 天窗口下兜底排在 7 天后，且不超过窗口一半。
  2. `theMaintenanceAlarmSurvivesAnEmptyWindowAndBringsTheFarCourseIn`：窗口内没有课程时注册表为空但兜底仍存在；
     时间前进一个周期后用 `WINDOW_MAINTENANCE` 触发，窗口外的课程进入计划并被登记，兜底顺延。
  3. `repeatedMaintenanceTriggersAndTimeChangesNeverDuplicateIdentities`：重复 reconcile、重复维护触发与时间变化后
     注册表 URI 不重复、平台条目与注册表一致、身份仍唯一。
  4. `theMaintenanceAlarmFollowsTheToggleAndTheDeliveryCapability`：关闭提醒、通知权限缺失、渠道不可用时取消；
     能力恢复后重新建立。
  5. `cancelAllClearsTheMaintenanceAlarmToo`：关闭提醒走 `cancelAll` 时同样取消兜底。
  6. `aMaintenanceFailureIsReportedAndRetriedWithoutTouchingTheWindow`：调度失败进入 `failed` 且课程窗口保持一致，
     重试成功；取消失败同样进入 `failed`。
- API 37 ARM64 `connectedDebugAndroidTest` **165 tests、0 failures／errors／skipped**（第三批新增 3 条
  `ReminderMaintenanceTest`）：窗口外课表下兜底真实登记（`dumpsys alarm` 条目）、重复 reconcile 条目仍为 1、
  触发不发通知且注册表与 AlarmManager 一致并重新登记；`cancelAll` 后条目清零、重新开启后恢复；预撤销权限时
  兜底不保留、授权后重建。`ReminderReceiverTest` 的 Manifest 断言同步覆盖新的 `exported=false` 接收器与 action。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （全部为既有依赖版本、图标与工具链提示）；文档测试 71 tests OK 与两个文档脚本、`git diff --check` 通过。

## 证据与限制

`docs/Android/evidence/p3-08-a08-reminders-batch3/`：真实 `dumpsys alarm` 中该兜底的登记条目
（`maintenance-alarm-dump.txt`，固定 action tag）、两次专门运行记录（`OK (3 tests)`／`OK (1 test)`、appops
`ignore`）与 README。

**本环境无法验证，不得声称通过**：兜底的真实到期触发（周期 7 天，自动化不等待；设备测试用显式广播触发同一
接收器与处理函数）；真实重启后的 BOOT_COMPLETED 投递；系统投递的时间／时区／包替换／精确权限广播；
Doze／休眠与厂商真机后台限制下的实际投递时序；用户可见通知观感（本批不新增通知）。

## 准确状态

A08 第三批（14 天窗口兜底）已实现并自测（JVM 241、设备 165、lint 0 errors／24 warnings、`dumpsys alarm` 与
专门运行证据齐备），并已通过 [Sol 独立技术复审](p3-08-a08-reminders-batch3-review.md)。Sol 独立复跑
Debug／Release 提醒包各 57 项、API 37 ARM64 维护 3 项与接收器 6 项，均为 0 failures／errors／skipped。
A08 第一／二批（含各自 R1／R2）也已通过 Sol 独立技术复审，第二批设置页与交互已通过用户验收；真实权限弹窗、
课程通知观感与真机后台投递仍待验证，A10／A11、整个 P3 与完整 App 仍未完成。
P3-06-R7 与 P3-04-R8（含各自 R1／R2）的既有复审与验收结论不变；**P3-07-R1 新增警告框与文案仍待用户验收**。
