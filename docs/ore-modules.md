# Ore 模块结构与构建

## 依赖和发布

- 根工程：GEG 的市场、组织、邮件、聊天、背包入口及服务端业务。
- `:ore`：`io.github.luyingyilong.ore`，包含控件、布局、输入、字体、头像缓存、窗口宿主、无操作 DebugBridge 与属性注解。
- `:ore-inspector`：`io.github.luyingyilong.ore.inspector`，包含原生 Inspector、显式／反射适配、覆盖与编辑历史，仅在开发客户端挂载。
- 根 `src/oreDebug` 只提供 GEG 的 Inspector 启动桥；`src/oreDebugTest` 保留真实客户端联动夹具。

依赖方向为 GEG → Ore、GEG 开发启动桥 → Inspector → Ore。模块编译不依赖 GEG 主源码。两库没有自己的 `mods.toml`、`pack.mcmeta` 或 Mod 入口，也没有独立发布任务。

`gradle/ore-conventions.gradle` 统一 Java 17、Forge、映射、ModularUI 和旧 SideOnly 注解的编译环境。`build-support/legacy-annotations/java` 只用于编译，不进入运行类路径或发布包。

开发运行将 Ore 输出和 GEG 输出挂到同一个 `geg` Mod source；只在 client 中加入 Inspector 和启动桥，不另外加载模块 JAR。server、gameTestServer 和 data 不包含 Inspector。根 JAR 合并 Ore 未重映射的 main 输出，再由根 `reobfJar` 统一处理；不使用 Jar-in-Jar，不嵌入 ModularUI。

## 客户端初始化

仅在客户端 Mod 构造阶段调用：

```java
OreClient.initialize(new OreClientOptions(
        hostModId, "ore", OreFonts.BODY, OreFonts.HERO,
        ResourceLocation.fromNamespaceAndPath("ore", "ore_fallback"),
        OreSounds::playDefault, avatarCacheDirectory), modBus, forgeBus);
```

入口幂等；事件监听只注册一次。初始化不创建 GLFW 窗口、不激活 Windows IME，相关原生能力按需加载。默认字体为 Mojangles → Noto Sans → Minecraft 回退，hero 为 Minecraft Ten → Noto Sans → Minecraft 回退。宿主可传入自己的字体资源和音效回调；静音可传入空回调。

GEG 的初始化桥在 `GegClient`，继续使用游戏目录的 `geg/avatars`，已有离线／CSL 头像缓存无需移动。ModularUI 的字体兼容 Mixin 属于 `ore.mixins.json`；聊天快捷键 Mixin 仍属于 `geg.mixins.json`。

`NumberInput` 默认整数、无上下界；`decimalPlaces(n)` 配置固定精度，`minimumValue`／`maximumValue` 可用 null 清除边界。`availableMaximum` 用于动态可用上限，允许上限低于下限表示无可提交值。GEG 的 `GegInputs` 显式提供数量和 coin 预设；金额仍受两位小数和 long 分值上限约束。

## 资源覆盖迁移

| 原路径／入口 | 当前入口 |
| --- | --- |
| `assets/geg/textures/modularui/**` | `assets/ore/textures/modularui/**` |
| `assets/geg/themes/ore.json` | `assets/ore/themes/ore.json` |
| `assets/geg/font/*.ttf`、`*.otf` | `assets/ore/font/` 同名文件 |
| `assets/geg/sounds/<UI 音效>.ogg` | `assets/ore/sounds/` 同名文件 |
| `geg:ore`、`geg:ore_hero`、`geg:ore_fallback` | 旧入口保留 reference，指向 `ore:` 同名字体 |
| `geg_ore` 主题名 | 保留映射到 `ore:ore`，默认主题名为 `ore` |
| `geg:click` 等五个旧音效事件 | 保留注册和声音定义，引用 `ore:` 音频 |

纹理资源包须改为新命名空间，不保留重复 PNG／字体文件。GEG 业务翻译和方块纹理仍在 `assets/geg`；通用数字输入错误位于 `assets/ore/lang`。Inspector 翻译只在开发模块中。

## 验证命令

```powershell
.\gradlew.bat :ore:compileJava :ore-inspector:compileJava
.\gradlew.bat oreTest inspectorTest
.\gradlew.bat build verifyOreModules verifyOreRelease
.\gradlew.bat prepareRunClient genIntellijRuns
```

`oreTest` 汇总通用控件及 GEG 集成回归；`inspectorTest` 保留原来的常用入口。所有 main 方法测试从各模块 `build/tmp/regression` 运行，资源通过绝对路径提供，避免轮转仓库日志。可选真实兼容测试使用 `-PimBlockerJar=<文件>`、`-PcustomSkinLoaderJar=<文件>`；未提供 IMBlocker 时明确跳过这一项。CSL 测试支持 Universal JAR 内嵌的 Common JAR。

Windows 原生 IMM 测试会明确区分环境不支持与测试失败；完整中文候选交互仍需在真实桌面手测。客户端联动夹具必须显式加入 `oreDebugTest` 并传入原有 smoke 开关，只在隔离游戏目录／测试世界运行。正常 IDEA 配置不带这些夹具或开关。

发布文件仍为 `build/libs/geg-<版本>.jar`，玩家只安装这一份 GEG，继续安装原来声明的外部 Mod 依赖。`verifyOreRelease` 检查重映射产物的 Ore 类／资源、两份 Mixin、单 Mod 身份，以及 Inspector、测试、旧 Java 包、第三方库和兼容注解的隔离。

## 本次迁移验证

- `build inspectorTest verifyOreRelease prepareRunClient genIntellijRuns` 通过；80 个 main 回归入口通过，包括真实 IMBlocker／CSL 接口检查。
- Forge 47.4.10 隔离服务端：13 项必需 GameTest 全部通过，覆盖经济、交易、邮件、任务与聊天权限；运行源不包含 Inspector。
- 开发客户端：四类业务目标、Inspector 实时编辑／错误／撤销／拾取／三次 F8 重开通过；另在 Modern UI 3.11.1.6 与 IMBlocker 环境通过同一组联动。
- 双原生窗口及两种 framebuffer 尺寸的像素网格验证通过。
- 最终重映射 GEG JAR 使用本机 Forge 47.3.22 的正式运行库，仅搭配 ModularUI、Curios，在隔离单人世界成功启动并正常保存退出。
- 122 份 PNG、字体、音频和作者源文件迁移前后 SHA-256 一致；旧字体、主题、声音入口另有兼容测试。

客户端测试使用独立目录和本地测试身份，不修改整合包、玩家世界或头像缓存。IDEA 的正常 client/server/gameTest/data 配置已重新生成；没有 smoke 开关或测试源码挂载。Windows JDK 本地网络临时目录的环境绕行只用于测试进程，没有写入项目运行配置。
