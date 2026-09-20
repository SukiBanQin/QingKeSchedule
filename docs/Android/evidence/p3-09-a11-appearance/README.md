# P3-09／A11 外观模式证据（2026-09-20）

本目录只记录 P3-09／A11（正式设置页 06 外观、三态即时主题、SYSTEM 动态跟随与由此暴露的品牌 Logo 主题修正）
的验证证据，不改变 A08／A10 的状态。

## 文件

| 文件 | 内容 |
| --- | --- |
| `device-verification-20260920.txt` | 设备环境、命令与实际结果、主题一致性取样、暴露缺陷与修正、自动化覆盖、限制 |
| `verification-commands-20260920.txt` | 最终 JVM／构建／lint／设备／文档测试的命令与实际数量 |
| `brand-logo-theme-fix-20260920.txt` | A11 暴露的品牌 Logo 系统夜版取图问题：原因、修正与断言级前后证据 |
| `01-system-light-settings-06-api37.png` | 系统浅色 + 跟随系统：设置页 06 区与“当前显示：浅色” |
| `02-system-dark-settings-06-api37.png` | 系统深色 + 跟随系统：即时变深色与“当前显示：深色” |
| `03-forced-light-while-system-dark-api37.png` | 强制浅色（系统仍深色）：不跟随系统 |
| `04-forced-dark-while-system-light-api37.png` | 强制深色（系统仍浅色）：不跟随系统 |
| `05-after-force-stop-relaunch-dark-api37.png` | force-stop 重开后仍为深色（持久化） |
| `06-persisted-dark-settings-06-api37.png` | 重开后设置页 06 区：DARK 选中、`当前显示：深色` |
| `07-today-forced-dark-api37.png`、`12-today-forced-light-system-dark-api37.png` | 同数据今日页深色／浅色（浅色图为系统深色下强制浅色，用于暴露局部跟随系统） |
| `08-week-forced-dark-api37.png`、`13-week-forced-light-system-dark-api37.png` | 同数据周课表深色／浅色 |
| `09-course-editor-forced-dark-api37.png`、`14-course-editor-forced-light-system-dark-api37.png` | 同数据课程编辑器深色／浅色 |
| `10-course-editor-invalid-dialog-forced-dark-api37.png`、`15-course-editor-invalid-dialog-forced-light-system-dark-api37.png` | 同数据弹窗深色／浅色 |
| `11-settings-06-forced-light-system-dark-api37.png` | 设置页 06 区在系统深色下强制浅色 |
| `16-onboarding-no-06-system-dark-api37.png` | 首次设置页分区止于 05，无 06 入口（系统深色下仍按主题渲染） |

## 结论边界

- 已实现、已自测（JVM、Compose 设备、API 37 真实设备三态与持久化证据）。
- 修正了 A11 验证暴露的一处真实主题问题：品牌 Logo 曾按系统夜间资源限定符取图，强制浅色 + 系统深色时不可读。
- 尚未经过 Sol 独立复审与用户验收；iOS App 真实 UI 与 A08 真机通知遗留不因本轮变成已验证。
