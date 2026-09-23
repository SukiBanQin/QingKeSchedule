# D04 青课 Android 1.0 GitHub 公开发布（2026-09-23）

状态：**GitHub Release 已公开发布；发布动作完成。** [青课 Android 1.0 Release](https://github.com/SukiBanQin/QingKeSchedule/releases/tag/v1.0) 的 `v1.0` 标签指向 `Android` 分支提交 `38907cb81189b035577f475cc93e40de8ae3fb82`，未合并 `main`。可下载资产为 [`QingKeSchedule-1.0.apk`](https://github.com/SukiBanQin/QingKeSchedule/releases/download/v1.0/QingKeSchedule-1.0.apk)。

## 发布核对

- GitHub Release 页面显示正式发布、Latest、目标提交 `38907cb`，资产仅有一份 APK（另两项为 GitHub 自动生成的源码归档）。
- GitHub 页面给出的 APK SHA-256 为 `dd52315f3bc369dd6189bb820ea709cc31218521f733ce384ab88388758f8e51`，与本机签名 APK 一致。`v1.0` 标签和远程 `Android` 在发布后都解析到 `38907cb81189b035577f475cc93e40de8ae3fb82`。
- 公开说明写明 Android 8.0 以上、手动覆盖升级、Debug 签名迁移、提醒的已知限制与 SHA-256，并链接标签下的[下载和升级说明](https://github.com/SukiBanQin/QingKeSchedule/blob/v1.0/docs/Android/release-v1.0.md)。
- 正式 APK 仍为 `com.qingke.schedule`、`versionCode=1`、`versionName=1.0`、单个 v2 签名者。发布前已在 API 37 ARM64 模拟器验证首装和同签名递增版本覆盖升级后课表保留；Debug／Release JVM 各 283、设备 202 项通过，lint 0 errors／24 warnings。详细证据见[发布准备记录](evidence/d04-release-v1.0-20260923.md)及[独立复审](d04-release-v1.0-review.md)。

## 签名密钥保管

签名私钥不在 Git、Release 资产或聊天中。仓库外 PKCS#12 原件位于 `~/Library/Application Support/QingKeSchedule/AndroidRelease/release-key.p12`；加密 keystore 的两个副本位于本机 `~/Documents/QingKeSchedule-Release-Key-Backup/` 与私人 iCloud Drive `QingKeSchedule-Release-Key-Backup/`，已逐字节核对。iCloud 云端同步完成状态没有独立设备核验，之后可由用户在另一台设备检查。

发布前曾为安全起见更换 keystore 密码，**签名私钥和证书没有更换**；两个副本同步换成新密码保护的 keystore。新密码保存在本机登录钥匙串及用户明确同意的 Apple“密码”App，二者已通过安全读取后逐字节比对一致。用新密码重新运行签名构建脚本成功，最终 APK SHA-256 仍与已上传文件一致；剪贴板中的签名密码已清除。密码和私钥内容均未写入仓库或本记录。

## 后续边界

GitHub Release 不提供 App 内自动更新；后续版需继续使用同一包名和签名私钥，提高 `versionCode`，再发布新 APK。P6 已验收但未测的自然长时待机、完整手动拒绝权限路径、非精确提醒长期待机、其他厂商投递及 A10 iOS App 真实文件 UI 往返保持原记录，不因公开发布变成已验证。实体手机本轮未连接、未修改其数据。
