# D04 GitHub 1.0 APK 发布准备验证（2026-09-23）

状态：**1.0 APK 签名构建、安装启动和同密钥升级数据保留已验证；等待 GPT6 SOL 集中审查。未创建 GitHub Release 或正式 tag。**

## 基准与范围

- 分支：`Android`；开始 HEAD 与 `origin/Android` 一致，为 `aac6daa0c8b2d4d84c1932fab4258248c8535c27`。
- 开始时只有用户未跟踪 `.vscode/`；保留且未暂存。
- 修改 `Android/app/build.gradle.kts`、`Android/.gitignore`、`Android/scripts/`、本任务 `docs/Android/` 发布资料和 Android 文档测试；未改 iOS、Web、共享协议、`main` 或实体手机数据。
- 目标按用户确认：GitHub Releases 提供 Android `1.0` APK，用户手动下载；以后沿用包名与同一签名密钥、递增 `versionCode` 并手动覆盖升级；不接自动更新。

## APK 与签名

- 正式 APK：`Android/release-assets/QingKeSchedule-1.0.apk`；该目录仅忽略 APK 文件，不会被 Gradle 清理，资产不进入 Git。
- APK SHA-256：`dd52315f3bc369dd6189bb820ea709cc31218521f733ce384ab88388758f8e51`。
- 清单：包名 `com.qingke.schedule`、`versionCode=1`、`versionName=1.0`、minSdk `26`、targetSdk `37`。
- `apksigner verify --verbose` 成功，单个签名者，APK Signature Scheme v2 有效；JAR/v1、v3、v3.1、v4 未使用。
- 签名证书 SHA-256：`616de2e49fa9bb41ad6629e26b42ae0b97d5be021aecf1c28bfb6a2be51f8d39`。
- 私钥在仓库外 `~/Library/Application Support/QingKeSchedule/AndroidRelease/release-key.p12`，文件权限 `0600`，目录权限 `0700`；PKCS#12 / RSA 3072。密码存于本机登录钥匙串服务 `QingKeSchedule-Android-Release-v1`、账户 `qingke-release`。仅通过本机钥匙串读回验证可解锁 keystore；**本文件不含密码或私钥**。
- 用户尚需自行将 keystore 复制到至少两份加密备份，并通过“钥匙串访问”取回密码后保存到其密码管理器。密钥未备份前不要清理本机密钥或公开发布。

## 安装和数据升级验证

- 环境：API 37 ARM64 `emulator-5554`，实体手机未连接。开始前确认该模拟器没有安装 `com.qingke.schedule`。
- 将上述最终 1.0 APK 全新安装，系统接受安装；可启动首次设置及主界面，并成功保存一门名为 `D04FinalAPK` 的测试课程。
- 用相同证书构建本地测试包 `versionCode=2`、`versionName=1.0.1-test`，`apksigner verify` 成功。通过 `adb install -r` 原位升级返回 `Success`；系统报告版本已为 2，重新打开后 `D04FinalAPK` 仍出现在课表中。
- 此测试课程仅在 API 37 模拟器创建。验证完成后卸载 App，恢复开始时“无青课 App”的模拟器状态；未连接或修改实体手机。
- 这验证了同密钥递增版本覆盖更新和 Room 课表保留。真实厂商提醒体验沿用已验收的 P6 范围与限制，不把模拟器结果外推为真机提醒验收。

## 自动化验证

完整重跑命令：

```text
./gradlew --rerun-tasks testDebugUnitTest testReleaseUnitTest connectedDebugAndroidTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug --no-daemon --console=plain
```

- Debug JVM：283 tests，0 failures／errors／skipped。
- Release JVM：283 tests，0 failures／errors／skipped。
- API 37 ARM64 connected：202 tests，0 failures／errors／skipped。
- `assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest` 成功；`lintDebug` 0 errors、24 warnings。编译保留 `statusBarColor` 和 `navigationBarColor` 两项弃用警告；native library 未剥离提示属构建打包警告。
- 文档测试 `python3 docs/tests/android-documentation.test.py`：73 tests，全部通过；`docs/tests/documentation.test.sh`、`docs/tests/repository-layout.test.sh`、`git diff --check` 通过。
- 最终签名构建命令 `Android/scripts/build-signed-release.sh`：`BUILD SUCCESSFUL`；再次校验 v2 签名和上述 SHA-256。

## 保留限制与发布闸门

- 自然长时待机提醒无可判定结果；手动拒绝权限后的完整流程、非精确提醒长期待机及其他厂商仍未验证，沿用 P6 已接受的边界。
- 本轮一台额外临时 AVD 在 QEMU 启动时报告线程无响应并以 139 退出。最终安装／升级测试改在已运行且此前未安装青课的 API 37 模拟器完成；该启动故障不属于 APK 测试结果。
- 本机没有 `gh` 命令；依用户要求且待集中审查，本轮未创建 GitHub Release、未创建 `v1.0` tag，也未上传 APK。
- Android 官方开发者验证政策可能变化，当前说明链接至官方 FAQ；持续对外分发前应按当时规定复核。
