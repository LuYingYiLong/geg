package io.github.luyingyilong.geg.market.client;

import com.ibm.icu.text.Transliterator;
import io.github.luyingyilong.geg.market.MarketKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class MarketSearchIndex {
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower");
    private static final Map<MarketKey, String> CACHE = new HashMap<>();
    private static String language = "";

    private MarketSearchIndex() {
    }

    static boolean matches(ItemStack item, String query) {
        String currentLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!currentLanguage.equals(language)) {
            language = currentLanguage;
            CACHE.clear();
        }
        String index = CACHE.computeIfAbsent(MarketKey.of(item), ignored -> index(item.getHoverName().getString()));
        String normalized = query.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return index.contains(normalized);
    }

    static void clear() {
        CACHE.clear();
    }

    private static String index(String name) {
        String latin = PINYIN.transliterate(name).toLowerCase(Locale.ROOT);
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        for (String part : latin.split("[^a-z0-9]+")) {
            if (!part.isEmpty()) {
                full.append(part);
                initials.append(part.charAt(0));
            }
        }
        return name.toLowerCase(Locale.ROOT) + " " + full + " " + initials;
    }
}
