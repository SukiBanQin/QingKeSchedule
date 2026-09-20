# P3-08／A08 上课提醒第一批 R1／R2 证据（2026-09-20）

本目录只记录 P3-08／A08 第一批（纯规划器与 Android 平台基础设施）及其 R1 返修，不覆盖其他任务证据。

## 环境

- 设备：`emulator-5554`，API 37（Android 17）ARM64，`wm size 1080x2400`、`density 420`、`font_scale 1.0`。
- 应用：同一份 debug APK；截图与权限撤销记录使用 `adb install app-debug.apk` + `am instrument` 直接运行
  证据用例，因为 Gradle `connectedDebugAndroidTest` 会在结束后卸载应用、通知随之消失。

## 证据

| 文件 | 内容 |
| --- | --- |
| `notification-shade-api37.png` | 真实通知栏截图（R1 重新采集）：标题「证据课程」、正文「08:00–08:45 · A101 （可能延迟）」，位于提醒（非静默）分区，说明渠道可用、正文与 D03 非精确标记都真实呈现 |
| `device-verification-20260920.txt` | 证据用例记录：计划提醒 URI／fireAt／startAt／title／body／exact、reconcile 的 submitted／unchanged／cancelled／active／generation、三项能力（notificationsPermitted／channelReady／exactAlarmsAvailable／degraded）、渠道 id／name／importance、活动通知标题与正文，以及本环境无法验证的项目清单 |
| `permission-denied-20260920.txt` | R1／R2：预先撤销 POST_NOTIFICATIONS 的专门运行记录（appops 状态 `ignore`、`OK (1 test)`、logcat 中的 `permitted=false`），验证投递前抑制、不先通知后取消、不把静默未发布报告为 Delivered；R2 起该用例按通知 tag（完整提醒 URI）并辅以 `android.title` 检查通知栏，常规已授权分支必须真正投递成功 |

关键量化（当次设备运行）：

- 提醒身份：`qingke://reminder/0/evidence/0/slot/1/2026-09-21`（课程／安排 ID + 位置 + 教学周 + 实际日期 + 来源判别）。
- 能力：`notificationsPermitted=true`、`channelReady=true`、`exactAlarmsAvailable=false` → **degraded=true**，
  即当前 AVD 未授予 SCHEDULE_EXACT_ALARM，系统按 D03 走非精确提醒并在正文标注「（可能延迟）」。
- 渠道：`course_reminders`／「上课提醒」／importance=4（HIGH）。
- 协调器：一次 `reconcile` 提交 2 条闹钟、unchanged=0、cancelled=0、active=2，generation 递增到 4。

## R1／R2 在设备上覆盖的行为

- 通知身份：以完整提醒 URI 作为通知 tag（id=0），`Aa`／`BB` 这类 Java hash 碰撞不会互相覆盖或误取消
  （`ReminderPlatformTest.notificationIdentityUsesTheUriTagSoHashCollisionsCannotOverrideEachOther`）。
- 渠道被关闭：平台把 IMPORTANCE_NONE 报告为不可用；用例使用私有探测渠道验证，避免把共享提醒渠道推进 NONE
  ——平台会跨应用侧删除／重建保留这个已关闭状态，之后无法恢复。
- 权限撤销：见 `permission-denied-20260920.txt`；R2 修正了断言对象（通知 tag 与 `android.title`，不再拿正文与标题比较），
  并让常规已授权分支断言明确的 `Delivered` 结果。
- 重建不信任注册表：先清空平台闹钟再以 `BOOT_COMPLETED` 动作重建，验证按预期重新提交
  （`ReminderReceiverTest.rebuildResubmitsWhenThePlatformLostItsAlarms`）；受保护广播本身仍无法由应用发送。

## 本环境无法验证的项目（不得声称通过）

1. 真实重启后的 BOOT_COMPLETED 投递（只验证了清单声明、共享重建处理函数与重建重提交行为）。
2. 系统投递的 TIME_SET／TIMEZONE_CHANGED／MY_PACKAGE_REPLACED／SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
   （这些是受保护广播，应用无法自行发送）。
3. 休眠／Doze 唤醒与精确／非精确闹钟的真实投递时间（只能验证能力判定与非精确降级标记）。
4. 用户可见的通知观感验收（本截图是程序化采集，不等于用户验收）。

## 未纳入本批（A08 仍未完成）

- 提醒设置界面、运行时权限申请流程、课表编辑／保存后自动重算。
- 超出滚动窗口的再排程策略（当前依赖每个已触发闹钟与各重建入口推进窗口）。
