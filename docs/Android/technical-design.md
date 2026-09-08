# 安卓技术方案

## 文档状态

更新日期：2026-09-08。状态：用户已确认方案；P1-01 及两轮修正已完成[独立审查](p1-01-review.md)。P1-03 已升级到经 D02 核对的 API 37.0 组合，工具链、源码／依赖边界和 API 37 设备运行门槛均通过[专项复审](p1-03-review.md)，授权范围完成；这不代表完整应用、P1 阶段或用户验收完成，本轮不进入 P2。

产品要求见 [功能对照及验收清单](product-baseline.md)，阶段安排见 [实施计划](implementation-plan.md)，实时状态见 [交接记录](handoff.md)。

## 建议技术路线

保留 iOS 原生实现，在 `Android/` 新建 Kotlin 原生安卓项目。采用 Jetpack Compose、ViewModel 与 StateFlow 管理 UI 和状态，Room 保存结构化课表，DataStore 保存偏好设置，Kotlin 序列化库负责 JSON。P1-01 已固定依赖并验证可构建；P1-03 已采用 API 37.0、AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0 与 JDK 17，保持 minSdk 26。AGP 9 built-in Kotlin、serialization 和 Compose plugin 迁移已通过主机侧构建复核；Room/DataStore 尚未接入。

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
- 开发时明确记录未知字段处理、重复 ID、节次顺序和数字类型等边界的实际兼容行为。Schema 与 Swift 解码／校验可能并非完全等价，不能只凭 JSON 能解析就声称兼容；差异应先测试、记录，再确定处理。

## 存储与导入导出

Room 建议保存元数据、单个学期、节次、课程和多个安排，保留稳定 ID 与顺序字段。课程删除级联删除安排。数据替换和元数据更新在事务内进行，测试注入失败确保旧数据仍可读取。

偏好中保存教学日历、提醒提前量和外观；系统通知授权不以偏好布尔值代替。数据库和偏好分别版本化；如未来需要同时导入二者，先设计跨存储恢复策略，不能把两个独立写入误称为原子事务。

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
