# P3-08／A08 上课提醒第一批：纯规划器与 Android 平台基础设施（实施记录）

任务编号：**P3-08／A08（第一批）**。范围：纯 Kotlin 提醒规划器、可替换平台接口、独立内部闹钟注册表、
串行协调与 generation 门禁、通知渠道、闹钟触发与重建 Receiver、Manifest 权限与装配。

- 代码基准：分支 `Android`，开始基准 `9a6ed3e`（已推送 `origin/Android`），开始时工作区干净。本轮未改
  iOS／Web／Room schema／DataStore 用户偏好结构／共享 JSON schema 或版本 1，未推进 A10／A11，未合并
  `main`。提交号见交付消息或 `git log`。
- 已确认 D03：精确提醒优先；精确闹钟不可用时使用非精确提醒并明确标记「可能延迟」；采用可由用户授予／
  撤销的 `SCHEDULE_EXACT_ALARM`，**不使用** `USE_EXACT_ALARM`；通知权限、渠道与精确能力分别建模，
  提醒偏好开启不等于已可靠安排。

## 业务规则（对齐 iOS 基准）

- 遍历「课程 → 安排 → 教学周（startWeek..endWeek 并应用单双周）→ 显示星期 1..7」，用共享教学日历解析器
  决定该日期是否上课：停课日不提醒；周末默认不上课开启时跳过周末；调课日按「跟随的星期」匹配课程，并按
  该日期自身所属教学周应用单双周（因此调课不会沿用原安排星期所在周的奇偶）。
- 提前量 0—180 分钟（越界收敛）；只规划 `fireAt > now` 且落在滚动窗口内的提醒；标题为课程名，正文为
  `开始时间–结束时间`，教室非空时追加 ` · 教室`；排序按 fireAt 再按身份 URI，稳定可复现。
- 午休、午休标题与时长完全不参与提醒。
- 提醒身份 = 课程／安排业务 ID + occurrence 位置（`courseIndex`／`scheduleIndex`）+ 教学周 + 实际日期 +
  调课来源标记；编码为 RFC 3986 百分号转义的 `qingke://reminder/...` URI，版本 1 允许的重复课程／安排
  ID 也不会产生相同身份。

## 平台与状态设计

| 关注点 | 实现 |
| --- | --- |
| 精确／非精确 | `AndroidAlarmScheduler` 按 `ReminderAlarm.exact` 选择 `setExactAndAllowWhileIdle` 或 `setAndAllowWhileIdle`（RTC_WAKEUP），并在调度前再次检查能力，避免权限被撤销后抛错；`exact=false` 的提醒正文带「（可能延迟）」 |
| PendingIntent 身份 | 显式 `CourseReminderReceiver`、身份 URI 作为 `Intent.data`、`FLAG_IMMUTABLE`、单一常量 requestCode（不依赖字符串哈希） |
| 通知渠道 | `course_reminders`／「上课提醒」（IMPORTANCE_HIGH），`ensureChannel` 返回渠道是否可用，`IMPORTANCE_NONE`（用户关闭）一律判为不可用 |
| 能力建模 | `ReminderAvailability(notificationsPermitted, channelReady, exactAlarmsAvailable)`；`canDeliver` 才排程；非精确计划 `degraded=true` |
| 注册表 | `DataStoreReminderRegistry` 使用独立文件 `schedule_reminders.preferences_pb`（与用户偏好文件分离），内容为 generation + 闹钟负载；损坏时退化为空并在下次协调修复 |
| 协调 | `CourseReminderCoordinator`：Mutex 串行；副作用前重读 generation，若被更新则以 `superseded` 收口、不触碰平台与注册表；R1 起每轮无条件重新提交全部应排闹钟（同一 PendingIntent 幂等），注册表只用于确定该取消哪些条目，不作为平台仍有闹钟的证明；单条失败继续并记入 `failed`；取消失败条目保留以便下次重试；从不写课表或偏好 |
| 触发 | `CourseReminderReceiver` 先以 payload 自身 fireAt 为基准重算当前已提交数据的计划，再用 `CourseReminderDelivery` 判定（身份一致且 fireAt 未变、或位置变化但同一 occurrence；已开始超过 10 分钟或不再存在则抑制），随后推进窗口 |
| 重建入口 | `ReminderRebuildReceiver`：BOOT_COMPLETED、MY_PACKAGE_REPLACED、TIME_SET、TIMEZONE_CHANGED、SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED；动作映射与重建处理函数抽到包内可直接测试 |
| 装配 | `ScheduleAppDependencies` 暴露 `reminderRegistry`／`reminderCoordinator`（Application 懒加载不变），供后续 ViewModel／设置页调用 |

## 有界窗口的选择依据与限制

iOS 受系统限制只能保留 60 条通知；Android 的 AlarmManager 没有同类硬限额，本批改为 **14 天滚动窗口、
最多 120 条闹钟**（`CourseReminderPlanner.DEFAULT_WINDOW`／`DEFAULT_LIMIT`）。依据：注册表体积、开机重建
耗时与耗电只与近期课程成比例；窗口由每个已触发闹钟（`ALARM_FIRED` 后重新协调）与各重建入口推进。
**限制**：若连续超过窗口长度既无闹钟触发也不打开应用，更远提醒会延迟到下一次入口；这正是下一批的候选
改进（更长窗口或周期性兜底），已如实记录。

## R1 返修：对照 Sol 复审意见

Sol 对第一批提交 `c4cb5e7` 的独立复审提出七项意见，本轮逐条收口：

| 复审意见 | 处理 | 代码／测试 |
| --- | --- | --- |
| 1. 重建不能把持久化注册表当成系统闹钟仍存在的证明 | `reconcile` 每轮无条件重新提交全部应排闹钟（同一 PendingIntent 幂等），不再用「注册表非空」或「PendingIntent 存在」推断平台仍有闹钟；取消只针对注册表里已不该存在的条目；取消失败保留待下次重试 | `CourseReminderCoordinator.reconcile`；`CourseReminderCoordinatorTest.rebuildResubmitsEveryExpectedAlarmAfterThePlatformLostThem`（先清空平台闹钟再重建）、`everyRebuildReasonResubmitsTheExpectedAlarmsIdempotently`；真机 `ReminderReceiverTest.rebuildResubmitsWhenThePlatformLostItsAlarms`（先 `AlarmManager.cancel` 再以 `BOOT_COMPLETED` 重建） |
| 2. 触发前复核课表／`remindersEnabled`／通知权限／渠道，任一不可投递即抑制，不得先通知后取消，也不得把静默未发布报告为 Delivered | `deliver()` 在通知前判定 `remindersEnabled`、`notificationsPermitted`、`channelReady`，并核对 payload 是否仍属于当前已提交课表；任一不满足返回 `Suppressed`；`notify()` 改为返回 Boolean，`false` 记为 `Failed("notification was not published")` | `CourseReminderCoordinator.deliver`／`AndroidNotificationPresenter.notify`；`CourseReminderCoordinatorTest` 的 `deliverSuppressesWhenRemindersAreDisabled`／`deliverSuppressesWhenNotificationsAreDenied`／`deliverSuppressesWhenTheReminderChannelIsUnusable`／`aSilentNonPostIsReportedAsFailedNotDelivered`／`deliverReportsAFailedPostWithoutThrowing`；设备端权限撤销证据见 [证据目录](evidence/p3-08-a08-reminders/README.md) |
| 3. `channelReady` 必须把 `IMPORTANCE_NONE` 判为不可用 | `ensureChannel()` 以「渠道 importance != IMPORTANCE_NONE」判定可用，用户关闭的渠道直接报不可用 | `AndroidNotificationPresenter.ensureChannel()`；`ReminderPlatformTest.aChannelTheUserTurnedOffIsReportedAsUnusable` |
| 4. 通知身份不得只用 `uri.hashCode()` | 通知以完整提醒 URI 作为 tag、id 固定 0，投递与取消都按 tag 定位；PendingIntent 身份同样使用 URI 作为 `Intent.data` | `AndroidNotificationPresenter.notify`／`cancelNotification`；`ReminderPlatformTest.notificationIdentityUsesTheUriTagSoHashCollisionsCannotOverrideEachOther`（`Aa`／`BB` hash 碰撞不互相覆盖、取消其一不影响另一条） |
| 5. `degraded` 必须反映当前全部活动提醒 | `degraded`／`activeCount` 改为由当前全部活动提醒（含 retained 的非精确提醒）计算，不再只看本轮提交 | `CourseReminderCoordinator`；`CourseReminderCoordinatorTest.degradedReflectsActiveInexactAlarmsAcrossRuns` |
| 6. 补齐生产 `APP_START` 恢复入口，不申请权限、不新增 UI | `QingKeScheduleApplication.requestReminderSync(reason = APP_START)`，由 `MainActivity.onCreate` 调用；不申请权限、不加界面 | `QingKeScheduleApplication`／`MainActivity`；真机 `ReminderStartupEntryTest` |
| 7. 清理状态文档矛盾 | D03 已确认；A08 第一批已实施、R1 待复审（该次复审后来未通过，见下节 R2）；删除仍称「只授权只读分析」的过时状态；保留 P3-07-R1 文案待用户验收、A08 与 P3 整体未完成 | 本文件、[交接状态](handoff.md)、[实施计划](implementation-plan.md)、[产品基准](product-baseline.md) |

## R2 返修：部分失败一致性与权限撤销证据

Sol 对 R1（`f4be82f`）的增量独立复审未通过，提出两个必须修正的问题，本轮逐条收口：

| 复审问题 | 处理 | 代码／测试 |
| --- | --- | --- |
| 1a. 既有且未变化的闹钟在本轮重提交失败时会从注册表删除，原平台闹钟可能仍存在，之后无法可靠取消 | `reconcile` 区分首次提交失败与既有条目重提交失败：首次失败不写入注册表；既有且内容相同的条目重提交失败时保守保留原条目（它是取消平台闹钟的唯一依据）并在下一轮重试；旧条目已成功取消而新提交失败时不保留；取消与提交同时失败时保留旧条目 | `CourseReminderCoordinator.reconcile`；`CourseReminderCoordinatorTest.unchangedResubmitFailureKeepsTheAlarmForRetryAndLaterCancel`／`failedCancelWithFailedResubmitKeepsThePreviousEntry`／`cancelledPreviousEntryIsDroppedWhenTheResubmitFails` |
| 1b. 最终注册表、`activeAlarms`、`activeCount`、`degraded` 与失败列表必须来自同一最终活动集合，URI 不重复 | 由一次计算得到最终活动集合并按 URI 去重；注册表、`activeAlarms`、`activeCount`、`degraded` 全部取自已保存的注册表内容；`failed` 为去重后的失败身份列表 | 同上；`duplicateRegistryEntriesCollapseIntoOneActiveAlarm`（注册表里的重复条目收敛为一条） |
| 1c. `cancelAll` 取消失败时没有把注册表中仍活动的条目写回 `activeAlarms` | `cancelAll` 用去重后的失败集合保存剩余条目，并把这些条目写入返回对象的 `activeAlarms`，使 `activeCount`／`degraded` 与注册表一致 | `CourseReminderCoordinator.cancelAll`；`CourseReminderCoordinatorTest.cancelAllPartialFailureReportsTheRemainingActiveAlarms` |
| 2. 权限撤销设备用例用正文 `android.text` 与课程标题比较，「通知栏没有该标题」的断言实际上查不到任何东西 | 改为按生产使用的身份检查通知栏：`notification tag == 完整提醒 URI`，并辅以 `android.title`；常规已授权分支改为断言明确的 `Delivered`，且必须能按 URI tag 在通知栏找到该提醒 | `ReminderPermissionRevocationTest`；重新取证的专门运行见 [证据目录](evidence/p3-08-a08-reminders/README.md) 的 `permission-denied-20260920.txt`（含 `permitted=false` 分支记录） |

## 测试

- Debug／Release JVM 各 **220 tests、0 failures／errors／skipped**（本批新增 40；R1 新增 7 条协调器用例并移除 1 条把注册表当作平台闹钟凭据的旧用例；R2 再新增 5 条协调器用例覆盖部分失败与 `cancelAll` 状态）：
  - `CourseReminderPlannerTest`（18）：每周／单双周、首末周与越界收敛、停课日、周末关闭、调课跟随星期
    ＋按自身教学周筛选（含奇偶）、0／180 分钟与跨日、过去与窗口外跳过、限流、同刻排序、教室正文格式、
    重复 ID 身份不碰撞、URI 转义、午休无关、时区差异、夏令时保持墙钟时间、无学期／无课程／未知节次。
  - `CourseReminderCoordinatorTest`（28）：排程与幂等、停用清空、通知被拒时清空且不排程、非精确降级与
    标记、获得精确能力后替换、提前量变化替换、单条排程失败与重试、取消失败保留重试、generation 被更新
    时 superseded 且零副作用、并发协调串行无重复、数据不可读时平台与注册表零改动、投递判定（有效／旧课表／
    fireAt 变化／位置变化／过晚）；R1 新增重建后无条件重提交、`degraded` 口径与投递前抑制；R2 新增既有
    条目重提交失败后保留并可重试与后续取消、取消与提交同时失败保留旧条目、旧条目已取消而新提交失败不保留、
    注册表重复条目收敛为一条、`cancelAll` 部分失败返回剩余活动条目。
  - `CourseReminderDeliveryTest`（5）：身份一致投递、fireAt 变化抑制、occurrence 不再存在抑制、位置变化
    仍投递、过晚抑制与非精确宽限。
- API 37 ARM64 `connectedDebugAndroidTest` **148 tests、0 failures／errors／skipped**（本批新增 10；R1 在提醒相关测试类新增 5 条）：
  - `ReminderPlatformTest`（6）：通知渠道 id／名称／描述；重复 ID 的三条闹钟身份互不相同、取消其一不影响
    其余；`appops set SCHEDULE_EXACT_ALARM deny/allow` 后精确能力随之变化且两种模式都能注册；
    注册表在自己文件中往返、损坏数据退化为空、文件名与偏好文件不同；R1 新增 `Aa`／`BB` hash 碰撞下
    两条通知互不覆盖与误取消，以及用户关闭的渠道（`IMPORTANCE_NONE`）被判为不可用。
  - `ReminderReceiverTest`（6）：真实 alarm broadcast 后通知按计划标题／正文出现并注册后续窗口；旧课表
    payload 被抑制、不产生通知；Manifest 声明两个 `exported=false` Receiver、四个权限且无 USE_EXACT_ALARM、
    受保护动作都能解析到重建 Receiver；五个重建入口分别排入闹钟；payload 在 Intent extras 中往返一致；
    R1 新增「平台闹钟被清空后重建仍按预期重新提交」。
  - `ReminderPermissionRevocationTest`（1）：R1 新增。整套运行中通知权限已被其他用例授予，本用例断言
    权限没有被用来抑制可投递提醒；「权限已撤销」分支由预先撤销权限的专门 `am instrument` 运行覆盖，
    记录见 `docs/Android/evidence/p3-08-a08-reminders/permission-denied-20260920.txt`（`OK (1 test)`）。
  - `ReminderStartupEntryTest`（1）：R1 新增。真机启动 `MainActivity`，不弹权限、不加界面，验证
    生产 `APP_START` 入口按预期重新提交闹钟。
  - `ReminderEvidenceTest`（1）：产出 `docs/Android/evidence/p3-08-a08-reminders/` 的设备记录。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／24 warnings**
  （warning 全部为既有依赖版本、图标与工具链提示，提醒相关代码无新增）；文档测试 71 tests OK、
  `documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

## 证据与限制

`docs/Android/evidence/p3-08-a08-reminders/`：真实通知栏截图（R1 重新采集，「证据课程」／「08:00–08:45 ·
A101 （可能延迟）」，提醒分区）＋设备记录（身份 URI、能力三项、渠道与通知内容、reconcile 的
submitted／unchanged／cancelled／active／generation、未验证清单）＋`permission-denied-20260920.txt`
（预先撤销 POST_NOTIFICATIONS 的专门运行，appops 状态 `ignore`、`OK (1 test)`，并记录该次运行的
`permitted=false` 分支；R2 起该用例按通知 tag 与 `android.title` 检查通知栏）＋README。截图与权限记录用
`adb install` + `am instrument` 采集，因为 Gradle connected 任务结束会卸载应用并清除通知。

**本环境无法验证，不得声称通过**：真实重启后的 BOOT_COMPLETED 投递；系统投递的时间／时区／包替换／精确
权限广播（受保护广播，应用无法发送）；Doze／休眠唤醒与精确／非精确真实投递时间；用户可见通知的观感
验收。另外：POST_NOTIFICATIONS 的「已撤销」分支无法在整套 connected 运行内构造——撤销已授予的运行时权限
会立刻杀死被测进程，而 appops 拒绝对运行时 op `android:post_notification` 直接写入——只由上面那次专门
运行取证，整套运行中该用例走「已授权」分支。

## 未纳入本批（A08 仍未完成）

提醒设置界面、运行时通知权限申请流程、课表编辑／保存后的自动重算、超出滚动窗口的再排程策略。

## 准确状态

A08 第一批已实现（`c4cb5e7`），R1 对照 Sol 首轮七项意见完成返修（`f4be82f`），Sol 再复审发现部分失败时
注册表活动集合丢失、`cancelAll` 活动集合失真，以及权限撤销设备用例按错误字段检查标题，因此 R1 独立
复审未通过；R2 已修正上述问题并自测通过（JVM 220、设备 148、lint 0 errors／24 warnings、证据目录已更新），
**R2 已通过 Sol 独立技术复审，用户验收仍未进行**。Sol 独立复跑提醒包 Debug／Release JVM 各 51 项、
API 37 ARM64 提醒包设备测试 15 项，均为 0 failures／errors／skipped。详情见
[P3-08-R1／R2 独立技术复审](p3-08-r1-review.md)。A08 其余部分
（提醒设置 UI、运行时权限流程、编辑后自动重算）、A10／A11、整个 P3 与完整 App 仍未完成、未授权。
P3-06-R7 与 P3-04-R8（含各自 R1／R2）的既有复审与用户验收结论不变；P3-07-R1 新增警告框与文案仍待用户
验收。
