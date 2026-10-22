package io.github.luyingyilong.ore.gui;

public final class UiException extends IllegalArgumentException {
    private final UiBlueprint.Source source;

    public UiException(UiBlueprint.Source source, String message) {
        super(source + ": " + message);
        this.source = source;
    }

    public UiException(UiBlueprint.Source source, String message, Throwable cause) {
        super(source + ": " + message, cause);
        this.source = source;
    }

    public UiBlueprint.Source getSource() {
        return source;
    }
}
