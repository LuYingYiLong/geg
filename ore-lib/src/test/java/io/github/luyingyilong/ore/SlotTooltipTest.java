package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.*;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;

/** Test real hit routing without initializing Minecraft's item registry or graphics context. */
public final class SlotTooltipTest {
    private static final class Preview extends Control {
        boolean available = true;
        @Override public boolean hasTooltip() { return available; }
    }

    public static void main(String[] args) throws Exception {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length() * 6; }
        };
        Preview preview = new Preview();
        preview.setPosition(30, 40).setRequestedSize(26, 26);
        preview.addChild(new Control().setRequestedSize(26, 26));
        Control scrollContent = new Control().setPreferred(240, 200);
        scrollContent.addChild(preview);
        Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.NEVER).setVertical(Scroll.BarMode.NEVER).setContent(scrollContent);
        scroll.setPosition(10, 10).setRequestedSize(100, 60);
        Overlay overlay = new Overlay();
        var childContainer57 = new Control();
        childContainer57.addChild(scroll);
        Window window = new Window(true).setContent(childContainer57).setOverlay(overlay);
        window.layoutViewport(200, 140, font);
        check(window.tooltipTarget(42, 68) == preview, "Visible portion of a clipped slot has a tooltip");
        check(window.tooltipTarget(42, 72) == null, "Clipped portion cannot trigger a tooltip");
        scroll.setScroll(20, 30);
        check(window.tooltipTarget(22, 22) == preview, "Hit routing includes both scroll offsets");
        check(window.tooltipTarget(42, 68) == null, "The old unscrolled position is no longer hovered");
        preview.available = false;
        check(window.tooltipTarget(22, 22) == null, "An empty slot has no tooltip");
        preview.available = true;
        scrollContent.setVisible(false);
        check(window.tooltipTarget(22, 22) == null, "Hidden ancestors suppress tooltips");
        scrollContent.setVisible(true);

        Preview drawerItem = new Preview(); drawerItem.setMinimum(26, 26);
        Drawer drawer = new Drawer(Component.literal("Drawer")).setAnimationDuration(0).setWidth(100).setContent(drawerItem);
        overlay.addChild(drawer); drawer.open(); window.layoutViewport(200, 140, font);
        check(window.tooltipTarget(22, 22) == null, "Drawer backdrop blocks underlying slot tooltips");
        Rect p = drawerItem.getAbsoluteBounds();
        check(window.tooltipTarget(p.x() + 2, p.y() + 2) == drawerItem, "A slot in the top Drawer is eligible");
        Preview modalItem = new Preview(); modalItem.setMinimum(26, 26);
        Modal modal = new Modal(Component.literal("Modal"), null, null, null).setSize(100, 90).setContent(modalItem);
        overlay.addChild(modal); window.layoutViewport(200, 140, font);
        check(window.tooltipTarget(p.x() + 2, p.y() + 2) != drawerItem, "A higher Modal suppresses Drawer tooltips");
        Rect m = modalItem.getAbsoluteBounds();
        check(window.tooltipTarget(m.x() + 2, m.y() + 2) == modalItem, "Top Modal's own slot remains eligible");
        modal.setVisible(false);
        check(window.tooltipTarget(p.x() + 2, p.y() + 2) == drawerItem, "Closing Modal restores Drawer tooltips");
        drawer.close();
        check(window.tooltipTarget(22, 22) == preview, "Closing Drawer restores page tooltips");
        scrollContent.removeChild(preview);
        check(window.tooltipTarget(22, 22) == null, "Removed slots cannot retain tooltip state");
        renderingContract();
    }

    private static void renderingContract() throws Exception {
        List<String> calls = calls("io/github/luyingyilong/ore/layout/Window", "draw");
        check(calls.indexOf("drawTooltip") > calls.indexOf("draw:io/github/luyingyilong/ore/layout/Overlay"),
                "Tooltip paints after overlay and Scroll clipping have finished");
        List<String> slot = calls("io/github/luyingyilong/ore/Slot", "renderTooltip");
        check(slot.contains("renderTooltip:net/minecraft/client/gui/GuiGraphics"), "Slot uses vanilla's item-stack tooltip path");
    }

    private static List<String> calls(String owner, String methodName) throws Exception {
        List<String> calls = new ArrayList<>();
        new ClassReader(owner).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals(methodName)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        calls.add(name); calls.add(name + ":" + owner);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return calls;
    }

    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
