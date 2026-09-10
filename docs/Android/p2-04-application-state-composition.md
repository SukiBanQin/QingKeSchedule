# P2-04 应用状态与生产依赖装配分析

## 状态与范围

分析日期：2026-09-10。分析基准为
`9f7bf013a64c42ddaf7966987542728ce6f91df1`，分支 `Android`；开始时本地 HEAD、
`refs/heads/Android` 与 `origin/Android` 一致，工作区干净。用户授权本分析窗口在
P2-03 获确认后检查 P2 的收口缺口。本轮只读取 Android／iOS 实际源码并维护 Android
分析、交接文档和文档测试，不修改任何应用代码，也不进入 P3。

分析中重新获取远端后，`origin/IOS` 为
`fc3ddfb8ffa14b205a591ffdbed5632d5f975001`。相对 Android 分支所带 iOS 副本，最新 iOS
只在 `ScheduleAppState`／`AppRootView` 等展示路径增加当前时间刷新和周表跟随行为；
`QingKeScheduleApp` 的依赖装配、设置存储及本节所引用的加载边界没有变化。时间刷新属于
P3 展示／生命周期范围，不并入 P2-04。

结论是 P2 尚缺一个可独立验证的 P2-04：把已经通过审查的 Room 课表仓库、DataStore
偏好仓库与同一个应用状态边界装配成真实生产入口。P2-04 仍是存储与状态层任务，不实现
Compose 页面。用户随后已授权实施；当前实现和测试已经完成，等待独立复审。实施提交编号
及远程状态以最新交接和交付消息为准。

## 实际代码与 iOS 基准

### Android 当前缺口

- `ScheduleAppState` 只依赖 `ScheduleRepository`，其 `StateFlow` 只有课表、加载、保存和错误；
  P2-02 的 `SchedulePreferencesRepository` 尚未接入任何可观察状态。
- `RoomScheduleRepository` 只能由调用方传入 `ScheduleDatabase`，生产代码没有固定数据库名的
  Room 构建入口；DataStore 虽有 `create(context)`，也没有与 Room 一起形成进程内唯一依赖。
- `AndroidManifest.xml` 没有自定义 `Application` 或等价应用级容器。若后续由 Activity 每次
  直接构建，配置变更可能重复创建 DataStore／数据库实例并丢失内存状态。
- `MainActivity` 仍只显示“轻课”，没有 ViewModel、加载触发或页面。这是 P3 尚未开始的准确
  状态，P2-04 不应借装配任务提前修改它。
- P2-03 的课程／学期草稿和冲突评估是纯 Kotlin 能力，保存仍须由后续页面明确调用状态层；
  P2-04 不应把草稿自动持久化或实现确认对话框。

### iOS 对照与 Android 差异

iOS 的 `QingKeScheduleApp` 在应用入口一次创建 SwiftData 容器、课表仓库、三个设置存储和
`ScheduleAppState`；`AppRootView.task` 只在尚未加载时调用课表 `load()`。设置在状态初始化时
同步从 UserDefaults 读取，状态修改设置后立即保存。

Android 的 Room／DataStore 均是异步接口，且 P2-02 已明确普通 I/O 失败和
`CancellationException` 不能伪装成默认值或成功。因此 Android 不照搬 iOS 的同步初始化，
而应使用一个显式、可重试、无部分发布的联合加载边界。生产依赖由 Application 级容器保持
唯一，状态对象仍只依赖仓库接口，供 P3 的 Activity 级 ViewModel 持有。Application 本身不
启动加载协程，避免把生命周期和错误展示提前塞入进程入口。

## P2-04 可实施契约

### 统一应用状态

- 扩展现有 `ScheduleAppState`／`ScheduleState`，让构造参数同时要求
  `ScheduleRepository` 和 `SchedulePreferencesRepository`。状态快照至少包含完整
  `ScheduleData`、完整 `SchedulePreferences`、`NOT_LOADED`／`LOADING`／`READY`／`FAILED`、
  保存中标记和可清除错误；不得让状态层直接引用 Room DAO、DataStore 或 Android `Context`。
- 初始课表空值和 `SchedulePreferences.defaults` 都只是尚未加载时的占位快照，不表示磁盘
  数据已经确认存在。`needsOnboarding` 仍只在联合加载进入 `READY` 且学期为空时为真。
- `load()` 必须先取得两个仓库的结果，再一次性发布完整 `READY` 快照；读取顺序或是否并发
  不作产品约束，但任何一个仓库失败时都不得先发布另一个仓库的新值。普通失败保留操作前的
  两类数据，进入 `FAILED`，给出可区分课表／设置来源的稳定中文错误，并允许完整重试。
- 任一读取抛出 `CancellationException` 时恢复操作前的完整状态并原样重新抛出；不得发布
  `FAILED`、普通错误、半加载偏好或错误的 onboarding。若实现采用并发加载，必须取消并等待
  另一子任务结束后再返回，不能遗留后台读取继续改状态。
- 保留 P2-01 四种课表写方法及其已审查语义。新增对完整偏好的 `savePreferences` 和原子
  `updatePreferences`（或语义等价的明确 API），供后续页面按字段构造变更；本任务不加入通知
  权限或调度副作用。
- 所有经状态层发起的课表／偏好仓库写操作在同一状态边界串行化，避免跨存储并发更新造成
  `isSaving`、错误或公开快照相互覆盖。这只是内存协调，不是 Room 与 DataStore 的跨存储事务；
  P2-04 不提供同时写两者的复合操作。
- 偏好写入不得先乐观发布。DataStore 返回规范化且已提交的 `SchedulePreferences` 后一次性
  发布，不再额外 `load`；普通失败保留课表和偏好旧快照、结束保存中并发布错误，取消则恢复
  完整操作前快照并传播。成功写偏好不得改变课表，成功写课表不得改变偏好。
- 状态快照更新必须使用互斥或原子更新方式，不能通过互不协调的“读取旧 value 再 copy 写回”
  丢失另一路已完成的字段。现有加载失败重试、写入失败回滚、取消传播和课表成功不二次读取
  测试必须保留。

### 生产依赖装配

- 建立一个小型 `ScheduleAppDependencies`（或等价命名）边界，公开的依赖类型是
  `ScheduleRepository` 与 `SchedulePreferencesRepository`；默认实现使用
  `applicationContext` 创建一个版本 1 `ScheduleDatabase`、一个 `RoomScheduleRepository`
  和一个 `DataStoreSchedulePreferencesRepository`。
- 为 Room 生产文件定义稳定、集中且可测试的数据库名；继续使用 P2-02 已固定的
  `schedule_preferences.preferences_pb`。不得启用 destructive migration、复制第二套偏好键，
  或把课表改存为 JSON。
- 通过自定义 `Application`（或能证明相同进程唯一性和可替换性的等价方案）懒加载并持有
  一份生产依赖，在 manifest 注册。多次取得容器必须返回同一实例；不能在 Activity 重建时
  新建同一路径的 DataStore。为了设备测试，可提供 internal 的数据库名／文件／仓库工厂注入，
  但默认生产路径必须明确且不依赖测试参数。
- 应用级容器只负责对象生命周期和仓库提供，不持有 Activity、View、Composable，也不主动
  调用 `load()`。P3 再由 Activity 级 ViewModel 创建／持有 `ScheduleAppState`，在自身作用域
  只触发一次联合加载，并使用生命周期感知方式收集 `StateFlow`。
- 测试创建的容器必须能明确关闭 DataStore scope 和 Room 数据库，才能在同一路径关闭重建并
  验证持久化。生产进程正常存活期间保持单实例；不得依赖 `Application.onTerminate()` 保证保存。

## 明确不包含

- 不修改 `MainActivity`，不新增 ViewModel、Composable、导航、加载页、错误弹窗、首次设置、
  今日／周表、课程／学期／教学日历／外观页面，也不做视觉验收；这些从 P3 开始实施。
- 不实现草稿自动保存、冲突／放弃／删除确认，不改变 P2-03 默认值、dirty 或冲突规则。
- 不实现通知权限、闹钟、通知调度或 D03；偏好变化在本任务中只保存和发布状态。
- 不实现 JSON 文件选择、导入导出、分享，不扩展版本 1 协议，不决定 D01。
- 不修改 iOS、Web、共享 schema／fixtures，不改变 Room schema、DataStore 键及规范化规则，
  不进入 P3、P4、P5、P6 或完整 App 验收。

## 测试与验收边界

### JVM 状态测试

- 初始两类占位快照均为 `NOT_LOADED`；两个 fake 仓库都成功后一次进入 `READY`，空课表才需要
  onboarding，并证明课表与非默认偏好同时发布。
- 分别注入课表读取失败和偏好读取失败，证明不发布另一端的新快照、进入 `FAILED`、错误来源
  可区分且可重试。分别从两个读取点取消，证明恢复完整前态并传播取消，不遗留 `LOADING`。
- 偏好 `save`／`update` 成功发布仓库返回的规范化快照、不二次读取且不改变课表；普通失败、
  取消和失败后重试保留旧快照并正确恢复保存标记。
- 现有 replace、saveSemester、saveCourse、deleteCourse 的成功、失败、取消和不二次读取用例
  全部保留；增加课表写与偏好写并发请求，证明状态边界串行且最终两类更新都不丢失。

### API 37 生产装配集成测试

- 证明 manifest 使用预期 Application，应用级生产依赖为懒加载单例，仓库接口实际由 Room 与
  DataStore 实现；不得只在测试里手工拼 fake 后声称生产已装配。
- 通过可关闭的测试装配，在唯一临时数据库／偏好文件上联合加载空库与默认设置，分别保存有效
  学期／课程和非默认外观、提醒、教学日历，关闭两种存储并以同一路径重建；再次联合加载必须
  恢复完全相同的课表和规范化偏好。
- 继续运行既有 Room 11 项和 DataStore 8 项设备测试。使用 API 37 ARM64 模拟器或等效 API 37
  设备执行 `connectedDebugAndroidTest`，记录设备序列号、SDK、ABI、总测试数、各测试类数量、
  failures／errors／skipped，并确认新增装配用例进入测试体。

### 交付检查

执行窗口至少运行：

```bash
cd Android
./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest --no-daemon --console=plain
./gradlew connectedDebugAndroidTest --no-daemon --console=plain
cd ..
python3 docs/tests/android-documentation.test.py
bash docs/tests/documentation.test.sh
bash docs/tests/repository-layout.test.sh
git diff --check
```

只提交 P2-04 文件并推送 `Android`，核对本地与远程同名分支。由于本任务改变应用级依赖生命周期、
联合加载和两类持久化状态的一致性，实施后必须由分析审查窗口独立复审实际 diff、JVM 测试以及
API 37 XML／HTML 证据。通过只表示 P2-04 的实现和审查门槛关闭；P2 整体验收仍需用户确认，且不
自动授权 P3、A01—A11 或完整 App。
