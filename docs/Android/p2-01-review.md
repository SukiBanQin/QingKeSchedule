# P2-01 独立复审记录

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
