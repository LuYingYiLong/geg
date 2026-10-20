# 组件展示

开发客户端按 **F9** 打开 Ore 组件展示，可在按键设置中重绑定。F8 打开 Inspector，能够检查展示界面的真实节点。展示代码和翻译位于 inspector 开发 source set，标准发布产物不包含这些内容；独立 Forge 宿主挂载该 source set 后同样可用，无需 GEG。

界面用一个整体 Panel 作为背景，内部采用固定 Navbar、左侧分组 Menu、右侧 Padding 与滚动示例；右侧不再另加背景板。导航与预览保留最小尺寸，窄视口通过水平滚动访问。右上角可选择跟随宽度或 127／128／129px。

目录覆盖布局、文字与图片、交互、输入与表单、数据和弹层。示例使用本地数据，展示真实 Button、输入、信号与生命周期。鼠标悬浮、按住、拖出释放、键盘 focus、中文输入及撤销可直接交互。示例在切换时才创建，旧节点在渲染安全点 queueFree；开发快捷键沿用延后打开机制。

Window 示例使用当前内嵌宿主；Canvas 配方嵌入和原生窗口继续由现有专用客户端夹具覆盖。FocusOverlay 是框架内部节点，不作为可独立操作的目录项。

验证入口：

```powershell
.\gradlew.bat :ore-lib:oreTest inspectorTest
.\gradlew.bat regressionMarketDetailLayoutTest regressionOrganizationDetailsLayoutTest regressionMailLayoutTest
.\gradlew.bat verifyOreRelease verifyOrePortability
```

GalleryLayoutTest 检查目录覆盖、窄屏布局及替换清理。宿主的可选 GalleryClientTest 由 `ore.gallery.smoke=true` 启用，测试使用独立运行目录和真实 GLFW 回调，不操作玩家业务数据。

