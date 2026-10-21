# Inventory、Slot 与容器宿主

Ore 的 `Inventory` 是一组物品槽的视图，不拥有玩家或方块中的物品。`Slot` 默认 **18×18 GUI px**，其中物品模型为 16×16，主题内距每边 1px。数量、耐久、冷却和原版提示框由同一槽位绘制；更大的显式最小尺寸仍然有效。

## 三种模式

| 模式 | 行为 |
| --- | --- |
| `READ_ONLY` | 展示和提示框，不获取焦点或移动物品 |
| `SELECT` | 释放时选中，拖出取消，支持键盘激活 |
| `CONTAINER` | 绑定当前 OreMenuScreen 的真实 Menu，使用原版物品操作语义 |

```java
MemoryInventorySource source = window.getSignalScope().own(new MemoryInventorySource(27));
source.setItem(0, new ItemStack(Items.IRON_INGOT, 32));

Inventory preview = new Inventory().setSource(source).setColumns(9);
Inventory picker = new Inventory().setSource(source)
        .setSlotIndices(List.of(0, 4, 8))
        .setMode(Inventory.Mode.SELECT);
picker.selectionChanged().connect(window, index -> updateSelection(index));
picker.slotActivated().connect(window, index -> inspectItem(source.getItem(index)));
picker.setSelectedNoSignal(4);
```

数据源复制传入和返回的 ItemStack；不能通过修改快照操作真实库存。源有自己的 SignalScope，由创建方关闭，视图仅关闭订阅。多个视图可以共享只读或选择数据源。真实 Menu 中，一个槽位只能有一个可交互视图。

`slotIndices` 是源中的稳定槽位地址，不是显示位置。Menu 源中的地址就是 `AbstractContainerMenu.slots` 的 ID，不能替换成玩家库存内部索引。重排复用原 Slot；替换源或移除条目时废弃节点 queueFree，安全帧后释放连接。

默认 9 列，空槽仍占位置。Inventory 不带滚动；放入 `Scroll` 后，窄视口保留槽位原尺寸，支持双轴滚动。`Inventory:gap` 默认 0，`Inventory:section_gap` 默认 4，均通过主题覆盖设置。

`showTitle` 默认 `true`，左上角显示原版 `container.inventory` 翻译（中文为“物品栏”）。标题使用 Label 的主题字体与颜色，并按实际行高为下方槽位留出空间；`Inventory:title_gap` 默认 2px。通过 `setShowTitle(false)` 隐藏时同时收起标题和间距，适合已有标题或固定坐标的整张容器背景；`getShowTitle()` 查询当前设置。

`getTitleControl()` 返回 Inventory 持有的同一个 Label，可通过主题覆盖设置标题颜色，或使用 Offset 微调标题位置，不影响槽位和物品数量文字。`Inventory:title_height` 默认为 0，跟随字体度量；正数指定固定标题行高，不缩放字体。Gallery 的原版箱子与高炉示例将其覆盖为 10px，并分别设置 `title_gap` 为 0／1px，在实际字体资源加载后仍保持原版背包槽位坐标。

## 注册容器 Screen

服务端继续使用 Forge 的标准 MenuType、MenuProvider 和打开流程。UI 不注册物品存储，不处理配方，不决定可放入物品、取出条件、距离校验或 Shift 移动范围。

```java
// 客户端初始化中注册；MY_CHEST_MENU 由宿主注册。
MenuScreens.register(MY_CHEST_MENU.get(), MyChestScreen::new);

public final class MyChestScreen extends OreMenuScreen<MyChestMenu> {
    public MyChestScreen(MyChestMenu menu,
                         net.minecraft.world.entity.player.Inventory inventory,
                         Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected Control createContent(MenuSession session) {
        Inventory chest = new Inventory().setShowTitle(false).setSource(session.getSource())
                .setSlotIndices(IntStream.range(0, 27).boxed().toList())
                .setMode(Inventory.Mode.CONTAINER);
        Inventory player = new Inventory().setSource(session.getSource())
                .setSlotIndices(IntStream.range(27, 63).boxed().toList())
                .setMode(Inventory.Mode.CONTAINER)
                .setPlayerSlots(IntStream.range(27, 54).boxed().toList(),
                        IntStream.range(54, 63).boxed().toList());
        Box body = new Box().setGap(4);
        body.addChild(new Label(title));
        body.addChild(chest);
        body.addChild(new Divider());
        body.addChild(player);
        return new Panel().setChild(body);
    }
}
```

这个示例假定宿主 Menu 按箱子 27 格、玩家主背包 27 格、快捷栏 9 格的顺序添加槽位。`setPlayerSlots()` 接收实际 Menu ID 的两份列表，不猜测宿主的顺序；装备和副手通过额外 Slot 或 Inventory 显式绑定。

一个 Screen 内的两个 Inventory 共享一个 MenuSession、鼠标携带物品和原版拖动分配状态。`createContent()` 每次 Screen 会话只构建一次；改变 GUI 尺寸只重新布局。宿主为整个内容提供居中及外层 Scroll，放不下时可以访问溢出内容。

## 熔炉组合

```java
Slot input = new Slot().setBinding(session.getBinding(0)).setMode(Inventory.Mode.CONTAINER);
Slot fuel = new Slot().setBinding(session.getBinding(1)).setMode(Inventory.Mode.CONTAINER);
Slot output = new Slot().setBinding(session.getBinding(2)).setMode(Inventory.Mode.CONTAINER);
ProgressBar cooking = new ProgressBar();
ProgressBar burning = new ProgressBar().setDirection(ProgressBar.Direction.BOTTOM_TO_TOP);
burning.setPreferred(10, 24);

Box inputs = new Box().setGap(4);
inputs.addChild(input);
inputs.addChild(burning);
inputs.addChild(fuel);
Box machine = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(8);
machine.addChild(inputs);
machine.addChild(cooking);
machine.addChild(output);
```

下面继续添加绑定玩家槽位的 Inventory 即可。`containerTick()` 中先调用 `super.containerTick()`，再读取 Menu 已同步的加工／燃烧比例，用 `setValueNoSignal()` 回填 ProgressBar。原版 FurnaceMenu 可使用 `getBurnProgress()/24.0` 和 `getLitProgress()/13.0`；自定义 Menu 建议直接暴露已同步计数的比例，以保留精度。

燃料限制、输出槽不可放入和取出经验等规则由服务端原有 Slot/FurnaceResultSlot 决定。UI 绑定不会改变这些规则。服务器公共 Menu 类不引用 Ore 的客户端宿主或控件。

## 输入与关闭

宿主复用 ScreenWindowHost、Canvas 和 MUI 输入适配层。真实输入只有 `Window.dispatchInput` 一条链；普通 Button 仍在释放时激活，真实物品槽使用 Minecraft 的拿取、拆分、Shift 移动、拖动分配、双击、数字键、副手及丢弃规则。

原版固定槽位坐标不参与 Ore 命中。仅针对 OreMenuScreen 的 Mixin 将查找转到实际布局、Scroll 和裁剪几何，复用原版容器手势及标准物品请求。快捷移动的目标范围仍由宿主 `quickMoveStack()` 决定。

Input/IME、顶层菜单、Modal 和 Drawer 先处理事件，不允许下层槽位或查看器抢占。Esc 先完成预编辑取消、输入失焦及弹层关闭，最后走标准 Menu 关闭；不要使用普通 Window 返回链恢复已经结束的 Menu。

临时打开查看器时保留控件，返回后检查 Menu 是否仍有效。关闭、断线或 Menu 失效清理输入、订阅和树；不会销毁方块库存，也不会自行删除鼠标携带物品。携带物品的最终处理交给服务端 Menu。

JEI/EMI/REI 获得实际可见槽位的物品与避让区域；配方转移仍由宿主为自己的 Menu 注册。原生窗口及普通 Canvas 可使用预览和选择，真实容器交互首版使用 OreMenuScreen。

## ProgressBar

`min=0`、`max=100`、`value=0`，默认从左向右、无百分比。支持 `LEFT_TO_RIGHT`、`RIGHT_TO_LEFT`、`TOP_TO_BOTTOM`、`BOTTOM_TO_TOP`。值钳制到范围，非法数字或 `min >= max` 不改变已有状态。

`valueChanged()` 只在实际数值变化时通知，普通与静默赋值均更新显示。控件不消费输入、不获取焦点，也没有过渡动画。背景和填充是 StyleBox，填充通过裁剪完整样式显示，纹理不会随百分比被挤压。

主题项包括 `ProgressBar:background`、`fill`、`font_color`、`font_size` 和四边 `padding_*`。水平偏好 128×8，垂直方向交换尺寸。`setShowPercentage(true)` 使用内部 Label 的统一文本排版；内距、颜色和字号通过主题设置。

## 开发验证

F9 的 Inventory、ProgressBar、Chest/Furnace 示例只使用独立内存数据。真实测试 Menu 不从 Gallery 打开，也不操作玩家存档。

- `:ore-lib:regressionInventoryTest`、`regressionMenuSessionTest`、`regressionProgressBarTest`：数据、布局、输入、绑定和清理
- `:ore-lib:inspectorTest`：Gallery 目录、窄宽度及现有 Inspector 回归
- `src/containerTest`：开发专用 GameTest 源码集，使用 `forge.enabledGameTestNamespaces=ore_test`，需宿主显式加入 GameTest run 的 mod sources
- `MenuClientTest`：根工程的 opt-in 客户端夹具，使用隔离世界和真实 GLFW/Menu 数据包

`containerTest`、Gallery、Inspector 及客户端测试均不进入发布 JAR。测试资源包含独立 `ore_test:empty` 模板，不依赖宿主业务资源。
