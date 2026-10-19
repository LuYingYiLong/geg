package io.github.luyingyilong.ore.input;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** An absent optional render parameter must not prevent access to the text/IME API. */
public final class ImBlockerOverlayAccessTest implements Opcodes {
    private ImBlockerOverlayAccessTest() { }

    public static void main(String[] args) throws Exception {
        var loader = new FixtureLoader();
        Class<?> preedit = loader.create("test/ime/Preedit", "preeditContentUpdated", "Ljava/lang/String;");
        Class<?> candidates = loader.create("test/ime/Candidates", "candidateListUpdated", "[Ljava/lang/String;");
        try {
            preedit.getMethod("getInstance");
            throw new AssertionError("Fixture must reproduce reflection's linkage of the missing optional UI type");
        } catch (NoClassDefFoundError expected) {
            assert expected.getMessage().contains("AbsentImDrawList");
        }
        var access = new ImBlockerOverlayAccess(preedit, candidates);
        access.clear();
        access.clear();
        assert preedit.getField("calls").getInt(null) == 2;
        assert candidates.getField("calls").getInt(null) == 2;
        assert preedit.getField("value").get(null) == null;
        assert candidates.getField("value").get(null) == null;
        assert preedit.getField("index").getInt(null) == 0;
        assert candidates.getField("index").getInt(null) == 0;

        Class<?> incompatible = loader.create("test/ime/Incompatible", "candidateListUpdated", "Ljava/lang/Object;");
        try {
            new ImBlockerOverlayAccess(preedit, incompatible);
            throw new AssertionError("A missing required signature must still be rejected");
        } catch (NoSuchMethodException expected) {
            // Optional rendering signatures are ignored; required API signatures remain strict.
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        Class<?> create(String name, String update, String parameter) {
            var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(V17, ACC_PUBLIC, name, null, "java/lang/Object", null);
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "calls", "I", null, null).visitEnd();
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "index", "I", null, null).visitEnd();
            writer.visitField(ACC_PUBLIC | ACC_STATIC, "value", "Ljava/lang/Object;", null, null).visitEnd();
            var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            var instance = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "getInstance", "()L" + name + ";", null, null);
            instance.visitCode();
            instance.visitTypeInsn(NEW, name);
            instance.visitInsn(DUP);
            instance.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false);
            instance.visitInsn(ARETURN);
            instance.visitMaxs(0, 0);
            instance.visitEnd();
            var changed = writer.visitMethod(ACC_PUBLIC, update, "(" + parameter + "I)V", null, null);
            changed.visitCode();
            changed.visitFieldInsn(GETSTATIC, name, "calls", "I");
            changed.visitInsn(ICONST_1);
            changed.visitInsn(IADD);
            changed.visitFieldInsn(PUTSTATIC, name, "calls", "I");
            changed.visitVarInsn(ALOAD, 1);
            changed.visitFieldInsn(PUTSTATIC, name, "value", "Ljava/lang/Object;");
            changed.visitVarInsn(ILOAD, 2);
            changed.visitFieldInsn(PUTSTATIC, name, "index", "I");
            changed.visitInsn(RETURN);
            changed.visitMaxs(0, 0);
            changed.visitEnd();
            var optional = writer.visitMethod(ACC_PUBLIC, "renderOnOptionalUi", "(Loptional/AbsentImDrawList;)V", null, null);
            optional.visitCode();
            optional.visitInsn(RETURN);
            optional.visitMaxs(0, 0);
            optional.visitEnd();
            writer.visitEnd();
            byte[] bytes = writer.toByteArray();
            return defineClass(name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}
