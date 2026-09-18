# 安卓项目当前交接状态

## P3-05／A03 周课表视觉返修 R2 已通过技术复审及用户视觉验收（最新，2026-09-18）

P3-05 R2 实现提交为 `e3321bd`（`fix(android): correct r2 week controls layout and spacing`）。Sol 已集中核对完整 diff、测试、API 37 设备结果与 5 张 R2 生产截图，确认 130% 字号下 `ODD WEEK/EVEN WEEK` 完整显示、“切走→箭头回本周→点击中部→恢复自动跟随”回归通过，浅色／深色／100%／130% 与日清单视觉正常；Debug／Release JVM 各 92 tests、API 37 connected 74 tests、lint、文档测试与 `git diff --check` 均通过。

用户于 2026-09-18 明确确认视觉没有问题，P3-05／A03 当前范围的用户视觉验收门槛关闭。用户同时注意到课程编辑下拉项显示各节开始时间但不能直接修改；该控件按现有设计只选择课程所在节次，时间来自当前学期的每日节次设置，应由尚未实施的 A06 完整设置／学期编辑提供修改入口，因此不记作 P3-05 或 P3-04 缺陷。该确认不扩大为 A06—A11、整个 P3 或完整 App 验收。

下一项建议为 **P3-06／A06 完整设置页与学期编辑**，用于编辑学期名、开始日期、总周数及每日节次数量和起止时间。开始实施前应先只读分析并定界 iOS 学期编辑基准、Android 现有 `SemesterDraft`／设置壳层、课程引用节次与周数缩短时的校验、保存和持久化流程；A07 教学日历建议作为后续独立任务。当前仅记录建议，尚未因本次验收自动授权分析或实施，也未修改应用代码、启动下一阶段或合并 `main`。

本轮还处理了 Android Studio 无法删除 `Android/app/build` 的本机环境问题：旧目录含 root 所有的构建产物，已停止 Gradle daemon 并将该目录移至可恢复的废纸篓 `~/.Trash/QingKeSchedule-app-build-root-owned-20260918-1920`，随后以用户身份重建并成功执行 `assembleDebug`、安装和启动最新 R2；新 `app/build` 中 root 所有文件为 0。后续执行窗口不得以 root 身份向共享的 `Android/app/build` 生成产物；这项环境修复没有源码改动。

## 默认流程改为 DeepSeek 执行、Sol 按需审查（2026-09-18）

用户确认采用 DeepSeek 主力开发、自测，Sol 按需独立审查；当前 DeepSeek V4.1 的实际服务与参数由客户端决定，未由本窗口核实。小任务可直接交付，关键任务和已有审查要求保留；人工按需交接，默认不自动委派。遇到问题可由用户决定回退 Codex，旧 Terra 子 Agent 规则仅作回退参考。

该流程调整轮次的基准为 `e3321bd`，当时仅修改流程文档和相应验证，未改全局配置、应用代码或模型设置。其后 P3-05 R2 已完成 Sol 独立复审及用户视觉验收，最新状态以上一节为准；尚未启动下一阶段或子 Agent。

## P3-05／A03 周课表视觉返修 R2 已实施（历史实施记录，2026-09-18）

本节回应 Sol 对 R1（`af825a1`）的独立审查意见。R1 未通过技术复审；本轮在同一允许路径内按三条意见集中修正，由当前主窗口实施、未创建子 Agent，实际模型与思考档位未核实（工具不能读取）。

- 基准与工作区：分支 `Android`，开始基准 `af825a1`（`fix(android): align r1 week schedule visuals`），本地 HEAD 与 `origin/Android` 一致且工作区干净，未发现其他窗口改动。
- 允许范围：`Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt`、`Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt`、本交接文件、`docs/Android/evidence/p3-05-visual-r1/README.md`（修正旧结论）与新增 `docs/Android/evidence/p3-05-visual-r2/`。未改 P3-04、其他阶段、iOS/Web、Room/schema、DataStore、共享协议、业务规则、设置、通知、导入导出与 `main`；继续复用既有展示模型与 `openCourseAt()`；testTag 全部保留，**没有重命名**。
- 修正 1（130% 下 `ODD WEEK` 消失）：根因是面板对承载三行文字的 `Row` 施加了高度约束——实测 `height(64.dp)` 与 `heightIn(min = 64.dp)` 都会让内置 `Column` 把第三行压缩到剩余空间（`week-parity` 行盒仅 6.48dp，像素计数 0）。现改为纯内容高度 `Row` + `padding(vertical = 6.dp)`，iOS 的 64dp 视觉下限由左右箭头的 `heightIn(min = 64.dp)` 提供；三行补显式 `lineHeight`（14／21／12sp）与 `maxLines = 1`。浅／深色 100% 仍是 64dp，130% 下增高到约 77dp 且三行完整可见。
- 修正 2（中部按钮遗漏 `followsCurrentWeek`）：`canReturn = currentWeek != null && (currentWeek != week || !followsCurrentWeek)`。手动切走再用箭头回到本周时中部重新可用，点击恢复自动跟随；手动浏览仍不会被时钟刷新重置。
- 修正 3（分组间距）：对照只读 iOS `WeekScheduleView.swift` 的 `LazyVStack(spacing: 14)` 与 `.padding(.top, 12)`，滚动内容顶部 12dp、页面标题→教学周面板与面板→日期条各 14dp；矩阵分区间距、七日一屏与点击路由未变。
- 新增测试 2 项：`weekControlsKeepAllTextVisibleAndSpacedAcrossFontScales`（100%／130% 下逐行断言实际行盒高度、三行完整落在面板内、分组间距 ≥ 12dp、130% 面板增高，并用像素断言证明 `ODD WEEK` 真的被绘制在面板中部）；`weekControlsRestoreAutoFollowAndManualBrowsingSurvivesClockRefresh`（切走→箭头回本周→中部可用→点击恢复跟随→时钟进入下一教学周自动更新；随后手动选到第 04 周，时钟推进到第 05 周仍停留 04）。既有 5 个周页用例未改名并继续通过。
- 负向对照：把面板临时改回 R1 的 `height(64.dp)` 后新用例在 130% 失败，报错为 `fontScale=1.3 ODD/EVEN WEEK text must be painted inside the panel, cyanPixels=0`；恢复后同用例通过。证明测试检查的是真实渲染与布局，而不是仅断言语义文本存在。
- 顺带修好 R1 的测试缺陷：R1 的两个像素断言把分隔线写成“比底色更暗”，设备处于深色主题时会失败（本轮完整套件第 2 次运行即复现：`lineColumns=0`、`top=90 middle=18`）。现改为与主题无关的亮度偏差判定（`|sample - baseline| >= 12`，取样仍限 ±3px），浅／深色均通过。该缺陷属于 R1 测试写法问题，不是产品行为问题。
- 主机验证：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **92 tests、0 failures／errors／skipped**；`lintDebug` **0 errors、20 warnings**；文档 70 tests OK、两个文档脚本与 `git diff --check` 通过。
- 设备验证：API 37 ARM64 `emulator-5556` 完整 `connectedDebugAndroidTest --no-daemon` **74 tests、0 failures／errors／skipped**（R1 的 72 + 本轮 2 项）；定向 `am instrument` 7 项周页用例 `OK (7 tests)`；并在 **dark／font_scale 1.3** 与 **light／1.0** 两种设备状态下分别复跑周页与像素用例均通过。
- 截图证据：`docs/Android/evidence/p3-05-visual-r2/` 浅色 100%、浅色 130%、浅色 130% 日清单、深色 100%、深色 130% 共 5 张，逐张目视结论见该目录 README；`p3-05-visual-r1/README.md` 中“大字体不裁切”的错误结论已标注推翻，历史图与说明保留。
- 已知差异（本轮未改，待决定）：iOS 中部按钮只恢复周，Android 仍会同时把选中日恢复为今天并重新开启日跟随，属 P3-05 首版行为。
- 状态更新：本节是实施完成时的历史记录；R2 后续已通过 Sol 独立复审，并由用户于 2026-09-18 确认视觉没有问题。最新准确状态及下一项边界以上方收口节为准。

## P3-05／A03 周课表视觉返修 R1 已实施，待独立审查与用户视觉验收（2026-09-18）

本节由当前主窗口在用户明确指示下直接实施并记录；上一节给 DeepSeek V4.1 Flash 的交接内容已由本次实施覆盖，未另行委派，也未创建任何子 Agent。实际模型与思考档位未核实（当前工具不能读取）。

> 状态更新（2026-09-18）：R1 未通过 Sol 独立复审，已由 R2（见上节）修正教学周面板 130% 裁切、中部按钮跟随状态与分组间距三点。本节其余范围、允许路径与验收清单仍为有效边界；R1 的 dark-font130 截图结论已在 `docs/Android/evidence/p3-05-visual-r1/README.md` 标注推翻。

- 基准与工作区：分支 `Android`，开始基准 `2299e0b`（`docs(android): hand off p3-05 visual r1`）；开始时本地 HEAD 与 `origin/Android` 一致且工作区干净，未发现其他窗口的未提交改动或基准漂移。
- 允许范围：只改 `Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt`、`Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt`、本交接文件与 `docs/Android/evidence/p3-05-visual-r1/`。未改 iOS、Web、Room/schema、DataStore、共享协议、业务规则、设置、通知、导入导出、P3-04 既有视觉与 `main`；继续复用 `WeekSchedulePresentation`、`WeekMatrixPresentation`、`ScheduleDisplayText`、`courseColor`、`TodayVisualSpec`、`openAddCourse()`、`openCourseAt()` 与 `AcademicCalendarPreferences`，未复制新的领域规则。
- 七项修正均已实现：反相黑底白字周页标签；粗体「课表」+ 右侧动态 `WEEK` 两位周号；白底（深色主题为深色面）1dp 细边框教学周面板（左右箭头／竖分隔线／学期名／`第 XX 教学周`／`ODD|EVEN WEEK`，中部点击返回当前周，删除原独立「返回当前周」整行）；日期条上下 1dp 横线与每日竖分隔线、选中日期反相背景 + 日期下方 3dp 黄色下划线；`05 周视图` + 动态 `MON–SUN / N PERIODS`；矩阵 8 条竖线与节次数 + 1 条横线、表头 38dp／行高 68dp／午休行 30dp、`TIME`／星期名／节次编号深色粗体居中、午休仅在设置启用且展示模型产生 break 时以青色底 + 上下细边框 + 左侧标题／分隔线／时间显示；日清单标题改为编号／周几／条目数并删除 ISO 日期，课程卡片最小高度 94dp，含旋转序号、起止时间列、竖分隔线、节次或冲突标记、课程名、教室／教师与右箭头，点击仍路由既有 `openCourseAt(courseIndex)`。
- testTag 与无障碍语义：既有周页标签与语义全部保留，**没有重命名**，仅新增 `week-controls`、`week-teaching-week`、`week-parity`、`week-matrix-header-index`、`week-view-title`、`week-matrix-summary`、`week-manifest-detail`、`week-matrix-canvas`、`week-lunch-break-title`；因此不存在需要同步改名的定向测试。点击控件内部的文本断言改为 unmerged tree，因为 `clickable` 会合并子节点语义。
- 新增 Compose 契约测试 4 项，覆盖要求中的动态教学周、箭头与返回当前周、选中日期下划线（像素重心随选日右移）与日期条上下横线（像素亮度）、矩阵竖线／横线位置、午休条件显示与青色底、日清单无 ISO 日期与 `week-list-*` 点击路由；既有 `weekScheduleRendersMatrixHeadersAndRoutesAddAndCourseSource` 未改名且继续通过。
- 主机验证：`./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` BUILD SUCCESSFUL；Debug／Release JVM 各 **92 tests、0 failures／errors／skipped**；`lintDebug` **0 errors、20 warnings**；Android 文档 70 tests OK、两个文档脚本与 `git diff --check` 通过。
- 设备验证：API 37 ARM64 `emulator-5556`（1080x2400／420dpi，font_scale 1.0 与 1.3、light 与 dark）完整 `connectedDebugAndroidTest --no-daemon` **72 tests、0 failures／errors／skipped**；定向 `am instrument` 5 个周页用例 `OK (5 tests)`。上一节记录的既有 P3-04 失败用例（`chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales`、`r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState`）在本轮同一 AVD 上通过；本轮未改任何 P3-04 代码或测试、也未专门分析该差异，仅如实记录。
- 截图证据：`docs/Android/evidence/p3-05-visual-r1/` 下浅色 100%、浅色日清单、深色 100%、深色日清单、深色 130% 共 5 张生产截图（真实入口新建学期「2026 秋季学期」与课程 `Advanced Mathematics`），逐张目视结论与数据说明见该目录 README.md；设备结束时恢复 light／`font_scale=1.0` 并强停前台应用。
- 环境限制：本会话文件沙箱不允许写工作区以外路径，构建使用可写临时目录承载 Gradle 用户目录／Android 用户目录／TMPDIR（未改变仓库配置）；`git ls-remote` 因本机 SSH host key 校验失败无法实时查询远端，推送结果以实际命令返回为准。
- 准确状态：**已实现、已测试、有 API 37 生产截图证据；尚未独立复审，用户视觉验收未进行**。两个历史 P3-04 connected 失败在本轮通过，不据此宣称已修复或已验收；P3-05／A03、A03 之外的 A 项、整个 P3 与完整 App 仍未验收，后续阶段未授权。

## DeepSeek V4.1 Flash 实施交接：P3-05／A03 周课表视觉返修 R1（2026-09-18）

> 状态更新（2026-09-18）：本节描述的委派未被执行。用户随后指示由当前主窗口直接实施，R1 已按上一节完成实现、测试、API 37 设备验证与截图证据；本节保留作历史记录，其范围、允许路径与验收清单仍为当前有效边界。

### 交接目的

用户已查看 P3-05 生产截图，并在 macOS“备忘录”的《APP 还有的问题》中提出 7 组周课表视觉问题。由于当前中转站 Terra／Luna 不稳定，用户明确决定将后续实施交给 **DeepSeek V4.1 Flash**；本节是可独立执行的交接，不要求等待 Terra 恢复。Terra 恢复后仍可回到本 Sol 主窗口继续复审，但不得与 DeepSeek 同时写入共享工作区。

### 当前代码基准与工作区

- 仓库：`/Users/takagisan/课表软件`
- 分支：`Android`；禁止合并 `main`
- 基准提交：`f6d2a80`（`docs(android): close p3-05 technical review`）
- 本地 HEAD、`origin/Android`、远程 `refs/heads/Android` 在交接前一致；工作区干净。
- iOS 只读视觉／结构基准：`/Users/takagisan/课表软件-IOS/ios`，当前参考提交 `fc3ddfb`；不得修改 iOS 工作区。
- 当前没有运行中的 Gradle、模拟器或连接设备。Android SDK 的 `adb` 不在默认 PATH；如需设备验证，使用已配置 SDK 的绝对路径并在结束后关闭模拟器、确认 `adb devices` 为空。

### 已完成事项（不要重复实施）

- P3-01 展示模型、P3-04 课程编辑／ADD 路由和 P3-05 第一版真实周课表均已实现；相关实现提交为 `26f2fab`、`b36532e`、`477eae6`、`865ef6e`、`ce5c1b6`，均已推送。
- P3-05 第一版已通过 Sol 技术复审；用户已实际查看生产截图并提出本轮 R1 视觉返修意见，但尚未验收 R1。
- 不能重复改动或重新验收 P3-04 已关闭的用户视觉范围。

### 本次授权范围：只实施 P3-05 视觉返修 R1

允许修改：

- `Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt`
- `Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt` 或与本轮周页契约直接相关的测试文件
- 必要的 `docs/Android/handoff.md`、`docs/Android/evidence/` 交接／证据文件

不得修改：iOS、Web、Room/schema、DataStore、共享协议、业务规则、设置页、通知、导入导出、P3-04 既有视觉实现、`main`，以及与本轮周页无关的 Android 模块。不要复制一套新的周课表领域规则；继续复用 `WeekSchedulePresentation`、`WeekMatrixPresentation`、`ScheduleDisplayText`、既有 `openAddCourse()`／`openCourseAt()` 和 `AcademicCalendarPreferences`。

### R1 具体验收清单

1. `SCHEDULE :// WEEK MATRIX` 对齐 iOS：黑底、白色粗体标签。
2. 顶部标题对齐 iOS：左侧粗体“课表”，右侧 `WEEK` 与两位教学周数字；数字随选中教学周变化。
3. 教学周控件使用 iOS 同款白底细边框结构：左右箭头、中间学期名、`第 03 教学周`、`ODD WEEK/EVEN WEEK`；中部点击返回当前周；不保留错位的独立“返回当前周”布局。
4. 七日日期条增加上下细横线、每日竖分隔线；选中日期保留反相背景并在日期下方显示黄色下划线。
5. 周视图标题改为 `05 周视图`，右侧动态显示 `MON–SUN / N PERIODS`，其中 `N` 取真实节次数。
6. 周矩阵对齐 iOS：七列竖线、横向行线；`TIME`、星期、节次编号／时间使用黑色粗体并居中；行高和表头高度足以让课程块可读。午休只在设置启用且展示模型产生 break 时显示，使用青色底、上下细边框、左侧标题／分隔线／时间；不得改变午休业务规则。
7. 日清单标题删除完整日期（如 `2026-09-16`），改为 iOS 风格的编号、周几和条目数；课程卡片增大到可读尺寸，显示序号、起止时间、节次、课程名、教室／教师和右箭头，继续支持点击编辑。

### 必须验证与回报

- 先核对 `git status --short --branch`、`git log --oneline -n 12`、`git ls-remote --heads origin Android`，确认基准未漂移。
- 先读本节、`docs/Android/product-baseline.md` 的周课表相关章节、`docs/Android/technical-design.md` 与 `docs/Android/implementation-plan.md` 的必要章节，再读 iOS `WeekScheduleView.swift`／相关 `CourseStyle.swift` 和 Android 当前周页；不要无理由全量复制文档。
- 新增或更新 Compose 契约／回归测试，至少覆盖：黑底白字周页标题、教学周动态文本及箭头／返回当前周、日期条分隔与选中下划线、`05 周视图`／动态 periods、矩阵列／行线和午休条件显示、日清单无 ISO 日期及课程点击路由。
- 运行与改动相符的 Debug／Release JVM、AndroidTest APK、`lintDebug`、文档测试和 `git diff --check`。如有设备，补做 API 37 浅色／深色／大字体视觉核对；没有设备时如实记录限制。
- 完成后只暂存本任务文件，创建 Git commit 并推送 `origin/Android`；回报实际提交、测试结果、截图／设备限制和未解决问题。不得声称用户视觉验收完成。
- 当前已知的完整 connected 套件历史失败仍是两个既有 P3-04 测试：`chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales`、`r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState`。不要把它们静默改入 R1；若重跑仍失败，单独报告。

### 可直接发送给 DeepSeek V4.1 Flash 的提示词

```text
你是本仓库 Android 分支的唯一实施者。请在 /Users/takagisan/课表软件 上实施 P3-05／A03 周课表视觉返修 R1。

基准：分支 Android，HEAD／origin/Android 预期为 f6d2a80；不合并 main。开始先运行：
DEVELOPER_DIR=/Library/Developer/CommandLineTools git status --short --branch
DEVELOPER_DIR=/Library/Developer/CommandLineTools git log --oneline -n 12
DEVELOPER_DIR=/Library/Developer/CommandLineTools git ls-remote --heads origin Android
若基准或工作区有变化，先停止并报告，不覆盖其他窗口改动。

用户已授权本次只修改 P3-05 周课表视觉层和对应测试／交接证据。允许路径：Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt、与周页直接相关的 Android 测试文件、必要的 docs/Android/handoff.md 与 docs/Android/evidence/。禁止修改 iOS、Web、Room/schema、DataStore、共享协议、业务规则、设置、通知、导入导出、P3-04 既有视觉范围和 main。你是唯一写入者，不要创建其他 Agent。

先增量阅读 docs/Android/handoff.md 本节、product-baseline.md／technical-design.md／implementation-plan.md 的周课表相关章节；只读参考 /Users/takagisan/课表软件-IOS/ios 的 WeekScheduleView.swift、CourseStyle.swift（iOS 当前参考 fc3ddfb）。继续复用 Android 的 WeekSchedulePresentation、WeekMatrixPresentation、ScheduleDisplayText、AcademicCalendarPreferences、openAddCourse() 与 openCourseAt()；不要复制新的领域规则。

必须完成以下视觉返修：
1. SCHEDULE :// WEEK MATRIX 改为 iOS 同款黑底白色粗体标签；左侧标题为粗体“课表”，右侧为动态 WEEK＋两位教学周数字。
2. 教学周控件改为白底细边框面板，含左右箭头、学期名、第 XX 教学周、ODD WEEK/EVEN WEEK；中间点击返回当前周。
3. 日期条增加上下横线和每日竖分隔线；选中日期下方有黄色下划线。
4. 周视图标题为 05 周视图，右侧动态显示 MON–SUN / N PERIODS。
5. 矩阵补齐七列竖线和行线；TIME、星期、节次编号／时间黑色粗体居中；提高表头／行高使课程块可读；午休仅在设置启用且展示模型产生 break 时显示，使用青色底和细边框，不改业务规则。
6. 日清单标题移除完整 ISO 日期，改为编号／周几／条目数；课程卡片增大并显示序号、起止时间、节次、课程名、教室／教师和右箭头，点击仍路由到既有课程编辑。

新增或更新 Compose 契约测试覆盖上述结构、动态周文本、选中下划线、矩阵线框、午休条件、日清单无日期与点击路由。运行 Debug／Release JVM、AndroidTest APK、lintDebug、文档测试和 git diff --check；有可用 API 37 设备时做浅色／深色／大字体截图核对，没有设备要如实记录。完整 connected 套件已有两个与 P3-04 相关的历史失败，不要擅自修复或混入本任务；若重跑失败请单独列出。

完成后只暂存本任务文件，创建 commit 并推送 origin/Android。最终回报实际 diff、提交号、测试／截图证据、限制和未解决问题；不要声称用户视觉验收完成。
```

## P3-05／A03 周课表 UI 已通过 Sol 技术复审，待用户视觉验收（2026-09-15）

本轮在 `Android` 分支将 `SCHEDULE` 从“课表（壳层）”替换为周课表垂直切片。实现只使用既有
`WeekSchedulePresentation`、`WeekMatrixPresentation` 和 `ScheduleDisplayText` 读取结果，未复制课程、单双周、停课、调课、冲突或 lane 领域规则；课程矩阵点击按稳定 occurrence 的 `courseIndex` 调用既有 P3-04 `openCourseAt` 路由。

- 已接入周前后与当前周控件、七日日期条、矩阵项目/冲突色、跨节高度、午休预留 tag、选中日清单及停课/无课状态；使用 `rememberSaveable` 保存选周/选日，不因时钟刷新重置。
- 后续复审收口：矩阵现以可用内容宽度减去 44dp TIME 列后均分七日列，课程块按 presentation 的 `startRow`、`rowSpan`、`lane` 和 `laneCount` 定位；午休按 `insertionRow` 与节次标签共享纵轴。周/日跟随状态独立，周页 ADD 复用主壳同一浮动动作。
- 已完成主机验证：`testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintDebug assembleDebugAndroidTest --no-daemon --console=plain` BUILD SUCCESSFUL；新增 Compose 契约覆盖 SCHEDULE 真页、七列 TIME 表头、节次、ADD 回调与 occurrence 来源 index。
- 文档验证已完成：`docs/tests/android-documentation.test.py` 70 tests OK、`documentation.test.sh` passed、`repository-layout.test.sh` passed、`git diff --check` passed（均使用 `DEVELOPER_DIR=/Library/Developer/CommandLineTools`）。API 37 ARM64 `connectedDebugAndroidTest` XML 为 `Android/app/build/outputs/androidTest-results/connected/debug/TEST-qingke-api37-r3-arm(AVD) - 17.xml`，全套 68 tests 中 66 passed、2 failures、0 errors、0 skipped；失败为既有 `chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales` 与 `r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState`，非 P3-05，且单独复跑仍稳定失败。定向 `QingKeAppTest#weekScheduleRendersMatrixHeadersAndRoutesAddAndCourseSource` 通过。
- 已完成生产 debug 入口与截图目视核对：API 37 ARM64 真实入口创建 `2026 秋季学期`／`VisualP305`，确认品牌头、周切换／当前周、七日日期条、TIME 轴、周三课程块、午休分隔、悬浮 ADD 与底栏层级；证据截图为 `/tmp/p3-05-week-empty.png`、`/tmp/p3-05-week-course.png`。复核后模拟器已关闭，`adb devices` 为空。
- Sol 已集中核对 P3-05 实际 diff、布局契约、主机与定向 connected 证据及生产截图，确认技术复审通过。当前准确状态为已实现、已测试、已通过 Sol 技术复审，待用户视觉验收；完整 connected 套件仍受上述两个既有 P3-04 失败影响，不能写成全部测试通过或用户已验收。
- 修改限定在 Android UI 与本交接；未修改 iOS、Web、Room/schema、DataStore、共享协议、设置/通知/导入导出或 `main`。

## 切换新 Sol 主窗口：P3-04 已验收，下一项待授权（最新，2026-09-15）

本次切换前已完成 P3-04／A04／A05 当前实现范围的用户产品／视觉验收记录。接手基准为 Android
`62075fe0a2667c603f5c85e8e2e88548bfac1461`（`docs(android): record p3-04 user acceptance`）；核对时
本地 HEAD、`origin/Android` 与远程 `refs/heads/Android` 一致，工作区干净，未合并 `main`。本次切换只
更新交接文档及对应验证；其提交与推送结果以交付消息和 Git 历史为准，避免在文件中自引用。

- **准确状态**：P3-04 已实现、测试、完成 API 37 生产验证、通过 Sol 技术独立复审，并由用户于
  2026-09-15 明确确认视觉结果通过。P3-04／A04／A05 当前实现范围的验收门槛已关闭；这不等于 A02、
  A03、A06—A11、整个 P3 或完整 App 已验收。
- **当前运行与 Agent**：核对时没有运行中的 Gradle、模拟器或连接设备。唯一旧 Terra
  `/root/p3_04_visual_r3`（`gpt-5.6-terra`／`high`）在当前主会话 Agent 列表中为 `completed`；其工具摘要
  仍写“待用户验收”，属于完成时的历史状态，不得覆盖本节最新结论，也不得在未获新任务授权时自动续接。
- **可进行的工作**：新 Sol 主窗口先按增量规则核对分支、HEAD、远端和本节，不重复 P3-04 实施或验收。
  下一项建议为 P3-05 周课表 UI（A03）的分析与定界，因为 P3-01 已有纯 Kotlin 周课表展示模型，P3-04
  已接通课程编辑路由；但用户尚未明确授权 P3-05 分析或实施，接手本身不构成授权。
- **后续边界**：只有用户在新窗口明确授权后，才可先开展 P3-05 只读分析、核对当前 iOS 周课表基准并
  给出范围、风险和验收清单；应用实施仍需随后取得明确授权，再按一个 Sol 主 Agent＋至多一个 Terra
  执行子 Agent及单写入者规则进行。不得自动进入完整设置／学期编辑、教学日历、文件迁移、提醒、发布或
  合并 `main`。

## P3-04 已通过 Sol 技术复审及用户视觉验收（最新，2026-09-15）

本轮以 Android `18dad62d9bacd0a7f8d833ebd68043d156be7970` 为基准，开始时工作区、`origin/Android` 与远程 Android 一致且干净。仅按用户本轮反馈改 TODAY 右下角 ADD 的视觉、对应 instrumentation 回归及 R7 生产证据；iOS `/Users/takagisan/课表软件-IOS/ios` 仅只读参考 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001` 的 `TerminalFloatingAction`，未修改 iOS、Web、Room/schema、共享协议、业务规则、其他 TODAY 字体、chooser、课程编辑、其他阶段或 `main`。

- 黄色 64dp ADD 恢复 iOS 同款约 3dp inset、1dp、75% alpha 的完整白色内框；右上 13dp 白色半透明折角仍在框上层，轻微阴影绘制在折角下方且未恢复黑框。位置、点击区、中文语义及回调均未变。
- 粗体文本 `+` 已替换为平台无关的 25dp Canvas 细线十字：横竖等长、居中对称、约 1.6dp 圆端笔画；`ADD` 保留 8sp monospace 黑体和既有视觉间距。像素回归覆盖浅／深色及 100%／130%，分别证明四边白框、白角与轻影存在、黑框为零，以及十字尺寸、中心、对称和笔画宽度上限；同时确认 `ADD` 文本存在，能拒绝 R6 的“无白框＋粗文本加号”实现。
- R7 实现、测试和全新生产证据提交为 `25b44f3`（`fix(android): restore r7 add action frame and plus`），Terra 交接提交为 `5171650`（`docs(android): record r7 add visual delivery`），均已推送 `origin/Android`。证据位于 `docs/Android/evidence/p3-04-visual-r7/`：浅色 100%、深色 100%、深色 130% 的 TODAY 均逐张目视核对，`VisualR5` 在页面中可见；没有沿用 R6 截图。
- 验证：Debug／Release JVM 各 **92 tests、0 failures/errors/skipped**；Debug、Release、AndroidTest APK 构建通过；`lintDebug` **0 errors、20 warnings**；API 37 ARM64 `connectedDebugAndroidTest --rerun-tasks` **67 tests、0 failures/errors/skipped**。Android 文档 **68 tests**、两个文档脚本与 `git diff --check` 通过。Sol 已集中核对 R7 完整实际 diff、四边白框与无黑框的正负向像素断言、白角及轻影、细线十字的尺寸／中心／对称／笔画上限，并逐张目视核验浅色 100%、深色 100%、深色 130% 三张生产截图，确认技术复审通过。最终模拟器已恢复为生产 APK、light／`font_scale=1.0`、保留 `VisualR5` 并停在 TODAY；前台为 `MainActivity`，目标 logcat 无 FATAL/ANR。

用户于 2026-09-15 明确确认最新视觉结果通过，P3-04／A04／A05 当前实现范围的产品与用户视觉验收门槛关闭。该确认不扩大为 A02、A03、A06—A11、整个 P3 或完整 App 验收，也不授权周课表、完整设置／学期编辑、教学日历、文件迁移、提醒、发布或合并 `main`；下一阶段仍须另行分析和授权。

## P3-04 视觉 R6 已通过 Sol 技术复审，待用户视觉验收（最新，2026-09-15）

本轮从 Android `5d271835cf44d4a9535925a426177b99c180522b` 开始，工作区、`origin/Android` 和远程 Android 一致且干净。仅实施用户已授权的 TODAY 日期与 ADD、ADD chooser 及 CourseEditor RGB 滑块；iOS 工作区 `/Users/takagisan/课表软件-IOS/ios`（只读 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`）未修改，未改 Web、Room/schema、共享协议、业务规则、周课表、设置、导入导出、其他阶段或 `main`。

- Sol 首次集中复审发现 Android instrumentation 测试无条件读取 API 28 才提供的 `Typeface.weight`，与 minSdk 26 不相容；生产实现和截图不受影响。本轮仅修复该测试兼容性：`QingKeAppTest` 只在 API 28+ 读取 `Typeface.weight` 并保持 weight 100 的精确断言；API 26–27 改为不调用该 API 的 fallback 非粗体／非斜体断言。测试修正提交为 `dc6d973`（`test(android): guard r6 date typeface assertion`），交接提交为 `b7f15c6`（`docs(android): record r6 typeface test compatibility fix`），均已推送 `origin/Android`；`assembleDebugAndroidTest`、`lintDebug` 通过，API 37 ARM64 定向 typography instrumentation **1 test、0 failures/errors/skipped**。

- RGB 每行现在为同一基线的 `R [三位值] [滑轨]`、`G`、`B`：值使用等宽单行文本，滑轨保留原实时写回、严格 `#RRGGBB`、disabled 与触控。Compose/API 37 回归在 100%／130% 检查三个标签、值、轨道顺序与同基线，并验证拖动红通道确实变化。
- TODAY ADD 保持 64dp、位置、语义和回调；移除了旧黑色内框／包裹线，将右上 13dp 折角替换为黄色底层上的 75% 白色折角和轻微偏移阴影，`+` 与 `ADD` 均为粗体。像素回归负向断言旧黑框为零，正向验证白角与阴影。
- 日期数字独立使用 Android `sans-serif-condensed` 的 weight 100 Typeface，不再以 NORMAL 基础 Typeface 配合 Thin hint；只影响日期数字，中文继续由系统 fallback。chooser 的 22dp 外框改为约 3.3dp 圆角、内部加号继续严格居中，“新建一门课程”改为细体；像素回归验证圆角角点无墨、边缘有墨及既有加号中心负向探针。
- 生产证据在 `docs/Android/evidence/p3-04-visual-r6/`，README 列出浅色／深色／130% TODAY、chooser 及 RGB 滑块 100%／130% 的逐张核验结果。返修后完整 Debug／Release JVM 各 **92 tests、0 failures/errors/skipped**；Debug、Release、AndroidTest APK 构建通过；`lintDebug` **0 errors、20 warnings**；API 37 ARM64 完整 `connectedDebugAndroidTest --rerun-tasks` **67 tests、0 failures/errors/skipped**，XML 位于 `Android/app/build/outputs/androidTest-results/connected/debug/TEST-qingke-api37-r3-arm(AVD) - 17.xml`。Android 文档 **68 tests**、两个文档脚本和 `git diff --check` 均通过。实现、测试、R6 证据与 README 提交为 `3e9f1ea`（`fix(android): refine r6 terminal course surfaces`），首次交接提交为 `eaeb240`（`docs(android): record r6 visual delivery`），均已推送 `origin/Android`。
- 最终 debug APK 已生产冷启动，重新创建并保留 `VisualR5`，恢复 light／`font_scale=1.0` 且停在 TODAY；目标 logcat 无 FATAL/ANR。Sol 已集中核对 R6 完整实际 diff、API 26–27 字体 fallback 与测试保护、ADD 正负向像素证明、chooser 圆角及加号中心探针、RGB 实时写回和 100%／130% 单行布局，并逐张目视核验 6 张生产截图，确认技术复审通过。**用户视觉验收仍待进行；不得据此启动下一阶段。**

## P3-04 视觉 R5 已通过 Sol 技术复审，待用户视觉验收（最新，2026-09-15）

本轮以 `b636eeddc02ba7cc51ae5bb14c05ed0b6c2a91ec` 为 Android 基准，仅实施用户授权的三模式自定义课程颜色面板、星期／起止节次终端下拉菜单和每周／单周／双周终端分段控件。iOS 工作区 `/Users/takagisan/课表软件-IOS/ios`（只读基准 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`）未修改；未改 Web、Room/schema、共享协议、持久化格式、业务规则、周课表、设置、导入导出、其他阶段或 `main`。

- 实现、测试及 R5 首版证据提交为 `ba1be75`（`feat(android): add r5 terminal course controls`），已推送 `origin/Android`。Sol 目视核对四张首版截图后发现“COLOR MATRIX”标题下没有色块：`ColorGridPicker` 的 `fillMaxSize()` 处在可滚动、无界高度的内容中，实际没有可绘制高度。本次只将网格单元改为明确 `42dp` 外层／`36dp` 色块高度，并以 bitmap 像素回归验证红、绿代表色数量与选中后的 `editor.color`／勾标记；未改颜色持久化或任何业务规则。返修实现、测试、经核验截图和证据说明提交为 `7c2663a`（`fix(android): render r5 color matrix cells`），Terra 交接提交为 `12158eb`（`docs(android): record r5 grid fix delivery`），均已推送 `origin/Android`；本次 Sol 复审状态提交待生成，避免在文件中自引用。
- `TerminalDropdownMenu` 供星期、开始节次、结束节次共用：直角、不透明主题 surface、边框／阴影、青色分隔线、monospace 选项，当前项为 inverse 背景和黄色勾；保留原 choices、回调、disabled 与无障碍。`TerminalRepeatSelector` 取代 Material outlined buttons，三项 48dp、`selectableGroup`/radio 语义、深色反相选中态和黄色状态点，未改 `RepeatRule` 映射。
- 新增纯 Kotlin 颜色契约覆盖网格代表色、HSV 平面边界／回环、RGB/HSV/HEX 边界；API 37 Compose 用例覆盖三模式切换、网格像素、网格／光谱／RGB 实时更新、状态保留、三类下拉回调和 repeat 点击。返修后重新执行 Debug／Release JVM，各 **92 tests、0 failures/errors/skipped**；Debug、Release、AndroidTest APK 构建通过；`lintDebug` 为 **0 errors、21 warnings**；API 37 ARM64 完整 `connectedDebugAndroidTest --rerun-tasks` 为 **67 tests、0 failures/errors/skipped**，XML 位于 `Android/app/build/outputs/androidTest-results/connected/debug/TEST-qingke-api37-r3-arm(AVD) - 17.xml`。Android 文档 **68 tests**、两个文档脚本及 `git diff --check` 均通过。
- 最终生产入口保留 `VisualR5` 并重新打开 EDIT；返修后已实际核对网格浅色 100%、深色 100%、深色 130%，并新增经目视核验的浅色光谱、滑块、开始节次菜单和 repeat 截图，清单在 `docs/Android/evidence/p3-04-visual-r5/README.md`。目标 logcat 无 FATAL/ANR；设备已恢复为 light／1.0，停在 `VisualR5` 的 EDIT“网格”面板。Sol 已集中核对 R5 完整实际 diff、网格尺寸根因修正、像素及交互回归、三类末尾菜单选项滚动选择，以及 7 张返修后关键截图，未发现新的技术阻断，确认技术复审通过。**用户视觉验收仍待进行；不得据此启动下一阶段。**

## P3-04 视觉 R4 已通过 Sol 技术复审，待用户视觉验收（最新，2026-09-15）

本轮仅按用户明确授权对齐三组最新版 iOS 基准：chooser“新建一门课程”图标、四类课程成功通知和 TODAY 日期／featured／课程序列字体层级。iOS 工作区 `/Users/takagisan/课表软件-IOS/ios` 仅作只读参考，未修改或提交；未改 Web、Room、共享协议、业务规则、周课表、设置、导入导出、其他阶段或 `main`。

- 开始基准为 `70a1defb931af8d786c054a301b832a09bfe8cba`，分支 `Android`，开始时工作区干净且本地、`origin/Android`、远程 Android 一致；实施提交为 `20979ce`（`fix(android): align visual r4 course surfaces`），已推送至 `origin/Android`。本交接更新提交仍待生成，避免在文件中自引用。
- Sol 集中复审指出原 chooser 像素测试把方框与十字合并取包围盒，可能由对称方框掩盖内部十字偏移。本次以 `db2465892e23054e0a2103eb202f4c365cf36c75` 为基准，仅修正该测试：裁掉外框带后分别识别横、竖十字，校验其中心接近画布中心及上下／左右臂长度对称；三像素平移会触发断言。另补齐 `TodayVisualSpec.featuredCourseNameSize == 20.sp` 的规格断言，未改生产 Canvas、通知、字体或既有截图。
- chooser 继续以 Android Canvas 绘制 22dp 方框＋十字，所有笔画以同一中心计算，不使用或复制 Apple SF Symbol；保留现有点击区、中文无障碍语义、回调和正文前景。像素回归继续检查浅／深色前景和非 cyan。
- ViewModel 成功文案精确统一为 `SYSTEM // 课程添加成功`、`SYSTEM // 添加上课安排成功`、`SYSTEM // 课程修改已保存`、`SYSTEM // 课程删除成功`；黄色左线、圆点、勾、2.6 秒计时及 ADD／tab 堆叠未变。JVM／UI 回归覆盖四句精确文本和浅／深／130% 通知布局。
- `TodayVisualSpec` 固化 iOS 对齐的 74sp thin 日期／106dp 列、featured 32/13sp 与 82dp、sequence 24/11sp 与 66dp，以及 20sp featured 课程名；Android 使用 `sans-serif-condensed` 系统映射，缺失字形由系统 fallback（包括中文）渲染。最终 production evidence 在 `docs/Android/evidence/p3-04-visual-r4/`。既有最终验证：Debug／Release JVM 各 **91 tests、0 failures/errors/skipped**；Debug、Release 和 AndroidTest APK 构建通过；`lintDebug` **0 errors、21 warnings**；API 37 ARM64 `qingke-api37-r3-arm`（`emulator-5554`）完整 `connectedDebugAndroidTest --rerun-tasks` **66 tests、0 failures/errors/skipped**。本次复审修正后，Debug／AndroidTest APK 编译通过，API 37 ARM64 分别定向执行 `chooserPlusSquareUsesThemeForegroundInsteadOfCyan` 与 `todayTypographyUsesCondensedIosEquivalentScaleAndFitsAtLargeFont`，均通过；测试修正提交为 `87ba031`（`test(android): tighten r4 plus geometry probe`），已推送至 `origin/Android`。Sol 随后独立复跑上述两项 connected 测试，均通过，并集中核对完整实际 diff、负向像素证明、四条通知文案及浅色／深色／130% 最终截图，确认技术复审通过。生产入口已实测浅色／深色／130%，目标 logcat 未匹配 FATAL／ANR；设备恢复 light／1.0 且停在 TODAY。Android 文档 68 tests、两个文档脚本和 `git diff --check` 已通过；**用户视觉验收仍待进行。** 不得据此启动下一阶段。

## P3-04 视觉 R3 删除确认层不透明度返修已通过 Sol 技术复审，待用户视觉验收（最新，2026-09-15）

Sol 第二轮集中审查确认旧浅色／深色 130% 删除确认截图的面板内容层仍透出背景星期、节次和周数字体。本轮仅修复 `terminalModalSurface`：渐变高光改用预混合的不透明颜色，保留既有高光、边框和阴影；scrim、确认流程、业务回调和其他视觉项不变，未改 iOS、Web、`source/`、Room、共享协议、其他阶段或 `main`。

- 开始基准为 `66fe7cc5cad81a9e7f6a39c29344f346029dba16`，分支 `Android`，开始时工作区干净且本地、`origin/Android`、远程 Android 一致；应用、测试、证据和本节首版记录已提交为 `05ed072` 并推送 `origin/Android`。本节后续交接文档提交编号以 Git 历史为准，不在文件中自引用。
- 新增确定性 instrumentation 像素回归：面板下方放置 3dp 黑白高对比条纹，在没有前景内容的采样带检查相邻像素跳变。若 surface 或 highlight 以 alpha 透出底图，条纹会产生大幅跳变并失败；不透明渐变只保留平滑的颜色过渡。既有删除 dialog 的 bounds、主题／130% 和颜色契约继续保留。
- 已以最终 debug APK 重拍 `delete-modal-light-100.png` 和 `delete-modal-dark-130.png`，肉眼核对确认面板内部没有背景星期、节次或周数字体。最终验证：Debug／Release JVM 各 **90 tests、0 failures/errors/skipped**；Debug、Release 和 AndroidTest APK 构建通过；`lintDebug` **0 errors、21 warnings**；API 37 ARM64 `qingke-api37-r3-arm`（`emulator-5554`）完整 `connectedDebugAndroidTest --rerun-tasks` **65 tests、0 failures/errors/skipped**，XML 位于 `Android/app/build/outputs/androidTest-results/connected/debug/TEST-qingke-api37-r3-arm(AVD) - 17.xml`。已重新安装最终 debug APK、创建并保留 `VisualR3`、恢复 light／1.0 且停在 TODAY；目标 logcat 未匹配 FATAL／ANR。文档验证和 `git diff --check` 已通过，实现及交接提交均已推送。Sol 已集中核对实际 diff、回归原理、测试结果和浅色／深色 130% 最终截图，确认本轮技术复审通过；**用户视觉验收仍待进行。** 不得据此启动下一阶段。

## P3-04 视觉 R3 集中复审返修已完成，待 Sol 再复审／用户验收（最新，2026-09-15）

本轮严格限定在 P3-04 视觉 R3＋首次设置月历的集中复审返修：修复月历前后按钮的空方框、TODAY ADD 装饰被内容层覆盖、canonical 截图时机不正确，以及确认层仅有 bounds 断言的问题。未改 iOS、Web、`source/`、Room、业务规则、共享协议或其他阶段，未合并 `main`。

- 开始基准为 `2c086dad4416770f9edeef6b526f5af7b2de7fff`，分支 `Android`，开始时工作区干净且 `origin/Android` 同步；应用、测试、证据和本节首版记录已提交为 `c08c61e` 并推送 `origin/Android`。本节后续交接文档提交编号以 Git 历史为准，不在文件中自引用。
- 月历前后按钮改为无外框的 `48dp` 点击区、黄色 `‹`／`›`，保留 test tag 和中文 content description；新增浅色、深色及 130% 字体的像素／无障碍回归。TODAY ADD 的 3dp 内框、13dp 折角与斜线移至黄色内容层，新增 bitmap 像素回归；校验卡增加 coral 图标／左线像素证明，确认层增加高不透明 surface 像素契约。
- 以最终生产入口重新拍摄首次设置月历、TODAY、校验、成功、chooser、危险区 footer、浅色删除确认和深色 130% 删除确认；证据清单见 `docs/Android/evidence/p3-04-visual-r3/README.md`。清数据后创建并保留 `VisualR3`，最终设备恢复 light／1.0 并停在 TODAY。
- 最终验证：Debug／Release JVM 各 **90 tests、0 failures/errors/skipped**；Debug、Release 和 AndroidTest APK 构建通过；`lintDebug` **0 errors、21 warnings**；API 37 ARM64 `qingke-api37-r3-arm`（`emulator-5554`）最终完整 `connectedDebugAndroidTest --rerun-tasks` **64 tests、0 failures/errors/skipped**，XML 位于 `Android/app/build/outputs/androidTest-results/connected/debug/TEST-qingke-api37-r3-arm(AVD) - 17.xml`。生产入口与上述截图已核对，目标 logcat 未匹配 FATAL／ANR。
- 准确状态：**已实现、已测试、等待 Sol 再次集中实际 diff 复审；用户尚未验收。** 不得据此启动下一阶段、重新设计界面或声称用户视觉认可。

## P3-04 视觉 R3＋首次设置月历补充已实施并待 Sol 审查／用户验收（最新，2026-09-15）

本轮仅实施用户明确授权的七项视觉修正及首次设置日期控件：开始日期从系统 `DatePickerDialog` 改为页面内、周一开头的中文月历（跨月／跨年和闰日）；TODAY ADD 为 64dp；chooser `plus.square` 使用主题正文前景；危险区 footer 移至删除按钮面板外下方；确认层采用高不透明度专用 elevated acrylic；成功通知改为黄色左线／圆点／勾；课程和学期校验改为危险提示卡。未修改周课表、完整设置、通知、导入导出、iOS、Web、`source/`、Room 或共享协议，也未合并 `main`。

- 开始基准为 `951777b9471202aa7502949b074add49b53b4e5f`，分支 `Android`，开始时工作区干净；远程 `origin/Android` 同为该提交。代码、测试和证据提交为 `fa5317ceb069035e06e12243713dc2e60797eed9`，已推送 `origin/Android`；本节后续交接文档提交不在此自引用。
- 新增 `SemesterMonthGrid` 的 JVM 边界测试，Compose／API 37 回归涵盖月历展开收起、跨年／闰日与受控回调、64dp ADD 与通知垂直栈、chooser 图标浅深像素前景、危险区顺序、modal 表面、toast、浅深／130% 校验卡。
- 完整验证：Debug／Release JVM 各 **90 tests、0 failures/errors/skipped**；`assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest` 通过；`lintDebug` **0 errors、21 warnings**；API 37 ARM64 `qingke-api37-r3-arm`（`emulator-5554`）完整 `connectedDebugAndroidTest --rerun-tasks` **63 tests、0 failures/errors/skipped**，XML 位于 `Android/app/build/outputs/androidTest-results/connected/debug/`。
- 生产入口：清数据冷启动显示首次设置，页面内月历已实际展开；保存后进入 TODAY，CREATE／EDIT 保存和成功通知已检查；浅色／深色／130% 复拍在 `docs/Android/evidence/p3-04-visual-r3/`。目标 logcat 未匹配 FATAL／ANR。设备已恢复 light／1.0，生产 App 停在 TODAY，并保留 `VisualR3` 课程供用户查看。
- 准确状态：**已实现、已测试、等待 Sol 集中实际 diff 审查；用户尚未验收。** 不得据此启动下一阶段或声称用户视觉认可。

## 切换新 Sol 主窗口：P3-04 B4 R2 等待用户视觉验收（最新，2026-09-15）

用户因当前窗口上下文较长，要求保存现场并切换到新的 Sol 主窗口。当前仓库为 `/Users/takagisan/课表软件`，分支 `Android`；交接前代码与流程基准为 `b85e6df`，其中 `cccbb26` 是 TODAY／ADD chooser／CourseEditor 视觉返工及验证提交，`b85e6df` 是“完整实施与集中审查”协作规则提交。二者均已推送 `origin/Android`，不合并 `main`。

- 当前任务已经完成实施、自动化、生产入口验证和 Sol 独立技术审查；用户尚未在最新结果上完成视觉验收。新窗口先等待并接收用户实际查看 TODAY、chooser、CREATE／EDIT／APPEND、确认弹窗、浅色／深色／130% 字体后的反馈，不得把现有技术证据解释为用户认可。
- 若用户确认通过，只更新用户视觉验收状态及对应验证，提交并推送 `Android`；再说明可选后续阶段并等待明确授权。若用户报告问题，先形成可验证的问题清单并限定在本轮 TODAY／ADD／课程编辑范围内，再实施修正；不自动扩展到周课表、完整设置、通知、导入导出、iOS、Web、`source/`、共享协议、其他 P3 阶段或 `main`。
- 本轮最终证据：Debug／Release JVM 各 88 tests 全通过；`lintDebug` 0 errors、21 warnings；三类 APK 构建通过；API 37 ARM64 完整 connected 58 tests 全通过；生产首次设置、TODAY ADD→CREATE、force-stop 冷启动和 FATAL／ANR 检查通过；Android 文档测试 66 tests、两个文档脚本和 `git diff --check` 通过。canonical 截图位于 `docs/Android/evidence/p3-04-visual-r1/`。
- 当前工作区干净，没有未提交改动；没有运行中的 Gradle、模拟器或其他写入者。当前子 Agent 工具只显示主窗口；旧中转站 Terra `/root/p3_03_r2_visual_fix` 曾为 `gpt-5.6-terra/high`，现不在列表中且已停止写入。新窗口不得假定可直接使用旧标识；先查询自己的 Agent 列表。同一修正至多一个 `gpt-5.6-terra/high`，禁止它创建子 Agent，并按新规则让 Terra 完整实施后由 Sol 集中审查。
- 新窗口按增量阅读先核对 `git status --short --branch`、`git log -n 12`、远程 `Android` 和本节；仅在收到用户反馈或明确恢复后补读相关代码与证据，不重复实施 `cccbb26`，不自动启动新阶段。

## 协作调整：完整实施后集中审查（2026-09-15）

用户根据账单分析授权调整：Sol 开始时明确目标和约束，Terra 完成独立任务的实施、自测、普通失败修复及交付记录后，Sol 集中审查。普通进度不触发逐段检查，真正阻塞才提前回报；修正集中反馈给同一个 Terra。模型与思考档位保持原约定，增量阅读、Agent 复用、单写入者和关键独立验证继续适用，详见根规则与实施计划。

本轮起点 `cccbb26`、`Android` 工作区干净，仅更新流程文档及对应验证，没有创建或调用开发子 Agent、没有修改应用代码。下方 P3-04 用户视觉验收仍待确认，不启动新阶段；下方运行状态属于原窗口记录，本轮未重新验证设备或旧 Agent。当前流程修改的验证和提交推送结果见交付消息。

## P3-04 B4 R2 已完成技术收口，待用户视觉验收（最新，2026-09-14）

本轮严格限定在已授权的 TODAY、ADD chooser 与 CourseEditor 视觉对齐；未改 iOS、Web、共享协议、Room 业务规则、周课表、设置、通知、导入导出或 `main`。当前分支为 `Android`，开始基准为 `9eefec1`；交付提交只包含 `QingKeApp.kt`、`QingKeAppTest.kt`、本轮证据说明和 `docs/Android/evidence/p3-04-visual-r1/` 的 canonical 截图。没有运行中的 Gradle 或其他写入者；中转站唯一 Terra `/root/p3_03_r2_visual_fix` 为 `gpt-5.6-terra/high`，已中断并停止写入，工具不保证永久删除。

- 成功提示不再以两个独立 bottom offset 叠放：`MainShell` 以共享底部栈实际测量为 `ADD → success notice → TerminalTabBar`。notice 出现时 ADD 保持可点击并上移；notice 自动 2.6 秒消失后，ADD 回到 tab bar 上方的常规位置。
- API 37 Compose 回归使用稳定 `terminal-tab-bar` tag 严格验证垂直顺序、ADD 与 notice 的 6–22dp 间距、notice 位于 tab bar 上方、ADD 可点击、深色和 130% 字体以及 2.6 秒自动消失；不再使用任一方向不相交的宽松断言。
- CREATE 资料字段改为全宽单行 BasicTextField placeholder；空值显示“课程名称”／“教师（选填）”，保留 48dp 高度和 divider。130% 回归验证两个 placeholder 横向不越界且为单行，修正了旧版“教师（可选）”在固定 92dp 标签列折成两行的问题。
- 已在生产 APK 重新核对浅色/100% 成功提示：从现有 `ToastProof` 课程直接进入 EDIT 后保存，未聚焦文本输入；`uiautomator` 实测 ADD bottom `1962` < notice top `2015` < tab container top `2110`，`dumpsys input_method` 为 `mInputShown=false`。浅色 CREATE 已在最终代码上重拍并显示“教师（选填）”；深色/130%重新核对 chooser、CREATE 与 APPEND，当前可见区域无裁切/溢出；模拟器已恢复 light / `font_scale=1.0`。
- 最终主机验证：Debug／Release JVM 各 **88 tests、0 failures、0 errors、0 skipped**；`lintDebug` **0 errors、21 warnings**；Debug、Release 和 AndroidTest APK 均构建通过。API 37 ARM64 `emulator-5554` 完整 `connectedDebugAndroidTest --rerun-tasks` 为 **58 tests、0 failures、0 errors、0 skipped**，其中 `QingKeAppTest` 31 tests 全通过。
- 生产 Debug APK 在清数据后的真实入口完成首次学期保存、TODAY ADD→CREATE，随后 force-stop 冷启动恢复 TODAY；ADD 可见，目标 logcat 无 FATAL／ANR。Sol 已核对实际 diff、iOS 当前实现与全部 canonical 截图并完成独立技术复审。这里只能记录“已修正、已测试、已技术审查”；用户视觉验收仍未通过，必须等待用户重新查看确认，不自动开始其他页面或阶段。
- 文档收口验证：Android 文档测试 **66 tests** 全通过，`documentation.test.sh`、`repository-layout.test.sh` 和 `git diff --check` 均通过。

## P3-04 B4 后续终端视觉返工进行中（2026-09-14）

本轮在 `Android` 分支、基准 `3eff01c` 上继续用户已明确授权的 TODAY 与 ADD／CourseEditor 视觉对齐；未修改 iOS、Web、`source/`、共享协议、Room 语义、周课表、设置、通知、导入导出或 `main`。工作区只有当前唯一写入者，未创建或恢复子 Agent。

已实现但尚待最终全量 API 37 回归：CourseEditor 独立完整 terminal backdrop；58dp 文字顶栏（取消、中文／英文双行、黄色保存）；PROFILE chooser 的 `01 / 创建方式 / COURSE DATA` 与 `02 / 已有课程 / REUSE N`；CREATE／EDIT 的圆形六色样本、卡外安排标题、黄色添加安排、仅顶部保存及 EDIT `99`；TODAY 54dp ADD、`QUEUE EMPTY`、行进入提示、无末端色条、居中结束标记；冲突／未保存／删除／错误／颜色终端 dialog。新增 Compose 用例验证这些结构和原有末端色条语义移除。

复审修正已继续实现但尚未提交：覆盖层首层实际绘制带网格和同心圆的 `TerminalBackdrop`，而非仅填充纯色；课程资料及 chooser 新建路径使用 terminal panel；日／起止节次使用紧凑选择控件，周数才保留 stepper；空态正确分离 section 的 `QUEUE EMPTY` 与卡内 `STANDBY`／完整提示；ADD 有内描边和实心折角；颜色可访问语义保留在左侧 accent；dialog 补 status tag、状态点、主题表面和确认类型对应的次要操作。当前定向 API 37 instrumentation 通过；随后必须完整回归后才可提交此修正。

第二轮复审后，紧凑选择控件已改为方角 `DropdownMenu`，可直接选择任意星期或节次且显式继承应用主题；新增用例实际打开菜单并选择非相邻星期。浅色 dialog 文字改用主题前景，危险／放弃操作使用 danger 色；课程行仅保留一根左侧语义色条。本次仍待定向与完整设备回归。

本轮继续收口（未提交）：01 课程资料完整收进同一 panel，plain 输入行／divider、颜色、圆形样本、自定义入口和当前值均在其中；APPEND、chooser、99 危险区补说明 footer。编辑器 backdrop 已覆盖全屏 root，测试验证与 editor 相同 bounds；dialog 使用独立 status tag、状态点、主题文字和明确的冲突／放弃／不可撤销文案。三项 API 37 定向 UI 测试通过；仍待提交后最终全量验证与生产截图。

确认层文案已收紧为 iOS 约定：冲突 `检测到课程冲突`／`冲突会被标记，但仍可保存。`；未保存修改为明确放弃提示；删除为不可撤销和确认删除。该修正及最终验证仍待提交。

## P3-04 B4 视觉返工已完成自动化与生产复拍，待 Sol 最终视觉复审（2026-09-14）

最终代码为 `Android` 分支 `ccfb078d017c1a4346df2f35e3b0a4ab449d9691`，本地与 `origin/Android` 同步、交付前工作区干净。仅修改 P3-04 TODAY／ADD／CourseEditor、对应 AndroidTest 和证据文档；没有扩大到 iOS、Web、共享协议、Room 业务、周表、设置、通知、导入导出或 `main`。最终 production 证据目录为 `docs/Android/evidence/p3-04-visual-r1/`。

最终验证：API 37 ARM64 `emulator-5554`；Debug／Release JVM 各 88 tests、0 failures/errors/skipped；`lintDebug` 0 errors、21 warnings；Debug／Release／AndroidTest APK 通过。最终 `connectedDebugAndroidTest --rerun-tasks` XML 为 **56 tests、0 failures、0 errors、0 skipped**。生产 APK 已实际完成最小学期、空态／有课 TODAY、CREATE、chooser、APPEND、真实 picker、conflict、discard、delete、深色／130% 复拍；目标 FATAL／ANR 检索无匹配，系统已恢复 light / 1.0。准确状态为**已修正、已自动化测试、已生产复拍，等待 Sol 最终视觉复审及用户验收**；不得称为用户已验收或启动下一阶段。

R1 复核发现旧生产图中的 success notice 覆盖右下 ADD，且一张 chooser 证据误采 CREATE。当前代码通过 180dp bottom inset 将 notice 置于 tab/ADD 上方，新增 bounds 不相交测试；浅色真正 chooser 与关闭软键盘的 conflict 已替换，新增深色 chooser 与深色／130% conflict。相关生产截图均在 `docs/Android/evidence/p3-04-visual-r1/`；最终提交后的完整主机／connected 复跑结果以交付记录为准，仍待 Sol 复审和用户验收。

已完成的本机验证：固定 API 37 SDK 下 `clean testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintDebug assembleDebugAndroidTest` 返回成功；Debug／Release JVM 各 **88 tests、0 failures、0 errors、0 skipped**，lint **0 errors、21 warnings**。设备唯一为 `emulator-5554` / API 37 / `arm64-v8a`；新增 TODAY 定向 instrumentation 已通过。此前整类 `QingKeAppTest` 首轮因 5 项旧结构断言失败，已按新结构修正；第二轮仅余 1 项未合并语义树定位，已修正并通过定向测试。此处尚无本轮提交；下一步是 `git diff --check`、提交当前实现／测试／证据／交接并推送 `Android`，然后在相同 API 37 设备执行 `clean connectedDebugAndroidTest`，从 XML 记录完整结果。不得把当前状态写成用户验收或生产人工截图更新。

## 子 Agent 复用规则补充（2026-09-14）

用户授权补充创建前查找和按标识续接规则：同阶段相关任务先查旧 Agent，completed、闲置一两小时或缓存未命中均不单独触发重建。创建后记录所属主会话／服务、标识、范围、模型档位和最后确认状态；无法确认旧写入停止时不启动另一个写入者。具体见 AGENTS.md 与实施计划“子 Agent 查找、复用与重建”。

本轮基准 `fc58089`，`Android` 工作区干净，仅修改流程文档和相关验证，不创建或调用开发子 Agent，不改应用代码。下方官方 Agent 标识仍为历史交接信息，本窗口未查询其实时状态，不将它们认作当前窗口可用 Agent。P3-04 视觉验收未通过和返工范围保持，未启动返工。本次验证及提交推送结果见交付消息。

## 切换至中转站 Sol：P3-04 技术复审已完成，但用户视觉验收未通过（最新，2026-09-14）

用户在查看真实 Android 模拟器后明确反馈：仅就 TODAY 页面和 ADD／课程编辑相关页面而言，仍然存在“问题很大”的视觉或体验差异，
准备切换至中转站 Sol 继续处理。用户本轮没有提供新的逐项问题清单，因此不得猜测具体缺陷、擅自重新设计，或把此前生产长链和
Sol 技术独立复审误写成用户认可。准确状态是：**P3-04 已实施、已完成自动化与生产技术验证、已通过 Sol 技术独立复审；
但用户产品／视觉验收未通过，仍须返工后重新验收。** P3-04 之外的周课表、完整设置、教学日历、通知、导入导出、分享、iOS／Web／
`source/`、共享协议、其他阶段和 `main` 合并仍未授权，不得自动开始。

切换前现场已经核对：分支为 `Android`；本地 `HEAD`、`origin/Android` 和远程 `refs/heads/Android` 均为
`abdb5fc74e1c2a96ace44fcedf16ec20f98af45f`，远程地址为
`ssh://git@ssh.github.com:443/SukiBanQin/QingKeSchedule.git`；记录本节前工作区干净。本次只更新交接及对应文档验证，
最终交接提交编号由 Git 历史和交付消息确认。

用户误操作关闭了 Android 模拟器；切换时 ADB 设备列表为空，没有运行中的模拟器，也没有运行中的 Gradle。此前
`qingke-api37-r3-arm` / `emulator-5554` 的 API 37 ARM64 技术验证和截图证据仍有效，但“模拟器暂不关闭、停在 TODAY”已经是
过时运行状态。中转站若要继续目视复现，须自行重启正确 AVD、确认 `SDK 37`／`arm64-v8a`，安装当前生产 Debug APK，并先以用户
最新反馈为准重新比较 TODAY 与 ADD／编辑页面；不得仅凭旧截图认定已经满足视觉验收。

官方窗口已有 `/root/p3_04_connected_fix` 与 `/root/p3_03_r2_visual`，工具最终状态均为 `completed`，没有运行中或写入中的子 Agent；
当前工具只能确认完成／中断状态，不能承诺永久删除。它们属于官方窗口，不能假定跨服务迁移，也不得在中转站直接使用这些标识。
官方 Sol 在完成本交接的验证、提交和推送后停止文件写入并等待用户明确恢复。

中转站 Sol 的下一项具体工作：

1. 按增量阅读规则核对 `git status --short --branch`、`git log -n 12`、`git ls-remote --heads origin Android`，阅读本节以及
   `docs/Android/evidence/p3-04-course-editor-20260914.txt` 的“官方 Sol 生产 APK 人工长链与最终独立复审”，并检查本次交接后的
   未提交改动；不重复全文读取未变资料。
2. 核对自身子 Agent 工具是否支持明确指定 `gpt-5.6-terra` 和档位。官方子 Agent 不能迁移；有明确且适合委派的 P3-04 返工时，
   中转站只创建一个自己的 `gpt-5.6-terra`／`high` 执行子 Agent，禁止其创建子 Agent。工具不支持时如实报告，不以其他模型冒充。
3. 重新启动正确 API 37 ARM64 模拟器并打开当前生产 APK，先让用户给出或现场确认 TODAY／ADD 页面逐项差异；同时对照 iOS 生产
   App、iOS 代码以及 `docs/Android/evidence/p3-04/` 现有证据，形成可验证的问题清单。未明确的视觉偏好先向用户说明，不自行决定。
4. 只在 P3-04 已授权边界内修正已确认问题，复用同一个 Terra 完成实施和复审反馈；主窗口检查实际 diff，并补跑相称的 JVM、
   lint／构建、API 37 connected、生产入口、浅／深色与 130% 字体截图及 FATAL／ANR 检查。完成后更新证据和本文件、提交并推送
   `Android`，再交由用户重新验收；不得据此启动下一阶段。

## P3-04 已通过 Sol 技术独立复审，待用户验收（最新，2026-09-14）

官方 Sol 已在唯一 API 37 ARM64 `qingke-api37-r3-arm` / `emulator-5554` 上完成生产入口 APK 人工长链，并审查
`80ed71b^..7f9137d` 的实际生产、JVM 和 AndroidTest diff。清数据建学期、TODAY 真实 ADD、两安排新建、完全重复门禁、
保存、force-stop 冷启动恢复、ADD chooser、新建／复用资料追加、课程编辑、预设／自定义颜色、冲突返回修改／仍然保存、
顶部取消／系统返回未保存确认、删除取消／确认均已实际验证。长链后 logcat 精确检索没有目标应用 FATAL／ANR。

- 最终 API 37 ARM64 connected XML：**52 tests、0 failures、0 errors、0 skipped**。
- 最新生产修正后的主机验证：Debug／Release JVM 各 88 tests、0 failures／errors／skipped，`lintDebug` 0 errors、
  21 warnings，Debug／Release APK 与 AndroidTest APK 均构建通过。
- 生产截图已保存到 `docs/Android/evidence/p3-04/`，覆盖浅色、深色、130% 字体、chooser、CREATE、多个安排、
  APPEND、冲突、未保存确认、删除确认及重启恢复。完整说明见
  `docs/Android/evidence/p3-04-course-editor-20260914.txt`。
- Sol 已核对 Room 的来源 index＋打开时 `Course` 指纹精确写入、重复／冲突规则、single-flight、失败／取消恢复、
  Activity 重建和真实 Compose 回调；没有剩余 P3-04 代码阻断项。
- **状态边界**：P3-04 已实施、已自动化验证、已完成生产人工技术验证并通过 Sol 独立复审；用户产品验收尚未完成。
  不得自动开始周课表、设置或其他阶段，不得自动合并 `main`。
- 模拟器已恢复浅色、100% 字体并停在 TODAY，暂不关闭，供用户直接查看。

当前代码基准为 `Android` 分支 `7f9137d8757f57f517112ec092f598f1217fc1af`；本次证据／交接提交编号由最终
交付消息和 Git 历史确认。提交前本地与 `origin/Android` 一致，除本次证据图片与文档外无其他未提交改动。

## P3-04 编辑器取消文字对比度已修正，等待生产收口的先前状态（2026-09-14）

生产 APK 人工检查发现 CREATE／EDIT／APPEND／PROFILE 共用的编辑器 toolbar 中，左侧“取消”按钮在浅色和 130% 字体截图只剩
外框、文字不可见。根因是浅色主题的 `primary` 为 `InverseSurface`，而该 `OutlinedButton` 未指定颜色并默认以 `primary` 绘制，
因而与固定深色 toolbar 同色。本轮只在 `EditorHeader` 的取消按钮显式指定 iOS `textOnInverse` 对应的 `#F1F5F4` 前景；保存
按钮原有黄底深字色配置未改，回调、尺寸和布局不变。

- 新增 Compose 像素回归测试，在浅色／深色及 1.0／1.3 font scale 下分别捕捉取消按钮图像，排除外框区域后验证至少 20 个
  实际浅色文字像素，同时保留显示和 enabled 语义断言；它可捕捉本次“语义有文字但视觉同色”的回归。
- 定向设备测试与最终 `./gradlew connectedDebugAndroidTest --rerun-tasks --no-daemon --console=plain` 均通过；最终 XML 为
  **52 tests、0 failures、0 errors、0 skipped**。`lintDebug` 为 0 errors、21 warnings，`assembleDebugAndroidTest` 与
  `testDebugUnitTest`（88 tests、0 failures、0 errors、0 skipped）及 `git diff --check` 通过。
- 本轮提交编号由交付消息和 Git 历史确认。生产 APK 的其余 CRUD／重启／FATAL-ANR／浅深色和 130% 截图长链仍待 Sol 继续；
  这项自动化修正不构成 Sol 独立复审或用户验收。

## P3-04 connected 测试修正完成，仍待生产硬门槛与 Sol 复审（2026-09-14）

本轮仅修正 P3-04 的 Android instrumentation 测试，生产代码、Room schema／版本、iOS、Web、`source/` 和共享协议均未改动。
起始及提交前代码基准均为 `Android` 分支的 `47f02894fcecf9602aa577dfca7f327cfd8cc298`；本轮提交编号由交付消息和 Git
历史确认。Room 的 `preciseDeleteFailureAndCancellationReopenOriginalData` 现在显式返回 `Unit`，使 JUnit 实际执行整个
测试类；Compose 测试改为在未合并语义树验证 `EDIT / 04` 与最新 success message 的文本节点。编辑器编号／危险区继续滚动至
真实可见位置断言；success message 的重启计时测试移除阻塞 UI 重组的 `SystemClock.sleep`，仍验证替换后的 `B` 在 1.3 秒后
显示、随后自行过期。

- 三个定向 API 37 ARM64 instrumentation 用例均通过。
- 最终 `./gradlew connectedDebugAndroidTest --rerun-tasks --no-daemon --console=plain` 使用唯一
  `qingke-api37-r3-arm` / `emulator-5554`，最终 XML 为 **51 tests、0 failures、0 errors、0 skipped**。
- `./gradlew assembleDebugAndroidTest --no-daemon --console=plain` 与 `git diff --check` 已通过。文档验证和提交／推送结果
  以本轮最终交付记录为准。
- 生产 APK 的真实 CRUD 长链、force-stop 重启、FATAL／ANR 检查及浅色／深色／130% 截图仍未完成；Sol 独立复审和用户验收
  均未完成，不能据此宣称 P3-04 或完整 App 已验收。

下一步由 Sol 核对本轮实际 diff、最终 XML 与提交范围，并仅在现有 P3-04 授权下安排仍缺的生产硬门槛；不得自动扩展阶段。

## P3-04 技术收口的先前状态（2026-09-14）

用户要求中转站 Sol 主窗口停止继续开发并切换回官方 Sol 主窗口。当前已授权任务仍只有 **P3-04（A04／A05）
课程新增、编辑与删除**：真实新增、编辑、删除、复用资料追加安排、多个安排、六个预设色与 Android 自定义颜色、
完全重复门禁、跨课程冲突确认、未保存退出／删除确认、Room 持久化、Activity 重建、生产 TODAY 入口和重启恢复。
合法重复课程 ID 继续按用户确认的“源课程位置＋打开时 `Course` 指纹”精确修改／删除；业务 ID、导入协议和共享
schema 不变，iOS 同步未授权。不得扩大到周课表、完整设置、教学日历、通知、导入导出、分享、iOS／Web／
`source/`、共享协议、P3-04 以外阶段或 `main` 合并。

中转站期间的提交范围为 `80ed71b^..adc366d`，均在 `Android` 分支：

- `80ed71b`：记录 P3-04 授权和重复 ID 决定；`30015f7`：首轮课程 CRUD 实现；
- `8f1bfda`、`ba24478`、`2e67130`、`03631e0`：验证修正、入口／追加只读／颜色／返回／提示和编辑器结构收口；
- `751028e`、`8d29ec2`、`328d239`、`adc366d`：无效／重复／冲突／失败／精确删除、single-flight、Activity
  重建、取消、真实 Back／连续提示与 Room 取消重开测试。

切换前已核对本地 `HEAD`、`origin/Android` 和远程 `refs/heads/Android` 均为
`adc366d6a9991914542069fa9996ac31abd97a84`，远程地址仍为
`ssh://git@ssh.github.com:443/SukiBanQin/QingKeSchedule.git`。记录本节前工作区干净；本次只允许更新交接文档及
对应文档测试，最终交接提交编号由交付消息和 Git 历史确认。没有未提交应用改动，也没有需要抢救或覆盖的文件。

准确完成状态如下：

- **已实施**：P3-04 授权范围的生产代码和 B4 前自动化补测已经提交并推送；TODAY ADD／chooser、CREATE／EDIT／
  APPEND、多个安排、精确重复 ID 写入、冲突确认、退出／删除确认、成功提示与持久化链路均已接通。
- **已验证但不是最终完整设备回归**：Sol 已运行
  `./gradlew clean testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease lintDebug assembleDebugAndroidTest --no-daemon --console=plain`，
  结果为 `BUILD SUCCESSFUL`；Debug／Release JVM 各 88 tests、0 failures／errors／skipped，lint 0 errors、21 warnings，
  Debug、Release unsigned 和 AndroidTest APK 均生成。较早代码基准分别有 API 37 ARM64 connected 42、45、46 tests
  全通过；B3 定向 `scenario.recreate()` 为 3 tests 全通过。这些旧结果不能替代 `adc366d` 的完整 connected 回归。
- **最终 connected 尚未完成**：Sol 在确认 `emulator-5554` 为 SDK 37、`arm64-v8a` 后运行
  `connectedDebugAndroidTest --rerun-tasks`，模拟器在构建期间退出，Gradle 编译 74 个任务后以
  `DeviceException: No connected devices!` 结束；这不是仪器测试断言失败，也不能记为 connected 通过。
- **生产硬门槛尚未完成**：尚缺生产 APK 的清数据建学期→多安排新建→保存→force-stop→重启→ADD chooser→新建／
  复用资料追加→编辑→完全重复门禁→冲突返回修改／仍然保存→顶部取消／系统返回未保存确认→删除取消／确认长链，
  以及 FATAL／ANR 检查和浅色、深色、130% 字体、chooser／新建／多安排／append／冲突／删除截图。
- **审查与验收**：Sol 已完成部分实际 diff 与主机构建核对，但 P3-04 最终技术独立复审尚未关闭；用户已说明会在
  技术收口后亲自测试，因此用户验收也尚未完成。不得宣称 P3、功能页面整体或完整 App 已验收。

切换时没有运行中的 Gradle、模拟器或连接设备，ADB 列表为空。中转站曾有两个标识：
`/root/p3_03_r2_visual_fix` 已完成最后的 B4 提交 `adc366d` 并停止写入；误创建的
`/root/p3_04_course_editor` 已中断，禁止恢复或继续使用。切换前再次查询时中转站工具只返回主窗口本身；确认没有任何运行中的子 Agent。
当前工具不支持重命名或保证永久删除 Agent，不能把“中断”写成“已删除”。中转站主窗口完成
本交接提交和推送后停止写入、等待用户明确恢复。

官方 Sol 主窗口下一步须按增量阅读规则先核对 `git status --short --branch`、`git log -n 12`、本节和
`80ed71b^..adc366d` 实际 diff，不依赖旧聊天中的过时状态，不重复实施已完成内容；同时核对未提交改动，不能只凭
提交号判断现场。然后保持模拟器进程所在工具会话存活，在唯一 API 37 ARM64 AVD 上串行重跑完整
`connectedDebugAndroidTest` 并以最终 XML 汇总为准；核对 `adc366d` 新增 Compose／Room／Activity 测试没有放宽断言，
再完成上述生产长链、日志和截图。发现缺陷时，官方窗口先检查自己的子 Agent 工具；至多使用一个明确
`gpt-5.6-terra`／`high` 的执行子 Agent，并禁止它再创建子 Agent。官方旧 Terra 若仍可访问且适合本任务，先同步
`80ed71b^..adc366d` 变化再复用，否则才按需创建新的；不得尝试直接使用中转站
`/root/p3_03_r2_visual_fix` 或 `/root/p3_04_course_editor` 标识。技术复审完成后再更新证据与交接、运行相关验证、
提交并推送 `Android`；不自动开始任何未授权阶段。

## P3-04 B 协调层补测进行中（最新，2026-09-14）

本轮新增 ViewModel 协调测试，实际覆盖无效／重复不写、冲突冻结候选、保存失败保留、删除取消／成功和重复 ID 陈旧指纹行为；Debug JVM 为 86 tests、0 failures／errors／skipped。B 所要求的 Activity 重建、挂起取消、Room cancellation 及完整 Compose 流程尚未全部关闭，C 生产硬门槛也不在本轮范围；准确状态仍为进行中，不得称 P3-04 完成。

补充 B2：`CompletableDeferred` 单飞用例已验证 append 成功文案、挂起保存双击单写及挂起删除双击单写，Debug JVM 更新为 87 tests、0 failures／errors／skipped。Activity editor 重建、取消传播、Room cancellation 和剩余 Compose 实际交互仍未完成，B 不能标为关闭。

补充 B3：真实 `scenario.recreate()` 已覆盖同一 ViewModel 下的课程 editor 路由、名称／教师、非法颜色输入、颜色对话框、第二安排和确认层保持；定向 API 37 ARM64 connected 为 3 tests、0 failures／errors／skipped。ViewModel 已覆盖保存取消后编辑器／草稿保留且无普通错误，以及删除普通失败保留编辑器和错误发布；Debug JVM 更新为 88 tests、0 failures／errors／skipped。B4 的 Compose Back／连续提示与 Room cancellation/reopen 尚待，B 仍未关闭。

补充 B4：已加入真实系统 Back、顶部保存／危险删除点击、连续成功提示重计时，以及 Room 精确删除普通／取消失败后重开保持数据的自动化用例；AndroidTest APK 编译通过。完整 connected 与双变体 clean XML 尚待本轮最终执行前，不将 B 标记为关闭；C 仍是唯一生产范围外硬门槛。

## P3-04 R3 编辑器结构收口，仍待生产设备硬门槛与 Sol 复审（最新，2026-09-14）

R3 已将编辑器品牌头与 TODAY 分离，正确显示 `PROFILE/CREATE/EDIT/APPEND / 04`，并修正工具栏与品牌头顺序、01／02…／99 编号及起止节次时间文案。对应 Compose 测试已加入并完成 Debug JVM／AndroidTest APK 编译。生产 APK 的真实 CRUD 长链、force-stop 重启、无 FATAL／ANR 记录及要求截图仍未取得，属于未关闭硬门槛；不得称为已设备验证或用户验收。

## P3-04 R2 视觉与提示返工已验证，待 Sol 最终复审（最新，2026-09-14）

R2 修正主壳成功提示在深色面板上的文字可读性，并以 Compose 测试覆盖浅／深、ADD 不被提示阻挡和约 2.6 秒自动消失。编辑器现固定显示品牌头、顶部取消／标题／保存栏及底部 signal line；chooser 采用平衡顶部栏，身份与安排为编号终端分区，编辑模式的删除移至末尾 `99 / 危险操作`，append 不显示危险区。起止节次各自显示对应时间。

R2 API 37 ARM64 connected：46 tests、0 failures／errors／skipped；`testReleaseUnitTest assembleDebug assembleRelease lintDebug` 已取得 `BUILD SUCCESSFUL`，lint 0 errors、21 warnings。仍未完成正式生产 APK 的完整 CRUD 长链、force-stop 记录和要求的截图；该限制必须由 Sol 最终复审保留，不能声称完成用户验收。详见 [P3-04 课程 CRUD 验证证据](evidence/p3-04-course-editor-20260914.txt)。

## P3-04 R1 复审返工已完成自动化验证，待 Sol 最终复审（最新，2026-09-14）

首轮复审列出的 TODAY 浮动 ADD、追加只读、排序来源 index、确认层返回、iOS 六色、可访问语义、深色表单、in-flight 禁用、主壳成功提示及测试覆盖问题均已在 P3-04 原授权范围内返工。新增 `ADD` 不再只出现在空状态；chooser 排序不丢失精确编辑来源；追加身份字段在 ViewModel 和 UI 双层禁止修改；自定义颜色的原始输入保存在 ViewModel，成功提示为约 2.6 秒的非阻塞终端层。没有扩展至周表、设置、通知、传输、iOS/Web/source/schema/main。

R1 API 37 ARM64 connected 已通过 45 tests、0 failures／errors／skipped；Debug／Release JVM、构建、lint、AndroidTest APK 与文档验证须以本轮最终命令记录为准。生产 APK 的完整人工新增→重启→编辑→chooser追加→冲突／删除流程以及浅色／深色／130% 截图尚未完成，必须在 Sol 最终复审中如实保留，不得称为用户验收。详见 [P3-04 课程 CRUD 验证证据](evidence/p3-04-course-editor-20260914.txt)。

## P3-04（A04/A05）课程 CRUD 已实施并完成自动化验证，待 Sol 独立复审（最新，2026-09-14）

`Android` 分支已实现真实课程新建、编辑、删除、复用资料追加安排、多个安排、六个预设色和 `#RRGGBB`／RGB／HSV 自定义色、完全重复门禁、跨课程冲突的“返回修改／仍然保存”、脏草稿退出确认、删除确认、保存失败保留草稿、成功反馈、TODAY 空状态 ADD 与 featured／课程行精确编辑入口。Activity 配置重建继续保留同一 ViewModel 的 editor state。没有进入周课表、完整设置、通知、导入导出、分享、iOS、Web、`source/`、共享 schema、Room schema／版本或 `main`。

重复业务 ID 已按用户确认的“源课程位置＋打开时 `Course` 指纹”精确保存／删除；目标被并发改变时拒绝写入并提示重新打开，因而不会误操作同 ID 的其他课程。实施提交为 `30015f7`；本轮收尾提交编号由交付消息和 Git 历史确认。当前须由 Sol 独立核对实际 diff、测试证据、生产 APK 新增／追加／编辑／冲突／删除／重启及浅色／深色／130% 画面，之后仍待用户亲自验收，不能称为已审查或已验收。

自动化验证已通过：Debug／Release JVM、Debug／Release 构建、`lintDebug`、AndroidTest APK 和 API 37 ARM64 `connectedDebugAndroidTest`（42 tests、0 failures／errors／skipped）。新增点击容器使 Compose 合并了 TODAY 子节点语义，`QingKeAppTest` 的 5 处查询改为未合并树，但原状态、颜色、详情和显示断言均保留。详见 [P3-04 课程 CRUD 验证证据](evidence/p3-04-course-editor-20260914.txt)。生产人工流程与截图尚未作为本轮证据完成，须如实保留此验证边界。

## P3-04 重复 ID 方案确认并授权实施（最新，2026-09-14）

用户已确认 P3-04 对合法重复课程 ID 采用“源课程位置＋打开时数据指纹”精确修改／删除：编辑路由保留
源位置与打开时指纹，提交前验证目标仍匹配，避免误改或误删同 ID 的第一项。业务 ID、版本 1 导入接受
范围和共享 schema 保持不变；Android 可以先修复该疑似 iOS 基准缺陷，iOS 是否同步修复以后另行分析和授权。

用户已授权开始完整 P3-04 实施，对应 A04／A05：真实新增、编辑、删除、复用资料追加安排、多个安排、
六个预设色与 Android 自定义颜色、完全重复门禁、跨课程冲突返回修改／仍然保存、未保存退出和删除确认、
Room 持久化与失败保留、Activity 重建状态、生产 TODAY ADD／featured／课程行入口及重启恢复。用户将在
功能完成并通过技术复审后亲自测试；当前只能记为“已授权实施”，不能记为已实现、已测试、已审查或已验收。

实施明确排除周课表、完整设置、教学日历、通知、导入导出、分享、共享协议扩展、iOS／Web／`source/`
修改、调试后门、无效截图入口、P3-04 以外阶段及 `main` 合并。文档决策基准为
`485263e1910de6a7e4a9a34cc0ef9e13ad7e1c5a`，分支 `Android`，记录前工作区干净且与 `origin/Android`
一致。下一步先提交并推送本次授权记录，再由唯一 `gpt-5.6-terra`／`high` 执行 Agent 实施，禁止其创建
子 Agent；Terra 写入期间 Sol 主 Agent 不修改文件。实施完成后必须由 Sol 独立复审实际 diff、JVM／Room／
API 37 ARM64 证据、生产增改删与重启流程及浅色／深色／130% 截图，问题修正优先复用同一 Agent。

## P3-04 课程 CRUD 分析完成，等待重复 ID 决定与实施授权（最新，2026-09-14）

用户授权 Sol 主 Agent 先分析 P3-04，不授权应用实施。本轮以 `ed8eb246d3a3dc9b2bf36c97b6fea58ac85f86c6` 为干净基准，核对当前 iOS `AppRootView`／`CourseEditorView`／`CourseDraft` 及 Android `CourseDraft`、ViewModel、应用状态和 Room 入口，形成 [P3-04 课程新增、编辑与删除分析](p3-04-course-editor.md)。建议范围为 A04／A05 的真实垂直切片：新建／编辑／删除、复用资料追加安排、预设与自定义颜色、重复门禁、冲突确认、未保存与删除确认、持久化／重启，以及接通后才恢复 TODAY ADD 与课程点击。周课表、完整设置、通知、导入导出和 P3 整体仍排除。

分析发现实施前必须由用户决定的疑似基准缺陷：共享版本 1 合法接受重复课程 ID，TODAY 以来源位置保留全部项目，但 iOS／Android 当前 `saveCourse/deleteCourse` 都按 ID 命中第一项；点击第二个同 ID 课程可能误改／误删第一项。建议 Android 按“源位置＋打开时指纹”精确操作所选课程，保持导出 ID 和导入协议不变，并把 iOS 同步修正留作另行授权；备选是照搬 iOS 的首项行为或检测重复后阻止编辑。用户未决定前不得把建议当作结论，也不得派发 P3-04 实施。

## P3-03-R2 用户视觉验收通过，下一项待定界（最新，2026-09-14）

用户查看本轮 API 37 浅色、深色、130% 字体、完整课程队列和空状态结果后，明确反馈“目前来看没发现什么问题，很完美”，因此 **P3-03-R2 聚焦视觉修正已通过用户视觉验收**。该确认只关闭本轮 Logo、底栏居中和亚克力层次等视觉反馈，不扩大为 A02、A07、A11、整个 P3 或完整 App 验收。

下一项尚未授权实施。结合 P2-03 已完成的 `CourseDraft`、重复安排／跨课程冲突评估以及 P2 的 Room／应用状态保存能力，建议先分析并定界 P3-04 为真实课程新增／编辑／删除垂直切片，对应 A04／A05，并在完整保存、确认、持久化和重启链路接通时再恢复 TODAY／周表的 ADD 与课程点击入口。分析仍须核对当前 iOS `CourseEditorView` 和 `AppRootView`；不得仅添加截图按钮，也不得自动进入周课表、完整设置、通知或导入导出。

## P3-03-R2 聚焦视觉修正通过 Sol 独立复审，待用户视觉验收（2026-09-14）

执行子 Agent 已无损恢复 `qingke-api37-r3-arm`：此前 `-no-window` 父进程快速返回被误判为 AVD 退出，verbose 日志确认 QEMU 继续完整冷启动；`emulator-5554` 已实际确认 SDK 37、`arm64-v8a`、`sys.boot_completed=1`。最终 `connectedDebugAndroidTest` 为 41 tests、0 failures、0 errors、0 skipped。设备 testhost 截图已更新浅／深、130% 字体、固定课程 featured／队列／底栏与三类空状态；130% 目视发现的课程行时间换行已在当前授权视觉范围内以固定单行字号修正并重跑通过。底栏几何测试现在通过非合并语义树验证实际 wrap-content 内容组小于标签宽度且与标签中心相差不超过 1.5px。

本轮代码与设备证据提交为 `5f5bf2b48d763acab15b9ae83b24ff9b8ee5c1e2`，详见 [P3-03-R2 聚焦视觉修正 API 37 设备证据](evidence/p3-03-r2-visual-device-recovery-20260914.txt)。Sol 主 Agent 已独立核对实际 diff、最终 connected XML、设备属性、七张更新截图和 iOS 同状态参考，未发现新的阻断项。测试宿主固定课程仍不是生产入口能力；未加入 ADD、测试入口或课程 CRUD。此状态是“已实现／已设备测试／已通过 Sol 技术独立复审、用户视觉验收待定”，不代表 P3、A02、A07、A11 或完整 App 已验收。

## P3-03-R2 聚焦视觉修正实施（2026-09-14）

本轮执行子 Agent 已在 `Android` 分支完成用户新增视觉反馈范围：`TerminalTabBar` 三个等宽标签的图标／标题／编号组改为水平居中；从 `source/qingke-logo-q-matrix-preview.png` 机械裁除透明留白并输出 1300×500 Android Logo，同步浅色、深色及 night 变体；共享 `terminalPanel` 增加浅／深色半透明表面、左上至右下高光渐变、亮边及方角黑色柔和阴影，并应用于底栏及既有面板使用点。新增 AndroidTest 覆盖标签图标／标题／编号存在和 1300×500 Logo 资源契约，未新增生产 ADD 或测试数据入口。

上一提交 `c79b137` 已提交并推送；前一轮修正提交为 `d5cdca9`，最终测试收口提交为 `89bf383`（均已提交并推送）。最终内容 Row 使用 `fillMaxWidth` 外层居中，测试标签位于实际 wrap-content 内容组并通过 bounds 中心断言；恢复通用 `terminalBorder`，新增独立 `terminalPanelEdge`；实现 standard/elevated 两级面板，底栏使用 elevated，其余面板使用 standard。主机 JVM／lint／AndroidTest APK 与文档测试通过；connectedDebugAndroidTest 因 `qingke-api37-r3-arm` 当前无法启动、无连接设备而未执行。不得宣称用户视觉验收完成。

## 切换中转站 Sol 主窗口，P3-03-R2 用户视觉反馈待定界（最新，2026-09-14）

用户准备从当前官方 Sol 主窗口切换到中转站 Sol 主窗口，继续采用“一个 Sol 主 Agent＋同时至多一个 Terra 执行子 Agent”。
当前窗口已停止派发新任务，不启动 P3-04 或其他阶段；唯一官方子 Agent `/root/p3_03_r2_visual` 已返回 `completed`，最后写入为
`58ad3b1396ad2e9628c1f9f768bd68e14b745f30`，之后没有继续运行或写入。该标识只属于官方窗口，不能在中转站直接复用或假定可迁移。
原人工执行窗口未被删除或自动操作。当前主窗口完成本交接提交和推送后停止开发，等待用户明确恢复。

当前分支为 `Android`。应用实施提交为 `5b18381b22ffe6876868395fce2a446083fe7423`，首轮复审修正为
`58ad3b1396ad2e9628c1f9f768bd68e14b745f30`，Sol 最终复审与证据收口为
`b2947af72c92e7aa7f758e997ac813290aafd229`；本交接文档提交编号由最终交付消息提供。切换前 `b2947af` 的本地 HEAD、
`origin/Android` 和远程 `refs/heads/Android` 一致，工作区干净。

P3-03-R2 的代码、自动化和 Sol 独立复审已经完成，但用户随后对照 Android／iOS 模拟器指出新的视觉差异，尚未接受当前结果，因此准确状态是：
技术独立复审已通过，用户视觉验收未通过，必须保留新反馈并重新定界修正，不能宣称 P3-03、A02、A07、A11、P3 或完整 App 已验收。
已确认的视觉问题如下：

1. Android 底栏每个等宽标签内部的图标／标题组没有水平居中，视觉偏左；iOS `TerminalTabBar` 的内容组居中。
2. Android 直接使用用户指定的 1672×941 原始 Logo，透明画布留白较多，导致同为约 154×54 布局框时可见图案小于 iOS；iOS
   实际 `QingKeLogo` 资源为裁紧的 1300×500。修正应从用户原图机械裁掉透明留白并匹配 iOS 可见框，不改变 Logo 图形。
3. Android `terminalPanel` 当前是平面半透明背景、普通边框和色条，没有 iOS `TerminalAcrylicSurface` 的
   `.ultraThinMaterial`／对角高光 wash、亮色 `panelEdge` 与黑色柔和 shadow；空状态、featured、课程行和底栏因此缺少反光与层次。
   Android 可用稳定的半透明底色、对角高光渐变、亮边与方角阴影做跨版本拟态；不要求照搬 Apple 私有材质或字体。
4. Android 没有 ADD 是既有范围决定，不是遗漏图标：生产课程新增／编辑尚未实现。不得为了截图添加无效按钮或测试数据入口；真正
   ADD 必须与课程新增、多个安排、保存、重复门禁、冲突确认、持久化和返回确认一起在另行授权的功能任务中接通。

下一项建议先由接手 Sol 按增量阅读核对上述现场，形成聚焦的 **P3-03-R2 视觉修正**范围：底栏内容居中、Logo 可见尺寸、共享
亚克力表面／高光／边缘／阴影及浅色、深色、130% 字体、空状态和固定课程截图对照；仍排除 ADD、课程 CRUD、周课表、完整设置和
P3-04。用户本轮要求先分析，尚未明确授权开始该代码修正；接手窗口须先向用户确认实施授权，不能自动委派或开发。ADD 应在视觉壳
确认后另行分析对应真实课程功能。

最近已完成的正式验证见 [P3-03-R2 最终独立复审证据](evidence/p3-03-r2-final-review-20260914.txt)：Debug／Release JVM 各
79 tests、0 failures／errors／skipped；`lintDebug` 0 errors、20 warnings；正确 API 37 ARM64 connected 39 tests、0 failures／
errors／skipped；文档测试、`documentation.test.sh`、`repository-layout.test.sh` 和 `git diff --check` 通过。环境为
`ANDROID_HOME=ANDROID_SDK_ROOT=/Users/takagisan/Library/Android/sdk-qingke-api37`、
`ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd`、AVD `qingke-api37-r3-arm`。交接前已用 `adb emu kill` 正常关闭
`emulator-5554`，ADB 列表为空，没有运行中的 Gradle 或模拟器；应用和本轮视觉反馈没有产生未提交代码。

中转站 Sol 必须先检查自身子 Agent 工具是否能明确指定 `gpt-5.6-terra`、思考档位、后续复用和停止操作；不得直接使用官方
`/root/p3_03_r2_visual` 标识。不支持指定模型或工具不可用时如实报告，不以普通聊天窗口或其他模型冒充。只有用户明确授权上述
聚焦实施后，才创建一个 Terra 执行子 Agent，沿用既定 **Terra／高**档位；同时最多一个，并在任务中禁止它继续创建子 Agent。

## P3-03-R2 通过最终独立复审，等待用户视觉验收（最新，2026-09-14）

本轮在 `Android` 分支从 `10b92535216b87de2f808e3d5ec392aa6af18321` 实施已授权的 P3-03-R2；开始时
工作区干净且与 `origin/Android` 一致。应用代码仅改 Android Manifest、资源、`QingKeApp.kt` 和相关
Compose AndroidTest；没有修改 iOS、Web、`source/`、领域／持久化／共享协议或构建工具链，也没有进入 P3-04。

首轮实施提交为 `5b18381b22ffe6876868395fce2a446083fe7423`，Sol 独立复审尚未通过，实际指出：夜间／130% Logo
不可读、adaptive foreground 自引用导致默认机器人、双重下拉手势风险、序号未旋转且完整队列缺图、iOS 参考未进入
TODAY。本轮只修正这五项，并未开始 P3-04。Android 现与 `origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001` 的 `TerminalBackdrop`、品牌头、
三段 hero、活动条、featured／序列、空状态和 `TerminalTabBar` 对齐；保留 R1 已验证的颜色回退、状态、
下拉门禁、生命周期 tick、Activity／ViewModel 所有权及唯一 occurrence tag。`source/cover.png` 已以独立
`qingke_cover` drawable 作为 launcher/adaptive foreground 来源，Logo 为用户指定
`source/qingke-logo-q-matrix-preview.png` 的 Android 资源副本，并新增符合 Android night qualifier 的机械派生浅色前景
版本（保留青色和透明度）；应用名为“青课”。生产手写下拉已删除，只保留 `PullToRefreshBox`，并在启动协程前同步门禁；
课程行索引按 iOS 方向旋转。没有放置不可用的 ADD、编辑或详情入口。

已测试：修正后的 clean Debug／Release JVM 各 79 tests／8 XML、0 failures／errors／skipped；`lintDebug` 为 0 errors、20 warnings，
Debug、Release unsigned 与 AndroidTest APK 均已生成。正确 API 37 ARM64 `connectedDebugAndroidTest` 为 39 tests／1 XML、
0 failures／errors／skipped；夜间 Logo、资源契约、唯一标准下拉和旋转队列截图都由该轮覆盖。iOS 独立 `fc3ddfb8` 的 fixture
导入 XCTest 为 1 test／0 failures，并已在实际 Debug App 确认导入后进入 `today-tab`、保存 TODAY 图。所有截图、命令、
限制、iOS 参考和 Android 平台差异见 [P3-03-R2 视觉对齐证据](evidence/p3-03-r2-visual-alignment-20260914.txt)。

关键限制必须如实保留：P3-03 尚未授权课程录入／导入，生产入口只能验证 TODAY 空状态；固定课程、当前 featured、
完整序列、深浅／130% 与三类空状态由 API 37 connected 测试宿主截图覆盖，文件名均标注 `testhost`，不能当作
生产课程数据。iOS 旧 onboarding 参考不再作为 TODAY 证据；最新 `ios-fc3ddfb8-today-imported.png` 是实际 fixture 导入确认后的
TODAY。Sol／高已独立核对 `10b9253..58ad3b1` 实际 diff、iOS 源码与同状态截图，并用正确 API 37 ARM64
`emulator-5554` 带 `--rerun-tasks` 重跑：主机 145 个任务全部执行，双变体 JVM 各 79 项，设备 74 个任务全部执行、
connected XML 39 项，失败／错误／跳过均为 0。启动器应用抽屉的同一静态帧已清楚显示 cover 图标和“青课”标签；生产 APK
切换三标签、force-stop 后重启正常且无 FATAL／ANR。完整复审见
[P3-03-R2 最终独立复审证据](evidence/p3-03-r2-final-review-20260914.txt)。

**结论：P3-03-R2 已实施、测试并通过最终独立复审，当前只等待用户视觉验收。** `emulator-5554` 暂时保留在生产 TODAY
空状态供用户查看；这不代表 A02、A07、A11、P3 或完整 App 已验收，不得自动开始 P3-04。

## Sol 主 Agent／Terra 执行子 Agent 流程启用（2026-09-14）

用户授权当前窗口作为 Sol 主 Agent：用户主要与主 Agent 对话，主 Agent 负责分析、定界、协调、核对实际 diff 和审查，不直接修改应用代码；用户明确授权的文档更新及其验证除外。已授权且适合委派的应用实施可由主 Agent 自行调用至多一个执行子 Agent，无需用户逐次复制提示词，但不能据此开始未授权阶段。

当前工具可在创建时明确指定 `gpt-5.6-terra` 和思考档位，并可用同一任务标识发送后续任务以复用 Agent；常规默认 `medium`，任务已有明确约定时保留。新 Agent 使用必要任务上下文，不默认继承完整聊天，并在派发中禁止其再创建子 Agent。工具可以中断 Agent 当前轮次且 Agent 之后仍可复用，但没有永久删除或保证释放 Agent 的能力；所有停止、关闭、释放或删除表述以工具返回为准。子 Agent 工具也不能读取或修改主 Agent 的实际模型和用户级全局设置，因此当前窗口的 Sol 选择由用户界面负责，本轮不改全局配置。

共享工作区只有一个写入者：Terra 实施时主 Agent 不同时修改文件。Terra 回报实际提交范围、测试证据和限制，主 Agent 复核实际 diff；关键改动保留独立审查，产品验收仍由用户确认。同一任务的实施、修正和复审反馈优先复用同一 Terra；独立任务结束且关联较少时，可以先保存交接并中断旧 Agent 的活动轮次，再按需新建。原人工执行窗口保留为备用，不删除或自动操作；切换官方／中转站前先保存交接、停止原写入，不假定子 Agent 会话跨服务迁移。

本轮起点 `b730e01ebc6408a3fb4d896292a87f81d66060c4`，分支 `Android`、工作区开始时干净；只更新流程文档和文档验证，不创建子 Agent，不实施 P3-03-R2，不修改应用代码，也不进入 P3-04。先观察后续任务的总消耗、返工、重复阅读和交接次数，不承诺固定节省额度。

## 增量阅读规则更新（2026-09-14）

用户授权先解决重复全文阅读，本轮不引入子 Agent。同一窗口已读未变内容直接复用；接手、切换服务或压缩后按缺失信息补读最新状态和相关差异，具体见根目录 AGENTS.md 的“增量阅读”和实施计划“阅读与接手续接”。旧提示词中的全量阅读清单不再作为每轮固定前置要求，实际审查和验收要求保持。

本轮起点 `97e79cf`，分支 `Android`、工作区干净；只更新阅读规则、模板及对应文档验证，不修改应用代码。下方 P3-03-R2 的任务范围、执行建议及待验收状态保持，本轮未实施或复审 R2。本次验证、提交和远程同步结果见交付消息，接手仍核对现场。

## P3-03-R2 视觉对齐分析完成，等待执行（最新，2026-09-13）

用户在 API 37 模拟器中查看 P3-03-R1 生产 App 后，确认今日页与当前 iOS App 仍有明显视觉差距，决定
P3-03 暂不验收、先完成 P3-03-R2；这不否定 R1 已通过的功能、生命周期、颜色和测试结论。分析窗口以
Android `ef0ed3a0cfe9bbac1b3e164e959b8f773012afac`、`origin/IOS`
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`、用户提供的 iOS 今日页截图及实际 API 37 画面完成
[P3-03-R2 今日页与共享主壳视觉对齐分析](p3-03-r2-visual-alignment.md)。本轮只修改文档和文档测试，
没有修改应用代码，也没有进入 P3-04。

用户指定 `source/cover.png` 为 App 图标、`source/qingke-logo-q-matrix-preview.png` 为页面 Logo；除
Android 系统栏、返回方式、合法可用字体和平台渲染等不可避免差异外，Android 应以当前 iOS 源码和运行画面
为权威基准，不重新设计。R2 必须现在对齐共享网格／圆弧背景、主题 token、品牌头、三段日期 hero、活动条、
featured／序列卡片和带图标、标题、`01/02/03`、独立深色选中块及黄色短线的底部标签栏，同时修正 launcher
图标和应用名“青课”。

课程新增／编辑尚未实现，因此 R2 仍不得放置无法工作的 `ADD`、编辑箭头或课程点击；这些控件在对应功能
接通时按 iOS 原位出现。执行窗口应先运行干净 `IOS` `fc3ddfb8` App 并取得同状态参考，再在 API 37 做浅色、
深色、130% 字体／窄屏、当前课程、完整序列、三类空状态、三标签和 launcher 的成对截图及自动化回归。
建议执行窗口 **Terra／高**，因为涉及共享视觉组件、资源、字体／图标替代和多轮截图校准；完成后必须交回
**Sol／高**独立复审并再次由用户人工确认。不得自动开始 P3-04。

## P3-03-R1 最终独立复审通过，等待用户确认（最新，2026-09-13）

分析审查窗口已独立复审 Android
`19a04f37a87e55b078e0a4425028044948a2b62f..d7e31dae414c9f5cb6b47aaa0ee35c9c6bd7d590` 的实际
12 文件 diff、`origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`、原始 JVM／lint／API 37 XML、
六张设备截图及真实生产入口记录；没有发现阻断问题，没有修改应用代码，也没有进入 P3-04。

课程颜色现在只接受严格 `#RRGGBB`，非法值回退 `QingKeCyan`；生产行实际使用解析后的色条，同时以
`COMPLETE/CURRENT/NEXT/UPCOMING` 文字标签表达状态。API 37 用例覆盖四项顺序、重复业务 ID 的唯一
`OccurrenceKey` tag、当前／下一门 featured、全结束无 featured、三类空状态、详情回退、深浅主题、底部标签、
有课／空状态真实下拉门禁及反馈，以及 STARTED tick、停止、恢复立即刷新和 Activity 重建后的同一 ViewModel。

审查窗口以 `--rerun-tasks` 独立执行 Debug／Release JVM、lint、AndroidTest APK 和唯一正确 API 37 ARM64
connected 测试：主机 100 个任务通过，JVM 两变体各 79 tests 且 0 failures／errors／skipped，lint 为 0 errors、
15 warnings；正确 `target=android-37` AVD 的启动参数含 `-enable-hvf`，唯一 `emulator-5584` 实测
`boot_completed=1`、SDK 37、`arm64-v8a`，connected XML 为 36 tests、0 failures、0 errors、0 skipped。
文档 55 项、通用文档测试、布局测试和 `git diff --check` 均通过；设备已正常关闭。完整记录见
[P3-03-R1 最终独立复审证据](evidence/p3-03-r1-final-review-20260913.txt)。

准确状态是：P3-03 已实现、测试并通过最终独立复审，但尚未获得用户对 P3-03 或 A02／A07 的验收；P3 和
完整 App 仍未完成。当前没有已授权的 P3-04 实施任务，不自动生成或开始下一阶段。

## P3-03-R1 已实施并完成 API 37 验证，等待最终独立复审（最新，2026-09-13）

执行窗口只在 P3-03-R1 授权范围内修正 TODAY 课程颜色：仅接受 `#RRGGBB`，非法值回退
`QingKeCyan`，并以文字状态标签保留不依赖颜色的可读信息。测试还补齐真实下拉回调／重复门禁／反馈结束、
STARTED 每秒 tick／停止／恢复／Activity 重建的时间所有权，以及完整 TODAY API 37 矩阵：四项目顺序与
`COMPLETE/CURRENT/NEXT/UPCOMING`、重复业务 ID 的唯一 tag、featured 当前／下一门／全结束无 featured、三类空状态、
详情回退、颜色、深浅主题、底部标签。新增内容没有进入 P3-04、课程编辑、周课表、完整设置、通知或导入导出。

此前“API 37 ARM64 模拟器阻断”结论已更正：四次失败错误地启动了
`/Users/takagisan/.android/avd/qingke-api37-r3-arm.ini`（`target=android-0`），不是指定的正确副本
`/Users/takagisan/.android/qingke-api37-r3-avd/qingke-api37-r3-arm.ini`（`target=android-37`）。因此旧 TCG、
HVF、shell 和 SIGSEGV 记录只保留为错误 AVD 的历史，不能证明正确 API 37 环境阻断。以显式
`ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_AVD_HOME` 启动正确副本后，日志确认 API 37、`-enable-hvf`、
唯一 `emulator-5584`、boot completed、SDK 37 和 `arm64-v8a`；完整更正、启动命令、原始 XML、截图和生产入口
结果见 [P3-03-R1 API 37 AVD 路径更正与恢复证据](evidence/p3-03-r1-api37-blocker-20260913.txt)。

最终 clean 验证的 Debug／Release JVM XML 各为 79 tests、0 failures、0 errors、0 skipped；`lintDebug` 为
0 errors、15 warnings，Debug／Release APK 与 AndroidTest APK 均完成。正确 API 37 ARM64 的
`connectedDebugAndroidTest` 原始 XML 为 36 tests、0 failures、0 errors、0 skipped（装配 2、Room 11、
DataStore 8、Activity 重建 2、Compose 13）。真实 Debug APK 已验证：保存学期进入 TODAY 空状态、空状态实际
下拉出现刷新指示器、force-stop 重启仍进入 TODAY；相同过程 logcat 无 `FATAL EXCEPTION`／`ANR`。

当前基准为 `647a8dca449023f4d89bf01de38c4fa0e037bfd7`，分支 `Android`，尚需将本轮未提交变更的最终提交
推送至 `origin/Android` 后，交给 **Sol／高** 做 P3-03-R1 最终独立复审。不得将这些验证称为 P3-03、A02、A07、P3、
完整 App 或用户验收完成，也不得自动开始 P3-04。

## P3-03 独立复审未通过，等待 P3-03-R1 修正（最新，2026-09-13）

分析审查窗口已只读复审 Android
`1426661035d386bce0146d3eb048a72f0abd197a..60d776a3c315a4b43fec1c52a7d6e64bcd489e87` 的实际
11 文件 diff、最新 `origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`、Debug／Release JVM XML、
API 37 connectedDebugAndroidTest XML 和 P3-03 证据。实施提交的父提交、文件范围、远端同步和测试数量与执行
交接一致；复审期间没有修改应用代码，也没有进入 P3-04。

已确认秒级生产语义与 iOS 基准一致：开始时刻进入 `ONGOING`、结束前一秒仍为 `ONGOING`、结束时刻进入
`FINISHED`，共享 fixture 的进度为 2812／3788 秒和 `63:08`。`ScheduleViewModel.refreshCurrentTime()` 只更新
内存时钟，根 Compose 使用 `repeatOnLifecycle(STARTED)`，列表生产键和 tag 使用
`OccurrenceKey(courseIndex, scheduleIndex)`；代码范围没有越界接入课程编辑、周表、设置、通知或导入导出。

复审仍发现三个阻断项：

1. 今日课程序列没有读取或解析 `course.color`，`CourseRow` 始终使用主题 `surfaceVariant`，因此合法
   `#RRGGBB` 和非法颜色显示相同，也没有实现非法颜色回退青色的契约。
2. 本轮唯一新增 Compose 用例只检查静态页面、课程数、featured 和 `63:08`，最后反而断言刷新回调为 0；
   没有执行有课／空状态真实下拉、刷新门禁及反馈结束，也没有验证 STARTED tick 在离开前台后停止、返回前台
   立即刷新和 Activity 级 ViewModel 复用。其名称声称覆盖重复业务 ID 稳定 tag，但没有逐项断言四个
   `today-course-{courseIndex}-{scheduleIndex}` 节点、顺序或状态。
3. 没有提交任何 `p3-03-*.png`；现有证据只有摘要文本，未满足固定数据浅色／深色和 130% 字体或窄屏截图，
   也没有记录真实生产入口对今日空状态下拉刷新及强停重启的完整步骤和结果。下一门 featured、全部结束时
   无 featured、三类空状态、详情回退、课程颜色与深浅主题也缺少 API 37 回归。

原始 API 37 XML 确为 31 tests、0 failures、0 errors、0 skipped，Debug／Release JVM XML 各 78 tests 且新增
秒级和 ViewModel 用例真实进入测试体；这些结果有效，但不能覆盖上述缺口。完整复审记录见
[P3-03 独立复审证据](evidence/p3-03-review-20260913.txt)。下一步只能由执行窗口完成 **P3-03-R1**：修复课程
颜色及回退，补齐生命周期、真实下拉、完整今日 UI 矩阵、截图和生产入口证据，再交回分析窗口最终复审。
建议执行窗口使用 **Terra／高**。不得宣称 P3-03、A02、A07、P3、完整 App 或用户验收完成，不得进入 P3-04。

## P3-03 今日页与秒级时钟已实施，等待独立复审（最新，2026-09-13）

执行基准为 `1426661035d386bce0146d3eb048a72f0abd197a`，分支 `Android`。本轮将
`TodaySchedulePresentation` 接入 TODAY 标签，按 `origin/IOS` `fc3ddfb8` 的秒级语义更新状态：开始时刻进入
ONGOING、结束时刻进入 FINISHED，进行中进度提供秒数、分钟派生与 `m:ss` 倒计时。`ScheduleViewModel` 持有可观察
`LocalDateTime`，`refreshCurrentTime()` 只更新内存时间；根 Compose 以生命周期 STARTED 边界立即刷新并每秒更新。

最终 API 37 ARM64 connectedDebugAndroidTest XML 为 31 tests、0 failures、0 errors、0 skipped：装配 2、Room 11、
DataStore 8、Compose 10（QingKeAppTest 9、Activity 重建 1）。Debug／Release JVM XML 各 78 tests、0 failures、0 errors、
0 skipped；lintDebug 为 0 errors、15 warnings。完整范围、命令和限制见
[P3-03 API 37 证据](evidence/p3-03-api37-20260913.txt)。本轮未实现课程编辑、周课表、设置、通知、导入导出或 P3-04。

P3-03 仍须 Sol／高独立复审；不得据此宣称 P3-03、A02、A07、P3、完整 App 或用户验收完成。

## 用户确认 P3-02，P3-03 今日页与实时刷新分析完成待实施（最新，2026-09-13）

用户已确认 P3-02 的实现、测试和最终独立复审结果。准确状态为：P3-02 已实现、测试、通过最终独立复审
并获用户确认，但这只关闭应用壳、状态加载与首次学期设置任务，不表示 A01、A06、A11、P3 或完整 App
已经完成。

用户随后授权准备 P3-03。分析窗口以 Android `db1077f4c7103b548644b484346b34588469bd4e` 为基准，
重新获取并核对 `origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`，完成
[P3-03 今日课表页面与实时刷新分析](p3-03-today-schedule.md)。Android 分支内保留的 iOS 文件不是
当前 iOS 基准；远端 iOS 已含 `c3191ae` 的秒级倒计时、前台每秒刷新、回前台立即刷新和下拉刷新时间语义。
P3-03 必须先把 Android P3-01 的整分钟进度和“结束分钟仍进行中”修正为当前 iOS 的秒级、结束时刻即
`FINISHED`，再将真实今日页接入主壳；P3-01 的排序、下一门、教学日历及重复 ID 规则保持不变。

P3-03 只实现 A02 今日展示和 A07 对今日展示的既有影响，包括日期 hero、featured 当前／下一门、完整
课程序列、空状态、进度倒计时、生命周期 tick、下拉刷新、深浅主题、稳定内部键及 API 37 视觉／交互证据。
课程新增／编辑／删除、课程点击、周课表、完整设置、通知和导入导出均排除；页面不放置当前无法工作的
ADD 或编辑入口。建议执行窗口 **Terra／高**，实施后必须独立复审。本轮分析只修改 Android 文档和文档
测试，没有修改应用代码；实际窗口模型和档位未核实，不自动进入 P3-04。

## P3-02-R3 重建测试证据已修正，等待最终独立复审（最新，2026-09-13）

执行基准为 `61db3890a889ae0de3cd59c1a5e5c192e826e4af`，分支 `Android`。本轮只修改
`Android/app/src/androidTest/java/com/qingke/schedule/ui/P3R2ActivityRecreationTest.kt`、本交接和
[P3-02-R3 API 37 证据](evidence/p3-02-r3-api37-20260913.txt)；没有修改任何 `src/main` 生产代码、
`QingKeAppTest.kt`、ViewModel、ScheduleAppState、SemesterDraft、Room、DataStore、JSON、领域规则、iOS、Web 或共享
schema／fixtures，也没有进入 P3-03。

本轮修复 R2 的测试证据缺口：每次 `ActivityScenario.recreate()` 后，均在 `scenario.onActivity` 的**新 Activity**
上用 `ViewModelProvider(activity, factory)` 重新取得 `ScheduleViewModel`，并以 `assertSame` 验证它与重建前原实例
为同一对象。随后重新 `setContent`、读取状态、保存、选择标签和断言均使用这个重建后重新取得的变量，不再把旧变量
直接传入新 Activity。第一次重建确认名称“重建保留”、节次展开和首节 `07:20` 均保留；保存并选择设置标签后第二次
重建同样重新取实例，设置标签仍选中。两次都断言工厂创建次数为 1。

最终 API 37 ARM64 `qingke-api37-r3-arm`／`emulator-5584`（SDK 37、ABI `arm64-v8a`）
`connectedDebugAndroidTest` XML 为 30 tests、0 failures、0 errors、0 skipped：装配 2、Room 11、DataStore 8、
Compose 9（`QingKeAppTest` 8、`P3R2ActivityRecreationTest` 1，后者方法
`activityRecreationKeepsActivityViewModelDraftAndSelectedTab` 已实际执行）。最终 clean 主机验证 Debug／Release JVM XML
各 76 tests、0 failures、0 errors、0 skipped，`ScheduleViewModelTest` 每变体 10 项；lintDebug 为 0 errors、15 warnings，
Debug／Release APK 与 AndroidTest APK 均成功。文档／布局／差异检查也已通过；本轮启动的模拟器已正常关闭。

P3-02-R3 只修正独立复审所指的测试所有权证据，仍须分析审查窗口最终独立复审；不得宣称 P3-02、A01、A06、A11、P3、
完整 App 或用户验收完成，也不得自动进入 P3-03。

## P3-02-R2 最后设备测试已实施，等待最终独立复审（最新，2026-09-13）

执行基准为 `11d9d9520528f9fb74ce252ed696c29a8b8cf5e0`，分支 `Android`。本轮只修改
`Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt`、新增
`P3R2ActivityRecreationTest.kt`、本交接和 [P3-02-R2 API 37 证据](evidence/p3-02-r2-api37-20260913.txt)；
没有修改任何 `src/main` 生产代码、ViewModel、ScheduleAppState、SemesterDraft、Room、DataStore、JSON、领域规则、
iOS、Web 或共享 schema／fixtures，也没有进入 P3-03。

新增设备测试以真实 `DatePickerDialog`／`TimePickerDialog` 控件选择和确认不同日期、开始／结束时间，取消不改变值；
真实点击添加第 11 节与删除中间节次，并验证重新编号、1／20 边界和“删除第 1 节”语义。另以 `ComponentActivity` 的
`ViewModelStore` 建立可控假仓库，先重建保留首次设置草稿名称、展开状态和节次时间，再保存、选择设置标签并第二次重建；
工厂只创建一次 ViewModel，未重新注入静态草稿。触控测试直接测量十个关键节点的 bounds 至少 48dp，并核对三个标签
content description 与选中语义。

最终 API 37 ARM64 `qingke-api37-r3-arm`／`emulator-5584`（SDK 37、ABI `arm64-v8a`）的
connectedDebugAndroidTest XML 为 30 tests、0 failures、0 errors、0 skipped：装配 2、Room 11、DataStore 8、
Compose 9（`QingKeAppTest` 8、Activity 重建 1）。最终 clean 主机验证 Debug／Release JVM XML 各 76 tests、
0 failures、0 errors、0 skipped，lintDebug 为 0 errors、15 warnings，APK 与 AndroidTest APK 均成功。已有 R1 真实生产
持久化路径和稳定截图继续有效；本轮结束后模拟器已正常关闭。

P3-02-R2 仅表示测试与证据补齐，仍须分析审查窗口独立复审；不得宣称 P3-02、A01、A06、A11、P3、完整 App 或用户验收
完成，也不得自动进入 P3-03。

## P3-02-R1 UI 复审缺口已实施，等待最终独立复审（最新，2026-09-13）

执行基准为 `c68c23390c3ba54e3a0b28b1e8a456354676364f`，分支 `Android`。本轮只在 P3-02-R1 授权范围内修改
`ui/QingKeApp.kt`、`ScheduleViewModelTest.kt`、`QingKeAppTest.kt` 和本交接／证据；没有修改
`ScheduleAppState`、`SemesterDraft`、Room、DataStore、协议、P3-01 展示模型、领域规则、iOS、Web 或共享 schema／fixtures，
没有进入 P3-03、今日／周内容、课程编辑、完整设置、提醒或导入导出。

界面新增单一、无状态的 `QingKeAppContent` 渲染边界；生产 `QingKeApp(viewModel)` 仍是唯一生命周期感知收集入口，
没有第二套业务状态。它使 API 37 Compose 测试可真实覆盖 `NOT_LOADED/LOADING`、失败重试、首次设置、主壳、
`READY + needsOnboarding + form=null` 加载兜底、保存失败弹窗和三个标签。首次设置改为方正终端控件、黄色强调、
深色标签栏和竖向节次操作行；删除语义包含节次编号。系统栏按顶部实际表面切换图标明暗，浅色主壳截图中状态栏图标
可读，边缘内容使用系统栏／导航栏 inset。

ViewModel JVM 测试从每变体 4 项增至 10 项：包括挂起初始读取的重试门禁及完成后的再试、默认十节及唯一 ID、
周数／节次数边界与连续编号、完整保存快照、挂起保存单写入、失败保留草稿与关闭错误、取消不发布普通错误、标签状态。
最终 clean 验证 Debug／Release JVM XML 各 76 tests、0 failures、0 errors、0 skipped；`ScheduleViewModelTest`
每变体 10 项。API 37 ARM64 `qingke-api37-r3-arm`（`emulator-5584`，SDK 37，`arm64-v8a`）最终 XML 为 26 tests、
0 failures、0 errors、0 skipped：装配 2、Room 11、DataStore 8、Compose 5。此前 P3-02 使用 espresso 3.5.0／3.6.1
失败、升级到固定 3.7.0 后通过的历史保留在 [原 API 37 证据](evidence/p3-02-app-shell-api37-20260911.txt)；R1 的测试调整期
曾有 3 项屏外 Compose 断言失败，最终重跑全绿，详见 [P3-02-R1 证据](evidence/p3-02-r1-api37-20260913.txt)。

真实生产路径已重新验证：清除应用数据、冷启动进入首次设置、保存默认合法学期进入“今日（壳层）”、force-stop 后冷启动
仍直接进入主壳，未发现该应用 FATAL EXCEPTION 或 ANR。稳定截图及 UI hierarchy 见该证据和 `evidence/p3-02-r1-*.png`。
P3-02-R1 仍须分析审查窗口独立复审；这不代表 P3-02、A01、A06、A11、P3、完整 App 或用户验收完成，也不得自动进入 P3-03。

## P3-02 应用壳与首次设置已实施，等待独立复审（最新，2026-09-11）

执行基准为 `7c6b38bfb6a80eb7ee49a7472b6276b62265b920`。本轮在 P3-02 授权范围内新增 Activity 级
`ScheduleViewModel`、Compose 根路由、首次学期表单、三标签壳及最小主题；没有接入真实今日／周内容、课程编辑、
完整设置、通知、导入导出或 P3-03。`espresso-core` 测试专用依赖固定为 3.7.0：此前 3.5.0 与 3.6.1 在 API 37
反射调用已移除的 `InputManager.getInstance()` 失败，3.7.0 改用系统服务后最终设备测试通过。

最终 clean 构建为 `BUILD SUCCESSFUL in 53s`，API 37 ARM64 `emulator-5584` 的 connectedDebugAndroidTest XML 为
22 tests、0 failures、0 errors、0 skipped（装配 2、Room 11、DataStore 8、Compose 1）。清数据冷启动显示首次设置默认
18 周，保存后显示主壳；force-stop 冷启动仍显示主壳，证明 Application、ScheduleAppState 与 Room 路径连通。完整记录见
[P3-02 API 37 证据](evidence/p3-02-app-shell-api37-20260911.txt)。

P3-02 仍须独立复审，不能据此宣称 P3-02、A01、A06、A11、P3、完整 App 或用户验收完成，不得自动进入 P3-03。

## 用户确认 P3-01，P3-02 应用壳与首次设置分析完成待实施（最新，2026-09-11）

用户已确认 P3-01 的实现、测试和最终独立复审结果。准确状态为：P3-01 已实现、测试、审查并获用户确认，
但它只完成纯 Kotlin 今日／周表展示模型，不表示 A02、A03、A07、P3 或完整 App 已完成。

用户随后授权继续下一项。分析窗口以 `0e994e01f9f1fb29be1ad167fd4a3f9bf70b876e` 为 Android 基准，
核对当前 `MainActivity`、`ScheduleAppState`、生产依赖、`SemesterDraft`、测试入口和最新 `origin/IOS`
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`，将 P3-02 划为“应用壳、状态加载与首次学期设置”。详细
状态路由、表单、主题、测试标识、API 37 冷启动／重启证据和排除项见
[P3-02 分析](p3-02-app-shell-onboarding.md)。本轮只维护 Android 文档和文档测试，没有修改应用代码。

P3-02 只做 Activity 级 ViewModel、加载／失败／首次设置／主壳四态、可保存的首次学期表单、三标签主壳
及最小主题。今日／周课表内容、课程编辑、完整设置、导入导出、通知和后续阶段均排除。建议执行窗口
Terra／高：边界已经明确，但这是 Compose、生命周期、应用状态和 Room 的第一个真实垂直切片；实施后
必须独立复审。当前不得宣称 P3-02、A01、A06、A11、P3 或完整 App 完成。

## P3-01-R2 通过，P3-01 独立复审关闭（最新，2026-09-11）

分析审查窗口已独立复审
`4aae401181dd4ef563d087dcaea89eb4d00fe8e3..8eaf32aed98c814cadae2ea6a78f4c834c3b8268`
的实际 4 文件 diff，并结合此前对原实施提交 `37851bd283029b1218de561fa633ccad33b40286` 和 R1
`fd06b752cdcbc6925491e1742474d9c8360cf15f` 的审查完成 P3-01 累计收口。本轮只有 JVM 测试、交接、证据和
文档验证变更，没有任何 `src/main` 生产代码或越界修改。

`matrixUsesOccurrenceKeyToOrderSameIntervalSources` 的三项均映射为同一显示日的 `startRow=0`、`endRow=0`，
输入来源键为 `(2,1)`、`(1,9)`、`(1,3)`，输出严格为 `(1,3)`、`(1,9)`、`(2,1)`；该构造真实进入
`courseIndex` 和 `scheduleIndex` 两级比较。较小来源键依次取得 lane 0／1／2，三项 `laneCount=3` 且矩阵 ID
唯一；移除来源键比较会保留逆序输入并使断言失败。结合 R1 已验证的重复业务 ID、今日／周调课一致性、
逆序 `zh_CN` 名称排序和缺失节次退化，P3-01 分析契约中的已知证据缺口均已关闭，未发现生产实现缺陷。

分析窗口使用 API 37 SDK 独立执行完整 clean 构建，`BUILD SUCCESSFUL in 42s`，145 actionable tasks
（141 executed、4 up-to-date）；Debug／Release JVM XML 各 66 tests、0 failures、0 errors、0 skipped，
`SchedulePresentationTest` 每变体 10 项且 R2 新用例在两个 XML 中出现；lint 为 0 errors、13 warnings，
Debug／Release APK 和 AndroidTest APK 均成功。文档测试 50 项及文档／布局／差异检查通过。本任务为纯
Kotlin，不要求或运行 `connectedDebugAndroidTest`。完整结果见
[P3-01 最终独立复审证据](evidence/p3-01-final-review-20260911.txt)。

准确状态为：P3-01 已实现、测试并通过独立复审，但尚未获用户验收；这不等于 A02、A03、A07、P3 或完整
App 完成。用户没有授权 P3-02 或页面实现，本次复审不得自动进入后续任务。

## P3-01-R2 矩阵稳定来源键测试与证据已实施，等待最终独立复审（最新，2026-09-11）

执行基准为 `4aae401181dd4ef563d087dcaea89eb4d00fe8e3`，分支 `Android`。本轮仅修改
`SchedulePresentationTest.kt`，补充本交接、[P3-01-R2 JVM 证据](evidence/p3-01-r2-jvm-20260911.txt) 和对应文档
验证；没有修改任何 `src/main` 生产代码、MainActivity、Application、Compose、ViewModel、导航、状态、持久化、
构建依赖、iOS、Web 或共享 schema／fixtures，未进入 P3-02 或页面实现。

新增 `matrixUsesOccurrenceKeyToOrderSameIntervalSources` 以同一显示日和完全相同 `startRow=0`、`endRow=0` 的三项
发生项，按逆序输入 `OccurrenceKey(2,1)`、`OccurrenceKey(1,9)`、`OccurrenceKey(1,3)`，明确同时触发
`courseIndex` 与 `scheduleIndex` 两级比较。矩阵最终严格输出 `(1,3)`、`(1,9)`、`(2,1)`，lane 为 0／1／2，
三项 laneCount 均为 3，项目 ID 全部唯一；移除来源键比较会保留逆序输入，从而使该断言失败。此前 R1 的所有
测试与断言均保留，未声称 R1 已覆盖这个此前尚未进入的比较器分支。

指定 API 37 SDK 的最终 clean 构建为 `BUILD SUCCESSFUL in 42s`（145 actionable tasks：142 executed、3 up-to-date）。
Debug／Release JVM XML 各 66 tests、0 failures、0 errors、0 skipped，`SchedulePresentationTest` 每变体 10 项且新增
稳定键测试进入两个变体测试体；`lintDebug` 为 0 errors、13 warnings，Debug／Release APK 和 AndroidTest APK 均编译
成功。文档测试 50 项、文档／布局／差异检查均通过。本轮纯 Kotlin，按授权未启动模拟器或运行
`connectedDebugAndroidTest`。完整命令、XML 和限制见上述证据。

P3-01-R2 的测试与证据已实施，仍需分析审查窗口最终独立复审；这不代表 P3-01、A02、A03、A07、P3、完整 App
或用户验收完成，不得自动进入 P3-02 或后续阶段。

## P3-01-R1 最终复审保留一项稳定键缺口，待 P3-01-R2（最新，2026-09-11）

分析审查窗口已独立核对
`e980238d4c2bc8d5fd2d7bcf063c1f9eaaad0cf7..fd06b752cdcbc6925491e1742474d9c8360cf15f`
的实际 4 文件 diff、四个新增测试、Debug／Release XML 和 P3-01-R1 证据。提交范围符合测试专项目标，
没有修改任何 `src/main` 生产代码，也没有进入 P3-02。重复 `course.id`／`schedule.id` 的发生项保留和
冲突判定、同一调课日期的今日／周来源星期一致性、逆序名称的 `zh_CN` 排序，以及缺失开始／结束节次的
`UPCOMING`／无进度退化均建立了真实前提，原复审对应缺口已关闭。

P3-01-R1 仍不能关闭 P3-01 独立审查：新增矩阵用例虽然打乱输入，但四项的 `startRow` 分别为 0、1、2、5，
排序全部由开始行决定，没有任何两项同时具有相同 `startRow` 和 `endRow`，因此没有进入生产比较器最后的
`OccurrenceKey(courseIndex, scheduleIndex)` 分支。当前断言能证明矩阵会按行排序和稳定计算 lane，但不能支持
证据中“乱序矩阵项目按稳定键排序”的表述。P3-01-R2 只需增加至少两个相同开始／结束行、来源键逆序输入的
项目，断言按 `courseIndex`、`scheduleIndex` 分配稳定顺序和 lane；不得修改生产代码。如果这个最小回归失败，
应停止并回传实际失败，另行决定生产修正。

本轮使用 API 37 SDK 独立 clean 构建，`BUILD SUCCESSFUL in 40s`，145 actionable tasks（141 executed、
4 up-to-date）；Debug／Release JVM XML 各 65 tests、0 failures、0 errors、0 skipped，
`SchedulePresentationTest` 每变体 9 项且四个 R1 用例均进入测试体；lint 为 0 errors、13 warnings，
Debug／Release APK 和 AndroidTest APK 均成功。文档测试 48 项及文档／布局／差异检查通过。任务仍为纯
Kotlin，不需要或运行 `connectedDebugAndroidTest`。完整记录见
[P3-01-R1 最终复审证据](evidence/p3-01-r1-review-20260911.txt)。

准确状态为：P3-01 生产实现未发现阻断缺陷，P3-01-R1 已关闭三类原证据缺口，但 P3-01 尚未独立复审通过或
获用户验收。不得宣称 P3-01、A02、A03、A07、P3 或完整 App 完成；P3-01-R2 前不得进入 P3-02 或页面实现。

## P3-01-R1 测试与证据已补齐，等待最终独立复审（最新，2026-09-11）

执行基准为 `e980238d4c2bc8d5fd2d7bcf063c1f9eaaad0cf7`，分支 `Android`。本轮仅修改
`SchedulePresentationTest.kt`，补充交接与 [P3-01-R1 JVM 证据](evidence/p3-01-r1-jvm-20260911.txt)；没有修改
任何 `src/main` 生产代码、MainActivity、Application、Compose、ViewModel、导航、主题、ScheduleAppState、Room、
DataStore、草稿、校验器、构建依赖、iOS、Web 或共享 schema／fixtures，未进入 P3-02 或页面实现。

新增 JVM 回归直接构造两个不同来源位置但相同 `course.id`／重复 `schedule.id` 的课程，确认两个发生项及各自
`OccurrenceKey` 均保留且同业务课程 ID 不误标冲突；另以不同 `course.id` 与相同 `schedule.id` 的重叠项确认
发生项不丢失、两个内部键均标记冲突。相同真实调课日期 `2026-09-05` 同时调用今日和周模型，二者均按来源周一
取课，周模型仍显示周六列且不显示周六来源课程。逆序课程和乱序矩阵输入锁定 `zh_CN` 名称排序、稳定来源键顺序、
闭区间 lane／laneCount 与分离分量 lane 复用；缺失开始或结束节次均安全退化为 `UPCOMING` 且没有
`timingProgress`，不抛异常。

指定 API 37 SDK 的最终 clean 构建为 `BUILD SUCCESSFUL in 53s`（145 actionable tasks：142 executed、3 up-to-date）。
Debug／Release JVM XML 各 65 tests、0 failures、0 errors、0 skipped，`SchedulePresentationTest` 每变体 9 项，新增
四项均进入测试体；`lintDebug` 为 0 errors、13 warnings，Debug／Release APK 和 AndroidTest APK 均编译成功。
文档测试 48 项、文档／布局／差异检查均通过。本轮是纯 Kotlin 测试补强，按授权未启动模拟器或运行
`connectedDebugAndroidTest`。完整命令、XML 和限制见上述证据。

P3-01-R1 的测试与证据已实施，仍需分析审查窗口最终独立复审；这不代表 P3-01、A02、A03、A07、P3、完整 App
或用户验收完成，不得自动进入 P3-02 或后续阶段。

## P3-01 独立复审未通过，待 P3-01-R1 补齐关键回归（最新，2026-09-11）

分析审查窗口已独立核对实施基准
`93e1005263871c25a87cd8d8dae4a8f822dbb52b` 至实施提交
`37851bd283029b1218de561fa633ccad33b40286` 的实际 7 文件 diff、iOS
`SchedulePresentation.swift`／教学日历基准、共享 fixture、JVM XML 和实施证据。实际范围符合纯 Kotlin
边界，没有修改 MainActivity、Compose、状态／持久化、iOS、Web、共享 schema／fixtures 或构建依赖；静态
检查未发现需要修改生产代码的语义缺陷。`OccurrenceKey` 使用课程／安排来源下标保留重复业务 ID，调课按
来源星期取课且保留实际显示列，冲突只比较当周显示日真实出现且业务课程 ID 不同的项目，闭区间矩阵分量
和 lane 复用实现也与既定契约一致。

本轮仍不能通过 P3-01 审查，因为实施证据对关键测试覆盖有两项实质性高估，并有一项稳定性门槛未被有效
锁定：现有重复用例只在同一个课程内复制相同 `schedule.id`，没有构造两个来源课程位置但相同
`course.id` 的发生项；调课用例只让 `WeekSchedulePresentation` 在周六取周一课程，没有让
`TodaySchedulePresentation` 在调课日取来源星期课程；今日名称排序和矩阵稳定顺序的断言所用输入本身已经
处于期望顺序，删除对应排序逻辑仍可能通过。另应补上无对应节次时课程状态／进行中进度安全退化的直接
断言，避免只由代码阅读代替回归证据。以上是测试与证据缺口，不是已确认的生产缺陷；P3-01-R1 应只修改
JVM 测试及本交接／证据，不修改生产代码，除非新增的最小复现确实失败且另行回传审查。

分析窗口使用 API 37 SDK 独立执行完整 clean 构建，`BUILD SUCCESSFUL in 53s`，145 actionable tasks
（141 executed、4 up-to-date）；Debug／Release JVM XML 各 61 tests，0 failures、0 errors、0 skipped，
`ScheduleRulesTest` 每变体 7 项、`SchedulePresentationTest` 每变体 5 项；lint、Debug／Release APK 和
AndroidTest APK 均成功。文档测试 46 项、文档／布局／差异检查均通过。P3-01 是纯 Kotlin，本轮不要求
或运行 `connectedDebugAndroidTest`。完整独立记录见
[P3-01 复审证据](evidence/p3-01-review-20260911.txt)。

准确状态为：P3-01 已实现并通过现有测试，但尚未独立复审通过，也未获用户验收。不得据此宣称 P3-01、
A02、A03、A07、P3 或完整 App 完成；P3-01-R1 关闭前不得自动进入 P3-02 或页面实现。

## P3-01 今日与周课表展示模型已实施，等待独立复审（最新，2026-09-10）

执行基准为 `93e1005263871c25a87cd8d8dae4a8f822dbb52b`，分支 `Android`。本次仅扩展纯 Kotlin
`ScheduleRules`、新增 `presentation/SchedulePresentation.kt` 及对应 JVM 测试，并维护本交接、证据和文档
验证；未修改 MainActivity、Application／依赖装配、Compose、ViewModel、导航、主题、`ScheduleAppState`、
Room、DataStore、草稿、校验器、通知、导入导出、iOS、Web、共享 schema/fixtures 或构建依赖，未进入 P3-02。

`CourseOccurrence` 使用课程和安排的稳定来源位置作为 `OccurrenceKey`，不假设业务 ID 唯一。`ScheduleRules`
新增指定教学周日期、指定周发生项、HH:mm 分钟解析和开始／结束分钟均为 `ONGOING` 的课程状态。展示层以
显式 `LocalDateTime` 计算今日、周和矩阵模型：停课→调课→周末→正常星期优先，调课保持显示列但按来源星期
取课；今日排序／下一门／进行中进度、周冲突、跨节矩阵 lane、午休间隔与文字辅助均为可测试纯 Kotlin 规则。

使用指定 API 37 SDK 的最终 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
assembleDebugAndroidTest` 为 `BUILD SUCCESSFUL in 53s`（145 actionable tasks：142 executed、3 up-to-date）。
最终 Debug／Release JVM XML 均为 61 tests、0 failures、0 errors、0 skipped，其中
`ScheduleRulesTest` 为 7 项、`SchedulePresentationTest` 为 5 项。`lintDebug` 为 0 errors、11 warnings；
`assembleDebugAndroidTest` 成功。文档测试 46 项及文档／布局／差异检查均通过。P3-01 是纯 Kotlin，按授权未运行
`connectedDebugAndroidTest`、未启动模拟器。

完整结果见 [P3-01 JVM 证据](evidence/p3-01-schedule-presentation-jvm-20260910.txt)。本次仅表示 P3-01 已实施
和测试通过，仍须分析审查窗口独立复审实际 diff、共享 fixture 用例和 XML；不代表 P3-01、A02、A03、A07、
P3、完整 App 或用户验收完成，也不得自动进入 P3-02。

## 用户确认 P2，P3-01 展示模型分析完成待 Terra 实施（最新，2026-09-10）

用户已明确接受 P2-04 当前分析角色同窗口复审及其组织性独立限制，并确认 P2 阶段结果。准确状态为：
P2-01、P2-02、P2-03、P2-04 的授权存储、状态、草稿和生产装配范围已实现、测试并完成各自记录的审查，
P2 获用户确认；这不表示 A01—A11 页面／端到端流程或完整 App 已实现、验证或验收。D01、D03 和正式
发行范围仍未决定。

用户同时授权继续下一项 P3 子任务，但没有授权一次性实施完整 P3。分析窗口在基准
`322227ef29bf1eab08ec74ecf0eb66b19c79b77c` 上核对 Android 实际缺口和最新
`origin/IOS` `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`，将下一项划为 P3-01“今日与周课表展示模型”，
详见 [P3-01 分析](p3-01-schedule-presentation.md)。本轮只维护 Android 文档和文档测试，没有修改应用代码。

P3-01 先以纯 Kotlin 固定 A02／A03 及 A07 的展示规则：本地日期时间、课程发生与状态、停课／调课、
今日排序和下一门、周次限制、周一至周日、真实冲突、矩阵跨节与 lane、午休分隔及稳定显示文字。内部
发生键必须保留 P1 已接受的重复业务 ID，不能用 `course.id`／`schedule.id` 去重。任务不包含
`MainActivity`、ViewModel、Compose、导航、页面、通知、导入导出或持久化修改。

建议执行窗口使用 Terra／高：范围和 iOS 基准清楚，适合常规实现，但组合边界与矩阵布局需要较多推理。
实施后必须独立复审；当前不宣称 P3-01、A02、A03、A07、P3 或完整 App 完成，也不自动开始后续页面任务。

## P2-04 通过当前分析角色复审，组织性独立限制保留（最新，2026-09-10）

用户指定由当前分析审查角色复审实施基准
`205831819ff1343b5f736ea011e6817f9b7e5b55` 至实施提交
`e6a3513d720fe39f3b3f10aca1acc55208d9d820`。本轮没有修改应用代码。实际 13 文件 diff 符合
[P2-04 契约](p2-04-application-state-composition.md)：联合加载没有部分发布，普通失败可区分来源并恢复
旧快照，两个读取点的取消均恢复完整前态并传播；课表和偏好写入共用串行边界，成功只发布各仓库返回的
已提交快照，没有声称 Room／DataStore 跨存储原子。

manifest、自定义 Application、懒加载单例依赖容器、固定 `schedule.db` 与既有 DataStore 路径均符合
生产装配要求；未修改 `MainActivity`、ViewModel、Compose、通知、导入导出、iOS、Web、共享协议、
Room schema 或 DataStore 键，未进入 P3。复审没有发现阻断问题，完整代码结论见
[P2-04 复审](p2-04-review.md)。

本轮从干净构建重跑成功，Debug／Release JVM 各 55 项，0 failures／errors／skipped；API 37 ARM64
唯一 `emulator-5584` 上实际运行 21 项（装配 2、Room 11、DataStore 8），0 failures／errors／skipped，
新增装配测试均进入测试体且 JVM 签名为 `void`。复审 lint 为 0 errors、13 个既有类别 warnings；设备已
正常关闭、adb 为空。完整记录见
[同窗口复审证据](evidence/p2-04-same-window-review-20260910.txt)。

由于 P2-04 的实施和本轮复审由同一 Codex 任务完成，本结论准确称为“当前分析角色同窗口复审通过”，
不能冒充另一窗口或另一审查者完成的组织性独立审查。P2-04 和 P2 整体尚未获用户验收；是否接受本轮
作为 P2-04 审查门槛由用户决定。不得据此宣称 A01—A11、P2、P3 或完整 App 完成，也不自动进入 P3。

## P2-04 应用状态与生产依赖装配已实施，等待独立复审（最新，2026-09-10）

用户已明确授权当前 Sol／高窗口直接执行 P2-04。实施基准为
`205831819ff1343b5f736ea011e6817f9b7e5b55`，分支 `Android`。本次扩展
`ScheduleAppState` 联合管理课表和偏好，两个读取均成功后才一次发布 `READY`；普通失败不部分发布，
取消恢复完整前态并传播。课表和偏好写操作共用串行边界，偏好只发布 DataStore 返回的已提交快照，
没有声称 Room／DataStore 跨存储原子。

新增 manifest 注册的 `QingKeScheduleApplication` 和懒加载进程单例 `ScheduleAppDependencies`；默认
生产实现用 `applicationContext` 创建 `schedule.db`、`RoomScheduleRepository` 及既有
`schedule_preferences.preferences_pb` 的 DataStore 仓库。测试工厂可指定文件并关闭两种存储，已在
API 37 ARM64 上证明保存课表和规范化偏好后关闭重建可恢复。

最终 clean 构建成功，Debug／Release JVM 各 55 项且无失败／错误／跳过，`ScheduleAppStateTest`
各 21 项；`lintDebug` 0 errors、11 个既有类别 warnings，AndroidTest APK 成功。最终
`emulator-5584` 为 ADB `device`、boot 1、SDK 37、ABI `arm64-v8a`，`connectedDebugAndroidTest`
实际运行 21 项（装配 2、Room 11、DataStore 8），0 failures、0 errors、0 skipped。过程中的一次
未设置 SDK 快速编译和一次模拟器提前退出均已如实记录，最终模拟器已关闭、adb 为空。完整证据见
[P2-04 验证证据](evidence/p2-04-application-state-connected-debug-android-test-20260910.txt)。

实际范围未修改 `MainActivity`、ViewModel、Compose 页面、通知、导入导出、iOS、Web、共享
schema／fixtures、Room schema 或 DataStore 键，也没有进入 P3。P2-04 尚未独立审查或用户验收；
不得据此宣称 P2、A01—A11 或完整 App 完成。下一步只交给分析审查窗口核对实际 diff、联合状态语义、
生产单例及 API 37 XML，不自动实施 P3。

## P2-04 应用状态与生产依赖装配分析完成，等待实施授权（最新，2026-09-10）

用户授权分析窗口在 P2-03 获确认后检查 P2 收口缺口。分析基准为
`9f7bf013a64c42ddaf7966987542728ce6f91df1`，分支 `Android`；开始时本地 HEAD、
`refs/heads/Android` 与 `origin/Android` 一致，工作区干净。本轮只读取 Android／iOS 实际源码，
维护 Android 分析、交接文档和文档测试，没有修改任何应用代码，也没有进入 P3。
重新获取后的 `origin/IOS` 为 `fc3ddfb8ffa14b205a591ffdbed5632d5f975001`；其新增当前时间刷新
不改变本次依赖装配结论，并留在 P3 范围。

核对确认 Android 的 Room、DataStore 和纯 Kotlin 草稿分别存在，但尚无生产 Room 构建入口、
Application 级唯一依赖容器，也没有把偏好接入 `ScheduleAppState`。`MainActivity` 仍只显示“轻课”。
因此 P2 还需要一个可独立验证的 P2-04“应用状态与生产依赖装配”，具体联合加载、失败／取消回滚、
写入串行、生产单例和 API 37 关闭重建要求见
[P2-04 分析](p2-04-application-state-composition.md)。

P2-04 只连接存储与状态：不修改 `MainActivity`，不实现 ViewModel、Compose 页面、导航、通知、
导入导出或后续阶段。实施尚未获单独授权；建议执行窗口 Sol／高，因为它同时涉及进程级依赖生命周期、
两种异步存储和状态取消／部分失败语义。实施后必须独立复审。P2、A01—A11 和完整 App 均未据此完成，
不得自动进入 P3。

## P2-03 通过最终独立复审并获用户确认（最新，2026-09-10）

分析审查窗口已完整复审 `ee2decac5849f7047f40d8b8638586b9d810ef83..5c5c08a5772b1c3792406ee2fc5aa6d0eefff9b8`。
首次实施、重复安排 ID 删除修正、测试矩阵补强及最终测试构造均符合
[P2-03 分析](p2-03-form-drafts.md)，没有遗留阻断问题。最终独立主机构建成功，Debug／Release JVM
各 48 项且无失败／错误／跳过；完整范围、XML、lint 动态警告差异和纯 Kotlin 无设备门槛说明见
[P2-03 最终复审证据](evidence/p2-03-final-review-20260910.txt)。

用户已明确确认 P2-03 本子任务结果。准确状态为 P2-03 已实现、已测试、已独立复审并获用户确认；
这不等于 A04、A05、A06 的页面／端到端流程已验收，也不等于 P2 或完整 App 完成。P2-01、P2-02
虽已实现并通过独立复审，但尚无用户对整个 P2 的验收决定。下一步应先分析 P2 是否还需要应用装配／
状态接线任务，再由用户决定后续授权；不得自动进入 P3。

## P2-03-R3 测试构造修正已实施，等待最终独立复审（最新，2026-09-10）

执行基准为 `37b53e6d870748cfecf826852133af6143558195`，分支 `Android`。本次只修改
`DraftTest.kt`、本交接和 [P2-03-R3 JVM 证据](evidence/p2-03-r3-jvm-20260910.txt)；未修改任何生产代码、
`ScheduleRulesTest`、构建依赖、Room、DataStore、`ScheduleAppState`、页面、iOS、Web 或共享 schema/fixtures，
也没有进入 P3。

`validationPrecedesDuplicateAndCrossCourseConflict` 不再用无效的第 99 节破坏冲突前提：候选现在有空名称、两项
合法且完全重复的安排，并与另一门课程的合法同星期／同节次／共同周安排真实冲突。测试先断言重复特征和
`ScheduleRules.conflicts` 均真实成立，再断言 `evaluateSave` 只返回含课程名称错误的 `Invalid`，不返回重复
消息或 `Conflicting`。新增同课程、相同星期／节次／周次／规范化教室但 `EVERY` 与 `ODD` 不同的有效安排，
确认 `evaluateSave(..., emptyList())` 返回 `Ready`；既有不同教室分支保留。

使用指定 API 37 SDK 的最终 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
assembleDebugAndroidTest` 为 `BUILD SUCCESSFUL in 43s`（145 actionable tasks：143 executed、2 up-to-date）。
最终 Debug／Release JVM XML 均为 48 tests、0 failures、0 errors、0 skipped；`DraftTest` 均为 12 项。
`lintDebug` 为 0 errors、10 warnings；`assembleDebugAndroidTest` 成功。文档测试 40 项、
`documentation.test.sh`、`repository-layout.test.sh` 和 `git diff --check` 均通过。本轮为纯 Kotlin 测试构造
修正，按授权未启动模拟器、未运行 `connectedDebugAndroidTest`。

本记录只表示 P2-03-R3 已实施和测试通过，仍须交回分析审查窗口最终独立复审；不代表 P2-03、A04、A05、
A06、P2、完整 App 或用户验收完成，且不得自动进入 P3。

## P2-03-R2 JVM 测试与证据补强已实施，等待最终独立复审（最新，2026-09-10）

执行基准为 `01b2965151aa8f3bf89748ae3abcdcd88e06374b`，分支 `Android`。本次只修改
`DraftTest.kt`、`ScheduleRulesTest.kt`、本交接和
[P2-03-R2 JVM 证据](evidence/p2-03-r2-jvm-20260910.txt)，没有修改任何生产代码、构建依赖、Room、
DataStore、`ScheduleAppState`、MainActivity、Compose、iOS、Web 或共享 schema/fixtures，也没有进入 P3。

课程草稿测试补齐了完整新建默认字段与注入 ID、编辑保留 ID／顺序／字段、仅外围空白不 dirty、追加默认
安排、重复安排的新建／追加／编辑三种入口、历史重复计数、规范化教室／重复规则区别，以及基础校验优先于
重复和跨课程冲突。保留 R1 的重复 ID 只删除首项、缺失 ID 和最后一项保护。冲突测试补齐星期、闭区间
节次端点、无相交节次／周次、EVERY 与 ODD／EVEN、ODD 与 EVEN、共同周升序、稳定输入顺序、自身排除及
完整引用。学期草稿测试逐项覆盖 6 月 30 日／7 月 1 日边界、十节完整时间、编辑、去除名称空白、编号、
最少一节、空列表与常规新增，以及超 20 节、倒序和相邻重叠的 `ScheduleValidator` 错误。

使用指定 API 37 SDK 的最终 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
assembleDebugAndroidTest` 为 `BUILD SUCCESSFUL in 55s`（145 actionable tasks：143 executed、2 up-to-date）。
最终 Debug／Release JVM XML 均为 47 tests、0 failures、0 errors、0 skipped；其中 `DraftTest` 为 11 项，
`ScheduleRulesTest` 为 6 项。`lintDebug` 为 0 errors、13 warnings（既有依赖版本、未用资源和 application icon
提示）；`assembleDebugAndroidTest` 成功。文档测试 40 项、`documentation.test.sh`、`repository-layout.test.sh`
和 `git diff --check` 均通过。本轮纯 Kotlin 测试补强，按授权未启动模拟器、未运行 `connectedDebugAndroidTest`。

本记录仅表示 P2-03-R2 已实施和测试通过，仍须交回分析审查窗口最终独立复审；不代表 P2-03、A04、A05、
A06、P2、完整 App 或用户验收完成，且不得自动进入 P3。

## P2-03-R1 重复安排 ID 删除修正已实施，等待独立复审（最新，2026-09-10）

基准 `25ef0b38ba72570ba56bba169c7289528a548665`。`CourseDraft.removeSchedule` 现在仅删除稳定的首个匹配项；
单项、缺失 ID 和删除后最后一项均不改变列表。没有新增 ID 唯一性约束，保留历史重复安排 ID。新增 JVM
回归覆盖两个相同 ID、缺失 ID 与最后一项保护；既有草稿、重复、冲突和学期测试保留。未修改其他生产代码或进入 P3。

P2-03-R1 尚未独立审查或用户验收，不代表 P2-03、A04、A05、A06、P2 或完整 App 完成。
## P2-03 课程／学期表单草稿与保存评估已实施，等待独立复审（最新，2026-09-10）

执行基准为 `ee2decac5849f7047f40d8b8638586b9d810ef83`，分支 `Android`；开始时 HEAD、
`refs/heads/Android`、`origin/Android` 与远端 `refs/heads/Android` 一致，工作区干净。仅新增纯 Kotlin
的 `draft` 草稿／评估代码，扩展 `ScheduleRules` 冲突计算，并补充 JVM 测试；未修改 `ScheduleData`、
`ScheduleValidator`、构建依赖、Room、DataStore、`ScheduleAppState`、MainActivity、Compose、iOS、Web 或
共享 schema/fixtures，未进入通知、导入导出、P3 或其他 P2 子任务。

`CourseDraft`／`CourseScheduleDraft` 支持可注入日期和 ID，建立、编辑、追加、复制、删除（至少保留一项）、
规范化转换与 dirty 比较。保存评估严格先校验、再检查新增完全重复安排、最后计算跨课程冲突；历史重复
未增加仍可编辑，冲突结果稳定保留候选／已有课程及安排和升序共同周。`SemesterDraft`／`PeriodDraft`
使用 `LocalDate`／`LocalTime`，实现季节名称、18 周、十节默认时间、编辑、连续编号和 10／45 分钟新增规则，
并复用既有 `ScheduleValidator`。

最终干净构建与双变体 JVM 结果、AndroidTest APK 编译、lint 和文档检查见
[P2-03 JVM 证据](evidence/p2-03-form-drafts-jvm-20260910.txt)。P2-03 为纯 Kotlin 能力，本轮不运行
`connectedDebugAndroidTest`；AndroidTest APK 已编译。P2-03 尚未独立审查或用户验收，不代表 A04、A05、A06、P2 或完整 App 完成；下一步只交回分析审查窗口复审，不得自动进入 P3。

## P2-03 表单草稿与保存评估分析完成，等待实施授权（最新，2026-09-10）

用户授权本分析窗口收口 P2-02-R1 独立复审状态，并为下一项 P2 子任务划定边界。
当前分支 `Android`，分析基准、HEAD、`refs/heads/Android` 和 `origin/Android` 均为
`2ba11ec8ec2fe95b9e34a210e4597f52ee05805a`，开始工作区干净。本轮只修改 Android
分析／交接文档和文档测试，没有修改应用代码、iOS、Web 或共享协议／fixtures。

下一项建议为 P2-03“课程／学期表单草稿与保存评估”，详见
[P2-03 分析](p2-03-form-drafts.md)。实施只建立纯 Kotlin 草稿、可注入日期／ID、复用现有校验器，
并补足星期／节次／周次／单双周的冲突评估。同课程新增完全重复安排必须阻止，历史
重复未增加时仍可保存。学期草稿保持 iOS 的季节名称、18 周、默认十节、增删节次和
连续编号行为。

P2-03 不实现 Compose 页面、冲突／放弃／删除确认对话框，不调用仓库、不持久化草稿、
不改 Room／DataStore／`ScheduleAppState`，不进入 P3、通知或导入导出。实施尚未获用户授权；
授权后建议执行窗口 Terra／高，因范围清晰但重复兼容和冲突组合需要较多推理。实施后必须
由分析窗口独立复审。

## P2-02-R1 聚焦修正通过独立复审（最新，2026-09-10）

分析审查窗口已复审 `8ae63295ca16cb22f5e233ef27f5e17d943991f3..2ba11ec8ec2fe95b9e34a210e4597f52ee05805a`，
实际 diff 仅为 `SchedulePreferences.kt`、DataStore AndroidTest、执行交接／证据和文档测试五个文件。
无效提前量现在保留 `remindersEnabled`，只回退提前量与自定义标记，与 iOS 及 Android 原始
DataStore 读取路径一致。公开 `save(-1)`、`update(181)`、`load` 和关闭重建测试证明外观、
教学日历和提醒开关不被意外改变，未发现新的阻断问题。

本窗口独立使用指定 API 37 SDK 完成 clean 双变体构建、JVM 测试、lint 和 AndroidTest APK
编译：145 个任务成功，Debug／Release JVM 各 34 项且无失败，`lintDebug` 0 errors。
独立启动 API 37 ARM64 `emulator-5584` 后，最终提交状态的 `connectedDebugAndroidTest`
实际运行 19 项（Room 11、DataStore 8），0 failures、0 errors、0 skipped，新用例进入测试体。
设备已正常关闭，adb 为空。完整结果见 [P2-02-R1 复审证据](evidence/p2-02-r1-review-20260910.txt)。

**结论：P2-02-R1 聚焦修正通过独立复审，P2-02 已知实施阻断项关闭。** 这不等于
P2-02 用户验收、A09、P2 或完整 App 完成；也不自动授权 P2-03 或 P3。

## P2-02-R1 提醒规范化修正已实施，等待独立复审（最新，2026-09-10）

执行基准为 `8ae63295ca16cb22f5e233ef27f5e17d943991f3`，分支 `Android`；开始时 HEAD、
`refs/heads/Android`、`origin/Android` 与远端 `refs/heads/Android` 均为该基准，工作区干净。
本次仅修改 `ReminderPreferences.normalized()`：提前量不在 0—180 时保留既有
`remindersEnabled`，仅将提前量回退为 10、`usesCustomLeadTime` 回退为 `false`。这与 iOS
`UserDefaultsReminderSettingsStore.load` 和 Android 原始 DataStore 读取路径一致；外观、教学日历、
DataStore 版本、键、仓库、状态层和页面均未改变。

新增真实 DataStore AndroidTest 通过公开 `save` 覆盖 `-1`、通过公开 `update` 覆盖 `181`，每步均断言
返回值、`load` 和关闭重建结果一致，并证明外观和教学日历未改变；既有原始无效值读取、0／180 边界、
取消传播、写入失败、损坏恢复、并发更新和复杂日历测试均保留。未修改 iOS、Web、共享 schema/fixtures、
Room、课表 JSON、MainActivity、Compose、通知调度、导入导出、D01 或 D03，且未进入 P2-03、P3 或完整 App。

最终干净构建 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
assembleDebugAndroidTest` 成功（40 秒）；Debug／Release JVM 各 34 tests、均为 0 failures／0 errors／
0 skipped。使用指定 SDK 与 AVD 在唯一 API 37 ARM64 `emulator-5584` 上确认 ADB `device`、
`sys.boot_completed=1`、SDK 37、ABI `arm64-v8a` 后，`connectedDebugAndroidTest` 成功；最终 XML 为
19 tests（Room 11、DataStore 8）、0 failures、0 errors、0 skipped，新增
`publicInvalidReminderLeadSaveAndUpdateKeepEnabledAfterReopen` 已进入设备测试体。完整证据见
[P2-02-R1 connected 测试证据](evidence/p2-02-r1-connected-debug-android-test-20260910.txt)。取证后已正常关闭
本次模拟器，`adb devices -l` 无连接设备。

本次修正仅为 P2-02-R1 实施，尚未独立审查、尚未用户验收，不代表 P2-02、A09、P2 或完整 App 完成。
下一步只交回分析审查窗口复审实际 diff、跨端提醒语义和 API 37 XML；不得自动进入 P2-03 或 P3。

## P2-02 偏好设置持久化已实施，等待独立复审（最新，2026-09-10）

执行基准为 `63b019a24b91021aad83e7528fcadbaa1fcca554`，分支 `Android`；开始时本地 HEAD、
`refs/heads/Android`、`origin/Android` 与远端 `refs/heads/Android` 均为该基准，工作区干净。
本次仅新增 Android DataStore 偏好边界和真实 DataStore AndroidTest，并固定
`androidx.datastore:datastore-preferences:1.2.1`；未修改 iOS、Web、共享 schema/fixtures、
MainActivity、Compose 页面、Room schema／仓库、课表 JSON、通知调度、导入导出、D01 或 D03，
也未进入 P2-03、P3 或后续阶段。

实现以可替换的 `SchedulePreferencesRepository` 为边界，在单个版本化 DataStore 文件中集中定义键和
编码，持久化外观三态、提醒和教学日历。读取时按 iOS 基准处理缺失字段、未知外观、提前量、旧数据
缺少自定义提前量标记、日期／星期、停课优先和午休规范化；可识别的文件损坏由 DataStore 恢复默认值。
普通 I/O／写入失败与 `CancellationException` 不捕获、不伪装为默认或成功；写入通过 DataStore 单次
更新原子执行，但没有也不宣称与 Room 的跨存储原子事务。系统通知授权仍是 Android 系统状态，未被
持久化，且本任务没有把偏好接入 `ScheduleAppState`、页面或通知。

指定 API 37 ARM64 AVD `qingke-api37-r3-arm` 以唯一 `emulator-5588` 运行，实测 ADB `device`、
`sys.boot_completed=1`、SDK 37、ABI `arm64-v8a`。`connectedDebugAndroidTest` 的 XML 实际为
18 tests（Room 11、DataStore 7）、0 failures、0 errors、0 skipped，全部 DataStore 测试进入测试体；
取证后已正常关闭本次启动的模拟器，`adb devices -l` 无连接设备。随后最终 clean 构建后的三次设备
重试（`emulator-5588` 一次、推荐端口 `emulator-5584` 两次）均在启动核验后、测试任务识别设备前
自行退出，`connectedDebugAndroidTest` 如实报 `No connected devices`，未执行任何测试体；该宿主模拟器
稳定性限制不覆盖已固定的成功 XML 结果。完整成功与失败记录见
[P2-02 DataStore connected 测试证据](evidence/p2-02-datastore-connected-debug-android-test-20260910.txt)。

最终 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest`
成功（45 秒）；Debug／Release JVM 各 34 tests、均为 0 failures／0 errors／0 skipped，`lintDebug` 和
AndroidTest APK 均已生成。成功 XML 后补强了既有 DataStore 测试中的 0 分钟边界、复杂日历重开及写入
故障后的重开重试断言；最终 `testDebugUnitTest testReleaseUnitTest assembleDebugAndroidTest` 也成功并编译
这些 AndroidTest 断言，但受上述模拟器退出限制，未能再次在设备上执行最终补强后的测试体。文档测试
36 项通过，`documentation.test.sh`、`repository-layout.test.sh` 与 `git diff --check` 通过。

P2-01-R1 协程取消代码复审此前已通过；本次 P2-02 实施尚未独立审查、尚未用户验收，不代表 P2-02、
A09、P2 或完整 App 完成。下一步仅交回分析审查窗口复审本次实际 diff、DataStore 真实设备证据和失败
边界；不得据此自动进入 P2-03 或 P3。

## P2-02 偏好设置持久化已获授权，待执行（最新，2026-09-09）

用户已明确同意在 P2-01 之后实施 P2-02“偏好设置持久化”。本项只建立 Android DataStore
偏好存储接口、默认值／未知值回退、重启持久化和可测试失败边界，不连接 Compose 页面、不实现
通知调度、不决定 D03，也不扩展版本 1 JSON 或 iOS 协议。

允许持久化的偏好范围为现有 iOS 基准中的外观模式、提醒设置和教学日历设置；系统通知授权仍
由 Android 系统状态负责，不能用偏好布尔值替代。各偏好键／序列化格式应稳定、版本可演进，
损坏或未知值回退到文档规定默认值；多个 DataStore 写入不宣称与 Room 课表构成跨存储原子事务。

下一项仅安排 P2-02，建议执行窗口 Terra／中。完成后必须由本窗口独立复审实际 diff、DataStore
重启／损坏／默认值测试和构建证据；通过后仍不得自动进入 P2-03、P3 或完整 App 验收。

## P2-01-R3 独立复审通过（最新，2026-09-09）

本窗口已复审 `fc1c7f7..85098ac`，确认提交 `f79ac57` 的 R3 生产修正与测试补齐未越界，
并核对证据澄清提交 `85098ac`。Room 存储损坏统一分类为 `InconsistentStore`，写入候选仍为
`InvalidData`；事务、回滚、重复 ID、显式顺序、外键级联、状态不二次 `load` 和取消传播语义均保持。

执行证据记录 API 37 ARM64 `emulator-5586` 的 11 项 Room 测试通过（0 failures、0 errors、0 skipped），
Debug／Release JVM 各 34 项通过，文档测试 33 项通过。本窗口独立重跑主机侧 clean 构建、双变体测试、
lint 和 AndroidTest APK 编译，`145 actionable tasks` 成功；当前无法再次启动已关闭 AVD，设备 XML 已被
后续 clean 清理，因此设备结论依据提交的固定证据文件，未声称本轮重新执行设备测试。

**结论：P2-01 独立复审通过。** 这不等于 A09、P2、完整 App 或用户验收完成；不得自动进入 P2-02、
P3 或实现其他未授权功能。D01、D03 和正式发行范围继续保持未决定。下一步等待用户安排后续任务。

## P2-01-R3 已执行，待最终独立复审（最新，2026-09-09）

执行基准为 `fc1c7f716df32a528317f13455b2c9c0e2f077e3`，分支 `Android`；开始时 HEAD、
`refs/heads/Android`、`origin/Android` 一致且工作区干净。仅修改了 R3 授权的
`RoomScheduleRepository.kt`、`ScheduleAppStateTest.kt`、`RoomScheduleRepositoryTest.kt`、
本证据文件和本交接；未修改 `p2-01-review.md`、iOS、Web、共享 schema/fixtures、页面、
DataStore、通知或导入导出，未进入 P2-02/P3。

生产修正：读取已能映射但重建聚合未通过 `ScheduleValidator` 的数据库记录时，统一抛出
`ScheduleRepositoryException.InconsistentStore`；待写入候选仍抛 `InvalidData`。已有损坏分类和
`CancellationException` 原样传播保持不变，未自动清库、未启用 destructive migration，也未改变
重复 ID、显式顺序、事务提交或状态回滚语义。

状态 JVM 测试按四种公开写操作分别补齐成功返回快照／不二次 `load`，并分别补齐失败保留旧
快照、`isSaving=false` 和普通错误；加载、重试、并发串行化、取消测试均保留。
Room 测试补齐多安排／重复 ID／反序节次重开、saveSemester/saveCourse/deleteCourse 全部 CRUD
语义、直接外键级联观察、无效写入保留、手工损坏分类与不清库、schema/外键/索引检查。

验证结果（最终 XML）：

- `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest`：成功；Debug/Release JVM 各 34 项，均 `failures=0, errors=0, skipped=0`；`ScheduleAppStateTest` 各 14 项。
- `connectedDebugAndroidTest`：成功；API 37 ARM64 `emulator-5586` 实际进入 11 项 Room 测试，`failures=0, errors=0, skipped=0`。随后最终 `clean ... assembleDebugAndroidTest` 清理了设备 XML 输出目录，数量已在证据文件中固定记录。
- 设备实测 `state=device`、`sys.boot_completed=1`、SDK 37、ABI `arm64-v8a`；取证后已执行 `adb -s emulator-5586 emu kill`，设备已关闭。
- `python3 docs/tests/android-documentation.test.py`：33 项通过；`bash docs/tests/documentation.test.sh`、`bash docs/tests/repository-layout.test.sh`、`git diff --check` 均需在提交前再次执行。

完整测试／设备证据见 [P2-01-R3 证据](evidence/p2-01-r3-connected-debug-android-test-20260909.txt)。本轮仍未声明 P2-01、A09、P2、完整 App 或用户验收完成，须交回分析审查窗口复核实际 diff、错误分类、原始记录、事务／外键和测试报告。

## P2-01-R2-R1 复审通过，P2-01-R3 待修正（最新，2026-09-09）

分析审查窗口最终复审了基准 `1bdfa77d01ba19f1dd3a2d1b289757012a452012` 上的实施提交
`886bca62061081a71144e7dcb4cccddf967554d7`。实际 diff 仅为 `RoomScheduleRepositoryTest.kt`、
执行交接和设备证据三个文件，没有超出 R2-R1 授权。重新 fetch 后本地与远端 `Android`
均为 `886bca6`，`origin/IOS` 为 `81ae16f`，复审开始工作区干净。本窗口没有修改应用代码，
也没有启动子 Agent。

三个表达式测试已正确改为块体 Unit 方法；独立 clean 编译后四个 `@Test` 的 JVM 签名均为
`void`。故障注入显式绑定 `beforeCommit`，位于 Room 事务写入之后、提交之前，既有异常和
重开后旧快照断言保留。独立使用唯一 API 37 ARM64 `emulator-5588` 从 clean 运行：109 个任务中
106 executed、3 up-to-date，Debug／Release JVM 各 28 项，Room 设备测试 4 项，失败、错误、
跳过均为 0。模拟器已在取证后正常关闭。因此 **P2-01-R2-R1 聚焦修正通过独立复审**。

但 P2-01 整体仍不能关闭。最终对照 [P2-01 契约](p2-01-persistence-state.md) 发现：当前 4 个 Room 用例
未覆盖原契约的多安排／重复安排 ID 顺序、完整仓库 CRUD、删除不存在项、外键级联、完整无效
写入组合、损坏存储不清库及 schema／destructive migration 自动化检查；状态 JVM 测试也未对四种
写方法逐一证明成功不二次读取和失败回滚。更具体地，`RoomScheduleRepository.read` 对“已存储行能够
映射但重建聚合校验失败”会原样抛出 `InvalidData`，而契约要求存储损坏 `InconsistentStore`。

下一项仅为 P2-01-R3：修正该错误分类并补齐契约中已要求的状态 JVM 和 API 37 Room 测试；新测试如
暴露其他生产缺陷，应保留失败证据并回交审查，不自行扩围。详细结论见
[P2-01 复审记录](p2-01-review.md) 和
[R2-R1 复审证据](evidence/p2-01-r2-r1-review-20260909.txt)。**P2-01 整体最终复审未通过**；
不得进入 P2-02、P3，不得宣称 P2-01、A09、P2 或完整 App 已完成或用户已验收。本轮文档验证
33 项、既有文档和布局测试及 `git diff --check` 均通过；提交和推送结果以最终交付消息为准。

## P2-01-R2-R1 已实施，等待最终独立复审（最新，2026-09-09）

执行窗口以 `1bdfa77d01ba19f1dd3a2d1b289757012a452012` 为基准，仅修改
`RoomScheduleRepositoryTest.kt`。三个原本表达式形式的 AndroidJUnit4 测试改为普通块体方法，
内部调用 `runBlocking`，避免末尾 `File.delete()` 的 `Boolean` 成为 JVM 方法返回值；同时将既有
事务故障注入调用显式绑定为 `beforeCommit`。后者是 R1 新增 `beforeRead` 参数后尾随 lambda 的
绑定目标变化所致，修正后保持原来的“提交前抛出 `IllegalStateException`”断言，不弱化任何测试。
未修改生产代码、iOS、Web、共享 schema／fixtures、MainActivity、页面、DataStore、通知或导入导出，
也未进入 P2-02、P3 或完整 App。

`javap` 已确认四个 `@Test` 方法均为 JVM `void`。使用指定 SDK、指定 AVD 目录和
`ANDROID_SERIAL=emulator-5588` 启动 `qingke-api37-r3-arm`：ADB 为 `device`、
`sys.boot_completed=1`、SDK 为 37、ABI 为 `arm64-v8a`。`connectedDebugAndroidTest` 实际在该唯一
API 37 ARM64 设备运行并通过；XML／HTML 报告为 4 tests、0 failures、0 errors、0 skipped，四个
Room 用例均进入测试体。双变体 JVM 测试各 28 项，均为 0 failures／0 errors／0 skipped。
完整设备、`javap` 和报告证据见
[P2-01-R2-R1 connected 测试证据](evidence/p2-01-r2-r1-connected-debug-android-test-20260909.txt)。

本次启动的 `emulator-5588` 已在取证后正常关闭，`adb devices -l` 已确认无连接设备。P2-01-R1 协程取消代码复审此前已通过；
本轮取得的是此前缺失的真实 Room 设备测试证据和测试入口修正，仍须由分析审查窗口最终复审
P2-01。不得自行标记 P2-01、A09、P2 或完整 App 已完成／用户验收。

## P2-01-R2 模拟器已可用，Android 测试入口待修正（最新，2026-09-09）

用户授权按已验证方案复用本机 API 37 ARM64 模拟器。本窗口以
`08aaab1b2e4550f05fcdaf6180400ffdd61736fc` 为基准，开始时分支 `Android`、本地 HEAD 和
`origin/Android` 一致，工作区干净。本轮只进行设备执行、分析和文档证据维护，
没有修改 Android 应用或测试源码，没有修改 iOS、Web、共享 schema／fixtures，也没有启动子 Agent。

使用 `ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd` 启动
`qingke-api37-r3-arm` 后，`emulator-5588` 已达到 ADB `device`、
`sys.boot_completed=1`，实测 SDK 37、`arm64-v8a`，启动日志包含 `-enable-hvf`。因此模拟器
环境门槛已解除，无需实体真机；先前 R1 只是没有启动正确 AVD。默认
`~/.android/avd` 中的 `target=android-0` 旧登记不得作为后续执行入口。

`connectedDebugAndroidTest` 已两次进入该模拟器，不再报 `No connected devices`；但
AndroidJUnit4 在测试体之前因 `InvalidTestClassError` 失败。报告为 1 个
`initializationError`（failures 1，errors 0，skipped 0），四个 Room 测试均未执行。根因是
`RoomScheduleRepositoryTest.kt` 三个表达式 `@Test` 以 `File.delete()` 的 `Boolean` 作为
`runBlocking` 结果，`javap` 确认它们被编译为 `boolean` 而非 JUnit 4 要求的 `void`。

下一步仅修正这三个测试方法的返回类型，不修改生产代码，不删除或弱化断言；然后在
正确 AVD 上重跑全部 `connectedDebugAndroidTest`。详细分析见
[P2-01 复审记录](p2-01-review.md)，现场证据见
[P2-01-R2 connected 测试证据](evidence/p2-01-r2-connected-debug-android-test-20260909.txt)。模拟器已在取证后
正常关闭。**P2-01 整体仍未验证、未审查通过**；不得进入 P2-02、P3，也不得
宣称 A09、P2 或完整 App 完成。本轮文档验证 32 项、既有文档与布局测试及
`git diff --check` 均通过；文档提交和推送结果以最终交付消息为准。

## P2-01-R1 代码复审通过，设备门槛仍开放（最新，2026-09-09）

分析审查窗口已重新独立复审 `c96658a3a5c8943a820893ff8c46d1079185a0ea`，修正基准为
`932f4b5367c641e3d1abc5a5ba1f7286283b2613`，详见
[P2-01 复审记录](p2-01-review.md)首节。实际 diff 与回传的 6 个文件一致，没有越过 R1 授权
范围；重新获取远端后本地 HEAD、`refs/heads/Android` 和 `origin/Android` 均为 `c96658a`，
`origin/IOS` 为 `81ae16f`，复审开始工作区干净。本窗口没有修改应用代码，也没有启动子 Agent。

结论：repository 读取取消在普通异常包装前重新抛出；状态加载／保存取消恢复操作前快照并
重新抛出，不发布 `FAILED`、普通错误或遗留 `isSaving`。状态取消测试在 Debug／Release JVM
各自实际通过；Room 读取取消和提交前取消回滚测试的源码范围与断言正确。因此
**P2-01-R1 协程取消修正通过代码复审，无新的应用代码修正项。**

独立使用 API 37 SDK 完成 clean 双变体构建、测试和 lint：112 个任务中 109 executed、
3 up-to-date，Debug／Release JVM 各 28 项且失败／错误／跳过均为 0；`lintDebug` 为 0 errors，
当前在线检查为 9 个既有 warnings。独立 `connectedDebugAndroidTest` 仍在设备执行前因
`DeviceException: No connected devices!` 失败，74 个任务中 33 executed、41 up-to-date，
实际 Room 集成测试数为 0；指定 SDK 的 `adb devices -l` 为空，也没有运行中的 Emulator／qemu。

所以 P2-01-R2 设备门槛仍开放，**P2-01 整体仍未验证、未审查通过**。当前证据只证明没有连接
设备，不足以证明此前 HVF／`mprotect` 环境问题仍存在或已经解除；不得把测试 APK 编译当作
Room 运行通过。后续只需在可用 API 37 ARM64 模拟器或等效 API 37 设备实际运行全部
`connectedDebugAndroidTest` 并回传测试数量、失败和跳过；未取得该证据前不得进入 P2-02、
P3，也不得宣称 A09、P2 或完整 App 完成。

## P2-01-R1 已实施，等待重新独立复审（最新，2026-09-09）

执行窗口仅修正 P2-01 独立复审的协程取消语义，未进入 P2-02、P3 或完整 App。基准为
`932f4b5367c641e3d1abc5a5ba1f7286283b2613`，开始工作区干净。`RoomScheduleRepository.read` 在
包装普通读取异常前显式重新抛出 `CancellationException`；`ScheduleAppState.load` 与写入边界在
取消时恢复操作前快照并重新抛出取消，不发布 `FAILED`、普通 `error` 或遗留 `isSaving` 状态。
新增的状态 JVM 测试覆盖加载／写入取消传播和无误导状态；Room Android 集成测试新增可控读取取消与
事务提交前取消，断言取消原样传播且重新打开数据库仍为旧快照。原有事务、重复 ID、显式顺序、
损坏数据及成功写入不二次读取语义未改。

本机使用临时 `ANDROID_HOME=/Users/takagisan/Library/Android/sdk-qingke-api37` 完成
`clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug`；Gradle 记录为
`BUILD SUCCESSFUL in 48s`。Debug／Release JVM 各 28 项（共 56 项）均为 0 failures／0 errors／0 skipped；
`lintDebug` 为 0 errors、7 个既有 warnings。`python3 docs/tests/android-documentation.test.py`（30 项）、
`bash docs/tests/documentation.test.sh`、`bash docs/tests/repository-layout.test.sh` 及 `git diff --check`
均已通过。

`connectedDebugAndroidTest` 已重新执行，测试 APK 74 个任务中完成 34 个、40 个 up-to-date，但因
`DeviceException: No connected devices!` 在设备执行前失败，没有运行 Room 集成用例。随后
`adb devices -l` 为空；SDK 可列出 `qingke-api37-r3-arm` 等 AVD，但没有正在运行的 Emulator。
此前 API 37 ARM64 宿主的 HVF／`qemu_mprotect__osdep`／ADB offline 限制仍未解除；本轮完整命令和
现场证据见 [P2-01-R1 connected 测试证据](evidence/p2-01-r1-connected-debug-android-test-20260909.txt)。
因此不得将设备集成测试、P2-01、A09 或 P2 标记为已验证／已审查通过。完成本轮文档验证、提交和推送后，
必须回交分析审查窗口复审实际 diff、取消测试和设备限制。

## P2-01 独立复审未通过，等待修正（最新，2026-09-09）

本窗口已独立复审提交 `39eeaa3529aa761174ff6c4f3c7fcd38edb06d6f`（范围
`6f5258c..39eeaa3`）。Room schema、事务替换、重复 ID／顺序设计、StateFlow 不二次读取以及
主机侧 JVM／文档验证大体符合 P2-01 范围，但复审未通过，详见 [P2-01 独立复审记录](p2-01-review.md)。

必须先修正两项：

1. `RoomScheduleRepository` 与 `ScheduleAppState` 的 `catch (Throwable)` 会吞掉
   `CancellationException`，把协程取消误报为存储／状态失败；需要显式传播取消并补充取消测试。
2. `connectedDebugAndroidTest` 尚未成功运行。API 37 ARM64 AVD 受宿主
   `hvf is not enabled on this aarch64 host`、`qemu_mprotect__osdep: mprotect failed: Permission denied`
   和 ADB `offline` 限制，当前只有测试 APK 编译证据，没有真实 Room 运行证据。

当前不得标记 P2-01 已审查通过，不得进入 P2-02、P3 或宣称 A09／P2／完整 App 完成。修正建议为
Terra／中；修正后回到本分析窗口复审。复审应用基准为 `39eeaa3`；本次审查文档提交后，当前本地
HEAD 与 `origin/Android` 均为 `6e4f62e`，工作区干净。

## P2-01 执行完成，待独立审查（最新，2026-09-09）

执行窗口在用户授权范围内完成 P2-01“持久化基础与状态边界”实施。开始前已读取
`AGENTS.md`、本交接、P2-01 分析记录及相关基线文档；分支为 `Android`，开始基准、本地
HEAD 与 `origin/Android` 均为 `e8aefb426f791cdda8c3fb748e973a2243b78a71`，工作区干净。
本次不修改 iOS、Web、共享 schema／fixtures 或 `MainActivity`，未启动子 Agent，未进入
P2-02。

实际新增 Room 2.8.4/KSP 2.3.11 的版本 1 数据库及已提交 schema、可替换的 suspend
`ScheduleRepository`、事务型 `RoomScheduleRepository` 和 `StateFlow` 的
`ScheduleAppState`。业务 ID 与内部行键分离，显式顺序字段还原列表，课程删除由外键级联；
空存储、损坏数据、整表替换、首个重复 ID 增改删、固定时钟及事务故障回滚均按 P2-01 契约
实现。写仓库调用返回已提交完整快照，状态层仅在成功后发布该快照而不二次读取；加载／保存
失败保留旧内存快照且可重试。新增 JVM fake-repository 状态测试和 API 37 Room 集成测试；
README 同步记录 Room/KSP 与两端严格未知字段决定。

已实际通过：使用本机保留 API 37 SDK（platform `android-37.0`）执行
`./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest`（104 个
任务，成功）、`./gradlew lintDebug`（成功）、`./gradlew assembleDebugAndroidTest`（56 个任务，
成功）、三项文档／布局验证及 `git diff --check`。Room schema 已生成于
`Android/app/schemas/com.qingke.schedule.persistence.ScheduleDatabase/1.json`。

`connectedDebugAndroidTest` **未通过也未执行**：本次启动已登记 API 37 ARM64 AVD 后，受当前
宿主限制，Emulator 报 `hvf is not enabled on this aarch64 host` 和重复
`qemu_mprotect__osdep: mprotect failed: Permission denied`，ADB 一直是 `offline`；已停止本次
失败实例，未保留运行中的课表 Emulator。故 API 37 Room 集成测试仅完成 APK 编译，尚缺真实
设备运行证据，不能标为已测试通过或审查通过。完成提交与推送后须独立审查，审查重点包括实际
diff、事务回滚／损坏边界、重复 ID 与顺序、StateFlow 无二次读取，以及上述设备限制；不得自动
进入 P2-02、P3 或宣称 P2／完整 App／用户验收完成。

## P2-01 分析完成与执行边界（最新，2026-09-08）

分析审查窗口已基于实际 Android 与 iOS 源码完成 P2-01“持久化基础与状态边界”范围分析，
详见 [P2-01 分析记录](p2-01-persistence-state.md)。分析基准为
`cba042bc68cb6254c9410fc9f84c6767bc6715e9`，当前分支 `Android`；重新获取远端后本地 HEAD、
`refs/heads/Android` 和 `origin/Android` 一致，`origin/IOS` 为
`81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587`，工作区开始时干净。两个分支的 iOS
持久化／状态源码没有差异。本轮只修改 Android 分析／交接文档和文档测试，
没有修改应用代码，也没有启动子 Agent。

实际 Android 仍只有 P1 领域、校验、JSON 边界和占位 Compose 入口，没有 Room、DataStore、
仓库或应用状态实现。P2-01 现已划定为：稳定 Room 2.8.4 + KSP 2.3.11、版本 1 schema、
可替换 suspend 仓库、原子增改删／整体替换，以及只在提交成功后发布仓库返回快照的最小
StateFlow 状态边界。空库与损坏库必须区分；失败保留磁盘和内存旧快照；顺序显式保存；
不得以数据库唯一约束收紧 P1 已接受的重复 ID。

iOS 基准的可用行为继续保留，但不照搬两个风险：SwiftData 的 DTO ID 唯一约束比当前领域
规则严格；iOS 状态层写入后再次读取存在部分失败窗口。Android 写方法应直接返回已提交完整
快照，并通过事务故障注入、关闭重开数据库和 fake repository 状态测试证明一致性。

本任务明确不包含 DataStore／偏好、页面、草稿、导入导出、通知、权限、分享、发布或
D01／D03／正式发行决定，也不修改 iOS、Web 或共享 schema／fixtures。实施完成后必须运行
双变体 JVM 测试、API 37 Room 集成测试、构建、lint 和文档验证，独立提交并推送 `Android`；
由于这是完整持久化模块和数据一致性边界，随后需要独立审查。P2-01 尚未实现、测试或审查，
不得自动进入其余 P2 或后续阶段。

## 分析审查窗口切换状态（最新，2026-09-08）

本窗口按用户要求完成同角色交接后停止写入。当前角色为执行窗口（Terra）；本次授权
仅限整理交接状态，不启动 P2 实施、不修改应用代码。切换前已核对当前分支 `Android`，
本地 HEAD、`refs/heads/Android` 与 `origin/Android` 均为
`d4f7f6478c2e007dea5e3d60774a5e3e2525c535`，工作区干净；`origin/IOS` 为
`81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587`。本轮只更新本交接文档，没有启动子 Agent。

当前有效状态：P1 已完成独立审查并获用户确认，P2 已获授权；P2 尚无应用实施提交，
下一项仅为 P2-01“持久化基础与状态边界”。P2-01 的分析记录和执行边界已写入
`p2-01-persistence-state.md`，但尚未实现、测试或审查。新窗口须先核对最新代码、
分支、远端和工作区，再继续用户明确授权的 P2-01 工作；不授权分析窗口修改应用代码，
不得重复 P1 实施或审查，
也不得把 P2-01 扩大为整个 P2 或完整 App。

继续保留的决定与边界：版本 1 JSON 未知字段两端严格拒绝；API 37 工具链和设备门槛
已通过；D01、D03 和正式发行范围仍未决定。P2-01 应聚焦可测试的存储接口、状态
加载／保存和失败回滚契约，不提前实施页面、导入导出、通知或发布能力。

切换现场的只读检查没有发现连接中的 Android 设备，也没有发现正在运行的 Gradle、
`xcodebuild` 或课表项目 Emulator 进程；本窗口没有终止其他窗口的进程。当前没有正在
运行的操作。详细 P1 证据继续以现有专项复审文档为准，不因窗口切换重复验收；本次交接
提交编号以最终交付消息或 `git log` 为准，避免文档自引用尚未产生的提交。

## P1 用户确认与 P2 授权状态（最新，2026-09-08）

用户已确认 P1 阶段交付结果，并明确授权开始 P2。P1-01、P1-03、P1-04 及
P1-04-IOS-SYNC 的实现范围、独立复审、构建／测试和设备证据均已完成；`Android` 分支
远端为 `7e61f41`，`IOS` 分支远端为 `81ae16f`，相关工作区均已核对干净并推送。

当前决定：**P1 授权范围已完成并获用户确认；允许进入 P2，但不代表完整 App 或 A01—A11
已完成。** D01、D03、正式发行范围仍未决定。下一项只安排 P2-01“持久化基础与状态边界”，
先建立可测试的存储接口和状态协调边界，再由用户逐项确认后续实现；不一次实施整个 App。

本轮只整理文档和交接，不修改应用代码。新窗口接手前必须先读取 `AGENTS.md` 和本文件，
核对分支、提交、工作区及未提交改动；不得重复 P1 实施或审查，不自动扩展 P2 范围。

## P1 阶段关闭核对状态（最新，2026-09-08）

分析审查窗口已核对 `Android` 与 `IOS` 两个开发分支的 P1 交付状态。P1-01、P1-03、P1-04
及 P1-04-IOS-SYNC 均已完成对应独立复审，P1-04-IOS-SYNC 独立专项复审通过；`Android` 远端为 `b1ab580`，`IOS` 远端为
`81ae16f`，两个分支工作区均干净并已推送。P1-04 的 iOS 传输源码和测试在两个分支逐字一致，
共享 schema/fixtures 未被修改。

当前结论：**P1 授权范围和审查门槛已完成，并已获用户确认；P2 已获明确授权。**
本核对不代表完整 App 已完成，不代表 A01—A11 已全部验收。D01、D03、正式发行范围等
未决事项继续保留；P2 仍须按独立任务逐项实施和验证。

详细的 IOS 同步复审见 [P1-04-IOS-SYNC 复审](p1-04-ios-sync-review.md)。

## P1-04 未知字段复审状态（历史记录，已由顶部状态更新）

用户已确认版本 1 课表 JSON 的顶层、semester、period、course 和 course schedule 对象
均严格拒绝协议未声明字段。共享 schema 和 Android 当前实现已经严格；iOS
`ScheduleDataTransfer.previewImport` 因默认 `JSONDecoder` 行为仍会忽略未知字段，需在
P1-04 统一。这样可以避免未知数据被静默接受后在再次导出时无提示丢失。

P1-04 已从基准 `cb7323f` 实施完成：iOS 导入边界在
版本检查后递归拒绝五层未知字段，保持不支持版本错误优先级；新增 7 项 iOS 单元测试。
API 37/JDK 17 的 Android 构建与双变体 JVM 测试通过（70 个任务实际执行）；扩展后的
19 例跨端探针逐项核对，五层未知字段均被 Android 与 Swift 拒绝；iPhone 17 Pro、iOS
26.5 Simulator 完整测试 93 项通过，失败/跳过均为 0。详细证据见
 [P1-04 任务记录](p1-04-unknown-fields.md)。本任务涉及跨端协议，已通过本轮独立专项复审；
未修改共享 schema、Android 行为、重复 ID、节次顺序、D01／D03 或 UI；独立专项复审通过，不进入 P2。后续另行
核对 P1 是否关闭，并安排同步到 `IOS` 分支。

## P1 阶段关闭核对状态（历史记录，已由顶部状态更新）

分析审查窗口已核对 P1-01、P1-03、P1-04 的授权范围、独立复审、构建／测试证据、
Android 分支和远端状态。三项子任务均已通过相应审查，`Android` 分支工作区干净，
本地 HEAD 与 `origin/Android` 一致。P1-04 的 iOS 导入修正目前只存在于 `Android`
分支提交 `4b7ff3e`，`IOS` 分支当前仍为 `c11bd2b`，因此跨分支同步和 IOS 分支复测
尚未完成。

当前结论：**Android 分支上的 P1 授权范围和审查门槛已具备关闭条件；IOS 同步和复测也已
完成。P1 阶段仍不标记为用户验收完成，不进入 P2。** 这不是用户验收；后续阶段需用户明确授权。

## P1-03-R4 最新专项复审状态（优先于下方历史，2026-09-08）

用户已授权继续在本机修复项目所需模拟器环境。基准 `4ee8919`，分支 `Android`，
开始工作区干净；本轮只维护文档和证据，不修改应用代码，不启动子 Agent。
已定位 AVD 登记 `target=android-0` 导致误识别 API 3、HVF 未启用；修正为
`target=android-37` 后，原稳定版 Emulator 37.1.11.0 成功完成 API 37 ARM64 开机。
安装 APK、两次冷启动、Activity／进程、截图和清空后无崩溃／ANR logcat 已实测，
关闭并带窗口再次开机也成功。此前“必须换真机或宿主”的限制已解除。

**运行证据已补齐并通过独立专项复审，P1-03 授权范围完成；P1 阶段关闭与用户验收
不在本次结论内，不进入 P2。**
详见 [R4 修复和运行记录](p1-03-validation.md) 的首节；下方旧设备阻塞和换宿主建议
仅作历史。未知字段策略现已确认并纳入 P1-04；D01／D03 和正式发行决定不变。本轮构建 70 项 up-to-date，
读取既有双变体各 20 项报告，不冒充重新执行 JVM 测试。
系统开发者模式按用户授权已开启，但单独开启未解决问题；未证明其必要性。
预览版仅隔离保留，实际成功使用稳定版，未修改项目依赖。
独立复审用临时只读登记副本重现 `android-0` 的 API 3／HVF／`mprotect` 失败，并用当前
`android-37` 登记在端口 5588 再次完成 API 37 开机、安装和冷启动；70 个 Gradle 任务
通过 `--rerun-tasks` 实际执行，双变体各 20 项测试通过。详见
[R4 独立证据](evidence/p1-03-r4-review-20260908.txt)。无需 Terra 继续排查环境；未知字段
已决定为两端严格拒绝，下一项为 P1-04，本轮不实施 P2。本次审查提交从交付消息或 git log 查询。


## 当前流程更新（优先于历史配置）

2026-09-08 用户确认试行“执行为主、分析按需、关键点审查”，规则见根目录 AGENTS.md 和实施计划。普通任务由执行窗口完成分析、实现、测试、交接状态维护、提交和推送；低风险任务不强制独立审查。复杂实施可直接建议 Sol／高，Astra 按需；分析窗口仍不修改应用代码。先观察 3–5 个实际任务的返工、交接及可获得的用量，再调整，不承诺最省。官方和中转站共用规则，用户只复制必要的完整交接块。

P1-03 的历史过程包括实施提交 `7d34c78`、R2 文档修正 `2e5b8ec` 和 R3 环境失败记录 `23743be`；这些步骤当时仍缺 API 37 设备证据。此后 R4 已定位 AVD 登记问题并补齐设备运行证据，最新结论以本文首节和 [P1-03 专项复审](p1-03-review.md) 为准。

## 当前分支安排（优先于下方历史记录）

2026-09-08 用户确认使用 `Android`、`IOS` 两条开发分支和 `main` 稳定分支。当前工作目录留在 `Android`，所有安卓窗口接手先核对这一分支。旧 `codex/ios-ui-redesign-demo` 保留在 `8791dbe` 作为历史，不再用于日常开发。

建分支前已确认工作区干净、远程没有同名新分支；`git diff --exit-code bef808b HEAD -- ios` 通过，当前 iOS 文件与最后 iOS 提交一致。两个新分支从包含本轮规则的同一完整快照起步，均保留全部目录和历史，避免 iOS 分支丢失新的协作／同步规则。分支规则提交 `c11bd2b` 已上传 `origin/Android`；此操作不代表任一应用新增验收通过。

本轮新增分支规则验证；文档测试和远程核对结果在最终交付报告。后续每次提交推送对应开发分支，稳定阶段合并 `main` 需明确安排。下方旧分支名与旧推送范围只作历史，不作为当前操作指令。

## GitHub 同步交接（历史）

2026-09-08 用户确认正式仓库为 [QingKeSchedule](https://github.com/SukiBanQin/QingKeSchedule)，常规交付需要提交并推送同名任务分支。当前分支 `codex/ios-ui-redesign-demo`，原 `origin` 指向旧名 `School_timetable`，本轮更新为指定仓库（保留 SSH 443 传输方式）。

同步前本地 HEAD 为 `e4df5fb`；远程仅有 `main`，头提交 `552c045`，与本地比较为远程独有 1、本地独有 67 个提交。因此本轮上传开发分支，不覆盖或合并 `main`，不强制推送。新增开发分支的实际推送结果及本轮提交编号见最终交付消息，接手时用 `git ls-remote origin refs/heads/codex/ios-ui-redesign-demo` 核对，不把上传计划当作已成功。

本轮仅修改同步规则及文档验证，未重新验收应用。文档验证结果在交付时报告。

## 本轮协作配置更新

2026-09-08 用户确认：默认分析审查模型改为 Sol（日常中、正式审查高），执行仍默认 Terra；Astra 仅作为疑难问题升级选项。窗口和交接按角色命名，具体建议见实施计划。本轮仅维护文档与对应验证，不修改应用代码、不重做无关历史审查，不切换实际模型设置。当前实际窗口模型和档位未核实。P1-01-R2 实施提交 `985cdd6` 已由分析审查窗口独立复核通过；下方上一轮“待执行／待审查 R2”的描述属于历史，勿重复派发。旧审查记录里的 Astra 名称保留作历史，不要求接手者继续使用 Astra。

本轮文档验证：文档测试 16 项、既有文档和布局测试、工作区与暂存补丁检查通过。P1-01-R2 有独立应用验证；D02 授权只代表允许开始 P1-03，不等于升级已经完成、整个 P1 通过或用户验收通过。

## 最近更新与阅读入口

更新时间：2026-09-08。P1-01、P1-03、P1-04 及 P1-04-IOS-SYNC 均已通过独立复审；API 37 模拟器环境阻塞已解除，两个开发分支已同步。用户已确认 P1 结果并授权进入 P2，下一项为 P2-01，详见 [P1-04 任务记录](p1-04-unknown-fields.md) 和 [IOS 同步复审](p1-04-ios-sync-review.md)。

先读 [根目录规则](../../AGENTS.md)，再按任务阅读 [功能对照](product-baseline.md)、[技术方案](technical-design.md)、[实施计划](implementation-plan.md)。接手时检查 `git status --short`、`git branch --show-current` 和 `git log -5 --oneline`，不能只信文档中的状态。

## 已确认决定

- Android 首版复现当前 iOS 已有功能、业务行为和视觉风格，系统交互按安卓方式实现。
- `Android/` 存放安卓代码，`docs/Android/` 存放本文档，保持大小写一致。
- 官方／中转站各一个分析审查窗口（默认 Sol）和执行窗口（默认 Terra），采用人工提示词交接；Luna 按需增加，暂不启用自动委派。
- 分析窗口不修改应用代码；本轮用户明确要求写文档，因此允许文档和相关验证改动。
- 延续每次改动更新测试、验证并创建 Git commit 的仓库要求。
- 用户已确认直接交接：模型最终回复末尾给出唯一一段完整中文交接块，标明接收窗口和建议档位；用户只复制，不查找提示词或整理结果。执行窗口默认维护任务状态，分析审查窗口在需要审查时维护结论，具体任务的更窄授权优先。

## 建议与待决定事项

- Kotlin + Compose、Room、DataStore 和 Mac 主力开发是已提出的建议；本轮确认 P1 采用 Kotlin + Compose 单 app 工程，Room/DataStore 仍按阶段计划在 P2 接入。
- D01：是否让备份携带教学日历设置并同步扩展 iOS 协议；现有版本 1 不携带这些设置。
- D02：已确认包名 `com.qingke.schedule`、最低 API 26。P1-03 已采用 API 37.0 + AGP 9.4.0 + Gradle 9.6.0 + Build Tools 36.0.0 + JDK 17；保持 minSdk 26，工具链、Android 17 适用性和 API 37 设备运行门槛均已通过专项复审。API 37.1／37.2 没有本轮核实的完整官方兼容映射，不纳入此次升级。
- D03：精确提醒不可用时的降级方式与文案尚未确定。
- D04：已确认 P1 仅做个人安装验证和可复现 debug 构建，不配置商店签名；正式发布范围仍未确定。
- 用户已确认版本 1 未知字段两端严格拒绝；P1-04 已在 `Android` 分支通过独立专项复审，P1-04-IOS-SYNC 已在 `IOS` 分支同步并通过复审。用户已确认 P1 结果并授权进入 P2，下一项为 P2-01。实际窗口模型和档位未核实，不要替用户变更设置。

## 当前代码与既有改动

初始 iOS 参考提交：`dabdc2eae41143b1288ae5f9ba5eabcebce6de5a`。本次文档提交另从交付消息或 Git 历史查询，不使用自引用提交编号。

开始建档时已有以下未提交文件，后已按用户授权独立提交：

- `ios/QingKeSchedule.xcodeproj/project.pbxproj`
- `ios/QingKeSchedule.xcodeproj/xcshareddata/xcschemes/QingKeSchedule.xcscheme`

历史窗口在 `525910f` 后按用户授权将两处 iOS 工程配置单独提交为 `bef808b`。本轮开始 HEAD 为 `23743be`、分支为 `Android`，本地与 `origin/Android` 一致，工作区干净；相关 iOS 领域/传输与共享协议未改动。

首轮审查时已核对：分支 `codex/ios-ui-redesign-demo`，开始 HEAD `85e3234`。当时两文件为未提交改动，后由用户授权提交为 `bef808b`；pbxproj 为开发团队和包标识设置，scheme 为测试并行属性及 XML 换行。它们没有进入本轮文档暂存范围。相关 iOS 源码与共享协议自初始基准以来无提交变化。后续接手仍需重新核对现场。源码阅读不代表已执行 iOS 构建、模拟器、UI 或真机验收。

## 已完成与未完成

- 文档已由用户确认；P1-01 实施提交 `9b521db` 已逐项核对。实际父提交 `0a498b9`，前置 `f6a920a`／`0a498b9` 是交接和参数确认文档；后续 `85e3234` 是流程文档，不是实现内容。
- 已实现并复跑测试：单 app Compose 工程、教学周／单双周、最小 DTO／校验和共享 fixture 解码。未实现完整存储、状态、页面和系统能力，A01—A11 未完整验收。
- 首轮已审查，结论未通过：数值字符串被接受／整数数值表示误拒绝、非法 UTF-8 被替换后接受、年份 0000 与 iOS 不一致。具体位置、影响和复现见 [审查记录](p1-01-review.md)。
- 已确认决定：版本 1 未知字段按共享 schema 严格拒绝；Android 已符合，Swift 导入边界待 P1-04 修正。重复 ID／节次编号顺序实测两端均宽容，不自行加严。
- 修正提交 `3924d26`、`985cdd6`、P1-03-R4、P1-04 提交 `4b7ff3e` 及 IOS 同步提交 `0a3252d` 均已通过独立复审。版本 1 未知字段策略已决定为两端严格拒绝；P1 授权范围审查已完成并获用户确认，P2 已获授权。
- 本轮同步纠正功能基线和技术方案中“待确认／没有 Wrapper”等过时状态，不修改 iOS 基准或擅自扩大协议。D01、D03、正式发行和目标机型仍待后续决定。

## 验证记录

2026-09-07 首轮审查独立执行；以下应用结果属于修正前，不能作为 `3924d26` 已通过的证据。完整命令与探针结果见 [审查记录](p1-01-review.md)。

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| 默认 `./gradlew assembleDebug test --rerun-tasks` | 失败 | SDK location not found，测试未启动 |
| 设置实际临时 ANDROID_HOME 后同命令 | 通过 | 68 个任务全部执行；debug APK；debug/release 各 12 项 JVM 测试，无失败/错误/跳过 |
| `python3 docs/tests/android-contract-review-probe.py` | 已执行，发现差异 | 10 个同输入案例调用 Android decoder 和 macOS 编译的原 Swift previewImport；执行成功不代表兼容通过 |
| adb 设备清单 | 无连接设备 | 未安装或启动应用 |
| `python3 docs/tests/android-documentation.test.py` | 通过，7 项测试 | 文档结构、链接、阶段、基准和新增审查交接约束 |
| `bash docs/tests/documentation.test.sh` | 通过 | 既有文档约束回归 |
| `bash docs/tests/repository-layout.test.sh` | 通过 | 既有仓库布局回归 |
| `git diff --check`、`git diff --cached --check` | 通过 | 工作区与本任务暂存文档补丁空白检查 |

未运行：Android 安装/UI/模拟器/真机、iOS 完整构建/测试/设备、Web 测试。未做 Windows 或全新依赖缓存复现。临时 SDK 路径只用于记录此次实测，不能当作长期环境配置。文档验证通过不代表 P1-01 审查通过。

P1-02-R2 已取得 API 35 ARM64 设备安装、两次冷启动、Activity、截图和 logcat 证据。该证据不替代 P1-03 所需的 API 37 设备证据；P1-03 主机侧专项复审结果见 [独立记录](p1-03-review.md)。

### P1-01-R2 独立复审证据

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| `./gradlew clean assembleDebug test --no-daemon --console=plain`（显式配置临时 SDK/JDK 17） | 通过 | 69 个任务执行；Debug/Release 各 20 项，失败／错误／跳过均为 0 |
| `python3 docs/tests/android-contract-review-probe.py` | 通过并人工核对结果 | 16 例；四种非法数字两端均拒绝，合法指数两端均接受；未知字段差异保持未决 |
| 修复前后临时探针日志 | 存在且一致 | diff 只显示四种非法数字的 Android 结果由接受变为拒绝；临时日志不替代本轮独立探针 |
| APK 元数据检查 | 通过 | `com.qingke.schedule`、minSdk 26、targetSdk 35 |
| 本轮设备／升级工具链 | 未运行 | API 35 ARM64 启动沿用既有已复审证据；未运行 AGP 9.4、CI、Windows、iOS 全量或真机测试 |

### P1-03 独立专项复审证据

复审范围为 `7d34c78^..7d34c78`，实际 14 个文件，与执行报告一致；没有应用 Kotlin
源码、iOS、Web 或共享 schema／fixtures 改动。执行者未提交完整构建控制台日志，复审
没有把其自述当作证据，而是重新运行可用检查并读取新生成的报告。

| 检查 | 独立结果 |
| --- | --- |
| 官方兼容与依赖解析 | API 37／AGP 9.4.0／Gradle 9.6.0／Build Tools 36.0.0／JDK 17 符合兼容表；KGP、Compose compiler、serialization plugin 均为 2.2.10 |
| 在线与离线 clean 双变体构建／测试 | 均通过；各 100 个任务；Debug／Release 各 20 项，无失败／错误／跳过 |
| `lintDebug` 与 APK 元数据 | 均通过；包名正确，minSdk 26、targetSdk 37 |
| 跨端探针 | 16 例实际执行并逐项核对；结果与执行记录一致，未知字段差异保持未决 |
| API 37 ARM64 模拟器 | R2 复审用官方启动器禁快照、无窗口、软件图形后仍复现 HVF 未启用与 QEMU `mprotect` 拒绝，ADB 持续 offline；判定为当前环境限制，设备门槛仍缺证据 |
| 结论 | 工具链主机侧及 R2 源码／依赖边界通过专项复审；P1-03 整体暂不通过，下一步为 P1-03-R3 设备环境恢复与运行验证 |

完整命令、范围和修正要求见 [P1-03 专项复审](p1-03-review.md)。

## P1 实施准备记录（历史）

以下保留进入 P1 前的讨论依据；D02 和首轮 D04 的后续确认见本节末尾，旧建议不是需要重复询问的事项：

- P1 工程建议先采用 Kotlin + Compose + 单 `app` 模块，领域与 JSON 契约先不依赖 Android UI；Room、DataStore 在 P2 再接入。这样首个提交可独立验证构建、日期规则和共享协议。
- D02 建议首轮以当前可获得的 Android Studio/SDK 稳定组合为准，最低版本优先选择 API 26（Android 8.0），目标版本使用安装环境可用的最新稳定 API；包名建议暂用 `com.qingke.schedule`。这些参数影响 Gradle 配置和真机覆盖，需用户确认后锁定。
- D03 不阻塞 P1 领域与协议测试；提醒实现前建议采用“降级为系统允许的非精确提醒，并在设置页明确提示可能延迟”的方案，具体文案留待提醒阶段确认。
- D04 不阻塞 P1；P1 只产出可复现的 debug 构建和测试，不配置商店签名或发布流水线。
- D01 不阻塞 P1；P1 仅兼容现有版本 1 JSON，不修改 iOS、共享 schema 或备份协议。

已收到的 P1 参数确认：

用户已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；P1 仅做个人安装验证（debug/reproducible build），不做商店发布签名。上述决定不等同于 D01、D03 或 D04 的完整产品决定。

## 给 Terra 的第一项实施提示词（历史，不要重复执行）

```text
任务编号与阶段：P1-01，工程与规则基础
角色：执行；仅实施本次范围，不完成整个 App。
建议模型／思考档位：Terra／中（本轮补充建议，不代表此前执行实际设置）。
选择理由：涉及工程、稳定依赖和基础规则测试，需要中档起步。
目标：在 Android/ 建立可构建的 Kotlin Android 工程（Compose、单 app 模块），固定经过验证的稳定依赖；实现与 Android UI 无关的最小领域/JSON 契约骨架，覆盖教学周计算、单双周判断、版本 1 数据解码与业务校验，并接入共享 fixtures。
代码基准／当前分支：基于提交 78e362e；当前分支 codex/ios-ui-redesign-demo。开始前运行 git status --short --branch、git log -5 --oneline，保留两处既有 iOS 工程配置改动，不覆盖、不暂存、不提交它们。
必读文档与参考源码：AGENTS.md；docs/Android/handoff.md；docs/Android/product-baseline.md；docs/Android/technical-design.md；docs/Android/implementation-plan.md；ios/Shared/schedule-data.schema.json；ios/Shared/fixtures/manifest.json 及其 fixtures。
允许修改的路径：Android/**；必要的 Android 测试与构建配置仅限 Android/**。不得修改 ios/**、web/**、共享 schema/fixtures 或 docs/Android/**（交接记录由分析窗口维护）。
已确认决定及不能自行决定的差异：目标是复现 iOS；P1 不扩展 D01 协议。D02 已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；D04 当前仅限个人安装 debug 验证。不得自行扩展 D01、D03 或 D04 的范围。
验收条件：./gradlew assembleDebug 可复现通过；./gradlew test 通过；JVM 测试覆盖教学周边界、单双周、版本/必填字段/颜色/日期时间/课程安排校验、semester:null，以及有效和无效共享 fixtures；未知版本、非法业务数据和超出输入上限按文档拒绝。提供实际命令与输出摘要。
交付：修改摘要、测试文件和结果、构建命令和结果、提交编号、未完成项/限制、下一步建议。只暂存 Android/** 并创建一个独立 Git commit；完成后把提交号和证据回传，并说明交接记录待分析窗口更新。
```

## 下一步

用户已确认 P1 结果并授权进入 P2。下一项为 P2-01“持久化基础与状态边界”，由执行窗口
先实现最小可测试存储接口、状态加载／保存边界和失败回滚契约；具体范围、文件和验收条件由
分析窗口交接块明确。未完成 P2-01 前不安排 P2-02 或完整 App 实现。

P1-03-R4 已通过专项复审，P1-03 无后续修正任务。用户已决定两端严格拒绝版本 1
未知字段，P1-04 及 P1-04-IOS-SYNC 已通过独立专项复审并完成同步。以上为历史交接内容，
当时没有 P2 授权；当前以本文顶部状态为准，不重复派发 P1 或自动扩大 P2 范围。

### P1-03 已授权范围（历史，已实施）

- 目标组合：`compileSdk`／`targetSdk` 37（API 37.0）、AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0、JDK 17；`minSdk` 26、包名和 namespace 保持不变。
- 处理 AGP 9 built-in Kotlin 迁移，按官方迁移方式调整 `org.jetbrains.kotlin.android`；保留并实测 Kotlin serialization 与 `org.jetbrains.kotlin.plugin.compose`。插件版本必须与 AGP 内置 Kotlin 实际兼容，不能只保留 2.0.21 后假定成功。
- Compose BOM 2024.12.01 和其他 AndroidX 依赖默认保持；只有构建或运行证据证明必须升级时才做最小调整并说明依据。不得借机修改业务规则、JSON 未知字段策略、iOS、Web、共享 schema/fixtures、Room/DataStore 或页面功能。
- 允许维护本任务 `docs/Android/handoff.md` 及必要文档验证；允许修改 Android 工具链和构建文件、Wrapper、`Android/README.md`，以及为验证真实工具链回归所必需的 Android 测试。主应用 Kotlin 源码只有在工具链编译迁移确实要求时才能最小修改，并须单独说明原因和行为不变证据。
- 验收至少包括：核对官方兼容资料与稳定 SDK 包；`./gradlew --version`；JDK 17 下 clean Debug/Release 构建和双变体 JVM 测试；一次缓存就绪后的离线 clean 构建；16 例跨端契约探针；APK 元数据确认 minSdk 26、targetSdk 37；API 37 ARM64 模拟器或等效设备安装、两次冷启动、Activity/进程和无崩溃 logcat。运行 `lintDebug` 并记录结果。
- 若 API 37 system image、AGP/Kotlin 插件或设备环境不可用，保留最小错误证据并回传，不降级目标、不把 API 35 历史证据冒充本轮通过。只提交本任务改动并推送 `Android`；执行窗口回传提交范围、完整命令、测试数量、设备信息、限制和复审请求。
- P1-03 完成后只申请复审，不进入 P2，不宣称 P1 或用户验收通过。

## 后续更新约定

每次阶段结束更新本文件的当前状态，保留决定理由及相关提交；较旧细节可从 Git 查阅，不无限追加聊天。执行者给出提交编号、测试证据、未完成项；审查者记录检查范围和发现。重要决定或切换服务前及时保存，即使任务尚未完成，也如实记录最后操作和未提交内容。


## D02 工具链兼容矩阵核查（历史任务，已完成记录）

由用户授权在 iOS 配置提交 `bef808b` 后，下一步交给 Terra 只核查稳定 SDK 与构建工具兼容组合，不修改应用代码或依赖。未知字段策略仍待产品决定。


## 2026-09-08 独立复核证据

- 实际 `55eff97` 只新增 D02 文档；后续 `5613f23`／`398fa43` 已有两次复审，本轮未把它们当作独立证据。AGP 9.4 参数核对正确；AGP 9.1.1 也支持 API 37.0，已消除“最低要求”的误解。
- `sdkmanager --list --channel=0` 退出 0，除交接列出的平台外还看到无 beta 后缀的 android-37.2；[清单节选](evidence/d02-sdk-list-20260908.txt) 已保存。37.1／37.2 的 AGP 映射未核实，未把包可见性写成项目兼容。
- 当前 API 35 工程 `assembleDebug test --rerun-tasks --console=plain` 独立通过，68 个任务执行，debug/release 各 21 项，无失败／错误／跳过。扩展 16 例跨端探针已运行，确认 4 种非法数字仍被 Android 接受；应用审查未通过。
- 本轮未做升级构建、CI、Windows、设备/iOS 全量复测；沿用已明确来源的 API 35 历史启动证据，不冒充本轮执行。
- 文档验证：`android-documentation.test.py` 11 项通过、`documentation.test.sh` 和 `repository-layout.test.sh` 通过，`git diff --check`／`git diff --cached --check` 通过。只提交本轮文档与相关验证。
