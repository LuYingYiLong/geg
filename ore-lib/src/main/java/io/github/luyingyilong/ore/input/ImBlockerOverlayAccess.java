package io.github.luyingyilong.ore.input;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Resolve only our API methods, without linking IMBlocker's optional ImGui render signatures. */
final class ImBlockerOverlayAccess {
    private final Object preedit;
    private final Object candidates;
    private final MethodHandle clearPreedit;
    private final MethodHandle clearCandidates;

    ImBlockerOverlayAccess() throws ReflectiveOperationException {
        this(Class.forName("io.github.reserveword.imblocker.common.gui.UniversalIMEPreeditOverlay"),
                Class.forName("io.github.reserveword.imblocker.common.gui.UniversalIMECandidateOverlay"));
    }

    ImBlockerOverlayAccess(Class<?> preeditType, Class<?> candidateType) throws ReflectiveOperationException {
        var lookup = MethodHandles.publicLookup();
        preedit = invoke(lookup.findStatic(preeditType, "getInstance", MethodType.methodType(preeditType)));
        candidates = invoke(lookup.findStatic(candidateType, "getInstance", MethodType.methodType(candidateType)));
        clearPreedit = lookup.findVirtual(preeditType, "preeditContentUpdated",
                MethodType.methodType(void.class, String.class, int.class));
        clearCandidates = lookup.findVirtual(candidateType, "candidateListUpdated",
                MethodType.methodType(void.class, String[].class, int.class));
    }

    void clear() {
        invoke(clearPreedit, preedit, null, 0);
        invoke(clearCandidates, candidates, null, 0);
    }

    private static Object invoke(MethodHandle method, Object... arguments) {
        try {
            return method.invokeWithArguments(arguments);
        } catch (RuntimeException | Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("IMBlocker overlay API invocation failed", error);
        }
    }
}
