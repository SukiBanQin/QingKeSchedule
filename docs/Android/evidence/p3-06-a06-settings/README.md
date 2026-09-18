# P3-06／A06 完整设置页与学期编辑证据（2026-09-18）

> **2026-09-18 第二轮 Sol 复审返修（R3）**：第二轮复审在 `6f25e6b` 上发现两处问题——成功保存后草稿的节次身份基准没有更新（首次设置或新增／合法重排节次保存后，课程引用该节次再普通改名或改时间会被 `impactIssues` 错误拒绝），以及本 README 与流程文档仍有残留的过时描述。R3 只改预检基准的更新时机与文档，未改设置页 UI；R2 的截图、设备结果与验证数字保留为历史。详见「第二轮 Sol 复审问题与返修（R3）」。

> **2026-09-18 首轮 Sol 复审未通过，返修（R2）记录见下节**：首轮提交 `83d1a9f` 的四项问题（节次身份级课程引用保护、深色设置页裸文本前景色、证据目录权限、状态文档过时）已在本轮修正；深色设置首页与展开节次的生产截图已重新拍摄为 `p3-06-r2-settings-dark-api37.png`／`p3-06-r2-periods-dark-api37.png`，首轮深色截图保留作历史对照并标注为已推翻。

## 首轮 Sol 复审问题与返修（R2）

| 复审问题 | 根因 | 返修 | 回归与证据 |
| --- | --- | --- | --- |
| `impactIssues` 只检查引用编号是否仍存在 | 草稿删除节次后会重排编号，编号仍存在但已指向别的节次（例：1=A、2=B、3=C，课程引用 2，删除 A 后 C 变成第 2 节） | `PeriodDraft` 记录 `sourceNumber`（持久化学期中的原编号）；`impactIssues` 要求 `currentNumberBySource[引用编号] == 引用编号`，即引用必须仍指向同一节次；被占用或被删除都拒绝，仍不做时间比较 | `DraftTest.semesterDraftRejectsDeletionThatMovesAnotherPeriodOntoAReferencedNumber`（前置／中间删除拒绝、末尾未引用删除允许、新增不影响引用、反序编号保留）、`ScheduleViewModelTest.deletingAPeriodThatShiftsAReferencedNumberIsRejectedAndKeepsDraft`；负向对照：临时改回旧实现后两个新用例立即失败 |
| 深色设置页「总周数：N」「第 N 节」黑字深底 | 这两个裸 `Text` 未指定颜色，Compose 的 `LocalContentColor` 默认为黑色 | `WeekControl`／`PeriodRow` 改为 `terminalText(dark)`，节次标签新增 `period-<id>-label`（仅新增）；引导页共用组件一并修复 | `QingKeAppTest.semesterLabelsUseThemeForegroundsInBothThemes`：深色断言浅色墨迹、浅色断言深色墨迹（像素，不是语义文本）；负向对照：临时去掉颜色后以 `inkPixels=0` 失败 |
| 证据目录属 root、验证文本 0600 不可读 | 首轮以 root 身份创建证据文件 | `chown -R takagisan:staff` + 目录 755／文件 644，未改动内容；同时规范化 `p3-05-visual-r1`／`p3-05-visual-r2` | 见 host-and-device-verification 记录中的权限核对；`Android/app/build` 无 root 所有文件 |
| 状态文档过时 | P3-05 验收与 P3-06 状态未写清 | 更新 `product-baseline.md`、`technical-design.md`、`implementation-plan.md`、`handoff.md`：P3-05／A03 已通过技术复审与用户验收；P3-06 已实施、已测试、首轮复审未通过并完成返修，等待再次复审与用户验收 | 文档测试 71 tests OK；未提前写成复审通过 |

返修后的验证数字：Debug／Release JVM 各 **98 tests、0 failures／errors／skipped**；API 37 ARM64 完整 `connectedDebugAndroidTest` **84 tests、0 failures／errors／skipped**（首轮为 96／83，保留为历史）。

## 第二轮 Sol 复审问题与返修（R3）

| 复审问题 | 根因 | 返修 | 回归与证据 |
| --- | --- | --- | --- |
| 成功保存后节次身份基准没有更新 | `SemesterDraft.create()` 与 `addPeriod()` 产生的 `sourceNumber` 为 null，`saveSemester()` 成功后又没有重建基准；首次设置或新增／合法重排节次保存后，课程引用该节次，再普通改名或改时间会被 `impactIssues` 误判为“原节次身份不再对应原编号” | 新增 `SemesterDraft.markPersisted(persistedNumbers)`，在 `saveSemester()` **成功**后按写入请求时捕获的 `period id → number` 快照重建基准：本次写过的节次以写入时的编号为新基准，保存期间新增的节次仍为未持久化；失败保存不更新；草稿内容与展开状态不变 | `DraftTest.markPersistedRebasesFirstOnboardingIdentityOntoTheWrittenSemester`、`DraftTest.markPersistedUsesPeriodIdentityNotNumbersAndLeavesLaterAdditionsUnsaved`、`ScheduleViewModelTest.onboardingSaveRebasesPeriodIdentitySoCoursesCanLaterReferenceThoseNumbers`、`savedNewPeriodBecomesTheIdentityBaselineAndDeletingItIsStillRejected`、`failedSemesterSaveKeepsThePersistedBaselineForTheNextJudgement`、`editsDuringAnInFlightSemesterSaveAreNotMarkedPersisted` |
| 文档残留过时描述 | 本 README 仍写“编号消失／编号集合变化”，`implementation-plan.md` 仍称 P3-05 R2 待复审，`product-baseline.md` 默认流程仍写 Terra | 统一为“原节次身份仍对应原编号”，明确新增不影响引用时允许；P3-05 R2 记为已通过技术复审与用户视觉验收；默认流程改为 DeepSeek 主力开发、自测，Sol 按需审查 | 文档测试 71 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 与 `git diff --check` 通过 |

负向对照：临时移除成功保存后的 `markPersisted` 调用，`onboardingSaveRebasesPeriodIdentitySoCoursesCanLaterReferenceThoseNumbers` 与 `savedNewPeriodBecomesTheIdentityBaselineAndDeletingItIsStillRejected` 立即失败（45 tests 中 2 failed）；改为按“保存完成时的当前编号”重建后，`editsDuringAnInFlightSemesterSaveAreNotMarkedPersisted` 失败（1 failed）。恢复正确实现后全部通过。

R3 验证数字：Debug／Release JVM 各 **104 tests、0 failures／errors／skipped**（R2 为 98，本轮新增 6）；`lintDebug` **0 errors、20 warnings**；`assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` BUILD SUCCESSFUL。R3 未修改 UI 代码，未重启模拟器或重拍截图，沿用 R2 已核实的 API 37 视觉证据（`p3-06-r2-settings-dark-api37.png`、`p3-06-r2-periods-dark-api37.png` 等 R2 截图与 84 tests 设备记录）。

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
7. 结构变化预检 `SemesterDraft.impactIssues(previous, courses)`（纯 Kotlin，可 JVM 测试）：缩短总周数会让既有课程越界、或既有课程引用不再解析到原节次（删除／重排后原节次身份仍对应原编号的要求不成立，仅编号集合变化不算）时，整次拒绝并提示先调整课程；新增节次不影响既有引用时允许，修改已被引用的节次时间也允许保存（课程随该节次使用新时间）。Room 保存仍在事务内跑 `ScheduleValidator` 作为安全网，失败时不落盘、旧数据完整保留。

## 业务规则与测试对应

| 已确认规则 | 实现 | 测试 |
| --- | --- | --- |
| 修改已使用节次的时间可保存，课程使用新时间 | `impactIssues` 只检查原节次身份是否仍对应原编号与周数越界 | `DraftTest.semesterDraftKeepsImportedNumbersAndReportsOnlyCourseBreakingChanges`、`ScheduleViewModelTest.semesterSaveReportsCourseBreakingChangesKeepsDraftAndWritesPreservedNumbers` |
| 缩短总周数导致课程越界 → 整次拒绝并保留旧数据 | `impactIssues` 周数分支 + Room 事务校验 | 同上 + `RoomScheduleRepositoryTest.shorteningWeeksBeyondCourseRangeRollsBackWholeSemesterSave` |
| 删除／重编号影响既有课程引用 → 拒绝并提示先调整课程 | `impactIssues` 编号分支（提示语含「请先在课程编辑中调整相关课程」） | 同上 + `RoomScheduleRepositoryTest.invalidSemesterChangeAndMissingSemesterCourseKeepOldData` |
| 非连续／反序编号在只改名称、日期、周数时保留 | `semester()` 使用 `period.number` | `DraftTest`（4/9 保留）、`RoomScheduleRepositoryTest.saveSemesterKeepsReversedNumbersNewTimesAndCoursesAcrossReopen` |
| 新增节次不影响既有引用时允许；删除／重排使原节次身份不再对应原编号 → 拒绝 | 预检要求每个被引用编号仍解析到同一节次（`currentNumberBySource[引用编号] == 引用编号`）；重复编号由验证器拒绝 | `DraftTest.semesterDraftRejectsDeletionThatMovesAnotherPeriodOntoAReferencedNumber`、`DraftTest.markPersistedUsesPeriodIdentityNotNumbersAndLeavesLaterAdditionsUnsaved`、`ScheduleViewModelTest.deletingAPeriodThatShiftsAReferencedNumberIsRejectedAndKeepsDraft` |
| 已有学期冷启动建立草稿且不被刷新覆盖 | `loadAndPrepare` 建立草稿后不再重建 | `ScheduleViewModelTest.existingSemesterDraftKeepsUnsavedEditsAcrossTabsClockRefreshAndReload`、`P3R2ActivityRecreationTest` |
| 两个保存入口共用同一路径 | 两处都调用 `actions.saveSemester` | `QingKeAppTest.settingsScreenEditsExistingSemesterAndBothSaveEntriesShareOnePath`（saves == 2） |
| 保存中防重复提交 | `saveRequested`／`isSaving` + 按钮禁用 | `ScheduleViewModelTest.suspendedSaveOnlyWritesOnceAndSendsFullNormalizedSemester`、`QingKeAppTest.settingsSavingDisablesBothEntriesAndShowsValidationError` |
| 保存失败保留草稿 | 失败不改草稿，只发布 error | `ScheduleViewModelTest.failedSaveAndDismissErrorRetainEntireDraft` |
| 刷新保留草稿 | 下拉刷新只刷时钟，不重建草稿 | `QingKeAppTest.settingsPullToRefreshKeepsEditedDraftAndOnlyRefreshesTheClock` |
| 保存后 TODAY／周表／编辑器立即用新时间 | 状态直接发布已提交快照 | `QingKeAppTest.savedSemesterPeriodTimesFlowIntoTodayAndWeekImmediately`、`savedSemesterPeriodTimesFlowIntoCourseEditorImmediately` |
| 持久化重启 | Room 事务写入 + 重开数据库 | `RoomScheduleRepositoryTest.saveSemesterKeepsReversedNumbersNewTimesAndCoursesAcrossReopen`、`emptyRoundTripOrderDuplicatesAndReopen` |

## 验证结果

- 主机（返修后）：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **98 tests、0 failures／errors／skipped**（首轮 96 + 返修 2）；`lintDebug` **0 errors、20 warnings**（首轮数字保留为历史）
- 设备（返修后）：API 37 ARM64 AVD（`qingke-api37-r3-arm`，1080x2400，420dpi）完整 `connectedDebugAndroidTest --no-daemon` **84 tests、0 failures／errors／skipped**（首轮 83 + 返修 1），结果 XML 见本目录
- 文档：`python3 docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`／`repository-layout.test.sh` 通过、`git diff --check` 通过

## 截图清单（真实 debug 入口，真实数据）

数据：全新安装 → 首次设置默认学期（2026 秋季学期，2026-09-18 起 18 周、10 节）→ 新增课程 `Advanced Mathematics`（周五第 1 节，1–18 周）。

| 文件 | 状态 | 目视核对结果 |
| --- | --- | --- |
| p3-06-settings-light-api37.png | light／100% | 顶部「学期与节次 / SYSTEM CONFIG / 保存」+ 黄色下划线；品牌头 SYSTEM / 03；CONFIGURATION 标签 + 系统设置 + SYS 03；01 学期信息（名称、开始日期、总周数 18 −/+）；02 每日节次（PERIODS / 10、展开按钮、脚注）；底部「保存学期设置 / COMMIT CHANGES」；标签栏 设置 选中 |
| p3-06-periods-light-api37.png | light／100% | 展开后每节一行：第 N 节 + 起止时间按钮 + 删除第 N 节，节次时间与默认 10 节一致 |
| p3-06-settings-dark-api37.png | dark／100% | **首轮截图，结论已被推翻**：结构层级与强调色正常，但「总周数：18」为黑字深底不可读；保留作历史对照 |
| p3-06-periods-dark-api37.png | dark／100% | **首轮截图，结论已被推翻**：「第 N 节」为黑字深底不可读；保留作历史对照 |
| p3-06-r2-settings-dark-api37.png | dark／100% | 返修后深色设置首页：「总周数：18」为浅色可读，其余分区、按钮与标签栏正常 |
| p3-06-r2-periods-dark-api37.png | dark／100% | 返修后深色展开态：「第 1—5 节」为浅色可读，起止时间与删除按钮完整 |
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
