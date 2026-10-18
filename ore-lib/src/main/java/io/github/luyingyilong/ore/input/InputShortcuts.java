package io.github.luyingyilong.ore.input;

import org.lwjgl.glfw.GLFW;

/** Clipboard access is supplied by the screen; edits follow the same transactional handler. */
final class InputShortcuts {
    interface Clipboard {
        String get();
        void set(String text);
    }

    private InputShortcuts() { }

    static boolean matches(int key, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0 || (modifiers & GLFW.GLFW_MOD_ALT) != 0) return false;
        return key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_C || key == GLFW.GLFW_KEY_X
                || key == GLFW.GLFW_KEY_V || key == GLFW.GLFW_KEY_Z || key == GLFW.GLFW_KEY_Y;
    }

    static boolean dispatch(OreTextFieldHandler editor, int key, int modifiers, Clipboard clipboard) {
        if (!matches(key, modifiers)) return false;
        switch (key) {
            case GLFW.GLFW_KEY_A -> editor.markAll();
            case GLFW.GLFW_KEY_C -> {
                if (editor.hasTextMarked()) clipboard.set(editor.getSelectedText());
            }
            case GLFW.GLFW_KEY_X -> {
                editor.endEditGroup();
                if (editor.hasTextMarked()) {
                    clipboard.set(editor.getSelectedText());
                    editor.deleteSelection();
                }
            }
            case GLFW.GLFW_KEY_V -> {
                editor.endEditGroup();
                String text = clipboard.get();
                // Do not pre-delete the selection: invalid or empty paste leaves it intact.
                if (text != null && !text.isEmpty()) editor.insert(text, true);
            }
            case GLFW.GLFW_KEY_Z -> {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) editor.redo();
                else editor.undo();
            }
            case GLFW.GLFW_KEY_Y -> editor.redo();
        }
        return true;
    }

    static boolean type(OreTextFieldHandler editor, char character) {
        if (character < ' ' || character == '\u007f' || character == '\u00a7') return false;
        // Replace the selection in one validated edit, without MUI's preliminary deletion.
        editor.type(character);
        return true;
    }
}
