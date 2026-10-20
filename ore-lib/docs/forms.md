# 类型化表单

字段键、适配器和取值都携带类型。Form 不再接受字符串字段名取值或字符串 Supplier 字段构造器。现有控件不需要修改继承关系。

```java
FieldKey<String> name = new FieldKey<>("name", String.class);
FieldKey<BigDecimal> amount = new FieldKey<>("amount", BigDecimal.class);

Input nameInput = new Input();
NumberInput number = new NumberInput().setDecimalPlaces(2);
Form form = new Form()
        .addItem(new FormItem<>(name, Component.literal("Name"), FormFields.text(nameInput))
                .addValidator(Validators.required()).addValidator(Validators.length(1, 48)))
        .addItem(new FormItem<>(amount, Component.literal("Amount"), FormFields.number(number)));
form.setVisible(true);

form.setInitialValues(FormValues.builder()
        .setValue(name, "").setValue(amount, BigDecimal.ONE).build());

submit.pressed().connect(form, () -> {
    FormValidation result = form.validate();
    if (!result.getValid()) return;
    FormValues values = result.getValues();
    String validName = values.getValue(name);
    BigDecimal validAmount = values.getValue(amount);
    // 调用方继续负责业务请求
});
```

FormFields 提供 text（Input／TextArea）、number、checkbox、switchField、dropdown、segmented、只读字段和 custom 适配器。Segmented 可以返回索引，或通过显式列表映射枚举。组合字段通过 custom 提供类型、控件、取值、静默写入及变化信号；更复杂的解析通过 FormField 构造器接入。只读字段可展示并提交，但不回写。

普通字段首次失焦或提交后显示错误，之后随修改即时复验。Validators 提供必填、Unicode 字符数、数值范围及自定义谓词；每个字段展示第一个错误，提交会校验所有活动字段，返回有序错误并聚焦首个错误。

NumberInput 实时校验只读取精确计算预览，不回填文档。Enter、失焦和表单提交保留原有 commit／钳制规则，计算仍是独立撤销步骤。解析失败没有可提交的旧值。失败结果调用 getValues 会抛出 IllegalStateException。

`setValuesNoSignal` 静默回填，不触发操作信号；`setInitialValues` 和 `captureInitialValues` 建立新的重置基线。`reset` 恢复基线并清除 touched／错误。`getDirty` 按编辑快照比较，包括尚不能解析的数值草稿。隐藏或非活动字段不校验，也不进入提交快照。

字段订阅跟随 FormItem 的 SignalScope，移除、回收与永久销毁仍遵循 Ore 的生命周期规则。业务限制与服务端复核继续属于宿主，Ore 不包含金额或组织规则。

