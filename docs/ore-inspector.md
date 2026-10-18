# Ore Inspector

重新加载 Gradle，运行开发客户端 `runClient`，按 **F8** 打开独立的 Ore 调试窗口；再次按 F8 聚焦已有窗口。快捷键可以重新绑定。仅开发客户端启用，第一版原生窗口支持 Windows。

## 使用

- 保持市场、组织、邮件或聊天在 Minecraft 主窗口打开；Inspector 不替换主窗口的 Screen。目标下拉框可以切换已发现的窗口，未绘制的目标标为 inactive。
- 左侧是支持搜索、展开和键盘导航的 Tree；长名称和深层节点可水平滚动，中间分隔条可以拖动。树只实例化视口及缓冲范围内的行。
- 右侧按布局、外观、文字和状态分组。字段显示基础值、有效值及覆盖标记；所有编辑在有效时实时应用，无须提交。无效输入保留原文和上一次有效状态，在字段下显示错误；中文预编辑确认后才应用。
- “拾取”在目标窗口中高亮，左键选定、Esc 取消；业务按下和释放都被拦截。Inspector 自身不参与检查或拾取。
- 保留节点／全部恢复按钮及最近 100 步撤销／重做，移除各字段的应用／恢复按钮；后端仍支持单项恢复。应用代码更新基础值后，恢复采用最新基础值。节点移除、换绑或根会话关闭后旧编辑失效。
- Label 文本是临时预览，原始翻译 Component 保留。颜色支持有符号 ARGB 整数、`#RRGGBB`、`#AARRGGBB`。输入值、业务状态和内部诊断节点只读。
- 布局辅助线：青色实际矩形、金色分配空间、绿色最小尺寸、紫色裁剪、橙色 Padding。文字辅助线显示真实行框、基线、绘制原点、字体族光学范围、光标和选区。
- 辅助线绘制在目标窗口中；默认仅显示选中节点。字体回退链不是每个字符实际字形来源的断言。
- 关闭 Inspector 或断开世界时停止本次调试并清理覆盖。原生窗口关闭后恢复原先的失焦暂停设置。调试修改不写入源码、主题或世界存档。

## Ore Tree

Tree 的展开按钮使用 `arrow_right_light`／`arrow_down_light` 的 7×7 图标，叶节点保留对齐空间但不显示箭头。滚动由外层 Scroll 管理，长名称完整测量，行实例仍按视口复用。

| 属性／接口 | 行为与默认值 |
| --- | --- |
| `rowHeight(int)`／`indent(int)` | 行高 22px（最小 18px），每层缩进 10px |
| `blockNode(boolean)` | 默认 true，节点按钮占满行；false 时只占名称和图标所需宽度 |
| `showSwitcher(boolean)` | 默认 true；false 时同时移除箭头列的占位 |
| `switcherIcon((item, expanded) -> Icon)` | 自定义展开／折叠图标 |
| `showIcon(boolean)` | 默认 false；显示 Item 的可选图标 |
| `disabled(boolean)`／`selectable(boolean)` | 默认 false／true；禁用整树交互，或仅禁止用户选中节点 |
| `defaultExpandAll(boolean)` | 默认 true，保持旧行为；决定后续新节点的初始展开状态 |
| `expandedKeys(Collection)`／`expandedKeys()` | 设置／读取展开的分支键集合；程序更新不触发回调 |
| `expanded(key, boolean)`／`expandAll(boolean)` | 控制单个分支或全部分支 |
| `onExpand(event)` | 用户展开／折叠时返回 Item、展开状态和不可变展开键快照 |
| `selected(key)`／`onSelect(value)` | 程序设置选中项不触发回调；用户选择时回传节点值 |

四参数 `Item(key, parent, text, value)` 保持兼容；完整构造支持 `disabled`、`selectable`、`Button.Icon`。禁用节点不响应选择／展开；不可选择的分支仍可展开和通过键盘遍历。方向键跳过禁用节点：右键展开或进入子节点，左键折叠或返回父节点，上下键遍历。搜索临时展开匹配路径，清除搜索后恢复先前展开状态。数据拒绝重复键、缺失父节点和环。

```java
Tree<String> tree = new Tree<String>()
        .rowHeight(22).indent(12).blockNode(true)
        .defaultExpandAll(false).showIcon(true)
        .onSelect(key -> selectObject(key))
        .onExpand(event -> expandedChanged(event.item().key(), event.expanded()));
tree.items(nodes);
Scroll scroll = new Scroll().content(tree);
```

## 属性接入

普通属性在 public getter 标记 `@Inspectable`。`InspectorProperties` 缓存每个 Class 的访问器，通过反射配对 public setter；不读取字段、不使用 `setAccessible`。支持 boolean、int、long、float、double、枚举、String 和 Component。

```java
@Inspectable(group = "Text", min = 0.0001, max = 256)
public float fontSize() {
    return DebugBridge.value(this, "fontSize", fontSize);
}

public Label fontSize(float value) {
    if (!(value > 0.0F) || !Float.isFinite(value))
        throw new IllegalArgumentException("Invalid font size");
    // 在校验之后、任何字段修改和副作用之前截住 Inspector 预检
    if (DebugBridge.previewWrite(this, "fontSize", value)) return this;
    fontSize = value;
    requestLayout();
    return this;
}
```

可使用注解的 `key`、`setter` 设置不同名称。Inspector 调用真实 setter 预检，在 `previewWrite` 截止写入后只更新覆盖层。不要给缺少该拦截点的 setter 添加注解。正常业务 setter 不受影响。

复合尺寸、Offset、自由定位、Padding、Grid 列参数、Scroll 轴设置及内部 Label 转发保留显式 `debugProperties` 适配器；相同 target＋key 的显式描述优先。两种描述共享校验、覆盖、历史和生命周期规则。

## 结构与验证

`src/main/.../client/ui/window` 是发布版客户端窗口基础设施；`client/ui/debug` 是无操作桥接和注解。`src/oreDebug` 包含原生 Inspector、类型化快照／命令和反射适配器；`src/oreDebugTest` 是测试夹具。没有 HTTP 服务、端口、网页或 WebSocket，专用 Netty 依赖已移除。

- `gradlew inspectorTest`：注解发现、真实 setter 预检、非法值、覆盖和历史回归。
- `WindowLayoutTest`：窗口坐标／DPI、输入隔离、SplitContainer 拖动和 Tree 虚拟化。
- `NativeWindowClientTest`：显式 `geg.native.smoke=true` 的隔离客户端测试，覆盖双窗口、字符／多行输入、缩放、最小化、异常恢复和关闭。
- `NativeInspectorClientTest`：显式 `geg.native.inspector.smoke=true` 的隔离客户端测试，覆盖原生前端、数百节点、属性编辑、拾取及四类业务目标。

客户端夹具只有显式加入 `oreDebugTest` 输出时才运行，不应在普通游戏世界开启。发布 JAR 不包含 Inspector、测试或调试语言资源。
