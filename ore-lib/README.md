# Ore Lib for Forge 1.20.1

Ore 是嵌入宿主 Mod 的客户端 UI 库，依赖 Java 17、Forge 1.20.1、ModularUI 3.3.1，不声明独立 Mod 身份。控件、Inspector、资源和构建支持统一放在 `ore-lib/`，复制这一个目录即可接入其他同版本 Forge 工程。

## 源码集与发布边界

| 源码集 | 内容 | 用途 |
| --- | --- | --- |
| `src/main` | 控件、布局、字体、窗口、IME、头像缓存、调试桥接 | 开发和发布 |
| `src/inspector` | 原生 Inspector、属性适配、覆盖、历史、开发服务描述与翻译 | 仅开发客户端 |
| `src/test` | 通用控件回归 | 测试 |
| `src/inspectorTest` | Inspector 回归 | 测试 |

四个源码集属于同一个 Gradle 模块。标准 `jar` 仅包含 main，不包含 Inspector。Inspector 只能引用 main；main 不引用 Inspector。运行时通过开发服务描述自动注册 F8。

## 复制与构建

宿主使用 ForgeGradle 6，映射和 Forge 版本与库一致。配置示例：

```groovy
// settings.gradle
include 'ore-lib'
```

```groovy
// 宿主 build.gradle，加载 ForgeGradle 6 后
evaluationDependsOn(':ore-lib')
repositories {
    maven { url = 'https://www.cursemaven.com'; content { includeGroup 'curse.maven' } }
}
dependencies {
    compileOnly project(':ore-lib')
    implementation fg.deobf('curse.maven:modularui-624243:8339410')
    // ModularUI 开发模式会注册依赖 Curios 的测试物品。
    runtimeOnly fg.deobf('curse.maven:curios-309927:6418456')
}
minecraft.runs.configureEach {
    mods { your_mod_id {
        source sourceSets.main
        source project(':ore-lib').sourceSets.main
    } }
    args '--mixin.config', 'ore.mixins.json'
}
minecraft.runs.client {
    mods { your_mod_id { source project(':ore-lib').sourceSets.inspector } }
}
// ForgeGradle 从宿主解析自定义源码集的 classes 任务，转发到库模块。
tasks.register('inspectorClasses') { dependsOn ':ore-lib:inspectorClasses' }
tasks.matching { it.name == 'prepareRunClientCompile' }.configureEach {
    dependsOn ':ore-lib:inspectorClasses'
}
tasks.named('jar', Jar) {
    from project(':ore-lib').sourceSets.main.output
    manifest { attributes 'MixinConfigs': 'ore.mixins.json' }
    finalizedBy 'reobfJar'
}
```

`your_mod_id` 换成宿主 Mod ID。已有 Mixin 列表须保留并追加 `ore.mixins.json`。最终合并 main 未重映射输出，由宿主统一 reobf；不要合并 Inspector、Forge、ModularUI、Curios 或编译注解。宿主 mods.toml 自行声明 ModularUI 依赖。服务端、数据生成不要挂载 inspector 源码集。

模块逻辑名和物理目录均可修改，不需要额外定位属性。例如：

```groovy
include 'ui-lib'
project(':ui-lib').projectDir = file('libraries/controls')
```

随后将宿主构建脚本中的 `:ore-lib` 替换为 `:ui-lib` 即可。模块内部所有路径按 projectDir 计算。

默认环境位于 `gradle/environment.properties`，无需宿主提供 Mod 名称、group 或版本。宿主可覆盖 `minecraft_version`、`forge_version`、`mapping_channel`、`mapping_version`、`java_version`、`modularui_dependency`、`modularui_dev_runtime_dependency`。当前仅验证 1.20.1，不保证跨 Minecraft 大版本兼容。

## 客户端初始化

宿主客户端 Mod 构造分支，在创建控件前调用：

```java
OreClient.initialize(
        OreClientOptions.defaults(MOD_ID,
                Minecraft.getInstance().gameDirectory.toPath().resolve(MOD_ID).resolve("avatars")),
        modBus, MinecraftForge.EVENT_BUS);
```

初始化幂等，不立即创建原生窗口或激活输入法。不要在专用服务端加载客户端类。完整 options 构造器可配置主题、字体、音效和头像缓存位置。

资源命名空间为 `ore:`，默认主题名为 `ore`，字体为 `ore:ore`、`ore:ore_hero`、`ore:ore_fallback`。资源包覆盖 `assets/ore/`。Inspector 的翻译属于 `ore_inspector:`，只挂载在开发客户端。

## Switch

基于 [Ant Design Switch](https://ant.design/components/switch-cn/) 的状态与回调语义，使用固定像素纹理：

```java
Switch toggle = new Switch(true)
        .onChange(checked -> updateSetting(checked))
        .onClick(checked -> { });
box.addChild(toggle);
toggle.checked(false); // 程序化更新不触发回调或音效
toggle.disabled(true);
toggle.loading(true); // 显示滑块内的 Spin，阻止再次操作
```

`new Switch()` 默认关闭，布尔构造参数设置初始状态。checked、disabled、loading 同时提供 getter 和链式 setter。用户松开鼠标或 Enter／Space 后先更新状态，再触发 onChange、onClick；拖出后松开会取消。禁用、加载、失焦、隐藏和顶层弹层会取消未完成操作。

底板为 27×13，滑块为 14×15，底部对齐；滑块从 x=0 移动到 x=14，整体占 28×15。全部纹理按原始像素绘制，额外分配的空间不会放大图像或点击区。默认水平始端收缩、垂直居中收缩；聚焦框为滑块外围 1px，并跟随移动。

默认 240ms 的 ease-out bounce 动画支持途中反向切换，不因重复绑定相同 checked 值而重新开始。`animationDuration(ms)` 可调整时长，0 关闭动画；初始值直接定位，不播放入场动画。

## 输入事件与弹出菜单

所有宿主输入交给所属 `Window.dispatchInput(InputEvent)`。`event` 包提供鼠标、双轴滚轮、键盘、提交文本、预编辑和取消事件；`WindowInputs` 仅将 MUI 回调转换为这些事件。旧的 `dispatchPointer`、`dispatchScroll`、`pointerPressed`、`pointerReleased` 和 `pointerDragged` 接口已删除。

自定义控件重写 `input(InputEvent)`，通过 `target()`、`currentTarget()`、`localX()`／`localY()` 读取分发信息，通过 `accept()` 消费事件。鼠标按下被消费后，Window 将后续移动和释放交给该处理者；组合控件也可以调用 `event.capturePointer()` 显式申请捕获。释放事件的 `over()` 同时考虑控件命中范围和祖先裁剪；拖出后松开不能激活按钮。未处理事件仅沿命中路径向父节点冒泡，不穿透到下层兄弟节点。

`EventGeometry` 统一显示区域和局部坐标转换。Scroll 只转换内容分支，滚动条不叠加自身所属 Scroll 的偏移。控件不应自行加减滚动量。一个事件只能在所属 Window 的客户端线程分发一次，跨窗口、跨线程和重复分发会被拒绝。

`Dropdown` 必须加入控件树，例如作为 `Table.ControlCell`。其菜单由 Window 创建为 Overlay 子树，选项使用真实 Button，统一处理焦点、音效和释放行为；无需手动绘制或转发菜单。菜单会避开宿主边界和所属浮动面板，点击外部关闭并消费该次点击，Esc 返回触发控件；触发节点离开可见区域、换绑、隐藏或被更高层弹层阻断时关闭。

`InputControl` 负责把事件适配到现有 MUI 编辑器，接收时应用字段自身的 MUI 局部矩阵。Ore 的 ModularPanel Mixin 仅在 Canvas 已处理当前按下时保留 Window 设置的字段焦点；其他 MUI 控件仍沿用原有焦点行为。树中 Input／NumberInput／TextArea 的 MUI 直接回调不会重复编辑；宿主不要再单独调用输入框事件方法。提交文字和 IME 预编辑分开处理，预编辑更新不写入文档。未加入 Ore 树的独立 MUI Input 保留原来的接收方式。

## 测试与兼容

```powershell
.\gradlew.bat :ore-lib:oreTest :ore-lib:inspectorTest
.\gradlew.bat :ore-lib:check
```

回归从模块 build/tmp 运行，不依赖宿主目录名。`oreTest` 的类路径只有正式控件；`inspectorTest` 额外使用 inspector 输出。可用 `-PimBlockerJar=<路径>` 和 `-PcustomSkinLoaderJar=<路径>` 验证真实兼容 JAR。

ModularUI 3.3.1 的开发测试注册需要 Curios 运行库，正式运行不走该分支；Ore 不链接 Curios API。Modern UI、IMBlocker 和 CustomSkinLoader 保持可选。Windows 原生窗口和输入法按需启用，其他系统保留内嵌窗口。`gradle/legacy-annotations` 仅用于读取 ModularUI 旧注解，不能进入发布包。
