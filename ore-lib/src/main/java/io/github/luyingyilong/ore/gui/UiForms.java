package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.form.FieldKey;
import io.github.luyingyilong.ore.control.form.FormField;
import io.github.luyingyilong.ore.control.form.FormFields;
import io.github.luyingyilong.ore.control.form.FormItem;
import io.github.luyingyilong.ore.control.form.validation.Validator;
import io.github.luyingyilong.ore.control.form.validation.Validators;
import io.github.luyingyilong.ore.control.input.*;

import java.math.BigDecimal;
import java.util.*;

final class UiForms {
    private UiForms() {
    }

    static String constructionKey(UiTypeRegistry registry, UiBlueprint.Node node) {
        UiBlueprint.Node field = node.children().stream().filter(n -> !Set.of("validate", "connect", "theme").contains(n.type())).findFirst().orElseThrow();
        String identity = field.get("id", "");
        return List.of(node.require("name"), field.type(), registry.require(field).constructionKey().apply(field), identity.isEmpty() ? UUID.randomUUID().toString() : identity).toString();
    }

    static Control create(UiBlueprint.Node node, List<Control> children, UiBindings bindings) {
        FormField<?> field = field(children.get(0));
        return createTyped(node, field, bindings);
    }

    private static FormField<?> field(Control control) {
        if (control instanceof InputControl input)
            return input.getWidget() instanceof NumberInput ? FormFields.number(input) : FormFields.text(input);
        if (control instanceof Checkbox checkbox) return FormFields.checkbox(checkbox);
        if (control instanceof Switch toggle) return FormFields.switchField(toggle);
        if (control instanceof Slider slider)
            return slider.getRange() ? FormFields.sliderRange(slider) : FormFields.slider(slider);
        if (control instanceof Segmented segmented) return FormFields.segmented(segmented);
        if (control instanceof UiRadioGroup radio) return FormFields.radioGroup(String.class, radio, radio.getGroup());
        if (control instanceof Dropdown<?> dropdown) {
            @SuppressWarnings("unchecked") Dropdown<String> typed = (Dropdown<String>) dropdown;
            return FormFields.dropdown(String.class, typed);
        }
        throw new IllegalArgumentException("Unsupported FormItem field: " + control.getClass().getSimpleName());
    }

    private static <T> FormItem<T> createTyped(UiBlueprint.Node node, FormField<T> field, UiBindings bindings) {
        return new FormItem<>(new FieldKey<>(node.require("name"), field.getType()), UiCodecs.component(node.require("name")), field);
    }

    static <T> List<Validator<? super T>> validators(UiBlueprint.Node node, Class<T> type, UiBindings bindings) {
        List<Validator<? super T>> result = new ArrayList<>();
        for (var rule : node.children())
            if (rule.type().equals("validate")) {
                UiTypeRegistry.attributes(rule, Set.of("rule", "min", "max", "error", "ref"));
                if (!rule.children().isEmpty())
                    throw new UiException(rule.source(), "Validator cannot contain children");
                Validator<? super T> validator = validator(rule, type, bindings);
                if (rule.attributes().containsKey("error")) {
                    var base = validator;
                    validator = value -> base.validate(value).isPresent() ? Optional.of(UiCodecs.component(rule.require("error"))) : Optional.empty();
                }
                result.add(validator);
            }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static <T> Validator<? super T> validator(UiBlueprint.Node node, Class<T> type, UiBindings bindings) {
        String kind = node.get("rule", node.attributes().containsKey("ref") ? "ref" : "required");
        if (kind.equals("required")) return Validators.required();
        if (kind.equals("length") && type == String.class)
            return (Validator<T>) Validators.length(UiBuiltins.integer(node, "min", 0), UiBuiltins.integer(node, "max", Integer.MAX_VALUE));
        if (kind.equals("range") && type == BigDecimal.class)
            return (Validator<T>) Validators.range(decimal(node, "min"), decimal(node, "max"));
        if (kind.equals("ref"))
            return ((UiValidator<T>) bindings.requireValue(node.require("ref"), UiValidator.class)).require(type);
        throw new UiException(node.source(), "Validator " + kind + " cannot validate " + type.getSimpleName());
    }

    private static BigDecimal decimal(UiBlueprint.Node node, String name) {
        return node.attributes().containsKey(name) ? new BigDecimal(node.require(name)) : null;
    }
}
