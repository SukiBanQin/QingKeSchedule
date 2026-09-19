# P3-06-R7 节次级联确认与设置保存错误弹窗（实施记录）

任务编号：**P3-06-R7**。范围：设置页节次变更对已有课程的级联确认、学期＋课程原子保存，以及设置页
保存错误统一为居中红色弹窗（P3-07-R1 之后新增的产品决定）。

- 代码基准：分支 `Android`，开始前 HEAD／`origin/Android` 为 `1cc9d343e62c29bcef7b43b3b6ffe16baa4b46c9`，
  工作区仅有用户保留的三份未提交文档改动（`handoff.md`、`implementation-plan.md`、`product-baseline.md`），
  本轮**未修改、未暂存、未纳入提交**这三份文件。
- 未改 iOS、Web、Room schema、共享 JSON schema／版本 1、A08／A10／A11，未合并 `main`。
- 证据目录：[`evidence/p3-06-r7-semester-cascade/`](evidence/p3-06-r7-semester-cascade/README.md)。

## 产品规则的实现位置

| 已确认规则 | 实现 |
| --- | --- |
| 草稿中可连续增删／调整节次，编辑时不逐次弹窗 | 只有 `saveSemester()`／`confirmSemesterCascade()` 触发评估，编辑路径不产生确认 |
| 顶部「保存」与底部「保存学期设置」共用同一校验、确认与保存状态机 | 两者都调用 `ScheduleViewModel.saveSemester()`；`QingKeAppActions.saveSemester` 只有一个入口，弹窗只有 `SemesterSaveDialogHost` 一处 |
| 删除／重排影响已有课程时只汇总弹一次红色破坏性确认，无「今日不再提醒」 | `SemesterCascadePlanner.plan(...)` 一次算出汇总，`SemesterSaveState.AwaitingCascade` 只持有一个 plan；弹窗内只有「返回修改」与「确认保存并级联」 |
| 汇总说明失效安排、部分失效课程、整门删除课程、仅重编号安排 | `SemesterCascadePlan.summaryLines`：整门删除行（含每门失效安排数）＋每条部分失效课程行（列出失效安排）＋每门仅重编号课程行 |
| 返回修改不写入，草稿／课程／持久化数据完整保留 | `dismissSemesterSave()` 只把状态置回 `Idle`；写入只发生在 `submitSemester` 内 |
| 确认后：删除直接引用被删节次的安排；删除起止跨被删节次的安排；仍有有效安排则保留课程；全部失效则删除整门课程 | `SemesterCascadePlanner`：`spansDeleted` 判定 `DIRECT_REFERENCE`／`SPANNED_RANGE`；`kept.isEmpty()` 时课程整门移除，否则 `course.copy(schedules = kept)` |
| 仅因前方节次被删除而产生的新编号按原节次身份重映射 | 映射表由 `SemesterDraft.periods.sourceNumber` → 新 `number` 构成；未重映射到的引用视为失效，不会误指向编号恰好相同的另一节 |
| 学期、节次、课程同一 Room 事务原子提交；校验失败／取消／存储失败／CancellationException 全部保持原状 | 新增 `ScheduleRepository.saveSemesterWithCourses(semester, courses)`，`RoomScheduleRepository` 用 `mutate {}`（`read` → `validate` → `database.withTransaction { write; beforeCommit }`）；`ScheduleAppState.saveSemesterWithCourses` 只在成功后发布 |
| 无课程受影响时直接保存，不弹确认 | `plan.hasImpact == false` → 直接 `submitSemester(..., plan = null, ...)` |
| 保留最低一节与既有非法时间／重叠／周数／课程周数校验，且不能强制绕过 | `SemesterDraft.validationIssues()` 与 `courseRangeIssues()` 先于级联计划；命中时进入 `SemesterSaveState.Blocked`，弹窗只有一个「返回修改」 |
| 设置页保存错误统一居中红色弹窗，不再依赖底部红字 | 删除了 `SemesterFormState.validationMessage` 与 `semester-validation-error` 底部提示；`DANGER / INVALID INPUT` 弹窗替代它 |
| 写入失败统一错误弹窗，关闭后保留草稿与可重试状态 | `ScheduleAppState.error` 走既有居中错误框；失败时级联状态回到 `AwaitingCascade` 可重试，草稿不动 |
| 保存中单飞：重复保存／确认只写一次；写入期间忽略返回修改；成功后才清除确认 | `SemesterSaveState.Writing` 作为唯一在飞标记（`evaluateSemesterSave` 早退），`dismissSemesterSave()` 在 `Writing` 时直接返回；成功后才置 `Idle` |
| 首次设置共享保存逻辑但不出现课程级联确认 | `previous == null` 时计划恒为无影响（首次设置没有已存课程），仍然写入学期与空课程列表 |

## 分层与文件

1. `domain/PeriodCascade.kt`（新增，纯 Kotlin，可独立测试）
   `PeriodIdentity`、`CascadeRemovalReason`、`RemovedSchedule`、`RemappedSchedule`、`CourseCascade`、
   `SemesterCascadePlan`（含 `hasImpact`／`summaryLines`）与 `SemesterCascadePlanner.plan(previous, courses, periods)`。
2. `draft/SemesterDraft.kt`
   删除旧 `impactIssues`（节次删除不再直接拒绝），改为 `courseRangeIssues`（仅保留“缩短总周数会让已有
   课程越界”这一不可继续错误）与 `periodIdentities()`；`markPersisted` 语义不变。
3. `persistence/ScheduleRepository.kt` + `RoomScheduleRepository.kt`
   新增原子入口 `saveSemesterWithCourses`（接口默认抛 `InconsistentStore`，与 `saveCourseAt` 一致，
   提醒其它实现显式支持）；Room 实现复用 `mutate`，未改 schema。
4. `state/ScheduleAppState.kt`
   新增 `saveSemesterWithCourses`，与其它写入共用互斥锁、`isSaving` 与错误发布。
5. `viewmodel/ScheduleViewModel.kt`
   `SemesterSaveState{Idle, Blocked, AwaitingCascade, Writing}`；`saveSemester()`／`confirmSemesterCascade()`／
   `dismissSemesterSave()`；`submitSemester` 快照 `persistedNumbers` 以保持“在飞期间编辑不算已持久化”。
6. `ui/QingKeApp.kt`
   `SemesterSaveDialogHost`（唯一弹窗宿主）＋ `SemesterCascadeDialog`（汇总可滚动，最多 250dp 后内部滚动，
   按钮固定在弹窗底部）；`TerminalDialog` 增加 `enabled` 参数（默认 `true`，其它调用点行为不变）。

失败／取消后确认框中显示的仍是发起写入时的计划；下一次点「确认保存并级联」会按当前草稿重新评估，
因此不会把过期计划写成新状态。

## 测试

- Debug／Release JVM：各 **151 tests、0 failures／errors／skipped**（P3-07-R1 基线 132，本轮 +19）。
  - `domain/PeriodCascadePlannerTest`（+10）：删除未被引用节次无影响；直接引用被删节次只删该安排；
    区间跨被删中间节次按 `SPANNED_RANGE` 删除而不重编号；前方删除后按身份重映射且不误指向同号节次；
    同一课程部分失效保留其余安排；全部失效整门删除；多课程一次汇总（三类行都在且无未受影响课程行）；
    首次设置与无变化无影响；仅新增节次／只改时间无影响；引用未知编号按失效处理。
  - `viewmodel/ScheduleViewModelTest`（+9 与改写）：未引用节次直接写入；被引用节次进入一次确认且
    返回修改不写入、草稿保留；多课程多节次只出现一个确认并一次写入；确认后草稿基准重定，二次保存
    无需确认；写入失败保留可重试确认与已存数据；CancellationException 不报普通错误且确认可重试；
    写入期间单飞并忽略返回修改；校验错误（学期名称、节次时间）优先且不可绕过；首次设置不出现级联确认。
  - `draft/DraftTest`：旧 `impactIssues` 断言改写为 `courseRangeIssues` + 计划断言（含“编号恰好相同
    也不能误指向”的回归检查）。
- Room／repository（设备，`RoomScheduleRepositoryTest` +3）：原子写入学期＋课程并在重开数据库后一致；
  注入失败与 `CancellationException` 全部回滚且重开仍为旧数据；原子入口也用于首次建学期，并拒绝仍在
  引用旧节次表的课程。
- Compose／API 37 connected：**120 tests、0 failures／errors／skipped**（基线 110，本轮 +10 含证据用例）。
  新增：顶部／底部入口共用同一级联弹窗且返回修改不写入；确认后今日页与周表立即使用新节次与级联结果
  （`week-item-5:0:0`、`week-period-1-start=08:55`、`week-period-3` 消失）；写入失败时错误框与可重试确认
  同时存在、关闭后草稿仍在、修复后重试成功；不可继续错误从两个入口弹出同一错误框；无错误时不出现
  任何弹窗且成功提示照常；汇总行过多时内部滚动（250dp 上限，按钮不被推离屏幕）。
- 构建与静态检查：`assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest`、`lintDebug`
  （0 errors／20 warnings，与上一轮相同）。
- 文档：`docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`、
  `repository-layout.test.sh` 通过。

## 设备证据

`docs/Android/evidence/p3-06-r7-semester-cascade/`：7 张同一次安装的 debug APK 截图（顶部错误弹窗、
底部同一错误弹窗、级联汇总确认框、返回修改后的草稿、确认后的周表与今日页、保存后的设置页）＋
`node-verification-20260919.txt`（Compose 语义节点 bounds、两个入口面板 bounds 完全一致）＋
`pixel-verification-20260919.txt`（危险红／青色／信号黄区域分类与弹窗内墨迹行带）＋逐张结论 README。
设备为本机 API 37（Android 17）ARM64 AVD，`wm size` 覆盖为 1080x2400、420dpi、font_scale 1.0、系统浅色。

## 已知限制

1. 真实 Room／DataStore 写入失败无法在生产 App 内注入，因此本目录没有“写入失败”截图，不伪造；该路径
   由设备测试与 Room 回滚测试断言，并在证据 README 中如实说明。
2. 关闭错误弹窗后没有自动滚动到问题字段（只保留草稿与可见表单）；按用户规则这是可选增强，不作为
   弹窗的替代。
3. 汇总框在受影响课程很多时只显示前 4 项并给出总数，具体到每门课程的失效安排仍逐条列出（部分失效
   与重编号课程各占一行）。
4. 失败后确认框内显示的是发起写入时的计划，重新确认才会按最新草稿重算。
5. 本轮证据为程序化核对，**用户观感验收尚未进行**。

## 准确状态

P3-06-R7 已实现并自测（JVM 151、设备 120、lint 0 errors、7 张截图证据齐备）；**Sol 独立复审与用户
验收均未进行**。按 AGENTS.md，本任务涉及破坏性数据与原子状态，不得以自测代替独立复审；A08／A10／A11、
整个 P3 与完整 App 仍未授权、未完成。
