# 安卓与 iOS 功能对照及验收清单

## 状态与目标

更新日期：2026-09-10。用户已确认本文档及 P1 交付结果；P1-01 已完成修正和[独立审查](p1-01-review.md)。P1-03 的 API 37 工具链、源码／依赖边界和设备运行门槛均通过[专项复审](p1-03-review.md)，授权范围完成。用户已确认版本 1 未知字段两端严格拒绝，P1-04 及 P1-04-IOS-SYNC 均已通过独立复审；P1 已获用户确认。P2-01 已独立复审，P2-02-R1 聚焦修正已独立复审，但不等于 P2-02 用户验收或 P2 完成。[P2-03 表单草稿与保存评估](p2-03-form-drafts.md)已完成分析、尚待实施授权。

用户已确认的目标：安卓拥有当前 iOS App 的全部已有功能，业务行为一致，页面信息、布局和视觉风格尽量一致；系统交互采用安卓方式。本文用于落实“照着 iOS 做”，不重新设计产品。新增需求及对现有行为的修正需明确记录。

工作方式：官方／中转站各保留一个分析审查窗口（默认 Sol）和执行窗口（默认 Terra），人工交接；Luna 按需增加。当前分析窗口仅负责审查和维护文档；实施通过人工提示词交给执行窗口。

## 对照基准与资料优先级

iOS 源码参考提交：`dabdc2eae41143b1288ae5f9ba5eabcebce6de5a`（`fix(ios): complete custom control hit areas`）。这是可追踪的初始参考，不代表该版本已完成用户验收。

建档时工作区还有两处既有 iOS 工程配置改动，见 [交接状态](handoff.md)。这些配置不属于本次文档交付；首次构建对照前应核对其差异，不声称整个工作区等于该提交。

- 已确认产品目标决定要做什么；基准代码和测试说明当前行为；发现差异或疑似缺陷时记录并讨论，不盲目复制缺陷。
- [旧 iOS 产品文档](../ios-product-design.md) 和 [旧技术方案](../ios-technical-solution.md) 只作历史背景：其中“Web 为空”、特殊校历未实现等说法已落后于代码。
- [当前 Web README](../../web/README.md) 将 Web 定义为会话内 Demo；不能把模拟导入导出和固定日期状态作为安卓实现依据。
- iOS 后续变化按提交记录新增对照项及影响，不静默移动验收基准。

## 功能对照

下表 A01—A11 均未完整验收；P1-01 仅有工程和部分领域／协议基础，不能视为功能项已完成。“源码”表示行为依据，不表示本次运行过对应测试。

| 编号 | 功能及应保持的行为 | 主要源码依据 | 安卓验收方式 |
| --- | --- | --- | --- |
| A01 | 无学期时引导设置或导入，配置后进入今日、课表和设置 | [AppRootView](../../ios/QingKeSchedule/Features/AppRootView.swift) | 空安装创建学期、导入、重启后进入正确页面 |
| A02 | 今日按课程时间排序，展示时间、教师、教室及状态，突出正在上课或下一门课 | [TodayScheduleView](../../ios/QingKeSchedule/Features/TodayScheduleView.swift)、[展示规则](../../ios/QingKeSchedule/Features/SchedulePresentation.swift) | 固定相同日期、时区和课表，对照内容、状态、排序和空状态 |
| A03 | 周课表切周、定位当前周，正确绘制跨节次课程；重叠课程可见、可点击 | [WeekScheduleView](../../ios/QingKeSchedule/Features/WeekScheduleView.swift)、[展示规则](../../ios/QingKeSchedule/Features/SchedulePresentation.swift) | 第一周、末周、学期外、重叠课程及午休分隔对照 |
| A04 | 新增、编辑、删除课程，名称、教师、预设与自定义颜色；多个上课安排 | [CourseEditorView](../../ios/QingKeSchedule/Features/CourseEditorView.swift)、[CourseDraft](../../ios/QingKeSchedule/Features/CourseDraft.swift) | 完整增改删、重启保留、安排增删、空值与格式错误 |
| A05 | 同课程新增完全重复安排受阻，跨课程冲突提示但可确认保存；未保存退出和删除有确认 | [CourseDraft](../../ios/QingKeSchedule/Features/CourseDraft.swift)、[规则](../../ios/QingKeSchedule/Domain/ScheduleRules.swift) | 重复、历史重复编辑、冲突取消／继续、放弃编辑分别验证 |
| A06 | 单个当前学期、名称、开始日期、总周数、每日节次及时间，节次区域展开收起 | [SemesterFormView](../../ios/QingKeSchedule/Features/SemesterFormView.swift)、[SemesterDraft](../../ios/QingKeSchedule/Features/SemesterDraft.swift) | 创建修改、增删节次、重叠时间、缩短学期影响已有课程时的校验 |
| A07 | 周末停课、指定日期停课、调课日按指定星期上课，午休设置 | [教学日历设置](../../ios/QingKeSchedule/Notifications/ReminderSettings.swift) | 停课与调课优先级、单双周组合、今日／周表／通知一致 |
| A08 | 提醒开关、预设与自定义提前时间；修改课表后更新、删除后取消 | [通知规划与协调](../../ios/QingKeSchedule/Notifications/NotificationScheduling.swift)、[提醒界面](../../ios/QingKeSchedule/Features/ReminderSettingsSection.swift) | 拒绝权限仍可使用课表；测试时间、内容、取消和真机投递 |
| A09 | 离线保存学期及课程，失败有反馈，失败替换保留旧数据 | [SwiftData 仓库](../../ios/QingKeSchedule/Persistence/SwiftDataScheduleRepository.swift) | 重启、离线增改删、模拟写入失败和替换失败 |
| A10 | JSON 导入先校验、预览、确认后整体替换；导出课表备份 | [数据传输](../../ios/QingKeSchedule/Transfer/ScheduleDataTransfer.swift)、[文件界面](../../ios/QingKeSchedule/Features/DataTransferSection.swift) | 有效／无效／未知版本／超大文件、取消、写入失败及双向导入 |
| A11 | 跟随系统、浅色、深色；保持品牌、颜色、信息层级和终端风格 | [CourseStyle](../../ios/QingKeSchedule/Features/CourseStyle.swift)、[应用状态](../../ios/QingKeSchedule/State/ScheduleAppState.swift) | 同数据截图对照、小屏、大字体、触控范围和读屏检查 |

## 关键业务边界

- 公历、本地时区、星期一开周；学期开始日期所在周是第 1 周。单双周按教学周判断，不按自然周编号。
- 星期字段为 1 到 7（周一到周日）；安排同时满足周次范围与重复规则才生效。学期外今日不显示课程。
- 当前状态按分钟判断：开始前未开始，结束分钟仍进行中，之后结束。需覆盖跨分钟刷新和返回前台；若基准存在刷新问题，记录为待决差异。
- 冲突需星期相同、节次范围相交且至少有一个共同生效的教学周。保存冲突与显示重叠分别检查。
- 教学日历解析优先级：指定停课日、指定调课日、周末停课、正常星期。当前调课表示“该日期按某星期课表上课”，仍按该日期所属教学周筛选；不等于任意课程跨周搬移。
- 当前上限：学期 1 至 52 周、每日 1 至 20 节；颜色为六位十六进制 `#RRGGBB`；提醒提前量 0 至 180 分钟，默认关闭、提前 10 分钟。
- 午休是展示设置，不应擅自改变节次时间或变成停课规则。

## 数据迁移范围

[版本 1 协议](../../ios/Shared/schedule-data.schema.json) 只包含 `schemaVersion`、`semester`、`courses`、`updatedAt`。现有 iOS 停课、调课、午休、提醒和外观设置另存于设备偏好，不在备份中。

安卓需兼容版本 1 基础课表，手动设置教学日历等功能也必须具备。版本 1 文件相同但设备教学日历设置不同，两端显示和提醒可能不同，验收时必须同时对齐设置。

**待用户决定 D01：**首版是否新增携带教学日历设置的协议版本并同步修改 iOS 导出／导入。当前未授权扩展协议；不能把“完整功能复现”写成“现有备份已支持全部设置迁移”。通知授权始终由每台设备单独获取。

## 安卓系统差异与待确认范围

- 权限、文件选择、分享面板和返回操作使用安卓系统能力，业务结果保持一致；不强求系统弹窗像素一致。
- iOS 当前最近 60 条提醒是平台实现策略。安卓需设计可恢复的调度方案，不能直接把 60 条上限和仅前台补充作为验收要求。
- 已确认版本 1 未知字段策略：顶层及所有嵌套协议对象均严格拒绝未声明字段；共享 schema 与 Android 已符合，iOS 导入边界由 P1-04 统一。
- D02：已确认包名 `com.qingke.schedule`、最低 API 26。P1-03 已将 `compileSdk`／`targetSdk` 升至 API 37.0，并采用 AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0、JDK 17；主机侧构建与测试、API 37 设备安装启动及 Android 17 行为变化适用性记录均已完成专项复审。目标机型与后续完整验证系统清单待定。
- 待决定 D03：精确提醒不可用时采用什么降级行为和文案，不能显示已可靠准点安排。
- D04：P1 已确认仅做个人 debug 安装验证，不做商店签名；正式发行范围仍待定。
- 不新增账号、云同步、OCR／教务系统／Excel 导入、桌面组件等基准未有功能，除非用户另行确认。

## 验收记录规则

每个 A 编号记录实现提交、验证命令／设备、结果和证据位置。流程状态为：未开始 → 已实现 → 已验证 → 已审查 → 用户已验收；失败或阻塞单独说明，不能跨级宣称完成。

先执行共享数据与领域规则测试，再验证界面与系统功能。同日期、时区、课表、教学日历设置下对照两端，截图和真机检查补充源码分析。发现基准疑似缺陷时，记录现象、依据、影响及处理决定。
