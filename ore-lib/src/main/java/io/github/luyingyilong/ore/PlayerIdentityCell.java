package io.github.luyingyilong.ore;

import brachy.modularui.screen.viewport.ModularGuiContext;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.theme.OreTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Retained, non-interactive identity cell; the table keeps its existing action-column focus. */
public final class PlayerIdentityCell<T> implements Table.Cell<T> {
    private static final int AVATAR_SIZE = 16;
    private static final int GAP = 4;
    private static final int INSET_X = 4;
    private static final int INSET_Y = 2;
    private final Function<T, UUID> playerId;
    private final Function<T, String> playerName;

    public PlayerIdentityCell(Function<T, UUID> playerId, Function<T, String> playerName) {
        this.playerId = Objects.requireNonNull(playerId);
        this.playerName = Objects.requireNonNull(playerName);
    }

    private static Label nameLabel() {
        return new Label().setColor(OreTheme.TEXT_WHITE).setWrap(Label.Wrap.CHARACTER).setMaxLines(2)
                .setOverflow(Label.Overflow.ELLIPSIS).setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    }

    @Override public int measureHeight(Font font, T row, int width) {
        int textHeight = font == null ? 0 : Label.measureTextHeight(font, Component.literal(playerName.apply(row)),
                Math.max(1, width - 2 * INSET_X - AVATAR_SIZE - GAP), Label.Wrap.CHARACTER, Label.Overflow.ELLIPSIS, 2);
        return Math.max(AVATAR_SIZE, textHeight) + 2 * INSET_Y;
    }

    @Override public Control createControl() { return new Identity(); }

    @Override public void bindControl(Control control, T row) {
        Identity identity = (Identity) control;
        String name = playerName.apply(row);
        identity.avatar.setPlayer(playerId.apply(row), name);
        identity.name.setText(Component.literal(name));
    }

    @Override public void draw(ModularGuiContext context, T row, Rect bounds) {
        // The retained subtree is rendered and clipped by Table.RowView.
    }

    private static final class Identity extends Container {
        private final Avatar avatar = new Avatar().setSize(AVATAR_SIZE);
        private final Label name = nameLabel();
        private final Padding content;

        private Identity() {
            name.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
            content = new Padding(new Padding.Insets(INSET_Y, INSET_X, INSET_Y, INSET_X))
                    .setChild(new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(GAP).add(avatar).add(name));
            addChild(content);
        }

        @Override public void layout(Rect bounds, Font font) {
        if (!isAlive()) return;
        try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
            super.layout(bounds, font);
            fitChild(content, new Rect(0, 0, bounds.width(), bounds.height()), font);

        }
    }
    }
}
