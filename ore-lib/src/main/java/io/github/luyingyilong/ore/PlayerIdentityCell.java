package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.theme.ThemeInsets;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.render.DrawContext;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Retained, non-interactive identity cell; the table keeps its existing action-column focus.
 */
public final class PlayerIdentityCell<T> implements Table.Cell<T> {
    private static final int AVATAR_SIZE = 16;
    private static final int GAP = 4;
    private final Function<T, UUID> playerId;
    private final Function<T, String> playerName;

    public PlayerIdentityCell(Function<T, UUID> playerId, Function<T, String> playerName) {
        this.playerId = Objects.requireNonNull(playerId);
        this.playerName = Objects.requireNonNull(playerName);
    }

    private static ThemeInsets padding(Control owner) {
        return new ThemeInsets(owner.getThemeItem(Table.CELL_PADDING_LEFT), owner.getThemeItem(Table.CELL_PADDING_TOP),
                owner.getThemeItem(Table.CELL_PADDING_RIGHT), owner.getThemeItem(Table.CELL_PADDING_BOTTOM));
    }

    private static Label nameLabel() {
        return new Label().setWrap(Label.Wrap.CHARACTER).setMaxLines(2)
                .setOverflow(Label.Overflow.ELLIPSIS).setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
    }

    @Override
    public int measureHeight(Control owner, Font font, T row, int width) {
        int textHeight = font == null ? 0 : Label.measureTextHeight(owner, font, Component.literal(playerName.apply(row)),
                Math.max(1, width - padding(owner).getHorizontal() - AVATAR_SIZE - GAP), Label.Wrap.CHARACTER, Label.Overflow.ELLIPSIS, 2);
        return Math.max(AVATAR_SIZE, textHeight) + padding(owner).getVertical();
    }

    @Override
    public Control createControl() {
        return new Identity();
    }

    @Override
    public void bindControl(Control control, T row) {
        Identity identity = (Identity) control;
        String name = playerName.apply(row);
        identity.avatar.setPlayer(playerId.apply(row), name);
        identity.name.setText(Component.literal(name));
    }

    @Override
    public void draw(DrawContext context, T row, Rect bounds) {
        // The retained subtree is rendered and clipped by Table.RowView.
    }

    private static final class Identity extends Container {
        private final Avatar avatar = new Avatar().setSize(AVATAR_SIZE);
        private final Label name = nameLabel();
        private final Padding content;

        private Identity() {
            name.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
            var identityRow = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(GAP);
            identityRow.addChild(avatar);
            identityRow.addChild(name);
            content = new Padding(Padding.Insets.all(0)).setChild(identityRow);
            this.addChild(content);
        }

        @Override
        public void layout(Rect bounds, Font font) {
            if (!isAlive()) return;
            try (var lifecyclePhase = io.github.luyingyilong.ore.signal.DispatchGuard.enter()) {
                var insets = padding(this);
                content.setInsets(new Padding.Insets(insets.top(), insets.right(), insets.bottom(), insets.left()));
                super.layout(bounds, font);
                fitChild(content, new Rect(0, 0, bounds.width(), bounds.height()), font);

            }
        }
    }
}
