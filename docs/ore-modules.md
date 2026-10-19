# Ore Lib 模块结构与构建

## 单模块、独立输出

根工程负责 GEG 市场、组织、邮件、聊天和服务端业务；唯一 UI 模块是 `:ore-lib`。

- `ore-lib/src/main`：`io.github.luyingyilong.ore` 控件、布局、窗口、字体、IME、头像缓存、无操作调试桥接和属性注解
- `ore-lib/src/inspector`：`io.github.luyingyilong.ore.inspector` 原生调试器、快照、反射适配、属性覆盖和历史
- `ore-lib/src/test`：正式控件回归
- `ore-lib/src/inspectorTest`：Inspector 回归
- 根 `src/clientIntegrationTest`：显式启用的真实客户端业务联动夹具

依赖方向为 GEG → ore-lib main，inspector → main。GEG 的 clientIntegrationTest 可以编译引用 inspector；普通 root test 和正式运行不加载 inspector。两个 Java 包保持原名，不增加 Mod 身份或独立元数据。

构建支持在 `ore-lib/gradle/`，包括环境默认值和仅编译期旧注解。模块内部不引用 GEG 业务源码、翻译或根构建脚本。复制一个 ore-lib 目录即可接入同版本 Forge 项目，详见该目录 README。

## 运行与打包

正常 runClient 挂载 GEG main、ore-lib main 和 ore-lib inspector，并由 clientDevelopment 源码集类路径提供仅客户端使用的 JEI；server、gameTestServer、data 只挂载 GEG main 和 ore-lib main。IDEA 客户端运行前构建 inspectorClasses，详见 [开发客户端说明](development-client.md)。

Inspector 的 `META-INF/services/io.github.luyingyilong.ore.OreClientExtension` 仅在 inspector 输出中，调用 OreClient.initialize 后自动注册 F8。主源码不链接 Inspector 实现。

库标准 JAR 仅包含 main。GEG JAR 同样只合并 main 未重映射输出，根 reobfJar 统一映射，包含 geg.mixins.json 和 ore.mixins.json。不使用 Jar-in-Jar，不嵌入 ModularUI、Curios、Inspector、测试或编译注解。

初始化仍由客户端 GegClient 传入 OreClientOptions；头像缓存保留 geg/avatars。NumberInput 的数量／coin 业务预设由 GEG 的 GegInputs 提供。协议、存档、交易和界面状态逻辑未变。

## 资源

Ore 字体、纹理、主题、UI 音效和通用翻译在 assets/ore；业务资源仍在 assets/geg。旧 geg 字体、geg_ore 主题和旧 UI 音效入口已删除，不添加忽略或迁移处理。默认字体、基线、IME 和可选皮肤兼容保持原行为。

ModularUI 3.3.1 开发模式会加载 Curios 测试物品，开发 runtimeOnly 已配置 Curios；正式运行不走此注册分支，Ore 无 Curios API 引用。

## 验证入口

```powershell
.\gradlew.bat :ore-lib:compileJava :ore-lib:inspectorClasses
.\gradlew.bat oreTest inspectorTest
.\gradlew.bat verifyOreModules verifyOreRelease verifyOrePortability
.\gradlew.bat prepareRunClientCompile genIntellijRuns
```

oreTest 汇总根业务及库的通用回归；inspectorTest 委托库的同名任务。所有 main 回归从隔离 build/tmp 目录执行；可选真实兼容检查使用 imBlockerJar 和 customSkinLoaderJar Gradle 属性。

verifyOreModules 检查 main 与 inspector 的源码和字节码不引用 GEG；verifyOreRelease 检查最终重映射发布包的资源及隔离边界。

verifyOrePortability 将单个模块复制到 build/tmp/ore-lib-portability-host，逻辑名改为 :ui-lib，物理目录改为 libraries/controls。独立宿主 ID 为 ore_sample，不提供 GEG 业务代码、资源、原宿主 Gradle 属性或根脚本，验证正式控件与 Inspector 测试及单 JAR 重映射。夹具源码位于 build-support/ore-portability-host；其 runClient 可执行控件开屏和 F8 原生 Inspector 联动。

正常 IDEA 配置不包含 clientIntegrationTest、smoke 参数或测试专用输入法环境绕行。发布客户端和专用服务端继续只安装 GEG JAR 及声明的外部依赖。

## 合并验证

- 合并后的 main、inspector、inspectorTest 及 GEG 主源码、客户端联动夹具编译通过；普通测试类路径确认无 Inspector 服务
- 改名为 :ui-lib 的独立宿主完成全部 56 项库回归，包含真实 IMBlocker／CSL JAR 检查
- 独立客户端实际显示 Ore 控件、通过 F8 创建并绘制原生 Inspector，输出 ORE_PORTABILITY_CLIENT_PASS 后正常退出
- 独立宿主、库标准 JAR 和最终 GEG 重映射产物均确认不包含 Inspector、开发服务描述或第三方依赖实现
- 标准 IDEA client/server/gameTest/data 配置已重新生成；仅 client 挂载 inspector 输出，旧模块目录已删除
