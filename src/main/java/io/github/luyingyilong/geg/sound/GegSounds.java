package io.github.luyingyilong.geg.sound;

import io.github.luyingyilong.geg.GegMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GegSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GegMod.MODID);

    public static final RegistryObject<SoundEvent> CLICK = sound("click");
    public static final RegistryObject<SoundEvent> BUTTON = sound("button");
    public static final RegistryObject<SoundEvent> DRAWER_OPEN = sound("drawer_open");
    public static final RegistryObject<SoundEvent> DRAWER_CLOSE = sound("drawer_close");
    public static final RegistryObject<SoundEvent> TOAST = sound("toast");

    private GegSounds() { }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.tryBuild(GegMod.MODID, name)));
    }

    public static void register(IEventBus modBus) { SOUNDS.register(modBus); }
}
