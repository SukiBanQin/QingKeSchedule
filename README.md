# 青课课表

青课帮助学生查看和整理课程安排。仓库包含 Android App 与 iOS App 源码；目前公开提供下载的是 Android 1.0。iOS 尚无公开安装包或 App Store／TestFlight 下载版本。

## Android

青课 Android 版支持查看今日课表和周课表、编辑课程与教学日历、设置上课提醒，以及通过 JSON 文件导入或导出课表。

### 下载与安装

需要 Android 8.0（API 26）或更高版本。

[前往 GitHub Releases 下载青课 Android 1.0](https://github.com/SukiBanQin/QingKeSchedule/releases/tag/v1.0)，下载 `QingKeSchedule-1.0.apk`。安装前请核对 Release 页面提供的 APK SHA-256。打开 APK 后，若系统提示，请在 Android 设置中允许当前浏览器或文件管理器安装此来源的应用，再按系统提示完成安装。

如果设备上安装的是开发期间的 Debug 测试版，它与正式版签名不同，不能直接覆盖。请先在 Debug 版导出课表 JSON，卸载 Debug 版，再安装正式版并导入课表。卸载会清除应用私有数据；JSON 不包含外观和提醒提前量等本地设置，这些设置需要重新配置。

### 手动升级

后续版本请从本仓库的 [GitHub Releases](https://github.com/SukiBanQin/QingKeSchedule/releases) 下载 APK 并手动安装覆盖。覆盖升级要求新 APK 使用相同包名和正式签名密钥，并具有更高的 `versionCode`。青课目前不会自动检查或安装更新。

### 提醒说明

提醒是否准时送达会受到 Android 版本和手机厂商后台策略影响。已验证的小米 10（Android 13）需要开启青课自启动、将电池策略设为“无限制”，并允许提醒通知悬浮、震动和完整锁屏显示。自然长时待机、部分权限拒绝后的完整流程、非精确提醒长期待机及其他厂商设备尚未完成验证。

## iOS 源码与 Mac 本机安装

iOS 工程位于 [`ios/QingKeSchedule.xcodeproj`](ios/QingKeSchedule.xcodeproj)，最低部署版本为 iOS 17。仓库目前提供 iOS 源码，没有公开的 iOS 安装包（IPA）、TestFlight 或 App Store 下载版本。

如需在自己的 iPhone 上试用，需要一台 Mac、Xcode 和 Apple Account：

1. 在 Mac 上用 Xcode 打开 `ios/QingKeSchedule.xcodeproj`。
2. 选择 `QingKeSchedule` App target，在 `Signing & Capabilities` 中选自己的 Team，并开启 `Automatically manage signing`。免费 Apple Account 通常会显示为 `Personal Team`；也可以使用自己付费开发者团队。
3. 用数据线连接 iPhone、解锁并信任这台 Mac，在 Xcode 中选择该设备作为运行目标。若 iPhone 提示开启 Developer Mode，按系统步骤开启并重启确认。
4. 在 Xcode 中运行 App。Xcode 会为所选 Team 配置签名并安装到已连接的设备。

`Personal Team` 适合在自己的设备上测试：开发配置文件有效期为 7 天，到期后需重新用 Xcode 构建并安装。它不是向公众分发应用的方式。通过 TestFlight 或 App Store 发布需要加入 Apple Developer Program，并遵循 Apple 的分发和审核流程。请参考 Apple 的[开发者账户说明](https://developer.apple.com/help/account/basics/about-your-developer-account/)、[向已注册设备分发 App](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices)、[在设备上启用 Developer Mode](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device)和 [Apple Developer Program](https://developer.apple.com/programs/) 文档。

## 反馈

欢迎通过 [GitHub Issues](https://github.com/SukiBanQin/QingKeSchedule/issues) 报告问题或提交建议。请附上青课版本、系统版本和设备型号，并避免发布个人课表或其他隐私信息。
