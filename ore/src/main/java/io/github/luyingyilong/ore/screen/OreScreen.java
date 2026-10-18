package io.github.luyingyilong.ore.screen;

import brachy.modularui.factory.ClientGUI;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.ScreenWrapper;
import brachy.modularui.screen.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Window paints the scrim; suppress MUI's additional world-background gradient. */
public final class OreScreen extends ScreenWrapper {
    private Screen retainedParent;
    private boolean initialized;

    private OreScreen(ModularScreen screen) { super(screen); }

    @Override public void init() {
        // MUI treats returning from a vanilla dialog as opening a new child of that dialog.
        // Keep this wrapper's original return target when its existing UI is resumed.
        if (initialized) screen().getContext().setParent(retainedParent);
        super.init();
        if (!initialized) {
            retainedParent = screen().getContext().getParent();
            initialized = true;
        }
    }

    public static void open(ModularScreen screen) {
        // MUI's client-only open(screen, settings) path ignores customGui.
        // Construct the wrapper directly, after installing the same default settings.
        screen.getContext().setSettings(new UISettings());
        // MUI retains the actual parent screen and reopens its existing panels on return.
        screen.openParentOnClose(true);
        ClientGUI.open(new OreScreen(screen));
    }

    /** Close only the current MUI screen, returning through its retained parent chain. */
    public static void back() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (minecraft.screen instanceof OreScreen current) current.screen().close();
        else if (minecraft.screen != null) minecraft.screen.onClose();
    }

    @Override public void renderBackground(GuiGraphics graphics) { }
}
