# P3-06／A06 视觉返修 R6 证据（2026-09-19）

## 背景与范围

R6 把首次设置（onboarding）改成与正式设置页一致的终端风格，并用共享的终段时间选择器替换两页共用的 Android `TimePickerDialog`：

1. 首次设置复用 01 学期信息／02 每日节次的单一青线面板、内部横线、节次行与添加入口；新增 iOS 对应的品牌头 `SETUP / 00`（tag `onboarding-brand-header`）与 `FIRST BOOT`／首次设置／`INIT 00` 介绍区（tag `onboarding-terminal-header`／`onboarding-status-tag`／`onboarding-title`）；底部使用「创建课表／INITIALIZE TERMINAL」黑色保存卡片（tag `semester-save`）；顶部仍是黑底黄字「保存」。
2. 移除 `TimePickerDialog`，改为共享的 `TerminalTimePickerOverlay`：顶部 `TIME SELECT` + 目标标题（如「第 1 节 开始时间」）、大号 `HH:MM` 读数、24 小时（00—23）与 00—59 分钟双列数字选择、取消／确认按钮；取消与系统返回只关闭不写入，确认才调用 `updatePeriodStart`／`updatePeriodEnd`。

共享实现（避免两页再次漂移）：`TerminalSemesterForm(prefix, …)` 同时渲染两页的 01／02 面板、节次行与提交卡片；`TerminalToolbar`／`TerminalIntro(onboarding)`／`TerminalNameField`／`TerminalDateControl`／`TerminalWeekControl`／`TerminalPeriodRow`／`TerminalCommitCard`／`TerminalTimePickerHost`／`TerminalTimePickerState` 全部只有一份实现；页面差异仅剩前缀（`onboarding-`／`settings-` tag）、品牌头代码、介绍区文案与卡片文案。

时间选择状态按 `PeriodTimeTarget(periodId, PeriodTimeField.START/END)` 稳定标识：`TerminalTimePickerHost` 只在目标节次仍存在时渲染，增删节次不会串位；行本身不保存任何位置索引。

范围限制：未改业务逻辑、ViewModel、Room、DataStore、共享协议、iOS、Web、`main`；未实施导入、教学日历、提醒、备份、外观（A07／A08／A10／A11）；时间先后与重叠等校验规则未改。

## 验证结果

- 主机（工作区副本 `/private/tmp/qingke-r6`）：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **104 tests、0 failures／errors／skipped**；`lintDebug` **0 errors、20 warnings**。
- 设备（API 37 ARM64 AVD `emulator-5554`，1080x2400@420dpi）：定向 `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.qingke.schedule.ui.QingKeAppTest` **65 tests、0 failures**；完整 `connectedDebugAndroidTest` **94 tests、0 failures／errors／skipped**（R5 基线 92；本轮删除 1 项系统 TimePicker 用例、改写 1 项首次设置用例并新增 3 项终端选择器用例，净增 2）。
- 文档：`python3 docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 与 `git diff --check` 通过。

新增／改写的用例：`onboardingUsesTheSharedTerminalPanelsAndBrandHeader`（品牌头、FIRST BOOT／INIT 00、01／02 青线面板与内部横线计数、节次行与添加入口同属面板、创建课表卡片契约，浅深色各一次）、`terminalTimePickerWritesOnlyOnConfirmAndIgnoresCancelAndBack`（开始／结束、确认写入、取消不写、系统返回不写）、`terminalTimePickerKeepsItsTargetAfterPeriodsAreAddedOrRemoved`（增删节次后仍写到正确 periodId，标题与初始值随行变化）、`terminalTimePickerStaysUsableAcrossThemesFontScalesAndSmallScreens`（浅深色 × 100%／130% × 320dp 窄屏，取消／确认 ≥48dp，23 时与 59 分可达）；`settingsPeriodsToggleAddRemoveAndTimePickerUseRealCallbacks` 改为使用共享选择器。

## 截图清单（真实 debug 入口、真实数据）

数据：全新安装 → 首次设置（默认学期 2026 秋季学期，2026-09-19 起 18 周、10 节）→ 创建课表 → 设置页。

| 文件 | 状态 | 目视结论 |
| --- | --- | --- |
| `p3-06-r6-onboarding-light-api37.png` | 浅色首次设置 | 黑底黄字「保存」+ `SETUP / 00` 品牌头 + 黄色 FIRST BOOT／首次设置／INIT 00 + 01／02 青线面板与内部横线 + 「创建课表／INITIALIZE TERMINAL」卡片 |
| `p3-06-r6-onboarding-dark-api37.png` | 深色首次设置 | 同上，深色下面板、青线、内部横线与卡片均清晰 |
| `p3-06-r6-onboarding-periods-expanded-light-api37.png` | 浅色、首次设置展开节次 | 折叠行（收起节次设置／10 节／上箭头）与节次行、内部横线、添加入口同属一个青线面板 |
| `p3-06-r6-onboarding-font130-api37.png` | 浅色 130% 首次设置 | 文案折行但无水平裁切，面板与青线完整 |
| `p3-06-r6-settings-periods-expanded-light-api37.png` | 浅色正式设置展开节次 | 与首次设置同构（共享组件），节次行与时间单元一致 |
| `p3-06-r6-time-picker-start-light-api37.png` | 浅色时间选择器（开始） | `TIME SELECT`、「第 1 节 开始时间」、大号 08:00、小时／分钟双列（当前值高亮）、取消／确认 |
| `p3-06-r6-time-picker-end-dark-api37.png` | 深色时间选择器（结束） | 「第 1 节 结束时间」、08:45、深色面板与按钮可读 |
| `p3-06-r6-time-picker-small-api37.png` | 小屏 720x1280@320 时间选择器 | 选择器完整落在屏内，双列与按钮不被裁切 |

## 限制

- 未实施 A07 教学日历、A08 上课提醒、A10 数据备份、A11 外观，首次设置页也不含导入入口。
- 首次设置顶部按钮文案沿用「保存／保存中」（iOS 为「继续」），以保证既有保存行为与既有用例稳定；卡片文案已按 iOS 改为「创建课表／INITIALIZE TERMINAL」。
- 设备验证复用当时已在运行的 AVD；截图后已恢复 AVD 默认窗口／密度／字体比例／浅色模式。
