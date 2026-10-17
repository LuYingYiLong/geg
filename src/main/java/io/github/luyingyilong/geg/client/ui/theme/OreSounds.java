package io.github.luyingyilong.geg.client.ui.theme;

import io.github.luyingyilong.geg.sound.GegSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Local UI feedback; never broadcasts a world sound or sends a network packet. */
public final class OreSounds {
    public enum Cue {
        CLICK,
        BUTTON,
        DRAWER_OPEN,
        DRAWER_CLOSE,
    }

    private OreSounds() { }

    public static void play(Cue cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        var sound = switch (cue) {
            case CLICK -> GegSounds.CLICK;
            case BUTTON -> GegSounds.BUTTON;
            case DRAWER_OPEN -> GegSounds.DRAWER_OPEN;
            case DRAWER_CLOSE -> GegSounds.DRAWER_CLOSE;
        };
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0F));
    }
}
