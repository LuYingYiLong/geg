package io.github.luyingyilong.geg.client.ui;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

/** Inspect the installed optional jar's public API without booting Forge or initializing IMBlocker. */
public final class ImBlockerCompatibilityTest {
    private ImBlockerCompatibilityTest() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the installed IMBlocker jar path");
        try (ZipFile jar = new ZipFile(Path.of(args[0]).toFile())) {
            require(jar, "gui/FocusContainer", Set.of(
                    "requestFocus(Lio/github/reserveword/imblocker/common/gui/FocusableWidget;)V",
                    "switchFocus(Lio/github/reserveword/imblocker/common/gui/FocusableWidget;)V",
                    "removeFocus(Lio/github/reserveword/imblocker/common/gui/FocusableWidget;)V"));
            require(jar, "gui/FocusManager", Set.of(
                    "getFocusOwner()Lio/github/reserveword/imblocker/common/gui/FocusableObject;"));
            require(jar, "IMManager", Set.of("setState(Z)V", "updateCaretPosition()V"));
            require(jar, "gui/UniversalIMEPreeditOverlay", Set.of(
                    "getInstance()Lio/github/reserveword/imblocker/common/gui/UniversalIMEPreeditOverlay;",
                    "preeditContentUpdated(Ljava/lang/String;I)V"));
            require(jar, "gui/UniversalIMECandidateOverlay", Set.of(
                    "getInstance()Lio/github/reserveword/imblocker/common/gui/UniversalIMECandidateOverlay;",
                    "candidateListUpdated([Ljava/lang/String;I)V"));
        }
    }

    private static void require(ZipFile jar, String type, Set<String> required) throws Exception {
        Set<String> available = new HashSet<>();
        var entry = jar.getEntry("io/github/reserveword/imblocker/common/" + type + ".class");
        if (entry == null) throw new AssertionError("Missing IMBlocker API: " + type);
        try (var stream = jar.getInputStream(entry)) {
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                          String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_PUBLIC) != 0) available.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        if (!available.containsAll(required)) throw new AssertionError("Incompatible IMBlocker API: " + type);
    }
}
