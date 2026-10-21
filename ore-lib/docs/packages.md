# Java 包分类与迁移

Ore 以 `io.github.luyingyilong.ore` 为根包。根目录只保留客户端初始化、宿主配置和扩展接口；公开控件按职责放入 `control`。本轮是源码包迁移，不提供旧包的转发类，宿主需要同步更新 import。

| 包 | 职责 |
| --- | --- |
| `control.base` | Control、Container、几何值、SizeFlags、控件树及生命周期 |
| `control.layout` | Box、Grid、Panel、Padding、Scroll、Splitter、Divider、Collapse |
| `control.display` | Label、TextureRect、Badge、Tag、Descriptions 及描述项 |
| `control.display.avatar` / `skin` | Avatar 与整组皮肤缓存、加载、兼容实现 |
| `control.progress` | ProgressBar、TextureProgressBar、Spin 与包级数值／填充几何实现 |
| `control.input` | 按钮、选择、输入、Slider、RadioGroup 与编辑器实现 |
| `control.data` | Table、Tree、PlayerIdentityCell |
| `control.inventory` / `source` | Inventory、Slot、数据源和 SlotBinding |
| `control.form` / `validation` | 表单、类型化字段与适配、值快照、校验器 |
| `control.navigation` | Menu、MenuItem、Navbar |
| `control.overlay` / `toast` | Overlay、Drawer、Dialog、Modal、Toast 与通知队列 |
| `window` / `container` / `screen` | Window、焦点管理、宿主、Minecraft 容器及 Screen 接入 |
| `event.gesture` | 共享按下／释放手势 |
| `control.input.internal` | MUI 输入控件的绘制接入辅助 |
| `render.internal` | 共享绘制层级实现 |
| `text` | TextLayout、字体与文本算法，包括 TextLines |

事件、信号、绘制、主题、文本、兼容、调试桥和 Mixin 保持共享基础设施身份。Inspector 与 Gallery 的开发包和 source set 不变，不进入发布产物。

## 包级封装

Java 子包不共享包级可见性。ControlTree、生命周期调度、进度模型、文本编辑器以及直接与 Input 双向协作的 IME 实现，继续与其所有者同包，不为目录整齐而公开内部字段、构造器或状态。输入法原计划拆入 `ime` 子包，但其原生连接、预编辑清理和焦点检测依赖 Input 的包级／受保护方法，因此按封装优先约定整体保留在 `control.input`。后续若单独抽象输入法端口，应作为另一轮行为重构进行。

Window 与 FocusManager 同包。Window 通过 Control 的三个 `protected final` 宿主钩子进入树、暂存和排队 READY；树结构、永久清理和通知分发仍为包级实现。

跨职责组合使用明确的绘制／样式入口：Divider 的主题键构造器、Tag.Type.getTextColor()，以及 DrawContext 和 Control.draw() 的统一绘制管线。Toast 的全局宿主同样使用这条管线，不公开 Label 的原始绘制旁路，也不公开编辑器、几何算法或树实现类。

## 测试与接入

白盒测试随所观察的实现迁移。跨控件夹具放在测试源码中，包括同包的 ButtonTestAccess、OverlayTestAccess、LabelTestAccess、WindowTestAccess，以及 `support` 中的事件、注册表、字体和主题资源夹具；这些类型不进入生产输出。

Gradle 仍按测试简单类名登记 `regression…`／`regressionInspector…`，已有常用命令不变。`regressionPackageContractTest` 检查分类、源码路径、根目录限制、包级可见性和旧根类缺失。

主题类型／变体／键、资源命名空间、翻译键、头像缓存目录和 Mixin 配置名均不随包名改变。独立 Forge 宿主、GEG 和开发工具使用同一套新包；无需安装新的 Mod 或迁移世界存档。
