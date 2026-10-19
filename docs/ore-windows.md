# Ore Window 管线

## 创建窗口

```java
Input input = new Input().setPlaceholder(Component.literal("输入内容"));
Box content = new Box().setGap(4);
content.addChild(new Label(Component.literal("Title")).setHero(true));
content.addChild(new InputControl(input));
content.addChild(new Button(Component.literal("Close")));

Window window = Window.nativeWindow(WindowOptions.defaults("My Ore Window"))
        .setContent(content);
window.open();
```

`Window.embedded()` 使用 Minecraft GUI 视口，`Window.nativeWindow(options)` 创建独立 Windows 系统窗口。Input／NumberInput／TextArea 通过 InputControl 或 FormItem 加入树，宿主自动挂载 MUI 编辑器，不需要手工注册到另一个 Screen。控件树及输入实例不可同时归属两个宿主。

`Window(boolean fullscreen)` 保留为旧内嵌 Canvas 兼容入口；已有业务 Canvas 的手工弹出层适配保持不变。工厂创建的通用窗口统一管理 Dropdown 弹出层。`fullscreen` 仅表示填满宿主 GUI 视口。

`WindowOptions` 指定标题、客户区尺寸、最小尺寸及独立 GUI 缩放。默认 1280×800、最小 800×480、GUI scale=2，并根据工作区限制初始大小。WindowContext 根据 framebuffer、窗口客户区和 DPI 换算坐标。第一版不支持无边框、独占全屏或运行中更换宿主。

原生窗口以整数物理像素显示 UI：请求的 GUI scale 乘显示器 DPI 后四舍五入，最小为 1。例如默认 scale=2 在 125% DPI 下采用 3 倍物理像素，而非 2.5 倍。投影保留真实 framebuffer 与缩放的比值，窗口尺寸不能整除缩放时只裁去边缘的部分 GUI 像素，不拉伸整个界面。副窗口最终逐像素读取离屏纹理，不进行额外线性插值；字体自身的抗锯齿不受影响。

`open` 创建或打开，`requestFocus` 聚焦，`close` 关闭；`closed().connect(owner, slot)` 接收关闭通知。原生关闭的 GPU／GLFW 资源在客户端线程下一次调度释放，重复关闭不重复通知。API 仅客户端使用；其他系统原生 open 明确抛出暂不支持的异常，内嵌窗口仍可使用。

## 每窗口上下文

通过 `window.getContext()` 或 `control.getWindowContext()` 获取视口、缩放、窗口身份、按键／鼠标状态、剪贴板及 MUI 上下文。每个窗口分别拥有逻辑焦点、鼠标捕获、弹出层和 IME 会话；宿主在派发事件和绘制时进入可恢复的上下文作用域。

Windows IME 使用所属窗口的 HWND 和客户区光标位置。主窗口继续使用原有 IMBlocker 适配；副窗口使用独立 Windows 连接，不注册进 IMBlocker 的主窗口全局焦点容器。失焦结束预编辑和按压，重新聚焦恢复仍有效的逻辑焦点。Esc 依次取消预编辑、输入框失焦、关闭弹层，最后返回／关闭窗口。

## 绘制与生命周期

所有 Ore／Minecraft 字体绘制都在主 OpenGL 上下文中输出到窗口独立离屏目标；副上下文只呈现共享纹理，拥有自己的 capabilities、VAO 和 shader。两个上下文通过 GPU 同步，不回读 CPU 图像。投影、目标、viewport、纹理绑定、裁剪、混合、深度及 stencil 等在作用域退出时恢复。

原生 GuiGraphics 以所属窗口尺寸计算裁剪，不使用主窗口 GUI scale。窗口缩放和资源重载更新布局与绘制；窗口最小化时不绘制。调度沿用 Minecraft GLFW 事件循环和客户端线程，最高 30 FPS；主渲染停顿时使用客户端 tick 补充调度。

原生窗口存在期间临时关闭失焦暂停，最后一个关闭后恢复原设置。断开世界及客户端关闭时释放原生窗口。原生基础设施随 mod 的客户端代码发布，但只在显式打开时初始化；Inspector 与测试实现仍只在开发 source set。
