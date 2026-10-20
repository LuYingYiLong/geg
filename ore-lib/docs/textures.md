# TextureRect 与图片资源

普通 UI 图片使用 `TextureRect`，不再通过 Button 的私有图片结构或 StyleBox 绘制。背景与九宫格继续使用 StyleBox；Avatar 和 Slot 继续保留其专用渲染。

## 创建与共享

`TextureRegion` 是不持有 GL 资源的不可变数据：资源 ID、图片宽高、源区域 x/y/width/height。区域必须为正且位于图片内。`getSubRegion()` 使用相对源区域的坐标，适合图集和动画帧。

```java
TextureRegion logo = TextureRegion.full(
        ResourceLocation.fromNamespaceAndPath("example", "textures/gui/logo.png"), 64, 32);
TextureRect image = new TextureRect().setTexture(TextureSource.region(logo));
box.addChild(image);

Button button = new Button(Component.literal("Filter"));
button.setIcon(TextureSource.themed(IconTheme.FILTER));
button.getIconControl().setOpacity(0.7F).setFlipHorizontal(true);
```

`TextureSource` 表示固定区域或 `ThemeKey<TextureRegion>`。数据可在多个控件之间共享；每个按钮、菜单行和虚拟行分别持有自己的图片控件。`setTexture(null)` 清空图片。

Button 的 `setIcon/getIcon`、`setTrailingIcon/getTrailingIcon` 使用 TextureSource；`getIconControl/getTrailingIconControl` 返回两个固定的内部 TextureRect。Dropdown 同样接受 TextureSource，并提供 `getIconControl/getArrowControl`。图片来源更新不替换内部控件，同尺寸换帧不触发布局。

## 显示属性

- `stretchMode`：SCALE 拉伸；TILE 平铺；KEEP 原尺寸左上对齐；KEEP_CENTERED 原尺寸居中；KEEP_ASPECT 等比缩放左上对齐；KEEP_ASPECT_CENTERED 等比缩放居中；KEEP_ASPECT_COVERED 等比铺满并裁剪 UV。默认 KEEP_ASPECT_CENTERED；按钮图标默认 KEEP_CENTERED。
- `expandMode`：KEEP_SIZE（默认）将图片尺寸纳入固有最小尺寸；IGNORE_SIZE 不使用图片最小尺寸。不会改写显式 minimum。SizeFlags 继续决定容器空间分配。
- `opacity`：有限的 0～1，默认 1。`modulate`：ARGB 颜色，默认 `0xFFFFFFFF`。最终颜色与图片和当前绘制作用域的颜色、透明度相乘。
- `flipHorizontal/flipVertical`：默认 false，只反转 UV；不翻转布局、点击或焦点矩形。平铺先翻转完整图块，再裁剪末尾图块，避免串入图集中相邻区域。

图片默认不获取焦点、不参与业务命中。KEEP 模式允许原尺寸图片按正常容器溢出规则绘制；TILE 和 COVERED 的尺寸/UV 被限制在控件区域内，所有模式继续服从祖先裁剪。

共享 TexturePainter 使用标准 Minecraft 着色器和缓冲区，保留资源原有采样设置（默认最近邻）。绘制作用域恢复 shader、颜色、纹理绑定、混合和活动纹理单元，不改变矩阵与裁剪，不通过主窗口尺寸换算副窗口坐标。

## 主题、重载与诊断

图片项使用 `ThemeKey.Kind.TEXTURE`，值类型是 TextureRegion。JSON 保留 `location/imageWidth/imageHeight/x/y/width/height`；缺省尺寸在资源加载时读取真实 PNG，每次重载共享尺寸缓存。图片项不接受 style、内距、边框或透明度等背景字段；这些字段分别属于 StyleBox 或 TextureRect 显示属性。

纹理数据通过图片控件所属的主题链解析。资源重载或尺寸变化使测量失效；内部图片会通知所属组合控件重新布局。永久销毁只清理控件及订阅，不释放共享图片数据或 Minecraft 管理的纹理。

Inspector 显示内部 TextureRect，允许修改拉伸、尺寸模式、翻转、透明度和着色；`modulate` 接受 `#RRGGBB` 或 `#AARRGGBB`。来源与区域只读。撤销、恢复及虚拟行换绑都会处理内部图片的覆盖，不把上一行的修改带给下一行。

旧的 Button 图片嵌套类型、主题图片绘制包装、裸资源图标常量与反向主题映射已删除，没有兼容重载。所有调用方必须使用 TextureSource 和 TextureRegion。
