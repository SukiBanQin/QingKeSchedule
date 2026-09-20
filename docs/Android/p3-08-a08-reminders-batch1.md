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
| 通知渠道 | `course_reminders`／「上课提醒」（IMPORTANCE_HIGH），`ensureChannel` 返回渠道是否可用 |
| 能力建模 | `ReminderAvailability(notificationsPermitted, channelReady, exactAlarmsAvailable)`；`canDeliver` 才排程；非精确计划 `degraded=true` |
| 注册表 | `DataStoreReminderRegistry` 使用独立文件 `schedule_reminders.preferences_pb`（与用户偏好文件分离），内容为 generation + 闹钟负载；损坏时退化为空并在下次协调修复 |
| 协调 | `CourseReminderCoordinator`：Mutex 串行；副作用前重读 generation，若被更新则以 `superseded` 收口、不触碰平台与注册表；新增／保留／替换／取消；单条失败继续并记入 `failed`；取消失败条目保留以便下次重试；从不写课表或偏好 |
| 触发 | `CourseReminderReceiver` 先以 payload 自身 fireAt 为基准重算当前已提交数据的计划，再用 `CourseReminderDelivery` 判定（身份一致且 fireAt 未变、或位置变化但同一 occurrence；已开始超过 10 分钟或不再存在则抑制），随后推进窗口 |
| 重建入口 | `ReminderRebuildReceiver`：BOOT_COMPLETED、MY_PACKAGE_REPLACED、TIME_SET、TIMEZONE_CHANGED、SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED；动作映射与重建处理函数抽到包内可直接测试 |
| 装配 | `ScheduleAppDependencies` 暴露 `reminderRegistry`／`reminderCoordinator`（Application 懒加载不变），供后续 ViewModel／设置页调用 |

## 有界窗口的选择依据与限制

iOS 受系统限制只能保留 60 条通知；Android 的 AlarmManager 没有同类硬限额，本批改为 **14 天滚动窗口、
最多 120 条闹钟**（`CourseReminderPlanner.DEFAULT_WINDOW`／`DEFAULT_LIMIT`）。依据：注册表体积、开机重建
耗时与耗电只与近期课程成比例；窗口由每个已触发闹钟（`ALARM_FIRED` 后重新协调）与各重建入口推进。
**限制**：若连续超过窗口长度既无闹钟触发也不打开应用，更远提醒会延迟到下一次入口；这正是下一批的候选
改进（更长窗口或周期性兜底），已如实记录。

## 测试

- Debug／Release JVM 各 **209 tests、0 failures／errors／skipped**（本批新增 40）：
  - `CourseReminderPlannerTest`（18）：每周／单双周、首末周与越界收敛、停课日、周末关闭、调课跟随星期
    ＋按自身教学周筛选（含奇偶）、0／180 分钟与跨日、过去与窗口外跳过、限流、同刻排序、教室正文格式、
    重复 ID 身份不碰撞、URI 转义、午休无关、时区差异、夏令时保持墙钟时间、无学期／无课程／未知节次。
  - `CourseReminderCoordinatorTest`（17）：排程与幂等、停用清空、通知被拒时清空且不排程、非精确降级与
    标记、获得精确能力后替换、提前量变化替换、单条排程失败与重试、取消失败保留重试、generation 被更新
    时 superseded 且零副作用、并发协调串行无重复、注册表重启恢复、数据不可读时平台与注册表零改动、
    投递判定（有效／旧课表／fireAt 变化／位置变化／过晚）。
  - `CourseReminderDeliveryTest`（5）：身份一致投递、fireAt 变化抑制、occurrence 不再存在抑制、位置变化
    仍投递、过晚抑制与非精确宽限。
- API 37 ARM64 `connectedDebugAndroidTest` **143 tests、0 failures／errors／skipped**（本批新增 10）：
  - `ReminderPlatformTest`（4）：通知渠道 id／名称／描述；重复 ID 的三条闹钟身份互不相同、取消其一不影响
    其余；`appops set SCHEDULE_EXACT_ALARM deny/allow` 后精确能力随之变化且两种模式都能注册；
    注册表在自己文件中往返、损坏数据退化为空、文件名与偏好文件不同。
  - `ReminderReceiverTest`（5）：真实 alarm broadcast 后通知按计划标题／正文出现并注册后续窗口；旧课表
    payload 被抑制、不产生通知；Manifest 声明两个 `exported=false` Receiver、四个权限且无 USE_EXACT_ALARM、
    受保护动作都能解析到重建 Receiver；五个重建入口分别排入闹钟；payload 在 Intent extras 中往返一致。
  - `ReminderEvidenceTest`（1）：产出 `docs/Android/evidence/p3-08-a08-reminders/` 的设备记录。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／18 warnings**；
  文档测试 71 tests OK、`documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

## 证据与限制

`docs/Android/evidence/p3-08-a08-reminders/`：真实通知栏截图（「证据课程」／「08:00–08:45 · A101
（可能延迟）」，提醒分区）＋设备记录（身份 URI、能力三项、渠道与通知内容、reconcile 计数与 generation、
未验证清单）＋README。截图用 `adb install` + `am instrument` 采集，因为 Gradle connected 任务结束会卸载
应用并清除通知。

**本环境无法验证，不得声称通过**：真实重启后的 BOOT_COMPLETED 投递；系统投递的时间／时区／包替换／精确
权限广播（受保护广播，应用无法发送）；Doze／休眠唤醒与精确／非精确真实投递时间；用户可见通知的观感
验收。

## 未纳入本批（A08 仍未完成）

提醒设置界面、运行时通知权限申请流程、课表编辑／保存后的自动重算、超出滚动窗口的再排程策略。

## 准确状态

A08 第一批已实现并自测（JVM 209、设备 143、lint 0 errors、截图与设备记录齐备）；**Sol 独立复审与用户验收
均未进行**。A08 其余部分、A10／A11、整个 P3 与完整 App 仍未完成、未授权。P3-06-R7 与 P3-04-R8（含各自
R1／R2）的既有复审与用户验收结论不变；P3-07-R1 新增警告框与文案仍待用户验收。
