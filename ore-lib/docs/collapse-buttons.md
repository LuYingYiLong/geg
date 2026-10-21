# Collapse 与按钮主题变体

Button 使用主题变体选择外观，默认样式与 SecondaryButton 相同。内置变体包括 SecondaryButton、PrimaryButton、DestructiveButton、TextButton、MenuButton 和 DarkButton；它们均为主题类型，不是 Java 子类。

```java
Button action = new Button(Component.literal("Confirm"));
action.setThemeTypeVariation("PrimaryButton");
dropdown.getTriggerButton().setThemeTypeVariation("TextButton");
```

按钮解析 `normal`、`hover`、`pressed`、`disabled` StyleBox，以及 `font_color`、`font_hover_color`、`font_pressed_color`、`font_disabled_color` 文字颜色。按下偏移由 StyleBox 的 drawInset 和 contentOffset 决定。第三方可通过 ThemeDB 注册继承 Button 或现有变体的新类型，无须修改 Button 绘制逻辑。

```java
Collapse section = new Collapse(Component.literal("Options"));
section.setSecondaryText(Component.literal("Optional settings"));
section.setContent(content);
section.expandedChanged().connect(window, expanded -> { /* target state changed */ });
```

Collapse 默认折叠，标题使用 DarkButton。主标题与可选说明保持单行，说明使用次要颜色；宽度不足时省略，箭头保留空间。正文是保留的 Panel，默认内距为左右 8px、上下 4px。setContent 替换时仅卸载旧节点，永久废弃需由调用方 queueFree。

正文使用 smoothstep 高度过渡，时长由 animation_duration 控制（当前默认 100ms），正文不缩放。标题和箭头不播放展开动画，箭头直接按目标状态切换纹理。中途反向从当前高度继续；首次布局前设置 expanded 直接显示初始状态。setExpandedNoSignal 只抑制通知，不改变后续动画规则；setAnimationDuration(0) 禁用正文动画。

disabled 只禁用标题开关，已展开的内容仍可操作。折叠开始时正文停止接收输入，并清理正文的捕获、焦点、预编辑和菜单；关闭结束才隐藏，不销毁正文或连接。新的裁剪视口遵守 Transform2D 的轴对齐限制。

Collapse 的 panel、secondary_color、collapsed_arrow、disabled_collapsed_arrow、expanded_arrow、disabled_expanded_arrow、animation_duration 均可通过主题配置。箭头由同一个 TextureRect 绘制，不再依赖旋转角度；四种状态默认分别使用 arrow_down_light、arrow_down、arrow_up_light、arrow_up。

Collapse 和 Drawer 均注册 `padding_top`、`padding_right`、`padding_bottom`、`padding_left` 四个非负整数主题项，参与测量与布局。Collapse 只调整正文，Drawer 调整标题、正文和操作区外围的内距；Drawer 默认仍为四边 4px。

```json
"Collapse:padding_top": 4,
"Collapse:padding_right": 8,
"Collapse:padding_bottom": 4,
"Collapse:padding_left": 8,
"Drawer:padding_top": 4,
"Drawer:padding_right": 4,
"Drawer:padding_bottom": 4,
"Drawer:padding_left": 4
```

Collapse 和 Drawer 均只通过主题项配置内距，例如：

```java
collapse.setThemeOverride(Collapse.PADDING_LEFT, 8);
Integer localLeft = collapse.getThemeOverride(Collapse.PADDING_LEFT); // 当前控件覆盖值，未设置时为 null
int effectiveLeft = collapse.getThemeItem(Collapse.PADDING_LEFT); // 包含继承和默认值回退

drawer.setThemeOverride(Drawer.PADDING_TOP, 1);
drawer.setThemeOverride(Drawer.PADDING_RIGHT, 4);
drawer.setThemeOverride(Drawer.PADDING_BOTTOM, 4);
drawer.setThemeOverride(Drawer.PADDING_LEFT, 1);
```

调用 `removeThemeOverride` 可恢复对应边的主题继承。默认 `Collapse:panel` 的 contentMargin 为 0，避免重复计算内距；自定义 StyleBox 的 contentMargin 和外部 Padding 仍独立生效。

F9 提供交互示例，F8 可编辑主题变体并查看样式来源。纹理资源文件名统一使用 hover 后缀；旧 Button.Type 与旧按钮主题项已移除。
