# P3-08／A08 第三批证据：14 天滚动窗口兜底（2026-09-20）

本目录只记录 P3-08／A08 第三批（内部维护闹钟：窗口兜底）的设备证据，不覆盖第一／二批证据
（见 `../p3-08-a08-reminders/` 与 `../p3-08-a08-reminders-batch2/`）。

## 环境

- 设备：`emulator-5554`（AVD `qingke-api37-r3-arm`），API 37（Android 17）ARM64，`wm size 1080x2400`、
  `density 420`、动画缩放 0。
- 应用：同一份 debug APK；用 `adb install` + `am instrument` 直接运行，因为 Gradle
  `connectedDebugAndroidTest` 结束后会卸载应用并清除设备侧证据目录。

## 证据

| 文件 | 内容 |
| --- | --- |
| `maintenance-alarm-dump.txt` | 真实 `dumpsys alarm` 中该兜底的登记条目：一条 `RTC_WAKEUP` 闹钟，属于本包，tag 为固定 action `com.qingke.schedule.action.REMINDER_MAINTENANCE` |
| `device-verification-20260920.txt` | 两次专门运行的实际命令与结果（`OK (3 tests)`／`OK (1 test)`）、appops 状态 `ignore`、各断言内容与未验证清单 |

## 覆盖的行为

- 窗口为空（下一次课程在窗口外）时兜底仍存在，且不进入注册表、不计入 `activeCount`／`degraded`、不发通知。
- 重复 reconcile（含维护触发）只保留一个固定身份，`dumpsys alarm` 条目数始终为 1。
- 关闭提醒（`cancelAll`）或投递能力不可用时兜底被取消；能力恢复或重新开启后重新登记。
- 触发后注册表与 AlarmManager 保持一致，并重新安排下一次维护。

## 未验证（不得声称通过）

1. 兜底的真实到期触发（周期 7 天，自动化不等待；用显式广播触发同一接收器与处理函数）。
2. 真实重启后的 BOOT_COMPLETED 投递与系统受保护广播。
3. Doze／休眠与厂商真机后台限制下的实际投递时序。
4. 用户可见通知观感（本批不新增通知）。
