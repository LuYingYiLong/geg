package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.OreClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import java.util.Locale;

/** Local UI sounds need no separate mod or server-side registry entries. */
public final class OreSounds {
    public enum Cue { CLICK, BUTTON, DRAWER_OPEN, DRAWER_CLOSE, TOAST }
    private OreSounds() { }
    public static void play(Cue cue) { OreClient.options().soundPlayer().accept(cue); }
    public static void playDefault(Cue cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        var id = ResourceLocation.fromNamespaceAndPath("ore", cue.name().toLowerCase(Locale.ROOT));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0F));
    }
}
