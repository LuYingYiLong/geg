# Ore Lib for Forge 1.20.1

Ore 是嵌入宿主 Mod 的客户端 UI 库，依赖 Java 17、Forge 1.20.1、ModularUI 3.3.1，不声明独立 Mod 身份。控件、Inspector、资源和构建支持统一放在 `ore-lib/`，复制这一个目录即可接入其他同版本 Forge 工程。

## 源码集与发布边界

| 源码集 | 内容 | 用途 |
| --- | --- | --- |
| `src/main` | 控件、布局、字体、窗口、IME、头像缓存、调试桥接 | 开发和发布 |
| `src/inspector` | 原生 Inspector、F9 组件展示、属性适配、覆盖、历史、开发服务描述与翻译 | 仅开发客户端 |
| `src/test` | 通用控件回归 | 测试 |
| `src/inspectorTest` | Inspector 回归 | 测试 |

四个源码集属于同一个 Gradle 模块。标准 `jar` 仅包含 main，不包含 Inspector。Inspector 只能引用 main；main 不引用 Inspector。运行时通过开发服务描述自动注册 F8 Inspector 与 F9 组件展示。

Box 换行见 [布局说明](docs/layout.md)，类型化字段与校验见 [表单说明](docs/forms.md)，开发验证入口见 [组件展示](docs/gallery.md)。

Java 包按控件职责组织，完整分类、包级封装和宿主迁移规则见 [包结构说明](docs/packages.md)。旧包不保留兼容转发类。

## 原生主题

主题使用独立的 `Theme`、`ThemeDB` 与 `StyleBox`，支持类型化登记、继承、局部覆盖及资源重载。
默认主题 ID 为 `ore:ore`；纹理路径已改为 `textures/gui` 和 `textures/icons`。
配置格式、扩展接线、解析顺序与 Inspector 来源查看见 [主题文档](docs/themes.md)。

## JEI / EMI / REI 避让

### 在配方区域嵌入 Ore

新增 `Canvas`、`DrawContext` 和 `ScreenWindowHost`，支持把控件树放入查看器的自定义配方区域，以及普通外部 Screen。内容保持透明，多个区域共享焦点和输入分发，弹层统一在宿主 Overlay 绘制。具体 API、三种查看器接线及生命周期说明见 [Canvas 接入文档](docs/canvas.md)。

外部 Screen 使用独立的 `ScreenInputBridge → Window.dispatchInput` 链，不借用当前 OreScreen 的 MUI 事件处理器。EMI 的早期原始输入经过受宿主会话约束的桥接；已转发但未处理的事件不会在 Forge Screen 事件中再次发送。查看器原生物品槽继续负责配方快捷键与转移语义。

三个查看器分别通过自己的可选插件入口读取 `RecipeViewerExclusions`，不要求安装其中任何一个，也不将其 API 或 Mod 打入宿主 JAR。编译 API 固定为 JEI 15.20.0.105、EMI 1.1.24、REI 12.1.785（Forge 1.20.1）。

`OreScreen` 中的可见窗口及原版 Screen 中的 `OreButtonWidget` 自动提供 GUI 像素矩形，包含按钮 Badge 改变后的实际宽度。页面正文按布局范围避让；Drawer、Modal、Dialog 和 Dropdown 弹出层使用面板范围，忽略全屏遮罩。空的基础 `Control` 不绘制，因此仅遍历其子节点。原生窗口、暂存界面、隐藏或待销毁节点不占用主窗口区域；动画、偏移、缩放和界面尺寸变化都使用当前布局结果，无需宿主业务手写固定坐标。

最终物品栏如何排列由对应查看器决定。没有开启查看器的界面不会被强制开启查看器，也不修改其用户配置。

布局避让和输入隔离是两条独立的兼容链：`RecipeViewerInputGuard` 在 Forge Screen 输入事件的最高优先级，将 Ore 负责的输入交给原有 MUI → Window 事件链一次，再消费该事件。Drawer、Modal、Dialog 和 Dropdown 打开期间，键盘及整个遮罩区的点击、拖动和滚轮不会进入查看器；没有弹层时，仅保护 Ore 控件区域及已聚焦的文本输入。获得保护时释放查看器搜索框的焦点，不改动 Ore 的逻辑焦点或 IME。

按下归属一直保留到释放，即使期间弹层关闭或返回其他 Screen，也不会把释放传给下层或新界面。鼠标转发前同步 MUI 默认上下文和当前界面的事件坐标，避免点击输入框时使用上一帧的位置。焦点丢失、登出及没有 Screen 时的释放都会清理手势。这里的焦点查询只读，不触发滚动定位。

`RecipeViewerInputGuardTest`、`InputGestureOwnersTest` 覆盖保护范围及手势归属；宿主的 `RecipeViewerInputClientTest` 通过真实 GLFW 回调分别验证 JEI、EMI、REI 及无查看器环境，包括弹层、Dropdown、输入、粘贴、撤销重做、释放触发和拖出取消。

JEI 与 EMI 同装时沿用 ModularUI 的 EMI 优先选择，JEI 通过 EMI 的 JEMI 桥接继续提供兼容数据。两个 Ore 插件只登记区域，不分别转发输入；输入仍经过同一个保护层和 MUI → Window 链。桥接可能重复报告相同矩形，但不会按报告次数扩大避让空间。

两种客户端夹具都支持 `jei+emi` 模式：分别将 `ore.recipe.viewer.smoke` 或 `ore.recipe.input.smoke` 设为 `jei+emi`，在隔离客户端同时提供上述 JEI／EMI 运行依赖。避让测试检查两个原生回调、背包的最终桥接区域及 20 次查询的稳定性；输入测试检查实际 JEMI 运行时、两侧搜索焦点、Ctrl+O 不误切换侧栏，以及文字、按钮和 Dropdown 选项只通知一次。测试不会强制开启原本没有侧栏的 Ore 页面。

## 复制与构建

宿主使用 ForgeGradle 6，映射和 Forge 版本与库一致。配置示例：

```groovy
// settings.gradle
include 'ore-lib'
```

```groovy
// 宿主 build.gradle，加载 ForgeGradle 6 后
evaluationDependsOn(':ore-lib')
repositories {
    maven { url = 'https://www.cursemaven.com'; content { includeGroup 'curse.maven' } }
}
dependencies {
    compileOnly project(':ore-lib')
    implementation fg.deobf('curse.maven:modularui-624243:8339410')
    // ModularUI 开发模式会注册依赖 Curios 的测试物品。
    runtimeOnly fg.deobf('curse.maven:curios-309927:6418456')
}
minecraft.runs.configureEach {
    mods { your_mod_id {
        source sourceSets.main
        source project(':ore-lib').sourceSets.main
    } }
    args '--mixin.config', 'ore.mixins.json'
}
minecraft.runs.client {
    mods { your_mod_id { source project(':ore-lib').sourceSets.inspector } }
}
// ForgeGradle 从宿主解析自定义源码集的 classes 任务，转发到库模块。
tasks.register('inspectorClasses') { dependsOn ':ore-lib:inspectorClasses' }
tasks.matching { it.name == 'prepareRunClientCompile' }.configureEach {
    dependsOn ':ore-lib:inspectorClasses'
}
tasks.named('jar', Jar) {
    from project(':ore-lib').sourceSets.main.output
    manifest { attributes 'MixinConfigs': 'ore.mixins.json' }
    finalizedBy 'reobfJar'
}
```

`your_mod_id` 换成宿主 Mod ID。已有 Mixin 列表须保留并追加 `ore.mixins.json`。最终合并 main 未重映射输出，由宿主统一 reobf；不要合并 Inspector、Forge、ModularUI、Curios 或编译注解。宿主 mods.toml 自行声明 ModularUI 依赖。服务端、数据生成不要挂载 inspector 源码集。

模块逻辑名和物理目录均可修改，不需要额外定位属性。例如：

```groovy
include 'ui-lib'
project(':ui-lib').projectDir = file('libraries/controls')
```

随后将宿主构建脚本中的 `:ore-lib` 替换为 `:ui-lib` 即可。模块内部所有路径按 projectDir 计算。

默认环境位于 `gradle/environment.properties`，无需宿主提供 Mod 名称、group 或版本。宿主可覆盖 `minecraft_version`、`forge_version`、`mapping_channel`、`mapping_version`、`java_version`、`modularui_dependency`、`modularui_dev_runtime_dependency`。当前仅验证 1.20.1，不保证跨 Minecraft 大版本兼容。

## 属性命名

普通图片与按钮图标的接入方式见 [TextureRect 与图片资源](docs/textures.md)。

控件属性统一使用 `getXxx()`／`setXxx(value)`，setter 继续支持原有链式调用，例如
`label.setThemeOverride(Label.FONT_SIZE, 12.0F)`、`control.setVisible(true)`、`control.getSize()`。
旧的同名 getter／setter 重载已移除，所有宿主调用方需要同步更新。
`isAlive()` 等状态判断、Java record 的访问器、第三方 API、`addChild`／`queueFree` 等动作以及
`pressed()`／`textChanged()` 等信号订阅入口保留各自命名，不转换成属性 setter。

Inspector 使用 `@Inspectable` 标记 public `getXxx()`（或布尔 `isXxx()`），默认配对
`setXxx(value)`，属性键仍为 `xxx`，不带 `get` 前缀；也可显式指定 `key`／`setter`。
属性重命名不改变调试覆盖、校验、撤销或恢复行为。

## 信号与生命周期

通知使用类型安全的 `Signal0`／`Signal<T>`，不再提供 `onPress`、`onSelect` 等单订阅 setter。无参数槽使用 `Runnable`，有参数槽使用 `Consumer`，多个参数可以定义为 record。槽按连接顺序在所属线程同步执行；每次 `connect` 都创建独立连接。

```java
Button save = new Button(Component.literal("Save"));
Connection connection = save.pressed().connect(window, this::save);
checkbox.toggled().connect(window, this::updateChecked);
input.textChanged().connect(window, this::updateDraft);
connection.disconnect(); // close() 等价，可重复调用
```

接收方必须是 `SignalOwner`：Control 已实现该接口，非控件对象使用 `SignalScope`。发射方或接收方结束生命周期时，连接自动断开并释放闭包。控件仅公开订阅入口；自定义控件将 `SignalSource` 保留为 private，通过 `signal()` 暴露只读入口。

属性变化会通知，重复设置同一值不通知。受控刷新使用 `setCheckedNoSignal`、`setSelectedNoSignal`、`setTextNoSignal` 等静默方法。它们仍更新状态及布局。`pressed`、`itemActivated` 仅代表激活；程序设置选中状态不会点击按钮。输入框使用显式 `setSubmitOnEnter(true)` 启用提交；TextArea 默认仍是 Enter 换行。

`removeChild`、隐藏、暂存和回收不是销毁；永久废弃的节点必须调用幂等的 `queueFree()`。它立即取消交互并标记拥有的子树，在整轮渲染结束或客户端 tick 的安全收尾点永久清理，不在单个 Window.draw 返回时释放。父控件销毁会清理普通子节点、内部部件和回收池，窗口会话结束由宿主清理，返回链中的父界面继续保留。虚拟控件内部连接绑定到控件本身；捕获当前数据的连接绑定到 `getBindingScope()`，每次重绑或回收时清理。

Input 的 MUI `dispose()` 可能只是临时解绑。树内输入框的信号由 InputControl 的永久销毁清理；独立输入框应通过会话 `SignalScope.own(input::releaseSession)` 托管。不要将 UI 信号用于替代 Window 的 InputEvent 分发或服务端校验。

开发客户端按 F8 打开 Inspector，右侧使用 Segmented 切换“属性／信号”。信号面板只读，列出全部声明、有效连接、生命周期接收方、订阅的 Java 文件与行号，以及反向订阅。来源、接收方和订阅名称相同的多条有效连接会标为“疑似重复”，不会自动断开合法的多订阅。

可为订阅增加可选的诊断名称；它不影响类型检查或执行顺序：

```java
save.pressed().connect(window, "save settings", this::save);
```

自定义控件可以声明 `private final SignalSource<String> renamed = new SignalSource<>(this, "renamed", "String");`。第三个参数只描述诊断中的参数类型；不提供时显示泛型 `T`，不推测擦除后的运行时类型。`Signal0` 明确显示为无参数。面板中的接收方是 `connect` 的生命周期 owner，不是从 lambda 捕获内容猜测的方法接收对象。

选中节点移出控件树后仍可查看它的清理记录，状态区分“已移出”和“已销毁”。历史记录包含手动断开、绑定重置、作用域关闭和控件销毁，并显示剩余连接数；控件已不可访问时只显示历史，不推断清理成功。每线程最多保留 512 条事件，面板显示最近 120 条相关记录。记录只包含值对象；选中控件使用弱引用，Inspector 自身的连接和销毁不会污染目标历史。

纯 Java 的 `SignalDiagnostics` 位于 Ore 主源码，不依赖 Minecraft、GEG 或 Inspector。默认不记录调用位置或历史；开发 Inspector 扩展在初始化时启用追踪，因此 F8 打开前建立的连接也能显示来源。没有追踪时仍能查询当前声明及连接数量，未采集的来源明确标注为不可用。所有诊断读取仍受信号所属线程约束。

## 客户端初始化

宿主客户端 Mod 构造分支，在创建控件前调用：

```java
OreClient.initialize(
        OreClientOptions.defaults(MOD_ID,
                Minecraft.getInstance().gameDirectory.toPath().resolve(MOD_ID).resolve("avatars")),
        modBus, MinecraftForge.EVENT_BUS);
```

初始化幂等，不立即创建原生窗口或激活输入法。不要在专用服务端加载客户端类。完整 options 构造器可配置主题、字体、音效和头像缓存位置。

资源命名空间为 `ore:`，默认主题名为 `ore`，字体为 `ore:ore`、`ore:ore_hero`、`ore:ore_fallback`。资源包覆盖 `assets/ore/`。Inspector 的翻译属于 `ore_inspector:`，只挂载在开发客户端。

`ore.mixins.json` 还会修正 `file` 使用 `ore:` 命名空间的 TTF／OTF 字形重复叠加水平侧边距的问题。该修正保留字宽、基线、全角字符及光标测量；其他命名空间的字体仍使用原有渲染行为。

## Switch

基于 [Ant Design Switch](https://ant.design/components/switch-cn/) 的状态与回调语义，使用固定像素纹理：

```java
Switch toggle = new Switch(true);
toggle.toggled().connect(window, this::updateSetting);
toggle.activated().connect(window, checked -> { });
box.addChild(toggle);
toggle.setCheckedNoSignal(false); // 受控刷新不触发变化信号或音效
toggle.setDisabled(true);
toggle.setLoading(true); // 显示滑块内的 Spin，阻止再次操作
```

`new Switch()` 默认关闭，布尔构造参数设置初始状态。checked、disabled、loading 同时提供 get 前缀 getter 和 set 前缀链式 setter。用户松开鼠标或 Enter／Space 后先更新状态，再发送 toggled、activated；拖出后松开会取消。禁用、加载、失焦、隐藏和顶层弹层会取消未完成操作。

底板为 27×13，滑块为 14×15，底部对齐；滑块从 x=0 移动到 x=14，整体占 28×15。全部纹理按原始像素绘制，额外分配的空间不会放大图像或点击区。默认水平始端收缩、垂直居中收缩；聚焦框为滑块外围 1px，并跟随移动。

默认 240ms 的 ease-out bounce 动画支持途中反向切换，不因重复绑定相同 checked 值而重新开始。`setAnimationDuration(ms)` 可调整时长，0 关闭动画；初始值直接定位，不播放入场动画。

## 输入事件与弹出菜单

所有宿主输入交给所属 `Window.dispatchInput(InputEvent)`。`event` 包提供鼠标、双轴滚轮、键盘、提交文本、预编辑和取消事件；`WindowInputs` 仅将 MUI 回调转换为这些事件。旧的 `dispatchPointer`、`dispatchScroll`、`pointerPressed`、`pointerReleased` 和 `pointerDragged` 接口已删除。

自定义控件重写 `input(InputEvent)`，通过 `target()`、`currentTarget()`、`localX()`／`localY()` 读取分发信息，通过 `accept()` 消费事件。鼠标按下被消费后，Window 将后续移动和释放交给该处理者；组合控件也可以调用 `event.capturePointer()` 显式申请捕获。释放事件的 `over()` 同时考虑控件命中范围和祖先裁剪；拖出后松开不能激活按钮。未处理事件仅沿命中路径向父节点冒泡，不穿透到下层兄弟节点。

`EventGeometry` 统一显示区域和局部坐标转换。Scroll 只转换内容分支，滚动条不叠加自身所属 Scroll 的偏移。控件不应自行加减滚动量。一个事件只能在所属 Window 的客户端线程分发一次，跨窗口、跨线程和重复分发会被拒绝。

`Dropdown` 必须加入控件树，例如作为 `Table.ControlCell`。其菜单由 Window 创建为 Overlay 子树，选项使用真实 Button，统一处理焦点、音效和释放行为；无需手动绘制或转发菜单。菜单会避开宿主边界和所属浮动面板，点击外部关闭并消费该次点击，Esc 返回触发控件；触发节点离开可见区域、换绑、隐藏或被更高层弹层阻断时关闭。

`InputControl` 负责把事件适配到现有 MUI 编辑器，接收时应用字段自身的 MUI 局部矩阵。Ore 的 ModularPanel Mixin 仅在 Canvas 已处理当前按下时保留 Window 设置的字段焦点；其他 MUI 控件仍沿用原有焦点行为。树中 Input／NumberInput／TextArea 的 MUI 直接回调不会重复编辑；宿主不要再单独调用输入框事件方法。提交文字和 IME 预编辑分开处理，预编辑更新不写入文档。未加入 Ore 树的独立 MUI Input 保留原来的接收方式。

## 测试与兼容

```powershell
.\gradlew.bat :ore-lib:oreTest :ore-lib:inspectorTest
.\gradlew.bat :ore-lib:check
```

回归从模块 build/tmp 运行，不依赖宿主目录名。`oreTest` 的类路径只有正式控件；`inspectorTest` 额外使用 inspector 输出。可用 `-PimBlockerJar=<路径>` 和 `-PcustomSkinLoaderJar=<路径>` 验证真实兼容 JAR。

ModularUI 3.3.1 的开发测试注册需要 Curios 运行库，正式运行不走该分支；Ore 不链接 Curios API。Modern UI、IMBlocker 和 CustomSkinLoader 保持可选。Windows 原生窗口和输入法按需启用，其他系统保留内嵌窗口。`gradle/legacy-annotations` 仅用于读取 ModularUI 旧注解，不能进入发布包。


生命周期使用 `LifecycleState`：ALIVE → QUEUED_FOR_FREE → FREEING → FREED。`isDisposed()` 仅表示最后一态；排队后的控件不能重新挂载或取得部件所有权，原始 visible 属性不变。`addChild` 和 `removeChild` 仍同步改变树，不会永久清理对象。刷新时可以先 removeChild，再 queueFree 废弃节点，保留结构节点及需要复用的部件。

自定义控件可重写 `protected void notification(ControlNotification notice)`。框架保证 ENTER_TREE 父先子后、READY 子先父后且只发生一次、EXIT_TREE 子先父后、PRE_FREE 父先子后；另提供合并的 RESIZED、有效 VISIBILITY_CHANGED 及 SUSPENDED／RESUMED。通知携带当次窗口和树状态快照，不必调用 super 才能完成框架清理。READY 等待输入宿主就绪，但不保证尺寸稳定，尺寸相关代码使用 RESIZED。

不参加布局的内部控件使用 `ownPart("name", control)`；虚拟视图使用 `ownPooled(control)` 保留回收期所有权。一个控件不能被不相关的所有者重复拥有。普通资源放入 `getSignalScope().own(resource)`，控件统一由生命周期队列清理。应用无需手工调用 LifecycleScheduler.flush；只有宿主收尾和独立回归测试使用它，输入、信号、布局、绘制及通知期间的 flush 会被拒绝。Drawer 的 closed 仍在关闭动画完成后发送。

Inspector 的共有状态区显示生命周期、树内状态、READY、暂存状态、首次申请位置与原因。已释放选中节点只保留只读记录，禁止编辑、拾取或高亮；历史上限为 512，使用不可变元数据和弱引用，调试会话结束时清空。发布环境不采集申请堆栈。


## 子节点与具名槽位

真实父子结构统一由 `Control` 的 final API 提供：`addChild`、`removeChild`、`moveChild`、`getParent`、`getChild`、`getChildren`、`getChildCount`。`addChild` 返回 void，先配置属性，再独立添加；不提供 `add` 或 `attach` 别名。

```java
Box column = new Box().setGap(4);
Label title = new Label(Component.literal("Title")).setHero(true);
column.addChild(title);
column.addChild(new Button(Component.literal("Continue")));
column.moveChild(title, 1);
```

`getChildren()` 是不可变的直接子节点快照，包含隐藏和尚未解绑的待销毁节点；内部部件和脱离树的回收实例不在其中。`moveChild` 只改变真实顺序，不重新触发 ENTER／EXIT／READY，不清理订阅或取消有效手势。

Panel／Padding 使用 `setChild`；Window／Scroll／Dialog／Modal／Drawer 使用 `setContent`；Navbar 通过 `setExtra` 配置右侧控件。替换在完整校验后一次提交，失败保留原树，相同实例不产生变化；卸载的节点仍然存活，永久废弃时由调用方显式 `queueFree`。

Form 的 `addItem`、Descriptions 的 `addItem`／`removeItem`／`clearItems`、Table 的 `addColumn` 表示逻辑配置，不转发或重写真实树 API。固定骨架拒绝外部结构修改；Descriptions 条目的真实父节点可能是内部行 Box，不能向 Descriptions 的普通 children 添加逻辑条目。子类可通过 `validateChildren` 预检查整个拟定列表，通过 `childrenChanged` 在提交后同步直接或后代索引；预检查不得修改树。框架内部批量更新同样执行线程、生命周期、重复挂载及混合所有权环校验。

Grid／Table／Tree 只把活动缓冲实例加入普通 children，并按数据顺序重排。回收和临时卸载不等于销毁；所有权、输入焦点、IME 和信号生命周期规则保持不变。

变换、主题化焦点和 Radio／RadioGroup 的接口见 [说明](docs/transforms-radio-focus.md)。

[Collapse 与按钮主题变体](docs/collapse-buttons.md)

[Slider 单值与范围输入](docs/slider.md)

背包、物品槽、进度条及真实容器 Screen 接入见 [Inventory 与容器宿主](docs/inventory.md)。

火焰、箭头、三层纹理及环形进度见 [TextureProgressBar](docs/texture-progress.md)。
