package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class Input extends TextFieldWidget {
    private final float depth;
    private Button.Size controlSize = Button.Size.MEDIUM;
    private boolean inputVisible = true;
    private Component placeholder = Component.empty();

    public Input() {
        this(0.0F);
    }

    public Input(float depth) {
        this.depth = depth;
    }

    public Input controlSize(Button.Size size) {
        controlSize = java.util.Objects.requireNonNull(size);
        height(size.height());
        return this;
    }

    public Button.Size controlSize() {
        return controlSize;
    }

    public Input placeholder(Component placeholder) {
        this.placeholder = java.util.Objects.requireNonNull(placeholder);
        return this;
    }

    public void setInputVisible(boolean visible) {
        inputVisible = visible;
        setEnabled(visible);
    }

    @Override
    public void preDraw(ModularGuiContext context, boolean drawText) {
        if (drawText) {
            if (inputVisible) {
                withDepth(context, () -> super.preDraw(context, true));
            }
        } else {
            super.preDraw(context, false);
        }
    }

    @Override
    public void drawBackground(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        // The parent canvas may draw after the widget background pass.
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (inputVisible) {
            withDepth(context, () -> {
                WidgetThemeEntry<WidgetTheme> inputEntry = context.getTheme()
                        .getWidgetTheme(OreTheme.INPUT_BOX);
                if (inputEntry != null) {
                    WidgetTheme inputTheme = inputEntry.theme();
                    IDrawable background = inputTheme.getBackground();
                    if (background != null) {
                        background.drawAtZero(context, getArea(), inputTheme);
                    }
                }
                super.draw(context, theme);
                if (!isFocused() && getText().isEmpty() && !placeholder.getString().isEmpty()) {
                    Label.draw(context, placeholder, new Rect(getArea().x + 4, getArea().y,
                                    Math.max(0, getArea().width - 8), getArea().height),
                            0xFF858C94, Label.Overflow.CLIP,
                            Label.Horizontal.LEFT, Label.Vertical.CENTER);
                }
            });
        }
    }

    @Override
    public void drawOverlay(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        if (inputVisible) {
            withDepth(context, () -> super.drawOverlay(context, theme));
        }
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        if (inputVisible) {
            withDepth(context, () -> super.drawForeground(context));
        }
    }

    private void withDepth(ModularGuiContext context, Runnable draw) {
        GuiGraphics graphics = context.getGraphics();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, depth);
        try {
            draw.run();
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }
}
