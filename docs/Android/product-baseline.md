# 安卓与 iOS 功能对照及验收清单

## 状态与目标

更新日期：2026-09-15。用户已确认本文档及 P1、P2 阶段结果；P1-01 已完成修正和[独立审查](p1-01-review.md)。P1-03 的 API 37 工具链、源码／依赖边界和设备运行门槛均通过[专项复审](p1-03-review.md)，授权范围完成。用户已确认版本 1 未知字段两端严格拒绝，P1-04 及 P1-04-IOS-SYNC 均已通过独立复审。P2-01、P2-02-R1 已独立复审，[P2-03 表单草稿与保存评估](p2-03-form-drafts.md)已通过最终独立复审；[P2-04 应用状态与生产依赖装配](p2-04-application-state-composition.md)已通过[当前分析角色同窗口复审](p2-04-review.md)，用户接受其组织性独立限制并确认 P2。[P3-01 今日与周课表展示模型](p3-01-schedule-presentation.md)和 [P3-02 应用壳、状态加载与首次学期设置](p3-02-app-shell-onboarding.md)均已实现、测试、最终独立复审并获用户确认；[P3-03 今日课表页面与实时刷新](p3-03-today-schedule.md)及 R1 已完成技术复审，[P3-03-R2 今日页与共享主壳视觉对齐](p3-03-r2-visual-alignment.md)已实施、完成 API 37 设备验证、通过 Sol 技术独立复审并获用户视觉验收。[P3-04 课程新增、编辑与删除](p3-04-course-editor.md)已实现、测试、完成 API 37 生产验证、通过 Sol 技术独立复审，并于 2026-09-15 获用户产品／视觉验收。P3-04／A04／A05 当前实现范围的验收门槛已关闭；该确认不扩大为其他 A 项、整个 P3 或完整 App 验收。

最新状态（2026-09-18）：P3-05／A03 周课表已实现、测试、通过 Sol 技术独立复审并经用户确认视觉通过（实现提交 `e3321bd`，验收收口 `5fb0bd2`），A03 当前范围的验收门槛关闭。P3-06／A06 完整设置页与学期编辑已实现并自测（`83d1a9f`），首轮复审未通过（`6f25e6b`），第二轮复审发现的节次身份基准生命周期问题在 R3（`9fdec22`）修正并**已通过 Sol 技术复审**；但**用户视觉验收未通过**，指出设置页与 iOS `TerminalFormSection` 的结构与视觉差距（01／02 未合并为单一整体面板、折叠与保存入口样式、底部卡片过高与箭头过小）。R4（`94012b3`）已实施这四项视觉返修；Sol 复审又发现展开日历缺 `TerminalFormDivider` 与设置页样式越范围影响首次设置正文／课程编辑器，R5（`4df81f6`）已修正这两项；R6 又按用户要求把首次设置改成与正式设置页一致的终端风格（SETUP / 00、FIRST BOOT／INIT 00、创建课表卡片），并以共享终段时间选择器（24 小时 + 00—59 分钟双列、取消／确认、系统返回等同取消）替换两页的 Android TimePickerDialog，**等待 Sol 复审与用户视觉验收，尚未通过复审，也未获用户验收**。A06—A11、整个 P3 与完整 App 仍未验收，后续阶段未授权。

准确状态是 P3-03-R2、P3-04 与 P3-05／A03 的用户视觉验收均已通过；P3-04 按“源课程位置＋打开时数据指纹”精确操作
重复 ID 课程，业务 ID、导入协议和共享 schema 不变，iOS 同步未授权。A03／A04／A05 当前实现范围已经完成，
A06 已实施并自测，技术复审已通过（R3），视觉返修 R6 已实施、等待复审与用户视觉验收，
但 A01—A11 尚未全部验收，A02、A06—A11、整个 P3 和完整 App 仍未完成验收；后续阶段未授权。

用户已确认的目标：安卓拥有当前 iOS App 的全部已有功能，业务行为一致，页面信息、布局和视觉风格尽量一致；系统交互采用安卓方式。本文用于落实“照着 iOS 做”，不重新设计产品。新增需求及对现有行为的修正需明确记录。

工作方式：DeepSeek 主力开发、自测，Sol 按需独立审查，人工按需交接；不默认创建子 Agent。当前分析审查窗口负责必要的方案讨论、实际 diff 与证据审查，不直接修改应用代码；实施由 DeepSeek 在已授权范围内完成，关键任务保留 Sol 独立审查。原 Codex 窗口保留作回退，由用户决定是否启用。

## 对照基准与资料优先级

iOS 当前源码参考提交：`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`（`fix(ios): preserve week following before semester`）。
最初建档参考为 `dabdc2e`；后续 iOS 的时间刷新和周表修正已成为当前行为基准。Android 分支中保留的 iOS
文件可能是旧副本，跨端分析应读取 `origin/IOS` 对应提交，不能只读当前工作树。

建档时工作区还有两处既有 iOS 工程配置改动，见 [交接状态](handoff.md)。这些配置不属于本次文档交付；首次构建对照前应核对其差异，不声称整个工作区等于该提交。

- 已确认产品目标决定要做什么；基准代码和测试说明当前行为；发现差异或疑似缺陷时记录并讨论，不盲目复制缺陷。
- [旧 iOS 产品文档](../ios-product-design.md) 和 [旧技术方案](../ios-technical-solution.md) 只作历史背景：其中“Web 为空”、特殊校历未实现等说法已落后于代码。
- [当前 Web README](../../web/README.md) 将 Web 定义为会话内 Demo；不能把模拟导入导出和固定日期状态作为安卓实现依据。
- iOS 后续变化按提交记录新增对照项及影响，不静默移动验收基准。

## 功能对照

下表 A01—A11 尚未全部验收；其中 A04／A05 当前实现范围已随 P3-04 完成并获用户验收。P1-01 仅有工程和部分领域／协议基础，不能视为所有功能项已完成。“源码”表示行为依据，不表示本次运行过对应测试。

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
- 当前 iOS 状态按秒判断：开始前未开始，开始时刻至结束时刻前进行中，结束时刻及之后结束。页面处于前台
  时每秒刷新，返回前台立即刷新，并支持只更新时间的下拉刷新；Android 在 P3-03 同步这一当前基准。
- 冲突需星期相同、节次范围相交且至少有一个共同生效的教学周。保存冲突与显示重叠分别检查。
- 教学日历解析优先级：指定停课日、指定调课日、周末停课、正常星期。当前调课表示“该日期按某星期课表上课”，仍按该日期所属教学周筛选；不等于任意课程跨周搬移。
- 当前上限：学期 1 至 52 周、每日 1 至 20 节；颜色为六位十六进制 `#RRGGBB`；提醒提前量 0 至 180 分钟，默认关闭、提前 10 分钟。
- 午休是展示设置，不应擅自改变节次时间或变成停课规则。
- Android 的视觉目标是除系统交互和平台渲染差异外对齐当前 iOS App，而不是采用 Material 默认样式重新设计。
  App 图标以 `source/cover.png` 为准，页面 Logo 以 `source/qingke-logo-q-matrix-preview.png` 为准；背景、
  组件层级、相对尺寸、间距、颜色、边框、选中态和信息层级应通过同状态跨端截图核对。依赖尚未实现功能的
  控件不得做成无效入口，应记录为临时差异并在功能接通时按 iOS 原位补齐。

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
