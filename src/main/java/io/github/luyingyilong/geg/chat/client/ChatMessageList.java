package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.ChatChannel;
import io.github.luyingyilong.geg.chat.ChatMessage;
import io.github.luyingyilong.ore.Avatar;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
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
    private final Grid<ChatMessage> grid = new Grid<ChatMessage>().setColumns(1, 1, 6);
    private final Scroll scroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED).setContent(grid);
    private List<ChatMessage> values = List.of();
    private boolean follow = true;
    private int newMessages;
    private int pendingRestore = -1;

    public ChatMessageList() {
        grid.setAdapter(new Grid.Adapter<>() {
            public int getSize() {
                return values.size();
            }

            public ChatMessage getItem(int index) {
                return values.get(index);
            }

            public Object getKey(ChatMessage item) {
                return item.id();
            }

            public int measureHeight(ChatMessage item, int width, Font font) {
                return messageHeight(ChatMessageList.this, item, width, font);
            }

            public Grid.View<ChatMessage> createView() {
                return new MessageView();
            }
        });
        this.addChild(scroll);
        setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
    }

    private static boolean system(ChatMessage item) {
        return item.sender().equals(ChatChannel.NONE);
    }

    private static UUID localPlayerId() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.player == null ? ChatChannel.NONE : minecraft.player.getUUID();
    }

    static int messageHeight(Control owner, ChatMessage item, int width, Font font) {
        int textHeight = Label.measureTextHeight(owner, font, item.text(), Math.max(1, system(item) ? width : width - 28),
                Label.Wrap.WORD, Label.Overflow.VISIBLE, Integer.MAX_VALUE);
        return system(item) ? textHeight : Math.max(24, textHeight + 14);
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
        return scroll.getScrollY() >= scroll.getMaxScrollY() - 2;
    }

    public int newMessages() {
        return newMessages;
    }

    public int activeViews() {
        return grid.getActiveViews();
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
        return getMinimumSize();
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
            scroll.setScroll(0, scroll.getMaxScrollY());
            follow = false;
        }
        if (atBottom()) newMessages = 0;
    }

    static final class MessageView extends Container implements Grid.View<ChatMessage> {
        final RichText text = new RichText();
        private final Avatar avatar = new Avatar().setSize(20);
        private final Label author = new Label().setThemeTypeVariation("LabelSecondary").setOverflow(Label.Overflow.ELLIPSIS);
        private final Supplier<UUID> viewer;
        private ChatMessage message;
        private boolean outgoing;

        MessageView() {
            this(ChatMessageList::localPlayerId);
        }

        MessageView(Supplier<UUID> viewer) {
            this.viewer = java.util.Objects.requireNonNull(viewer);
            this.addChild(avatar);
            this.addChild(author);
            this.addChild(text);
        }

        public void bind(ChatMessage message) {
            this.message = message;
            boolean system = system(message);
            outgoing = !system && message.sender().equals(viewer.get());
            Label.Horizontal alignment = system ? Label.Horizontal.CENTER
                    : outgoing ? Label.Horizontal.RIGHT : Label.Horizontal.LEFT;
            avatar.setVisible(!system);
            author.setVisible(!system);
            if (avatar.getVisible()) avatar.setPlayer(message.sender(), message.senderName());
            String time = TIME.format(Instant.ofEpochMilli(message.time()));
            String name = message.senderName();
            String playerInfo = outgoing ? time + "  " + name : name + "  " + time;
            author.setText(Component.literal(name.isEmpty() ? time : playerInfo));
            author.setAlignment(alignment, Label.Vertical.TOP);
            text.label.setText(message.text());
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
        protected void drawContent(DrawContext context, int screenX, int screenY) {
            DebugBridge.backend().paint(this, context, screenX, screenY);
            if (system(message)) drawCommandPart(context, screenX, screenY);
            else drawPlayerPart(context, screenX, screenY);
        }

        private void drawPlayerPart(DrawContext context, int screenX, int screenY) {
            if (avatar.getVisible()) avatar.draw(context, screenX, screenY);
            if (author.getVisible()) author.draw(context, screenX, screenY);
            if (text.getVisible()) text.draw(context, screenX, screenY);
        }

        private void drawCommandPart(DrawContext context, int screenX, int screenY) {
            if (text.getVisible()) text.draw(context, screenX, screenY);
        }

        @Override
        protected boolean pointerDown(int x, int y) {
            if (avatar.hit(x, y) && message != null && !outgoing && Minecraft.getInstance().player != null
                    && !message.sender().equals(Minecraft.getInstance().player.getUUID())) {
                ClientChatState.openDirect(message.sender().toString());
                return true;
            }
            return false;
        }

        public void draw(DrawContext context, Rect area, boolean hovered) {
            layout(area, context.getFont());
            draw(context, 0, 0);
        }
    }

    static final class RichText extends Container {
        final Label label = new Label().setWrap(Label.Wrap.WORD);
        private Label.Horizontal alignment = Label.Horizontal.LEFT;
        private Style hovered;

        RichText() {
            this.addChild(label);
        }

        void align(Label.Horizontal alignment) {
            this.alignment = alignment;
            label.setAlignment(alignment, Label.Vertical.TOP);
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
            float scale = label.getScale() * (label.getFontSize() / Label.DEFAULT_FONT_SIZE);
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
        protected void render(DrawContext context, int screenX, int screenY) {
            hovered = style(context.getFont(), context.getAbsMouseX(), context.getAbsMouseY(), getScreenBounds(screenX, screenY));
        }

        @Override
        public boolean hasTooltip() {
            return hovered != null && hovered.getHoverEvent() != null;
        }

        @Override
        public void renderTooltip(DrawContext context, int x, int y) {
            context.getGraphics().renderComponentHoverEffect(context.getFont(), hovered, x, y);
        }

        @Override
        protected boolean pointerDown(int x, int y) {
            var mc = Minecraft.getInstance();
            Style style = style(mc.font, x, y, getAbsoluteBounds());
            return style != null && style.getClickEvent() != null && mc.screen != null && mc.screen.handleComponentClicked(style);
        }
    }
}
