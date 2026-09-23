# P3-04 视觉 R5 终端表单证据

2026-09-15，`qingke-api37-r3-arm`（API 37、`arm64-v8a`）最终 debug APK 的生产入口截图。范围仅为三模式课程颜色面板、课程安排下拉菜单和重复分段控件；不代表 Sol 集中实际 diff 复审或用户视觉验收。

首次 R5 复拍时，Sol 发现“COLOR MATRIX”标题下没有色块。原因是网格单元在可滚动、无界高度的容器中以 `fillMaxSize()` 测量；返修将每个单元改为明确的 `42dp` 外层／`36dp` 色块高度。下面仅列返修后已目视核验的截图；未列入的同目录旧文件是首版历史证据，不能用于证明本次修复。

- `color-grid-visualr5-light-100.png`：保存并重新打开 `VisualR5` 后，浅色 100% EDIT 的默认“网格”面板；三个入口、完整 6×6 色块矩阵和不透明 modal 可见。
- `grid-dark-100-r5fix.png`：深色 100% EDIT 网格面板；36 个色块完整可见。
- `grid-dark-130-r5fix.png`：深色 `font_scale=1.3` EDIT 网格面板；入口、色块和操作按钮均未裁切。
- `spectrum-light-100-r5fix.png`：浅色 100% HSV 饱和度／明度平面、色相条与选择指示器。
- `sliders-light-100-r5fix.png`：浅色 100% RGB 三轨道、通道值和 `HEX / ADVANCED` 输入。
- `start-period-menu-light-100-r5fix5.png`：开始节次终端菜单；不透明 surface、青色分隔线、反相当前项与黄色勾可见。
- `repeat-selector-light-100-r5fix2.png`：EDIT 安排中的每周／单周／双周直角分段控件；当前项带深色反相底和黄色状态点。

自动化覆盖三种颜色模式共享同一 editor 颜色、网格代表色像素、HSV／RGB 映射与回调、三类选择器回调和 repeat 互斥点击；完整 API 37 回归为 67 tests、0 failures/errors/skipped。生产入口还核对了 `VisualR5` 的保存、EDIT 重新打开、浅色／深色 100%、深色 130% 和目标 logcat；未出现目标 FATAL/ANR。设备恢复为 light／`font_scale=1.0`，停在 EDIT 的“网格”面板。
