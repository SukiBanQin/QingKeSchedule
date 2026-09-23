# 青课 Android

青课是一款帮助学生整理课程安排的 Android 课表软件。当前公开版本为 1.0。

## 功能

- 查看今日课表和周课表。
- 编辑课程与教学日历。
- 设置上课提醒。
- 通过 JSON 文件导入或导出课表。

## 下载与安装

需要 Android 8.0（API 26）或更高版本。

[前往 GitHub Releases 下载青课 Android 1.0](https://github.com/SukiBanQin/QingKeSchedule/releases/tag/v1.0)，下载 `QingKeSchedule-1.0.apk`。安装前请核对 Release 页面提供的 APK SHA-256。打开 APK 后，若系统提示，请在 Android 设置中允许当前浏览器或文件管理器安装此来源的应用，再按系统提示完成安装。

如果设备上安装的是开发期间的 Debug 测试版，它与正式版签名不同，不能直接覆盖。请先在 Debug 版导出课表 JSON，卸载 Debug 版，再安装正式版并导入课表。卸载会清除应用私有数据；JSON 不包含外观和提醒提前量等本地设置，这些设置需要重新配置。

## 手动升级

后续版本请从本仓库的 [GitHub Releases](https://github.com/SukiBanQin/QingKeSchedule/releases) 下载 APK 并手动安装覆盖。覆盖升级要求新 APK 使用相同包名和正式签名密钥，并具有更高的 `versionCode`。青课目前不会自动检查或安装更新。

## 提醒说明

提醒是否准时送达会受到 Android 版本和手机厂商后台策略影响。已验证的小米 10（Android 13）需要开启青课自启动、将电池策略设为“无限制”，并允许提醒通知悬浮、震动和完整锁屏显示。自然长时待机、部分权限拒绝后的完整流程、非精确提醒长期待机及其他厂商设备尚未完成验证。

## 反馈

欢迎通过 [GitHub Issues](https://github.com/SukiBanQin/QingKeSchedule/issues) 报告问题或提交建议。请附上青课版本、Android 版本和设备型号，并避免发布个人课表或其他隐私信息。
