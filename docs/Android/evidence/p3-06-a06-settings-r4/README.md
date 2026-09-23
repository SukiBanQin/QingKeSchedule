# P3-06／A06 设置页视觉返修 R4 证据（2026-09-18）

## 背景与范围

R3 提交 `9fdec22` 已通过 Sol 技术复审，但**用户视觉验收未通过**：设置页与 iOS `TerminalFormSection` 的结构与视觉仍有差距（用户备忘录“APP 还有的问题”第 1、2、3、5 项）。R4 只改设置页／引导页顶栏的视觉与其 Compose 测试，未改业务规则、ViewModel、Room、DataStore、共享协议、iOS、Web 或 `main`，也未实施 A07／A08／A10／A11。

只读基准：`/Users/takagisan/课表软件-IOS/ios/QingKeSchedule/Features/SemesterFormView.swift`、`CourseStyle.swift`（`TerminalFormSection`、`TerminalSectionHeader`、`TerminalFormDivider`、`settingsHeader` 的保存入口与底部 `saveButton`）。

## 备忘录条目 → 实现对照

| 备忘录条目 | iOS 依据 | Android 实现 | 测试 |
| --- | --- | --- | --- |
| 1「01 学期信息」合并为整体面板 | `TerminalFormSection(index: "01")`：亚克力表面 + 1px `panelEdge` 边框 + 左侧 3px 青色竖线 + `TerminalFormDivider` 内部横线 | 新增 `TerminalFormSection` 与 `terminalFormPanel`；学期名称（改为面板内无边框输入）、开始日期、总周数连同内部横线同属一个面板；`01` 序号改用显式粗体等宽字面（`Typeface.create("monospace", BOLD)` + `FontWeight.Black`，15sp／字距 0.6sp） | `QingKeAppTest.settingsFormSectionsUseOneRailPanelWithInternalDividers`（浅色／深色各一次：左侧青色竖线像素、2 条内部横线、子节点包含关系） |
| 2「02 每日节次」同属一个面板 | 折叠按钮位于 `TerminalFormSection` 内容内（青色文字 + 「N 节」+ `chevron.up/down`）；展开后节次行、`TerminalFormDivider` 与「添加节次」都在同一面板 | `PeriodsToggleRow` 移入面板：左侧「展开／收起节次设置」、右侧「N 节」+ Canvas 绘制的上下箭头；`PeriodRow` 改为「第 N 节 + 右上删除 ✕ + 开始／结束时间单元」，时间单元仍弹系统 TimePicker；3 条内部横线与「添加节次」同属面板 | `settingsPeriodsToggleKeepsEverythingInsideOnePanelWithStatefulChevron`（折叠／展开箭头 contentDescription、面板内包含关系、横线计数） |
| 3 顶部保存对齐 iOS | `settingsHeader`：`Button("保存")` 只有 `foregroundStyle(QingKeTheme.signal)`，无填充 | 新增 `SettingsToolbarSave`：黑色工具栏上的黄色文字（不再使用黄色填充矩形按钮），引导页顶栏同步；课程编辑器原本已是黑底黄字，未改动；ADD 等黄色主操作未改动 | `topSaveEntriesUseYellowTextOnTheInverseBarWithoutFilledButtons`（像素契约：黄字面积 ≤45%、黑底 ≥45%，设置页／引导页／课程编辑器三处） |
| 5 底部保存卡片对齐 iOS | `saveButton`：`frame(maxWidth: .infinity, minHeight: 58)` + `Image(systemName: "arrow.right")` | 卡片主体改为 `heightIn(min = 58.dp)`（去掉过大的上下留白），保留黑底、白色标题／副标题与 4dp 黄色底线；右侧箭头改为 26dp Canvas 大箭头 | `settingsSaveCardMatchesCompactIosHeightAndArrowContract`（58dp 高度区间、箭头 ≥22dp 且实际绘制、底线 4dp 且占满卡宽；100% 与 130% 字号各一次） |

另外新增 `settingsSectionsSurviveLargeFontAndNarrowScreenWithoutClipping`：130% 字号 + 320dp 窄屏下 01／02 面板与内部控件不水平溢出，两个面板的青色竖线仍存在。

## 验证结果

- 主机：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **104 tests、0 failures／errors／skipped**；`lintDebug` **0 errors、20 warnings**。
- 设备（复用当时已在运行的 API 37 ARM64 AVD `emulator-5554`，1080x2400@420dpi）：
  - 定向 `connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.qingke.schedule.ui.QingKeAppTest`：**60 tests、0 failures／errors／skipped**；
  - 完整 `connectedDebugAndroidTest`：**89 tests、0 failures／errors／skipped**（R3 基线 84 + 本轮 5）。
- 文档：`python3 docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 与 `git diff --check` 通过。
- 测试踩坑记录（已修正）：`captureToImage()` 以语义节点边界为准，面板的 testTag 原先落在 14dp 内边距之后，导致左侧竖线不在截图范围内——R4 把面板 testTag 移到面板外层节点；底部卡片在展开态会被底部标签栏遮挡黄色底线，测试改为先滚到页面底部占位（`settings-bottom-spacer`）再断言。

## 截图清单（真实 debug 入口、真实数据）

数据：全新安装 → 首次设置保存默认学期（2026 秋季学期，2026-09-18 起 18 周、10 节）→ 设置页。

| 文件 | 状态 | 目视结论 |
| --- | --- | --- |
| `p3-06-r4-settings-light-api37.png` | 浅色 100% | 顶部黑底黄字「保存」；01／02 各为单一青线面板，名称／开始日期／总周数为内部横线分隔；底部卡片紧凑、白色大箭头、黄色底线 |
| `p3-06-r4-settings-dark-api37.png` | 深色 100% | 同上，面板表面、边框、内部横线与青线在深色下清晰，无黑字深底 |
| `p3-06-r4-settings-light-font130-api37.png` | 浅色 130% | 无水平裁切，footer 折行显示，面板与青线保持完整 |
| `p3-06-r4-periods-light-expanded-api37.png` | 浅色展开节次 | 节次行、内部横线与「第 N 节／删除 ✕／开始·结束时间单元」全部位于同一青线面板内，未出现一组互不关联的描边按钮 |
| `p3-06-r4-periods-dark-expanded-api37.png` | 深色展开节次 | 同上，深色下时间单元与删除标记可读 |
| `p3-06-r4-save-card-light-api37.png` | 浅色保存卡片 | 卡片按约 58dp 紧凑高度，黑色底、白色标题／副标题、4dp 黄线、右侧大箭头 |
| `p3-06-r4-settings-small-api37.png` | 小屏 720x1280@320dpi 顶部 | 01 面板内「开始日期 2026年9月18日」与总周数不裁切 |
| `p3-06-r4-settings-small-save-api37.png` | 小屏滚动到底部 | 小屏下保存卡片与 02 面板完整显示，箭头与黄线正常 |

## 限制

- 未实施 A07 教学日历、A08 上课提醒、A10 数据备份、A11 外观；设置页仍不含这些分区。
- 设备验证复用用户当时已在运行的 AVD，未重装镜像；截图窗口使用 1080x2400 覆盖与 720x1280 小屏两次运行，字体比例 1.0／1.3。
- 时间选择、删除、添加、校验与既有 testTag 的行为未改，仍由既有用例覆盖；本轮不重复 R2/R3 的业务回归结论。
