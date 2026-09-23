# 青课 Android 1.0 GitHub 下载说明

## 发布资产状态

- 应用：青课 Android
- 包名：`com.qingke.schedule`
- 版本：`1.0`（`versionName` 1.0，`versionCode` 1）
- 最低 Android 版本：Android 8.0（API 26）
- APK 文件：`QingKeSchedule-1.0.apk`
- APK SHA-256：`dd52315f3bc369dd6189bb820ea709cc31218521f733ce384ab88388758f8e51`
- 签名证书 SHA-256：`616de2e49fa9bb41ad6629e26b42ae0b97d5be021aecf1c28bfb6a2be51f8d39`
- 公开发布状态：尚未创建 GitHub Release 或 `v1.0` 标签；此文档与 APK 先供集中审查

最终 APK 位于 `Android/release-assets/QingKeSchedule-1.0.apk`，该资产路径已加入 Git 忽略规则，不会随 Git 提交上传，也不会被后续 Gradle 构建清理。生成方式见 [`Android/scripts/build-signed-release.sh`](../../Android/scripts/build-signed-release.sh)。

## 安装与升级

1. 只从青课官方 GitHub 仓库的 Releases 页面下载 `QingKeSchedule-1.0.apk`，并核对页面所列 SHA-256。
2. 在 Android 设置中允许当前浏览器或文件管理器安装未知来源应用，再打开下载的 APK 完成安装。Android 可能显示侧载安全提示。
3. 若设备上装的是开发期间的 Debug 版本，因签名不同，不能直接覆盖安装。先在 Debug 版内导出课表 JSON，卸载 Debug 版，再安装正式版并导入 JSON；卸载会清除 App 私有数据。导出文件只包含课表，不包含外观、提醒提前量等本地设置，安装后需重新设置。
4. 后续版本会继续使用相同包名和同一正式签名密钥，并递增 `versionCode`。维护者在 [`app/build.gradle.kts`](../../Android/app/build.gradle.kts) 更新 `versionCode` 与 `versionName` 后，签名脚本会按版本名产出对应 APK；用户下载新版 APK 后直接安装覆盖即可，应用支持手动更新；目前不含自动检查或安装更新的功能。

如果系统报告“无法安装更新”或签名冲突，请先确认来源、包名与版本。不要为排除此错误直接清除现有数据；如确需跨签名切换，应先导出课表并确认已保存文件。

## 已知限制

- 提醒投递受设备和系统后台策略影响。已验证的小米 10（Android 13）需要为青课打开自启动、将电池策略设为“无限制”，并允许提醒类别的悬浮、震动和完整锁屏显示。
- 自然长时待机提醒没有得到可判定结果；权限被手动拒绝后的完整操作路径、非精确提醒长期待机及其他厂商设备尚未验证。
- Android 端课表导入／导出已按 P6 范围完成验证；iOS App 真实文件选择与分享 UI 往返仍未验证。

## 签名密钥保管

签名私钥保存在本机仓库外的 `~/Library/Application Support/QingKeSchedule/AndroidRelease/release-key.p12`，目录权限为仅当前用户可访问；密码保存在 macOS 登录钥匙串，服务名 `QingKeSchedule-Android-Release-v1`、账户 `qingke-release`。仓库、GitHub 和本说明中不保存签名密码或 keystore 内容。

发布前请在“钥匙串访问”中查找上述服务和账户，验证可取回密码，并将 keystore 文件与密码分别备份到你控制的加密备份／密码管理器中。保留至少两份可恢复副本。丢失或替换此私钥会使之后的 APK 无法作为同一应用直接升级。

在此 Mac 上重新构建可运行 `Android/scripts/build-signed-release.sh`；脚本通过 Security.framework 从登录钥匙串读取签名密码，不在终端打印密码。换电脑时先恢复同一 keystore 和密码条目，再构建。若签名密钥需要迁移、轮换或密码无法恢复，应先停止发布并处理密钥恢复，不要临时生成替代 key。

## GitHub Release 发布文字草案

**标题：** 青课 Android 1.0

**正文：**

青课 Android 首个公开版本，提供课表导入、课程编辑、今日课表与课程提醒等功能。

- 支持 Android 8.0（API 26）及以上。
- 下载 `QingKeSchedule-1.0.apk` 安装；后续版本从本仓库 Releases 下载后覆盖安装。
- 请核对下方 APK 的 SHA-256。提醒投递可能需要按手机厂商设置自启动、电池后台策略与通知显示权限。
- 已知限制和跨签名升级说明见[本说明的安装与升级章节](#安装与升级)。

**资产：** `QingKeSchedule-1.0.apk`

**SHA-256：** `dd52315f3bc369dd6189bb820ea709cc31218521f733ce384ab88388758f8e51`

截至 2026-09-23，Google 官方说明：2026-09-30 首阶段的开发者验证要求针对特定地区及参与应用商店，GitHub 直接下载的侧载 APK 不属于该首阶段强制范围；官方建议在 2027 年全球扩展前准备开发者验证。持续分发前请复核最新要求并评估注册。详见 [Android 开发者验证 FAQ](https://developer.android.com/developer-verification/guides/faq)。
