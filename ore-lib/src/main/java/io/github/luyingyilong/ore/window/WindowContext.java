package io.github.luyingyilong.ore.window;

import brachy.modularui.screen.viewport.ModularGuiContext;
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
    private final boolean nativeWindow;
    private final Set<Integer> keys = new HashSet<>(), buttons = new HashSet<>();
    private Window owner;
    private ModularGuiContext mui;
    private long handle;
    private int windowWidth = 1, windowHeight = 1, framebufferWidth = 1, framebufferHeight = 1;
    private double scale = 1, mouseX, mouseY;
    // Scoped to ModularPanel's current mouse-press dispatch, reset at its HEAD/RETURN.
    private boolean orePointerFocus;
    private int modifiers;
    private int originX, originY;
    private boolean focused = true;

    public WindowContext(boolean nativeWindow) {
        this.nativeWindow = nativeWindow;
    }

    public static WindowContext getPrimary() {
        return PRIMARY;
    }

    public static WindowContext getCurrent() {
        return current == null ? PRIMARY : current;
    }

    public static WindowContext of(ModularGuiContext mui) {
        return CONTEXTS.getOrDefault(mui, PRIMARY);
    }

    /**
     * Pixel-art UI uses whole physical pixels, including at fractional desktop DPI.
     */
    static double nativePixelScale(double requestedScale, double contentScale) {
        if (!Double.isFinite(requestedScale) || requestedScale <= 0
                || !Double.isFinite(contentScale) || contentScale <= 0)
            throw new IllegalArgumentException("Invalid native pixel scale");
        return Math.max(1, Math.floor(requestedScale * Math.max(1, contentScale) + 0.5));
    }

    public WindowPopups getPopups() {
        return popups;
    }

    public boolean getPointerFocusOwned() {
        return orePointerFocus;
    }

    public void setPointerFocusOwned(boolean owned) {
        orePointerFocus = owned;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public void setOrigin(int x, int y) {
        originX = x;
        originY = y;
    }

    public Scope enter() {
        WindowContext previous = current;
        current = this;
        return () -> current = previous;
    }

    public UUID getId() {
        return id;
    }

    public boolean getNativeWindow() {
        return nativeWindow;
    }

    public Window getOwner() {
        return owner;
    }

    public void setOwner(Window owner) {
        this.owner = owner;
    }

    public ModularGuiContext getMui() {
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

    public long getHandle() {
        return nativeWindow ? handle : Minecraft.getInstance().getWindow().getWindow();
    }

    void setHandle(long handle) {
        this.handle = handle;
    }

    public int getGuiWidth() {
        return Math.max(1, (int) Math.ceil(framebufferWidth / scale));
    }

    public int getGuiHeight() {
        return Math.max(1, (int) Math.ceil(framebufferHeight / scale));
    }

    /**
     * Preserve exact framebuffer coverage without stretching the rounded layout viewport.
     */
    double getProjectionWidth() {
        return framebufferWidth / scale;
    }

    double getProjectionHeight() {
        return framebufferHeight / scale;
    }

    public int getFramebufferWidth() {
        return framebufferWidth;
    }

    public int getFramebufferHeight() {
        return framebufferHeight;
    }

    public double getGuiScale() {
        return nativeWindow ? scale : Minecraft.getInstance().getWindow().getGuiScale();
    }

    public double clientX(double guiX) {
        return guiX * scale * windowWidth / framebufferWidth;
    }

    public double clientY(double guiY) {
        return guiY * scale * windowHeight / framebufferHeight;
    }

    public void setDimensions(int width, int height, int framebufferWidth, int framebufferHeight, double scale) {
        if (width < 1 || height < 1 || framebufferWidth < 1 || framebufferHeight < 1 || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid window dimensions");
        this.windowWidth = width;
        this.windowHeight = height;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.scale = scale;
    }

    public void setMouse(double clientX, double clientY) {
        mouseX = clientX * framebufferWidth / windowWidth / scale;
        mouseY = clientY * framebufferHeight / windowHeight / scale;
    }

    public int getMouseX() {
        return (int) mouseX;
    }

    /** Host-normalized GUI coordinates, also used by synthetic/keyboard inventory hit queries. */
    public void setGuiMouse(double x,double y) {
        if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("Invalid pointer coordinates");
        mouseX=x;mouseY=y;
    }

    public int getMouseY() {
        return (int) mouseY;
    }

    public boolean getFocused() {
        return !nativeWindow ? GLFW.glfwGetWindowAttrib(getHandle(), GLFW.GLFW_FOCUSED) != 0 : focused;
    }

    public void setFocused(boolean focused) {
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

    public void clearInput() {
        keys.clear();
        buttons.clear();
        modifiers = 0;
    }

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(int value) {
        modifiers = value;
    }

    public boolean mouseDown(int button) {
        return nativeWindow || owner != null ? buttons.contains(button) : GLFW.glfwGetMouseButton(getHandle(), button) == GLFW.GLFW_PRESS;
    }

    public boolean keyDown(int key) {
        return nativeWindow || owner != null ? keys.contains(key) : GLFW.glfwGetKey(getHandle(), key) == GLFW.GLFW_PRESS;
    }

    public String getClipboard() {
        String value = GLFW.glfwGetClipboardString(getHandle());
        return value == null ? "" : value;
    }

    public void setClipboard(String text) {
        GLFW.glfwSetClipboardString(getHandle(), text);
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
