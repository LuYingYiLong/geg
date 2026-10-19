package io.github.luyingyilong.ore.window;

import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.utils.Stencil;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.luyingyilong.ore.input.InputControl;
import io.github.luyingyilong.ore.input.OreInputMethod;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import java.util.*;

/**
 * Main-context Ore rendering, shared-texture presentation in an independent Windows window.
 */
public final class NativeWindowHost implements WindowHost {
    private final WindowOptions options;
    private final WindowContext context = new WindowContext(true);
    private final ArrayDeque<Runnable> events = new ArrayDeque<>();
    private MuiInputHost inputHost;
    private long handle, lastFrame;
    private int frames;
    private Window root;
    private GLCapabilities capabilities;
    private NativePresentation presentation;
    private NativeImeContext ime;
    private TextureTarget target;
    private ModularScreen screen;
    private Control retainedFocus;
    private boolean closing, dirty = true;
    private double pointerX, pointerY;

    public NativeWindowHost(WindowOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    int framesRendered() {
        return frames;
    }

    TextureTarget renderTarget() {
        return target;
    }

    @Override
    public WindowContext getContext() {
        return context;
    }

    @Override
    public boolean getNativeWindow() {
        return true;
    }

    @Override
    public boolean isOpen() {
        return handle != 0 && !closing;
    }

    public long nativeHandle() {
        return handle;
    }

    public void repaint() {
        dirty = true;
    }

    @Override
    public void open(Window root) {
        RenderSystem.assertOnRenderThread();
        if (isOpen()) {
            if (this.root != root) throw new IllegalStateException("Host already owns another window");
            requestFocus();
            return;
        }
        if (Util.getPlatform() != Util.OS.WINDOWS)
            throw new UnsupportedOperationException("Ore native windows currently support Windows only");
        this.root = root;
        context.setOwner(root);
        closing = false;
        long main = GLFW.glfwGetCurrentContext();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        try {
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            int width = options.width(), height = options.height(), left = 0, top = 0;
            double initialScale = 1;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var x = stack.mallocInt(1);
                var y = stack.mallocInt(1);
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                long monitor = GLFW.glfwGetPrimaryMonitor();
                if (monitor != 0) {
                    var scaleX = stack.mallocFloat(1);
                    var scaleY = stack.mallocFloat(1);
                    GLFW.glfwGetMonitorContentScale(monitor, scaleX, scaleY);
                    initialScale = Math.max(1, scaleX.get(0));
                    width = (int) Math.round(width * initialScale);
                    height = (int) Math.round(height * initialScale);
                    GLFW.glfwGetMonitorWorkarea(monitor, x, y, w, h);
                    width = Math.min(width, Math.max(1, w.get(0) - 32));
                    height = Math.min(height, Math.max(1, h.get(0) - 64));
                    left = x.get(0) + (w.get(0) - width) / 2;
                    top = y.get(0) + (h.get(0) - height) / 2;
                }
            }
            handle = GLFW.glfwCreateWindow(width, height, options.title(), 0, main);
            if (handle == 0) throw new IllegalStateException("GLFW could not create the Ore native window");
            context.setHandle(handle);
            ime = new NativeImeContext(handle);
            GLFW.glfwSetWindowPos(handle, left, top);
            GLFW.glfwSetWindowSizeLimits(handle, Math.min(width, (int) Math.round(options.minimumWidth() * initialScale)), Math.min(height, (int) Math.round(options.minimumHeight() * initialScale)), GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
            GLFW.glfwMakeContextCurrent(handle);
            capabilities = GL.createCapabilities();
            GLFW.glfwSwapInterval(0);
            presentation = new NativePresentation();
            GLFW.glfwMakeContextCurrent(main);
            GL.setCapabilities(mainCapabilities);
            resize();
            installCallbacks();
            inputHost = new MuiInputHost(context, null, this::isOpen, root::close);
            screen = inputHost.getScreen();
            prepareInputs();
            WindowManager.add(this);
            GLFW.glfwShowWindow(handle);
            requestFocus();
        } catch (RuntimeException | Error error) {
            destroy();
            throw error;
        } finally {
            GLFW.glfwMakeContextCurrent(main);
            GL.setCapabilities(mainCapabilities);
            GLFW.glfwDefaultWindowHints();
        }
    }

    private void installCallbacks() {
        GLFW.glfwSetWindowCloseCallback(handle, window -> events.add(root::close));
        GLFW.glfwSetWindowSizeCallback(handle, (window, w, h) -> dirty = true);
        GLFW.glfwSetFramebufferSizeCallback(handle, (window, w, h) -> dirty = true);
        GLFW.glfwSetWindowContentScaleCallback(handle, (window, x, y) -> dirty = true);
        GLFW.glfwSetWindowRefreshCallback(handle, window -> dirty = true);
        GLFW.glfwSetWindowFocusCallback(handle, (window, focused) -> events.add(() -> focusChanged(focused)));
        GLFW.glfwSetCursorPosCallback(handle, (window, x, y) -> events.add(() -> {
            double dx = x - pointerX, dy = y - pointerY;
            pointerX = x;
            pointerY = y;
            context.setMouse(x, y);
            updateMouse();
            io.github.luyingyilong.ore.event.WindowInputs.move(root, context.getMouseX(), context.getMouseY());
            dirty = true;
        }));
        GLFW.glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> events.add(() -> pointer(button, action, mods)));
        GLFW.glfwSetKeyCallback(handle, (window, key, scan, action, mods) -> events.add(() -> key(key, scan, action, mods)));
        GLFW.glfwSetCharCallback(handle, (window, codepoint) -> events.add(() -> {
            if (!context.getFocused()) return;
            root.dispatchInput(new io.github.luyingyilong.ore.event.TextInputEvent(context, new String(Character.toChars(codepoint)), context.getModifiers()));
            dirty = true;
        }));
        GLFW.glfwSetScrollCallback(handle, (window, x, y) -> events.add(() -> {
            root.dispatchInput(new io.github.luyingyilong.ore.event.ScrollEvent(context, context.getMouseX(), context.getMouseY(), x, y, context.getModifiers()));
            dirty = true;
        }));
    }

    private void updateMouse() {
        if (screen != null) screen.getContext().updateState(context.getMouseX(), context.getMouseY(), 0);
    }

    private void pointer(int button, int action, int mods) {
        if (!context.getFocused()) return;
        context.button(button, action != GLFW.GLFW_RELEASE);
        updateMouse();
        screen.getContext().updateMouseButton(button, action != GLFW.GLFW_RELEASE);
        root.dispatchInput(new io.github.luyingyilong.ore.event.PointerEvent(context,
                action == GLFW.GLFW_PRESS ? io.github.luyingyilong.ore.event.PointerEvent.Action.PRESS : io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE,
                context.getMouseX(), context.getMouseY(), button, mods));
        dirty = true;
    }

    private void key(int key, int scan, int action, int mods) {
        if (!context.getFocused()) return;
        var mui = screen.getContext();
        mui.updateKey(key, scan, mods, action != GLFW.GLFW_RELEASE);
        io.github.luyingyilong.ore.event.WindowInputs.key(root,
                action == GLFW.GLFW_RELEASE ? io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE :
                        action == GLFW.GLFW_REPEAT ? io.github.luyingyilong.ore.event.KeyEvent.Action.REPEAT : io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS,
                key, scan, mods, mui);
        dirty = true;
    }

    private void focusChanged(boolean focused) {
        if (!focused) {
            retainedFocus = root.getFocusManager().getFocused();
            root.cancelInteractions();
            if (screen.getContext().isFocused()) screen.getContext().removeFocus();
            OreInputMethod.releaseWindow(context);
            context.setFocused(false);
        } else {
            context.setFocused(true);
            if (retainedFocus != null && retainedFocus.getVisible())
                root.getFocusManager().requestFocus(retainedFocus, screen.getContext());
        }
        dirty = true;
    }

    private void resize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var fw = stack.mallocInt(1);
            var fh = stack.mallocInt(1);
            var sx = stack.mallocFloat(1);
            var sy = stack.mallocFloat(1);
            GLFW.glfwGetWindowSize(handle, w, h);
            GLFW.glfwGetFramebufferSize(handle, fw, fh);
            GLFW.glfwGetWindowContentScale(handle, sx, sy);
            if (w.get(0) < 1 || h.get(0) < 1 || fw.get(0) < 1 || fh.get(0) < 1) return;
            context.setDimensions(w.get(0), h.get(0), fw.get(0), fh.get(0), WindowContext.nativePixelScale(options.guiScale(), sx.get(0)));
        }
    }

    private void prepareInputs() {
        inputHost.prepare(root);
    }

    void frame() {
        if (closing) {
            destroy();
            return;
        }
        if (handle == 0) return;
        try (var active = context.enter()) {
            resize();
            prepareInputs();
            int count = 0;
            while (!events.isEmpty() && count++ < 512 && !closing) events.remove().run();
            if (closing) return;
            if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) != 0) return;
            long now = System.nanoTime();
            boolean animate = root.getLayoutDirty() || root.getFocusManager().getNativeInputFocused();
            if (now - lastFrame < 33_333_333L || !dirty && !animate && now - lastFrame < 100_000_000L) return;
            lastFrame = now;
            dirty = false;
            render();
        }
    }

    private void render() {
        Minecraft mc = Minecraft.getInstance();
        long main = GLFW.glfwGetCurrentContext();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        try (WindowRenderState state = new WindowRenderState()) {
            if (target == null || target.width != context.getFramebufferWidth() || target.height != context.getFramebufferHeight()) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(context.getFramebufferWidth(), context.getFramebufferHeight(), true, Minecraft.ON_OSX);
                target.enableStencil();
            }
            target.setClearColor(0.19F, 0.20F, 0.20F, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.disableScissor();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, (float) context.getProjectionWidth(), (float) context.getProjectionHeight(), 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.getModelViewStack().translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            WindowGraphics graphics = new WindowGraphics(mc, mc.renderBuffers().bufferSource(), context);
            var mui = screen.getContext();
            screen.onResize(context.getGuiWidth(), context.getGuiHeight());
            mui.updateState(context.getMouseX(), context.getMouseY(), 0);
            mui.setGraphics(graphics);
            mui.setOverrideFont(mc.font);
            mui.reset();
            mui.pushViewport(null, mui.getScreenArea());
            try {
                Stencil.reset();
                root.prepareTree(mui);
                root.layoutViewport(context.getGuiWidth(), context.getGuiHeight(), mc.font);
                for (InputControl input : WindowCanvas.inputs(root)) {
                    var area = input.getWidget().getArea();
                    var b = input.getWidgetBounds();
                    area.x = b.x();
                    area.y = b.y();
                    area.width = b.width();
                    area.height = b.height();
                    input.getWidget().onUpdate();
                }
                try (var frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(mui, context)) { root.draw(frame, 0, 0); }
                graphics.flush();
            } finally {
                mui.popViewport(null);
                mui.setGraphics(null);
            }
            long fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            GL11.glFlush();
            try {
                GLFW.glfwMakeContextCurrent(handle);
                GL.setCapabilities(capabilities);
                GL32.glWaitSync(fence, 0, GL32.GL_TIMEOUT_IGNORED);
                presentation.draw(target.getColorTextureId(), context.getFramebufferWidth(), context.getFramebufferHeight());
                GLFW.glfwSwapBuffers(handle);
                frames++;
            } finally {
                GLFW.glfwMakeContextCurrent(main);
                GL.setCapabilities(mainCapabilities);
                GL32.glDeleteSync(fence);
            }
        } finally {
            GLFW.glfwMakeContextCurrent(main);
            GL.setCapabilities(mainCapabilities);
        }
    }

    @Override
    public void close() {
        closing = true;
    }

    @Override
    public void requestFocus() {
        if (handle != 0) {
            GLFW.glfwRestoreWindow(handle);
            GLFW.glfwFocusWindow(handle);
        }
    }

    void destroy() {
        if (io.github.luyingyilong.ore.signal.DispatchGuard.active()) {
            closing = true;
            if (root != null) root.queueFree("host ended");
            return;
        }
        if (root != null) {
            root.hostClosed();
            root.queueFree("host ended");
        }
        io.github.luyingyilong.ore.layout.LifecycleScheduler.flush();
        OreInputMethod.releaseWindow(context);
        if (screen != null && screen.getContext().isFocused()) screen.getContext().removeFocus();
        if (inputHost != null) inputHost.close();
        context.detach();
        if (ime != null) {
            ime.close();
            ime = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        if (handle != 0) {
            long main = GLFW.glfwGetCurrentContext();
            GLCapabilities previous = GL.getCapabilities();
            try {
                GLFW.glfwMakeContextCurrent(handle);
                if (capabilities != null) GL.setCapabilities(capabilities);
                if (presentation != null) presentation.close();
            } finally {
                GLFW.glfwMakeContextCurrent(main == handle ? Minecraft.getInstance().getWindow().getWindow() : main);
                GL.setCapabilities(previous);
            }
            Callbacks.glfwFreeCallbacks(handle);
            GLFW.glfwDestroyWindow(handle);
            handle = 0;
        }
        presentation = null;
        screen = null;
        events.clear();
        WindowManager.remove(this);

    }

}
