package io.github.luyingyilong.geg.client.ui.screen;

import brachy.modularui.factory.ClientGUI;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.ScreenWrapper;
import brachy.modularui.screen.UISettings;
import net.minecraft.client.gui.GuiGraphics;

/** Window paints the scrim; suppress MUI's additional world-background gradient. */
public final class OreScreen extends ScreenWrapper {
    private OreScreen(ModularScreen screen) { super(screen); }

    public static void open(ModularScreen screen) {
        // MUI's client-only open(screen, settings) path ignores customGui.
        // Construct the wrapper directly, after installing the same default settings.
        screen.getContext().setSettings(new UISettings());
        ClientGUI.open(new OreScreen(screen));
    }

    @Override public void renderBackground(GuiGraphics graphics) { }
}
