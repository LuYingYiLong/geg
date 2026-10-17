package io.github.luyingyilong.geg.client.ui.input;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;

import java.util.Arrays;
import java.util.Objects;

/** Optional real-IMM integration. Missing environmental prerequisites are explicitly reported as SKIP. */
public final class WindowsInputMethodNativeTest {
    public static void main(String[] args) {
        boolean required = args.length == 1 && args[0].equals("--require-native");
        if (args.length > 0 && !required) throw new IllegalArgumentException("Optional argument: --require-native");
        if (!Platform.isWindows()) { unavailable("requires Windows IMM32", required); return; }
        var window = User32.INSTANCE.CreateWindowEx(0, "EDIT", "Ore hidden IME test", 0,
                0, 0, 100, 100, null, null, null, null);
        check(window != null, "Create the hidden test window");
        var imm = WindowsInputMethod.Imm.API;
        Pointer original = imm.ImmGetContext(window);
        if (original != null) imm.ImmReleaseContext(window, original);
        try {
            check(imm.ImmAssociateContextEx(window, null, 0x0010), "Restore the default context during capability probing");
            Pointer defaults = imm.ImmGetContext(window);
            if (defaults != null) imm.ImmReleaseContext(window, defaults);
            imm.ImmAssociateContext(window, original);
            if (defaults == null) {
                unavailable("Windows supplied no default IMM context for the hidden test window (original=" + original + ")", required);
                return;
            }
            try (WindowsInputMethod connection = new WindowsInputMethod(null, window)) {
                check(!connection.preedit().active(), "A fresh test window has no preedit");
                Pointer associated = imm.ImmGetContext(window);
                check(associated != null, "Keep the verified default context during activation");
                WindowsInputMethod.CandidateForm candidate = new WindowsInputMethod.CandidateForm();
                WindowsInputMethod.CompositionForm composition = new WindowsInputMethod.CompositionForm();
                boolean candidateCaptured, compositionCaptured, initialOpen;
                try {
                    candidateCaptured = imm.ImmGetCandidateWindow(associated, 0, candidate);
                    compositionCaptured = imm.ImmGetCompositionWindow(associated, composition);
                    initialOpen = imm.ImmGetOpenStatus(associated);
                    WindowsInputMethod.CandidateForm movedCandidate = new WindowsInputMethod.CandidateForm();
                    movedCandidate.style = 0x0040;
                    movedCandidate.position.x = 57; movedCandidate.position.y = 61;
                    imm.ImmSetCandidateWindow(associated, movedCandidate);
                    WindowsInputMethod.CompositionForm movedComposition = new WindowsInputMethod.CompositionForm();
                    movedComposition.style = 0x0002;
                    movedComposition.position.x = 41; movedComposition.position.y = 43;
                    imm.ImmSetCompositionWindow(associated, movedComposition);
                    imm.ImmSetOpenStatus(associated, !initialOpen);
                } finally { imm.ImmReleaseContext(window, associated); }
                connection.cancelComposition();
                connection.close();
                connection.close();
                Pointer restored = imm.ImmGetContext(window);
                try {
                    check(Objects.equals(original, restored), "Restore the original context association");
                    if (restored != null && Objects.equals(associated, restored)) {
                        check(imm.ImmGetOpenStatus(restored) == initialOpen, "Restore the original open status");
                        if (candidateCaptured) {
                            WindowsInputMethod.CandidateForm actual = new WindowsInputMethod.CandidateForm();
                            check(imm.ImmGetCandidateWindow(restored, 0, actual), "Read restored candidate position");
                            check(Arrays.equals(candidate.getPointer().getByteArray(0, candidate.size()),
                                    actual.getPointer().getByteArray(0, actual.size())), "Restore candidate position");
                        }
                        if (compositionCaptured) {
                            WindowsInputMethod.CompositionForm actual = new WindowsInputMethod.CompositionForm();
                            check(imm.ImmGetCompositionWindow(restored, actual), "Read restored composition position");
                            check(Arrays.equals(composition.getPointer().getByteArray(0, composition.size()),
                                    actual.getPointer().getByteArray(0, actual.size())), "Restore composition position");
                        }
                    }
                } finally { if (restored != null) imm.ImmReleaseContext(window, restored); }
            }
        } finally {
            imm.ImmAssociateContext(window, original);
            User32.INSTANCE.DestroyWindow(window);
        }
    }

    private static void unavailable(String reason, boolean required) {
        if (required) throw new AssertionError("Native IMM prerequisites unavailable: " + reason);
        System.out.println("SKIP WindowsInputMethodNativeTest: " + reason);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
