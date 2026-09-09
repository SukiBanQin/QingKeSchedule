# P2-01 独立复审记录

## P2-01-R2 设备环境已恢复，测试入口缺陷待修正（最新，2026-09-09）

用户授权复用本机已验证的 API 37 ARM64 模拟器后，本窗口以
`08aaab1b2e4550f05fcdaf6180400ffdd61736fc` 为基准完成设备执行。开始时分支为
`Android`，本地 HEAD、`refs/heads/Android` 和 `origin/Android` 一致，工作区干净；没有
其他连接设备、Emulator／qemu 或项目构建进程。本窗口没有修改应用或 Android 测试
源码，也没有启动子 Agent。

已指定成功 AVD 所在的
`ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd`，启动
`qingke-api37-r3-arm` 到 `emulator-5588`。日志确认 API level 37 和 `-enable-hvf`；ADB 实测
`device`、`sys.boot_completed=1`、SDK 37 与 `arm64-v8a`。因此先前的设备环境门槛已解除，
无需实体真机。默认 `~/.android/avd` 中另有 `target=android-0` 的旧登记，本轮没有
误用它。

在唯一在线设备上两次运行 `connectedDebugAndroidTest`，第二次使用 `set -o pipefail`
确认 Gradle 退出码为 1；任务已进入 `qingke-api37-r3-arm(AVD) - 17`，不再是
`No connected devices`。但 AndroidJUnit4 在执行用例前报告 `InvalidTestClassError`：
`emptyRoundTripOrderDuplicatesAndReopen`、`invalidWriteAndInjectedFailureRollBack` 和
`cancellationPropagatesFromReadAndRollsBackWrite` 均“should be void”。XML 只有 1 个
`initializationError`（failures 1，errors 0，skipped 0），四个 Room 测试方法均未进入测试体。

源码和 `javap` 交叉核对确认：这三个 `@Test` 是返回 `runBlocking` 结果的表达式函数，
最后执行的 `File.delete()` 返回 `Boolean`，所以编译后方法确实为 `boolean`，不符合 JUnit 4
的 `void` 要求。这是测试入口缺陷，不是 Room 断言已失败，也不是新的生产代码结论。
完整现场证据见 [P2-01-R2 connected 测试证据](evidence/p2-01-r2-connected-debug-android-test-20260909.txt)。

后续仅需修正 `RoomScheduleRepositoryTest.kt` 中这三个测试的 JVM 返回类型，不得删除或
弱化断言，不需要修改生产代码。修正后必须在上述正确 `ANDROID_AVD_HOME` 和 API 37 模拟器上
重跑全部 `connectedDebugAndroidTest`，核对实际测试数、失败和跳过数。由于 Room 功能断言尚未
执行，**P2-01 整体仍未验证、未审查通过**；不得进入 P2-02、P3，也不得宣称
A09、P2 或完整 App 完成。证据收集后已正常关闭 `emulator-5588`。本轮文档验证
32 项、既有文档与布局测试及 `git diff --check` 均通过。

## P2-01-R1 重新独立复审结论（最新，2026-09-09）

重新复审基准为 `932f4b5367c641e3d1abc5a5ba1f7286283b2613`，实施提交为
`c96658a3a5c8943a820893ff8c46d1079185a0ea`。实际 diff 只有执行交接列出的 6 个文件：
`RoomScheduleRepository.kt`、`ScheduleAppState.kt`、两处对应测试、`handoff.md` 和新的
connected 测试证据；没有 iOS、Web、共享 schema／fixtures、页面、`MainActivity`、
DataStore、通知或导入导出改动。重新获取远端后本地 HEAD、`refs/heads/Android` 与
`origin/Android` 均为 `c96658a`，`origin/IOS` 为 `81ae16f`，复审开始工作区干净。

### R1 代码与测试审查

- `RoomScheduleRepository.read` 在 `ScheduleRepositoryException` 和普通 `Throwable` 包装前
  单独重新抛出 `CancellationException`。因此 `load`、先读取再变更的保存方法，以及 Room DAO
  自身抛出的协程取消都不会再被转换为“课表存储损坏”。`replace`／事务路径原本不包装异常，
  提交前取消继续由 `withTransaction` 回滚并传播。
- `ScheduleAppState.load` 和串行保存边界都在进入操作前保存完整状态；捕获取消时以非挂起赋值
  恢复该快照并重新抛出，不发布 `FAILED`、普通错误或遗留 `isSaving`。普通失败处理和成功后不
  二次读取的既有行为未改变。
- 状态 JVM 测试分别从初始状态和 `READY` 状态注入 `CancellationException`，断言调用方收到
  取消、数据／加载状态恢复、错误为空且 `isSaving == false`。两项用例已在 Debug／Release
  JVM 测试中独立实际执行。
- Room 测试的 `beforeRead` 覆盖读取取消传播；`beforeCommit` 在事务已经写入但尚未提交时取消，
  随后关闭所有连接并重新打开文件库断言旧快照仍在。测试设计能覆盖本次问题；但本机没有连接
  设备，这两项 Room 断言本轮只完成编译，不能冒充实际运行。

本次 R1 范围没有发现需要继续修改应用代码的问题，**协程取消修正通过代码复审**。这只关闭
原记录的 P2-01-R1 代码问题，不关闭下述 P2-01-R2 设备证据门槛。

### 独立验证

- 使用 JDK 17 和
  `ANDROID_HOME=/Users/takagisan/Library/Android/sdk-qingke-api37` 运行
  `./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug --no-daemon --console=plain`：
  `BUILD SUCCESSFUL in 31s`，112 个任务中 109 executed、3 up-to-date。
- 新生成 XML 显示 Debug／Release JVM 各 28 项，均为 0 failures、0 errors、0 skipped；每个
  变体的 `ScheduleAppStateTest` 为 8 项，其中包括加载和保存取消两项。
- `lintDebug` 为 0 errors。本次独立在线检查生成 9 个 warnings，均指向既有固定版本提示、
  `app_name` 未用及缺少应用图标；实施交接记录的是 7 个 warnings，数量差异来自当前 lint
  重新查询到更多新版本提示，不影响本次取消代码结论，也不把警告误写成 0。
- 独立运行 `./gradlew connectedDebugAndroidTest --no-daemon --console=plain`：
  `BUILD FAILED in 6s`，74 个任务中 33 executed、41 up-to-date；在设备执行前失败为
  `DeviceException: No connected devices!`，实际 Room 测试数仍为 0。随后指定 SDK 的
  `adb devices -l` 为空，未发现运行中的 Emulator／qemu 进程。
- `git diff --check 932f4b5..c96658a` 通过；提交范围与执行回传一致。文档验证及本次审查文档
  提交结果以最终交付消息为准。

### 当前门槛

P2-01-R1 代码复审通过，但 P2-01-R2 仍未完成：版本 1 Room schema、事务取消回滚、关闭重开、
外键级联、重复 ID／顺序和损坏数据用例没有在 API 37 Android 运行时执行。本轮证据如实证明
测试 APK 能编译且当前没有设备，不证明此前 HVF／`mprotect` 环境问题，也不证明问题已解除。

因此 **P2-01 整体仍未达到“已验证／已审查通过”**，A09、P2 和完整 App 也未完成。
没有新的应用代码修正任务；后续只能在可用 API 37 ARM64 模拟器或等效 API 37 设备上实际运行
`connectedDebugAndroidTest` 并回传测试数量、失败和跳过，再申请关闭 P2-01。当前不进入
P2-02、P3 或其他功能实施。

## 复审范围与基准

复审日期：2026-09-09。复审提交为 `39eeaa3529aa761174ff6c4f3c7fcd38edb06d6f`，范围为
`6f5258c..39eeaa3`。本窗口只读检查 Android 持久化、状态、测试和交接证据，没有修改应用代码。
当前分支为 `Android`，本地 HEAD 与 `origin/Android` 均为 `39eeaa3`，工作区在复审开始和结束时均干净。

## 已核对内容

- Room 版本 1 schema 已提交，元数据、学期、节次、课程和课程安排为独立表；课程安排通过外键
  `ON DELETE CASCADE` 关联课程。
- 业务 ID 与内部自增行键分离，重复课程／安排 ID 不会被 Room 主键暗中拒绝；`sortIndex` 用于恢复
  节次、课程和安排顺序。
- `replace`、学期／课程保存和删除均通过 `withTransaction` 写入，并提供事务前提交故障注入点。
- 状态层使用 `StateFlow`，成功写入后发布仓库返回快照，没有二次 `load`；写失败保留旧内存快照。
- `python3 docs/tests/android-documentation.test.py`：29 项通过。
- `bash docs/tests/documentation.test.sh`：通过。
- `bash docs/tests/repository-layout.test.sh`：通过。
- `git diff --check`：通过。
- 已读取已生成的 Debug／Release JVM 测试 XML：每个变体 26 项，失败、错误、跳过均为 0；包含
  6 项状态测试、11 项 JSON 测试、5 项校验测试和 4 项规则测试。

## 未通过项与修正要求

### P2-01-R1：协程取消被吞掉并误报为业务失败

`Android/app/src/main/java/com/qingke/schedule/persistence/RoomScheduleRepository.kt:80`、
`Android/app/src/main/java/com/qingke/schedule/state/ScheduleAppState.kt:34` 和 `:50` 使用
`catch (Throwable)`。这会捕获 `CancellationException`，把调用方取消误转换为
`InconsistentStore("读取失败")` 或状态错误，并可能继续发布 `FAILED`／结束保存流程。P2-01 契约要求
协程取消不被当作存储失败；应在捕获业务／底层异常前显式重新抛出取消异常，或只捕获明确的非取消异常。
请为 repository 和 state 增加取消测试，证明取消会传播、不会写入部分状态、不会发布误导性的业务错误。

### P2-01-R2：Room 设备集成证据缺失

交接记录确认 `connectedDebugAndroidTest` 未成功运行；API 37 ARM64 AVD 因
`hvf is not enabled on this aarch64 host` 与 `qemu_mprotect__osdep: mprotect failed: Permission denied`
保持 `offline`。`assembleDebugAndroidTest` 只能证明测试 APK 编译，不能证明 Room 事务、重启和外键
级联在真实 Android 运行时通过。修正后需要在 API 37 设备或等效环境重新运行
`./gradlew connectedDebugAndroidTest --no-daemon --console=plain`，记录实际测试数量和失败／跳过；
若仍受环境限制，保留最新失败证据，不能把 P2-01 标为已验证或已审查通过。

## 结论

P2-01 的结构设计和主机侧 JVM／文档验证大体符合范围，但当前**独立复审未通过**。在 R1 取消语义
修正和 R2 设备集成证据补齐（或明确记录持续阻塞）前，不得标记 P2-01 已审查通过，不得进入
P2-02、P3 或宣称 A09／P2／完整 App 完成。建议执行窗口使用 Terra／中修正 R1；修正后由本窗口
重新核对 diff、取消测试和设备证据。
