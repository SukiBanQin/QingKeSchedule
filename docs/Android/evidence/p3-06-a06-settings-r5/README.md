# P3-06／A06 设置页视觉返修 R5 证据（2026-09-19）

## 背景与范围

R4 提交 `94012b3` 的两项 Sol 复审问题：

1. 设置页 01 面板展开「开始日期」日历时，日期行与 `InlineMonthCalendar` 之间缺少 iOS 的 `TerminalFormDivider`；
2. R4 的 `WeekControl`／`PeriodRow` 新样式同时改变了已验收的首次设置正文，`TerminalSectionHeader` 的粗黑等宽字体也影响了课程编辑器（跨范围影响）。

R5 只修这两项：给设置页日期控件加分隔线并补展开态测试与截图；把设置页样式改为专用组件（`SettingsDateControl`／`SettingsWeekControl`／`SettingsPeriodRow`），恢复首次设置正文与课程编辑器为 R3 视觉，`TerminalSectionHeader` 增加显式 `heavyIndex` 参数（只有设置页开启）。保留 R4 已正确的 01／02 单面板、青线、折叠箭头、顶部保存与底部保存卡片；未改业务逻辑、ViewModel、Room、协议、A07／A08／A10／A11，未覆盖 R4 历史证据。

只读基准：`/Users/takagisan/课表软件-IOS/ios/QingKeSchedule/Features/SemesterFormView.swift`（`chineseDatePicker` 展开日历前有 `TerminalFormDivider`）、`CourseStyle.swift`。

## 修正对照

| 复审问题 | iOS 依据 | 实现 | 测试 |
| --- | --- | --- | --- |
| 展开日历缺分隔线 | `chineseDatePicker`：`if calendarExpanded { TerminalFormDivider(); DatePicker(...) }` | 新增设置页专用 `SettingsDateControl`，展开时先在日期行与 `InlineMonthCalendar` 之间插入 `TerminalFormDivider(dark, "settings-semester-divider")`；日历仍在同一青线面板内 | `QingKeAppTest.settingsSemesterPanelKeepsTheInlineCalendarInsideTheSameRailPanel`：收起态 2 条分隔线 → 展开态 3 条，全部在 `settings-semester-panel` 内；日历也在面板内；新增分隔线的 y 区间落在日期行与日历之间；面板青线仍在；点选日历日期仍写回表单 |
| R4 跨范围影响首次设置正文 | iOS 用同一 `SemesterFormView`，但 Android 首次设置页正文是 R3 已验收设计 | 恢复 `DateControl`／`WeekControl`／`PeriodRow` 为 R3 实现（与 `9fdec22` 逐字一致），设置页改用 `SettingsDateControl`／`SettingsWeekControl`／`SettingsPeriodRow` | `QingKeAppTest.onboardingBodyKeepsTheAcceptedR3Controls`：折叠按钮文字仍为「收起节次设置（10 节）」、节次时间按钮文字仍只有时间、删除仍为「删除第 1 节」按钮、日期箭头仍是 `⌄` 文本、周数步进仍是 Material 按钮（宽度 ≥52dp，非 48dp 无边框字形） |
| R4 跨范围影响课程编辑器 | 课程编辑器分区使用同一 `TerminalSectionHeader` | `TerminalSectionHeader` 增加 `heavyIndex: Boolean = false`；只有 `TerminalFormSection`（设置页）传 `heavyIndex = true`，课程编辑器走默认 R3 度量 | `QingKeAppTest.courseEditorSectionIndexKeepsThePreviousMetrics`：设置页序号行高 < 课程编辑器序号行高（15sp/18sp 粗黑 vs 默认 16sp/24sp） |

## 验证结果

- 主机（工作区副本 `/private/tmp/qingke-r5`）：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **104 tests、0 failures／errors／skipped**；`lintDebug` **0 errors、20 warnings**。
- 设备（API 37 ARM64 AVD `emulator-5554`，1080x2400@420dpi）：定向 `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.qingke.schedule.ui.QingKeAppTest` **63 tests、0 failures**；完整 `connectedDebugAndroidTest` **92 tests、0 failures／errors／skipped**（R4 基线 89 + 本轮 3）。
- 文档：`python3 docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 与 `git diff --check` 通过。
- 测试取样注意事项（本轮踩到并修正）：滚动后又量取屏幕外节点的 `boundsInRoot` 会得到 0（首次设置页周数步进宽度一度为 0），断言前需先 `performScrollTo()`；可点击容器内的子节点需用 `useUnmergedTree = true` 定位。

## 截图清单（真实 debug 入口、真实数据）

数据：全新安装 → 首次设置保存默认学期（2026 秋季学期，2026-09-19 起 18 周、10 节）→ 设置页展开「开始日期」日历。

| 文件 | 状态 | 目视结论 |
| --- | --- | --- |
| `p3-06-r5-settings-light-calendar-expanded-api37.png` | 浅色、01 面板展开日历 | 学期名称 / 开始日期行 / **新增分隔线** / 月历都在同一青线面板内，青线贯穿整个面板高度 |
| `p3-06-r5-settings-dark-calendar-expanded-api37.png` | 深色、01 面板展开日历 | 同上；深色下日期行、分隔线、月历与选中日期（黄色）均清晰可读 |
| `p3-06-r5-onboarding-light-api37.png` | 浅色、首次设置正文 | 恢复 R3 视觉：方框学期名称字段、带青线的开始日期面板（`⌄` 文本箭头）、带边框的 −／+ 步进、带边框的「展开节次设置（10 节）」、底部黄色「保存并继续」；仅顶栏保留已授权的黑底黄字「保存」 |

## 限制

- 未实施 A07 教学日历、A08 上课提醒、A10 数据备份、A11 外观。
- 首次设置页正文只恢复视觉，未改任何行为与 testTag；R4 的 01／02 单面板、青线、折叠箭头、顶部保存、底部卡片保持不变（其证据见 `p3-06-a06-settings-r4/`）。
- 设备验证复用当时已在运行的 AVD；截图后已把窗口／密度／字体比例／夜间模式恢复为 AVD 默认。
