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

## 测试与兼容

```powershell
.\gradlew.bat :ore-lib:oreTest :ore-lib:inspectorTest
.\gradlew.bat :ore-lib:check
```

回归从模块 build/tmp 运行，不依赖宿主目录名。`oreTest` 的类路径只有正式控件；`inspectorTest` 额外使用 inspector 输出。可用 `-PimBlockerJar=<路径>` 和 `-PcustomSkinLoaderJar=<路径>` 验证真实兼容 JAR。

ModularUI 3.3.1 的开发测试注册需要 Curios 运行库，正式运行不走该分支；Ore 不链接 Curios API。Modern UI、IMBlocker 和 CustomSkinLoader 保持可选。Windows 原生窗口和输入法按需启用，其他系统保留内嵌窗口。`gradle/legacy-annotations` 仅用于读取 ModularUI 旧注解，不能进入发布包。
