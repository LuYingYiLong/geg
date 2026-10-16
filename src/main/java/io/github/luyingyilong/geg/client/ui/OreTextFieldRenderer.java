package io.github.luyingyilong.geg.client.ui;

import brachy.modularui.widgets.textfield.TextFieldHandler;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import brachy.modularui.utils.Point;
import brachy.modularui.utils.PointF;
import brachy.modularui.widget.sizer.Area;
import brachy.modularui.screen.viewport.GuiContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Styled measured lines also drive MUI caret hit testing, selection and scrolling. */
final class OreTextFieldRenderer extends TextFieldRenderer {
    private InputPreedit preedit = InputPreedit.EMPTY;
    private boolean drawingPreedit;

    OreTextFieldRenderer(TextFieldHandler handler) {
        super(handler);
    }

    void preedit(InputPreedit preedit) { this.preedit = preedit; }

    private InputPreedit.Presentation presentation() {
        return preedit.present(handler.getText().get(0), handler.getStartCursor().x, handler.getEndCursor().x);
    }

    private float preeditScroll(PointF caret) {
        return maxWidth > 0 ? Math.max(0, caret.x - x - maxWidth + 1) : 0;
    }

    private PointF preeditPosition(List<Line> measured, String text, int cursor) {
        // MUI's formatted substring counts code points, while IMM uses UTF-16 offsets.
        return getPosOf(measured, new Point(text.codePointCount(0, cursor), 0));
    }

    PointF caretPosition() {
        if (!preedit.active()) return getPosOf(measureStringLines(handler.getText()), handler.getMainCursor());
        var display = presentation();
        PointF caret = preeditPosition(measureStringLines(List.of(display.text())), display.text(), display.cursor());
        caret.x -= preeditScroll(caret);
        return caret;
    }

    @Override protected void drawMeasuredLines(GuiGraphics graphics, List<Line> lines) {
        if (!preedit.active()) {
            super.drawMeasuredLines(graphics, lines);
            return;
        }
        var display = presentation();
        List<Line> measured = measureStringLines(List.of(display.text()));
        PointF start = preeditPosition(measured, display.text(), display.start());
        PointF end = preeditPosition(measured, display.text(), display.end());
        PointF caret = preeditPosition(measured, display.text(), display.cursor());
        boolean previousCursor = renderCursor;
        int previousColor = color;
        renderCursor = false;
        color = cursorColor; // Empty fields must not render preedit with the placeholder's dim color.
        drawingPreedit = true;
        graphics.pose().pushPose();
        graphics.pose().translate(-preeditScroll(caret), 0, 0);
        try {
            super.drawMeasuredLines(graphics, measured);
            if (!simulate) {
                int bottom = (int) Math.ceil(start.y + getFontHeight()) - 1;
                graphics.fill((int) start.x, bottom, Math.max((int) start.x + 1, (int) Math.ceil(end.x)),
                        bottom + 1, cursorColor);
                graphics.fill((int) caret.x, (int) caret.y, (int) caret.x + 1, bottom, cursorColor);
            }
        } finally {
            graphics.pose().popPose();
            renderCursor = previousCursor;
            color = previousColor;
            drawingPreedit = false;
        }
    }

    @Override protected void drawMarked(GuiGraphics graphics, List<Line> lines) {
        if (!drawingPreedit) super.drawMarked(graphics, lines);
    }

    @Override public void drawScrolling(GuiGraphics graphics, Line line, float offset, Area area, GuiContext context) {
        if (!preedit.active()) {
            super.drawScrolling(graphics, line, offset, area, context);
            return;
        }
        context.getStencil().push(x, -500.0F, Math.max(0, (int) maxWidth), 1000.0F);
        try { drawMeasuredLines(graphics, List.of(line)); }
        finally { context.getStencil().pop(); }
    }

    @Override public List<Line> measureLines(List<Component> lines) {
        return super.measureLines(lines.stream().map(OreFonts::text).toList());
    }

    @Override public boolean wouldFit(List<String> lines, boolean checkWidth) {
        if (maxHeight > 0 && maxHeight < lines.size() * getFontHeight() - scale) return false;
        if (checkWidth && maxWidth > 0)
            for (Line line : measureStringLines(lines))
                if (line.width() > maxWidth) return false;
        return true;
    }
}
