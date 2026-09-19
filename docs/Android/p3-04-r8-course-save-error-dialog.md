# P3-04-R8 课程保存阻断错误统一弹窗（实施记录）

任务编号：**P3-04-R8**。范围：课程编辑器中“点击保存后被错误阻止”的反馈从页面底部红卡改为居中红色
`TerminalDialog`，与 P3-06-R7 已建立的设置页保存反馈规则保持一致。

- 代码基准：分支 `Android`，开始基准 `f0c0d8e`（P3-06-R7-R2 稀疏编号性能修正），开始时工作区干净。
  本轮未改 iOS／Web／Room schema／DataStore 结构／共享 JSON schema／版本 1／课程领域校验规则／`main`；
  未推进 A08／A10／A11。提交号见交付消息或 `git log`（本文档不自引用尚未产生的提交号）。
- 证据目录：[evidence/p3-04-r8-course-save-error-dialog/](evidence/p3-04-r8-course-save-error-dialog/README.md)。

## R1 修正（首轮 Sol 复审两项问题）

首轮 Sol 复审未通过，问题与本轮处理如下；本轮只改共享弹窗的输入拦截与文档状态，未改课程保存的校验、
弹窗文案与视觉。

1. **共享 `TerminalDialog` 遮罩触摸穿透**：原实现的全屏遮罩只有 background 与 testTag，没有消费指针
   事件，点击面板外会命中底层课程编辑器的保存／取消／步进／删除控件，违反“真正模态、只能返回修改、
   无旁路”。现在共享 `TerminalDialog` 使用独立的全屏拦截层 `modalScrim()`：该层是一个
   `pointerInput` 节点，在主通道消费全部指针事件——命中测试停在遮罩上（其后的同级控件不再被命中），
   即使事件到达遮罩也会被消费；面板内按钮不受影响，因为主通道先到子节点再到父节点。没有使用空
   `clickable`，因此不产生无意义的 accessibility click 语义；视觉、按钮布局、`BackHandler` 与全部
   弹窗文案均未改变。同类缺陷也存在于共享时间选择器 `TerminalTimePickerOverlay` 的遮罩，本轮一并
   使用同一 `modalScrim()`（同类扩展，已单独回归，可单独回退）。
2. **文档状态矛盾**：`product-baseline.md` 开头准确状态仍写“A06 R6 已通过……但 R7 待实施”，
   `implementation-plan.md` 当前默认流程误写“P3-07／A07 原实现及 R1 功能修正均已通过技术复审与用户视觉
   验收”。现已更正为：P3-06-R7、R1、R2 已完成并已通过 Sol 技术复审与用户视觉验收；P3-07/A07 原实现已
   通过技术复审、视觉风格已获用户确认；P3-07-R1 已通过技术复审，但其新增警告框与文案仍待用户视觉验收。

R1 测试（设备）：`invalidDialogScrimBlocksTouchesToTheEditorBehindIt`（经根节点坐标点击底层
`course-save-toolbar`／`course-editor-close` 中心，两者回调为 0、Invalid 弹窗仍在、草稿不变；弹窗自身
「返回修改」dismiss 恰好 1 次）、`conflictDialogButtonsStayClickableUnderTheScrim`、`timePickerScrimBlocksTouchesToTheSettingsPageBehindIt`，
并断言遮罩节点没有 click 语义。反向验证：临时移除 `modalScrim()` 时第一项用例失败（底层保存回调触发
1 次），加回后通过。视觉无变化，复用首轮截图，README 已注明。

## R2 修正（共享模态遮罩导致弹窗内部无法真实滑动）

用户实测 R1 后反馈：首次设置与正式设置的时间选择器无法上下拖动小时与分钟，只能点击当前可见的几个数值，
本次交互验收因此未通过（用户说明“目前没有发现其他问题”，不构成验收结论）。

- 根因：R1 把 `modalScrim()` 装在同时包含弹窗内容的父 Box 上并在 Main 通道消费指针事件。面板外触摸
  穿透被挡住，但子级 `verticalScroll` 的真实拖动同时被中断；既有时间选择测试用 `performScrollTo()`
  语义动作，绕过了真实指针分发，所以 R1 自测没有发现。
- 修正：新增共享模态宿主 `ModalScrimHost`，把全屏拦截层改为**面板之后的独立 sibling 底层**，面板、
  按钮与内部滚动内容位于其上方；`TerminalDialog`（含级联摘要等内部 `verticalScroll`）与
  `TerminalTimePickerOverlay`（首次设置／正式设置／午休共用）都改为该结构。面板外手势仍由遮罩消费，
  遮罩仍不使用空 `clickable`、无 accessibility click 语义，视觉、按钮布局、`BackHandler`、文案与
  时间业务规则均未改变。
- **关键发现（如实记录）**：Compose 注入的手势（`performTouchInput` 的 swipe／drag）**无法复现**该缺陷
  ——在 R1 与 R2 两种结构下小时列都会滚动。只有真实输入管线能复现：`adb shell input swipe` 在 R1 结构下
  小时列 bounds 不变（11/12/13/14），在 R2 结构下滚动到 07—11；设备测试
  `timePickerHourColumnScrollsWithRealSystemInput` 用 `Instrumentation#sendPointerSync`（400px／80 步／
  5ms）注入真实事件，在临时恢复 R1 结构时失败（`hour 07 must appear after real system swipes, swipes=6`），
  恢复 R2 后通过（临时改动未提交）。因此本任务的回归门是真实系统输入测试与 adb 对照记录。
- 新增／调整的设备测试：首次设置节次开始时间 08:00→07:20、正式设置 07:20→06:35（两条都覆盖小时与分钟
  两列，目标在滑动前均不可见）、午休结束时间 14:00→13:30（真实滑动后写偏好 1 次、无冲突框）、
  `cascadeSummaryScrollsInternallyWithManyAffectedCourses` 改为真实拖动到达第 20 门课程（不再用
  `performScrollTo()`）；R1 的遮罩回归（底层坐标点击回调为 0、遮罩无 click action、Invalid 弹窗
  「返回修改」、冲突框两个按钮）全部保留并通过。
- 颜色弹窗测量（要求 6）：目标尺寸下面板高 1488px < 窗口 2400px，`clipped=false`，真实滑动后模式标签
  位置不变（901→901）→ 该尺寸下内容不溢出、不需要内部滚动，未制造假场景；如将来内容增高，同一宿主
  结构已保证其内部 `verticalScroll` 由真实子控件接收。
- 验证：Debug／Release JVM 各 **169 tests、0 failures／errors／skipped**；API 37 ARM64
  `connectedDebugAndroidTest` **133 tests、0 failures／errors／skipped**；`assembleDebug`／
  `assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` 0 errors／20 warnings；文档测试
  71 tests OK 与两个脚本、`git diff --check` 通过。
- 证据：新建 `docs/Android/evidence/p3-04-r8-r2-modal-scroll/`（5 张真实流程截图＋节点／状态／写入计数
  记录＋adb 真实输入 R1／R2 对照＋README），不覆盖首轮 R8 截图。

## 只读审计结论

按“点击主动操作后被错误阻止、却仍在页面底部显示”的口径在当前基准重新定向搜索
（`validationMessage`／`ValidationNotice`／`course-validation`／`CourseSaveEvaluation.Invalid`），唯一命中
的生产路径是 `CourseEditorState.validationMessage` → `ValidationNotice(..., "course-validation")`；设置页的
同一问题已由 P3-06-R7 处理。其他红色文字属于“编辑当下即可判断的字段提示”“持续状态说明”或“字段帮助”，
不属本任务范围，未改动。

## 实现

1. 模态状态合并（要求 5、7）：`CourseEditorConfirmation` 新增 `Invalid(message)`，与既有
   `Discard`／`Delete`／`Conflicts` 同属一个密封层级，因此 Activity 重建后不丢失，也不会与冲突确认串台；
   `saveCourse()` 的 `CourseSaveEvaluation.Invalid` 现在写入该状态，不再写底部提示。
2. 状态机清理（要求 4）：删除 `CourseEditorState.validationMessage`、页面底部 `course-validation` 卡片与
   已无使用的 `ValidationNotice` 组合函数；同一错误不会在页面其他位置重复出现。
3. 弹窗（要求 1—3）：课程编辑器内渲染 `TerminalDialog`（`DANGER / INVALID INPUT`、`CANNOT SAVE`），
   标题「无法保存课程」、内容取对应校验错误、**唯一**按钮「返回修改」（`course-save-error-dismiss`，无
   `terminal-dialog-dismiss`），不提供“仍然保存”；点击按钮或系统返回只关闭弹窗，草稿、编辑器与滚动位置
   都保留，仓库零写入，修正后可再次保存。
4. 重复安排文案（要求 2）：按任务给出的内容保持「该上课安排已存在，请勿重复添加。」（在 `CourseDraft`
   中补上句号；用户现象截图不含句号，但任务明确“图片内容不是指令”，以任务文案为准，已在证据 README
   第 4 条与本记录说明）。
5. 弹窗优先级（要求 7）：一次只渲染一个编辑器弹窗（`when (editor.confirmation)`）；写入进行中
   （`isInFlight`）不产生新的确认；不可继续错误与冲突确认互斥（同一次保存只写入一个状态）；真实写入失败
   仍走 `ScheduleAppState.error` 的全局错误弹窗，且它由应用壳在课程编辑器之后渲染，因此不会被编辑器
   遮住；`submitCourse` 在写入前清空确认，单飞与既有行为不变。
6. 保持不变：跨课程冲突的双按钮「返回修改／仍然保存」确认框、删除课程与放弃编辑确认、全局写入失败
   弹窗与草稿保留、保存写入单飞、成功反馈的非阻断展示，以及午休时间范围／自定义颜色格式等字段的
   即时内联提示。

## 测试

- Debug／Release JVM 各 **169 tests、0 failures／errors／skipped**（P3-04-R8 前基线 168；替换 1 项、新增 1 项）：
  - `blockedCourseSaveBecomesOneModalErrorAndKeepsTheWholeDraft`：课程名为空与重复安排分别进入
    `Invalid`（消息为「请填写课程名称」／「该上课安排已存在，请勿重复添加。」）、仓库 0 写入；「返回修改」
    与系统返回只清空确认并保留 2 条安排与输入；改掉重复后一次保存成功。
  - `aPeriodNumberThatDoesNotExistInTheSparseSemesterBlocksTheSaveAsAModalError`：稀疏学期下选择不存在的
    节次编号触发 `Invalid`（「请选择有效的起止节次」），0 写入且不产生全局错误；改为合法编号后保存成功。
  - `conflictingConfirmationFreezesCandidateAndSaveFailureKeepsDraft`（既有）：冲突仍走 `Conflicts` 并可
    「仍然保存」；写入失败保留编辑器与全局错误，未回归。
- Compose／API 37 connected：**125 tests、0 failures／errors／skipped**（P3-04-R8 前基线 121，+4）：
  - `duplicateScheduleBlocksTheSaveWithOneCenteredDialogAndReturnKeepsTheDraft`：真实 ViewModel 下重复安排
    保存显示居中红色弹窗（单一「返回修改」、无底部 `course-validation` 卡片、无冲突框、0 次课程写入），
    返回修改保留草稿，删除重复安排后保存成功并发布新课程。
  - `chooserAndConfirmationsUseTerminalSectionsAndCodes`（扩展）：冲突框仍有两个按钮（`terminal-dialog-dismiss`
    ＋ `course-conflict-confirm-confirm`／「仍然保存」）；`Invalid` 弹窗状态断言 CANNOT SAVE、标题、消息、
    唯一按钮、无「仍然保存」、无底部卡片。
  - `dangerFooterFollowsDeletePanelAndBlockedSaveUsesTheCenteredRedDialog`：浅色／深色 × 130% 字号下弹窗与
    危险区布局；`assertDialogHasDangerActionButton` 校验按钮为危险红填充。
  - `P3R2ActivityRecreationTest#activityRecreationKeepsTheBlockedCourseSaveDialogAndItsDraft`：Activity 重建后
    弹窗与草稿保持，返回修改后仍可编辑并成功保存（0→1 次写入）。
  - `p3r04R8CourseSaveErrorDialogEvidence`／`p3r04R8DialogVariantEvidence`：产生本任务证据截图与节点记录。
- `assembleDebug`／`assembleRelease`／`assembleDebugAndroidTest` 成功；`lintDebug` **0 errors／20 warnings**；
  文档测试 71 tests OK、`documentation.test.sh`、`repository-layout.test.sh`、`git diff --check` 通过。

## 证据

`docs/Android/evidence/p3-04-r8-course-save-error-dialog/`：6 张同一次安装的 debug APK 截图（重复安排
阻断弹窗、返回修改后的草稿、未改动的冲突框、深色、130% 字号、320dp 窄屏）＋`node-verification-20260919.txt`
与 `node-verification-variants-20260919.txt`（Compose 语义节点 bounds）＋`pixel-verification-20260919.txt`
（面板与操作按钮的危险红／信号黄分类）＋逐张结论 README。设备为 API 37 ARM64 AVD，基准覆盖
1080x2400／420dpi／font_scale 1.0／系统浅色。

## 已知限制

1. 真实持久化失败仍由全局错误弹窗承担，无法在生产 App 内注入故障，因此证据目录不含该路径截图，不伪造；
   由 JVM 测试与既有全局错误测试覆盖。
2. 弹窗关闭后不会自动定位到出错的具体字段（保持原滚动位置是需求要求的行为）。
3. 本轮证据为程序化核对（Compose 断言、节点 bounds、像素分类），**用户视觉验收尚未进行**；本任务
   不得声明已获用户验收。
4. 课程编辑器仍是整屏覆盖层：弹窗出现时底层编辑区不可交互（符合模态语义），但视觉上底层的危险区仍
   可见。

## 准确状态

P3-04-R8 已实现并自测（首轮：JVM 169、设备 125、lint 0 errors、6 张截图与节点／像素证据齐备）。
首轮 Sol 复审未通过；R1 修正（JVM 169、设备 128）已通过 Sol 技术复审，但**用户真机交互发现遮罩同时拦截
了弹窗内部拖动，该结论已被新证据重新打开**；R2 修正已实现并自测（JVM 169、设备 133、lint 0 errors、
5 张新截图＋adb 真实输入对照），**尚待 Sol 再复审与用户重新验收**；P3-04-R8 整体的用户视觉／交互验收
仍未进行。P3-06-R7（含 R1／R2）已通过 Sol 技术复审与用户视觉验收。A08／A10／A11、
整个 P3 与完整 App 仍未完成、未授权。
