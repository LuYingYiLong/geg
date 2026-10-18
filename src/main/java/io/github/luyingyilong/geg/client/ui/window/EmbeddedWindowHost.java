package io.github.luyingyilong.geg.client.ui.window;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import io.github.luyingyilong.geg.client.ui.screen.OreScreen;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;

/**
 * Existing screens attach lazily on paint; standalone Window.open uses a normal OreScreen.
 */
public final class EmbeddedWindowHost implements WindowHost {
    private final WindowContext context = new WindowContext(false);
    private boolean open;

    @Override
    public WindowContext context() {
        return context;
    }

    @Override
    public boolean nativeWindow() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void attached() {
        open = true;
    }

    public void detached() { open = false; context.detach(); }

    @Override
    public void open(Window root) {
        if (open) return;
        context.owner(root);
        ModularPanel<?> panel = new ModularPanel<>("ore_window").full().disableThemeBackground(true);
        WindowCanvas canvas = new WindowCanvas(root);
        panel.child(canvas.full().disableThemeBackground(true));
        OreScreen.open(new ModularScreen("geg", panel).useTheme(OreTheme.NAME).pausesGame(false));
        open = true;
    }

    @Override
    public void close() {
        open = false;
        OreScreen.back();
        context.detach();
    }

    @Override
    public void requestFocus() {
    }
}
