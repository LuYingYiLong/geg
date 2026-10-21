# Ore 原生主题

主题由 Ore 自己解析和绘制。ModularUI 仅保留宿主、文本编辑与焦点适配所需的内部默认主题；不要向 MUI 注册 Ore 主题，也不要创建 `assets/ore/themes.json`。

## 注册与加载

默认资源为 `assets/ore/themes/ore.json`，ID 为 `ore:ore`，代码安全默认值为 `ore:default`。纹理位于 `assets/ore/textures/gui` 和 `assets/ore/textures/icons`。资源包覆盖相同路径即可；旧的 MUI JSON 格式和 `textures/modularui` 路径已删除。

每个控件使用静态 `registerTheme(ThemeDB)` 声明类型和项，初始化统一调用。第三方类型使用带命名空间的名称，并重写 `getThemeType()` 返回登记的名称。禁止在构造函数或逐帧绘制中注册。

```java
public static final ThemeKey<Integer> TITLE_FONT_COLOR = ThemeKey.of(
        "example:Status", "title_font_color", Integer.class,
        ThemeKey.Kind.COLOR, 0xFFFFFFFF, false);

public static void registerTheme(ThemeDB db) {
    db.registerType("example:Status", "Control");
    db.register(TITLE_FONT_COLOR);
}
```

宿主通过 `OreClient.initialize(options, modBus, forgeBus, extension...)` 显式传入 `OreClientExtension`，在 `registerThemes(ThemeDB)` 中登记扩展类型、类型变体和主题。可选的开发集成仍可使用 ServiceLoader；宿主主题采用显式传入，避免 Forge 合并输出目录时同名服务文件被遮蔽。扩展按实现类去重，初始化幂等，注册不会创建原生窗口。Forge 并行构造 Mod 时不创建主题信号，主题初始化在客户端资源加载的 apply 阶段完成。

`ThemeKey.Kind` 支持 STYLE、COLOR、FONT、FLOAT、INTEGER、BOOLEAN、TEXTURE。图片项值为不可变 TextureRegion，由 TextureRect 显示，见 [图片接口](textures.md)。相同声明可重复登记，冲突报错。字号及配置可在声明中提供值校验器。类型变体通过 `registerVariation(name, base)` 登记，继承基础类型项的定义，不必复制所有声明；`getKey(type, name, valueType)` 可取得类型安全的变体项。

### 图片主题归属

`Icons` 是独立的共享资源类型，通过 `IconTheme.registerTheme(db)` 注册，不继承 `Control`，也不需要对应控件类。关闭、箭头、勾选和筛选图标放在此处；普通 Control 的 Inspector 面板不会列出整份图标目录。

控件专用图片由控件自己的 `registerTheme` 声明：

| 控件 | 图片项 |
| --- | --- |
| Checkbox | `unchecked_normal`、`unchecked_hovered`、`unchecked_disabled`、`checked_normal`、`checked_hovered`、`checked_pressed`、`checked_disabled` |
| Switch | `base`、`base_disabled`、`button_normal`、`button_hovered`、`button_pressed`、`button_disabled` |
| Spin | `texture` |
| Dropdown | `arrow`、`checked` |
| Tree | `expanded_icon`、`collapsed_icon` |
| Navbar | `back_icon` |
| Dialog／Modal／Drawer | `close_icon` |

共享符号只配置一次。Dropdown 的 `arrow` 缺失时回退到 `Icons:arrow_down`；Tree、Navbar 和弹层也使用显式登记的共享符号回退。每一层先检查控件项（包括类型变体），再检查共享项，然后才检查资源父主题及更远的控件主题。局部覆盖仍优先于资源值。Inspector 显示最终的共享项及 `fallback for ...` 来源原因。

```json
{
  "parent": "ore:ore",
  "Icons:close": { "location": "example:icons/close" },
  "Drawer:close_icon": { "location": "example:icons/drawer_close" },
  "Checkbox:checked_normal": { "location": "example:icons/checked" }
}
```

整张图片仅需要 `location`，其尺寸在资源重载时读取并缓存；图集局部区域才填写 `x/y/width/height`。控件内的 TextureRect 保留自己的显示属性，同时使用所属控件的主题变体及局部覆盖。不得重新把状态图片集中登记到 `Control` 或 `TextureRect`。

业务图标由宿主注册独立主题类型并放在宿主资源包。GEG 的 `geg:ui` 继承 `ore:ore`，在 `assets/geg/themes/ui.json` 中提供 `geg:MailIcons` 和 `geg:OrganizationIcons`；这些声明及图片不进入独立 Ore 模块。

`registerTheme(ResourceLocation)` 登记资源入口，加载器只读取这些主题及其父主题，不扫描其他 Mod 的 MUI 主题文件。

## 继承与局部覆盖

```java
Theme theme = ThemeDB.getDefault().getTheme(ResourceLocation.parse("example:dark"));
panel.setTheme(theme);
label.setThemeTypeVariation("LabelSecondary");
label.setThemeOverride(Label.FONT_COLOR, 0xFF8CB3FF);
Integer localColor = label.getThemeOverride(Label.FONT_COLOR);
int effectiveColor = label.getThemeItem(Label.FONT_COLOR);
label.removeThemeOverride(Label.FONT_COLOR);
```

`getTheme()` 只返回控件自身设置的主题，未设置时为 null。解析顺序为显式属性、局部覆盖、自身主题、最近到最远祖先主题、宿主默认主题、登记的安全默认值。每个主题资源优先尝试类型变体、控件类型和基础类型，再查资源父主题；缺失项继续回退。

`getThemeOverride(ThemeKey<T>)` 类型安全地返回当前控件对该项的局部覆盖，未设置时返回 null（0、false 等已设置值照常返回）。它只按完整主题项 ID 查询，不解析类型变体、继承、显式属性或回退项，也不会建立主题订阅或使布局失效。要取得绘制实际使用的最终值，使用 `getThemeItem`；查询来源使用 `getThemeResolution`。所有查询和修改均在控件所属线程执行。

内部部件继承其所有者。弹出菜单通过 `setThemeParent(trigger)` 继承触发控件，独立于 Overlay 的绘制父级；关系检查循环，销毁时解除。普通应用代码不需要为 Dropdown 手动设置该关系。

共享 Theme 不随控件销毁。`theme.changed()` 通知有效值变化，控件收到合并的 `THEME_CHANGED`。颜色只使绘制缓存失效；字体、字号及 StyleBox 内距同时使测量与布局失效。控件订阅在 `queueFree()` 的最终清理阶段断开。回收节点保留所有权；相同所有权的重复登记不会重置滚动文字或布局。

`Label.setFont/setHero/setShadow` 表示显式属性，使用 `clearFont/clearHero/clearShadow` 恢复继承。字号和文字颜色只使用主题项，不提供独立的字号或文字颜色 setter/getter。Theme 和局部覆盖不会被资源重载或 Inspector 属性预览覆盖写回。

### 文字颜色

普通文字统一使用 `Label:font_color`（`Label.FONT_COLOR`／`OreTheme.FONT_COLOR`），颜色为 ARGB 整数。`LabelDark`、`LabelSecondary` 等文字变体也覆盖各自的 `font_color`。透明度为 0 的颜色仍是有效覆盖；Component 内显式指定的子文本颜色继续保留。

组合控件通过文字角色区分不同区域，内部 Label 直接继承主题，不再逐个复制颜色：

| 区域 | 主题项 | Java 常量 |
| --- | --- | --- |
| Dialog／Modal／Drawer 标题 | `LabelHero:font_color` | `OreTheme.TITLE_FONT_COLOR` |
| Navbar 标题 | `NavbarTitle:font_color` | `OreTheme.NAVBAR_TITLE_FONT_COLOR` |
| Descriptions 标题／字段名／内容 | `DescriptionsTitle/DescriptionsLabel/DescriptionsContent:font_color` | `Descriptions.TITLE_FONT_COLOR/LABEL_FONT_COLOR/CONTENT_FONT_COLOR` |
| Table 表头及紧凑布局字段名 | `TableHeader:font_color` | `Table.HEADER_FONT_COLOR` |
| Menu 整体及分组空提示 | `MenuEmpty:font_color` | `Menu.EMPTY_FONT_COLOR` |
| Input 正文／占位文字 | `Input:font_color/font_placeholder_color` | `OreTheme.INPUT_FONT_COLOR/INPUT_PLACEHOLDER_FONT_COLOR` |

```java
drawer.setThemeOverride(OreTheme.TITLE_FONT_COLOR, 0xFFFFFFFF);
table.setThemeOverride(Table.HEADER_FONT_COLOR, 0xFFD0D1D4);
menu.setThemeOverride(Menu.EMPTY_FONT_COLOR, 0xFFFFFFFF);
input.getThemeControl().setThemeOverride(OreTheme.INPUT_FONT_COLOR, 0xFFFFFFFF);
input.getThemeControl().setThemeOverride(OreTheme.INPUT_PLACEHOLDER_FONT_COLOR, 0x80D0D1D4);
```

Input、NumberInput 和 TextArea 的 Ore 绘制以这些主题项为准，包括正文、光标及复用的 placeholder；不要使用 MUI 继承的颜色配置方法。选区背景继续由 `Input:selection_color` 控制。

Button 的文字随交互状态解析 `font_color`、`font_hover_color`、`font_pressed_color`、`font_disabled_color`（`Button.FONT_COLOR/FONT_HOVER_COLOR/FONT_PRESSED_COLOR/FONT_DISABLED_COLOR`）；类型变体可覆盖同名项。Segmented 选中文字使用 `Segmented:font_selected_color`，Collapse 次要说明使用 `Collapse:secondary_font_color`。移除局部覆盖即可恢复最新继承值，不再提供重复的文字颜色方法或旧主题键别名。

Inspector 的颜色编辑使用 `theme:Label:font_color` 等主题路径，接受 `#RRGGBB`、`#AARRGGBB` 或 ARGB 整数。无效输入保留上次有效预览；恢复读取最新基础值，预览不写入应用的局部覆盖。

### 字号

普通文字使用 `Label:font_size`（`Label.FONT_SIZE`／`OreTheme.FONT_SIZE`），默认 8 GUI 像素；`scale` 继续作为额外的相对缩放。标题可用 `LabelHero:font_size`（`OreTheme.TITLE_FONT_SIZE`）单独配置，未提供时继承普通文字字号。Dialog、Modal、Drawer 和 Navbar 的标题都使用该标题角色。Descriptions 的标题使用 `DescriptionsTitle:font_size`（`Descriptions.TITLE_FONT_SIZE`），Slot 数量文字使用 `SlotCount:font_size`（`Slot.COUNT_FONT_SIZE`，默认资源固定为 8）。

```java
Label label = new Label(title);
label.setThemeOverride(Label.FONT_SIZE, 12.0F);
float effective = label.getThemeItem(Label.FONT_SIZE);
Float local = label.getThemeOverride(Label.FONT_SIZE);
label.removeThemeOverride(Label.FONT_SIZE);

button.setThemeOverride(Label.FONT_SIZE, 12.0F);
modal.setThemeOverride(OreTheme.TITLE_FONT_SIZE, 12.0F);
descriptions.setThemeOverride(Label.FONT_SIZE, 10.0F);
descriptions.setThemeOverride(Descriptions.TITLE_FONT_SIZE, 14.0F);
```

字号必须为正的有限 float。修改和移除覆盖、祖先主题变化及资源重载均重新测量、换行并重置滚动文字计时；内部 Label 不再靠逐个复制字号同步，新加入的描述项也直接继承。对组合控件使用普通文字项会影响其继承该项的内部部件；只想调整弹层标题时使用标题项。

Inspector 保留实时字号编辑，但属性路径改为 `theme:Label:font_size`。预览只在解析读取时覆盖有效值，不写入 Theme 或控件局部覆盖；无效输入保留上次有效值，撤销、重做和恢复继续生效。

### 弹层内距

Dialog 和 Modal 的各区内距统一由 `header_padding_top/right/bottom/left`、`body_padding_top/right/bottom/left`、`footer_padding_top/right/bottom/left` 控制，均为非负整数 GUI 像素，参与测量和布局。Modal 的外框另用 `padding_top/right/bottom/left`。

| 类型 | 标题（上、右、下、左） | 正文 | 操作区 | 外框 |
| --- | --- | --- | --- | --- |
| Dialog | 8、8、4、8 | 四边 8 | 4、8、8、8 | 无额外内距 |
| Modal | 0、1、0、1 | 四边 8 | 四边 8 | 四边 1 |

```java
modal.setThemeOverride(Modal.BODY_PADDING_LEFT, 12);
modal.setThemeOverride(Modal.FOOTER_PADDING_TOP, 4);
Integer local = modal.getThemeOverride(Modal.BODY_PADDING_LEFT);
int effective = modal.getThemeItem(Modal.BODY_PADDING_LEFT);
modal.removeThemeOverride(Modal.BODY_PADDING_LEFT);

dialog.setThemeOverride(Dialog.HEADER_PADDING_TOP, 6);
```

两种弹层不提供重复的内距 setter/getter。局部覆盖优先于继承主题，移除后恢复最新主题值；资源重载保留局部覆盖。StyleBox 的 contentMargin 与这些内距独立计算，各消费一次。

Dialog 在视口高度小于 110px 时使用 `compact_header_padding_top` 和 `compact_footer_padding_bottom`，默认均为 2px；每项显式回退到对应的普通标题／操作区内距。因此对普通 `HEADER_PADDING_TOP`、`FOOTER_PADDING_BOTTOM` 的局部覆盖在紧凑布局也有效；专门设置 compact 项则只影响紧凑布局。

### 其他控件的内距

控件自身的外观内距也通过主题配置，不再提供第二套 padding/inset setter/getter。下表数值依次为上、右、下、左；每组均展开为四个整数项，例如 `Tag:padding_left`、`Table:cell_padding_top`。

| 类型 | 项目前缀 | 默认值 | 作用 |
| --- | --- | --- | --- |
| Collapse | `padding_` | 4、8、4、8 | 正文 Panel |
| Drawer | `padding_` | 四边 4 | 整体内容 |
| Navbar | `padding_` | 0、0、3、0 | 顶部内容与底部留白 |
| Tag／Toast | `padding_` | 2、4、2、4 | 文本区域 |
| Badge | `padding_` | 1、2、1、2 | 数字区域；圆点模式不使用 |
| Button | `icon_padding_` | 四边 4 | 纯图标按钮 |
| Button | `badge_padding_` | 2、8、2、8 | Badge 在按钮中的区域 |
| Menu | `padding_` | 0、8、0、8 | 分组标题与非交互空提示项 |
| Dropdown | `padding_` | 四边 1 | Overlay 菜单外框内距 |
| FormItem | `padding_` | 0、0、6、0 | 字段整行，含标签与错误 |
| Table | `cell_padding_` | 2、4、2、4 | 内置文本及头像姓名单元格 |
| Table | `compact_padding_` | 四边 4 | 紧凑布局整行 |
| Table | `header_padding_` | 0、4、0、4 | 表头文字 |
| Table | `empty_padding_` | 8、2、0、2 | 空状态 |
| Slot | `padding_` | 3、5、7、5 | 16px 物品预览 |
| Slot | `count_padding_` | 12、1、2、1 | 数量文字 |

```java
tag.setThemeOverride(Tag.PADDING_LEFT, 8);
table.setThemeOverride(Table.CELL_PADDING_TOP, 5);
dropdown.setThemeOverride(Dropdown.PADDING_RIGHT, 6);
button.setThemeOverride(Button.ICON_PADDING_LEFT, 6);
```

普通 Button 文字、Segmented、Panel 和输入框继续使用对应 StyleBox 的 `contentMargin`，可将新的 StyleBox 传给 `setThemeOverride`；不叠加另一套通用 padding。Input、NumberInput、TextArea 的正文、光标、选区、placeholder 和 IME 位置使用同一份解析后的内距。Table 的自定义 ControlCell 保留控件自身布局；表格的单元格内距默认作用于内置文本和头像姓名内容。

`Scroll:track_inset` 默认 2，仅改变外置滚动条轨道两端的视觉缩进，不改变 grabber 行程、拖拽或内容尺寸。Box/Grid 的 gap、Tree 的层级缩进、Descriptions 的行列间距属于布局参数；Padding 容器的 `setInsets/getInsets` 也保留，用于显式布局。它们不与控件外观内距重复。

## JSON

```json
{
  "parent": "ore:default",
  "Button:pressed": {
    "style": "texture",
    "location": "ore:gui/button_secondary_pressed",
    "textureBorders": {
      "borderLeft": 2,
      "borderTop": 2,
      "borderRight": 2,
      "borderBottom": 2
    },
    "contentMargin": { "left": 8, "top": 4, "right": 8, "bottom": 4 },
    "drawInset": { "top": 2 },
    "contentOffset": { "x": 0, "y": 2 },
    "expandMargin": { "left": 0, "top": 0, "right": 0, "bottom": 0 },
    "horizontalMode": "stretch",
    "verticalMode": "stretch",
    "drawCenter": true,
    "opacity": 1.0
  },
  "Label:font_color": "#FFFFFFFF",
  "Label:font": "ore:ore",
  "Label:font_size": 8,
  "Label:shadow": false
}
```

- `style` 为 `texture`、`flat` 或 `empty`。显式 empty 是有效值，会停止背景回退。
- texture 可指定 `imageWidth/imageHeight/x/y/width/height`。尺寸缺省时读取真实 PNG，区域缺省为整图；显式图片尺寸必须与资源一致。location 支持 `ore:gui/panel` 或完整的 `ore:textures/gui/panel.png`。
- `textureBorders` 使用源图片像素，只指定源区域的九宫格切分。目标过小时按比例缩小目标边框，不产生负宽高或反向 UV。拉伸和平铺可按轴独立设置。
- `contentMargin` 使用非负整数 GUI 像素，未填写时四边都是 0，参与内容布局和最小尺寸。Panel、Button、Input 的默认内距在资源中明确配置，只消费一次；外层 Padding 独立生效。
- `expandMargin` 默认全 0，只扩大背景绘制，不扩大点击矩形或 Scroll 内容范围，仍受正常裁剪和弹层顺序约束。
- `drawInset` 默认全 0，使用非负整数 GUI 像素，只向内缩小背景；先内缩再应用 `expandMargin`。Button、Segmented 的焦点框跟随内缩后的背景，不跟随装饰性的扩张边距。
- `contentOffset` 的 x/y 为整数 GUI 像素（可为负），默认均为 0，只平移文字、图标及按钮 Badge，不影响内容内距、最小尺寸、布局或点击区域。Button、Segmented 的按下状态和 Segmented 的选中状态默认配置 top=2、y=2；TEXT/MENU 不设置这两项，默认为 0。自定义状态样式不再隐含 2px 下沉。
- flat 使用 `color`、`borders.borderLeft/Top/Right/Bottom` 与 `borderColor`；没有圆角或通用阴影。empty 只提供内距和扩张信息。
- 颜色支持 `#RRGGBB`、`#AARRGGBB`，透明纹理始终正确混合，不再需要 `nonOpaque`。图标使用纹理区域对象，不需要 style。
- 不支持 `borderX/borderY`、`textureMargin` 和旧的嵌套 MUI 样式。未知项、无效值和样式记录资源及属性位置，并回退。无效 JSON 或资源父主题循环不替换上一次有效主题图。

Slot 的默认背景直接引用依赖中已有的原版风格纹理，保留之前的像素外观；它通过 Ore StyleBox 绘制，不查询 MUI Theme。

默认文字角色为 Label、LabelDark、LabelSecondary、LabelError、LabelAccent、LabelDisabled。LabelHero 启用标题字体，NavbarTitle 在其基础上使用深色文字。背景、状态颜色、字体与图标的完整可覆盖项见默认 `ore.json`。

### Segmented 选中标记

选中标记使用独立的装饰 StyleBox，支持 flat、texture 和 empty；设为 empty 即可隐藏，不会回退到白色线条。

```json
"Segmented:selected_indicator": { "style": "flat", "color": "#FFFFFF" },
"Segmented:selected_indicator_height": 1,
"Segmented:selected_indicator_width_ratio": 0.33333334,
"Segmented:selected_indicator_max_width": 16,
"Segmented:selected_indicator_bottom_gap": 1
```

宽度按选项宽度乘以比例向下取整，非零比例至少显示 1px，再限制到最大宽度及选项宽度。比例范围为 0～1；高度、最大宽度和底部间距必须是非负整数。高度、比例或最大宽度为 0 时不显示；高度和底部间距超出可用范围时安全收缩，标记不会因尺寸计算而越过选项边界。

标记水平居中，以原始选项矩形的底边为基准，不跟随选中背景的 drawInset 或内容的 contentOffset。参数只改变装饰绘制，不参与测量、点击、滚动范围或焦点框；仍受 Segmented 的视口裁剪。Inspector 的只读“主题”视图可查看这些项及其来源。

## 调试与验证

原生 Inspector 的“属性 / 信号 / 主题”三个 Segmented 视图相互独立。主题视图只读，显示类型、变体、状态、有效项和来源链；明确区分显式属性、局部覆盖、祖先主题、资源父主题、代码默认和 Inspector 预览。调试快照只有文本与 ID，不保留已释放控件或可变 Theme。

```powershell
.\gradlew.bat :ore-lib:oreTest :ore-lib:inspectorTest
.\gradlew.bat verifyOreRelease verifyOrePortability
```

主题测试覆盖登记、变体、继承、局部覆盖恢复、资源失败与循环、非对称九宫格、平铺、小目标、零内距、布局失效和订阅清理。客户端验证继续使用独立运行目录，不能对玩家世界运行测试。

## 统一文本排版与字体度量

Ore 的 `text` 包负责字体资源、ICU 字素／双向分析、整形、换行与绘制。`TextLayout` 是不可变的结果，文档位置为 UTF-16；命中、双向选区、软换行光标、输入法候选位置与绘制复用同一份几何。Input、NumberInput、TextArea 仍由 MUI 保存文档与撤销记录，预编辑不写入文档。普通字体为 Mojangles → Noto Sans → Minecraft 最终回退，hero 为 Minecraft Ten → Noto Sans → 最终回退。

可以通过资源包替换字体 JSON 和 TTF／OTF；不需要在 Java 中添加字体名称分支。仓库解析 reference、bitmap、ttf、space、unihex 提供者及资源包叠加顺序。轮廓字体读取 em、ascent、descent、leading 和 cap-height，并缓存字面参考范围。AUTO 将不同字体族协调到共同视觉参考范围，行框按整个回退链稳定计算，不随输入字符改变基线。

```json
"Control:font_metrics": {
  "match_mode": "AUTO",
  "line_spacing": 1.0,
  "optical_offset_y": 0,
  "faces": {
    "ore:minecraft_ten.ttf": { "scale": 1.0, "baseline_shift": 0.0 }
  }
}
```

- `match_mode` 可设为 `NATIVE` 保留原生比例；字号仍由现有 `font_size` 主题项控制。
- `line_spacing` 是稳定行框的倍率，至少 1；`optical_offset_y` 为 GUI 像素，只修正启用光学居中的单行控件。
- `faces` 的键是实际字体文件资源 ID，`scale` 必须为有限正数；`baseline_shift` 使用 em，正值向下。未配置时分别为 1 和 0。位图字体也可通过纹理资源 ID 配置。
- 原先字体 JSON 中仅用于 Ore 视觉校准的 size／shift／oversample 已移除。Ore 的字号、缩放与基线由统一排版层解释，不依赖 Minecraft 的 TTF 渲染器。
- 主题值沿用继承、类型变体及局部覆盖，例如 `label.setThemeOverride(OreTheme.FONT_METRICS, policy)`。清除覆盖后恢复继承值；资源重载更新字体版本，不清空输入内容或撤销记录。

`OreText.setBackendMode(AUTO / BUILTIN / MODERN_UI)` 选择后端。AUTO 在公开 API 可用且文字引擎及抗锯齿启用时使用 Modern UI；否则记录原因并使用内置 Java 17 后端。已验证版本为 Modern UI 3.11.1.6。适配器只借用其公开整形及字形缓存，不修改 Modern UI 的全局配置。缺失对应字形时需要在字体资源的回退链中提供支持该脚本的字体；用不含该脚本的字体无法获得正确整形。

每个布局绑定后端与字体资源版本。布局缓存最多 512 项，大于 8192 UTF-16 单元的文本不进入缓存；字形图集在帧边界执行容量回收，刷新或关闭客户端时释放 Ore 自有纹理，绝不释放 Modern UI 的纹理。缓存不持有 Control 或 Window。

Ore 文字使用独立的 `ore:text` shader。轮廓字形保留 8 位灰度覆盖率，位图保持最近邻采样；字体颜色 Alpha、上下文 Alpha 和覆盖率只相乘一次，输出预乘 Alpha，使用 `ONE / ONE_MINUS_SRC_ALPHA` source-over。没有 0.1 的透明度截断。阴影独立绘制，状态在返回宿主前恢复。该管线不替换全局 Minecraft 字体或 shader。

彩色 Emoji 来自固定 Noto Emoji v2.047，资源离线打包在 `assets/ore/text/emoji.zip`，序列索引在 `emoji.json`；原始资产和旗帜的许可证位于 `META-INF/licenses/noto-emoji`。资源包可覆盖单张 `assets/ore/text/emoji/<原文件名>.png`，也可替换完整索引与归档。来源：https://github.com/googlefonts/noto-emoji/tree/v2.047 。

F8 文字辅助线显示实际 TextLayout 行框、基线、后端、字体文件与度量；主题面板可查看 `font_metrics` 的值和来源。F9 的 Label 示例提供字体／后端切换、复杂文字、Emoji、透明度、阴影及深浅／彩色／棋盘背景。“Noto Sans — 多语种”示例包含阿拉伯文、希伯来文、天城文及泰文轮廓字体，这些演示字体仅进入开发资源，不改变发布字体链。

回归入口包括 `regressionTextAlphaTest`、`regressionTextLayoutRegressionTest`、`regressionComplexTextTest`、`regressionFontRepositoryTest`。复杂文字测试使用独立、带许可的 Noto 阿拉伯文、希伯来文、天城文及泰文字体，测试字体不打包发布。开发客户端的 `geg.glyph.smoke=true` 夹具额外验证真实 shader、图集上传、FBO 合成及 GL 状态恢复；必须使用隔离运行目录。
