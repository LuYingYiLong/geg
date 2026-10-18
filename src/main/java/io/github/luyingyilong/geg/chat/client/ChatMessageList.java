package io.github.luyingyilong.geg.chat.client;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.geg.chat.ChatChannel;
import io.github.luyingyilong.geg.chat.ChatMessage;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.Avatar;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One-column recycled message nodes, with Scroll's shared clipping and stable-key anchors.
 */
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
            public int size() {
                return values.size();
            }

            public ChatMessage item(int index) {
                return values.get(index);
            }

            public Object key(ChatMessage item) {
                return item.id();
            }

            public int measureHeight(ChatMessage item, int width, Font font) {
                return messageHeight(item, width, font);
            }

            public Grid.View<ChatMessage> createView() {
                return new MessageView();
            }
        });
        addChild(scroll);
        sizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
    }

    private static Label body(ChatMessage item) {
        return new Label(item.text()).wrap(Label.Wrap.WORD);
    }

    private static boolean system(ChatMessage item) {
        return item.sender().equals(ChatChannel.NONE);
    }

    private static UUID localPlayerId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.player == null ? ChatChannel.NONE : minecraft.player.getUUID();
    }

    static int messageHeight(ChatMessage item, int width, Font font) {
        return system(item) ? body(item).measureHeight(font, Math.max(1, width))
                : Math.max(24, body(item).measureHeight(font, Math.max(1, width - 28)) + 14);
    }

    public void messages(List<ChatMessage> next) {
        if (values.equals(next)) return;
        boolean bottom = atBottom();
        Set<UUID> old = new HashSet<>();
        values.forEach(m -> old.add(m.id()));
        long last = values.stream().filter(m -> m.sequence() < Long.MAX_VALUE / 2).mapToLong(ChatMessage::sequence).max().orElse(0);
        long lastLocal = values.stream().filter(m -> m.sequence() >= Long.MAX_VALUE / 2).mapToLong(ChatMessage::sequence).max().orElse(0);
        if (!bottom) newMessages += (int) next.stream().filter(m -> !old.contains(m.id())
                && (m.sequence() < Long.MAX_VALUE / 2 ? m.sequence() > last : m.sequence() > lastLocal)).count();
        values = List.copyOf(next);
        follow |= bottom;
        grid.invalidate();
        requestLayout();
    }

    public Scroll scroll() {
        return scroll;
    }

    public boolean atBottom() {
        return scroll.scrollY() >= scroll.maxScrollY() - 2;
    }

    public int newMessages() {
        return newMessages;
    }

    public int activeViews() {
        return grid.activeViews();
    }

    public void bottom() {
        follow = true;
        newMessages = 0;
        requestLayout();
    }

    public void restore(int y) {
        follow = false;
        newMessages = 0;
        pendingRestore = Math.max(0, y);
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return minimumSize();
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        fitChild(scroll, new Rect(0, 0, bounds.width(), bounds.height()), font);
        if (pendingRestore >= 0) {
            scroll.setScroll(0, pendingRestore);
            pendingRestore = -1;
        }
        if (follow) {
            scroll.setScroll(0, scroll.maxScrollY());
            follow = false;
        }
        if (atBottom()) newMessages = 0;
    }

    static final class MessageView extends Container implements Grid.View<ChatMessage> {
        final RichText text = new RichText();
        private final Avatar avatar = new Avatar().size(20);
        private final Label author = new Label().color(OreTheme.TEXT_SECONDARY).overflow(Label.Overflow.ELLIPSIS);
        private final Supplier<UUID> viewer;
        private ChatMessage message;
        private boolean outgoing;

        MessageView() {
            this(ChatMessageList::localPlayerId);
        }

        MessageView(Supplier<UUID> viewer) {
            this.viewer = java.util.Objects.requireNonNull(viewer);
            addChild(avatar);
            addChild(author);
            addChild(text);
        }

        public void bind(ChatMessage message) {
            this.message = message;
            boolean system = system(message);
            outgoing = !system && message.sender().equals(viewer.get());
            Label.Horizontal alignment = system ? Label.Horizontal.CENTER
                    : outgoing ? Label.Horizontal.RIGHT : Label.Horizontal.LEFT;
            avatar.visible(!system);
            author.visible(!system);
            if (avatar.visible()) avatar.player(message.sender(), message.senderName());
            String time = TIME.format(Instant.ofEpochMilli(message.time()));
            String name = message.senderName();
            String playerInfo = outgoing ? time + "  " + name : name + "  " + time;
            author.text(Component.literal(name.isEmpty() ? time : playerInfo));
            author.align(alignment, Label.Vertical.TOP);
            text.label.text(message.text());
            text.align(alignment);
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            if (system(message)) {
                layoutChild(text, new Rect(0, 0, bounds.width(), bounds.height()), font);
                return;
            }
            int avatarX = outgoing ? Math.max(0, bounds.width() - 20) : 0;
            int textX = outgoing ? 0 : 28;
            int textWidth = Math.max(1, bounds.width() - 28);
            layoutChild(avatar, new Rect(avatarX, 0, 20, 20), font);
            layoutChild(author, new Rect(textX, 0, textWidth, 10), font);
            layoutChild(text, new Rect(textX, 14, textWidth, Math.max(1, bounds.height() - 14)), font);
        }

        @Override
        public void draw(ModularGuiContext context, int screenX, int screenY) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (system(message)) drawCommandPart(context, screenX, screenY);
            else drawPlayerPart(context, screenX, screenY);
        }

        private void drawPlayerPart(ModularGuiContext context, int screenX, int screenY) {
            if (avatar.visible()) avatar.draw(context, screenX, screenY);
            if (author.visible()) author.draw(context, screenX, screenY);
            if (text.visible()) text.draw(context, screenX, screenY);
        }

        private void drawCommandPart(ModularGuiContext context, int screenX, int screenY) {
            if (text.visible()) text.draw(context, screenX, screenY);
        }

        @Override
        protected boolean onPointerPressed(int x, int y) {
            if (avatar.hit(x, y) && message != null && !outgoing && Minecraft.getInstance().player != null
                    && !message.sender().equals(Minecraft.getInstance().player.getUUID())) {
                ClientChatState.openDirect(message.sender().toString());
                return true;
            }
            return false;
        }

        public void draw(ModularGuiContext context, Rect area, boolean hovered) {
            layout(area, context.getFont());
            draw(context, 0, 0);
        }
    }

    static final class RichText extends Container {
        final Label label = new Label().wrap(Label.Wrap.WORD);
        private Label.Horizontal alignment = Label.Horizontal.LEFT;
        private Style hovered;

        RichText() {
            addChild(label);
        }

        void align(Label.Horizontal alignment) {
            this.alignment = alignment;
            label.align(alignment, Label.Vertical.TOP);
        }

        @Override
        public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            layoutChild(label, new Rect(0, 0, bounds.width(), bounds.height()), font);
        }

        Style style(Font font, int x, int y, Rect area) {
            if (!area.contains(x, y)) return null;
            var lines = label.lines(font, area.width());
            int row = (y - area.y()) / label.lineHeight(font);
            if (row >= lines.size()) return null;
            float scale = label.scale() * (label.fontSize() / Label.DEFAULT_FONT_SIZE);
            int lineWidth = (int) Math.ceil(font.width(lines.get(row)) * scale);
            int left = area.x() + switch (alignment) {
                case LEFT -> 0;
                case CENTER -> (area.width() - lineWidth) / 2;
                case RIGHT -> area.width() - lineWidth;
            };
            if (x < left || x >= left + lineWidth) return null;
            return font.getSplitter().componentStyleAtWidth(lines.get(row), (int) ((x - left) / scale));
        }

        @Override
        protected void render(ModularGuiContext context, int screenX, int screenY) {
            hovered = style(context.getFont(), context.getAbsMouseX(), context.getAbsMouseY(), screenBounds(screenX, screenY));
        }

        @Override
        public boolean hasTooltip() {
            return hovered != null && hovered.getHoverEvent() != null;
        }

        @Override
        public void renderTooltip(ModularGuiContext context, int x, int y) {
            context.getGraphics().renderComponentHoverEffect(context.getFont(), hovered, x, y);
        }

        @Override
        protected boolean onPointerPressed(int x, int y) {
            var mc = Minecraft.getInstance();
            Style style = style(mc.font, x, y, absoluteBounds());
            return style != null && style.getClickEvent() != null && mc.screen != null && mc.screen.handleComponentClicked(style);
        }
    }
}
