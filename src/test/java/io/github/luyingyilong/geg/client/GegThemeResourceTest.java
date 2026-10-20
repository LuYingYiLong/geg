package io.github.luyingyilong.geg.client;

import io.github.luyingyilong.geg.client.theme.GegThemes;
import io.github.luyingyilong.ore.OreClientExtension;
import io.github.luyingyilong.ore.TextureRect;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.LifecycleScheduler;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.*;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Production bootstrap registers host symbols before the same resource reload as Ore. */
public final class GegThemeResourceTest {
    public static void main(String[] args) {
        ThemeDB db = ThemeDB.getDefault();
        List<OreClientExtension> extensions = List.of(new io.github.luyingyilong.geg.client.theme.GegThemeExtension());
        extensions.forEach(extension -> extension.registerThemes(db));
        extensions.forEach(extension -> extension.registerThemes(db));
        db.setDefaultTheme(GegThemes.ID);
        db.reload(new ThemeDB.Resources() {
            public Reader open(ResourceLocation file) throws IOException {
                return new InputStreamReader(resource(file), StandardCharsets.UTF_8);
            }
            public int[] imageSize(ResourceLocation file) throws IOException {
                try(var stream = resource(file)) {
                    var image = javax.imageio.ImageIO.read(stream);
                    return new int[]{image.getWidth(), image.getHeight()};
                }
            }
        });
        check(db.getDiagnostics().isEmpty(), "All Ore and host symbols load: " + db.getDiagnostics());
        check(db.getDefaultTheme().getParent().getId().equals(OreTheme.ID), "Host theme inherits the unmodified Ore theme");
        for(var key : List.of(GegThemes.MAIL_UNREAD, GegThemes.MAIL_READ, GegThemes.MAIL_REWARD_UNREAD,
                GegThemes.MAIL_REWARD_READ, GegThemes.ORGANIZATION_CREATE, GegThemes.ORGANIZATION_JOIN)) {
            TextureRect image = new TextureRect().setTexture(TextureSource.themed(key));
            var region = image.getResolvedTextureRegion();
            check(region.getTexture().getNamespace().equals("geg") && region.getWidth() == 16 && region.getHeight() == 16,
                    "Business resource and intrinsic dimensions belong to GEG: " + key.getId());
            check(image.getThemeResolution(key).sources().stream().anyMatch(source -> source.resource().equals("geg:themes/ui.json")),
                    "Inspector traces the actual host resource");
            image.queueFree();
        }
        for(String file : List.of("email", "email_readed", "gift", "gift_opened", "flag", "org"))
            check(GegThemeResourceTest.class.getResource("/assets/ore/textures/icons/" + file + ".png") == null,
                    "No duplicate business resource remains in Ore");
        Control plain = new Control();
        check(plain.getThemeKeys().stream().noneMatch(key -> key.type().startsWith("geg:")), "Business catalogs do not pollute Control");
        plain.queueFree(); LifecycleScheduler.flush();
        System.out.println("GEG_THEME_RESOURCE_PASS");
    }
    private static InputStream resource(ResourceLocation file) throws IOException {
        InputStream stream = GegThemeResourceTest.class.getResourceAsStream("/assets/" + file.getNamespace() + "/" + file.getPath());
        if(stream == null) throw new FileNotFoundException(file.toString());
        return stream;
    }
    private static void check(boolean condition, String message) { if(!condition) throw new AssertionError(message); }
}
