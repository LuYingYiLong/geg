package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.input.InputControl;
import io.github.luyingyilong.ore.control.input.NumberInput;
import io.github.luyingyilong.ore.control.input.Slider;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Grid;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.control.progress.ProgressBar;
import io.github.luyingyilong.ore.control.progress.TextureProgressBar;
import io.github.luyingyilong.ore.theme.ThemeKey;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class UiConfiguration {
    private UiConfiguration() {
    }

    static Object editor(Control control) {
        return control instanceof InputControl input ? input.getWidget() : control;
    }

    static List<Write> parse(UiLoader loader, UiTypeRegistry.Type type, Control control, UiBlueprint.Node node, UiBindings bindings) {
        List<Write> writes = new ArrayList<>();
        Object editor = editor(control);
        Map<String, UiPropertySchema.Property> properties = UiPropertySchema.getProperties(control.getClass());
        Map<String, UiPropertySchema.Property> editorProperties = editor == control ? Map.of() : UiPropertySchema.getProperties(editor.getClass());
        Set<String> handled = new HashSet<>(Set.of("id"));
        handled.addAll(type.constructionAttributes());
        UiBuiltinProperties.parse(loader, control, node, bindings, writes, handled);
        if (type.structure() == UiTypeRegistry.Structure.DROPDOWN) handled.add("maxVisibleItems");
        if (control instanceof Grid<?> grid && (node.attributes().containsKey("minCardWidth") || node.attributes().containsKey("maxColumns") || node.attributes().containsKey("gap"))) {
            handled.addAll(Set.of("minCardWidth", "maxColumns", "gap"));
            Accessor a = new Accessor(c -> List.of(((Grid<?>) c).getMinCardWidth(), ((Grid<?>) c).getMaxColumns(), ((Grid<?>) c).getGap()),
                    (c, v) -> {
                        List<?> tuple = (List<?>) v;
                        ((Grid<?>) c).setColumns((int) tuple.get(0), (int) tuple.get(1), (int) tuple.get(2));
                    });
            List<Integer> values = List.of(Integer.parseInt(node.get("minCardWidth", Integer.toString(grid.getMinCardWidth()))),
                    Integer.parseInt(node.get("maxColumns", Integer.toString(grid.getMaxColumns()))), Integer.parseInt(node.get("gap", Integer.toString(grid.getGap()))));
            if (values.get(0) < 1 || values.get(1) < 1 || values.get(2) < 0)
                throw new UiException(node.source(), "Invalid grid columns or gap");
            writes.add(new Write("columns", values, a.read.apply(control), false, a));
        }
        // Coupled bounds are committed as one operation, regardless of XML attribute order.
        if (editor instanceof NumberInput && (node.attributes().containsKey("decimalPlaces") || node.attributes().containsKey("minimumValue") || node.attributes().containsKey("maximumValue"))) {
            handled.add("decimalPlaces");
            handled.add("minimumValue");
            handled.add("maximumValue");
            NumberInput number = (NumberInput) editor;
            List<Object> values = Arrays.asList(Integer.parseInt(node.get("decimalPlaces", "0")), decimal(node.get("minimumValue", "")), decimal(node.get("maximumValue", "")));
            Accessor a = new Accessor(c -> {
                NumberInput n = (NumberInput) editor(c);
                return Arrays.asList(n.getDecimalPlaces(), n.getMinimumValue(), n.getMaximumValue());
            },
                    (c, v) -> {
                        List<?> tuple = (List<?>) v;
                        ((NumberInput) editor(c)).setLimits((int) tuple.get(0), (BigDecimal) tuple.get(1), (BigDecimal) tuple.get(2));
                    });
            writes.add(new Write("limits", values, a.read.apply(control), false, a));
        }
        if (control instanceof Scroll && (node.attributes().containsKey("scrollX") || node.attributes().containsKey("scrollY"))) {
            handled.add("scrollX");
            handled.add("scrollY");
            Accessor a = new Accessor(c -> new Offset(((Scroll) c).getScrollX(), ((Scroll) c).getScrollY()),
                    (c, value) -> {
                        Offset position = (Offset) value;
                        ((Scroll) c).setScroll(position.x(), position.y());
                    });
            Offset value = new Offset(Integer.parseInt(node.get("scrollX", "0")), Integer.parseInt(node.get("scrollY", "0")));
            if (value.x() < 0 || value.y() < 0) throw new UiException(node.source(), "Negative scroll offset");
            writes.add(new Write("scroll", value, a.read.apply(control), true, a));
        }
        if ((control instanceof ProgressBar || control instanceof TextureProgressBar || control instanceof Slider)
                && (node.attributes().containsKey("min") || node.attributes().containsKey("max"))) {
            handled.add("min");
            handled.add("max");
            double low = control instanceof ProgressBar p ? p.getMin() : control instanceof Slider slider ? slider.getMin() : ((TextureProgressBar) control).getMin();
            double high = control instanceof ProgressBar p ? p.getMax() : control instanceof Slider slider ? slider.getMax() : ((TextureProgressBar) control).getMax();
            Accessor a = new Accessor(c -> c instanceof ProgressBar p ? List.of(p.getMin(), p.getMax()) : c instanceof Slider slider ? List.of(slider.getMin(), slider.getMax()) : List.of(((TextureProgressBar) c).getMin(), ((TextureProgressBar) c).getMax()),
                    (c, value) -> {
                        List<?> pair = (List<?>) value;
                        double min = (double) pair.get(0), max = (double) pair.get(1);
                        if (c instanceof ProgressBar p) p.setLimitsNoSignal(min, max);
                        else if (c instanceof Slider slider) slider.setLimitsNoSignal(min, max);
                        else ((TextureProgressBar) c).setLimitsNoSignal(min, max);
                    });
            writes.add(new Write("limits", List.of(Double.parseDouble(node.get("min", Double.toString(low))), Double.parseDouble(node.get("max", Double.toString(high)))), a.read.apply(control), false, a));
        }
        for (String key : List.of("minimum", "preferred"))
            if (node.attributes().containsKey(key)) {
                handled.add(key);
                Accessor a = new Accessor(c -> key.equals("minimum") ? c.getMinimumSize() : c.getPreferredSize(), (c, v) -> {
                    Size size = (Size) v;
                    if (key.equals("minimum")) c.setMinimum(size);
                    else c.setPreferred(size.width(), size.height());
                });
                Size size = (Size) loader.registry().convert(Size.class, node.require(key), key);
                if (size.width() < 0 || size.height() < 0) throw new UiException(node.source(), "Negative " + key);
                writes.add(new Write(key, size, a.read.apply(control), false, a));
            }
        for (var attribute : node.attributes().entrySet()) {
            String name = attribute.getKey();
            if (handled.contains(name)) continue;
            try {
                UiPropertySchema.Property property = editorProperties.getOrDefault(name, properties.get(name));
                if (property == null) throw new IllegalArgumentException("Unknown UI property " + name);
                boolean nativeEditor = editorProperties.containsKey(name);
                Class<?> valueType = property.getValueType();
                if (valueType == Object.class && type.payloads().containsKey("selectionChanged"))
                    valueType = type.payloads().get("selectionChanged");
                Object value = attribute.getValue().startsWith("ref:")
                        ? bindings.requireValue(attribute.getValue().substring(4), valueType)
                        : loader.registry().convert(valueType, attribute.getValue(), name);
                Accessor a = new Accessor(c -> property.get(nativeEditor ? editor(c) : c), (c, v) -> property.set(nativeEditor ? editor(c) : c, v));
                if (control instanceof Box && name.equals("lineGap"))
                    a = new Accessor(c -> ((Box) c).getLineGapOverride(),
                            (c, v) -> {
                                if (v == null) ((Box) c).clearLineGap();
                                else ((Box) c).setLineGap((int) v);
                            });
                Object initialDefault;
                try {
                    initialDefault = a.read.apply(control);
                } catch (IllegalStateException mode) {
                    if (!property.initialState() || !(control instanceof Slider)) throw mode;
                    initialDefault = null;
                }
                writes.add(new Write(name, value, initialDefault, property.initialState(), a));
            } catch (RuntimeException error) {
                throw new UiException(node.source(), "Property " + name + ": " + error.getMessage(), error);
            }
        }
        Set<String> themeKeys = new HashSet<>();
        for (var child : node.children())
            if (child.type().equals("theme")) {
                UiTypeRegistry.attributes(child, Set.of("key", "value"));
                if (!child.children().isEmpty())
                    throw new UiException(child.source(), "Theme override cannot have children");
                String id = child.require("key");
                if (!themeKeys.add(id)) throw new UiException(child.source(), "Duplicate theme override " + id);
                try {
                    ThemeKey<?> declaration = loader.themeKey(id);
                    Object value = loader.themeValue(declaration, child.require("value"));
                    Accessor a = new Accessor(c -> c.getThemeOverride(declaration), (c, v) -> setTheme(c, declaration, v));
                    writes.add(new Write("theme:" + id, value, a.read.apply(control), false, a));
                } catch (RuntimeException error) {
                    throw new UiException(child.source(), "Theme " + id + ": " + error.getMessage(), error);
                }
            }
        // Precision is configured before optional numeric limits, and values after all configuration.
        writes.sort(Comparator.comparingInt(UiConfiguration::order).thenComparing(Write::key));
        return List.copyOf(writes);
    }

    private static int order(Write write) {
        if (write.initial()) return 8;
        if (write.key().equals("limits") || write.key().equals("columns")) return 0;
        if (write.key().equals("range") || write.key().equals("step")) return 1;
        if (Set.of("layout", "orientation", "size", "rows").contains(write.key())) return 2;
        if (Set.of("minimum", "preferred").contains(write.key())) return 5;
        if (write.key().startsWith("sizeFlags")) return 6;
        if (write.key().equals("renderTransform")) return 7;
        return 3;
    }

    private static BigDecimal decimal(String text) {
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setTheme(Control control, ThemeKey key, Object value) {
        if (value == null) control.removeThemeOverride(key);
        else control.setThemeOverride(key, value);
    }

    record Accessor(Function<Control, Object> read, BiConsumer<Control, Object> write) {
    }

    record Write(String key, Object value, Object defaultValue, boolean initial, Accessor accessor) {
        void apply(Control target) {
            accessor.write.accept(target, value);
        }

        Object read(Control target) {
            return io.github.luyingyilong.ore.property.PropertyAccess.readBase(() -> accessor.read.apply(target));
        }

        void restore(Control target) {
            accessor.write.accept(target, defaultValue);
        }
    }
}
