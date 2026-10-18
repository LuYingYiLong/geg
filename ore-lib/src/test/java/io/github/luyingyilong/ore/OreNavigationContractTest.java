package io.github.luyingyilong.ore;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Verify the compiled bridge and the pinned MUI parent's reopen contract without a live Forge client. */
public final class OreNavigationContractTest {
    public static void main(String[] args) throws Exception {
        String oreScreen = "io/github/luyingyilong/ore/screen/OreScreen";
        String modularScreen = "brachy/modularui/screen/ModularScreen";
        Set<String> open = calls(oreScreen, "open");
        check(open.contains(modularScreen + ".openParentOnClose"), "Ore screens enable parent restoration");
        Set<String> back = calls(oreScreen, "back");
        check(back.contains(modularScreen + ".close"), "Back closes through MUI's lifecycle");
        check(!back.contains("net/minecraft/client/Minecraft.setScreen"), "Back must not discard MUI's parent chain");
        check(calls("io/github/luyingyilong/ore/Navbar", "<init>").contains(oreScreen + ".back"),
                "Navbar's default callback uses the navigation bridge");
        check(calls("io/github/luyingyilong/ore/layout/Window", "<init>").contains(oreScreen + ".back"),
                "Window Esc and Navbar share the same retained-parent return action");
        check(calls("io/github/luyingyilong/ore/input/Input", "onKeyPressed")
                        .contains("brachy/modularui/screen/viewport/ModularGuiContext.removeFocus"),
                "Input consumes Esc by removing native focus, including during preedit");
        List<String> focusKeys = orderedCalls("io/github/luyingyilong/ore/layout/FocusManager", "keyPressed");
        int inputEscape = focusKeys.indexOf("io/github/luyingyilong/ore/input/Input.onKeyPressed");
        int popupEscape = focusKeys.indexOf("io/github/luyingyilong/ore/layout/Control.onFocusKey");
        check(inputEscape >= 0 && popupEscape > inputEscape, "Native input receives Esc before the top popup");
        for (String popup : new String[]{"Modal", "Dialog"})
            check(calls("io/github/luyingyilong/ore/" + popup, "onFocusKey").contains("java/lang/Runnable.run"),
                    popup + " delegates Esc to its owner's close callback");

        // These library behaviors are what keep drawers, scroll offsets and drafts on the original instances.
        Set<String> close = calls("brachy/modularui/screen/ModularPanel", "closeIfOpen");
        check(close.contains("brachy/modularui/screen/viewport/ModularGuiContext.getParent")
                        && close.contains("brachy/modularui/api/MCHelper.popScreen"), "MUI closes to its retained parent");
        check(calls("brachy/modularui/screen/ClientScreenHandler", "onGuiChanged")
                        .contains("brachy/modularui/screen/viewport/ModularGuiContext.setParent"),
                "Switching screens records the actual previous screen");
        check(calls("brachy/modularui/screen/PanelManager", "tryInit").contains("brachy/modularui/screen/ModularPanel.reopen"),
                "Returning reopens retained panels instead of rebuilding their controls");
        Set<String> reopen = calls("brachy/modularui/screen/ModularPanel", "reopen");
        check(!reopen.contains("brachy/modularui/screen/ModularPanel.onOpen")
                        && !reopen.contains("brachy/modularui/screen/ModularPanel.initialise"),
                "Reopening cannot reinitialize or dispose the old controls");
    }

    private static Set<String> calls(String className, String methodName) throws Exception {
        return new HashSet<>(orderedCalls(className, methodName));
    }

    private static List<String> orderedCalls(String className, String methodName) throws Exception {
        List<String> result = new ArrayList<>();
        try (var stream = OreNavigationContractTest.class.getClassLoader().getResourceAsStream(className + ".class")) {
            if (stream == null) throw new AssertionError("Class missing: " + className);
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) {
                            result.add(owner + "." + method);
                        }
                        @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                            for (Object argument : arguments)
                                if (argument instanceof Handle handle) result.add(handle.getOwner() + "." + handle.getName());
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return result;
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
