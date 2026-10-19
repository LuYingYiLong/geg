package io.github.luyingyilong.ore.window;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Window-local services. No native resources are initialized merely by constructing a control.
 */
public final class WindowContext {
    private static final Map<ModularGuiContext, WindowContext> CONTEXTS = new WeakHashMap<>();
    private static final WindowContext PRIMARY = new WindowContext(false);
    private static WindowContext current;
    private final UUID id = UUID.randomUUID();
    private final WindowPopups popups = new WindowPopups(this);
    public WindowPopups popups() { return popups; }
    private final boolean nativeWindow;
    private final Set<Integer> keys = new HashSet<>(), buttons = new HashSet<>();
    private Window owner;
    private ModularGuiContext mui;
    private long handle;
    private int windowWidth = 1, windowHeight = 1, framebufferWidth = 1, framebufferHeight = 1;
    private double scale = 1, mouseX, mouseY;
    // Scoped to ModularPanel's current mouse-press dispatch, reset at its HEAD/RETURN.
    private boolean orePointerFocus;
    public void pointerFocusOwned(boolean owned){orePointerFocus=owned;}
    public boolean pointerFocusOwned(){return orePointerFocus;}
    private int modifiers;
    private int originX,originY;
    public int originX(){return originX;}
    public int originY(){return originY;}
    public void origin(int x,int y){originX=x;originY=y;}
    private boolean focused = true;

    public WindowContext(boolean nativeWindow) {
        this.nativeWindow = nativeWindow;
    }

    public static WindowContext primary() {
        return PRIMARY;
    }

    public static WindowContext current() {
        return current == null ? PRIMARY : current;
    }

    public static WindowContext of(ModularGuiContext mui) {
        return CONTEXTS.getOrDefault(mui, PRIMARY);
    }

    public Scope enter() {
        WindowContext previous = current;
        current = this;
        return () -> current = previous;
    }

    public UUID id() {
        return id;
    }

    public boolean nativeWindow() {
        return nativeWindow;
    }

    public Window owner() {
        return owner;
    }

    public void owner(Window owner) {
        this.owner = owner;
    }

    public ModularGuiContext mui() {
        return mui;
    }

    public void bind(ModularGuiContext mui) {
        this.mui = mui;
        CONTEXTS.put(mui, this);
    }

    public void detach() {
        if (mui != null) CONTEXTS.remove(mui);
        mui = null;
        keys.clear();
        buttons.clear();
    }

    public long handle() {
        return nativeWindow ? handle : Minecraft.getInstance().getWindow().getWindow();
    }

    void handle(long handle) {
        this.handle = handle;
    }

    public int guiWidth() {
        return Math.max(1, (int) Math.ceil(framebufferWidth / scale));
    }

    public int guiHeight() {
        return Math.max(1, (int) Math.ceil(framebufferHeight / scale));
    }

    /** Preserve exact framebuffer coverage without stretching the rounded layout viewport. */
    double projectionWidth() {
        return framebufferWidth / scale;
    }

    double projectionHeight() {
        return framebufferHeight / scale;
    }

    /** Pixel-art UI uses whole physical pixels, including at fractional desktop DPI. */
    static double nativePixelScale(double requestedScale, double contentScale) {
        if (!Double.isFinite(requestedScale) || requestedScale <= 0
                || !Double.isFinite(contentScale) || contentScale <= 0)
            throw new IllegalArgumentException("Invalid native pixel scale");
        return Math.max(1, Math.floor(requestedScale * Math.max(1, contentScale) + 0.5));
    }

    public int framebufferWidth() {
        return framebufferWidth;
    }

    public int framebufferHeight() {
        return framebufferHeight;
    }

    public double guiScale() {
        return nativeWindow ? scale : Minecraft.getInstance().getWindow().getGuiScale();
    }

    public double clientX(double guiX) {
        return guiX * scale * windowWidth / framebufferWidth;
    }

    public double clientY(double guiY) {
        return guiY * scale * windowHeight / framebufferHeight;
    }

    public void dimensions(int width, int height, int framebufferWidth, int framebufferHeight, double scale) {
        if (width < 1 || height < 1 || framebufferWidth < 1 || framebufferHeight < 1 || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid window dimensions");
        this.windowWidth = width;
        this.windowHeight = height;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.scale = scale;
    }

    public void mouse(double clientX, double clientY) {
        mouseX = clientX * framebufferWidth / windowWidth / scale;
        mouseY = clientY * framebufferHeight / windowHeight / scale;
    }

    public int mouseX() {
        return (int) mouseX;
    }

    public int mouseY() {
        return (int) mouseY;
    }

    public boolean focused() {
        return !nativeWindow ? GLFW.glfwGetWindowAttrib(handle(), GLFW.GLFW_FOCUSED) != 0 : focused;
    }

    public void focused(boolean focused) {
        this.focused = focused;
        if (!focused) {
            keys.clear();
            buttons.clear();
            modifiers = 0;
        }
    }

    public void key(int key, int action, int modifiers) {
        this.modifiers = modifiers;
        if (action == GLFW.GLFW_RELEASE) keys.remove(key);
        else keys.add(key);
    }

    public void button(int button, boolean down) {
        if (down) buttons.add(button);
        else buttons.remove(button);
    }

    public void modifiers(int value){modifiers=value;}
    public void clearInput(){keys.clear();buttons.clear();modifiers=0;}

    public int modifiers() {
        return modifiers;
    }

    public boolean mouseDown(int button) {
        return nativeWindow || owner != null ? buttons.contains(button) : GLFW.glfwGetMouseButton(handle(), button) == GLFW.GLFW_PRESS;
    }

    public boolean keyDown(int key) {
        return nativeWindow || owner != null ? keys.contains(key) : GLFW.glfwGetKey(handle(), key) == GLFW.GLFW_PRESS;
    }

    public String clipboard() {
        String value = GLFW.glfwGetClipboardString(handle());
        return value == null ? "" : value;
    }

    public void clipboard(String text) {
        GLFW.glfwSetClipboardString(handle(), text);
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
