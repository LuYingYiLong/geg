package io.github.luyingyilong.ore.util;

import io.github.luyingyilong.ore.Toast;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import net.minecraft.client.gui.Font;

import java.util.*;

/** Bounded, keyed toast instances survive screen changes without consuming input. */
public final class ToastQueue {
    public record Frame(Toast toast, Rect area) { }
    private final Map<String, Toast> messages = new LinkedHashMap<>();
    public Toast show(String key, Toast toast) {
        Objects.requireNonNull(key); Objects.requireNonNull(toast); prune();
        Toast existing = messages.get(key);
        if (existing != null) {
            existing.text(toast.text()).duration(toast.duration()).type(toast.type()).refresh();
            if (existing != toast) toast.queueFree();
            return existing;
        }
        while (messages.size() >= 3) messages.remove(messages.keySet().iterator().next()).queueFree();
        messages.put(key, toast.open()); return toast;
    }
    public void clear() { for (Toast toast : List.copyOf(messages.values())) toast.queueFree(); messages.clear(); }
    public int size() { prune(); return messages.size(); }
    private void prune() { messages.values().removeIf(toast -> { if (!toast.isAlive() || !toast.visible() || toast.finished()) { toast.queueFree(); return true; } return false; }); }

    public List<Frame> layout(Font font, int width, int height) {
        prune();
        if (width < 1 || height < 1) return List.of();
        int margin = Math.min(8, Math.max(0, (width - 2) / 2));
        int available = Math.max(1, Math.min(320, width - margin * 2 - 1));
        int top = Math.min(8, Math.max(0, height - 2));
        while (messages.size() > 1) {
            int required = messages.values().stream().mapToInt(toast -> toast.measureSize(font, new Size(available, height)).height() + 4).sum();
            if (required + top - 4 < height) break;
            messages.remove(messages.keySet().iterator().next()).queueFree();
        }
        List<Frame> frames = new ArrayList<>();
        int y = top;
        for (Toast toast : messages.values()) {
            Size size = toast.measureSize(font, new Size(available, height));
            int toastHeight = Math.min(size.height(), Math.max(1, height - top - 1));
            int x = Math.max(0, (width - size.width()) / 2);
            int offset = (int) Math.round((y + toastHeight + 1) * (1.0D - toast.progress()));
            Rect area = new Rect(x, y - offset, size.width(), toastHeight);
            toast.layout(area, font); frames.add(new Frame(toast, area)); y += toastHeight + 4;
        }
        return List.copyOf(frames);
    }
}
