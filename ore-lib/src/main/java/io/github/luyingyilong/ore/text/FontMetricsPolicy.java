package io.github.luyingyilong.ore.text;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resource-driven corrections, applied once before shaping and caret measurement. */
public record FontMetricsPolicy(MatchMode matchMode, float lineSpacing, float opticalOffsetY,
                                Map<ResourceLocation, Adjustment> faces) {
    public enum MatchMode { AUTO, NATIVE }
    public record Adjustment(float scale, float baselineShift) {
        public Adjustment {
            if (!Float.isFinite(scale) || scale <= 0 || !Float.isFinite(baselineShift))
                throw new IllegalArgumentException("Invalid font face adjustment");
        }
    }
    public static final Adjustment IDENTITY = new Adjustment(1, 0);
    public static final FontMetricsPolicy DEFAULT = new FontMetricsPolicy(MatchMode.AUTO, 1, 0, Map.of());
    public FontMetricsPolicy {
        java.util.Objects.requireNonNull(matchMode);
        if (!Float.isFinite(lineSpacing) || lineSpacing < 1 || !Float.isFinite(opticalOffsetY))
            throw new IllegalArgumentException("line_spacing must be finite and >= 1; optical offset must be finite");
        faces = Map.copyOf(faces);
    }
    public Adjustment getAdjustment(ResourceLocation file) { return faces.getOrDefault(file, IDENTITY); }
    public static FontMetricsPolicy parse(JsonObject json) {
        for (String key : json.keySet()) if (!java.util.Set.of("match_mode", "line_spacing", "optical_offset_y", "faces").contains(key))
            throw new IllegalArgumentException("Unknown font_metrics property: " + key);
        Map<ResourceLocation, Adjustment> faces = new LinkedHashMap<>();
        if (json.has("faces")) for (var entry : json.getAsJsonObject("faces").entrySet()) {
            var value = entry.getValue().getAsJsonObject();
            for (String key : value.keySet()) if (!key.equals("scale") && !key.equals("baseline_shift"))
                throw new IllegalArgumentException("Unknown face adjustment: " + key);
            faces.put(ResourceLocation.parse(entry.getKey()), new Adjustment(number(value, "scale", 1), number(value, "baseline_shift", 0)));
        }
        return new FontMetricsPolicy(json.has("match_mode") ? MatchMode.valueOf(json.get("match_mode").getAsString().toUpperCase(java.util.Locale.ROOT)) : MatchMode.AUTO,
                number(json, "line_spacing", 1), number(json, "optical_offset_y", 0), faces);
    }
    private static float number(JsonObject json, String name, float fallback) {
        if (!json.has(name)) return fallback;
        if (!json.get(name).isJsonPrimitive() || !json.getAsJsonPrimitive(name).isNumber()) throw new IllegalArgumentException(name + ": expected number");
        return json.get(name).getAsFloat();
    }
}
