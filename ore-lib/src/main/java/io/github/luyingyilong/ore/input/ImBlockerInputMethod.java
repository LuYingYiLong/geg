package io.github.luyingyilong.ore.input;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Optional IMBlocker 5.x adapter; no bundled copy or required mod dependency. */
final class ImBlockerInputMethod implements InputMethodSession.Connection {
    private static final String API = "io.github.reserveword.imblocker.common.";
    private final Object container;
    private final Object widget;
    private final Class<?> widgetType;
    private final Class<?> containerType;
    private final Method updateCaret;
    private final Method getFocusOwner;
    private final Method setState;
    private final Object preeditOverlay;
    private final Object candidateOverlay;
    private final Method clearPreedit;
    private final Method clearCandidates;
    private boolean registered;
    private boolean closed;

    ImBlockerInputMethod(Input input) {
        try {
            containerType = Class.forName(API + "gui.FocusContainer");
            widgetType = Class.forName(API + "gui.FocusableWidget");
            container = containerType.getField("MINECRAFT").get(null);
            var rectangle = Class.forName(API + "gui.Rectangle")
                    .getConstructor(double.class, double.class, double.class, double.class, double.class);
            var point = Class.forName(API + "gui.Point")
                    .getConstructor(double.class, double.class, double.class);
            updateCaret = Class.forName(API + "IMManager").getMethod("updateCaretPosition");
            setState = Class.forName(API + "IMManager").getMethod("setState", boolean.class);
            getFocusOwner = Class.forName(API + "gui.FocusManager").getMethod("getFocusOwner");
            Class<?> preeditType = Class.forName(API + "gui.UniversalIMEPreeditOverlay");
            Class<?> candidateType = Class.forName(API + "gui.UniversalIMECandidateOverlay");
            preeditOverlay = preeditType.getMethod("getInstance").invoke(null);
            candidateOverlay = candidateType.getMethod("getInstance").invoke(null);
            clearPreedit = preeditType.getMethod("preeditContentUpdated", String.class, int.class);
            clearCandidates = candidateType.getMethod("candidateListUpdated", String[].class, int.class);
            widget = Proxy.newProxyInstance(widgetType.getClassLoader(), new Class<?>[]{widgetType},
                    (proxy, method, args) -> {
                        return switch (method.getName()) {
                            case "equals" -> proxy == args[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "Ore Input IME focus";
                            case "getFocusContainer" -> container;
                            case "isTrulyFocused", "isRenderable" -> !closed && input.isFocused()
                                    && input.inputVisible() && input.inputMethodInteractive();
                            case "getPreferredState" -> true;
                            case "getGuiScale" -> input.getWindowContext().getGuiScale();
                            case "getFontHeight" -> input.inputMethodFontHeight();
                            case "getBoundsAbs" -> {
                                var area = input.inputMethodBounds();
                                yield rectangle.newInstance((double) area.x(), (double) area.y(),
                                        (double) area.width(), (double) area.height(),
                                        input.getWindowContext().getGuiScale());
                            }
                            case "getCaretPos" -> {
                                var caret = input.inputMethodCaret();
                                yield point.newInstance((double) caret.x(), (double) caret.y(),
                                        input.getWindowContext().getGuiScale());
                            }
                            default -> {
                                if (!method.isDefault())
                                    throw new UnsupportedOperationException("Unknown IMBlocker method: " + method);
                                yield InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
                            }
                        };
                    });
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported IMBlocker focus API", exception);
        }
    }

    @Override public void update() {
        try {
            // Register after InputMethodSession owns this connection, so even immediate probes work.
            if (!registered) {
                registered = true;
                containerType.getMethod("requestFocus", widgetType).invoke(container, widget);
            }
            updateCaret.invoke(null);
        }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @Override public void locateFocus() {
        try { containerType.getMethod("switchFocus", widgetType).invoke(container, widget); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @Override public InputPreedit preedit() {
        return !closed && Util.getPlatform() == Util.OS.WINDOWS
                ? WindowsInputMethod.currentPreedit() : InputPreedit.EMPTY;
    }

    // IMBlocker owns its preedit/candidate overlays and native context/position changes.
    @Override public boolean inlinePreedit() { return false; }

    @Override public void cancelComposition() {
        try {
            if (closed || !registered || getFocusOwner.invoke(null) != widget) return;
            // Only cancel our own uncommitted text; never re-associate IMBlocker's context.
            if (Util.getPlatform() == Util.OS.WINDOWS) WindowsInputMethod.cancelCurrentComposition();
            clearPreedit.invoke(preeditOverlay, null, 0);
            clearCandidates.invoke(candidateOverlay, null, 0);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    @Override public void close() {
        if (closed || !registered) return;
        try {
            boolean owned = getFocusOwner.invoke(null) == widget;
            closed = true;
            containerType.getMethod("removeFocus", widgetType).invoke(container, widget);
            Object next = getFocusOwner.invoke(null);
            // Do not disable or clear another mod's text field when it has taken focus already.
            if (owned && (next == null || next == container)) setState.invoke(null, false);
            updateCaret.invoke(null);
        }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }
}
