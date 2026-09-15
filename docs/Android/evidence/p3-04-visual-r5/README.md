# P3-04 视觉 R5 终端表单证据

2026-09-15，`qingke-api37-r3-arm`（API 37、`arm64-v8a`）最终 debug APK 的生产入口截图。范围仅为三模式课程颜色面板、课程安排下拉菜单和重复分段控件；不代表 Sol 集中实际 diff 复审或用户视觉验收。

- `color-grid-light-100.png`：CREATE 页面浅色 100% 的默认“网格”颜色面板，三个等宽入口、完整颜色矩阵、预览和终端 modal 可见。
- `color-grid-visualr5-light-100.png`：保存并重新打开 `VisualR5` 后的 EDIT 页面，默认网格面板仍可用。
- `color-grid-dark-100.png`：深色 100% 的 EDIT 网格面板。
- `color-grid-dark-130.png`：深色 `font_scale=1.3` 的 EDIT 网格面板，模式入口和网格保持可见。
- `weekday-menu-light-100.png`：星期选择器的非 Material 紫色终端菜单，包含不透明 surface、青色信息线和反相当前项。
- `repeat-selector-light-100.png`：EDIT 安排中的每周／单周／双周直角终端分段控件，当前项带深色反相底与黄色状态点。

自动化覆盖三种颜色模式共享同一 editor 颜色、网格／HSV／RGB 映射边界、三类选择器回调、repeat 互斥点击、浅深/130%既有布局及完整 API 37 回归。生产入口还核对了 `VisualR5` 的保存、EDIT 重新打开、浅色/深色 100%、深色 130% 和目标 logcat；未出现目标 FATAL/ANR。设备最后恢复为 light／`font_scale=1.0`，停在 EDIT 的“网格”面板。
