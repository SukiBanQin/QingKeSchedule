# P3-09／A11 Android 外观模式独立技术复审

## 复审结论（2026-09-20）

提交 `a3bc7bd4f870b4e208d6be2d7ecd7ea9ae3b57f8` **通过 Sol 独立技术复审**。复审范围为
`a3ecf997535f12328ccc925fc04c26273cfb99dc..a3bc7bd4f870b4e208d6be2d7ecd7ea9ae3b57f8`；未发现阻断或越界修改
iOS、Web、共享 JSON schema／fixtures、Room schema、DataStore 键名或存储值、A08 提醒语义、A10、发布配置或
`main`。本结论关闭 A11 的技术审查门槛，但**不等于用户视觉验收、整个 P3、P6 或完整 App 完成**。

## 已确认正确的实现

- `ScheduleViewModel.setAppearanceMode` 是新增的单一外观写入路径，复用
  `ScheduleAppState.updatePreferences` 的互斥与成功后发布边界。相同模式不写；本次外观写入未完成时忽略后续
  选择；失败恢复最后成功快照，取消不误报普通错误。该路径没有调用提醒 `reconcile`，也不改变课表、教学日历、
  提醒偏好或传输状态。
- 正式设置页顺序为 `04 上课提醒` → `05 数据备份` → `06 外观` → 保存卡片；首次设置页不传入外观分区，但根
  主题仍读取已保存模式。三态顺序与文案为 `AUTO／跟随系统`、`LIGHT／浅色`、`DARK／深色`，选中项有反相背景、
  黄色底线、RadioButton 与“已选择／未选择”语义。
- 根主题只解析一次实际 `dark` 值，外观分区的“当前显示：浅色／深色”直接复用该值；SYSTEM 随系统配置重组，
  LIGHT／DARK 覆盖系统状态，没有第二份可能漂移的实际外观状态。
- 选项区域按实际可用宽度 `< 300dp` 或字体缩放 `>= 1.3` 转为竖排。320dp、130% 与 200% 设备测试证明不重叠、
  可滚动且三个选项均至少 48dp；普通宽度仍保持横排。
- `BrandHeader` 的修正只改变 Logo 取图路径：深色继续显式取 `qingke_logo_dark`，浅色经
  `UI_MODE_NIGHT_NO` 配置解析既有 `qingke_logo`，避免强制浅色时被系统夜间资源限定符替换。资源文件和既有
  页面结构均未修改，反向系统状态的设备断言与截图证明两种模式的墨水对比正确。

## 证据核对与独立验证

- 已核对执行者报告的 Debug／Release JVM 各 283、API 37 ARM64 设备 202、三种构建、lint 0 errors／24 个既有
  warnings、文档验证和 16 张设备截图；命令、测试数量、DataStore 持久化、force-stop、系统明暗切换与
  FATAL／ANR 记录互相一致。
- Sol 独立复跑 `ScheduleViewModelAppearanceTest` 与 `AppearanceLayoutTest`：Debug／Release 各 **10 tests**，
  均为 0 failures／errors／skipped。
- Sol 在 API 37 ARM64 `emulator-5554` 独立复跑 `AppearanceSettingsTest` **13 tests**，为
  0 failures／errors／skipped。测试覆盖分区顺序、首次设置缺席、三态与读屏语义、SYSTEM 动态跟随、强制主题、
  写入中禁用、失败保留、窄屏／大字号／触控目标及 Logo 反向系统状态。
- 截图 01—04、12、16 已人工核对：设置页明暗与选中项一致，强制浅色时 Logo 为深色墨水，首次设置页止于 05；
  其余跨页面截图和亮度取样足以支持本轮“未发现局部继续跟随系统”的结论。

## 保留限制与下一步

真实 DataStore 写入持续时间很短，写入中门禁与失败恢复由确定性的自动化证明；设备验证使用 debug APK，因为仓库
没有发布签名配置。A08 的真实权限弹窗、通知观感、真实重启／Doze 与厂商真机后台投递，以及 A10 已记录的 iOS App
真实 UI 往返限制均不因 A11 关闭。

A11 当前状态为：**已实现、已测试并通过 Sol 独立技术复审**。用户随后于 2026-09-21 在 Android Studio 模拟器
实际检查外观入口、系统明暗切换与页面观感，确认 **A11 用户验收通过**。该确认只关闭 A11；P6、发布和 `main`
合并仍未授权。
