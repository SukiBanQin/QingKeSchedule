# P2-01 持久化基础与状态边界分析

## 状态与依据

分析日期：2026-09-08。分析基准为
`cba042bc68cb6254c9410fc9f84c6767bc6715e9`，分支 `Android`；分析时本地 HEAD、
`refs/heads/Android` 与 `origin/Android` 一致，`origin/IOS` 为
`81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587`，工作区干净。两个远端引用已重新获取；
`Android` 与 `IOS` 分支的 iOS 持久化／状态源码没有差异。

本轮只读取和分析代码、维护 Android 文档及文档测试，不修改 Android、iOS 或 Web
应用代码，也不启动子 Agent。P1 结果及其专项证据不在本轮重复审查。用户已授权 P2，
但当前只准备 P2-01；P2-01 尚未实现、测试或审查。

依据包括 [产品基线](product-baseline.md)、[技术方案](technical-design.md)、
[实施计划](implementation-plan.md)、Android 当前领域／传输源码，以及 iOS 的
`Persistence/`、`State/ScheduleAppState.swift`、设置存储和相关测试。

## 实际代码基线

### Android 当前边界

- `ScheduleData` 是包含元数据、可空单学期、节次、课程和安排的完整不可变聚合；
  `ScheduleValidator` 是当前业务有效性的统一入口。
- `ScheduleDataDecoder` 已负责版本 1 JSON 的严格 UTF-8、字段、数字词法、大小和业务
  校验；它是外部 JSON 边界，不应成为 Room 读取器，也不应把数据库编码成 JSON 保存。
- `MainActivity` 仍只显示 P1 占位文本。工程没有 Room、DataStore、仓库接口、数据库、
  ViewModel／StateFlow 状态容器或对应持久化测试。
- P1 已实测重复课程／安排 ID 和反序节次编号在解码与领域校验边界被接受。因此数据库
  不得通过 DTO ID 唯一索引或按节次编号排序，暗中新增协议／业务限制。

`Android/README.md` 仍写着未知字段策略“尚未由产品决定”，与已经确认并完成两端同步的
严格拒绝策略不一致。分析窗口不修改 `Android/**`；P2-01 执行时在更新依赖说明的同时纠正
这一句，不能据此改变已确认行为。

### iOS 可复用的行为基准

- `ScheduleRepository` 将读取、整体替换、保存学期、保存课程和删除课程隔离为可替换接口；
  `SwiftDataScheduleRepository` 使用独立上下文、关闭 autosave，并在保存前校验完整聚合。
- 空存储返回版本 1、`semester == nil`、空课程以及
  `1970-01-01T00:00:00.000Z`；元数据缺失但存在其他记录、多个当前学期、不支持的存储版本、
  非法重复规则或完整聚合校验失败均作为不一致／损坏数据报错，不静默清库。
- 元数据、单学期、节次、课程和多个安排分别持久化；节次、课程、安排用显式 `sortIndex`
  恢复数组顺序，课程删除级联删除安排。整体替换和元数据更新时间在一次 SwiftData 保存内
  完成，保存前注入异常的测试证明旧数据仍可读取。
- 保存学期会保留原课程并重新校验完整聚合；课程以 ID 更新首个匹配项，否则追加；删除不存在
  的课程是无变化成功。整体替换保留输入 `updatedAt`，普通增改删使用注入时钟生成 UTC 时间。
- `ScheduleAppState` 初始化为空数据且未加载，显式加载后才决定是否进入首次设置；增改删先写
  仓库，成功后重读并发布，失败保留原内存数据并暴露错误。偏好存储与课表仓库相互独立。

下列 iOS 实现细节不是 Android 应照搬的产品规则：SwiftData 把 DTO ID 标成全局唯一，
比已确认的解码／领域边界更严格；状态层写成功后再次 `load`，若第二步失败会出现磁盘已提交、
内存仍是旧快照。iOS 启动时数据库容器创建失败还会 `fatalError`。P2-01 应在不改变用户可见
业务规则的前提下建立可验证的更强一致性边界，并把差异留在本文记录。

## P2-01 实施契约

### 依赖和数据库边界

- 保持单 `app` 模块、AGP 9.4.0、Gradle 9.6.0、built-in Kotlin 2.2.10、JDK 17、
  compile／target API 37 和 minSdk 26 不变。
- 固定使用稳定的 `androidx.room` 2.8.4 与 KSP 2.3.11；使用 KSP，不引入与 built-in
  Kotlin 不兼容的 `kapt`，也不借任务迁移到新包名的 Room 3。加入实际需要的
  `room-runtime`、`room-ktx`、`room-compiler` 和测试依赖，禁止动态版本。
  版本依据为 AndroidX [Room 发布记录](https://developer.android.com/jetpack/androidx/releases/room)、
  Android [built-in Kotlin 迁移说明](https://developer.android.com/build/migrate-to-built-in-kotlin)
  和 KSP [发布记录](https://github.com/google/ksp/releases)。若真实解析／编译出现兼容阻塞，
  保留最小证据并回传，不静默切换 Room 大版本、关闭 built-in Kotlin 或升级现有工具链。
- 建立版本 1 Room 数据库并导出 schema JSON，禁止 destructive migration。P2-01 没有旧版
  Android 业务数据库需要迁移，但必须关闭、重开同一文件并验证数据仍可读取，为后续迁移
  保留 schema 基准。
- 分表保存固定单例元数据、固定当前学期槽、节次、课程和课程安排；外键和课程到安排的级联
  删除必须由数据库约束并经测试证明。DTO 的 `id` 是需要原样保存的业务字段，不作为会拒绝
  重复值的全局数据库主键；内部行键／父子键必须与业务 ID 分离。所有数组使用显式顺序字段，
  读取按该字段恢复，不能按 ID 或节次编号重新排序。

### 可替换仓库接口和事务语义

- 提供不依赖 UI／`Context` 的 `ScheduleRepository` 接口，生产实现通过构造参数取得 DAO／
  数据库、时钟和测试故障注入点；测试可直接使用 fake repository。接口使用 suspend 调用，
  至少覆盖 `load`、`replace`、`saveSemester`、`saveCourse`、`deleteCourse`。
- 每个写方法返回**已经提交的完整 `ScheduleData` 快照**。状态层不得在成功写入后再额外
  `load` 才知道新状态；生产仓库必须保证“返回即已提交，抛错即事务未提交”。这样避免
  “写成功、随后读取失败”被错误表示成保存失败。进程在提交后退出时，下次启动以数据库为准。
- `replace` 先用现有 `ScheduleValidator` 校验，再在一个 Room 事务中替换全部课表；保留输入
  `updatedAt`。`saveSemester`、`saveCourse` 和实际发生的删除在同一事务内从当前数据生成、
  校验、写入并返回快照，同时用可注入时钟更新 UTC `updatedAt`。
- 保存学期保留课程，但学期变化使现有课程无效时必须整次拒绝并保留旧数据；没有学期时保存
  课程必须拒绝。课程以当前数组顺序中的首个相同 ID 更新并保持位置，不存在则追加；删除也只
  删除首个匹配项，删除不存在 ID 不改数据和 `updatedAt`。这些首个匹配语义延续 iOS 的列表
  操作，同时允许当前领域边界已接受的重复 ID 完整往返，不新增唯一性规则。
- 校验失败、SQL／约束失败、测试注入失败或协程取消都必须回滚整个事务；不得留下新元数据与
  旧子表、空主表与残留子表等部分状态。不要把 Room 和未来 DataStore 两个独立写入称为一个
  原子事务。

### 缺失、损坏和默认状态

- 真正没有任何课表记录的数据库读取为版本 1 空课表，使用与 iOS 一致的固定 epoch
  `updatedAt`；这与“数据损坏所以读不到”是两种不同结果。
- 只要出现部分记录、缺失元数据、存储 schemaVersion 不支持、非法枚举／关系、顺序无法恢复，
  或重建后的聚合未通过现有 `ScheduleValidator`，就抛出可识别的仓库损坏／不一致错误；不得
  自动返回默认课表、自动删除用户数据或使用 destructive migration。
- 读取和写入错误应保留技术原因供测试／日志判断，同时给状态层稳定的中文用户错误；本任务不
  实现错误弹窗页面。

### 最小应用状态边界

- 建立可由 ViewModel 在后续页面层持有的 `ScheduleAppState`（或等价命名）和只读
  `StateFlow`。状态至少包含完整数据快照、`NotLoaded`／`Loading`／`Ready`／`Failed`
  加载状态、保存中状态和可清除错误；依赖只指向 `ScheduleRepository`，不直接引用 Room DAO。
- 初始值是“尚未加载 + 空占位快照”，不能把占位快照解释为真实空数据库。显式 `load` 成功后
  才进入 `Ready`；仅 `Ready` 且 `semester == null` 时 `needsOnboarding` 才为真。加载失败保留
  上一个内存快照、进入 `Failed`、暴露错误并允许重试，不能因此进入首次设置或覆盖数据库。
- 所有写操作只允许在成功加载后发起，并在状态边界串行化，避免两个基于同一旧快照的保存互相
  覆盖。不得先乐观修改公开内存状态；仓库返回已提交快照后一次性发布并清除旧错误。仓库抛错
  时保持原数据、结束保存中状态并暴露错误。
- 用 fake repository 证明状态层成功写入后不额外读取、发布的正是仓库返回值；模拟加载失败、
  写入失败、失败后重试和并发写入，证明内存回滚及串行边界。P2-01 不把状态连接到占位 UI，
  不修改 `MainActivity` 的页面内容。

## 验收与证据要求

### JVM 单元测试

- 状态初始、加载成功、真正空数据的 onboarding 判断、加载失败不误判为空、清错和重试。
- 保存学期、课程增改、删除、整体替换成功时发布仓库返回快照且不发生第二次读取。
- 每一种写失败保留原内存快照并恢复保存状态；并发写请求按顺序执行，不丢失先完成的结果。
- 仓库候选聚合的业务校验和时间生成可在无 Android UI 的测试中验证；继续运行现有全部领域与
  共享 JSON 契约测试。

### API 37 Room 集成测试

- 空库默认值；完整学期／课程／多安排往返；显式保留节次、课程和安排数组顺序。
- 关闭并重开文件数据库后数据一致；检查版本 1 schema 已导出，未配置 destructive migration。
- 新建／更新学期和课程、追加课程、删除及安排级联、删除不存在项、可注入固定时间。
- 重复课程／安排 ID 与反序节次编号可完整往返；CRUD 对重复课程 ID 采用首个匹配项语义。
- 无效替换、学期变更导致现有课程无效、无学期保存课程均在写入前拒绝并保留旧数据。
- 在事务已删除／插入部分记录后注入异常，重新打开数据库仍读到旧快照；验证不是只看当前
  内存缓存。
- 人工写入缺元数据、非法重复规则或其他可构造损坏记录后，读取给出损坏错误且不清库。

### 交付检查

执行窗口至少实际运行并报告：

```bash
cd Android
./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest
./gradlew lintDebug
./gradlew connectedDebugAndroidTest
cd ..
python3 docs/tests/android-documentation.test.py
bash docs/tests/documentation.test.sh
bash docs/tests/repository-layout.test.sh
git diff --check
```

`connectedDebugAndroidTest` 使用 API 37 ARM64 模拟器或等效 API 37 设备，记录设备、测试数量和
失败／跳过；若设备或依赖环境阻塞，必须回传证据，不能用 JVM 测试冒充 Room 设备集成通过。
执行完成后只提交本任务文件，推送 `Android` 并核对远程同名分支；这是持久化完整模块和数据
一致性边界，必须由分析审查窗口进行独立审查。通过实施测试不等于 P2、A09 或用户验收完成。

## 明确不包含

- 不实现 DataStore、提醒／教学日历／外观偏好，也不决定多存储恢复或 D01。
- 不实现或修改页面、首次设置流程、课程／学期表单草稿、导入导出文件流程、通知、权限、分享、
  发布签名或 D03／正式发行范围。
- 不修改 iOS、Web、共享 schema／fixtures，不改变版本 1 严格未知字段、数字、日期、重复 ID
  或节次顺序的已确认边界。
- 不自动开始其余 P2 子任务、P3 或后续阶段。
