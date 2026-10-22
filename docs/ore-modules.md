# Ore Lib 模块结构与构建

## 单模块、独立输出

根工程负责 GEG 市场、组织、邮件、聊天和服务端业务；唯一 UI 模块是 `:ore-lib`。

- `ore-lib/src/main`：`io.github.luyingyilong.ore` 控件、布局、窗口、字体、IME、头像缓存、无操作调试桥接和属性注解
- `ore-lib/src/inspector`：`io.github.luyingyilong.ore.inspector` 原生调试器、快照、反射适配、属性覆盖和历史
- `ore-lib/src/test`：正式控件回归
- `ore-lib/src/inspectorTest`：Inspector 回归
- 根 `src/clientIntegrationTest`：显式启用的真实客户端业务联动夹具

依赖方向为 GEG → ore-lib main，inspector → main。GEG 的 clientIntegrationTest 可以编6译引用 inspector；普通 root test 和正式运行不加载 inspector。两个 Java 包保持原名，不增加 Mod 身份或独立元数据。

构建支持在 `ore-lib/gradle/`，包括环境默认值和仅编译期旧注解。模块内部不引用 GEG 业务源码、翻译或根构建脚本。复制一个 ore-lib 目录即可接入同版本 Forge 项目，详见该目录 README。

## 运行与打包

正常 runClient 挂载 GEG main、ore-lib main 和 ore-lib inspector，并由 clientDevelopment 源码集类路径提供仅客户端使用的 JEI；server、gameTestServer、data 只挂载 GEG main 和 ore-lib main。IDEA 客户端运行前构建 inspectorClasses，详见 [开发客户端说明](development-client.md)。

Inspector 的 `META-INF/services/io.github.luyingyilong.ore.OreClientExtension` 仅在 inspector 输出中，调用 OreClient.initialize 后自动注册 F8。主源码不链接 Inspector 实现。

库标准 JAR 仅包含 main。GEG JAR 同样只合并 main 未重映射输出，根 reobfJar 统一映射，包含 geg.mixins.json 和 ore.mixins.json。不使用 Jar-in-Jar，不嵌入 ModularUI、Curios、Inspector、测试或编译注解。

初始化仍由客户端 GegClient 传入 OreClientOptions；头像缓存保留 geg/avatars。NumberInput 的数量／coin 业务预设由 GEG 的 GegInputs 提供。协议、存档、交易和界面状态逻辑未变。

## 资源

Ore 字体、纹理、主题、UI 音效和通用翻译在 assets/ore；业务资源仍在 assets/geg。旧 geg 字体、geg_ore 主题和旧 UI 音效入口已删除，不添加忽略或迁移处理。默认字体、基线、IME 和可选皮肤兼容保持原行为。

ModularUI 3.3.1 开发模式会加载 Curios 测试物品，开发 runtimeOnly 已配置 Curios；正式运行不走此注册分支，Ore 无 Curios API 引用。

## 验证入口

Tabs 管理页签和内容，Segmented 只表示单个选项值。两者均保留，Tabs 不会替换业务界面的 Segmented。

```java
Tabs tabs = new Tabs();
tabs.addItem(new TabItem("summary", Component.translatable("example.summary"), summary));
tabs.addItem(new TabItem("editor", Component.translatable("example.editor"), editor));
tabs.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
tabs.setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
tabs.selectionChanged().connect(window, key -> rememberSelectedPage(key));
tabs.setSelectedKeyNoSignal("editor");
```

`TabItem` 为不可变描述，完整构造参数依次是 key、标题、内容、可选 TextureSource、disabled。`setItem` 按 key 更新描述，同一内容实例不会重新挂载；`moveItem(key, index)` 调整顺序。`getItems()` 返回不可变快照，`getItem` 和 `removeItem` 在 key 不存在时返回 null。选中不存在或禁用的 key 抛出异常；有可用页面时不能主动清空选择。

`Tabs.setMaxTabWidth(px)` 限制每个页签的总宽度，默认 0 不限宽；长标题使用 ELLIPSIS，图标保留在标题前。`Segmented.setIcon(index, TextureSource)`／`getIcon(index)` 配置逐项图标，传入 null 清除。两种控件的图标与文字共享光学偏移。

标题栏靠左，超宽时内部水平滚动；左右／Home／End 只移动焦点，Enter／Space 在释放时切页。页签切换保留草稿、选区、撤销及滚动，不触发 NumberInput 的失焦计算。只有选中页参与显示和布局，隐藏页面仍保留在宿主的输入生命周期中。`selectionChanged` 表示实际变化，`tabActivated` 表示用户激活，包括再次激活当前页。

移除或替换只卸载调用方内容，旧内容可复用；永久废弃时显式调用返回条目的 `getContent().queueFree()`。Tabs 最终销毁时递归清理仍挂载的所有页面。样式通过 `Tabs:*` 主题项和 `setThemeOverride` 配置；F9 展示、F8 可检查页签、页面、内部图片／文字及主题来源。

`TabsTest` 覆盖选择、树契约、布局和 Window 输入；`InspectorTabsTest`、`GalleryTabsTest` 检查开发工具。`TabsClientTest` 仅在显式挂载 clientIntegrationTest 并设置 `ore.tabs.smoke=true` 时执行，覆盖实际 GLFW、原生窗口、缩放 Canvas、编辑历史与 IME 清理，不能在玩家存档中启用。

```powershell
.\gradlew.bat :ore-lib:compileJava :ore-lib:inspectorClasses
.\gradlew.bat oreTest inspectorTest
.\gradlew.bat verifyOreModules verifyOreRelease verifyOrePortability
.\gradlew.bat prepareRunClientCompile genIntellijRuns
```

oreTest 汇总根业务及库的通用回归；inspectorTest 委托库的同名任务。所有 main 回归从隔离 build/tmp 目录执行；可选真实兼容检查使用 imBlockerJar 和 customSkinLoaderJar Gradle 属性。

verifyOreModules 检查 main 与 inspector 的源码和字节码不引用 GEG；verifyOreRelease 检查最终重映射发布包的资源及隔离边界。

verifyOrePortability 将单个模块复制到 build/tmp/ore-lib-portability-host，逻辑名改为 :ui-lib，物理目录改为 libraries/controls。独立宿主 ID 为 ore_sample，不提供 GEG 业务代码、资源、原宿主 Gradle 属性或根脚本，验证正式控件与 Inspector 测试及单 JAR 重映射。夹具源码位于 build-support/ore-portability-host；其 runClient 可执行控件开屏和 F8 原生 Inspector 联动。

正常 IDEA 配置不包含 clientIntegrationTest、smoke 参数或测试专用输入法环境绕行。发布客户端和专用服务端继续只安装 GEG JAR 及声明的外部依赖。

## 合并验证

- 合并后的 main、inspector、inspectorTest 及 GEG 主源码、客户端联动夹具编译通过；普通测试类路径确认无 Inspector 服务
- 改名为 :ui-lib 的独立宿主完成全部 56 项库回归，包含真实 IMBlocker／CSL JAR 检查
- 独立客户端实际显示 Ore 控件、通过 F8 创建并绘制原生 Inspector，输出 ORE_PORTABILITY_CLIENT_PASS 后正常退出
- 独立宿主、库标准 JAR 和最终 GEG 重映射产物均确认不包含 Inspector、开发服务描述或第三方依赖实现
- 标准 IDEA client/server/gameTest/data 配置已重新生成；仅 client 挂载 inspector 输出，旧模块目录已删除


## XML 内容树与热重载

XML 运行能力属于 `ore-lib` 的正式输出，包名为 `io.github.luyingyilong.ore.gui`。资源放在
`src/main/resources/assets/<namespace>/gui/`；资源 ID 必须包含 `gui/` 和 `.xml`，例如
`example:gui/editor.xml`。GEG 的现有业务界面继续由 Java 构建。

```xml
<Panel id="panel">
    <slot name="child">
        <Box id="editor" gap="4">
            <Input id="name" placeholder="@example.ui.name"/>
            <Button id="save" text="@example.ui.save" themeTypeVariation="PrimaryButton">
                <connect signal="pressed" handler="save"/>
            </Button>
        </Box>
    </slot>
</Panel>
```

```java
UiBindings handlers = new UiBindings()
        .action("save", this::save)
        .handler("changed", String.class, this::draftChanged);
UiDocument document = UiLoader.load(ResourceLocation.parse("example:gui/editor.xml"), handlers, window);
window.setContent(document.getRoot());
InputControl name = document.getRoot().findControlFromId("name", InputControl.class);
String draft = name.getWidget().getText();
document.reload(); // 异步请求；在客户端整轮渲染结束或 tick 收尾安全点提交
document.reset();  // 显式重新应用 XML 初始文本、选择和滚动位置
document.close();  // 断开 XML 绑定并 queueFree 内容；不关闭 Window 或 Canvas
```

`getRoot()` 返回稳定的文档挂载节点，XML 根类型改变时宿主不必重新挂载。该节点建立独立 ID 作用域；
从它调用 `findControlFromId`，不要从外层 Window 跨文档查询。每份文档可使用相同 ID。
普通 Java 控件也能使用 `setId/getId/findControlFromId`，默认以所在树根为作用域。
ID 区分大小写，不能包含空白；隐藏节点可查，排队销毁的节点立即不可查。
查找只包含自身和真实后代，类型不匹配抛出异常。内部部件和脱离树的回收实例不混入查询。
Java 工厂根的 ID 对外可见，其内部树由独立作用域保护。

### 支持的结构

内置类型包括 Control、Container、Box、Grid、Panel、Padding、Scroll、Label、TextureRect、Tag、Badge、Divider、
Spin、ProgressBar、TextureProgressBar、Button、Checkbox、Switch、Radio、Slider、Input、NumberInput、TextArea、Navbar、Menu、Tabs、Descriptions、Segmented 和 Dropdown。
Input 系列返回 InputControl，属性和信号显式转接其编辑器。Window、Canvas、宿主及复杂业务树仍由 Java 创建。

- 普通容器直接嵌套控件；Panel／Padding 使用 `<slot name="child">`，Scroll 使用 `content`，Navbar 使用 `extra`
- Tabs 使用 `<item key="edit" text="@example.edit" icon="…" disabled="false">`，每项含一个内容控件
- Menu 使用 `item`，包含 key／text／icon／disabled；`type="GROUP"` 可嵌套 item，`type="EMPTY"` 为非交互提示
- Descriptions 使用 `<item key="name" label="@example.name" text="value"/>`
- Segmented 使用 `<item text="…" icon="…"/>`；其选项文本是构造配置，改变选项集合会更换该节点
- 默认 Dropdown 的值为 String，使用 `<item value="one" text="…"/>`；其他值类型通过 Java 工厂登记
- Collapse 使用 content 槽位；Splitter 使用 first／second 槽位
- Modal、Dialog、Drawer 使用 content 槽位和 actions 槽位，actions 中直接放 Button；Overlay 接收多个普通控件
- Avatar、Slot、Inventory、Table、Tree、Form、FormItem、RadioGroup、Toast 和 DescriptionItem 均已内置
- Window、Canvas 和 Screen／Menu 宿主仍由 Java 创建；泛型业务对象、特殊单元格及自定义校验通过类型化 Java 绑定接入

### 属性、主题和信号

公开 getter 使用独立的 `@UiProperty` 白名单；通过同类型 setter 写入，不反射字段。
可用 `value` 指定 XML 属性名，`setter`／`silentSetter` 指定方法，`initialState=true` 标记初始运行状态。
继承声明也可发现，元数据由 ClassValue 缓存；未声明属性、歧义 setter 和未知类型均报错。
Inspector 的 `@Inspectable` 不授予 XML 配置权限。

```java
@UiProperty(initialState = true, silentSetter = "setSelectedNoSignal")
public String getSelected() { return selected; }
```

普通数字、严格布尔值、枚举、Component、资源 ID、Size、Offset、Transform2D、TextureSource 等有内置转换。
Component 的 `@translation.key` 表示翻译键，普通字符串为字面文本，多行文本可使用 `&#10;`。
Size／Offset 使用逗号分隔；`minimum="80,22"`、`preferred="128,0"`；
SizeFlags 可写 `SIZE_EXPAND|SIZE_SHRINK_CENTER`。
Transform2D 按 `translationX,translationY,rotationDegrees,scale,pivotX,pivotY` 配置。
Label 的 `alignment="CENTER,CENTER"` 使用其水平、垂直枚举。
Grid 的 minCardWidth／maxColumns／gap 和数值控件的范围由原子适配器处理，不依赖属性顺序。
Scroll 的 scrollX／scrollY 是初始状态，在首次布局得到内容范围后应用。

字体、字号、hero、阴影和颜色通过已有主题键设置；XML 移除覆盖会恢复继承：

```xml
<Label id="heading" text="@example.heading" wrap="WORD">
    <theme key="Label:font_color" value="#8cb3ff"/>
    <theme key="Label:font_size" value="10"/>
</Label>
```

theme 的 value 依据注册类型读取：颜色和字体资源 ID 使用原始字符串，布尔／数值／StyleBox／纹理使用相应 JSON 值。
TextureSource 可以写 `$Type:item`，或提供包含 location、imageWidth、imageHeight 及可选源区域的 JSON 对象。
XML 不增加表达式、循环模板、脚本或双向绑定。

只读信号 getter 使用 `@UiSignal`。加载时检查 Signal0 与 Signal<T> 的参数数量及真实载荷类型，
无法解析的泛型必须由工厂登记具体类型。XML 连接最终调用现有 `connect(owner, label, slot)`；
label 含资源、行列和处理器名，Inspector 信号面板可看到来源与断开记录。
重复连接声明报错，重载替换文档绑定作用域，不逐次追加。

### 组合控件、数据与表单

```xml
<Splitter ratio="0.4" firstMinimum="48" secondMinimum="64">
    <slot name="first"><Label text="Left"/></slot>
    <slot name="second">
        <Collapse text="Details" expanded="true">
            <slot name="content"><Input id="draft" placeholder="Hint"/></slot>
        </Collapse>
    </slot>
</Splitter>
```

Splitter 的双侧最小／最大尺寸按原子配置应用；ratio 是初始状态，用户拖动后重载保留当前比例。
Avatar 支持 size、hatLayer、backgroundColor，以及 playerId／playerName 或互斥的 initials。
Slot 使用 item（物品 ID 或 ItemStack SNBT）、count、backgroundVisible、emptyIcon；物品数据均为预览副本。
DescriptionItem 使用必填 key 和 label／content 文本；也可继续使用 Descriptions 的 item 列表。

```xml
<Form id="settings">
    <FormItem id="nameField" name="name" label="@example.name">
        <Input id="nameInput" placeholder="@example.name"/>
        <validate rule="required"/>
        <validate rule="length" min="2" max="32"/>
    </FormItem>
    <FormItem id="choiceField" name="choice" label="@example.choice">
        <RadioGroup id="choice" orientation="HORIZONTAL" gap="4" selected="yes">
            <item value="yes"><Radio id="yes" text="Yes"/></item>
            <item value="no"><Radio id="no" text="No"/></item>
        </RadioGroup>
        <validate rule="required"/>
    </FormItem>
</Form>
```

FormItem 依据实际输入控件推导 FieldKey 的类型：Input／TextArea 为 String，NumberInput 为 BigDecimal，
Checkbox／Switch 为 Boolean，Slider 为 Double 或 RangeValue，Segmented 为 Integer，默认 Dropdown／RadioGroup 为 String。
Java 使用 `new FieldKey<>("name", String.class)` 从 Form.validate() 的成功结果读取值。
FormItem 和字段均应设置稳定 ID；重载 label、placeholder 和校验规则不重建编辑器、不计算未完成表达式。
字段类型、字段名或构造条件变化时替换相应字段；未声明 ID 的控件继续遵循重建规则。
validate 支持 required、String length、BigDecimal range 和自定义 ref，error 可替换成翻译文案。
XML RadioGroup 是 Box 布局和 String 类型 RadioGroup 的组合，运行时类型为 `UiRadioGroup`；用 getGroup() 访问真实分组。

```java
UiBindings bindings = new UiBindings()
        .value("backpack", InventorySource.class, source)
        .value("fuel", SlotBinding.class, menuSession.getBinding(fuelSlotId))
        .value("nameRule", UiValidator.class,
                new UiValidator<>(String.class, Validators.length(2, 32)));
```

```xml
<Inventory source="ref:backpack" slotIndices="0,1,2,3,4,5,6,7,8" columns="9"/>
<Slot binding="ref:fuel" mode="CONTAINER"/>
```

`ref:name` 只解析显式注册且类型匹配的 Java 值，不解析 Java 方法名或任意字段。
共享库存及 MenuSession 仍由 Java 宿主管理；文档关闭不会销毁共享源。
CONTAINER 模式必须使用所属 MenuSession 的源／绑定并最终挂载到该会话的 Window，不在普通原生窗口中启用真实物品操作。
重载候选不会提前登记第二份可交互槽位；成功替换才交接所有权，失败恢复原绑定。
不传 source 时，Inventory 创建独立内存源，slotCount 默认 36，item 条目只写入该演示源：

```xml
<Inventory slotCount="9" columns="9" mode="SELECT" showTitle="false">
    <item index="0" item="minecraft:stone" count="32"/>
</Inventory>
```

slotIndices 为逗号列表；玩家背包可使用 mainSlots 和 hotbarSlots 两个列表建立分区，与 slotIndices 互斥。
内存源随控件销毁，来自 Java 的共享源只解除订阅。源和槽位结构属于构造配置，改变时替换 Inventory；
普通属性重载复用原 Slot 实例和当前选择。

Tree 使用可嵌套的 item（key／text／value／disabled／selectable／icon），也支持平面条目的 parent。
key 唯一且不允许父链成环；默认值和 selectionChanged 载荷为 String，expansionChanged 为 Tree.Expansion。
selected、defaultExpandAll 和 expandedKeys 为初始状态；展开和选中状态在正常重载中保留。

```xml
<Table id="table">
    <column key="name" text="Name" minWidth="48" width="100" grow="1" wrap="WORD"/>
    <row key="one"><cell column="name" text="First"/></row>
</Table>
<Tree id="tree" defaultExpandAll="true">
    <item key="root" text="Root"><item key="child" text="Child"/></item>
</Tree>
```

Table 的 rowActivated 载荷为 UiTableRow，可通过 getKey／getValue 读取稳定键和各列 Component。
列的 cell 默认为 text，可设为 player 并用 nameField／playerIdField 指定姓名和 UUID 字段，复用 PlayerIdentityCell。
`cell="ref:actions"` 可引用 Java 注册的 `UiTableCell`，继续使用真实 ControlCell，不增加手绘按钮旁路。
表格列属于构造配置；行和 Tree 条目可以实时更新。未在 XML 中声明行／条目的空表格或树允许 Java 后续填充。
其他泛型值仍通过命名空间 Java 工厂登记，XML 不反射业务对象。

### 弹层挂载

弹层 XML 可以作为独立文档，以 Overlay 为根挂在 `window.getPopupOverlay()`，使其越过正文 Scroll 裁剪。
不要将希望覆盖整个宿主的弹层放进配方区域或预览 Scroll 的正文；Window／Canvas 的宿主边界仍由 Java 管理。
Window 会识别文档包装下的嵌套焦点作用域，输入、Esc、提示框和 Dropdown 仍走统一分发。

```xml
<Overlay>
    <Modal id="confirm" title="Confirm" width="240" visible="false">
        <slot name="content"><TextArea id="message" rows="3"/></slot>
        <slot name="actions"><Button text="Close"><connect signal="pressed" handler="close"/></Button></slot>
        <connect signal="closeRequested" handler="close"/>
    </Modal>
    <Drawer id="details" title="Details" placement="LEFT" open="false">
        <slot name="content"><Label text="Content"/></slot>
    </Drawer>
</Overlay>
```

Java 加载后将 document.getRoot() 添加到 Window 的 Overlay，通过 ID 控制显示和关闭。
Modal／Dialog 使用 closeRequested；Drawer 的 closed 仍在关闭动画结束后发出。
Drawer／Toast 的 open 是初始状态，候选构建和重载不会重复播放音效；实际用户操作调用 open() 时播放原有音效。
Toast 支持 text／duration／type；内联 Toast 可作为普通布局子节点，Window 级消息仍可通过 OreToasts 管理。
F9 的 XML UI → 更多控件包含这些组合，弹层按钮按需加载 `ore_gallery:gui/overlays.xml`。

### Java 工厂扩展

```java
UiTypeRegistry types = UiTypeRegistry.createDefault();
types.register("example:Machine", node -> buildMachine(node.require("machineKind")),
        node -> node.require("machineKind"),
        Map.of("selectionChanged", MachineMode.class), Set.of("machineKind"));
UiLoader loader = new UiLoader(types, id -> Minecraft.getInstance().getResourceManager().openAsReader(id));
UiDocument document = loader.open(resource, handlers, window);
```

第三方标签须带命名空间。工厂返回新建、未挂载的内容；工厂负责复杂子树的类型和订阅。
构造键表示安全复用的前提，最后一个集合明确登记由工厂消费的构造属性；其余属性仍须经过注解白名单。
需要自定义值转换时调用 `registerConverter`。

### 提交与状态

后台只读取并解析不可变蓝图。客户端线程先离树构建并验证候选，再在安全点提交配置、结构和连接。
只有最新请求可以提交；语法、属性或结构失败保留旧文档，错误可通过 `getStatus/getError` 查询。
解析禁用 DTD、外部实体和外部访问，并限制文档大小、节点数量与深度；错误包含资源、行列、ID、属性／信号名。

同作用域、同当前 ID、同工厂类型及兼容构造键优先复用。无 ID 节点重建；ID 改变后按新身份匹配。
复用原始输入实例，不调用文本回填或 NumberInput commit，所以草稿、选区、撤销和内部滚动保留。
选择与 Scroll 偏移保留有效状态，失效选择使用控件的正常回退，滚动在布局后钳制。
配置移除恢复创建默认值；Box.lineGap 移除后恢复跟随 gap；主题覆盖移除后恢复继承。
Inspector 覆盖继续高于 XML 基础值，安全重排／同窗口移动不更换调试身份。
提交取消旧手势和菜单，并通过保留编辑状态的路径暂时释放、恢复仍有效的焦点。
预编辑尚未结束时显示 WAITING_FOR_IME，保留最新请求而不改变文档。

开发客户端可显式注册 `UiSourceWatcher(Path resourceRoot, loader)`，resourceRoot 为包含 assets 的资源根目录。
也可通过开发 JVM 参数 `-Dore.gui.sourceRoots=<资源根目录>` 登记；多个目录用系统路径分隔符分开。
它读取源目录中的 XML 并在保存后防抖 300ms，只请求对应文档重载。
当前工程的 Gradle runClient 已自动登记 Ore main 和 inspector 两份源码资源根，IDEA 生成配置使用相同参数。
监听仅登记 namespace/gui 层级；每秒对已打开文档进行内容哈希检查，补偿丢失事件、替换保存和目录重建。
内容相同不重复重载；源文件删除会报告错误并保留旧界面，不悄悄读回过期的构建副本。
XML UI 示例独立显示监听路径、版本、等待预编辑和重载错误；监听启动／停止及重载成功也写入开发日志。
正式 JAR 不包含监听器、Gallery 和 Inspector，仍支持资源包覆盖及标准资源重载。

F9 的 XML UI 示例展示具名槽位、主题覆盖、三类输入、Tabs、Segmented 图标、Scroll 和类型安全连接。
F8 显示 ID、作用域、XML 行列、版本、错误，以及 XML／Java／Inspector 属性来源。
相关验证入口为 UiXmlTest、UiReloadTest、ControlIdsTest、UiInputReloadTest、GalleryXmlTest、InspectorXmlTest、UiSourceWatcherTest 和 UiWatchedInputTest。
`XmlUiClientTest` 需显式启用 `ore.xml.smoke=true`，仅在隔离测试世界执行真实 GLFW、内嵌／原生／外部 Canvas 联动。
