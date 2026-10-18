package io.github.luyingyilong.geg.inspector;

/** In-process commands. No JSON transport or network-facing command endpoint. */
public record InspectorCommand(Operation operation, String node, int property, String value, String flag, boolean enabled) {
    public enum Operation { SELECT, TARGET, PICK, FLAG, EDIT, RESET, RESET_NODE, RESET_ALL, UNDO, REDO, STOP }
    public static InspectorCommand action(Operation operation) { return new InspectorCommand(operation, "", -1, "", "", false); }
    public static InspectorCommand select(String id) { return new InspectorCommand(Operation.SELECT, id, -1, "", "", false); }
    public static InspectorCommand edit(String id, int index, String value) { return new InspectorCommand(Operation.EDIT, id, index, value, "", false); }
    public static InspectorCommand flag(String key, boolean value) { return new InspectorCommand(Operation.FLAG, "", -1, "", key, value); }
}
