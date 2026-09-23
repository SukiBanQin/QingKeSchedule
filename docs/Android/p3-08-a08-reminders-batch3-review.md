# P3-08／A08 上课提醒第三批独立技术复审

## 结论（2026-09-20）

提交 `19bf31e` 与证据修正 `123a5ff` **通过 Sol 独立技术复审**。复审范围为
`c4b3a4b..123a5ff`；未发现阻断问题，也未发现越界修改 iOS、Web、Room schema、DataStore 用户偏好键、
共享 JSON schema／版本 1、A10／A11 或 `main`。

第三批用独立接收器、action、固定 URI 与独立 requestCode 建立一个内部维护闹钟。它在 14 天窗口的一半
（生产配置为 7 天）重新协调，能够在当前窗口没有课程、App 也不再启动时继续推进窗口。维护闹钟不进入课程
提醒注册表、不计入 `activeCount`／`degraded`、不发布通知，也不依赖精确闹钟权限。

## 复审确认

- `ReminderMaintenanceReceiver` 只执行 `WINDOW_MAINTENANCE` 协调；生产 PendingIntent 使用显式接收器、独立
  action、`qingke://reminder-maintenance/window` 与 `MAINTENANCE_REQUEST_CODE = 1`，不会替换课程提醒。
- 每次 `reconcile` 都以同一身份重排维护闹钟；应用启动、课程触发、维护触发、数据／偏好写入和既有系统重建
  入口都会续期。关闭提醒、通知权限缺失或渠道不可用时取消，能力恢复后重新建立。
- 维护调度／取消失败以固定 URI 进入既有 `failed` 诊断；课程提醒注册表、活动数量与降级状态仍只由最终课程
  活动集合产生。`cancelAll` 的课程取消失败保留语义未被改变。
- API 37 证据中的 `dumpsys alarm` 显示真实、唯一的 `RTC_WAKEUP` 维护条目；重复协调仍为一条，显式触发后
  不发布通知并重新登记。证据明确区分了显式广播与真实等待 7 天到期，没有扩大结论。

## Sol 独立验证

- Debug／Release 提醒包 JVM 各 **57 tests、0 failures／errors／skipped**。
- API 37 ARM64 `ReminderMaintenanceTest` **3 tests**、`ReminderReceiverTest` **6 tests**，均为
  **0 failures／errors／skipped**。
- 执行者报告的完整 Debug／Release JVM 各 241、完整设备 165、构建、lint、文档测试及证据记录已核对；
  本次未重复全量 241／165 项、完整构建或 lint。

## 保留限制与状态

7 天是 14 天规划窗口内的工程安全余量，不是 Android 或厂商对实际唤醒时间的承诺。真实到期触发、真实重启后的
`BOOT_COMPLETED`、系统受保护广播、Doze／休眠和厂商后台策略仍未验证；发生维护调度失败时，仍依赖下一次应用
启动、数据变化或系统重建入口重试。这些限制已由实施记录和设备证据如实保留，不阻断本批技术复审。

A08 第一、二、三批的应用实现与 Sol 技术门槛现已关闭，第二批设置页视觉／交互已获用户确认；真实系统权限弹窗、
用户可见课程通知观感及真机后台投递尚未完整验收，因此不得据此宣称 A08 产品验收、整个 P3 或完整 App 完成。
P3-07-R1 新增警告框与文案仍待用户验收，A10／A11 未推进。
