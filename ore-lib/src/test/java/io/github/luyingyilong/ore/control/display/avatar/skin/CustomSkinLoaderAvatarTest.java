package io.github.luyingyilong.ore.control.display.avatar.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Local/Base64 CSL cache import and restart recovery, optionally against the installed CSL common JAR. */
public final class CustomSkinLoaderAvatarTest {
    public static final class LocalTexture extends MinecraftProfileTexture {
        private final File cached;
        public LocalTexture(Path cached) { super("unused", Map.of()); this.cached = cached.toFile(); }
        @Override public String getUrl() { return null; }
        public File getCacheFile() { return cached; }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of("build/tmp"), "ore-csl-test-");
        Path providerImage = root.resolve("provider/skin.png");
        Files.createDirectories(providerImage.getParent());
        writeSkin(providerImage, 128, 128);
        AvatarSkinSource source = AvatarSkinSource.from(new LocalTexture(providerImage));
        check(source.url() == null && source.image().equals(providerImage) && source.valid(),
                "CSL's null-URL local/Base64 contract is accepted through getCacheFile");
        AvatarSkinSource vanilla = AvatarSkinSource.from(new MinecraftProfileTexture("https://example.test/skin.png", Map.of()));
        check(vanilla.image() == null && vanilla.valid(), "Vanilla HTTP textures need no optional methods");

        GameProfile offline = new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:CSLMember".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "CSLMember");
        List<String> keys = new ArrayList<>();
        List<ResourceLocation> shown = new ArrayList<>();
        Path ownCache = root.resolve("host-existing-cache/avatars");
        AvatarSkinCache.TextureLoader loader = (profile, key, image, ready) -> {
            try {
                check(AvatarSkinSource.storedImage(key), "Local provider skins are never sent to the HTTP downloader");
                BufferedImage png = ImageIO.read(image.toFile());
                check(png.getWidth() == 64 && png.getHeight() == 64, "HD avatar texture normalized to 64x64");
                check(png.getRGB(8, 8) == 0xFF224466, "Head pixels and base opacity retained");
                check(png.getRGB(40, 8) == 0x80123456, "Semi-transparent outer head layer retained");
                keys.add(key);
                ready.accept(ResourceLocation.fromNamespaceAndPath("ore", "avatars/" + image.getFileName()));
            } catch (Exception failure) { throw new AssertionError(failure); }
        };
        AvatarSkinCache first = new AvatarSkinCache(ownCache, Runnable::run,
                profile -> { throw new AssertionError("Online CSL import must not query Mojang"); }, loader);
        first.loadKnown(offline, source, shown::add);
        check(shown.size() == 1 && Files.exists(ownCache.resolve(offline.getId() + ".json")),
                "Persist a successful local custom skin under the server's offline UUID");
        String saved = Files.readString(ownCache.resolve(offline.getId() + ".json"));
        check(!saved.contains(providerImage.toString()) && !saved.contains("file:"), "Index does not depend on the provider's file path");
        Files.delete(providerImage);
        new AvatarSkinCache(ownCache, Runnable::run,
                profile -> { throw new IllegalStateException("offline, CSL unavailable after restart"); }, loader)
                .load(offline, shown::add);
        check(shown.size() == 2 && shown.get(0).equals(shown.get(1)) && keys.get(0).equals(keys.get(1)),
                "A fresh cache instance restores the same avatar without CSL or its original image");

        Files.writeString(providerImage, "not a PNG");
        first.loadKnown(offline, source, shown::add);
        check(Files.readString(ownCache.resolve(offline.getId() + ".json")).equals(saved),
                "A broken new provider image preserves the last successful avatar");
        BufferedImage invalid = new BufferedImage(63, 64, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(invalid, "PNG", providerImage.toFile());
        try { AvatarSkinImages.read(providerImage); throw new AssertionError("Invalid skin dimensions accepted"); }
        catch (java.io.IOException expected) { /* Keep the old cache. */ }
        writeSkin(providerImage, 64, 32);
        BufferedImage legacy = ImageIO.read(new ByteArrayInputStream(AvatarSkinImages.read(providerImage)));
        check(legacy.getWidth() == 64 && legacy.getHeight() == 64 && legacy.getRGB(8, 8) == 0xFF224466,
                "Legacy 64x32 skin head maps to the same face coordinates");
        check(legacy.getRGB(40, 8) == 0x80123456, "Legacy skins with intentional hat transparency retain it");

        if (args.length > 0) checkInstalledJar(Path.of(args[0]), root.resolve("real-csl"));
    }

    private static void checkInstalledJar(Path jar, Path root) throws Exception {
        Path common = root.resolve("CustomSkinLoader-Common.jar");
        Files.createDirectories(root);
        // Universal distributions put the shared API in a nested jar; Forge extracts it at runtime.
        try (var archive = new java.util.zip.ZipFile(jar.toFile())) {
            var entry = archive.getEntry("META-INF/jar-jar/CustomSkinLoader-Common.jar");
            if (entry != null) try (var input = archive.getInputStream(entry)) { Files.copy(input, common); }
        }
        var urls = Files.exists(common) ? new java.net.URL[]{jar.toUri().toURL(), common.toUri().toURL()}
                : new java.net.URL[]{jar.toUri().toURL()};
        try (URLClassLoader classes = new URLClassLoader(urls, CustomSkinLoaderAvatarTest.class.getClassLoader())) {
            // HTTP parsing in the actual CSL 15.1 class is independent of its Minecraft bootstrap.
            Class<?> utility = classes.loadClass("customskinloader.utils.HttpTextureUtil");
            utility.getField("defaultCacheDir").set(null, root.toFile());
            Class<?> type = classes.loadClass("customskinloader.fake.FakeMinecraftProfileTexture");
            MinecraftProfileTexture actual = (MinecraftProfileTexture) type.getConstructor(String.class, Map.class)
                    .newInstance("https://example.test/skins/csl-texture-hash.png", Map.of("model", "default"));
            AvatarSkinSource decoded = AvatarSkinSource.from(actual);
            check(decoded.valid() && decoded.image() != null && decoded.image().startsWith(root),
                    "Read the real CSL 15.1 getCacheFile API without a hard dependency");
            Files.createDirectories(decoded.image().getParent());
            writeSkin(decoded.image(), 64, 64);
            List<ResourceLocation> result = new ArrayList<>();
            new AvatarSkinCache(root.resolve("own"), Runnable::run, profile -> null, (profile, key, file, ready) -> {
                check(AvatarSkinSource.storedImage(key), "The actual CSL texture is imported locally rather than re-downloaded");
                ready.accept(ResourceLocation.fromNamespaceAndPath("ore", "test/csl"));
            }).loadKnown(new GameProfile(UUID.randomUUID(), "RealCSL"), decoded, result::add);
            check(result.size() == 1, "The installed CSL class can feed the persistent avatar cache");
            MinecraftProfileTexture base64 = (MinecraftProfileTexture) type.getConstructor(String.class, Map.class)
                    .newInstance("(BASE64)csl-base64-test", Map.of("model", "default"));
            AvatarSkinSource embedded = AvatarSkinSource.from(base64);
            check(!AvatarSkinSource.downloadable(embedded.url()) && embedded.image() != null && embedded.valid(),
                    "The actual CSL BASE64 texture has no URL and exposes a usable cache file");
            Files.createDirectories(embedded.image().getParent());
            writeSkin(embedded.image(), 128, 128);
            UUID id = UUID.randomUUID();
            Path persisted = root.resolve("base64-own");
            AvatarSkinCache.TextureLoader restore = (profile, key, image, ready) -> {
                check(AvatarSkinSource.storedImage(key) && Files.isRegularFile(image), "CSL BASE64 is stored as an independent image");
                ready.accept(ResourceLocation.fromNamespaceAndPath("ore", "test/base64"));
            };
            new AvatarSkinCache(persisted, Runnable::run, profile -> null, restore)
                    .loadKnown(new GameProfile(id, "Base64Member"), embedded, result::add);
            Files.delete(embedded.image());
            new AvatarSkinCache(persisted, Runnable::run, profile -> null, restore)
                    .load(new GameProfile(id, "Base64Member"), result::add);
            check(result.size() == 3, "An actual CSL BASE64 skin survives restart and removal of its original file");
        }
    }

    private static void writeSkin(Path file, int width, int height) throws Exception {
        BufferedImage skin = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int scale = width / 64;
        for (int y = 8 * scale; y < 16 * scale; y++) {
            for (int x = 8 * scale; x < 16 * scale; x++) skin.setRGB(x, y, 0x00224466);
            for (int x = 40 * scale; x < 48 * scale; x++) skin.setRGB(x, y, 0x80123456);
        }
        ImageIO.write(skin, "PNG", file.toFile());
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
