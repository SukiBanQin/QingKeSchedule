# P3-06／A06 完整设置页与学期编辑证据（2026-09-18）

## 范围与基准

- 仓库：/Users/takagisan/课表软件，分支 `Android`，开始基准 `0e2e937`（`docs(android): hand off after p3-05 acceptance`），本地 HEAD 与 `origin/Android` 一致、工作区干净
- iOS 只读基准：/Users/takagisan/课表软件-IOS（`IOS` 分支 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`）的 `SemesterFormView.swift`／`SemesterDraft.swift`；未修改 iOS、Web、共享协议
- 只修改 `Android/`、`docs/Android/` 与必要测试；Room schema（version 1）与版本 1 共享协议保持不变；未合并 `main`

## 实现要点

1. `MainTab.SETTINGS` 的壳层替换为真实设置页 `SemesterSettingsScreen`：顶部工具栏「学期与节次 / SYSTEM CONFIG / 保存」与底部「保存学期设置 / COMMIT CHANGES」两个入口都调用同一 `actions.saveSemester`；顶部工具栏常驻，底部按钮随内容滚动。
2. 字段与控件复用既有引导页组件（学期名、可展开的开始日期日历、1—52 周步进、1—20 节展开收起、每节起止时间 TimePicker、删除第 N 节、添加节次），未新增重复实现；节次展开默认值对齐 iOS `shouldExpandPeriodsByDefault(count < 5)`。
3. 已有学期冷启动即建立草稿（`SemesterDraft.edit`，保留原节次编号与 id）；标签切换、每秒时钟刷新、`retryLoad()` 与设置页下拉刷新都不会重建或覆盖草稿。
4. 保存互斥：`saveRequested` + `state.isSaving` 双重防重，两个入口在保存中都禁用；失败或非法输入保留整份草稿并显示内联提示；成功显示 `semester-save-success` toast 并清空。
5. 保存成功后 `ScheduleAppState` 直接发布仓库返回的已提交快照，TODAY、周表与课程编辑读取同一 `state.data.semester`，因此新节次时间立即生效。
6. 节次编号语义（用户已确认规则）：`SemesterDraft.semester()` 不再把节次重编号为 1…n，合法导入的非连续／反序编号在只改名称、日期或周数时原样写回；`addPeriod()` 取 `max(number) + 1` 避免重复编号；`removePeriod()` 仍按 iOS 重排 1…n。
7. 结构变化预检 `SemesterDraft.impactIssues(previous, courses)`（纯 Kotlin，可 JVM 测试）：缩短总周数会让既有课程越界、或既有课程引用的节次编号消失时，整次拒绝并提示先调整课程；修改已被引用的节次时间允许保存（课程随该节次使用新时间）。Room 保存仍在事务内跑 `ScheduleValidator` 作为安全网，失败时不落盘、旧数据完整保留。

## 业务规则与测试对应

| 已确认规则 | 实现 | 测试 |
| --- | --- | --- |
| 修改已使用节次的时间可保存，课程使用新时间 | `impactIssues` 只检查编号是否消失与周数越界 | `DraftTest.semesterDraftKeepsImportedNumbersAndReportsOnlyCourseBreakingChanges`、`ScheduleViewModelTest.semesterSaveReportsCourseBreakingChangesKeepsDraftAndWritesPreservedNumbers` |
| 缩短总周数导致课程越界 → 整次拒绝并保留旧数据 | `impactIssues` 周数分支 + Room 事务校验 | 同上 + `RoomScheduleRepositoryTest.shorteningWeeksBeyondCourseRangeRollsBackWholeSemesterSave` |
| 删除／重编号影响既有课程引用 → 拒绝并提示先调整课程 | `impactIssues` 编号分支（提示语含「请先在课程编辑中调整相关课程」） | 同上 + `RoomScheduleRepositoryTest.invalidSemesterChangeAndMissingSemesterCourseKeepOldData` |
| 非连续／反序编号在只改名称、日期、周数时保留 | `semester()` 使用 `period.number` | `DraftTest`（4/9 保留）、`RoomScheduleRepositoryTest.saveSemesterKeepsReversedNumbersNewTimesAndCoursesAcrossReopen` |
| 新增或其他结构变化影响已有课程引用 → 拒绝 | 编号集合变化即触发预检；重复编号由验证器拒绝 | `DraftTest`、`ScheduleViewModelTest` |
| 已有学期冷启动建立草稿且不被刷新覆盖 | `loadAndPrepare` 建立草稿后不再重建 | `ScheduleViewModelTest.existingSemesterDraftKeepsUnsavedEditsAcrossTabsClockRefreshAndReload`、`P3R2ActivityRecreationTest` |
| 两个保存入口共用同一路径 | 两处都调用 `actions.saveSemester` | `QingKeAppTest.settingsScreenEditsExistingSemesterAndBothSaveEntriesShareOnePath`（saves == 2） |
| 保存中防重复提交 | `saveRequested`／`isSaving` + 按钮禁用 | `ScheduleViewModelTest.suspendedSaveOnlyWritesOnceAndSendsFullNormalizedSemester`、`QingKeAppTest.settingsSavingDisablesBothEntriesAndShowsValidationError` |
| 保存失败保留草稿 | 失败不改草稿，只发布 error | `ScheduleViewModelTest.failedSaveAndDismissErrorRetainEntireDraft` |
| 刷新保留草稿 | 下拉刷新只刷时钟，不重建草稿 | `QingKeAppTest.settingsPullToRefreshKeepsEditedDraftAndOnlyRefreshesTheClock` |
| 保存后 TODAY／周表／编辑器立即用新时间 | 状态直接发布已提交快照 | `QingKeAppTest.savedSemesterPeriodTimesFlowIntoTodayAndWeekImmediately`、`savedSemesterPeriodTimesFlowIntoCourseEditorImmediately` |
| 持久化重启 | Room 事务写入 + 重开数据库 | `RoomScheduleRepositoryTest.saveSemesterKeepsReversedNumbersNewTimesAndCoursesAcrossReopen`、`emptyRoundTripOrderDuplicatesAndReopen` |

## 验证结果

- 主机：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **96 tests、0 failures／errors／skipped**（P3-05 基线 92 + 本轮 4）；`lintDebug` **0 errors、20 warnings**
- 设备：API 37 ARM64 AVD（`qingke-api37-r3-arm`，1080x2400，420dpi）完整 `connectedDebugAndroidTest --no-daemon` **83 tests、0 failures／errors／skipped**（P3-05 基线 74 + 本轮 9），结果 XML 见本目录
- 文档：`python3 docs/tests/android-documentation.test.py` 70 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 通过、`git diff --check` 通过

## 截图清单（真实 debug 入口，真实数据）

数据：全新安装 → 首次设置默认学期（2026 秋季学期，2026-09-18 起 18 周、10 节）→ 新增课程 `Advanced Mathematics`（周五第 1 节，1–18 周）。

| 文件 | 状态 | 目视核对结果 |
| --- | --- | --- |
| p3-06-settings-light-api37.png | light／100% | 顶部「学期与节次 / SYSTEM CONFIG / 保存」+ 黄色下划线；品牌头 SYSTEM / 03；CONFIGURATION 标签 + 系统设置 + SYS 03；01 学期信息（名称、开始日期、总周数 18 −/+）；02 每日节次（PERIODS / 10、展开按钮、脚注）；底部「保存学期设置 / COMMIT CHANGES」；标签栏 设置 选中 |
| p3-06-periods-light-api37.png | light／100% | 展开后每节一行：第 N 节 + 起止时间按钮 + 删除第 N 节，节次时间与默认 10 节一致 |
| p3-06-settings-dark-api37.png | dark／100% | 深色面下结构、层级与对比正常，黄/青强调色保留 |
| p3-06-periods-dark-api37.png | dark／100% | 深色展开态：收起按钮与各节时间、删除按钮完整可读 |
| p3-06-settings-light-font130-api37.png | light／130% | 大字号下标题、分区标题、脚注换行、按钮均不重叠不裁切，内容可滚动 |
| p3-06-settings-small-top-api37.png | light／100%，720x1280@320dpi | 小屏首屏 |
| p3-06-settings-small-save-api37.png | light／100%，720x1280@320dpi | 小屏滚动到底部保存按钮完整可见且未被标签栏遮挡，顶部保存仍常驻 |
| p3-06-weeks-shrink-rejected-api37.png | light／100% | 已有课程 1–18 周时把总周数改为 17 并点保存：内联红色提示「缩短总周数会让已有课程超出学期范围，请先在课程编辑中调整相关课程的周次。」，保存被拒绝、草稿保留 |

## 已知差异与限制

- iOS 设置页还包含教学日历（A07）、提醒（A08）、本地备份（A10）与外观（A11）四个分区；本轮范围外，因此副标题写「管理学期与每日节次。」而非 iOS 的「管理学期、节次、提醒与本地课表备份。」，且不放置任何无效入口。
- iOS 删除节次后重排 1…n；Android 保持一致，但额外在保存前拒绝会改变既有课程含义的删除／重排（用户确认的规则）。
- Android 设置页下拉刷新只刷新内存时钟（与 TODAY／周表一致），不重新读取 Room，因此不可能丢弃未保存草稿；iOS 的 `onRefresh` 语义与平台刷新策略不同，记录为平台差异。
- 两个历史 P3-04 connected 用例（`chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales`、`r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState`）依赖窗口高度：设备为 1080x1920 时失败（`course-start-week-compact-minus height=22.0`、`expected:<ODD> but was:<EVERY>`），恢复记录基准 1080x2400 后通过。本轮未修改 P3-04 代码或测试，仅记录该现象。
- 未做真机、其他 API 级别与横屏验证；截图仅覆盖 1080x2400 与 720x1280 两种尺寸。
- 用户视觉验收与 Sol 独立复审均未进行；本轮只完成实现、测试与设备证据。
