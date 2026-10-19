package io.github.luyingyilong.ore.window;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.screen.OreScreen;
import io.github.luyingyilong.ore.theme.OreTheme;

/**
 * Existing screens attach lazily on paint; standalone Window.open uses a normal OreScreen.
 */
public final class EmbeddedWindowHost implements WindowHost {
    private final WindowContext context = new WindowContext(false);
    private boolean open;

    @Override
    public WindowContext getContext() {
        return context;
    }

    @Override
    public boolean getNativeWindow() {
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
        context.setOwner(root);
        ModularPanel<?> panel = new ModularPanel<>("ore_window").full().disableThemeBackground(true);
        WindowCanvas canvas = new WindowCanvas(root);
        panel.child(canvas.full().disableThemeBackground(true));
        OreScreen.open(new ModularScreen(io.github.luyingyilong.ore.OreClient.getOptions().hostModId(), panel).useTheme(io.github.luyingyilong.ore.OreClient.getOptions().defaultTheme()).pausesGame(false));
        OreScreen.attachRoot(root);
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
