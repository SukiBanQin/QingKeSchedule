# P3-02 应用壳、状态加载与首次学期设置分析

## 状态、基准与任务定位

分析日期：2026-09-11。用户已确认 P3-01 的实现、测试和最终独立复审结果，并授权继续准备 P3-02。
分析基准为 Android 分支 `0e994e01f9f1fb29be1ad167fd4a3f9bf70b876e`；分析时本地 HEAD、
`refs/heads/Android` 与 `origin/Android` 一致，工作区干净。重新获取后的 `origin/IOS` 为
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`。

本任务把已完成的 P2 应用状态、P2-03 学期草稿和 P3-01 展示模型连接到第一段真实可见的 Android
应用流程，但只交付“加载／失败／首次设置／主壳”四态和可保存的首次学期表单。它不会提前实现今日页、
周课表页、课程编辑页或完整设置页，也不表示 A01、A06、A11、P3 或完整 App 完成。

建议执行窗口使用 **Terra／高**：下述边界已经明确，适合常规实现；但这是第一个同时连接 Compose、
Activity 生命周期、状态和持久化的垂直切片，需要较多状态转换和设备验证。完成后必须由分析审查窗口
独立复审。

## 已核实的现状与 iOS 基准

- `MainActivity` 目前只在 `MaterialTheme` 中显示“轻课”，没有 ViewModel、导航、加载状态或页面。
- `QingKeScheduleApplication` 已提供进程级 `ScheduleAppDependencies`，其中有唯一 Room／DataStore 仓库；
  `ScheduleAppState` 已定义 `NOT_LOADED`、`LOADING`、`READY`、`FAILED`、`isSaving`、`error` 和
  `needsOnboarding`，但尚无 Activity 级持有者主动调用 `load()`。
- `SemesterDraft` 已提供可注入日期和 ID 的春／秋默认名、18 周、默认十节、增删、规范化和校验。
  它是普通可变 Kotlin 对象，不会自动触发 Compose 重组；界面层必须暴露可观察的不可变快照或等价状态，
  不能把裸 `SemesterDraft` 当作 Compose 状态直接修改。
- iOS `AppRootView` 按加载中、首次设置、主标签壳分流；首次设置保存默认学期后进入主壳。
  `SemesterFormView` 支持名称、开始日期、1 至 52 周、默认十节、节次折叠／展开、1 至 20 节增删和时间编辑。
  iOS 首次设置中的导入和教学日历区域分别属于后续文件迁移与设置范围，本任务明确不复制。
- iOS 主壳包含今日、课表、设置三个标签。P3-02 只建立标签结构和明确占位内容，不接入 P3-01 展示内容，
  以免把壳层任务扩大成今日／周课表页面。

## 一、Activity 级状态与根路由契约

1. 新增 Activity 级 `ScheduleViewModel`（或同职责且命名清晰的 ViewModel），由
   `QingKeScheduleApplication.dependencies` 构造并且只持有一个 `ScheduleAppState`。不得在 Composable
   中创建新的仓库、数据库或应用状态。
2. ViewModel 在 `viewModelScope` 中只触发一次初始 `load()`；重组、主题变化或 Activity 重建不得重复加载。
   UI 使用 `collectAsStateWithLifecycle` 或同等级生命周期感知方式订阅 `StateFlow`，不得用一次性快照绕过状态流。
3. ViewModel／表单边界允许注入 `now: () -> LocalDate` 和 ID 工厂，生产默认使用设备本地日期和 UUID，测试
   不依赖真实时间或随机值。
4. 根界面严格按状态选择唯一主体：
   - `NOT_LOADED`／`LOADING`：品牌加载界面；
   - `FAILED`：加载失败面板和重试按钮，不得把占位默认数据误判为首次设置；
   - `READY && needsOnboarding`：首次学期设置；
   - `READY && !needsOnboarding`：主标签壳。
5. `READY` 后的保存错误不应把整个根界面切成加载失败页。应保留当前首次设置表单或主壳，并以明确弹窗／
   提示展示 `state.error`；关闭提示调用既有 `clearError()`。
6. 加载重试必须复用同一个 `ScheduleAppState`；加载和保存期间相应操作不可重复触发。`isSaving=true` 时两个
   保存入口均禁用并显示一致的进行中状态，避免重复写入。
7. Activity 重建后保留当前标签和未提交的首次设置草稿。草稿由 ViewModel 或同一 Activity 级状态持有，
   不能只存在于局部 `remember`。本任务不要求证明系统杀进程后的未提交草稿恢复，也不得在证据中宣称已支持；
   已提交数据必须由 Room 在冷启动后恢复。

## 二、首次学期表单契约

1. 表单复用 `SemesterDraft` 和既有验证结果，不另写一套不同的名称、日期、周数或节次合法性规则。
2. 首次进入时完整显示由注入日期生成的默认名称、开始日期、18 周和默认十节时间。十节列表默认折叠，
   用户可显式展开；折叠不得丢失节次编辑。
3. 支持编辑学期名称、开始日期、总周数 `1..52`，并以 Android 原生或 Material 日期选择交互选择日期。
4. 展开后显示每节连续编号及开始／结束时间；支持 Android 对应的时间选择交互。节次最少 1、最多 20；
   新增继续复用 `SemesterDraft` 的 10／45 分钟规则，删除后编号连续。达到边界时按钮禁用或不执行，不能崩溃。
5. 顶部和底部保存按钮必须调用同一保存路径。保存前取得 `SemesterDraft.semester()` 或等价评估结果；非法时
   显示稳定的第一条验证信息，且不得调用 `ScheduleAppState.saveSemester()` 或仓库。
6. 合法保存只调用一次 `saveSemester`。成功后不直接篡改本地路由；应由已提交状态中的非空 semester 使
   `needsOnboarding` 变为 false 并进入主壳。
7. 普通保存失败后仍停留首次设置页，保留用户输入、展开状态和当前节次，显示状态层错误并允许关闭后重试；
   取消不转换成成功或误导性错误。界面不得吞掉状态层已经定义的失败／取消语义。
8. 键盘、日期／时间对话框、系统返回和安全区域按 Android 方式处理；保存入口在小屏和键盘打开时仍可访问。

## 三、主壳与最小视觉边界

1. 主壳默认选择“今日”，底部提供“今日／课表／设置”三个标签；标签选择应在 Activity 重建后保留。
2. 三个标签在本任务只显示清楚标注的壳层占位内容。不得接入今日／周课表展示模型、课程点击编辑、设置详情，
   也不得借此声称 A02、A03 或设置功能已实现。无需为三个固定标签引入导航框架。
3. 建立可复用的最小品牌／终端风格主题：参考 iOS 的信号黄 `#FFD400`、青色 `#28B9D6`、浅色反相表面
   `#091113`、深色表面 `#182427` 和明确危险色，面板保持方正、文字层级清楚。只实现本切片实际使用的 token，
   不一次性复制完整组件库。
4. 根主题读取已加载的 `AppearanceMode`，正确支持跟随系统、浅色、深色；本任务不提供外观切换设置页，
   因而不能宣称 A11 完成。
5. 主要交互至少满足 48dp 触控范围、可读 content description／语义、系统栏和安全区、小屏及放大字体基本可用。

## 四、测试入口与稳定标识

为避免测试依赖中文层级或屏幕坐标，界面至少提供以下稳定 Compose test tag（可在不改变含义时调整组件命名，
但交付文档须记录最终映射）：

- `app-loading`、`app-load-error`、`app-load-retry`；
- `onboarding-screen`、`onboarding-title`；
- `semester-name`、`semester-start-date`、`semester-total-weeks`；
- `daily-periods-toggle`、`add-period`、每节的稳定行／开始／结束／删除标识；
- `semester-validation-error`、`semester-save-toolbar`、`semester-save`；
- `app-error-dialog`、`app-error-dismiss`；
- `main-shell`、`today-tab`、`schedule-tab`、`settings-tab`。

测试分三层：

1. JVM ViewModel／表单状态测试：初始加载只发生一次、失败后重试、四态路由输入、默认草稿、编辑快照、
   Activity 级持有语义、非法表单不保存、成功传入完整 Semester、失败保留草稿、保存中阻止重复提交、错误关闭。
2. Compose Android 测试：加载／失败／首次设置／主壳四态；默认 18 周和十节折叠；展开、时间编辑、增删及
   1／20 边界；验证错误不回调保存；两个保存入口同路径；保存失败提示；重试；三个标签和重建后选择；
   关键触控与语义。测试假仓库必须可控成功、失败和挂起，不依赖生产数据库制造 UI 状态。
3. 真实生产路径设备证据：在唯一 API 37 ARM64 模拟器上清除应用数据后冷启动，看到首次设置；保存合法默认
   学期后进入主壳；`force-stop` 后再次冷启动直接进入主壳，证明真实 `Application`、`ScheduleAppState` 和 Room
   路径连通。记录设备序列号、SDK、ABI、命令、界面层级／截图及无崩溃或 ANR 的日志摘要。

## 五、允许修改与明确排除

允许执行窗口修改：

- `Android/app/src/main/java/com/qingke/schedule/MainActivity.kt`；
- 新增 `Android/app/src/main/java/com/qingke/schedule/ui/`、`viewmodel/` 或职责等价且清晰的界面／状态持有文件；
- `Android/app/build.gradle.kts` 中仅为生命周期感知 ViewModel／Compose UI 测试所需的直接依赖；保持现有版本图，
  不做无关升级；
- 对应 `Android/app/src/test/`、`Android/app/src/androidTest/` 测试；
- 本任务的 `docs/Android/` 交接、证据及 `docs/tests/android-documentation.test.py`。

除非新增最小回归先证明既有契约存在阻断缺陷并停止回传，不得修改 `ScheduleAppState`、`SemesterDraft`、Room、
DataStore、JSON 协议、P3-01 展示模型或领域校验器。不得修改 iOS、Web、共享 schema／fixtures。不得实现课程
增改删、今日／周表内容、完整学期编辑、教学日历、提醒、外观设置、导入导出、通知调度、D01、D03、P3-03
或后续阶段。

## 六、交付与验收门槛

执行窗口必须完成并记录：

- 指定 API 37 SDK 的 `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug
  assembleDebugAndroidTest`；
- 唯一 API 37 ARM64 设备上的 `connectedDebugAndroidTest`，以最终 XML／HTML 核对所有既有 21 项设备测试和
  新增 Compose 测试均真实进入测试体，记录 failures／errors／skipped；
- 上述清数据冷启动、保存、`force-stop` 后冷启动的真实生产持久化流程；
- `python3 docs/tests/android-documentation.test.py`、`bash docs/tests/documentation.test.sh`、
  `bash docs/tests/repository-layout.test.sh`、`git diff --check` 和提交前 `git diff --cached --check`。

界面交付还应保存首次设置和主壳的浅色／深色截图，至少补充一个小屏或大字体检查；不能只以编译成功代替
视觉和交互证据。若模拟器或 UI 测试受阻，必须记录真实失败和未验证项，不得将 P3-02 标记为完整通过。

完成后只提交本任务文件并推送 `Android`，核对本地 HEAD、`refs/heads/Android`、`origin/Android` 和远端
同名分支一致。随后交由分析审查窗口复审实际 diff、状态所有权、首次设置失败保留、真实生产冷启动路径、
设备 XML 和视觉证据。独立复审通过仍只表示 P3-02 已审查，不等于用户验收 A01／A06／A11、P3 或完整 App。
