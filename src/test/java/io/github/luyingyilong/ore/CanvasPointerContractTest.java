package io.github.luyingyilong.ore;

import brachy.modularui.api.widget.Interactable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashSet;
import java.util.Set;

/** Check the real compiled Canvas/MUI boundary without initializing a Forge client or its themes. */
public final class CanvasPointerContractTest {
    public static void main(String[] args) throws Exception {
        // ModularPanel only remembers the press owner when Result.accepts is true.
        check(Interactable.Result.SUCCESS.accepts && Interactable.Result.SUCCESS.stops, "Capture and consume press");
        check(!Interactable.Result.STOP.accepts, "STOP cannot retain a drag target");
        for (String screen : new String[]{"market/client/MarketCanvasWidget", "mail/client/MailCanvasWidget",
                "organization/client/OrganizationCanvasWidget"}) {
            Set<String> results = new HashSet<>();
            boolean[] forwardsDrag = {false};
            try (var stream = CanvasPointerContractTest.class.getClassLoader()
                    .getResourceAsStream("io/github/luyingyilong/geg/" + screen + ".class")) {
                if (stream == null) throw new AssertionError("Canvas class missing: " + screen);
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                              String signature, String[] exceptions) {
                        if (!name.equals("onMousePressed") && !name.equals("onMouseDrag")) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitFieldInsn(int opcode, String owner, String field, String type) {
                                if (name.equals("onMousePressed") && opcode == Opcodes.GETSTATIC
                                        && owner.equals("brachy/modularui/api/widget/Interactable$Result")) results.add(field);
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) {
                                if (name.equals("onMouseDrag") && owner.equals("io/github/luyingyilong/ore/layout/Window")
                                        && method.equals("pointerDragged")) forwardsDrag[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            check(results.equals(Set.of("SUCCESS", "IGNORE")), screen + " must retain every handled press");
            check(forwardsDrag[0], screen + " must forward captured drags to Window");
        }
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
