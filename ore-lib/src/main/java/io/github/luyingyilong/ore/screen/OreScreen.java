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
    private static final java.util.Set<OreScreen> SESSIONS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private final java.util.Set<io.github.luyingyilong.ore.layout.Window> roots =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private Screen retainedParent;
    private boolean initialized;

    private OreScreen(ModularScreen screen) { super(screen); SESSIONS.add(this); }

    public static void attachRoot(io.github.luyingyilong.ore.layout.Window root) {
        if (Minecraft.getInstance().screen instanceof OreScreen screen) screen.roots.add(root);
    }

    private void endSession() {
        for (var root : java.util.List.copyOf(roots)) {
            root.hostClosed();
            root.queueFree("host ended");
        }
        roots.clear();
        SESSIONS.remove(this);
    }

    public static void disposeSessions() {
        for (OreScreen screen : java.util.List.copyOf(SESSIONS)) screen.endSession();
    }

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

    private void suspendRoots() {
        for (var root : java.util.List.copyOf(roots)) if (root.isAlive()) root.hostDetached();
    }

    @Override public void removed() {
        suspendRoots();
        super.removed();
    }

    public static void open(ModularScreen screen) {
        // MUI's client-only open(screen, settings) path ignores customGui.
        // Construct the wrapper directly, after installing the same default settings.
        if (Minecraft.getInstance().screen instanceof OreScreen parent) parent.suspendRoots();
        screen.getContext().setSettings(new UISettings());
        // MUI retains the actual parent screen and reopens its existing panels on return.
        screen.openParentOnClose(true);
        ClientGUI.open(new OreScreen(screen));
    }

    /** Close only the current MUI screen, returning through its retained parent chain. */
    public static void back() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        if (minecraft.screen instanceof OreScreen current) {
            current.screen().close();
            current.endSession();
        }
        else if (minecraft.screen != null) minecraft.screen.onClose();
    }

    @Override public void renderBackground(GuiGraphics graphics) { }
}
