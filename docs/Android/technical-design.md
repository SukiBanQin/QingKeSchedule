# 安卓技术方案

## 文档状态

更新日期：2026-09-11。状态：用户已确认方案及 P1、P2 阶段结果；P1-01 及两轮修正已完成[独立审查](p1-01-review.md)。P1-03 已升级到经 D02 核对的 API 37.0 组合，工具链、源码／依赖边界和 API 37 设备运行门槛均通过[专项复审](p1-03-review.md)，授权范围完成；P1-04 及 P1-04-IOS-SYNC 均已通过独立复审，两个开发分支已同步。P2-01、P2-02-R1 已独立复审；P2-03 已通过最终独立复审。P2-04 的[应用状态与生产依赖装配](p2-04-application-state-composition.md)已通过[当前分析角色同窗口复审](p2-04-review.md)，用户接受组织性独立限制并确认 P2。P3-01 [今日与周课表展示模型](p3-01-schedule-presentation.md)已实现、测试、通过最终独立复审并获用户确认；P3-02 [应用壳、状态加载与首次学期设置](p3-02-app-shell-onboarding.md)已分析授权待实施。P3、功能页面整体和完整应用验收仍未完成。

产品要求见 [功能对照及验收清单](product-baseline.md)，阶段安排见 [实施计划](implementation-plan.md)，实时状态见 [交接记录](handoff.md)。P2-01 的可执行存储／状态契约见 [专项分析](p2-01-persistence-state.md)，P2-03 的纯 Kotlin 草稿／冲突边界见 [表单草稿分析](p2-03-form-drafts.md)，P2 收口的联合状态与生产入口见 [P2-04 分析](p2-04-application-state-composition.md)；今日／周表纯 Kotlin 展示规则见 [P3-01 分析](p3-01-schedule-presentation.md)，首个 Compose／状态／持久化垂直切片见 [P3-02 分析](p3-02-app-shell-onboarding.md)。

## 建议技术路线

保留 iOS 原生实现，在 `Android/` 新建 Kotlin 原生安卓项目。采用 Jetpack Compose、ViewModel 与 StateFlow 管理 UI 和状态，Room 保存结构化课表，DataStore 保存偏好设置，Kotlin 序列化库负责 JSON。P1-01 已固定依赖并验证可构建；P1-03 已采用 API 37.0、AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0 与 JDK 17，保持 minSdk 26。AGP 9 built-in Kotlin、serialization 和 Compose plugin 迁移已通过主机侧构建复核；Room 已在 P2-01 接入，DataStore 偏好边界已在 P2-02 以 `androidx.datastore:datastore-preferences:1.2.1` 接入，其 P2-02-R1 聚焦修正已通过独立复审。

以现有 Mac 为主力，安卓真机补充模拟器；Windows 可按需要承担安卓开发和测试。Gradle Wrapper 提供 macOS 与 Windows 对应入口，不使用个人绝对路径。包名 `com.qingke.schedule`、最低 API 26 及首轮个人 debug 验证已确认，正式发布范围待定。

参考：[Android 架构建议](https://developer.android.com/topic/architecture/recommendations)、[Jetpack 组件](https://developer.android.com/jetpack)、[Android Studio 安装](https://developer.android.com/studio/install)。

## 模块边界

首版可在一个应用模块中按职责分包，避免为目录形式提前拆分大量 Gradle 模块：

| 层 | 职责 | iOS 对照 |
| --- | --- | --- |
| 界面 | 今日、周表、编辑、学期、提醒、数据备份、外观 | `Features/` 中的 View 和样式 |
| 展示与状态 | 固定时钟输入、展示模型、表单草稿、保存／导入协调 | `SchedulePresentation`、`CourseDraft`、`ScheduleAppState` |
| 领域 | 数据类型、日期、单双周、冲突、校验、教学日历、未来课程发生 | `Domain/`、教学日历设置、通知规划逻辑 |
| 存储 | 课表事务、排序、关系、版本迁移、偏好设置 | `Persistence/`、各 SettingsStore |
| 系统适配 | 通知调度、权限、文件选择和分享、生命周期 | `Notifications/`、`Transfer/` |

界面不直接写数据库或安排闹钟；领域逻辑不依赖 Android UI／Context。日期计算采用明确时区及可注入时钟，使用日期类型做日历运算，不以固定秒数计算教学周。Android UI 持续显示时的时间刷新、前台恢复和日期切换需明确处理并测试。

## 跨端协议与校验

- 继续使用 [共享 Schema](../../ios/Shared/schedule-data.schema.json) 和 [fixtures 清单](../../ios/Shared/fixtures/manifest.json)，不复制一套会自行漂移的协议。
- JSON 字段保留 `repeat`、`dayOfWeek`、`classroom` 等原名；日期 `yyyy-MM-dd` 表示本地日期，时间 `HH:mm`，`updatedAt` 为 UTC ISO 8601。星期一为 1。
- 版本 1 必需包含 `semester`，允许显式 `null`；无学期不得有课程。未知版本和非法业务数据必须拒绝。
- 输入大小与 iOS 当前限制保持一致：最多 5 × 1,048,576 字节。对 ContentResolver 流执行有界读取，不只信任文件大小元数据。
- 先解码和业务校验，再预览、确认、事务替换；取消、解析失败或写入失败保留原课表。
- 版本 1 不携带教学日历等偏好。待 D01 决定是否扩展协议，未决定前不修改 iOS 或共享文件。
- 版本 1 未知字段按共享 schema 严格拒绝，适用于顶层和全部嵌套协议对象；Android 已符合，iOS 导入边界由 P1-04 统一。重复 ID、节次顺序和数字类型继续按已核实行为处理，不自行加严。

## 存储与导入导出

Room 建议保存元数据、单个学期、节次、课程和多个安排，保留稳定 ID 与顺序字段。课程删除级联删除安排。数据替换和元数据更新在事务内进行，测试注入失败确保旧数据仍可读取。

P2-01 固定采用 `androidx.room` 2.8.4 与 KSP 2.3.11，保持现有 AGP 9 built-in Kotlin
和工具链不变。Room 内部主键与 DTO ID 分离，避免把 P1 已接受的重复 ID 变成数据库新增
限制；所有数组使用显式顺序字段。仓库 suspend 写操作返回已提交完整聚合，状态层成功后直接
发布该快照，不执行可能产生“磁盘已提交、内存仍旧”的第二次读取。空库返回固定默认聚合；
部分／非法记录报损坏且不清库。详细失败、并发和测试契约以专项分析为准。

偏好中保存教学日历、提醒提前量和外观；系统通知授权不以偏好布尔值代替。P2-02 已采用
DataStore 建立可替换的偏好存储接口，覆盖 `AppearanceMode`、提醒开关／提前量／自定义标记、
以及教学日历的周末停课、停课日期、调课日期和午休设置。读取缺失、可识别磁盘损坏或未知枚举值时
回退到基准默认值；普通 I/O／写入异常和协程取消必须向调用方传播。写入后关闭并重建 DataStore
应恢复同一规范化设置。偏好自身独立版本化，不能把 DataStore 与 Room 的两个独立写入误称为跨存储
原子事务；P2-02 不接页面、`ScheduleAppState` 或通知调度。

P2-02 的键名和编码格式须集中定义并保留迁移余地；列表字段必须保持稳定顺序或按基准规则
规范化，日期继续使用 `yyyy-MM-dd`，时间继续使用 `HH:mm`。实现不得改变 iOS 已有默认值、
教学日历优先级、提醒提前量 0—180 分钟范围或外观三态语义。

DataStore 和上述偏好不属于 P2-01，已由独立授权的 P2-02 实现；该实现不提前决定跨存储恢复策略，
其 P2-02-R1 聚焦修正已通过独立复审。这不表示 P2-02 已用户验收或 P2 完成。

P2-03 将表单草稿定义为纯 Kotlin 转换／评估层：课程草稿负责默认安排、字段规范化、
dirty 判断、多安排和新增完全重复阻止；学期草稿负责季节名称、默认十节、增删和连续编号。
基础合法性复用 `ScheduleValidator`。`ScheduleRules` 补足同星期、闭区间节次相交和共同单双周的
冲突计算；冲突只返回给后续页面决定是否仍然保存，草稿层不调用仓库。日期和 ID 生成可注入，
不让 JVM 测试依赖当前时间、时区或随机 UUID。详细历史重复兼容、不包含项和测试矩阵以专项分析为准。

P2-04 负责把两个已审查仓库连接到一个可观察应用状态：联合加载在取得课表和偏好后一次发布，
任一普通失败不得发布另一端新快照，取消恢复完整前态并传播；课表和偏好写操作在状态边界串行，
各自只发布仓库返回的已提交快照，不声称跨存储原子。生产侧由 manifest 注册的 Application 级
惰性容器提供唯一 Room／DataStore 依赖，状态仍只依赖接口并留给 P3 的 Activity 级 ViewModel 持有。
P2-04 不修改 `MainActivity`、不主动加载、不新增页面或通知副作用，具体测试和关闭重建门槛以专项分析为准。

P3-02 由 Activity 级 ViewModel 持有唯一 `ScheduleAppState`，在 `viewModelScope` 中只启动一次初始加载，
Compose 以生命周期感知方式订阅状态。根界面按 `NOT_LOADED／LOADING`、`FAILED`、`READY` 且无学期、
`READY` 且有学期映射为加载、失败重试、首次设置和主壳；保存错误保留当前主体，不得把它误映射成加载失败。
首次设置复用 `SemesterDraft`，其可观察界面快照由 Activity 级状态持有以跨越 Activity 重建；成功后只由已
提交状态驱动进入主壳，失败保留草稿。三标签壳和最小主题不提前接入今日／周表或完整设置，真实 API 37
验收须覆盖清数据冷启动、保存以及强停后重启恢复。详细依赖、test tag 和排除范围以 P3-02 专项分析为准。

通过 Android 系统文件选择／创建文档和分享接口处理 JSON，不导出平台数据库文件。文件取消不显示成功；不可写、无学期、内容无效时给出明确反馈。

## 提醒设计与验证风险

业务层将学期、安排、单双周、停课调课设置及提前量转换为未来课程提醒，系统层负责提交与取消。使用稳定的课程发生标识，调课包含日期，避免覆盖同周其他发生；Android PendingIntent 身份需验证不冲突，不能仅依赖可能碰撞的字符串哈希。

建议以 AlarmManager 处理面向用户的课程定时提醒，NotificationManager 显示通知；WorkManager 如有必要用于延后校验或维护，不能承诺精确投递。具体滚动窗口／下一次触发策略在原型验证后确定。

需覆盖通知权限、通知渠道状态、精确闹钟能力检查、重启、时间／时区变化、权限变化、进程结束及重新进入应用后的重建。取消和重建应可重复执行，失败不得破坏已保存课表；必要时序列化协调并忽略旧状态任务，防止快速编辑留下过期提醒。

精确能力不可用的降级及文案属于待定 D03；不能承诺强制停止应用后仍一定提醒，也不能用常驻服务掩盖未处理的系统限制。iOS 的最近 60 条策略不直接作为安卓方案。

参考：[闹钟调度](https://developer.android.com/develop/background-work/services/alarms)、[通知权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)。实施时重新核实目标版本要求；真实可靠性以目标设备测试为准。

## 视觉与平台交互

按基准的品牌、终端风格、布局、课程颜色和信息层级实现 Compose 界面。系统文件面板和权限界面采用 Android 默认机制；返回手势、键盘遮挡、安全区域、字体缩放和读屏需适配。先选择相同数据做页面对照，再验收小屏、深色和大字体，不以网页模拟状态作为真实业务参考。

## 测试与交付

- JVM 单元测试：教学周、边界日期、单双周、状态分钟边界、冲突、草稿校验、教学日历优先级及通知规划。
- 契约测试：直接读取共享有效／无效 fixtures，保留 `semester: null`，验证版本、颜色、导入导出往返；新增规则用例应有明确预期。
- 存储集成测试：关系和顺序、增改删、事务回滚、重启和迁移。
- Compose／设备测试：覆盖 A01 至 A11 核心流程，确认／取消、错误反馈及无障碍。
- 真机测试：权限拒绝与变化、锁屏／休眠、重启、长时间未打开、时间变化、修改删除后旧提醒取消，以及系统文件导入分享。
- 建工程后提供可复现构建和测试命令。现有 `Android/gradlew` 与 `gradlew.bat`；配置 JDK 17 和有效 SDK 路径后，在 `Android/` 运行 `./gradlew assembleDebug` 和 `./gradlew test`。本轮具体环境和结果见审查记录。
- 每项交付记录提交、命令、结果和未验证限制；对照清单仍需人工验收，不以自动测试代替所有设备检查。

当前文档验证命令（仓库根目录执行）：

```bash
python3 docs/tests/android-documentation.test.py
bash docs/tests/documentation.test.sh
bash docs/tests/repository-layout.test.sh
git diff --check
```
