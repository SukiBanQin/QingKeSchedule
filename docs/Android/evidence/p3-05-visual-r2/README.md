# P3-05／A03 周课表视觉返修 R2 证据（2026-09-18）

## 起因：Sol 对 R1（`af825a1`）的独立审查

R1 未通过技术复审，需集中修正三点：教学周面板固定高度导致 130% 下 `ODD WEEK` 消失；教学周面板中部按钮的可用性遗漏 `followsCurrentWeek`；页面标题、教学周面板与日期条之间缺少 iOS 的分组间距。本轮即针对这三点，未扩大范围。

## 范围与基准

- 仓库：/Users/takagisan/课表软件，分支 `Android`
- 开始基准：`af825a1`（`fix(android): align r1 week schedule visuals`），本地 HEAD 与 `origin/Android` 一致、工作区干净
- 只修改：`Android/app/src/main/java/com/qingke/schedule/ui/WeekScheduleScreen.kt`、`Android/app/src/androidTest/java/com/qingke/schedule/ui/QingKeAppTest.kt`、`docs/Android/handoff.md`、`docs/Android/evidence/p3-05-visual-r1/README.md`（修正旧结论）与本目录
- 未修改：iOS、Web、Room/schema、DataStore、共享协议、业务规则、设置、通知、导入导出、P3-04、其他阶段与 `main`
- 继续复用 `WeekSchedulePresentation`、`WeekMatrixPresentation`、`ScheduleDisplayText`、`AcademicCalendarPreferences` 与既有 `openAddCourse()`／`openCourseAt()`；testTag 全部保留，**没有任何重命名**

## 修正 1：130% 下 `ODD WEEK` 被挤出面板

根因（本轮实测确认，非猜测）：R1 的教学周面板对承载三行文字的 `Row` 施加了高度约束。用 `Modifier.height(64.dp)` 时，130% 下三行文字所需高度大于 64dp，`Column` 把第三行压缩到剩余空间（实测 `week-parity` 仅 6.48dp 行盒），因此 `ODD WEEK` 不渲染（像素计数 0）。用 `Modifier.heightIn(min = 64.dp)` 同样会压缩第三行——加最小高度并不能阻止行盒被挤掉。

修正：面板 `Row` 改为纯内容高度，中间 `Column` 用 `padding(vertical = 6.dp)`；iOS 的 64dp 视觉下限改由左右箭头控件的 `Modifier.heightIn(min = 64.dp)` 提供，因此浅／深色 100% 下依然是 64dp，130% 下随字号增长到约 77dp。三行文字统一补上显式 `lineHeight`（14sp／21sp／12sp）与 `maxLines = 1`，避免行盒依赖系统字体度量。

## 修正 2：中部按钮遗漏 `followsCurrentWeek`

`canReturn` 由 `currentWeek != null && currentWeek != week` 改为 `currentWeek != null && (currentWeek != week || !followsCurrentWeek)`：手动切走再用箭头回到本周时，周号虽等于本周但自动跟随仍关闭，此时中部按钮必须可用，点击后恢复自动跟随。手动浏览（`followsCurrentWeek = false`）仍不会被时钟刷新重置。

## 修正 3：恢复 iOS 分组间距

对照只读 iOS `WeekScheduleView.swift` 的 `LazyVStack(spacing: 14)` 与 `.padding(.top, 12)`：滚动内容顶部改为 12dp，页面标题→教学周面板、教学周面板→日期条各补 14dp 间距（矩阵分区标题沿用原有 14dp 上间距、标题→矩阵保持 10dp）。七日一屏、可选日期与点击路由均未改变。

## 测试

- 新增 `weekControlsKeepAllTextVisibleAndSpacedAcrossFontScales`：在 100% 与 130% 下逐一断言学期名／教学周／单双周三行**实际行盒高度**（`getUnclippedBoundsInRoot`，分别不低于 11/17/9dp × 字号系数）、三行都完整落在面板边界内、面板相对页面标题与日期条的间距 ≥ 12dp、130% 下面板高度 > 66dp；并用像素断言证明 `ODD WEEK` 真的被绘制在面板中部（青色像素计数 ≥ 20，避开左右箭头区域）。
- 新增 `weekControlsRestoreAutoFollowAndManualBrowsingSurvivesClockRefresh`：本周→下一周→用箭头回到本周→中部可点击→点击后恢复跟随→时钟进入下一教学周时自动更新周号；随后手动连按下一周到第 04 周，再把时钟推进到第 05 周，断言仍停留在 04（手动浏览不被刷新重置）。
- 既有 5 个周页用例未改名、继续通过。testTag 无重命名，因此没有需要同步的定向测试。

### 负向对照（证明测试真的会失败）

把面板 Row 临时改回 R1 的 `Modifier.height(64.dp)` 并重跑新用例：130% 下失败，报错正是 `fontScale=1.3 ODD/EVEN WEEK text must be painted inside the panel, cyanPixels=0`，复现了复审指出的现象；恢复内容高度后同一用例通过。这说明新测试检查的是实际渲染与布局，而不是“语义文本存在”。

## 一轮目视核对后的实现修正（过程记录）

首次实现曾用 `heightIn(min = 64.dp)` 保证 100% 下的 64dp 下限；像素／行盒断言在 130% 下暴露出它仍会压缩第三行（`week-parity` 行盒 6.48dp 而非约 13.5dp），据此才改为“内容高度 + 箭头提供 64dp 下限”。该过程说明单靠断言语义文本或 100% 截图都不足以发现问题。

### 顺带修好的一项测试缺陷：R1 像素断言写死了浅色主题

把设备切到深色主题后再跑完整套件，R1 的两个像素用例失败：`weekMatrixDrawsGridLinesAndShowsLunchBreakOnlyWhenEnabled` 报 `must draw vertical column lines, lineColumns=0`，`weekDateStripSelectionMovesSignalUnderlineAndManifestTitleDropsIsoDate` 报 `day strip must draw a top rule, top=90 middle=18`。原因是这两处把“分隔线”实现成“比底色更暗”（`<= baseline - 12`），而深色主题下分隔线比底色更亮。已改为与主题无关的亮度偏差判定（`|sample - baseline| >= 12`，取样仍限 ±3px 以容忍取整），浅／深色下都成立。此项属于 R1 遗留的测试缺陷，不是产品行为问题。

## 主机验证

- `./gradlew testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon` → BUILD SUCCESSFUL
- Debug／Release JVM 各 **92 tests、0 failures／0 errors／0 skipped**；`lintDebug` **0 errors、20 warnings**
- 文档验证：`python3 docs/tests/android-documentation.test.py` 70 tests OK、`bash docs/tests/documentation.test.sh` passed、`bash docs/tests/repository-layout.test.sh` passed、`git diff --check` 通过

## 设备验证

- API 37 ARM64 `emulator-5556`（1080x2400／420dpi），完整 `connectedDebugAndroidTest --no-daemon` → BUILD SUCCESSFUL，`74 tests、0 failures、0 errors、0 skipped`（R1 的 72 + 本轮 2 项），结果 XML 见本目录 connected-debug-android-test-result.xml
- 定向 `am instrument` 复跑 7 个周页用例 `OK (7 tests)`；负向对照与恢复后的单用例分别 `Failures: 1` 与 `OK (1 test)`
- 主题／字号独立性：在 **dark + font_scale 1.3** 设备状态下复跑 7 个周页用例 `OK (7 tests)`，在 **light + font_scale 1.0** 下复跑像素用例 `OK (3 tests)`
- 完整套件在本轮共执行 3 次：第 1 次 light 100% 74/0 通过；第 2 次因设备停留在 dark + 1.3 暴露上述 R1 像素断言缺陷而 2 项失败；修正后第 3 次 light 100% 74/0 通过，并另在 dark + 1.3 复跑周页用例通过。最终证据 XML 为第 3 次结果

## 截图清单（真实 debug 入口，同一数据结构）

数据：全新安装后创建学期「2026 秋季学期」（2026-09-18 起 18 周、默认 10 节），新增课程 `Advanced Mathematics`（教师 Zhang，周五第 1 节，每周 1–18 周）。

| 文件 | 状态 | 目视核对结果 |
| --- | --- | --- |
| p3-05-r2-week-light-100-api37.png | light／100% | 教学周面板 64dp：学期名、`第 01 教学周`、`ODD WEEK` 三行完整；面板与标题／日期条间距约 14dp；日期条上下线、竖分隔线、五 18 反相与黄下划线正常 |
| p3-05-r2-week-light-130-api37.png | light／130% | `ODD WEEK` 完整可见（R1 缺陷已修复）；面板随字号增高且三行不重叠、不裁切；标题、面板、日期条间距保持 |
| p3-05-r2-week-light-130-manifest-api37.png | light／130% | 大字号日清单：`05 周五／1 ENTRIES`，卡片 08:00／08:45、`第 1 节`、课程名换成两行、Zhang、右箭头均完整可读，无 ISO 日期 |
| p3-05-r2-week-dark-100-api37.png | dark／100% | 深色面 100%：反相标签、深色面板三行、日期条线框与黄下划线、矩阵线框、青色午休 |
| p3-05-r2-week-dark-130-api37.png | dark／130% | 深色面 130%：`ODD WEEK` 完整可见；面板增高后与日期条间距仍存在 |

限制：单设备 1080x2400，未覆盖小屏与横屏；悬浮 ADD 按设计覆盖其下方内容（与 iOS 一致）；未做跨端同数据像素对照，仅按 iOS 结构与 token 对齐。

## 已知差异与未验证

- iOS 中部按钮只恢复周（`selectedWeek = currentWeek`），本轮 Android 仍沿用 P3-05 首版行为：点击中部同时把选中日恢复为今天并重新开启日跟随。该差异本轮未改，仅记录，待用户决定是否对齐。
- 未重新核对 iOS 远端最新提交（仍以 `fc3ddfb` 为只读参照）；`git ls-remote` 在本机 SSH host key 校验受限，推送结果以实际 `git push` 返回为准
- 用户视觉验收尚未进行；本轮只完成实现、测试、设备截图与证据
