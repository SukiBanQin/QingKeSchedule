# P4／A10 JSON 导入导出证据（2026-09-20）

本目录只记录 P4／A10（Android JSON 导入导出）的验证证据，不改变 A08／A11 的状态。

## 文件

| 文件 | 内容 |
| --- | --- |
| `device-verification-20260920.txt` | 设备环境、测试计数、真实系统文件面板操作、双向往返与未验证清单 |
| `device-properties.txt` | `emulator-5554` 的 API／ABI／分辨率／density |
| `ios-source-integrity.txt` | 参与往返的未修改 iOS 源码 sha256 与主机编译命令 |
| `ios-export-complete-schedule.json` | 未修改 iOS 实现导出的真实 version 1 文件（iOS → Android 方向） |
| `android-export-qingke-schedule-2026-09-20.json` | Android `CreateDocument` 真实导出并由 `adb pull` 取回的文件（Android → iOS 方向） |
| `01-onboarding-transfer-section-api37.png` | 首次设置的「05 数据备份／TRANSFER」区与无学期导出提示 |
| `02-opendocument-picker-api37.png` | 真实 `ACTION_OPEN_DOCUMENT` 面板（`application/json`） |
| `03-opendocument-downloads-api37.png` | 面板中的 Downloads 与 iOS 导出文件 |
| `04-import-preview-api37.png` | 预览：学期／课程数／updatedAt／整体替换说明 |
| `05-import-success-api37.png` | 确认后进入主壳（导入即刻生效） |
| `06-settings-transfer-success-api37.png` | 正式设置「05 区」：导出入口 + 「已导入 6 门课程」 |
| `07-opendocument-cancel-silent-api37.png` | 系统取消导入后静默返回（截图与 06 的画面 md5 相同，即取消没有改变任何界面状态） |
| `08-import-unknown-version-error-api37.png` | 未知版本错误 |
| `09-import-too-large-error-api37.png` | 5 MiB + 1 真实文件被拒 |
| `10-createdocument-picker-api37.png` | 真实 `ACTION_CREATE_DOCUMENT` 与建议文件名 |
| `11-export-success-api37.png` | 导出成功状态 |
| `12-createdocument-cancel-silent-api37.png` | 系统取消导出后静默返回（截图与 11 的画面 md5 相同，即取消没有改变任何界面状态） |

## 结论边界

- 已实现、已测试（JVM 契约／状态、真实 Room + DataStore 设备测试、Compose 设备测试、API 37 真机面板往返）。
- 未执行：iOS App 真实 UI（文件 App／ShareLink）往返，原因与替代证据见
  `device-verification-20260920.txt` 第 5 节。
- 尚未经过 Sol 独立复审与用户验收。
