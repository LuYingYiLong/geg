# Box 换行

`Box` 默认垂直排列，`wrap=false`。水平换行向下排列，垂直换列向右排列。Box 不裁剪、不处理滚轮，超出视口的内容放进 Scroll。

```java
Box actions = new Box().setOrientation(Box.Orientation.HORIZONTAL)
        .setWrap(true).setGap(4).setLineGap(8);
Button first = new Button(Component.literal("One"));
first.setMinimum(48, 22);
first.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
actions.addChild(first);
```

先按自然尺寸和最小尺寸分行，再在每行内部按 SizeFlags 分配空间；EXPAND 不会提前占满整行。扩展控件的 stretch ratio 仅与同一行的扩展控件比较，整数余量按节点顺序分配。交叉轴的 FILL／SHRINK 对齐应用于所在行，额外交叉空间不分给各行。

单项自然尺寸超过可用空间时允许缩小偏好尺寸，仍保留最小尺寸；最小尺寸也放不下时独占一行并溢出。隐藏和待销毁节点不占空间。

lineGap 未显式设置时跟随 gap；调用 `clearLineGap()` 恢复跟随。属性变化触发布局失效，Inspector 可以临时编辑两项属性。

垂直换列必须获得有限高度。主轴为 `Integer.MAX_VALUE` 时视为无界，不换行，也不把 EXPAND 控件扩展到无界尺寸。测量与布局使用相同分组和分配过程；水平分配宽度后重新测量文字高度。

