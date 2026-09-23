# P2-04 应用状态与生产依赖装配复审

## 复审结论

复审日期：2026-09-10。复审对象为分支 `Android` 上的实施基准
`205831819ff1343b5f736ea011e6817f9b7e5b55` 至实施提交
`e6a3513d720fe39f3b3f10aca1acc55208d9d820`。复审开始和结束时，本地 HEAD、
`refs/heads/Android`、`origin/Android` 与远端 `refs/heads/Android` 均指向实施提交，
工作区干净。

按用户指定，本轮由当前分析审查角色完成代码、测试和证据复核；没有修改应用代码。
复审没有发现阻断问题，P2-04 的联合状态、生产依赖装配和真实 API 37 持久化恢复证据符合
[实施契约](p2-04-application-state-composition.md)。但实施和本轮复审由同一 Codex 任务完成，
因此这是一轮有完整重跑证据的**同窗口复审**，不能表述为另一窗口或另一审查者完成的组织性独立审查。

准确状态是：P2-04 已实现、已测试并通过当前分析角色复审，尚未获得用户对 P2-04 或 P2 整体的验收。
本结论不表示 A01—A11、P2、功能页面或完整 App 完成，也不自动授权 P3。

## 范围与越界核对

实际实施 diff 共 13 个文件，范围为：

- manifest、自定义 `Application`、生产依赖容器；
- `ScheduleAppState` 联合课表／偏好状态及对应 JVM 测试；
- 真实 Room／DataStore 装配 AndroidTest；
- P2-04 分析、交接、技术／计划／产品状态、证据及文档测试。

`MainActivity`、Compose 页面、ViewModel、通知、导入导出、Room schema、DataStore 键、iOS、Web、
共享 schema／fixtures 均未修改。没有进入 P3 或其他后续阶段。

## 代码语义核对

### 联合加载与状态回滚

- `ScheduleAppState` 同时依赖 `ScheduleRepository` 与 `SchedulePreferencesRepository`，初始课表和
  `SchedulePreferences.defaults` 只是 `NOT_LOADED` 占位；`needsOnboarding` 仅在联合状态为
  `READY` 且学期为空时成立。
- `load()` 在同一互斥边界中顺序读取两个仓库，只在两者都返回后一次发布完整 `READY` 快照。
  课表读取失败不会读取偏好；偏好读取失败不会发布已经读到的课表。两类普通失败使用可区分的
  “读取课表失败”／“读取设置失败”前缀，恢复操作前两类数据并允许整体重试。
- 两个读取点的 `CancellationException` 均保持传播；外层恢复完整操作前状态，不遗留 `LOADING`、
  `FAILED`、普通错误、半加载数据或错误 onboarding。

### 写入、一致性与失败

- 既有 `replace`、`saveSemester`、`saveCourse`、`deleteCourse` 继续发布仓库返回的已提交快照，
  不进行第二次读取。新增偏好 `save`／`update` 也只发布 DataStore 返回的规范化已提交快照。
- 加载和所有课表／偏好写操作共用 `operationMutex`。跨类型并发写因此不会让 `isSaving`、错误或
  两类公开快照相互覆盖；实现没有提供同时写 Room 与 DataStore 的复合操作，也没有声称跨存储原子。
- 普通写入失败恢复完整旧快照、清除保存中并发布错误；取消恢复完整旧快照并重新抛出。
  成功写课表保留偏好，成功写偏好保留课表。
- `clearError()` 使用 `MutableStateFlow.update` 原子变换。未发现通过未协调的旧快照写回而丢失另一类
  已完成数据的路径。

### 生产生命周期

- manifest 注册 `QingKeScheduleApplication`；其 lazy 委托只在首次取得时创建一份进程内
  `ScheduleAppDependencies`，没有在 `Application` 中启动加载协程。
- 默认容器使用 `applicationContext`、固定 Room 文件 `schedule.db` 和既有
  `schedule_preferences.preferences_pb`，没有 destructive migration、第二套偏好键或 JSON 替代存储。
- 状态只依赖仓库接口；容器不持有 Activity、View、Composable 或 ViewModel。P3 仍需另行实现
  ViewModel、生命周期感知加载和页面。
- 测试工厂允许使用唯一临时数据库名和偏好文件，并能关闭 DataStore scope 与 Room 后按相同路径重建。

## 测试与证据复核

本轮从干净构建重新执行主机侧验证：Debug／Release JVM 各 55 项，均为 0 failures、0 errors、
0 skipped；其中 `ScheduleAppStateTest` 每个变体 21 项。Debug／Release 打包、`lintDebug` 和
AndroidTest APK 均成功。复审运行的 lint 为 0 errors、13 warnings，类别仍是工具链／依赖可更新、
未用资源和缺少 application icon；实施证据中的 11 个 warning 是其当次运行结果，动态版本提示数量
变化不构成应用回归。

本轮独立于实施时的测试进程重新启动唯一 API 37 ARM64 AVD `qingke-api37-r3-arm`，序列号
`emulator-5584`；实测 ADB `device`、boot 1、SDK 37、ABI `arm64-v8a`。最终
`connectedDebugAndroidTest` 成功，XML 为 21 tests、0 failures、0 errors、0 skipped：

- `ScheduleAppDependenciesTest`：2 项；
- `RoomScheduleRepositoryTest`：11 项；
- `DataStoreSchedulePreferencesRepositoryTest`：8 项。

装配测试两项均进入测试体。`javap` 确认它们的 JVM 签名均为 `public final void`。真实联合恢复用例
从空库／默认偏好加载，保存学期、课程和需规范化的设置，关闭两种存储后按相同路径重建，最终恢复
完全相同的课表和规范化偏好。设备取证后已正常关闭，`adb devices -l` 为空。

完整复审命令和结果见
[P2-04 同窗口复审证据](evidence/p2-04-same-window-review-20260910.txt)。

## 剩余门槛

- 没有已知 P2-04 代码阻断项需要返修。
- 当前用户尚未确认本次复审结果，也尚未验收 P2 整体。
- 如项目要求审查者与实施者必须是不同任务／窗口，本轮同窗口复审不能替代该组织性独立性证据；
  可以由用户决定是否接受当前复审作为 P2-04 的审查门槛。
- 在用户确认 P2 收口前不进入 P3；D01、D03 和正式发行范围继续保持未决定。
