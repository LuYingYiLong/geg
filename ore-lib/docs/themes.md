# Ore 原生主题

主题由 Ore 自己解析和绘制。ModularUI 仅保留宿主、文本编辑与焦点适配所需的内部默认主题；不要向 MUI 注册 Ore 主题，也不要创建 `assets/ore/themes.json`。

## 注册与加载

默认资源为 `assets/ore/themes/ore.json`，ID 为 `ore:ore`，代码安全默认值为 `ore:default`。纹理位于 `assets/ore/textures/gui` 和 `assets/ore/textures/icons`。资源包覆盖相同路径即可；旧的 MUI JSON 格式和 `textures/modularui` 路径已删除。

每个控件使用静态 `registerTheme(ThemeDB)` 声明类型和项，初始化统一调用。第三方类型使用带命名空间的名称，并重写 `getThemeType()` 返回登记的名称。禁止在构造函数或逐帧绘制中注册。

```java
public static final ThemeKey<Integer> TITLE_COLOR = ThemeKey.of(
        "example:Status", "title_color", Integer.class,
        ThemeKey.Kind.COLOR, 0xFFFFFFFF, false);

public static void registerTheme(ThemeDB db) {
    db.registerType("example:Status", "Control");
    db.register(TITLE_COLOR);
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
label.setThemeOverride(OreTheme.TEXT_COLOR, 0xFF8CB3FF);
Integer localColor = label.getThemeOverride(OreTheme.TEXT_COLOR);
int effectiveColor = label.getThemeItem(OreTheme.TEXT_COLOR);
label.removeThemeOverride(OreTheme.TEXT_COLOR);
```

`getTheme()` 只返回控件自身设置的主题，未设置时为 null。解析顺序为显式属性、局部覆盖、自身主题、最近到最远祖先主题、宿主默认主题、登记的安全默认值。每个主题资源优先尝试类型变体、控件类型和基础类型，再查资源父主题；缺失项继续回退。

`getThemeOverride(ThemeKey<T>)` 类型安全地返回当前控件对该项的局部覆盖，未设置时返回 null（0、false 等已设置值照常返回）。它只按完整主题项 ID 查询，不解析类型变体、继承、显式属性或回退项，也不会建立主题订阅或使布局失效。要取得绘制实际使用的最终值，使用 `getThemeItem`；查询来源使用 `getThemeResolution`。所有查询和修改均在控件所属线程执行。

内部部件继承其所有者。弹出菜单通过 `setThemeParent(trigger)` 继承触发控件，独立于 Overlay 的绘制父级；关系检查循环，销毁时解除。普通应用代码不需要为 Dropdown 手动设置该关系。

共享 Theme 不随控件销毁。`theme.changed()` 通知有效值变化，控件收到合并的 `THEME_CHANGED`。颜色只使绘制缓存失效；字体、字号及 StyleBox 内距同时使测量与布局失效。控件订阅在 `queueFree()` 的最终清理阶段断开。回收节点保留所有权；相同所有权的重复登记不会重置滚动文字或布局。

`Label.setColor/setFontSize/setFont/setHero/setShadow` 表示显式属性。使用 `clearColor/clearFontSize/clearFont/clearHero/clearShadow` 恢复继承。按钮与弹层的字体 setter 转发到内部 Label；它们也提供 `clearFontSize()`。Theme 和局部覆盖不会被资源重载或 Inspector 属性预览覆盖写回。

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
  "Label:color": "#FFFFFFFF",
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
