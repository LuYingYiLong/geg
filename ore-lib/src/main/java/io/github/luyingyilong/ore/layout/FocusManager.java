package io.github.luyingyilong.ore.layout;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.Table;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One keyboard focus per Ore window, restricted to the topmost visible popup. */
public final class FocusManager {
    public enum Direction { LEFT, RIGHT, UP, DOWN }

    private final Window window;
    private final Map<Control, Control> remembered = new IdentityHashMap<>();
    private Control scope;
    private Control focused;
    private boolean keyboardFocusVisible;
    private boolean inputPointerFocusVisible;
    private Control activationOwner;
    private int activationKey = -1;
    private final Set<Integer> activationKeysDown = new HashSet<>();

    FocusManager(Window window) {
        this.window = window;
    }

    public Control focused() {
        validate();
        return focused;
    }

    /** Does not validate focus: a native widget may ask while candidates are being collected. */
    public boolean allowsNativeInput(Control input) { return inside(input, topScope()); }

    public void requestFocus(Control target, ModularGuiContext context) {
        requestFocus(target, context, true);
    }

    private void requestFocus(Control target, ModularGuiContext context, boolean revealTarget) {
        validate(revealTarget);
        if (target != null && !eligible(target)) return;
        if (focused == target) return;
        cancelActivation();
        if (focused != null) focused.onFocusChanged(false);
        focused = target;
        if (!(target instanceof io.github.luyingyilong.ore.input.InputControl))
            inputPointerFocusVisible = false;
        if (target != null) {
            remembered.put(scope, target);
            if (revealTarget) reveal(target);
            target.onFocusChanged(true);
        }
        if (context != null) {
            if (target instanceof io.github.luyingyilong.ore.input.InputControl input) {
                if (!input.widget().isFocused()) context.focus(input.widget());
            } else if (context.isFocused()) context.removeFocus();
        }
    }

    public void pointerPressed(Control hit, ModularGuiContext context) {
        pointerActivity();
        // A pointer already identifies a visible point. Revealing the whole control here can
        // scroll a wide table/input before Window dispatches that very same press.
        validate(false);
        for (Control control = hit; control != null; control = control.parent()) {
            if (control.acceptsFocus() && inside(control, scope)) {
                requestFocus(control, context, false);
                inputPointerFocusVisible = control instanceof io.github.luyingyilong.ore.input.InputControl;
                return;
            }
        }
        if (focused instanceof io.github.luyingyilong.ore.input.InputControl)
            requestFocus(null, context);
    }

    /** Called by the MUI widget when Esc or another widget releases its native focus. */
    public void nativeFocusLost(Control input) {
        if (focused == input) requestFocus(null, null);
    }

    /** Virtualized controls call this before reusing a subtree for a different logical record. */
    public void forgetSubtree(Control subtree) {
        java.util.Objects.requireNonNull(subtree);
        if (inside(focused, subtree)) requestFocus(null, null);
        if (inside(activationOwner, subtree)) cancelActivation();
        remembered.entrySet().removeIf(entry -> inside(entry.getValue(), subtree));
    }

    /** Any mouse button switches away from visible keyboard navigation. */
    public void pointerActivity() {
        keyboardFocusVisible = false;
        inputPointerFocusVisible = false;
    }

    public void keyboardNavigation() {
        keyboardFocusVisible = true;
    }

    public void synchronizeNativeFocus(ModularGuiContext context) {
        validate();
        if (context == null) return;
        Control nativeField = nativeField(scopeRoot());
        if (nativeField != null) {
            if (nativeField != focused) requestFocus(nativeField, context);
        } else {
            if (focused instanceof io.github.luyingyilong.ore.input.InputControl)
                requestFocus(null, null);
            if (context.isFocused()) context.removeFocus();
        }
    }

    public boolean nativeInputFocused() {
        return nativeField(scopeRoot()) != null;
    }

    public boolean keyPressed(int keyCode, int modifiers, ModularGuiContext context) {
        synchronizeNativeFocus(context);
        // Keep the physical key latched even if its action opens a new focus scope.
        if (activationKeysDown.contains(keyCode)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                && focused instanceof io.github.luyingyilong.ore.input.InputControl input
                && nativeInputFocused()) {
            input.widget().onKeyPressed(keyCode, 0, modifiers);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP
                || keyCode == GLFW.GLFW_KEY_DOWN) keyboardNavigation();
        // TextFieldWidget owns its caret keys; Tab is the explicit way out of editing.
        if (nativeInputFocused() && keyCode != GLFW.GLFW_KEY_TAB) return false;
        if (scope != null && scope.onFocusKey(keyCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (moveTab((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1, context)) return true;
            return false;
        }
        if (focused != null && focused.onFocusKey(keyCode, modifiers)) return true;
        Direction direction = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> Direction.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> Direction.RIGHT;
            case GLFW.GLFW_KEY_UP -> Direction.UP;
            case GLFW.GLFW_KEY_DOWN -> Direction.DOWN;
            default -> null;
        };
        if (direction != null) {
            if (focused != null && focused.onFocusMove(direction)) return true;
            return move(direction, context);
        }
        if (focused != null && (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE)) {
            activationKeysDown.add(keyCode);
            if (activationOwner != focused || activationKey != keyCode) {
                cancelActivation();
                activationOwner = focused;
                activationKey = keyCode;
            }
            return focused.onActivationPressed(keyCode);
        }
        return false;
    }

    public boolean keyReleased(int keyCode, int modifiers, ModularGuiContext context) {
        boolean held = activationKeysDown.remove(keyCode);
        if (activationOwner == null || activationKey != keyCode) return held;
        Control captured = activationOwner;
        activationOwner = null;
        activationKey = -1;
        synchronizeNativeFocus(context);
        if (focused != captured || !eligible(captured)) {
            captured.cancelInteraction();
            return true;
        }
        return captured.onActivationReleased(keyCode);
    }

    public void cancelActivation() {
        if (activationOwner != null) activationOwner.cancelInteraction();
        activationOwner = null;
        activationKey = -1;
    }

    public void resetInput() {
        cancelActivation();
        activationKeysDown.clear();
    }

    public Rect outlineBounds() {
        validate();
        if (focused == null || !keyboardFocusVisible
                && !(inputPointerFocusVisible
                && focused instanceof io.github.luyingyilong.ore.input.InputControl)) return null;
        Rect area = visibleBounds(focused);
        if (area == null) return null;
        int pad = focused.focusExpansion();
        return new Rect(area.x() - pad, area.y() - pad,
                area.width() + pad * 2, area.height() + pad * 2);
    }

    private boolean moveTab(int step, ModularGuiContext context) {
        List<Control> candidates = candidates();
        if (candidates.isEmpty()) return false;
        int index = candidates.indexOf(focused);
        int next = index < 0 ? (step > 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(index + step, candidates.size());
        requestFocus(candidates.get(next), context);
        return true;
    }

    private boolean move(Direction direction, ModularGuiContext context) {
        List<Control> candidates = candidates();
        if (candidates.isEmpty()) return false;
        if (focused == null || visibleBounds(focused) == null) {
            requestFocus(candidates.get(0), context);
            return true;
        }
        Rect from = visibleBounds(focused);
        Control best = null;
        long bestScore = Long.MAX_VALUE;
        for (Control candidate : candidates) {
            if (candidate == focused) continue;
            Rect to = navigationBounds(candidate);
            if (to == null) continue;
            long dx = (long) to.x() * 2 + to.width() - ((long) from.x() * 2 + from.width());
            long dy = (long) to.y() * 2 + to.height() - ((long) from.y() * 2 + from.height());
            long primary = switch (direction) {
                case LEFT -> -dx;
                case RIGHT -> dx;
                case UP -> -dy;
                case DOWN -> dy;
            };
            if (primary <= 0) continue;
            long secondary = direction == Direction.LEFT || direction == Direction.RIGHT
                    ? Math.abs(dy) : Math.abs(dx);
            boolean overlaps = direction == Direction.LEFT || direction == Direction.RIGHT
                    ? from.y() < to.bottom() && to.y() < from.bottom()
                    : from.x() < to.right() && to.x() < from.right();
            long score = (overlaps ? 0 : 1_000_000_000L) + primary * 1000 + secondary;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best == null) return false;
        requestFocus(best, context);
        return true;
    }

    private List<Control> candidates() {
        List<Control> result = new ArrayList<>();
        if (scopeRoot() != null) collect(scopeRoot(), result);
        return result;
    }

    private void collect(Control node, List<Control> result) {
        if (!node.visible()) return;
        if (node.acceptsFocus() && navigationBounds(node) != null) result.add(node);
        for (Control child : node.children()) collect(child, result);
    }

    private void validate() {
        validate(true);
    }

    private void validate(boolean revealTarget) {
        Control nextScope = topScope();
        if (nextScope != scope) {
            cancelActivation();
            if (focused != null) {
                remembered.put(scope, focused);
                focused.onFocusChanged(false);
            }
            scope = nextScope;
            focused = remembered.get(scope);
            if (focused == null || !eligible(focused)) {
                focused = null;
                List<Control> choices = candidates();
                if (scope != null && !choices.isEmpty()) focused = choices.get(0);
            }
            if (focused != null) {
                if (revealTarget) reveal(focused);
                focused.onFocusChanged(true);
            }
        } else if (focused != null && !eligible(focused)) {
            cancelActivation();
            focused.onFocusChanged(false);
            focused = null;
        }
    }

    private Control topScope() {
        Overlay overlay = window.overlayLayer();
        if (overlay == null) return null;
        List<Control> layers = overlay.children();
        for (int index = layers.size() - 1; index >= 0; index--) {
            Control layer = layers.get(index);
            if (layer.visible() && layer.focusScope()) return layer;
        }
        return null;
    }

    private Control scopeRoot() { return scope == null ? window.contentNode() : scope; }

    private boolean eligible(Control target) {
        if (!target.acceptsFocus() || !inside(target, scope)) return false;
        for (Control node = target; node != null; node = node.parent()) {
            if (!node.visible()) return false;
            if (node == window) return true;
        }
        return false;
    }

    private static boolean inside(Control target, Control scope) {
        if (scope == null) return true;
        for (Control node = target; node != null; node = node.parent()) if (node == scope) return true;
        return false;
    }

    private Rect visibleBounds(Control target) {
        return visibleBounds(target, target.focusBounds());
    }

    private Rect visibleBounds(Control target, Rect area) {
        for (Control node = target.parent(); node != null; node = node.parent()) {
            if (node instanceof Scroll scroll) {
                area = area.translated(-scroll.scrollX(), -scroll.scrollY());
                Rect viewport = scroll.viewport().translated(scroll.absoluteBounds().x(),
                        scroll.absoluteBounds().y());
                area = intersection(area, viewport);
                if (area == null) return null;
            }
            if (node instanceof Table<?> table) {
                area = intersection(area, table.bodyBounds());
                if (area == null) return null;
            }
            if (node != target && !node.visible()) return null;
        }
        return intersection(area, window.absoluteBounds());
    }

    /** Buffered table buttons are navigation targets too; focusing one scrolls its row into view. */
    private Rect navigationBounds(Control target) {
        Rect visible = visibleBounds(target);
        if (visible != null) return visible;
        Rect area = target.focusBounds();
        boolean buffered = false;
        for (Control node = target.parent(); node != null; node = node.parent()) {
            if (!node.visible()) return null;
            if (node instanceof Table<?> table) {
                if (visibleBounds(table, table.bodyBounds()) == null) return null;
                buffered = true;
            }
            if (node instanceof Scroll scroll) area = area.translated(-scroll.scrollX(), -scroll.scrollY());
        }
        return buffered ? area : null;
    }

    private static boolean intersects(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right()
                && a.y() < b.bottom() && b.y() < a.bottom();
    }

    private static Rect intersection(Rect a, Rect b) {
        if (!intersects(a, b)) return null;
        int left = Math.max(a.x(), b.x());
        int top = Math.max(a.y(), b.y());
        return new Rect(left, top, Math.min(a.right(), b.right()) - left,
                Math.min(a.bottom(), b.bottom()) - top);
    }

    private void reveal(Control target) {
        Rect area = target.focusBounds();
        for (Control node = target.parent(); node != null; node = node.parent()) {
            if (node instanceof Table<?> table) {
                table.revealControl(target);
                area = target.focusBounds();
            }
            if (node instanceof Scroll scroll) {
                Rect origin = scroll.absoluteBounds();
                scroll.ensureVisible(new Rect(area.x() - origin.x(), area.y() - origin.y(),
                        area.width(), area.height()));
                area = area.translated(-scroll.scrollX(), -scroll.scrollY());
            }
        }
    }

    private Control nativeField(Control node) {
        if (node == null || !node.visible()) return null;
        if (node instanceof io.github.luyingyilong.ore.input.InputControl input
                && input.widget().isFocused() && input.acceptsFocus()) return input;
        for (Control child : node.children()) {
            Control found = nativeField(child);
            if (found != null) return found;
        }
        return null;
    }
}
