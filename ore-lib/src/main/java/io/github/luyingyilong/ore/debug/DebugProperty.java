package io.github.luyingyilong.ore.debug;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.theme.ThemeKey;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An explicit allowlisted property; it never writes application fields.
 */
public record DebugProperty(Control target, String key, String group, String kind,
                            List<String> options, Supplier<?> read, double min, double max,
                            Consumer<Object> validate) {
    public DebugProperty(Control target, String key, String group, String kind,
                         List<String> options, Supplier<?> read, double min, double max) {
        this(target, key, group, kind, options, read, min, max, value -> {
        });
    }

    public static DebugProperty number(Control c, String key, String group, Supplier<?> read, double min, double max) {
        return new DebugProperty(c, key, group, "number", List.of(), read, min, max);
    }

    public static <T extends Number> DebugProperty themeNumber(Control c, ThemeKey<T> key, String group, double min, double max) {
        return new DebugProperty(c, "theme:" + key.getId(), group, "number", List.of(), () -> c.getThemeItem(key), min, max,
                value -> ThemeDB.getDefault().validateValue(key, value));
    }

    public static DebugProperty themeColor(Control c, ThemeKey<Integer> key, String group) {
        if (key.kind() != ThemeKey.Kind.COLOR) throw new IllegalArgumentException("Expected a color theme item");
        return new DebugProperty(c, "theme:" + key.getId(), group, "color", List.of(), () -> c.getThemeItem(key),
                Integer.MIN_VALUE, 0xFFFFFFFFL, value -> ThemeDB.getDefault().validateValue(key, value));
    }

    public static DebugProperty bool(Control c, String key, String group, Supplier<?> read) {
        return new DebugProperty(c, key, group, "boolean", List.of(), read, 0, 1);
    }

    public static DebugProperty text(Control c, String key, String group, Supplier<?> read) {
        return new DebugProperty(c, key, group, "text", List.of(), read, 0, 2048);
    }

    public static DebugProperty choice(Control c, String key, String group, Supplier<? extends Enum<?>> read) {
        Enum<?> value = read.get();
        return new DebugProperty(c, key, group, "enum", java.util.Arrays.stream(value.getDeclaringClass().getEnumConstants())
                .map(Enum::name).toList(), read, 0, 0);
    }

    public Object parse(String input) {
        Object value = parseValue(input);
        validate.accept(value);
        return value;
    }

    private Object parseValue(String input) {
        Object base = read.get();
        if (kind.equals("boolean")) {
            if (!input.equals("true") && !input.equals("false"))
                throw new IllegalArgumentException("Expected true or false");
            return Boolean.parseBoolean(input);
        }
        if (kind.equals("text")) {
            if (input.length() > max) throw new IllegalArgumentException("Text is too long");
            return base instanceof net.minecraft.network.chat.Component ? net.minecraft.network.chat.Component.literal(input) : input;
        }
        if (kind.equals("enum")) {
            for (Object item : ((Enum<?>) base).getDeclaringClass().getEnumConstants())
                if (((Enum<?>) item).name().equals(input)) return item;
            throw new IllegalArgumentException("Unknown option");
        }
        java.math.BigDecimal value;
        if (input.startsWith("#")) {
            if (!isPackedColor() || (input.length() != 7 && input.length() != 9))
                throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB");
            long argb = Long.parseUnsignedLong(input.substring(1), 16);
            if (input.length() == 7) argb |= 0xFF000000L;
            value = java.math.BigDecimal.valueOf(argb);
        } else value = new java.math.BigDecimal(input);
        if (value.compareTo(java.math.BigDecimal.valueOf(min)) < 0 || value.compareTo(java.math.BigDecimal.valueOf(max)) > 0)
            throw new IllegalArgumentException("Value outside [" + min + ", " + max + "]");
        if (base instanceof Float) {
            float result = value.floatValue();
            if (!Float.isFinite(result)) throw new IllegalArgumentException("Float overflow");
            return result;
        }
        if (base instanceof Double) {
            double result = value.doubleValue();
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Double overflow");
            return result;
        }
        try {
            if (base instanceof Long) return value.longValueExact();
            int result;
            if (isPackedColor() && value.signum() >= 0 && value.compareTo(java.math.BigDecimal.valueOf(0xFFFFFFFFL)) <= 0)
                result = (int) value.longValueExact();
            else result = value.intValueExact();
            if (key.startsWith("sizeFlags") && !key.equals("sizeFlagsStretchRatio")) SizeFlags.validate(result);
            return result;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Expected an integer within the property's numeric type", ex);
        }
    }

    private boolean isPackedColor() {
        return kind.equals("color") || key.equals("modulate");
    }
}
