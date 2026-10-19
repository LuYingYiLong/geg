package io.github.luyingyilong.ore.compat.recipeviewer;

public final class InputGestureOwnersTest {
    public static void main(String[] args) {
        InputGestureOwners<Object> state = new InputGestureOwners<>();
        Object previousScreen = new Object(), nextScreen = new Object();
        var enter = InputGestureOwners.Token.key(257, 28);
        var mouse = InputGestureOwners.Token.button(0);
        var unknownA = InputGestureOwners.Token.key(-1, 2);
        var unknownB = InputGestureOwners.Token.key(-1, 3);
        state.claim(enter, previousScreen);
        state.claim(enter, nextScreen);
        check(state.getOwner(enter) == previousScreen, "Repeat after a popup/Screen switch does not rebind the original press");
        state.claim(mouse, previousScreen); state.release(enter); state.release(enter);
        check(!state.contains(enter) && state.contains(mouse) && !state.hasKeys(), "Mouse and keyboard releases are independent and idempotent");
        state.claim(unknownA, previousScreen); state.claim(unknownB, nextScreen);
        check(state.getOwner(unknownA) != state.getOwner(unknownB), "Unknown key codes retain distinct scan-code ownership");
        state.clear();
        check(!state.contains(mouse) && !state.hasKeys(), "OS focus loss/logout cancels all outstanding pairs");
        state.claim(enter, nextScreen);
        check(state.getOwner(enter) == nextScreen, "A fresh press after cancellation can belong to a new Screen");
        System.out.println("ORE_INPUT_GESTURE_OWNERS_PASS");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
