# P3-08／A08 第二批证据：提醒设置页与真实流程（2026-09-20）

本目录只记录 P3-08／A08 第二批（提醒设置 UI、运行时权限流程、提交后自动重算与关闭取消）的设备证据，不覆盖
第一批规划器／平台基础设施证据（见 `../p3-08-a08-reminders/`）。

## 环境

- 设备：`emulator-5554`（AVD `qingke-api37-r3-arm`），API 37（Android 17）ARM64，`wm size 1080x2400`、
  `density 420`、`font_scale 1.0`，动画缩放 0。
- 应用：同一份 debug APK；截图与权限拒绝记录用 `adb install` + `am instrument` 直接运行，因为 Gradle
  `connectedDebugAndroidTest` 结束后会卸载应用并清除通知与设备侧证据目录。

## 证据

| 文件 | 内容 |
| --- | --- |
| `settings-reminders-enabled.png` | 通知权限已授权时的真实设置页「04 上课提醒」面板：开关开启、预设与「自定义…」、通知权限「已授权」、提醒渠道「可用」、精确闹钟「不可用」、D03 说明、状态「已安排最近 2 条课程提醒（部分可能延迟）」、精确闹钟设置入口 |
| `settings-reminders-permission-denied.png` | 预先 `pm revoke` POST_NOTIFICATIONS 后的同一面板：通知权限「未开启」、红色状态「系统通知权限未开启，提醒不会投递」、「开启通知权限」与「系统通知设置」两个显式操作 |
| `settings-reminders-recovered-after-grant.png` | R1：预撤销 POST_NOTIFICATIONS → 开启提醒（无闹钟）→ 授予权限 → 调用能力恢复入口后的真实面板；与 `settings-reminders-enabled.png` 字节相同（MD5 `68ae63e4fc1f3f464304eb1a690f9ec5`），即恢复后的状态与一开始就有权限时一致 |
| `device-verification-20260920.txt` | 三次专门运行的实际命令与结果（`OK (3 tests)`／`OK (1 test)`／`OK (1 test)`）、appops 状态 `ignore`、断言内容与未验证清单 |

## R1 覆盖的能力恢复

- 权限结果成功（或从系统通知／渠道／精确闹钟设置返回）时，生产入口幂等执行 `reconcile` 并用结果更新 UI；
  权限仍被拒绝时只刷新状态，不重复弹窗（只有页面开关与显式按钮会发起申请）。
- 设备断言：预撤销权限 → 开启偏好（注册表与 AlarmManager 均为空）→ 授予权限（不重启、不再次修改设置）→
  能力恢复后注册表非空且 AlarmManager 登记数与注册表一致；从系统设置返回（handoff + 前台恢复）会重新协调并
  增长注册表 generation，普通恢复不会。
- 精确闹钟能力不能在设备上翻转：撤销该 appop 会让系统立刻杀死应用进程，因此该能力的恢复路径由 JVM 用例与
  第一批 `ReminderPlatformTest` 覆盖。

## 未验证（不得声称通过）

1. 真实系统权限弹窗的人工观感（自动化只断言拒绝后的界面状态）。
2. 真实重启后的 BOOT_COMPLETED 投递与系统投递的受保护广播。
3. Doze／休眠唤醒与精确／非精确闹钟的真实投递时间。
4. 用户可见提醒通知的观感验收（第一批截图仍只作程序化证据）。
