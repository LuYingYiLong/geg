package io.github.luyingyilong.ore.gui.dev;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.gui.UiDocument;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Development-only status outside the XML tree, so malformed XML cannot remove its own error UI. */
public final class UiReloadStatus {
    private final Label label = new Label();
    private static final Map<UiReloadStatus, Boolean> ACTIVE = new WeakHashMap<>();
    private UiDocument document;
    private String previous = "";

    public UiReloadStatus(UiDocument document) {
        this.document = document;
        label.setDebugName("XML reload status");
        label.setWrap(Label.Wrap.WORD);
        ACTIVE.put(this, Boolean.TRUE);
        label.getSignalScope().own(() -> { ACTIVE.remove(this); this.document = null; });
        refresh();
    }

    public Label getControl() { return label; }

    /** Client tick only; never mutates controls on a file watcher thread or during drawing. */
    public static void tick() {
        for (UiReloadStatus label : List.copyOf(ACTIVE.keySet())) if (label.label.isAlive()) label.refresh();
    }

    private void refresh() {
        if (document == null) return;
        UiSourceWatcher.SourceStatus source = UiSourceWatcher.getSourceStatus(document.getResource());
        String state = document.getStatus().name().toLowerCase(java.util.Locale.ROOT);
        String path = source == null ? document.getResource().toString() : source.path().toString();
        String watch = source == null ? "disabled" : source.running() ? "active" : "stopped";
        String error = document.getError().isEmpty() && source != null ? source.error() : document.getError();
        String signature = state + watch + path + document.getVersion() + error;
        if (signature.equals(previous)) return;
        previous = signature;
        label.setText(Component.translatable("gallery.ore.xml_status", document.getVersion(),
                Component.translatable("gallery.ore.xml_status." + state),
                Component.translatable("gallery.ore.xml_watch." + watch), path)
                .append(error.isEmpty() ? Component.empty() : Component.literal("\n" + error)));
        label.setThemeOverride(Label.FONT_COLOR, error.isEmpty() ? 0xFF8CB3FF : 0xFFF46D6D);
    }
}
