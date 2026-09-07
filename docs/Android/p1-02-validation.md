# P1-02 工具链核查与最小启动验证记录

验证日期：2026-09-07。角色：Terra 执行；未启动子 Agent。P1-02-R1 复核 ARM64 模拟器安装条件；范围限于工具链核查、现有 debug 骨架构建和启动条件探测；不进入 P2，不修改应用业务源码、依赖版本、iOS、Web 或共享协议。

## 现场与提交

- 分支：`codex/ios-ui-redesign-demo`
- 开始 HEAD：`0f20b2b`
- 应用基准：`3924d26`
- P1-01 修正复审：`5780b81`、`0f20b2b`
- 本任务只修改 `Android/README.md`、`Android/scripts/p1-02-apk-check.sh` 与本记录；两处既有 iOS 未提交文件保留且未暂存。

## SDK 与工具链核查

执行：

```bash
ANDROID_HOME=/tmp/qingke-android-sdk-1788767128 \
  "$ANDROID_HOME/cmdline-tools/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --list
```

实测已安装 `platform-tools` 37.0.1、`platforms;android-35` revision 2、`build-tools;35.0.0`、`emulator` 37.1.11。稳定渠道可用平台至少包括 `android-36`、`android-36.1`、`android-37.0`、`android-37.1`；`37.2-beta*` 为 beta，不纳入稳定候选。

结论：API 35 不能证明满足 D02 的“最新稳定版本”要求。当前 `AGP 8.8.2 + Gradle 8.10.2 + JDK 17 + API 35` 已验证可构建。升级建议仅回传 Astra 审查：以稳定渠道最高平台为目标，选择其官方兼容的稳定 AGP/Gradle/Build Tools，并复核 Kotlin/Compose 插件迁移；本任务不升级版本。官方链接见 `Android/README.md`。

## 构建与测试证据

```bash
cd Android
ANDROID_HOME=/tmp/qingke-android-sdk-1788767128 \
  ANDROID_SDK_ROOT=/tmp/qingke-android-sdk-1788767128 \
  ./gradlew assembleDebug test --no-daemon --console=plain
```

结果：`BUILD SUCCESSFUL`；使用 `--rerun-tasks` 时 68 个任务执行，Debug/Release JVM 测试通过，`assembleDebug` 成功。APK：`Android/app/build/outputs/apk/debug/app-debug.apk`。未设置 SDK 环境的同命令曾失败并报告 `SDK location not found`，不作为代码失败。

新增 APK 预检脚本：

```bash
ANDROID_HOME=/tmp/qingke-android-sdk-1788767128 bash Android/scripts/p1-02-apk-check.sh
```

结果：成功核对包名 `com.qingke.schedule`、`minSdk 26`、`targetSdk 35`；该预检不替代安装启动验证。

## 模拟器安装启动探测

已创建 AVD：`qingke-api35`，API 35，Google APIs，`x86_64`。启动失败，宿主机为 Apple Silicon/aarch64，QEMU2 报：

`Avd's CPU Architecture 'x86_64' is not supported by the QEMU2 emulator on aarch64 host.`

随后执行：

```bash
/tmp/qingke-android-sdk-1788767128/platform-tools/adb devices -l
/tmp/qingke-android-sdk-1788767128/platform-tools/adb install -r Android/app/build/outputs/apk/debug/app-debug.apk
/tmp/qingke-android-sdk-1788767128/platform-tools/adb shell am start -n com.qingke.schedule/.MainActivity
```

结果：`adb devices -l` 无设备，install/start 返回 `adb: no devices/emulators found`。没有 Activity 状态、截图、冷启动/重启或 logcat 崩溃证据。ARM64 API 35 image 已尝试下载但未完成，未记为可用设备。

P1-02-R1 再次探测 ARM64：

```bash
SDK=/tmp/qingke-android-sdk-1788767128
yes | "$SDK/cmdline-tools/bin/sdkmanager" --sdk_root="$SDK" \
  'system-images;android-35;google_apis;arm64-v8a'
echo no | "$SDK/cmdline-tools/bin/avdmanager" create avd -n qingke-api35-arm \
  -k 'system-images;android-35;google_apis;arm64-v8a' -d pixel_2 --force
"$SDK/cmdline-tools/bin/avdmanager" list avd
"$SDK/platform-tools/adb" devices -l
```

结果：ARM64 system image 安装命令在 30 秒窗口内无有效下载输出，本地目录仍约 4 KiB、无可用镜像元数据；`avdmanager` 返回 `Package path is not valid`，仅列出既有 `qingke-api35`（API 35、x86_64）。`adb devices -l` 仍无设备。因此 R1 未取得安装、冷启动、再次启动、Activity、截图或 logcat 证据，P1 启动门槛继续阻塞。

## 全部验收命令

| 命令 | 结果 |
| --- | --- |
| `ANDROID_HOME=... ANDROID_SDK_ROOT=... ./gradlew assembleDebug test --rerun-tasks --no-daemon --console=plain` | 成功；68 个任务执行，Debug/Release JVM 测试通过 |
| `ANDROID_HOME=... bash Android/scripts/p1-02-apk-check.sh` | 成功；包名/minSdk/targetSdk 符合已确认 D02 基线 |
| `python3 docs/tests/android-documentation.test.py` | 成功；9 项通过 |
| `bash docs/tests/documentation.test.sh` | 成功 |
| `bash docs/tests/repository-layout.test.sh` | 成功 |
| `git diff --check` | 成功；无空白错误 |
| 暂存补丁 `git diff --cached --check` | 待本提交后执行 |
| 模拟器安装/启动 | 未完成；环境限制，不声称成功 |

## 未决与限制

- D02 最新稳定目标仍需 Astra 根据稳定渠道清单和官方兼容矩阵审查；本任务不升级工具链。
- 未知字段 Android 严格拒绝、Swift 宽容接受的策略仍未决定。
- 未取得 ARM64 模拟器或真机，因此 P1 的安装启动门槛仍未完成；不进入 P2。
- 本任务不覆盖 A01—A11、完整 UI、存储、通知、发布签名或用户验收。
