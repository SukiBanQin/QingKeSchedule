# P3-07-R1 午休显示修正证据

本目录只包含 P3-07-R1 的证据，不覆盖 P3-07／A07 实施证据（`docs/Android/evidence/p3-07-a07-calendar/`）与更早的 R2—R6 历史证据。

- 任务：P3-07-R1 午休显示修正（用户确认的产品规则 1—4）。
- 代码基准：分支 `Android`，开始基准 `b9a0af9`（已推送 `origin/Android`）；本轮只新增本次提交，未改写历史、未强推、未合并 `main`。
- 证据时间：2026-09-19；设备：API 37 ARM64 AVD（`qingke-api37-r3-arm`，emulator-5554），`wm size 1080x2400`、`wm density 420`、浅色、字体比例 1.0。

## 规则与实现

1. **节次优先**：午休时间与任一节次重叠时，周课表不显示午休条（保持当前 iOS 基准）。冲突只影响展示，不修改课程、节次或停课规则。
2. **确认前一次红色警告**：确认、保存会与节次重叠的午休时间前，弹出一次红色冲突警告，复用课程 ADD 冲突框的 `TerminalDialog` 视觉（红色 danger 色调、代码行、状态标签、标题、说明与两个按钮），列明受影响节次，提供「返回修改」与「仍然保存」。「返回修改」不写入并把显示恢复到已存时间；「仍然保存」保留设置，并在「03 教学日历」区的午休时间下持续显示「午休与第 N 节重叠，节次优先：周课表不会显示午休条。」。不提供「今日不再提醒」。
3. **无冲突时必须显示**：范围合法且不与任何节次重叠时，无论每日有多少节都显示：午休在全部节次之前 → 矩阵顶部；位于两个节次之间 → 对应间隙；在全部节次之后 → 矩阵底部。
4. **范围校验不变**：开始不早于结束或时间格式非法时既不写入也不显示（原有行为）。

实现要点：`domain/AcademicCalendarRules.kt` 新增纯函数 `lunchBreakOverlappingPeriods(startTime, endTime, periods)`（相接不算重叠：节次结束正好等于午休开始、或节次开始正好等于午休结束都不计重叠），`WeekMatrixPresentation.makeBreak` 与 ViewModel 的冲突判定共用它，避免两处漂移；`ScheduleViewModel` 把 `setLunchBreakTimes` 改为 `requestLunchBreakTimes`（无冲突直接写入，冲突进入一次性确认状态）并新增 `confirmLunchBreakDespiteConflicts`／`dismissLunchBreakConfirmation`；UI 增加 `AcademicCalendarConflictHost` 与冲突提示文案，`TerminalDialog` 增加显式 `danger` 参数（默认值与既有调用完全一致）。R1 不处理节次删除后的课程级联（属 P3-06-R7）。

## 截图与检查结论

截图由真实 debug 入口（`com.qingke.schedule/.MainActivity`）经 `adb shell input tap` 逐级操作后 `screencap` 取得；无冲突场景用「先设结束时间」的顺序避免设置过程中产生中间冲突。每张都在同一状态用 `uiautomator dump` 记录可见节点与 bounds（`node-verification-20260919.txt`）。本窗口没有图形界面，逐张检查方式为像素分类渲染（浅／深、青、信号黄、危险红与墨迹占比）＋节点文本／内容描述与 bounds 核对，不是人眼观感确认。

| 截图 | 状态 | 检查结论 |
| --- | --- | --- |
| `r1-lunch-break-gap-light.png` | 周课表，默认午休 11:40–14:00（位于第 4 与第 5 节之间） | 午休条 `午休，11:40到14:00` bounds=[74,1503][1006,1582]；节次行 10:55（第 4 节）在 y=1414 之上、14:00（第 5 节）在 y=1672 之下，正好落在间隙；青色条带占 3.1% 墨迹 |
| `r1-lunch-break-top-light.png` | 周课表，午休 06:40–07:00（在全部节次之前） | 午休条 bounds=[74,1229][1006,1308] 位于第 1 节 08:00（y=1398）之上，14:00 行在 y=2112；青色条带 2.7% 墨迹。该场景在修正前会被隐藏 |
| `r1-lunch-break-bottom-light.png` | 周课表，午休 21:40–22:00（在全部节次之后） | 午休条 bounds=[74,1495][1006,1574] 位于最后一节 19:55（y=1406）之下；青色条带 2.8% 墨迹。该场景正是用户报告的缺陷（只剩午休前节次时不显示），现已显示在矩阵底部 |
| `r1-lunch-conflict-dialog-light.png` | 设置页，午休 08:40–14:00 与第 1—4 节重叠时的确认框 | 红色 `WARNING / CONFLICT`／`PERIOD OVERLAP`、标题「午休与节次重叠」、说明「与第 1、2、3、4 节时间重叠。节次优先：仍然保存后周课表不会显示该午休条。」与「返回修改」「仍然保存」两个按钮均在屏内；像素分类含 2.9% 危险红、无信号黄，确认这是红色警告框 |
| `r1-lunch-conflict-note-light.png` | 选择「仍然保存」后的设置页 | 午休时间保留为 08:40–14:00，并在时间下方持续显示「午休与第 1、2、3、4 节重叠，节次优先：周课表不会显示午休条。」（危险红 0.2% 墨迹），明确周课表不会显示该午休 |
| `r1-lunch-conflict-hidden-week-light.png` | 同一设置下的周课表 | 青色墨迹占比 0.0%：全表没有午休条；节次行 08:00／14:00／19:55 仍在（2.9% 深色墨迹），证明「节次优先」只隐藏午休条，不改动节次 |

## 验证与限制

完整命令、结果与设备记录见 `host-and-device-verification-20260919.txt`。摘要：Debug／Release JVM 各 127 tests、0 failures／errors／skipped；`connectedDebugAndroidTest` 107 tests、0 failures／errors／skipped；`lintDebug` 0 errors、20 warnings；`assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 通过；文档验证 71 tests OK、`documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

Sol 已完成首轮独立复审，结论为**未通过**，详见 [`p3-07-r1-review.md`](../../p3-07-r1-review.md)。上述顶部／间隙／底部、重叠隐藏、正式设置红色确认成功主路径和截图证据均已核实；仍须修正：（1）首次设置及当前节次草稿未参与冲突确认；（2）「仍然保存」写入失败时界面保留未写入候选时间且确认已消失。

限制：用户视觉验收（含红色警告框与提示文案的观感）尚未进行；`TerminalDialog` 的按钮沿用既有 46dp 最小高度（与已验收的 ADD 冲突框一致），本轮未改动该共享视觉；切换标签后日历选择态仍会回到默认值（P3-07 已知交互限制，未在 R1 处理）。
