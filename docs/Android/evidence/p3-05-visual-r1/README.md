# P3-05／A03 周课表视觉返修 R1 证据（2026-09-18）

> **2026-09-18 修正说明（保留历史原貌）**：R1 曾把 130% 大字号截图写成“教学周面板不裁切”，该结论有误——固定 `height(64.dp)` 在 130% 下把 `ODD WEEK` 挤出面板；同时页面标题、教学周面板与日期条之间缺少 iOS 的分组间距。两点已在 R2 修正，并补充了会真正失败的像素级布局测试。修正后证据见 [R2 证据](../p3-05-visual-r2/README.md)。

## 范围与基准

- 仓库：/Users/takagisan/课表软件，分支 `Android`
- 开始基准：`2299e0b`（`docs(android): hand off p3-05 visual r1`），当时本地 HEAD 与 `origin/Android` 一致，工作区干净
- 本轮实现提交与推送结果见 docs/Android/handoff.md 顶部交接节与交付消息（避免在文件中自引用尚未产生的提交）
- 只修改：`Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt`、`Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt`
- 未修改：iOS、Web、Room/schema、DataStore、共享协议、业务规则、设置、通知、导入导出、P3-04 既有视觉、`main`
- 继续复用 `WeekSchedulePresentation`、`WeekMatrixPresentation`、`ScheduleDisplayText`、`courseColor`、`TodayVisualSpec`、`openAddCourse()`／`openCourseAt()` 与 `AcademicCalendarPreferences`，未复制新的领域规则

## 七项视觉修正落点

1. `SCHEDULE :// WEEK MATRIX` 改为反相面（浅色 #091113／深色 #182427）黑底 + 白色粗体 monospace 标签。
2. 顶部标题：左侧 40sp 粗体「课表」；右侧 `WEEK` + 两位教学周数字，数字随选中教学周变化。
3. 教学周控件：白底（深色主题为深色面）1dp 细边框面板，含左右箭头、竖分隔线、学期名、`第 XX 教学周`、`ODD WEEK/EVEN WEEK`；中部可点击返回当前周；删除原独立「返回当前周」整行。
4. 日期条：上下 1dp 横线 + 每日竖分隔线；选中日期保留反相背景，日期下方 3dp 黄色下划线；停课日指示为危险色，与 iOS 的 weekdayIndicatorColor 一致。
5. 周视图标题：`05 周视图` + 右侧动态 `MON–SUN / N PERIODS`（N 取真实节次数，复用 `ScheduleDisplayText.weekMatrixSummary`）。
6. 矩阵：44dp 时间列 + 7 等分列；8 条竖线（首列 borderStrong）、节次数 + 1 条横线；表头 38dp、行高 68dp、午休行 30dp；`TIME`、星期名、节次编号与开始时间均为深色粗体居中；午休仅在设置启用且展示模型产生 break 时显示，青色底 + 上下细边框 + 左侧标题／分隔线／时间。
7. 日清单：标题改为 `编号 + 周几` 与 `N ENTRIES`／`OFF DAY`／`FOLLOW / 周X`，删除 ISO 日期；课程卡片最小高度 94dp，含旋转序号、起止时间列（与今日页一致的 condensed 字体）、竖分隔线、节次或冲突标记、课程名、教室／教师与右箭头，点击仍路由到 `openCourseAt(courseIndex)`。

## testTag 兼容

- 保留且未改名：`week-brand`、`week-title`、`week-current`、`week-date-strip`、`week-day-*`、`week-semester-name`、`week-previous`、`week-next`、`week-brand-header`、`week-schedule`、`week-refresh-container`、`week-refresh-status`、`week-matrix`、`week-matrix-header`、`week-time-header`、`week-column-header-*`、`week-period-*`、`week-lunch-break`、`week-item-*`、`week-manifest-header`、`selected-day-title`、`week-empty`、`week-end-marker`
- 本轮新增（仅新增，未删除或重命名）：`week-controls`、`week-teaching-week`、`week-parity`、`week-matrix-header-index`、`week-view-title`、`week-matrix-summary`、`week-manifest-detail`、`week-matrix-canvas`、`week-lunch-break-title`
- 无标签重命名映射，因此没有需要同步改名的定向测试

## 新增 Compose 契约测试（QingKeAppTest）

- `weekHeaderControlsAndSectionTitlesFollowIosStructure`：黑底白字品牌标签、动态两位周号与 `第 01/02 教学周`、`ODD/EVEN WEEK`、首周左箭头禁用、`week-current` 返回当前周与禁用态、`05 周视图` 与 `MON–SUN / 4 PERIODS`。
- `weekDateStripSelectionMovesSignalUnderlineAndManifestTitleDropsIsoDate`：日期条的上下横线（像素亮度断言）、选中日期下黄色下划线随选择从周一到周五右移（像素重心断言）、选中／未选中无障碍语义、标题改为周五且无 ISO 日期、`0 ENTRIES`。
- `weekMatrixDrawsGridLinesAndShowsLunchBreakOnlyWhenEnabled`：矩阵 8 条竖线与 5 条横线的像素位置断言、午休关闭时不渲染 `week-lunch-break`、开启时渲染并验证青色底色与标题 `午休`。
- `weekDayManifestOmitsIsoDateAndRoutesCourseClicks`：标题为 `周一`／`4 ENTRIES`、整屏不存在 ISO 日期 `2026-08-31`、`week-list-0-0` 与 `week-list-2-0` 点击分别路由到课程 0 与 2。
- 既有 `weekScheduleRendersMatrixHeadersAndRoutesAddAndCourseSource` 保持通过（未改名，未改断言）。

## 主机侧验证

命令（Android 目录，JAVA_HOME 指向 Android Studio JBR，GRADLE_USER_HOME／ANDROID_USER_HOME／TMPDIR 指向可写临时目录）：

- `./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --console=plain --no-daemon` → BUILD SUCCESSFUL
- Debug JVM 单元测试 92 tests、0 failures／0 errors／0 skipped
- Release JVM 单元测试 92 tests、0 failures／0 errors／0 skipped
- `lintDebug` 0 errors、20 warnings（与 P3-05 首版基线一致，未新增告警类别）
- Debug／Release／AndroidTest APK 均构建成功
- 文档验证：`python3 docs/tests/android-documentation.test.py` 70 tests OK、`bash docs/tests/documentation.test.sh` passed、`bash docs/tests/repository-layout.test.sh` passed、`git diff --check` 通过

## API 37 设备验证

- 设备：`emulator-5556`，API 37.0（google_apis arm64-v8a），1080x2400／420dpi，`font_scale=1.0` 与 `1.3`，light／dark 均实测
- 完整套件：`./gradlew connectedDebugAndroidTest --no-daemon` → BUILD SUCCESSFUL，`72 tests、0 failures、0 errors、0 skipped`，结果 XML 见本目录 connected-debug-android-test-result.xml
- 说明：本轮未改动任何 P3-04 代码或测试。上一轮交接记录的两个既有失败（`chooserProfileAndClosedPickersUseCompactIosAlignedStructureAcrossFontScales`、`r5TerminalColorModesDropdownsAndRepeatSelectorKeepOneEditorState`）在本轮同一 AVD 上通过；该差异未经专门复现分析，记录为观察结果，不声称已修复。
- 定向复跑：`am instrument` 运行 4 个新增周页用例 + 既有周页用例 → `OK (5 tests)`

## 截图清单（生产 debug 入口，真实数据）

数据：全新安装后创建学期「2026 秋季学期」（2026-09-18 起 18 周、默认 10 节），并新增课程 `Advanced Mathematics`（教师 Zhang，周五第 1 节，默认每周 1–18 周）。

| 文件 | 状态 | 目视核对结果 |
| --- | --- | --- |
| p3-05-r1-week-light-api37.png | light／100% | 黑底白字标签、粗体课表 + WEEK 01、白底教学周面板、日期条上下线与竖分隔线 + 五 18 反相与黄下划线、05 周视图 + MON–SUN / 10 PERIODS、矩阵线框与黑色粗体表头、青色午休行 |
| p3-05-r1-week-light-manifest-api37.png | light／100% | 日清单标题 `05 周五` + `1 ENTRIES`（无 ISO 日期）、卡片 01 序号 + 08:00／08:45 + 第 1 节 + 课程名 + Zhang + 右箭头、END OF MANIFEST |
| p3-05-r1-week-dark-api37.png | dark／100% | 深色面下同样成立：反相标签、面板、日期条线框与黄下划线、矩阵线框、青色午休 |
| p3-05-r1-week-dark-manifest-api37.png | dark／100% | 深色日清单卡片与 `05 周五／1 ENTRIES`，矩阵下半区行线与午休行 |
| p3-05-r1-week-dark-font130-api37.png | dark／130% | **原结论已被推翻**：该图当时被写成“教学周面板不裁切”，但 2026-09-18 的 R2 独立复审指出固定 `height(64.dp)` 使 `ODD WEEK` 在 130% 下被挤出面板（图中确实看不到该行）。历史图保留在此，作为 R1 漏检的证据；修正后的 130% 截图见 [R2 证据](../p3-05-visual-r2/README.md)。其余结论（标题、日期条、矩阵表头、大字号行高）仍成立 |

限制：截图为 Pixel 尺寸 1080x2400 的单设备结果，未覆盖小屏与横屏；未做跨端同数据像素对照，仅按 iOS 结构与 token 对齐；用户视觉验收尚未进行。

## 未验证与风险

- 未重新核对 iOS 远端最新提交；iOS 参照仍为交接记录的 `fc3ddfb`，且本轮未修改 iOS 工作区
- `git ls-remote` 因本机 SSH host key 校验失败未能实时查询远端，推送结果以实际 `git push` 返回为准并在交付消息中记录
- 本轮为视觉返修，未改变任何领域规则；矩阵课程块沿用 iOS 的单行截断策略，窄列下课程名会省略
