# TextureProgressBar

三层纹理进度条，适合火焰、加工箭头、血条及环形计量。PNG 的透明和半透明像素默认参与合成，无需开启透明选项。普通 StyleBox 进度条继续使用 ProgressBar。

```java
TextureProgressBar flame = new TextureProgressBar()
        .setUnderTexture(TextureSource.themed(IconTheme.LIT_PROGRESS_UNDER))
        .setProgressTexture(TextureSource.themed(IconTheme.LIT_PROGRESS))
        .setFillMode(TextureProgressBar.FillMode.BOTTOM_TO_TOP);

TextureProgressBar arrow = new TextureProgressBar()
        .setUnderTexture(TextureSource.themed(IconTheme.BURN_PROGRESS_UNDER))
        .setProgressTexture(TextureSource.themed(IconTheme.BURN_PROGRESS));

// 读取宿主 Menu 已同步的进度；控件本身不推进机器加工
flame.setValueNoSignal(fuelFraction * 100);
arrow.setValueNoSignal(processingFraction * 100);
arrow.valueChanged().connect(window, value -> updatePreview(value));
```

火焰默认 14×14px，箭头默认 24×16px。通用控件的三份图片都默认为空，不默认使用机器素材。可直接传入 `TextureSource.region(new TextureRegion(...))` 引用整张 GUI 图集中任意区域；不要求拆分图片文件。

## 数值及层次

默认范围 0～100、值 0。`setMin/setMax/setLimits/setValue` 校验有限数值，要求 min < max 且跨度有限，数值钳制到范围。范围替换原子生效。只有有效值实际变化才发送 `valueChanged(Double)`；对应 NoSignal 方法更新显示但不通知。

绘制顺序为 under → progress → over → 可选百分比。`setUnderTexture/setProgressTexture/setOverTexture` 接收 TextureSource 或 null。背景已经包含灰色箭头或火焰时，可以只设置 progress；over 可用作前景边框或高光。图片通过所属控件解析主题，支持局部覆盖及资源重载。

`setProgressOffset(new Offset(x, y))` 只移动 progress 层。三层及偏移的联合包围范围决定参考画布；负偏移扩展画布并统一平移，保留相对位置。当前进度不改变自然尺寸。偏移只影响绘图，不创建交互区域。

## 填充和缩放

FillMode 支持四向、CENTER_HORIZONTAL、CENTER_VERTICAL，以及 CLOCKWISE、COUNTER_CLOCKWISE、BIDIRECTIONAL_RADIAL。值改变的是可见区域和对应 UV，不会压缩整张填充纹理。

环形初始角默认 0°（右侧），正角度沿屏幕顺时针。`setRadialInitialAngle` 将有限角度归一化到 [0,360)；`setRadialFillDegrees` 接受 0～360，默认 360。`setRadialCenterOffset` 相对 progress 图像中心偏移，最终中心钳制到图像矩形。双向模式以初始角为中心向两侧各展开半个扫过角度。

环形按角度裁切原始矩形，不自动把图片裁成圆形。需要圆环时请提供自带透明区域的圆环 PNG。矩形纹理的可见面积不一定与角度百分比线性对应。

StretchMode 默认 KEEP，保持原尺寸；SCALE 映射到控件矩形；KEEP_ASPECT_CENTERED 保持比例居中。三层和偏移共用同一映射，不能各自铺满。`setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE)` 允许父容器分配小于固有纹理的尺寸；用户显式 minimum 不受影响。不提供九宫格、平铺或进度动画。

## 透明度和主题

`setOpacity` 默认 1，允许有限的 0～1，作用于三层及百分比。透明合成无需设置它；只有需要整控件淡出时才调整。

主题项为 `TextureProgressBar:under_tint`、`progress_tint`、`over_tint`，均默认 #FFFFFFFF；`font_color` 默认白色、`font_size` 默认 8。通过 setThemeOverride 设置，Tint 的 Alpha 与 PNG Alpha、opacity 和上下文透明度各相乘一次。文字使用 Ore 的统一排版层。

控件不接受输入、不获取焦点。内部百分比 Label、几何缓存及主题关系跟随 queueFree；控件不拥有或销毁共享纹理资源。Inspector 可查看图片与主题来源，编辑数值、偏移、填充、缩放及透明度，并支持撤销和恢复。

F9 → TextureProgressBar 展示九种模式及透明合成；Chest / Furnace 示例使用真实火焰和箭头纹理，但数据仍为本地模拟。

验证入口：`:ore-lib:regressionTextureProgressBarTest`、`:ore-lib:regressionProgressBarTest`、`:ore-lib:regressionInspectorInspectorTextureProgressTest`、`:ore-lib:regressionInspectorGalleryLayoutTest`。真实 GPU、内嵌／原生／Canvas 验证使用 opt-in 的 TextureProgressBarClientTest 及隔离客户端，不进入发布包。
