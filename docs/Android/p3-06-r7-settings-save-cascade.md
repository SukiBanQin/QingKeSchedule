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

- Debug／Release JVM：各 **162 tests、0 failures／errors／skipped**（P3-07-R1 基线 132；R7 +19、R1 修正 +11）。
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
- Compose／API 37 connected：**121 tests、0 failures／errors／skipped**（基线 110；R7 +10、R1 修正 +1 反序编号回归与证据用例）。
  新增：顶部／底部入口共用同一级联弹窗且返回修改不写入；确认后今日页与周表立即使用新节次与级联结果
  （`week-item-5:0:0`、`week-period-1-start=08:55`、`week-period-3` 消失）；写入失败时错误框与可重试确认
  同时存在、关闭后草稿仍在、修复后重试成功；不可继续错误从两个入口弹出同一错误框；无错误时不出现
  任何弹窗且成功提示照常；汇总行过多时内部滚动（250dp 上限，按钮不被推离屏幕）。
- 构建与静态检查：`assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest`、`lintDebug`
  （0 errors／20 warnings，与上一轮相同）。
- 文档：`docs/tests/android-documentation.test.py` 71 tests OK、`documentation.test.sh`、
  `repository-layout.test.sh` 通过。

## 设备证据

`docs/Android/evidence/p3-06-r7-semester-cascade/`：8 张同一次安装的 debug APK 截图（顶部错误弹窗、
底部同一错误弹窗、级联汇总确认框、返回修改后的草稿、确认后的周表与今日页、保存后的设置页）＋
`node-verification-20260919.txt`（Compose 语义节点 bounds、两个入口面板 bounds 完全一致）＋
`pixel-verification-20260919.txt`（危险红／青色／信号黄区域分类与弹窗内墨迹行带）＋逐张结论 README。
设备为本机 API 37（Android 17）ARM64 AVD，`wm size` 覆盖为 1080x2400、420dpi、font_scale 1.0、系统浅色。

## R1 修正（独立复审两项问题）

复审发现两项阻断缺口，本轮按同一 DeepSeek 执行窗口修正，仍不改 iOS／Web／Room schema／共享协议／`main`。

### 修正 1：反序／非连续节次编号下不可表示的身份映射

- 根因：`SemesterCascadePlanner` 只把两端点各自映射后直接落库。版本 1 与既有校验明确接受反序编号
  （例如持久化行序 9、4、20），删除第 20 节后剩余行重编号为 9→1、4→2，安排 4–9 就会生成 2–1，
  `ScheduleValidator` 报「请选择有效的起止节次」。
- 处理：`SemesterCascadePlanner` 由 `plan(...)` 改为返回密封结果
  `SemesterCascadeEvaluation{Plan|Blocked}`；`Blocked` **不携带任何可写计划**，因此不可能被误写。
  新增 `UnmappableSchedule` 与 `isRepresentable` 判定：只有当旧范围覆盖的持久化节次都落在新范围内、
  且新范围不包含该安排从未引用过的其他持久化节次时才允许重映射。满足不了（含 2–1 这类倒置、以及
  “重编号后会把无关节次吞进范围”的稀疏情形）即返回 `Blocked`，消息形如
  「「数学」周一 第4-9节 1-18周 的安排无法按新节次顺序安全重映射，请先在课程编辑中调整该课程。」，
  由 ViewModel 进入既有的红色错误弹窗（`semester-save-error`），不写入、不删除课程、不清除草稿。
- 未采用「静默多删一个安排」的替代方案：那需要新的产品授权，本轮按保守处理。
- 顺带修掉一个隐蔽缺陷：原判定的“缺失编号视为通过”写成 `?: true` 后经 `in` 变成布尔比较，
  会把任何含缺失编号的范围判成不可表示（反序学期即使只改名也会被误拦）；现改为显式 `return@all true`，
  并补测试固定“普通改名保留 9、4、20、不误触发”。

### 修正 2：封闭 `confirmSemesterCascade` 的确认旁路

- 只有当前状态是 `SemesterSaveState.AwaitingCascade` 时才可能执行确认；`Idle`／`Blocked`／`Writing`
  以及弹窗关闭后的过期确认都是无操作（先做类型取用，取不到直接 return）。
- 确认时用当前草稿与当前已存数据重新做校验与计划：命中不可继续错误 → `Blocked` 且不写入；重算出的
  破坏性计划与用户刚看到并确认的 `AwaitingCascade.plan` 不一致 → 更新为新的 `AwaitingCascade`
  重新展示摘要，本次点击不写入，必须再次确认；只有重算计划与已展示计划结构相等时才进入 `Writing`。
- 失败或 `CancellationException` 后仍回到可重试的 `AwaitingCascade`，再次确认会重新走同一一致性检查。
- 边界（有意为之并已测试）：确认时若重算结果已经**没有**破坏性影响（例如已存课程在别处被清空），
  本次点击也不写入，只清除过期确认并回到 `Idle`；下一次点保存按无影响路径直接写入，不会在用户没看过
  的破坏性计划上落库，也不会展示一张空的汇总弹窗。

### R1 测试与证据

- JVM：`PeriodCascadePlannerTest` 新增 4 项（反序 9、4、20 + 4–9 + 删 20 → `Blocked` 且无计划、
  消息含安排描述；反序下可表示的 4–4／9–9 仍正确重映射为 2–2／1–1；反序学期仅改名保留 9、4、20 且
  无影响；稀疏范围会吞掉无关节次时同样 `Blocked`），`ScheduleViewModelTest` 新增 7 项（反序阻止保存
  且写入 0、草稿保留、可继续修改；反序改名直接保存；`Idle`／`Blocked`／`dismiss` 后过期确认不写入；
  计划变化时第一次确认只刷新摘要、第二次才写入；确认变无害时清除过期确认不写入）。
- 设备：`p3r06R7ReversedPeriodsBlockedEvidence`（真实 ViewModel，反序学期 + 4–9 安排 + 删除第 20 节 →
  两个入口都弹红色错误框、断言文案、写入 0、关闭后草稿与已存学期不变），并新增截图
  `p3-06-r7-08-reversed-periods-blocked.png` 与 `node-verification-reversed-20260919.txt`。
- 修正 2 属纯状态保护，弹窗外观与按钮未变，因此复用 01—03 的视觉证据，由 JVM 测试断言（已在证据
  README 中如实说明）。

## R2 修正（稀疏编号性能阻断）

复审指出 `SemesterCascadePlanner.isRepresentable` 会遍历 `startPeriod..endPeriod` 与 `mappedStart..mappedEnd`
两个整数区间。版本 1 接受稀疏或反序的任意正整数节次编号（实际节次 ≤20，但编号跨度可能接近
`Int.MAX_VALUE`），因此合法学期只改名或普通保存时也可能执行数十亿次循环，造成卡顿／ANR。

- 修正：先显式判断 `mappedStart <= mappedEnd`（倒置仍不可表示）；随后只遍历**实际存在的持久化节次**：
  取「旧安排范围内仍保留的来源节次集合」与「新映射范围内实际存在的持久化来源节次集合」，两者完全
  相等才可表示（`numberBySource.keys.filter { it in start..end }` 与
  `sourceByNumber.filterKeys { it in mappedStart..mappedEnd }.values`，复杂度只与实际节次数有关）。
- 语义保持：编号空洞不是节次、不会单独导致 `Blocked`；缺失端点仍走既有直接删除；被删节次位于原安排
  范围内仍按直接引用／跨越删除处理；2–1 倒置仍 `Blocked`；新范围吞入无关持久化节次仍 `Blocked`；
  正常升序删除、部分删除、整门删除、单节次与合法身份重映射不回归；反序／稀疏学期只改名仍直接保存。
- ViewModel 的确认状态机、UI 文案与视觉均未改动。
- 测试：`PeriodCascadePlannerTest` 新增 5 项并全部带 `@Test(timeout = 5_000)` 作为“不得按跨度遍历”的
  回归保护（`Int.MAX_VALUE - 1` 与 `1_000_000_000` 级别的稀疏编号普通评估返回 `Plan`；极大空洞不是
  节次且仍可合法重映射为 1–2；极大稀疏范围吞入无关持久化节次仍 `Blocked`；大编号反序仍因倒置
  `Blocked`；极大范围内被删节次仍按直接引用／跨越删除而不是 `Blocked`），`ScheduleViewModelTest`
  新增 1 项（稀疏近上限编号只改名时直接写入且不阻塞）。超时保护下这些用例在当前实现为毫秒级；若
  有人恢复按跨度遍历，它们会因超时或断言失败而失败，但循环本身仍然终止（不是不可中断死循环）。
- 本轮验证：Debug／Release JVM 各 **168 tests、0 failures／errors／skipped**（R1 基线 162，+6）；
  完整 connectedDebugAndroidTest **121 tests、0 failures**（与 R1 相同，未新增设备用例）；
  `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` 0 errors／20 warnings；
  文档测试 71 tests OK 与两个脚本通过。
- 证据：UI 无变化，8 张截图由同一条证据用例重新采集，像素分类与节点 bounds 与上一轮完全一致（已在证据
  README 第 4、5 条如实说明）。

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

P3-06-R7 已实现并自测；R1 复审提出的两项缺口、R2 复审提出的稀疏编号性能阻断均已修正并复测
（JVM 168、设备 121、lint 0 errors、8 张截图证据齐备）；**Sol 对 R1／R2 修正的独立复审与用户验收
均未进行**。按 AGENTS.md，本任务涉及破坏性数据与原子状态，不得以自测代替独立复审；A08／A10／A11、
整个 P3 与完整 App 仍未授权、未完成。
