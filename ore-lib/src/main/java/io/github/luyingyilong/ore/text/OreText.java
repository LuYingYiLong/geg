package io.github.luyingyilong.ore.text;

import io.github.luyingyilong.ore.OreClient;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.util.Locale;

/** Client text service. Reload publishes a complete generation; documents are never reset. */
public final class OreText {
    public enum BackendMode { AUTO, BUILTIN, MODERN_UI }
    private static TextLayoutEngine engine = new TextLayoutEngine(new FontRepository(null, 0), TextBackend.builtin());
    private static BackendMode mode = BackendMode.AUTO;
    private OreText() { }
    public static TextLayoutEngine getEngine() { return engine; }
    public static BackendMode getBackendMode() { return mode; }
    public static void setBackendMode(BackendMode value) {
        mode = java.util.Objects.requireNonNull(value);
        engine = new TextLayoutEngine(engine.getFonts(), TextBackends.select(mode));
        TextRenderer.invalidate();
        io.github.luyingyilong.ore.theme.OreFonts.resourcesReloaded();
    }
    public static void reload(ResourceManager resources) {
        FontRepository next = new FontRepository(resources, engine.getFonts().getGeneration() + 1);
        next.getFamily(OreClient.getOptions().bodyFont()); next.getFamily(OreClient.getOptions().heroFont());
        engine = new TextLayoutEngine(next, TextBackends.select(mode));
        TextRenderer.invalidate();
    }
    public static TextLayout layout(Component text, ResourceLocation font, float size, float width,
                                    TextLayoutEngine.Wrap wrap, int maxLines, boolean ellipsis,
                                    FontMetricsPolicy policy, Font bootstrapFont) {
        var request = new TextLayoutEngine.Request(StyledText.of(text, font), font, size, Math.max(0, width), wrap,
                maxLines, ellipsis, policy, getLocale());
        try { return engine.layout(request, bootstrapFont); }
        catch (TextBackend.Unavailable error) {
            backendFailed(engine.getBackend(), error);
            return engine.layout(request, bootstrapFont);
        }
    }
    static void backendFailed(TextBackend backend, TextBackend.Unavailable error) {
        if (engine.getBackend() != backend || backend == TextBackend.builtin()) return;
        System.getLogger(OreText.class.getName()).log(System.Logger.Level.WARNING,
                "Ore text backend failed; new layouts will use the built-in backend", error);
        engine = new TextLayoutEngine(engine.getFonts(), TextBackend.builtin());
        TextRenderer.invalidate();
        io.github.luyingyilong.ore.theme.OreFonts.resourcesReloaded();
    }
    public static TextLayout layout(Control owner, Component text, Font font, float width, TextLayoutEngine.Wrap wrap) {
        return layout(text, owner.getThemeItem(OreTheme.TEXT_FONT), owner.getThemeItem(OreTheme.FONT_SIZE), width,
                wrap, Integer.MAX_VALUE, false, owner.getThemeItem(OreTheme.FONT_METRICS), font);
    }
    private static Locale getLocale() {
        var client = net.minecraft.client.Minecraft.getInstance();
        return client == null || client.getLanguageManager() == null ? Locale.ROOT
                : Locale.forLanguageTag(client.getLanguageManager().getSelected().replace('_', '-'));
    }
    public static FontRepository.Metrics metrics(ResourceLocation font, float size, FontMetricsPolicy policy) {
        return engine.getFonts().getMetrics(font, size, policy);
    }
    public static FontRepository.Metrics bodyMetrics(float scale) {
        return metrics(OreClient.getOptions().bodyFont(), 8 * scale, FontMetricsPolicy.DEFAULT);
    }
}
