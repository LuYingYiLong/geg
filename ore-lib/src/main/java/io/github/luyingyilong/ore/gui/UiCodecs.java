package io.github.luyingyilong.ore.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.luyingyilong.ore.control.base.Offset;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.render.Transform2D;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.Set;

final class UiCodecs {
    private UiCodecs() {
    }

    static Component component(String value) {
        return value.startsWith("@") ? Component.translatable(value.substring(1)) : Component.literal(value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object parse(Class<?> type, String value, String name) {
        if (type == String.class) return value;
        if (type == Component.class) return component(value);
        if (type == boolean.class || type == Boolean.class) {
            if (!value.equals("true") && !value.equals("false"))
                throw new IllegalArgumentException("Expected true or false");
            return Boolean.valueOf(value);
        }
        if (type == int.class || type == Integer.class) {
            if (name.equals("sizeFlagsHorizontal") || name.equals("sizeFlagsVertical")) return flags(value);
            if (name.equals("modulate") || name.endsWith("Color")) return ThemeDB.parseColor(value);
            return Integer.valueOf(value);
        }
        if (type == long.class || type == Long.class) return Long.valueOf(value);
        if (type == float.class || type == Float.class) {
            float f = Float.parseFloat(value);
            if (!Float.isFinite(f)) throw new IllegalArgumentException("Non-finite number");
            return f;
        }
        if (type == double.class || type == Double.class) {
            double d = Double.parseDouble(value);
            if (!Double.isFinite(d)) throw new IllegalArgumentException("Non-finite number");
            return d;
        }
        if (type == java.util.UUID.class) return java.util.UUID.fromString(value);
        if (type == net.minecraft.world.item.ItemStack.class) {
            try {
                if (value.stripLeading().startsWith("{"))
                    return net.minecraft.world.item.ItemStack.of(net.minecraft.nbt.TagParser.parseTag(value));
                var id = ResourceLocation.parse(value);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown item " + id));
                return new net.minecraft.world.item.ItemStack(item);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
                throw new IllegalArgumentException("Invalid ItemStack SNBT", invalid);
            }
        }
        if (type == BigDecimal.class) return new BigDecimal(value);
        if (type.isEnum()) return Enum.valueOf((Class) type, value);
        if (type == Theme.class) return ThemeDB.getDefault().getTheme(ResourceLocation.parse(value));
        if (type == io.github.luyingyilong.ore.control.input.Slider.RangeValue.class) {
            String[] p = parts(value, 2);
            return new io.github.luyingyilong.ore.control.input.Slider.RangeValue(Double.parseDouble(p[0]), Double.parseDouble(p[1]));
        }
        if (type == io.github.luyingyilong.ore.control.display.Label.Alignment.class) {
            String[] p = parts(value, 2);
            return new io.github.luyingyilong.ore.control.display.Label.Alignment(
                    io.github.luyingyilong.ore.control.display.Label.Horizontal.valueOf(p[0]), io.github.luyingyilong.ore.control.display.Label.Vertical.valueOf(p[1]));
        }
        if (type == ResourceLocation.class) return ResourceLocation.parse(value);
        if (type == Offset.class) {
            String[] p = parts(value, 2);
            return new Offset(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        if (type == Size.class) {
            String[] p = parts(value, 2);
            return new Size(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }
        if (type == Padding.Insets.class) {
            String[] p = parts(value, 4);
            return new Padding.Insets(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        }
        if (type == Transform2D.class) {
            String[] p = parts(value, 6);
            return new Transform2D(Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]));
        }
        if (type == TextureSource.class) {
            if (value.startsWith("$")) {
                String[] p = value.substring(1).split(":", 2);
                if (p.length != 2) throw new IllegalArgumentException("Expected $Type:item");
                return TextureSource.themed(ThemeDB.getDefault().getKey(p[0], p[1], TextureRegion.class));
            }
            return TextureSource.region((TextureRegion) parse(TextureRegion.class, value, name));
        }
        if (type == TextureRegion.class) {
            JsonObject o = JsonParser.parseString(value).getAsJsonObject();
            for (String key : o.keySet())
                if (!Set.of("location", "imageWidth", "imageHeight", "x", "y", "width", "height").contains(key))
                    throw new IllegalArgumentException("Unknown texture region field: " + key);
            if (!o.has("location") || !o.get("location").isJsonPrimitive() || !o.getAsJsonPrimitive("location").isString())
                throw new IllegalArgumentException("Texture location must be a resource ID string");
            int w = integer(o, "imageWidth", null), h = integer(o, "imageHeight", null);
            return new TextureRegion(ResourceLocation.parse(o.get("location").getAsString()), w, h,
                    integer(o, "x", 0), integer(o, "y", 0), integer(o, "width", w), integer(o, "height", h));
        }
        throw new IllegalArgumentException("No XML converter registered for " + type.getTypeName());
    }

    private static int integer(JsonObject object, String name, Integer fallback) {
        JsonElement value = object.get(name);
        if (value == null && fallback != null) return fallback;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Texture " + name + " must be an integer");
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("Texture " + name + " must be a finite 32-bit integer", invalid);
        }
    }

    private static String[] parts(String value, int count) {
        String[] parts = value.trim().split("\\s*,\\s*", -1);
        if (parts.length != count) throw new IllegalArgumentException("Expected " + count + " comma-separated values");
        return parts;
    }

    private static int flags(String value) {
        int result = 0;
        for (String token : value.split("\\|"))
            result |= switch (token.trim()) {
                case "SIZE_SHRINK_BEGIN" -> SizeFlags.SIZE_SHRINK_BEGIN;
                case "SIZE_FILL" -> SizeFlags.SIZE_FILL;
                case "SIZE_EXPAND" -> SizeFlags.SIZE_EXPAND;
                case "SIZE_EXPAND_FILL" -> SizeFlags.SIZE_EXPAND_FILL;
                case "SIZE_SHRINK_CENTER" -> SizeFlags.SIZE_SHRINK_CENTER;
                case "SIZE_SHRINK_END" -> SizeFlags.SIZE_SHRINK_END;
                default -> Integer.parseInt(token.trim());
            };
        return SizeFlags.validate(result);
    }
}
