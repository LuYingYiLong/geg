package io.github.luyingyilong.geg.chat.client;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.chat.ChatMessage;
import io.github.luyingyilong.geg.client.ui.Avatar;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.client.ui.theme.OreTheme;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** One-column recycled message nodes, with Scroll's shared clipping and stable-key anchors. */
public final class ChatMessageList extends Container {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Grid<ChatMessage> grid = new Grid<ChatMessage>().columns(1, 1, 6);
    private final Scroll scroll = new Scroll().horizontal(Scroll.BarMode.DISABLED).content(grid);
    private List<ChatMessage> values = List.of();
    private boolean follow = true;
    private int newMessages;
    private int pendingRestore = -1;

    public ChatMessageList() {
        grid.adapter(new Grid.Adapter<>() {
            public int size() { return values.size(); }
            public ChatMessage item(int index) { return values.get(index); }
            public Object key(ChatMessage item) { return item.id(); }
            public int measureHeight(ChatMessage item, int width, Font font) {
                return Math.max(24, body(item).measureHeight(font, Math.max(1, width - 28)) + 14);
            }
            public Grid.View<ChatMessage> createView() { return new MessageView(); }
        });
        addChild(scroll); sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
    }
    private static Label body(ChatMessage item) { return new Label(item.text()).wrap(Label.Wrap.WORD); }
    public void messages(List<ChatMessage> next) {
        if (values.equals(next)) return;
        boolean bottom = atBottom(); Set<UUID> old = new HashSet<>(); values.forEach(m -> old.add(m.id()));
        long last = values.stream().filter(m -> m.sequence() < Long.MAX_VALUE / 2).mapToLong(ChatMessage::sequence).max().orElse(0);
        long lastLocal = values.stream().filter(m -> m.sequence() >= Long.MAX_VALUE / 2).mapToLong(ChatMessage::sequence).max().orElse(0);
        if (!bottom) newMessages += (int) next.stream().filter(m -> !old.contains(m.id())
                && (m.sequence() < Long.MAX_VALUE / 2 ? m.sequence() > last : m.sequence() > lastLocal)).count();
        values = List.copyOf(next); follow |= bottom; grid.invalidate(); requestLayout();
    }
    public Scroll scroll() { return scroll; }
    public boolean atBottom() { return scroll.scrollY() >= scroll.maxScrollY() - 2; }
    public int newMessages() { return newMessages; }
    public int activeViews() { return grid.activeViews(); }
    public void bottom() { follow = true; newMessages = 0; requestLayout(); }
    public void restore(int y) { follow = false; newMessages = 0; pendingRestore = Math.max(0, y); }
    @Override public Size measureSize(Font font, Size available) { return minimumSize(); }
    @Override public void layout(Rect bounds, Font font) {
        super.layout(bounds, font); fitChild(scroll, new Rect(0, 0, bounds.width(), bounds.height()), font);
        if (pendingRestore >= 0) { scroll.setScroll(0, pendingRestore); pendingRestore = -1; }
        if (follow) { scroll.setScroll(0, scroll.maxScrollY()); follow = false; }
        if (atBottom()) newMessages = 0;
    }
    private static final class MessageView extends Container implements Grid.View<ChatMessage> {
        private final Avatar avatar = new Avatar().size(20);
        private final Label author = new Label().color(OreTheme.TEXT_SECONDARY).overflow(Label.Overflow.ELLIPSIS);
        private final RichText text = new RichText();
        private ChatMessage message;
        MessageView() { addChild(avatar); addChild(author); addChild(text); }
        public void bind(ChatMessage message) {
            this.message = message; avatar.visible(!message.sender().equals(io.github.luyingyilong.geg.chat.ChatChannel.NONE));
            if (avatar.visible()) avatar.player(message.sender(), message.senderName());
            author.text(Component.literal(message.senderName().isEmpty() ? TIME.format(Instant.ofEpochMilli(message.time()))
                    : message.senderName() + "  " + TIME.format(Instant.ofEpochMilli(message.time()))));
            text.label.text(message.text());
        }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            layoutChild(avatar, new Rect(0, 0, 20, 20), font);
            layoutChild(author, new Rect(28, 0, Math.max(1, bounds.width() - 28), 10), font);
            layoutChild(text, new Rect(28, 14, Math.max(1, bounds.width() - 28), Math.max(1, bounds.height() - 14)), font);
        }
        @Override protected boolean onPointerPressed(int x, int y) {
            if (avatar.hit(x, y) && message != null && Minecraft.getInstance().player != null
                    && !message.sender().equals(Minecraft.getInstance().player.getUUID())) {
                ClientChatState.openDirect(message.sender().toString()); return true;
            }
            return false;
        }
        public void draw(ModularGuiContext context, Rect area, boolean hovered) { layout(area, context.getFont()); draw(context, 0, 0); }
    }
    private static final class RichText extends Container {
        final Label label = new Label().wrap(Label.Wrap.WORD);
        private Style hovered;
        RichText() { addChild(label); }
        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font); layoutChild(label, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }
        private Style style(Font font, int x, int y, Rect area) {
            if (!area.contains(x, y)) return null;
            var lines = label.lines(font, area.width()); int row = (y - area.y()) / label.lineHeight(font);
            return row >= lines.size() ? null : font.getSplitter().componentStyleAtWidth(lines.get(row), x - area.x());
        }
        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            hovered = style(context.getFont(), context.getAbsMouseX(), context.getAbsMouseY(), screenBounds(screenX, screenY));
        }
        @Override public boolean hasTooltip() { return hovered != null && hovered.getHoverEvent() != null; }
        @Override public void renderTooltip(ModularGuiContext context, int x, int y) {
            context.getGraphics().renderComponentHoverEffect(context.getFont(), hovered, x, y);
        }
        @Override protected boolean onPointerPressed(int x, int y) {
            var mc = Minecraft.getInstance(); Style style = style(mc.font, x, y, absoluteBounds());
            return style != null && style.getClickEvent() != null && mc.screen != null && mc.screen.handleComponentClicked(style);
        }
    }
}
