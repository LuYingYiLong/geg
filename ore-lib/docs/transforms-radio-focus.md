# 控件变换、焦点与单选

`Control.setRenderTransform(Transform2D)` 设置显示变换，默认单位变换，旋转中心比例为 `(0.5, 0.5)`。平移、旋转和正等比缩放作用于完成布局及 Offset 后的子树，不改变容器分配的矩形。`getAbsoluteBounds()` 仍返回未变换的树坐标；`EventGeometry.displayed()` 返回 Window 坐标中的包围范围，`EventGeometry.matrix()` 与其逆矩阵用于精确转换。

```java
control.setRenderTransform(new Transform2D(0, 0, 45, 1, 0.5, 0.5));
```

绘制使用不可重写的 `Control.draw()` 入口。自定义单个控件的外观使用 `render()`；需要自定义子树绘制顺序时重写 `drawContent()`。命中对应使用 `hitTestTree(treeX, treeY, windowX, windowY)`：判断自身时使用 tree 坐标，调用子节点 `hitTop()` 时使用 Window 坐标。框架统一处理逆变换及裁剪，调用方不再额外加减 Scroll 偏移。

Window 和 CanvasRegion 宿主视口自身保持单位 RenderTransform；变换应设置在其内容节点上，Canvas 宿主定位继续使用既有 CanvasTransform。

首版不提供多边形裁剪。Scroll、Table、Canvas 视口及启用裁剪的 Label 不允许产生非轴对齐裁剪；MUI 输入框不允许旋转。设置变换、挂接子节点或启用裁剪时先检查，失败不改变原状态。正向 Scroll 中的普通内容仍可旋转。纹理保持最近邻采样，45° 旋转会出现正常的像素阶梯。

## 焦点

`Control:focus` 是 StyleBox，默认白色 1px 空心框，`expandMargin` 四边为 1；`Input:focus` 四边为 2。Segmented 额外的右侧像素由 `Segmented:focus` 表达。焦点样式不参与测量与命中。

`getFocusGeometry()` 提供局部矩形、到 Window 的变换和裁剪。Window 在有效弹层内容之后调度 `drawFocus()`，绘制时保持原始形状并应用裁剪。需要焦点只跟随内部部件时，可返回该部件的 `FocusGeometry`；Switch 跟随滑块，Radio 跟随图标。

## Radio 与 RadioGroup

Radio 使用原始 8×8 纹理，主题常量 `Radio:icon_rotation_degrees` 默认为 45。角度只作用于图标和焦点，文字保持正向。`setIconRotationDegrees()` 是显式属性覆盖，`clearIconRotationDegrees()` 恢复主题；也可使用类型化主题覆盖。

```java
Box choices = new Box();
Radio first = new Radio(Component.literal("First"));
Radio second = new Radio(Component.literal("Second"));
choices.addChild(first);
choices.addChild(second);
RadioGroup<String> group = new RadioGroup<>(choices, String.class);
group.addRadio("first", first).addRadio("second", second);
group.selectionChanged().connect(choices, value -> { /* value may be null after clearing */ });
```

RadioGroup 是由 SignalOwner 托管的逻辑组，不是布局容器，也不拥有成员控件。成员可以位于不同父容器；每个 Radio 只属于一组。初始不自动选择，用户释放时选中，重复点击保留选中；代码可通过 `clearSelection()` 清空。普通 setter 只在变化后通知，NoSignal setter 更新整组但不通知。

`FormFields.radioGroup(String.class, choices, group)` 将组作为类型化字段，可使用 `Validators.required()`。静默回填和 reset 不触发业务操作。临时移除或隐藏保留组关系；永久销毁成员时解除关系，组选中项消失时清空；关闭组解除引用和订阅，不销毁 Radio。
