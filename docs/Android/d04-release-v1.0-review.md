# D04 GitHub 1.0 APK 发布准备独立复审（2026-09-23）

结论：**发布准备通过 GPT6 SOL 独立技术复审；公开发布尚未完成。** 本结论覆盖 `5aa4805`、`cedc6bc`、`1b024e5` 三个提交的实际改动及本机签名 APK，不代表用户已完成密钥备份，也不代表 GitHub Release 已创建。

## 审查范围与独立证据

- 核对 `aac6daa..1b024e5` 的八个改动文件：Gradle release 签名只从本机环境变量读取四项凭据，缺项会报错；macOS 构建脚本从登录钥匙串读取密码，keystore 位于仓库外，APK 资产由 Git 忽略；发布说明、交接和对应文档测试均已审阅。未发现私钥或密码进入 Git。发布草案原有的相对章节链接已在 `1b024e5` 改为指向 `v1.0` 标签文档的绝对链接。
- 独立对 `Android/release-assets/QingKeSchedule-1.0.apk` 计算 SHA-256，结果为 `dd52315f3bc369dd6189bb820ea709cc31218521f733ce384ab88388758f8e51`，与证据及发布文案一致。`apksigner verify --verbose --print-certs` 返回 Verifies，v2 签名有效，单个签名者，证书 SHA-256 为 `616de2e49fa9bb41ad6629e26b42ae0b97d5be021aecf1c28bfb6a2be51f8d39`。`aapt dump badging` 核对包名 `com.qingke.schedule`、`versionCode=1`、`versionName=1.0`、minSdk 26、targetSdk 37。
- 独立读取本机测试 XML：Debug／Release JVM 各 283 项，API 37 ARM64 connected 202 项，均 0 failures／errors／skipped。执行者记录的首装、保存课程、同证书 `versionCode=2` 覆盖升级后课程保留及 lint 0 errors／24 warnings 与[发布准备证据](evidence/d04-release-v1.0-20260923.md)一致；本次复审没有重复执行设备安装或完整 Gradle 套件。
- 本地 `HEAD`、`origin/Android` 与远程 `Android` 均为 `1b024e5f69673c9c5c2090dc7e78eae9325d306d`；`git diff --check aac6daa..HEAD` 通过。工作区只有用户未跟踪 `.vscode/`，未纳入提交。

## 公开发布前待办

1. 用户亲自在仓库外完成 keystore 至少两处加密备份，并将登录钥匙串内的签名密码保存至其密码管理器，确认可取回。备份完成前不得删除本机密钥或公开发布 APK。
2. 使用有仓库写入权限的 GitHub 登录会话，在已审查的 `Android` 提交上创建 `v1.0` 标签和 Release，上传上述**同一文件**，核对上传资产 SHA-256、说明链接与公开页面。当前 Safari 未登录 GitHub，本机没有 `gh` CLI 或可用的 GitHub HTTPS 凭据；尚未创建标签、Release 或上传 APK。
3. 完成发布后更新交接，准确记录标签目标提交、Release 地址、资产哈希与发布结果；不自动合并 `main`。

P6 已接受的真机提醒限制及 A10 iOS App 文件 UI 往返限制继续保留。签名包的安装和升级验证使用 API 37 模拟器；实体手机未连接，不能据此宣称新增真机覆盖。
