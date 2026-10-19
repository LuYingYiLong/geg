package io.github.luyingyilong.ore;

import org.objectweb.asm.*;
import java.util.HashSet;
import java.util.Set;

/** Both development and published Forge entry points must work without a host-specific refmap. */
public final class CanvasInputMixinTest {
    public static void main(String[] args) throws Exception {
        check("ExternalKeyboardMixin", Set.of("keyPress", "m_90893_", "charTyped", "m_90889_"));
        check("ExternalMouseMixin", Set.of("onPress", "m_91530_", "onScroll", "m_91526_"));
        System.out.println("ORE_CANVAS_INPUT_MIXINS_PASS");
    }
    private static void check(String name, Set<String> expected) throws Exception {
        Set<String> methods = new HashSet<>(); boolean[] remapDisabled = {false};
        try (var stream = CanvasInputMixinTest.class.getResourceAsStream("/io/github/luyingyilong/ore/mixin/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing client input bridge");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.endsWith("/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public void visit(String key, Object value) {
                            if (key.equals("remap")) remapDisabled[0] = Boolean.FALSE.equals(value);
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            if (!descriptor.endsWith("/Inject;")) return null;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public AnnotationVisitor visitArray(String key) {
                                    if (!key.equals("method")) return null;
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override public void visit(String ignored, Object value) { methods.add((String) value); }
                                    };
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }
        if (!remapDisabled[0] || !methods.containsAll(expected))
            throw new AssertionError("Missing official/SRG input entry points: " + name + " " + methods);
    }
}
