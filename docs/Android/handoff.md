# 安卓项目当前交接状态

## P2-01-R3 已执行，待最终独立复审（最新，2026-09-09）

执行基准为 `fc1c7f716df32a528317f13455b2c9c0e2f077e3`，分支 `Android`；开始时 HEAD、
`refs/heads/Android`、`origin/Android` 一致且工作区干净。仅修改了 R3 授权的
`RoomScheduleRepository.kt`、`ScheduleAppStateTest.kt`、`RoomScheduleRepositoryTest.kt`、
本证据文件和本交接；未修改 `p2-01-review.md`、iOS、Web、共享 schema/fixtures、页面、
DataStore、通知或导入导出，未进入 P2-02/P3。

生产修正：读取已能映射但重建聚合未通过 `ScheduleValidator` 的数据库记录时，统一抛出
`ScheduleRepositoryException.InconsistentStore`；待写入候选仍抛 `InvalidData`。已有损坏分类和
`CancellationException` 原样传播保持不变，未自动清库、未启用 destructive migration，也未改变
重复 ID、显式顺序、事务提交或状态回滚语义。

状态 JVM 测试按四种公开写操作分别补齐成功返回快照／不二次 `load`，并分别补齐失败保留旧
快照、`isSaving=false` 和普通错误；加载、重试、并发串行化、取消测试均保留。
Room 测试补齐多安排／重复 ID／反序节次重开、saveSemester/saveCourse/deleteCourse 全部 CRUD
语义、直接外键级联观察、无效写入保留、手工损坏分类与不清库、schema/外键/索引检查。

验证结果（最终 XML）：

- `clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug assembleDebugAndroidTest`：成功；Debug/Release JVM 各 34 项，均 `failures=0, errors=0, skipped=0`；`ScheduleAppStateTest` 各 14 项。
- `connectedDebugAndroidTest`：成功；API 37 ARM64 `emulator-5586` 实际进入 11 项 Room 测试，`failures=0, errors=0, skipped=0`。
- 设备实测 `state=device`、`sys.boot_completed=1`、SDK 37、ABI `arm64-v8a`；取证后已执行 `adb -s emulator-5586 emu kill`，设备已关闭。
- `python3 docs/tests/android-documentation.test.py`：33 项通过；`bash docs/tests/documentation.test.sh`、`bash docs/tests/repository-layout.test.sh`、`git diff --check` 均需在提交前再次执行。

完整测试／设备证据见 [P2-01-R3 证据](evidence/p2-01-r3-connected-debug-android-test-20260909.txt)。本轮仍未声明 P2-01、A09、P2、完整 App 或用户验收完成，须交回分析审查窗口复核实际 diff、错误分类、原始记录、事务／外键和测试报告。

## P2-01-R2-R1 复审通过，P2-01-R3 待修正（最新，2026-09-09）

分析审查窗口最终复审了基准 `1bdfa77d01ba19f1dd3a2d1b289757012a452012` 上的实施提交
`886bca62061081a71144e7dcb4cccddf967554d7`。实际 diff 仅为 `RoomScheduleRepositoryTest.kt`、
执行交接和设备证据三个文件，没有超出 R2-R1 授权。重新 fetch 后本地与远端 `Android`
均为 `886bca6`，`origin/IOS` 为 `81ae16f`，复审开始工作区干净。本窗口没有修改应用代码，
也没有启动子 Agent。

三个表达式测试已正确改为块体 Unit 方法；独立 clean 编译后四个 `@Test` 的 JVM 签名均为
`void`。故障注入显式绑定 `beforeCommit`，位于 Room 事务写入之后、提交之前，既有异常和
重开后旧快照断言保留。独立使用唯一 API 37 ARM64 `emulator-5588` 从 clean 运行：109 个任务中
106 executed、3 up-to-date，Debug／Release JVM 各 28 项，Room 设备测试 4 项，失败、错误、
跳过均为 0。模拟器已在取证后正常关闭。因此 **P2-01-R2-R1 聚焦修正通过独立复审**。

但 P2-01 整体仍不能关闭。最终对照 [P2-01 契约](p2-01-persistence-state.md) 发现：当前 4 个 Room 用例
未覆盖原契约的多安排／重复安排 ID 顺序、完整仓库 CRUD、删除不存在项、外键级联、完整无效
写入组合、损坏存储不清库及 schema／destructive migration 自动化检查；状态 JVM 测试也未对四种
写方法逐一证明成功不二次读取和失败回滚。更具体地，`RoomScheduleRepository.read` 对“已存储行能够
映射但重建聚合校验失败”会原样抛出 `InvalidData`，而契约要求存储损坏 `InconsistentStore`。

下一项仅为 P2-01-R3：修正该错误分类并补齐契约中已要求的状态 JVM 和 API 37 Room 测试；新测试如
暴露其他生产缺陷，应保留失败证据并回交审查，不自行扩围。详细结论见
[P2-01 复审记录](p2-01-review.md) 和
[R2-R1 复审证据](evidence/p2-01-r2-r1-review-20260909.txt)。**P2-01 整体最终复审未通过**；
不得进入 P2-02、P3，不得宣称 P2-01、A09、P2 或完整 App 已完成或用户已验收。本轮文档验证
33 项、既有文档和布局测试及 `git diff --check` 均通过；提交和推送结果以最终交付消息为准。

## P2-01-R2-R1 已实施，等待最终独立复审（最新，2026-09-09）

执行窗口以 `1bdfa77d01ba19f1dd3a2d1b289757012a452012` 为基准，仅修改
`RoomScheduleRepositoryTest.kt`。三个原本表达式形式的 AndroidJUnit4 测试改为普通块体方法，
内部调用 `runBlocking`，避免末尾 `File.delete()` 的 `Boolean` 成为 JVM 方法返回值；同时将既有
事务故障注入调用显式绑定为 `beforeCommit`。后者是 R1 新增 `beforeRead` 参数后尾随 lambda 的
绑定目标变化所致，修正后保持原来的“提交前抛出 `IllegalStateException`”断言，不弱化任何测试。
未修改生产代码、iOS、Web、共享 schema／fixtures、MainActivity、页面、DataStore、通知或导入导出，
也未进入 P2-02、P3 或完整 App。

`javap` 已确认四个 `@Test` 方法均为 JVM `void`。使用指定 SDK、指定 AVD 目录和
`ANDROID_SERIAL=emulator-5588` 启动 `qingke-api37-r3-arm`：ADB 为 `device`、
`sys.boot_completed=1`、SDK 为 37、ABI 为 `arm64-v8a`。`connectedDebugAndroidTest` 实际在该唯一
API 37 ARM64 设备运行并通过；XML／HTML 报告为 4 tests、0 failures、0 errors、0 skipped，四个
Room 用例均进入测试体。双变体 JVM 测试各 28 项，均为 0 failures／0 errors／0 skipped。
完整设备、`javap` 和报告证据见
[P2-01-R2-R1 connected 测试证据](evidence/p2-01-r2-r1-connected-debug-android-test-20260909.txt)。

本次启动的 `emulator-5588` 已在取证后正常关闭，`adb devices -l` 已确认无连接设备。P2-01-R1 协程取消代码复审此前已通过；
本轮取得的是此前缺失的真实 Room 设备测试证据和测试入口修正，仍须由分析审查窗口最终复审
P2-01。不得自行标记 P2-01、A09、P2 或完整 App 已完成／用户验收。

## P2-01-R2 模拟器已可用，Android 测试入口待修正（最新，2026-09-09）

用户授权按已验证方案复用本机 API 37 ARM64 模拟器。本窗口以
`08aaab1b2e4550f05fcdaf6180400ffdd61736fc` 为基准，开始时分支 `Android`、本地 HEAD 和
`origin/Android` 一致，工作区干净。本轮只进行设备执行、分析和文档证据维护，
没有修改 Android 应用或测试源码，没有修改 iOS、Web、共享 schema／fixtures，也没有启动子 Agent。

使用 `ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd` 启动
`qingke-api37-r3-arm` 后，`emulator-5588` 已达到 ADB `device`、
`sys.boot_completed=1`，实测 SDK 37、`arm64-v8a`，启动日志包含 `-enable-hvf`。因此模拟器
环境门槛已解除，无需实体真机；先前 R1 只是没有启动正确 AVD。默认
`~/.android/avd` 中的 `target=android-0` 旧登记不得作为后续执行入口。

`connectedDebugAndroidTest` 已两次进入该模拟器，不再报 `No connected devices`；但
AndroidJUnit4 在测试体之前因 `InvalidTestClassError` 失败。报告为 1 个
`initializationError`（failures 1，errors 0，skipped 0），四个 Room 测试均未执行。根因是
`RoomScheduleRepositoryTest.kt` 三个表达式 `@Test` 以 `File.delete()` 的 `Boolean` 作为
`runBlocking` 结果，`javap` 确认它们被编译为 `boolean` 而非 JUnit 4 要求的 `void`。

下一步仅修正这三个测试方法的返回类型，不修改生产代码，不删除或弱化断言；然后在
正确 AVD 上重跑全部 `connectedDebugAndroidTest`。详细分析见
[P2-01 复审记录](p2-01-review.md)，现场证据见
[P2-01-R2 connected 测试证据](evidence/p2-01-r2-connected-debug-android-test-20260909.txt)。模拟器已在取证后
正常关闭。**P2-01 整体仍未验证、未审查通过**；不得进入 P2-02、P3，也不得
宣称 A09、P2 或完整 App 完成。本轮文档验证 32 项、既有文档与布局测试及
`git diff --check` 均通过；文档提交和推送结果以最终交付消息为准。

## P2-01-R1 代码复审通过，设备门槛仍开放（最新，2026-09-09）

分析审查窗口已重新独立复审 `c96658a3a5c8943a820893ff8c46d1079185a0ea`，修正基准为
`932f4b5367c641e3d1abc5a5ba1f7286283b2613`，详见
[P2-01 复审记录](p2-01-review.md)首节。实际 diff 与回传的 6 个文件一致，没有越过 R1 授权
范围；重新获取远端后本地 HEAD、`refs/heads/Android` 和 `origin/Android` 均为 `c96658a`，
`origin/IOS` 为 `81ae16f`，复审开始工作区干净。本窗口没有修改应用代码，也没有启动子 Agent。

结论：repository 读取取消在普通异常包装前重新抛出；状态加载／保存取消恢复操作前快照并
重新抛出，不发布 `FAILED`、普通错误或遗留 `isSaving`。状态取消测试在 Debug／Release JVM
各自实际通过；Room 读取取消和提交前取消回滚测试的源码范围与断言正确。因此
**P2-01-R1 协程取消修正通过代码复审，无新的应用代码修正项。**

独立使用 API 37 SDK 完成 clean 双变体构建、测试和 lint：112 个任务中 109 executed、
3 up-to-date，Debug／Release JVM 各 28 项且失败／错误／跳过均为 0；`lintDebug` 为 0 errors，
当前在线检查为 9 个既有 warnings。独立 `connectedDebugAndroidTest` 仍在设备执行前因
`DeviceException: No connected devices!` 失败，74 个任务中 33 executed、41 up-to-date，
实际 Room 集成测试数为 0；指定 SDK 的 `adb devices -l` 为空，也没有运行中的 Emulator／qemu。

所以 P2-01-R2 设备门槛仍开放，**P2-01 整体仍未验证、未审查通过**。当前证据只证明没有连接
设备，不足以证明此前 HVF／`mprotect` 环境问题仍存在或已经解除；不得把测试 APK 编译当作
Room 运行通过。后续只需在可用 API 37 ARM64 模拟器或等效 API 37 设备实际运行全部
`connectedDebugAndroidTest` 并回传测试数量、失败和跳过；未取得该证据前不得进入 P2-02、
P3，也不得宣称 A09、P2 或完整 App 完成。

## P2-01-R1 已实施，等待重新独立复审（最新，2026-09-09）

执行窗口仅修正 P2-01 独立复审的协程取消语义，未进入 P2-02、P3 或完整 App。基准为
`932f4b5367c641e3d1abc5a5ba1f7286283b2613`，开始工作区干净。`RoomScheduleRepository.read` 在
包装普通读取异常前显式重新抛出 `CancellationException`；`ScheduleAppState.load` 与写入边界在
取消时恢复操作前快照并重新抛出取消，不发布 `FAILED`、普通 `error` 或遗留 `isSaving` 状态。
新增的状态 JVM 测试覆盖加载／写入取消传播和无误导状态；Room Android 集成测试新增可控读取取消与
事务提交前取消，断言取消原样传播且重新打开数据库仍为旧快照。原有事务、重复 ID、显式顺序、
损坏数据及成功写入不二次读取语义未改。

本机使用临时 `ANDROID_HOME=/Users/takagisan/Library/Android/sdk-qingke-api37` 完成
`clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest lintDebug`；Gradle 记录为
`BUILD SUCCESSFUL in 48s`。Debug／Release JVM 各 28 项（共 56 项）均为 0 failures／0 errors／0 skipped；
`lintDebug` 为 0 errors、7 个既有 warnings。`python3 docs/tests/android-documentation.test.py`（30 项）、
`bash docs/tests/documentation.test.sh`、`bash docs/tests/repository-layout.test.sh` 及 `git diff --check`
均已通过。

`connectedDebugAndroidTest` 已重新执行，测试 APK 74 个任务中完成 34 个、40 个 up-to-date，但因
`DeviceException: No connected devices!` 在设备执行前失败，没有运行 Room 集成用例。随后
`adb devices -l` 为空；SDK 可列出 `qingke-api37-r3-arm` 等 AVD，但没有正在运行的 Emulator。
此前 API 37 ARM64 宿主的 HVF／`qemu_mprotect__osdep`／ADB offline 限制仍未解除；本轮完整命令和
现场证据见 [P2-01-R1 connected 测试证据](evidence/p2-01-r1-connected-debug-android-test-20260909.txt)。
因此不得将设备集成测试、P2-01、A09 或 P2 标记为已验证／已审查通过。完成本轮文档验证、提交和推送后，
必须回交分析审查窗口复审实际 diff、取消测试和设备限制。

## P2-01 独立复审未通过，等待修正（最新，2026-09-09）

本窗口已独立复审提交 `39eeaa3529aa761174ff6c4f3c7fcd38edb06d6f`（范围
`6f5258c..39eeaa3`）。Room schema、事务替换、重复 ID／顺序设计、StateFlow 不二次读取以及
主机侧 JVM／文档验证大体符合 P2-01 范围，但复审未通过，详见 [P2-01 独立复审记录](p2-01-review.md)。

必须先修正两项：

1. `RoomScheduleRepository` 与 `ScheduleAppState` 的 `catch (Throwable)` 会吞掉
   `CancellationException`，把协程取消误报为存储／状态失败；需要显式传播取消并补充取消测试。
2. `connectedDebugAndroidTest` 尚未成功运行。API 37 ARM64 AVD 受宿主
   `hvf is not enabled on this aarch64 host`、`qemu_mprotect__osdep: mprotect failed: Permission denied`
   和 ADB `offline` 限制，当前只有测试 APK 编译证据，没有真实 Room 运行证据。

当前不得标记 P2-01 已审查通过，不得进入 P2-02、P3 或宣称 A09／P2／完整 App 完成。修正建议为
Terra／中；修正后回到本分析窗口复审。复审应用基准为 `39eeaa3`；本次审查文档提交后，当前本地
HEAD 与 `origin/Android` 均为 `6e4f62e`，工作区干净。

## P2-01 执行完成，待独立审查（最新，2026-09-09）

执行窗口在用户授权范围内完成 P2-01“持久化基础与状态边界”实施。开始前已读取
`AGENTS.md`、本交接、P2-01 分析记录及相关基线文档；分支为 `Android`，开始基准、本地
HEAD 与 `origin/Android` 均为 `e8aefb426f791cdda8c3fb748e973a2243b78a71`，工作区干净。
本次不修改 iOS、Web、共享 schema／fixtures 或 `MainActivity`，未启动子 Agent，未进入
P2-02。

实际新增 Room 2.8.4/KSP 2.3.11 的版本 1 数据库及已提交 schema、可替换的 suspend
`ScheduleRepository`、事务型 `RoomScheduleRepository` 和 `StateFlow` 的
`ScheduleAppState`。业务 ID 与内部行键分离，显式顺序字段还原列表，课程删除由外键级联；
空存储、损坏数据、整表替换、首个重复 ID 增改删、固定时钟及事务故障回滚均按 P2-01 契约
实现。写仓库调用返回已提交完整快照，状态层仅在成功后发布该快照而不二次读取；加载／保存
失败保留旧内存快照且可重试。新增 JVM fake-repository 状态测试和 API 37 Room 集成测试；
README 同步记录 Room/KSP 与两端严格未知字段决定。

已实际通过：使用本机保留 API 37 SDK（platform `android-37.0`）执行
`./gradlew clean assembleDebug assembleRelease testDebugUnitTest testReleaseUnitTest`（104 个
任务，成功）、`./gradlew lintDebug`（成功）、`./gradlew assembleDebugAndroidTest`（56 个任务，
成功）、三项文档／布局验证及 `git diff --check`。Room schema 已生成于
`Android/app/schemas/com.qingke.schedule.persistence.ScheduleDatabase/1.json`。

`connectedDebugAndroidTest` **未通过也未执行**：本次启动已登记 API 37 ARM64 AVD 后，受当前
宿主限制，Emulator 报 `hvf is not enabled on this aarch64 host` 和重复
`qemu_mprotect__osdep: mprotect failed: Permission denied`，ADB 一直是 `offline`；已停止本次
失败实例，未保留运行中的课表 Emulator。故 API 37 Room 集成测试仅完成 APK 编译，尚缺真实
设备运行证据，不能标为已测试通过或审查通过。完成提交与推送后须独立审查，审查重点包括实际
diff、事务回滚／损坏边界、重复 ID 与顺序、StateFlow 无二次读取，以及上述设备限制；不得自动
进入 P2-02、P3 或宣称 P2／完整 App／用户验收完成。

## P2-01 分析完成与执行边界（最新，2026-09-08）

分析审查窗口已基于实际 Android 与 iOS 源码完成 P2-01“持久化基础与状态边界”范围分析，
详见 [P2-01 分析记录](p2-01-persistence-state.md)。分析基准为
`cba042bc68cb6254c9410fc9f84c6767bc6715e9`，当前分支 `Android`；重新获取远端后本地 HEAD、
`refs/heads/Android` 和 `origin/Android` 一致，`origin/IOS` 为
`81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587`，工作区开始时干净。两个分支的 iOS
持久化／状态源码没有差异。本轮只修改 Android 分析／交接文档和文档测试，
没有修改应用代码，也没有启动子 Agent。

实际 Android 仍只有 P1 领域、校验、JSON 边界和占位 Compose 入口，没有 Room、DataStore、
仓库或应用状态实现。P2-01 现已划定为：稳定 Room 2.8.4 + KSP 2.3.11、版本 1 schema、
可替换 suspend 仓库、原子增改删／整体替换，以及只在提交成功后发布仓库返回快照的最小
StateFlow 状态边界。空库与损坏库必须区分；失败保留磁盘和内存旧快照；顺序显式保存；
不得以数据库唯一约束收紧 P1 已接受的重复 ID。

iOS 基准的可用行为继续保留，但不照搬两个风险：SwiftData 的 DTO ID 唯一约束比当前领域
规则严格；iOS 状态层写入后再次读取存在部分失败窗口。Android 写方法应直接返回已提交完整
快照，并通过事务故障注入、关闭重开数据库和 fake repository 状态测试证明一致性。

本任务明确不包含 DataStore／偏好、页面、草稿、导入导出、通知、权限、分享、发布或
D01／D03／正式发行决定，也不修改 iOS、Web 或共享 schema／fixtures。实施完成后必须运行
双变体 JVM 测试、API 37 Room 集成测试、构建、lint 和文档验证，独立提交并推送 `Android`；
由于这是完整持久化模块和数据一致性边界，随后需要独立审查。P2-01 尚未实现、测试或审查，
不得自动进入其余 P2 或后续阶段。

## 分析审查窗口切换状态（最新，2026-09-08）

本窗口按用户要求完成同角色交接后停止写入。当前角色为执行窗口（Terra）；本次授权
仅限整理交接状态，不启动 P2 实施、不修改应用代码。切换前已核对当前分支 `Android`，
本地 HEAD、`refs/heads/Android` 与 `origin/Android` 均为
`d4f7f6478c2e007dea5e3d60774a5e3e2525c535`，工作区干净；`origin/IOS` 为
`81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587`。本轮只更新本交接文档，没有启动子 Agent。

当前有效状态：P1 已完成独立审查并获用户确认，P2 已获授权；P2 尚无应用实施提交，
下一项仅为 P2-01“持久化基础与状态边界”。P2-01 的分析记录和执行边界已写入
`p2-01-persistence-state.md`，但尚未实现、测试或审查。新窗口须先核对最新代码、
分支、远端和工作区，再继续用户明确授权的 P2-01 工作；不授权分析窗口修改应用代码，
不得重复 P1 实施或审查，
也不得把 P2-01 扩大为整个 P2 或完整 App。

继续保留的决定与边界：版本 1 JSON 未知字段两端严格拒绝；API 37 工具链和设备门槛
已通过；D01、D03 和正式发行范围仍未决定。P2-01 应聚焦可测试的存储接口、状态
加载／保存和失败回滚契约，不提前实施页面、导入导出、通知或发布能力。

切换现场的只读检查没有发现连接中的 Android 设备，也没有发现正在运行的 Gradle、
`xcodebuild` 或课表项目 Emulator 进程；本窗口没有终止其他窗口的进程。当前没有正在
运行的操作。详细 P1 证据继续以现有专项复审文档为准，不因窗口切换重复验收；本次交接
提交编号以最终交付消息或 `git log` 为准，避免文档自引用尚未产生的提交。

## P1 用户确认与 P2 授权状态（最新，2026-09-08）

用户已确认 P1 阶段交付结果，并明确授权开始 P2。P1-01、P1-03、P1-04 及
P1-04-IOS-SYNC 的实现范围、独立复审、构建／测试和设备证据均已完成；`Android` 分支
远端为 `7e61f41`，`IOS` 分支远端为 `81ae16f`，相关工作区均已核对干净并推送。

当前决定：**P1 授权范围已完成并获用户确认；允许进入 P2，但不代表完整 App 或 A01—A11
已完成。** D01、D03、正式发行范围仍未决定。下一项只安排 P2-01“持久化基础与状态边界”，
先建立可测试的存储接口和状态协调边界，再由用户逐项确认后续实现；不一次实施整个 App。

本轮只整理文档和交接，不修改应用代码。新窗口接手前必须先读取 `AGENTS.md` 和本文件，
核对分支、提交、工作区及未提交改动；不得重复 P1 实施或审查，不自动扩展 P2 范围。

## P1 阶段关闭核对状态（最新，2026-09-08）

分析审查窗口已核对 `Android` 与 `IOS` 两个开发分支的 P1 交付状态。P1-01、P1-03、P1-04
及 P1-04-IOS-SYNC 均已完成对应独立复审，P1-04-IOS-SYNC 独立专项复审通过；`Android` 远端为 `b1ab580`，`IOS` 远端为
`81ae16f`，两个分支工作区均干净并已推送。P1-04 的 iOS 传输源码和测试在两个分支逐字一致，
共享 schema/fixtures 未被修改。

当前结论：**P1 授权范围和审查门槛已完成，并已获用户确认；P2 已获明确授权。**
本核对不代表完整 App 已完成，不代表 A01—A11 已全部验收。D01、D03、正式发行范围等
未决事项继续保留；P2 仍须按独立任务逐项实施和验证。

详细的 IOS 同步复审见 [P1-04-IOS-SYNC 复审](p1-04-ios-sync-review.md)。

## P1-04 未知字段复审状态（历史记录，已由顶部状态更新）

用户已确认版本 1 课表 JSON 的顶层、semester、period、course 和 course schedule 对象
均严格拒绝协议未声明字段。共享 schema 和 Android 当前实现已经严格；iOS
`ScheduleDataTransfer.previewImport` 因默认 `JSONDecoder` 行为仍会忽略未知字段，需在
P1-04 统一。这样可以避免未知数据被静默接受后在再次导出时无提示丢失。

P1-04 已从基准 `cb7323f` 实施完成：iOS 导入边界在
版本检查后递归拒绝五层未知字段，保持不支持版本错误优先级；新增 7 项 iOS 单元测试。
API 37/JDK 17 的 Android 构建与双变体 JVM 测试通过（70 个任务实际执行）；扩展后的
19 例跨端探针逐项核对，五层未知字段均被 Android 与 Swift 拒绝；iPhone 17 Pro、iOS
26.5 Simulator 完整测试 93 项通过，失败/跳过均为 0。详细证据见
 [P1-04 任务记录](p1-04-unknown-fields.md)。本任务涉及跨端协议，已通过本轮独立专项复审；
未修改共享 schema、Android 行为、重复 ID、节次顺序、D01／D03 或 UI；独立专项复审通过，不进入 P2。后续另行
核对 P1 是否关闭，并安排同步到 `IOS` 分支。

## P1 阶段关闭核对状态（历史记录，已由顶部状态更新）

分析审查窗口已核对 P1-01、P1-03、P1-04 的授权范围、独立复审、构建／测试证据、
Android 分支和远端状态。三项子任务均已通过相应审查，`Android` 分支工作区干净，
本地 HEAD 与 `origin/Android` 一致。P1-04 的 iOS 导入修正目前只存在于 `Android`
分支提交 `4b7ff3e`，`IOS` 分支当前仍为 `c11bd2b`，因此跨分支同步和 IOS 分支复测
尚未完成。

当前结论：**Android 分支上的 P1 授权范围和审查门槛已具备关闭条件；IOS 同步和复测也已
完成。P1 阶段仍不标记为用户验收完成，不进入 P2。** 这不是用户验收；后续阶段需用户明确授权。

## P1-03-R4 最新专项复审状态（优先于下方历史，2026-09-08）

用户已授权继续在本机修复项目所需模拟器环境。基准 `4ee8919`，分支 `Android`，
开始工作区干净；本轮只维护文档和证据，不修改应用代码，不启动子 Agent。
已定位 AVD 登记 `target=android-0` 导致误识别 API 3、HVF 未启用；修正为
`target=android-37` 后，原稳定版 Emulator 37.1.11.0 成功完成 API 37 ARM64 开机。
安装 APK、两次冷启动、Activity／进程、截图和清空后无崩溃／ANR logcat 已实测，
关闭并带窗口再次开机也成功。此前“必须换真机或宿主”的限制已解除。

**运行证据已补齐并通过独立专项复审，P1-03 授权范围完成；P1 阶段关闭与用户验收
不在本次结论内，不进入 P2。**
详见 [R4 修复和运行记录](p1-03-validation.md) 的首节；下方旧设备阻塞和换宿主建议
仅作历史。未知字段策略现已确认并纳入 P1-04；D01／D03 和正式发行决定不变。本轮构建 70 项 up-to-date，
读取既有双变体各 20 项报告，不冒充重新执行 JVM 测试。
系统开发者模式按用户授权已开启，但单独开启未解决问题；未证明其必要性。
预览版仅隔离保留，实际成功使用稳定版，未修改项目依赖。
独立复审用临时只读登记副本重现 `android-0` 的 API 3／HVF／`mprotect` 失败，并用当前
`android-37` 登记在端口 5588 再次完成 API 37 开机、安装和冷启动；70 个 Gradle 任务
通过 `--rerun-tasks` 实际执行，双变体各 20 项测试通过。详见
[R4 独立证据](evidence/p1-03-r4-review-20260908.txt)。无需 Terra 继续排查环境；未知字段
已决定为两端严格拒绝，下一项为 P1-04，本轮不实施 P2。本次审查提交从交付消息或 git log 查询。


## 当前流程更新（优先于历史配置）

2026-09-08 用户确认试行“执行为主、分析按需、关键点审查”，规则见根目录 AGENTS.md 和实施计划。普通任务由执行窗口完成分析、实现、测试、交接状态维护、提交和推送；低风险任务不强制独立审查。复杂实施可直接建议 Sol／高，Astra 按需；分析窗口仍不修改应用代码。先观察 3–5 个实际任务的返工、交接及可获得的用量，再调整，不承诺最省。官方和中转站共用规则，用户只复制必要的完整交接块。

P1-03 的历史过程包括实施提交 `7d34c78`、R2 文档修正 `2e5b8ec` 和 R3 环境失败记录 `23743be`；这些步骤当时仍缺 API 37 设备证据。此后 R4 已定位 AVD 登记问题并补齐设备运行证据，最新结论以本文首节和 [P1-03 专项复审](p1-03-review.md) 为准。

## 当前分支安排（优先于下方历史记录）

2026-09-08 用户确认使用 `Android`、`IOS` 两条开发分支和 `main` 稳定分支。当前工作目录留在 `Android`，所有安卓窗口接手先核对这一分支。旧 `codex/ios-ui-redesign-demo` 保留在 `8791dbe` 作为历史，不再用于日常开发。

建分支前已确认工作区干净、远程没有同名新分支；`git diff --exit-code bef808b HEAD -- ios` 通过，当前 iOS 文件与最后 iOS 提交一致。两个新分支从包含本轮规则的同一完整快照起步，均保留全部目录和历史，避免 iOS 分支丢失新的协作／同步规则。分支规则提交 `c11bd2b` 已上传 `origin/Android`；此操作不代表任一应用新增验收通过。

本轮新增分支规则验证；文档测试和远程核对结果在最终交付报告。后续每次提交推送对应开发分支，稳定阶段合并 `main` 需明确安排。下方旧分支名与旧推送范围只作历史，不作为当前操作指令。

## GitHub 同步交接（历史）

2026-09-08 用户确认正式仓库为 [QingKeSchedule](https://github.com/SukiBanQin/QingKeSchedule)，常规交付需要提交并推送同名任务分支。当前分支 `codex/ios-ui-redesign-demo`，原 `origin` 指向旧名 `School_timetable`，本轮更新为指定仓库（保留 SSH 443 传输方式）。

同步前本地 HEAD 为 `e4df5fb`；远程仅有 `main`，头提交 `552c045`，与本地比较为远程独有 1、本地独有 67 个提交。因此本轮上传开发分支，不覆盖或合并 `main`，不强制推送。新增开发分支的实际推送结果及本轮提交编号见最终交付消息，接手时用 `git ls-remote origin refs/heads/codex/ios-ui-redesign-demo` 核对，不把上传计划当作已成功。

本轮仅修改同步规则及文档验证，未重新验收应用。文档验证结果在交付时报告。

## 本轮协作配置更新

2026-09-08 用户确认：默认分析审查模型改为 Sol（日常中、正式审查高），执行仍默认 Terra；Astra 仅作为疑难问题升级选项。窗口和交接按角色命名，具体建议见实施计划。本轮仅维护文档与对应验证，不修改应用代码、不重做无关历史审查，不切换实际模型设置。当前实际窗口模型和档位未核实。P1-01-R2 实施提交 `985cdd6` 已由分析审查窗口独立复核通过；下方上一轮“待执行／待审查 R2”的描述属于历史，勿重复派发。旧审查记录里的 Astra 名称保留作历史，不要求接手者继续使用 Astra。

本轮文档验证：文档测试 16 项、既有文档和布局测试、工作区与暂存补丁检查通过。P1-01-R2 有独立应用验证；D02 授权只代表允许开始 P1-03，不等于升级已经完成、整个 P1 通过或用户验收通过。

## 最近更新与阅读入口

更新时间：2026-09-08。P1-01、P1-03、P1-04 及 P1-04-IOS-SYNC 均已通过独立复审；API 37 模拟器环境阻塞已解除，两个开发分支已同步。用户已确认 P1 结果并授权进入 P2，下一项为 P2-01，详见 [P1-04 任务记录](p1-04-unknown-fields.md) 和 [IOS 同步复审](p1-04-ios-sync-review.md)。

先读 [根目录规则](../../AGENTS.md)，再按任务阅读 [功能对照](product-baseline.md)、[技术方案](technical-design.md)、[实施计划](implementation-plan.md)。接手时检查 `git status --short`、`git branch --show-current` 和 `git log -5 --oneline`，不能只信文档中的状态。

## 已确认决定

- Android 首版复现当前 iOS 已有功能、业务行为和视觉风格，系统交互按安卓方式实现。
- `Android/` 存放安卓代码，`docs/Android/` 存放本文档，保持大小写一致。
- 官方／中转站各一个分析审查窗口（默认 Sol）和执行窗口（默认 Terra），采用人工提示词交接；Luna 按需增加，暂不启用自动委派。
- 分析窗口不修改应用代码；本轮用户明确要求写文档，因此允许文档和相关验证改动。
- 延续每次改动更新测试、验证并创建 Git commit 的仓库要求。
- 用户已确认直接交接：模型最终回复末尾给出唯一一段完整中文交接块，标明接收窗口和建议档位；用户只复制，不查找提示词或整理结果。执行窗口默认维护任务状态，分析审查窗口在需要审查时维护结论，具体任务的更窄授权优先。

## 建议与待决定事项

- Kotlin + Compose、Room、DataStore 和 Mac 主力开发是已提出的建议；本轮确认 P1 采用 Kotlin + Compose 单 app 工程，Room/DataStore 仍按阶段计划在 P2 接入。
- D01：是否让备份携带教学日历设置并同步扩展 iOS 协议；现有版本 1 不携带这些设置。
- D02：已确认包名 `com.qingke.schedule`、最低 API 26。P1-03 已采用 API 37.0 + AGP 9.4.0 + Gradle 9.6.0 + Build Tools 36.0.0 + JDK 17；保持 minSdk 26，工具链、Android 17 适用性和 API 37 设备运行门槛均已通过专项复审。API 37.1／37.2 没有本轮核实的完整官方兼容映射，不纳入此次升级。
- D03：精确提醒不可用时的降级方式与文案尚未确定。
- D04：已确认 P1 仅做个人安装验证和可复现 debug 构建，不配置商店签名；正式发布范围仍未确定。
- 用户已确认版本 1 未知字段两端严格拒绝；P1-04 已在 `Android` 分支通过独立专项复审，P1-04-IOS-SYNC 已在 `IOS` 分支同步并通过复审。用户已确认 P1 结果并授权进入 P2，下一项为 P2-01。实际窗口模型和档位未核实，不要替用户变更设置。

## 当前代码与既有改动

初始 iOS 参考提交：`dabdc2eae41143b1288ae5f9ba5eabcebce6de5a`。本次文档提交另从交付消息或 Git 历史查询，不使用自引用提交编号。

开始建档时已有以下未提交文件，后已按用户授权独立提交：

- `ios/QingKeSchedule.xcodeproj/project.pbxproj`
- `ios/QingKeSchedule.xcodeproj/xcshareddata/xcschemes/QingKeSchedule.xcscheme`

历史窗口在 `525910f` 后按用户授权将两处 iOS 工程配置单独提交为 `bef808b`。本轮开始 HEAD 为 `23743be`、分支为 `Android`，本地与 `origin/Android` 一致，工作区干净；相关 iOS 领域/传输与共享协议未改动。

首轮审查时已核对：分支 `codex/ios-ui-redesign-demo`，开始 HEAD `85e3234`。当时两文件为未提交改动，后由用户授权提交为 `bef808b`；pbxproj 为开发团队和包标识设置，scheme 为测试并行属性及 XML 换行。它们没有进入本轮文档暂存范围。相关 iOS 源码与共享协议自初始基准以来无提交变化。后续接手仍需重新核对现场。源码阅读不代表已执行 iOS 构建、模拟器、UI 或真机验收。

## 已完成与未完成

- 文档已由用户确认；P1-01 实施提交 `9b521db` 已逐项核对。实际父提交 `0a498b9`，前置 `f6a920a`／`0a498b9` 是交接和参数确认文档；后续 `85e3234` 是流程文档，不是实现内容。
- 已实现并复跑测试：单 app Compose 工程、教学周／单双周、最小 DTO／校验和共享 fixture 解码。未实现完整存储、状态、页面和系统能力，A01—A11 未完整验收。
- 首轮已审查，结论未通过：数值字符串被接受／整数数值表示误拒绝、非法 UTF-8 被替换后接受、年份 0000 与 iOS 不一致。具体位置、影响和复现见 [审查记录](p1-01-review.md)。
- 已确认决定：版本 1 未知字段按共享 schema 严格拒绝；Android 已符合，Swift 导入边界待 P1-04 修正。重复 ID／节次编号顺序实测两端均宽容，不自行加严。
- 修正提交 `3924d26`、`985cdd6`、P1-03-R4、P1-04 提交 `4b7ff3e` 及 IOS 同步提交 `0a3252d` 均已通过独立复审。版本 1 未知字段策略已决定为两端严格拒绝；P1 授权范围审查已完成并获用户确认，P2 已获授权。
- 本轮同步纠正功能基线和技术方案中“待确认／没有 Wrapper”等过时状态，不修改 iOS 基准或擅自扩大协议。D01、D03、正式发行和目标机型仍待后续决定。

## 验证记录

2026-09-07 首轮审查独立执行；以下应用结果属于修正前，不能作为 `3924d26` 已通过的证据。完整命令与探针结果见 [审查记录](p1-01-review.md)。

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| 默认 `./gradlew assembleDebug test --rerun-tasks` | 失败 | SDK location not found，测试未启动 |
| 设置实际临时 ANDROID_HOME 后同命令 | 通过 | 68 个任务全部执行；debug APK；debug/release 各 12 项 JVM 测试，无失败/错误/跳过 |
| `python3 docs/tests/android-contract-review-probe.py` | 已执行，发现差异 | 10 个同输入案例调用 Android decoder 和 macOS 编译的原 Swift previewImport；执行成功不代表兼容通过 |
| adb 设备清单 | 无连接设备 | 未安装或启动应用 |
| `python3 docs/tests/android-documentation.test.py` | 通过，7 项测试 | 文档结构、链接、阶段、基准和新增审查交接约束 |
| `bash docs/tests/documentation.test.sh` | 通过 | 既有文档约束回归 |
| `bash docs/tests/repository-layout.test.sh` | 通过 | 既有仓库布局回归 |
| `git diff --check`、`git diff --cached --check` | 通过 | 工作区与本任务暂存文档补丁空白检查 |

未运行：Android 安装/UI/模拟器/真机、iOS 完整构建/测试/设备、Web 测试。未做 Windows 或全新依赖缓存复现。临时 SDK 路径只用于记录此次实测，不能当作长期环境配置。文档验证通过不代表 P1-01 审查通过。

P1-02-R2 已取得 API 35 ARM64 设备安装、两次冷启动、Activity、截图和 logcat 证据。该证据不替代 P1-03 所需的 API 37 设备证据；P1-03 主机侧专项复审结果见 [独立记录](p1-03-review.md)。

### P1-01-R2 独立复审证据

| 检查 | 状态 | 范围 |
| --- | --- | --- |
| `./gradlew clean assembleDebug test --no-daemon --console=plain`（显式配置临时 SDK/JDK 17） | 通过 | 69 个任务执行；Debug/Release 各 20 项，失败／错误／跳过均为 0 |
| `python3 docs/tests/android-contract-review-probe.py` | 通过并人工核对结果 | 16 例；四种非法数字两端均拒绝，合法指数两端均接受；未知字段差异保持未决 |
| 修复前后临时探针日志 | 存在且一致 | diff 只显示四种非法数字的 Android 结果由接受变为拒绝；临时日志不替代本轮独立探针 |
| APK 元数据检查 | 通过 | `com.qingke.schedule`、minSdk 26、targetSdk 35 |
| 本轮设备／升级工具链 | 未运行 | API 35 ARM64 启动沿用既有已复审证据；未运行 AGP 9.4、CI、Windows、iOS 全量或真机测试 |

### P1-03 独立专项复审证据

复审范围为 `7d34c78^..7d34c78`，实际 14 个文件，与执行报告一致；没有应用 Kotlin
源码、iOS、Web 或共享 schema／fixtures 改动。执行者未提交完整构建控制台日志，复审
没有把其自述当作证据，而是重新运行可用检查并读取新生成的报告。

| 检查 | 独立结果 |
| --- | --- |
| 官方兼容与依赖解析 | API 37／AGP 9.4.0／Gradle 9.6.0／Build Tools 36.0.0／JDK 17 符合兼容表；KGP、Compose compiler、serialization plugin 均为 2.2.10 |
| 在线与离线 clean 双变体构建／测试 | 均通过；各 100 个任务；Debug／Release 各 20 项，无失败／错误／跳过 |
| `lintDebug` 与 APK 元数据 | 均通过；包名正确，minSdk 26、targetSdk 37 |
| 跨端探针 | 16 例实际执行并逐项核对；结果与执行记录一致，未知字段差异保持未决 |
| API 37 ARM64 模拟器 | R2 复审用官方启动器禁快照、无窗口、软件图形后仍复现 HVF 未启用与 QEMU `mprotect` 拒绝，ADB 持续 offline；判定为当前环境限制，设备门槛仍缺证据 |
| 结论 | 工具链主机侧及 R2 源码／依赖边界通过专项复审；P1-03 整体暂不通过，下一步为 P1-03-R3 设备环境恢复与运行验证 |

完整命令、范围和修正要求见 [P1-03 专项复审](p1-03-review.md)。

## P1 实施准备记录（历史）

以下保留进入 P1 前的讨论依据；D02 和首轮 D04 的后续确认见本节末尾，旧建议不是需要重复询问的事项：

- P1 工程建议先采用 Kotlin + Compose + 单 `app` 模块，领域与 JSON 契约先不依赖 Android UI；Room、DataStore 在 P2 再接入。这样首个提交可独立验证构建、日期规则和共享协议。
- D02 建议首轮以当前可获得的 Android Studio/SDK 稳定组合为准，最低版本优先选择 API 26（Android 8.0），目标版本使用安装环境可用的最新稳定 API；包名建议暂用 `com.qingke.schedule`。这些参数影响 Gradle 配置和真机覆盖，需用户确认后锁定。
- D03 不阻塞 P1 领域与协议测试；提醒实现前建议采用“降级为系统允许的非精确提醒，并在设置页明确提示可能延迟”的方案，具体文案留待提醒阶段确认。
- D04 不阻塞 P1；P1 只产出可复现的 debug 构建和测试，不配置商店签名或发布流水线。
- D01 不阻塞 P1；P1 仅兼容现有版本 1 JSON，不修改 iOS、共享 schema 或备份协议。

已收到的 P1 参数确认：

用户已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；P1 仅做个人安装验证（debug/reproducible build），不做商店发布签名。上述决定不等同于 D01、D03 或 D04 的完整产品决定。

## 给 Terra 的第一项实施提示词（历史，不要重复执行）

```text
任务编号与阶段：P1-01，工程与规则基础
角色：执行；仅实施本次范围，不完成整个 App。
建议模型／思考档位：Terra／中（本轮补充建议，不代表此前执行实际设置）。
选择理由：涉及工程、稳定依赖和基础规则测试，需要中档起步。
目标：在 Android/ 建立可构建的 Kotlin Android 工程（Compose、单 app 模块），固定经过验证的稳定依赖；实现与 Android UI 无关的最小领域/JSON 契约骨架，覆盖教学周计算、单双周判断、版本 1 数据解码与业务校验，并接入共享 fixtures。
代码基准／当前分支：基于提交 78e362e；当前分支 codex/ios-ui-redesign-demo。开始前运行 git status --short --branch、git log -5 --oneline，保留两处既有 iOS 工程配置改动，不覆盖、不暂存、不提交它们。
必读文档与参考源码：AGENTS.md；docs/Android/handoff.md；docs/Android/product-baseline.md；docs/Android/technical-design.md；docs/Android/implementation-plan.md；ios/Shared/schedule-data.schema.json；ios/Shared/fixtures/manifest.json 及其 fixtures。
允许修改的路径：Android/**；必要的 Android 测试与构建配置仅限 Android/**。不得修改 ios/**、web/**、共享 schema/fixtures 或 docs/Android/**（交接记录由分析窗口维护）。
已确认决定及不能自行决定的差异：目标是复现 iOS；P1 不扩展 D01 协议。D02 已确认：包名 `com.qingke.schedule`、最低 API 26、目标 API 采用实施环境可用的最新稳定版本；D04 当前仅限个人安装 debug 验证。不得自行扩展 D01、D03 或 D04 的范围。
验收条件：./gradlew assembleDebug 可复现通过；./gradlew test 通过；JVM 测试覆盖教学周边界、单双周、版本/必填字段/颜色/日期时间/课程安排校验、semester:null，以及有效和无效共享 fixtures；未知版本、非法业务数据和超出输入上限按文档拒绝。提供实际命令与输出摘要。
交付：修改摘要、测试文件和结果、构建命令和结果、提交编号、未完成项/限制、下一步建议。只暂存 Android/** 并创建一个独立 Git commit；完成后把提交号和证据回传，并说明交接记录待分析窗口更新。
```

## 下一步

用户已确认 P1 结果并授权进入 P2。下一项为 P2-01“持久化基础与状态边界”，由执行窗口
先实现最小可测试存储接口、状态加载／保存边界和失败回滚契约；具体范围、文件和验收条件由
分析窗口交接块明确。未完成 P2-01 前不安排 P2-02 或完整 App 实现。

P1-03-R4 已通过专项复审，P1-03 无后续修正任务。用户已决定两端严格拒绝版本 1
未知字段，P1-04 及 P1-04-IOS-SYNC 已通过独立专项复审并完成同步。以上为历史交接内容，
当时没有 P2 授权；当前以本文顶部状态为准，不重复派发 P1 或自动扩大 P2 范围。

### P1-03 已授权范围（历史，已实施）

- 目标组合：`compileSdk`／`targetSdk` 37（API 37.0）、AGP 9.4.0、Gradle 9.6.0、Build Tools 36.0.0、JDK 17；`minSdk` 26、包名和 namespace 保持不变。
- 处理 AGP 9 built-in Kotlin 迁移，按官方迁移方式调整 `org.jetbrains.kotlin.android`；保留并实测 Kotlin serialization 与 `org.jetbrains.kotlin.plugin.compose`。插件版本必须与 AGP 内置 Kotlin 实际兼容，不能只保留 2.0.21 后假定成功。
- Compose BOM 2024.12.01 和其他 AndroidX 依赖默认保持；只有构建或运行证据证明必须升级时才做最小调整并说明依据。不得借机修改业务规则、JSON 未知字段策略、iOS、Web、共享 schema/fixtures、Room/DataStore 或页面功能。
- 允许维护本任务 `docs/Android/handoff.md` 及必要文档验证；允许修改 Android 工具链和构建文件、Wrapper、`Android/README.md`，以及为验证真实工具链回归所必需的 Android 测试。主应用 Kotlin 源码只有在工具链编译迁移确实要求时才能最小修改，并须单独说明原因和行为不变证据。
- 验收至少包括：核对官方兼容资料与稳定 SDK 包；`./gradlew --version`；JDK 17 下 clean Debug/Release 构建和双变体 JVM 测试；一次缓存就绪后的离线 clean 构建；16 例跨端契约探针；APK 元数据确认 minSdk 26、targetSdk 37；API 37 ARM64 模拟器或等效设备安装、两次冷启动、Activity/进程和无崩溃 logcat。运行 `lintDebug` 并记录结果。
- 若 API 37 system image、AGP/Kotlin 插件或设备环境不可用，保留最小错误证据并回传，不降级目标、不把 API 35 历史证据冒充本轮通过。只提交本任务改动并推送 `Android`；执行窗口回传提交范围、完整命令、测试数量、设备信息、限制和复审请求。
- P1-03 完成后只申请复审，不进入 P2，不宣称 P1 或用户验收通过。

## 后续更新约定

每次阶段结束更新本文件的当前状态，保留决定理由及相关提交；较旧细节可从 Git 查阅，不无限追加聊天。执行者给出提交编号、测试证据、未完成项；审查者记录检查范围和发现。重要决定或切换服务前及时保存，即使任务尚未完成，也如实记录最后操作和未提交内容。


## D02 工具链兼容矩阵核查（历史任务，已完成记录）

由用户授权在 iOS 配置提交 `bef808b` 后，下一步交给 Terra 只核查稳定 SDK 与构建工具兼容组合，不修改应用代码或依赖。未知字段策略仍待产品决定。


## 2026-09-08 独立复核证据

- 实际 `55eff97` 只新增 D02 文档；后续 `5613f23`／`398fa43` 已有两次复审，本轮未把它们当作独立证据。AGP 9.4 参数核对正确；AGP 9.1.1 也支持 API 37.0，已消除“最低要求”的误解。
- `sdkmanager --list --channel=0` 退出 0，除交接列出的平台外还看到无 beta 后缀的 android-37.2；[清单节选](evidence/d02-sdk-list-20260908.txt) 已保存。37.1／37.2 的 AGP 映射未核实，未把包可见性写成项目兼容。
- 当前 API 35 工程 `assembleDebug test --rerun-tasks --console=plain` 独立通过，68 个任务执行，debug/release 各 21 项，无失败／错误／跳过。扩展 16 例跨端探针已运行，确认 4 种非法数字仍被 Android 接受；应用审查未通过。
- 本轮未做升级构建、CI、Windows、设备/iOS 全量复测；沿用已明确来源的 API 35 历史启动证据，不冒充本轮执行。
- 文档验证：`android-documentation.test.py` 11 项通过、`documentation.test.sh` 和 `repository-layout.test.sh` 通过，`git diff --check`／`git diff --cached --check` 通过。只提交本轮文档与相关验证。
