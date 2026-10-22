# IDEA 开发客户端

执行 `gradlew genIntellijRuns` 后在 IDEA 刷新 Gradle 项目，使用标准 `runClient`。
其类路径模块为 `geg.clientDevelopment`，包含主工程、Ore 和开发 Inspector。

## XML 源码热重载

标准 `runClient` 已由 Gradle 自动配置 `ore.gui.sourceRoots`，包含 Ore 的
`src/main/resources` 和 `src/inspector/resources`。IDEA 和 Gradle 客户端使用同一配置，
重新生成运行配置不会丢失监听参数；服务端、GameTest 和数据生成不启用监听。

更新工程后执行一次 `gradlew prepareRunClientCompile genIntellijRuns`，刷新 IDEA 的 Gradle 项目并重启客户端。
按 F9 选择 **XML UI**，直接修改并保存
`ore-lib/src/inspector/resources/assets/ore_gallery/gui/components.xml`。
修改标题、gap、placeholder 或主题覆盖后，当前界面自动更新，无需构建、重新选择示例或按 F3+T。

示例顶部显示源码路径、监听状态、文档版本和重载错误。正常保存防抖约 300ms；
文件事件遗漏时通过每秒一次的内容校验兜底。相同内容不重复重载，IDEA 替换式保存和目录重建也支持。
XML 错误保留上次有效界面，修正保存后自动恢复。IME 预编辑期间延后提交。

相同 ID 和类型的输入框保留草稿、选区、撤销记录，Scroll 保留可用偏移；
修改 XML 的初始 text 或 scrollY 不会覆盖当前状态。验证热重载时应修改配置属性，保持示例打开。
重新切换示例会创建新文档，不属于同一文档的状态保留。

JEI 固定使用 Forge 1.20.1 的 `15.20.0.105`，与 Ore 当前测试的 API 版本一致。
这份 JAR 的必需依赖只有 Minecraft 和 Forge，工程已经提供；不需要另外安装
Architectury 或 Cloth Config。Gradle 负责下载和重映射，无需放入 `run/mods`。
不要再手动复制另一份 JEI，以免重复加载。

JEI 只进入 `clientDevelopment` 的运行类路径；普通服务端、GameTest、数据生成、
发布依赖和最终 JAR 均不包含它。`gradlew verifyClientDevelopment` 检查此边界。
不能只往 `legacyClassPath.file` 添加 JAR：这会让部分类型可见，但不足以完成
Forge 的模组发现。客户端使用真实 source set 类路径加载 JEI。

## 可选的 IMBlocker 开发验证

整合包中的 `IMBlocker-5.6.2.1-forge+1.17-1.20.4.jar` 同时包含两种可选注入：
`setGuiScale(D)V` 与 `setGuiScale(I)V`。Minecraft 1.20.1 使用前者。

Mixin 0.8.5 的 `InjectionInfo.findRootTargets()` 在
`mixin.env.remapRefMap=true` 时会对找不到的方法再次进行忽略 descriptor 的搜索，
导致 `int` 回调匹配到 `double` 方法，产生 WindowMixin 参数错误。
`allowPermissiveMatch=false` 不能禁用这段方法级回退。
直接关闭 refmap 又会遇到 IMBlocker 注解中未转换的 SRG 名称，例如
`IMEOverlayRendererV2` 的 `m_109093_`。

另一个独立问题在 Ore 的 IMBlocker 适配器中：对预编辑 Overlay 调用
`Class.getMethod` 会解析该类的其他方法签名，其中包含可选的
`imgui.moulberry92.ImDrawList`。没有 Axiom 时这会抛出 `NoClassDefFoundError`，
让 Ore 回退至 GLFW。适配器现用 `MethodHandles` 精确查找需要的方法，
不要求安装 Axiom 或 ImGui。`ImBlockerOverlayAccessTest` 在缺失可选类型的
类加载器中复现旧问题，并验证清理接口及必需签名校验。

可使用以下可选适配测试该 JAR：

```powershell
.\gradlew.bat '-PimBlockerDevelopmentJar=C:/路径/IMBlocker-5.6.2.1-forge+1.17-1.20.4.jar' genIntellijRuns
```

随后刷新 IDEA 的 Gradle 项目。再次构建或启动时也需保留该 Gradle 项目属性，
例如在本机的 Gradle 项目属性中配置路径；不要将机器专属路径提交到仓库。
默认开发客户端只安装 JEI，不自动安装 IMBlocker。

适配仅在 `build/client-development` 中生成副本：先通过 ForgeGradle 正常重映射代码，
再按同一份 `createSrgToMcp` 映射转换注解、字符串和 refmap 中的 SRG 引用，
保持运行时 `remapRefMap=false`。不删除注入，不修改 `int`／`double` 参数，
不改动已安装的原 JAR，也不向 Ore 添加特殊的 WindowMixin。
`verifyDevelopmentImBlocker` 校验全部方法签名、元数据、资源以及原文件完整性。
生成的副本仅适合当前开发映射环境，不得放进正式整合包或服务器。

恢复默认配置时去掉该项目属性，重新执行 `genIntellijRuns` 并刷新 Gradle。

参考：

- [JEI ForgeGradle 接入说明](https://github.com/mezz/JustEnoughItems/wiki/Getting-Started-%5BMinecraft-1.18.2-to-1.20.1%5D)
- [Mixin 0.8.5 InjectionInfo](https://github.com/SpongePowered/Mixin/blob/0.8.5/src/main/java/org/spongepowered/asm/mixin/injection/struct/InjectionInfo.java)
