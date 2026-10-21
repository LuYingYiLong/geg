# Slider

水平数值输入，支持单滑块和双滑块。轨道只沿长度伸展，默认保持 6px 高；滑块按纹理原尺寸 14×15px 绘制。横向默认 FILL，纵向 SHRINK_CENTER，偏好宽度 128px，最小宽度 28px。额外高度只影响居中，marks 标签在轨道下方单独占行。

```java
Slider volume = new Slider().setLimits(0, 100).setStep(5).setValueNoSignal(40);
volume.valueChanged().connect(window, value -> updateVolume(value));
volume.changeCompleted().connect(window, change -> saveVolume(change.upper()));

Slider interval = new Slider().setRangeNoSignal(true)
        .setRangeValueNoSignal(new Slider.RangeValue(20, 80));
interval.rangeChanged().connect(window, value -> updateInterval(value.lower(), value.upper()));
```

- 范围滑块可以穿越，物理滑块跟随原鼠标捕获；对外始终返回有序的 lower／upper。重合时优先使用上次活动滑块，首次默认上限滑块。
- min／max／step 必须有限，min < max，step > 0。按十进制网格吸附，max 即使不能整除步长也始终可达。修改限制前检查 marks，失败保留原状态。`setLimits` 可一次替换整个范围。
- 单值模式使用 `set/getValue`，范围模式使用 `set/getRangeValue`；接口用错会报错。开启范围模式得到 `[min, 当前值]`，关闭后保留上限。
- `NoSignal` 方法用于回填。属性变化通知与用户完成通知分离；相同值回填不取消正在进行的拖拽，也不重新排序物理滑块。
- 点击轨道选择最近滑块并拖动；拖出后松开仍提交。失焦、隐藏、禁用、顶层弹层阻断和销毁只取消手势，保留最后值，不发送 `changeCompleted`。
- 左／右按步长调整，支持按键重复；Home／End 到端点。Shift＋左／右切换范围模式的活动滑块，上／下和 Tab 继续进行焦点导航。`reverse` 反转坐标轴；滚轮留给外层 Scroll。
- `setShowTicks(true)` 显示步长刻度；`setMarks(Map<Double, Component>)` 使用有序标签刻度。过密的刻度和标签按可见像素省略，不改变吸附精度。
- `setValueFormatter(Function<Double, Component>)` 自定义滑块悬浮提示；返回 null 隐藏提示。提示由 Window 最终阶段绘制，遵守弹层顺序。

## 表单

```java
FieldKey<Double> value = new FieldKey<>("volume", Double.class);
FormItem<Double> field = new FormItem<>(value, Component.literal("Volume"), FormFields.slider(volume));
FieldKey<Slider.RangeValue> range = new FieldKey<>("interval", Slider.RangeValue.class);
FormItem<Slider.RangeValue> rangeField = new FormItem<>(range, Component.literal("Interval"), FormFields.sliderRange(interval));
```

表单适配器的类型由创建时的模式决定；绑定期间不要切换单值／范围模式，需要切换时替换对应 FormItem。支持校验、静默回填、dirty／touched 和 reset。

## 主题与诊断

`Slider:slider`、`slider_disabled`、`grabber_area` 为 StyleBox；`grabber_normal/hovered/pressed/disabled` 和 `tick/tick_disabled` 为 TextureRegion。背景沿用 StyleBox 透明度和局部覆盖，滑块使用内部 TextureRect。focus 使用 Control 的主题化焦点，跟随活动滑块。

F9 的 Slider 示例包含单值、双滑块、反向、小数、禁用、marks 及表单。F8 显示当前值、边界、步长、活动滑块、信号和主题来源；业务数值只读，显示开关可临时预览。

回归：`regressionSliderValueTest`、`regressionSliderInteractionTest`、`regressionInspectorInspectorSliderTest`。开发客户端夹具使用 `ore.slider.smoke=true`，只能在隔离运行目录启用；夹具及 Gallery 不进入发布包。
