# P3-07-R1 午休显示修正证据

本目录只包含 P3-07-R1 的证据，不覆盖 P3-07／A07 实施证据（`docs/Android/evidence/p3-07-a07-calendar/`）与更早的 R2—R6 历史证据。首轮独立复审未通过后，本目录已按修正后的构建重新采集。

- 任务：P3-07-R1 午休显示修正（用户确认的产品规则 1—4）与首轮复审两项修正。
- 代码基准：分支 `Android`，开始基准 `546b61b`（首轮复审记录，已推送 `origin/Android`）；实现提交 `1c5c05d`；本轮只新增修正提交，未改写历史、未强推、未合并 `main`。
- 证据时间：2026-09-19；设备：API 37 ARM64 AVD（`qingke-api37-r3-arm`，emulator-5554），`wm size 1080x2400`、`wm density 420`、浅色、字体比例 1.0。

## 规则与实现

1. **节次优先**：午休时间与任一节次重叠时，周课表不显示午休条（保持当前 iOS 基准）。冲突只影响展示，不修改课程、节次或停课规则。
2. **确认前一次红色警告**：确认、保存会与节次重叠的午休时间前，弹出一次红色冲突警告，复用课程 ADD 冲突框的 `TerminalDialog` 视觉，列明受影响节次，提供「返回修改」与「仍然保存」。「返回修改」不写入并把显示恢复到已存时间；「仍然保存」在写入成功后才完成确认。不提供「今日不再提醒」。
3. **无冲突时必须显示**：范围合法且不与任何节次重叠时，无论每日有多少节都显示：午休在全部节次之前 → 矩阵顶部；位于两个节次之间 → 对应间隙；在全部节次之后 → 矩阵底部。
4. **范围校验不变**：开始不早于结束或时间格式非法时既不写入也不显示（原有行为）。

实现要点：`domain/AcademicCalendarRules.kt` 的纯函数 `lunchBreakOverlappingPeriods(startTime, endTime, periods)`（相接不算重叠）由周表隐藏判定、设置页提示与 ViewModel 冲突判定共用；`WeekMatrixPresentation.makeBreak` 在无重叠时允许 `insertionRow` 为 0、中间行或 `periods.size`。

### 首轮复审修正

- **修正 1（冲突判断纳入当前可见节次配置）**：`ScheduleViewModel.lunchBreakConflictFor` 同时检查**已持久化学期节次**与**当前 `SemesterDraft` 草稿节次**（草稿时间规范为 HH:mm），任一来源重叠即要求一次红色确认；`LunchBreakConflict` 记录 `persistedPeriodNumbers` 与 `draftPeriodNumbers`，消息据此区分「已保存节次」（周表仍会隐藏午休条）与「尚未保存的当前节次设置」（保存学期设置后才会隐藏）。首次设置（无持久化学期）与正式设置先改节次时间再设午休都会弹框；周表仍只按持久化节次展示，边界未被破坏。
- **修正 2（仍然保存的写入失败收口）**：`confirmLunchBreakDespiteConflicts` 改为在 `appState.updatePreferences` **返回成功后才清除** `LunchBreakConflict`；失败或取消时保留待确认状态（可重试）并沿用既有错误反馈，页面不会把未写入的候选时间显示成已保存。写入进行中为单飞（重复点按只写一次），期间忽略「返回修改」，避免状态互串。

## 截图与检查结论

截图由真实 debug 入口（`com.qingke.schedule/.MainActivity`）经 `adb shell input tap` 逐级操作后 `screencap` 取得；无冲突场景用「先设结束时间」的顺序避免设置过程中产生中间冲突，并在切换周表后轮询确认已存时间生效才截图。每张都在同一状态用 `uiautomator dump` 记录可见节点与 bounds（`node-verification-20260919.txt`）。本窗口没有图形界面，逐张检查方式为像素分类渲染（浅／深、青、信号黄、危险红与墨迹占比）＋节点文本／内容描述与 bounds 核对，不是人眼观感确认。

| 截图 | 状态 | 检查结论 |
| --- | --- | --- |
| `r1c-lunch-conflict-first-boot-light.png` | 首次设置（尚无持久化学期），把午休开始时间改为 08:40 后 | 红色 `WARNING / CONFLICT`／`PERIOD OVERLAP` 与「与当前节次设置的第 1、2、3、4 节时间重叠，这些节次时间尚未保存到学期设置。节次优先：周课表按已保存的节次时间判断，保存学期设置后才会隐藏该午休条。」；屏内同时可见 `开始时间，08:40`／`结束时间，14:00` 候选与「返回修改」「仍然保存」；像素含 2.9% 危险红、0.4% 信号黄。该场景在修正前完全不弹框 |
| `r1-lunch-break-gap-light.png` | 周课表，默认午休 11:40–14:00（位于第 4 与第 5 节之间） | 午休条 `午休，11:40到14:00` bounds=[74,1507][1006,1586]；节次行 10:55 在 y=1418 之上、14:00 在 y=1676 之下，正好落在间隙（青色 2.7%） |
| `r1-lunch-break-top-light.png` | 周课表，午休 06:40–07:00（在全部节次之前） | 午休条 bounds=[74,1229][1006,1308] 位于第 1 节 08:00（y=1398）之上（青色 2.8%） |
| `r1-lunch-break-bottom-light.png` | 周课表，午休 21:40–22:00（在全部节次之后） | 午休条 bounds=[74,1495][1006,1574] 位于最后一节 19:55（y=1406）之下（青色 3.1%）；该场景正是用户报告的缺陷 |
| `r1-lunch-conflict-dialog-light.png` | 正式设置，午休 08:40–09:00 与第 1、2 节重叠时的确认框 | 红色确认框 + 「与第 1、2 节时间重叠。节次优先：仍然保存后周课表不会显示该午休条。」+「返回修改」「仍然保存」（危险红 2.9%、无信号黄）；草稿与已存节次一致时使用精简措辞 |
| `r1-lunch-conflict-note-light.png` | 选择「仍然保存」后的设置页 | 午休时间保留为 08:40–09:00，并持续显示「午休与第 1、2 节重叠，节次优先：周课表不会显示午休条。」（危险红 0.2%） |
| `r1-lunch-conflict-hidden-week-light.png` | 同一设置下的周课表 | 青色墨迹 0.1%（无午休条）；节次行 08:00／14:00／19:55 仍在，证明「节次优先」只隐藏午休条，不改动节次 |

写入失败路径无法在真实 App 上注入 DataStore 故障，因此没有对应截图；该路径由设备测试断言（错误框出现、确认框保留可重试、已存值不变、`isSaving` 为 false、修复故障后重试成功）记录在设备测试报告中。

## 验证与限制

完整命令、结果与设备记录见 `host-and-device-verification-20260919.txt`。摘要：Debug／Release JVM 各 132 tests、0 failures／errors／skipped；`connectedDebugAndroidTest` 110 tests、0 failures／errors／skipped；`lintDebug` 0 errors、20 warnings；`assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 通过；文档验证 71 tests OK、`documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

Sol 的首轮独立复审结论为**未通过**，列出的两项修正已在本轮完成并自测，详见 [`p3-07-r1-review.md`](../../p3-07-r1-review.md)；**复审结果与用户视觉验收仍待进行**。

限制：用户视觉验收（含红色警告框与提示文案的观感）尚未进行；`TerminalDialog` 的按钮沿用既有 46dp 最小高度（与已验收的 ADD 冲突框一致），本轮未改动该共享视觉；冲突判定在草稿与已持久化节次不一致时按两者并集提示，周表仍按持久化数据显示；首次设置阶段没有已持久化节次，因此提示文案说明「保存学期设置后才会隐藏」；切换标签后日历选择态仍会回到默认值（P3-07 已知交互限制，未在 R1 处理）。
