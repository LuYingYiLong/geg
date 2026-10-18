package io.github.luyingyilong.geg.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import io.github.luyingyilong.geg.chat.client.ChatScreenInitial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Dev uses official fields; packaged Forge 1.20.1 uses the corresponding SRG field. */
@Mixin(value = ChatScreen.class, remap = false)
public abstract class ChatScreenInitialMixin implements ChatScreenInitial {
    @Shadow(aliases = "f_95576_", remap = false) private String initial;
    @Override public String geg$initial() { return initial; }
}
