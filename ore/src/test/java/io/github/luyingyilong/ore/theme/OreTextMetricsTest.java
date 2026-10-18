package io.github.luyingyilong.ore.theme;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import java.nio.file.Path;
import java.util.List;

/** Measures the actual bundled fonts without GL, then checks stable baselines and scaled row geometry. */
public final class OreTextMetricsTest {
    public static void main(String[] args) {
        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(
                new PathPackResources("test", Path.of(System.getProperty("ore.resourceRoot")), false)))) {
            OreTextMetrics.reload(resources);
            for (boolean hero : new boolean[]{false, true}) {
                var profile = OreTextMetrics.family(hero);
                System.out.println((hero ? "Hero " : "Body ") + profile);
                check(profile.advance() >= 10 && profile.origin() >= 1);
                check(profile.inkTop() + profile.origin() >= 0);
                check(profile.inkBottom() + profile.origin() <= profile.advance());
                for (float scale : new float[]{0.75F, 1, 1.5F, 2}) {
                    for (int height : new int[]{18, 20, 22, 40}) for (int lines : new int[]{1, 2, 3}) {
                        float drawY = profile.centeredOrigin(height, lines, scale, true);
                        float center = drawY + profile.inkTop() * scale + profile.opticalHeight(lines, scale) / 2;
                        check(Math.abs(center - height / 2.0F) <= 0.5001F);
                        check(profile.lineHeight(scale) >= profile.advance() * scale);
                    }
                }
            }
        }
    }

    private static void check(boolean value) {
        if (!value) throw new AssertionError("Ore line metrics or optical alignment regression");
    }
}
