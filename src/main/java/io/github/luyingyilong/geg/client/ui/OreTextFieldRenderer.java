package io.github.luyingyilong.geg.client.ui;

import io.github.luyingyilong.geg.client.ui.util.OreFonts;
import io.github.luyingyilong.geg.client.ui.util.TextLines;
import io.github.luyingyilong.geg.client.ui.util.WrappedText;

import brachy.modularui.widgets.textfield.TextFieldHandler;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import brachy.modularui.utils.Point;
import brachy.modularui.utils.PointF;
import brachy.modularui.widget.sizer.Area;
import brachy.modularui.screen.viewport.GuiContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Styled measured lines also drive MUI caret hit testing, selection and scrolling. */
final class OreTextFieldRenderer extends TextFieldRenderer {
    private InputPreedit preedit = InputPreedit.EMPTY;
    private boolean drawingPreedit;
    private boolean multiline;
    private List<String> wrappedSource = List.of();
    private float wrappedWidth = -1, wrappedScale = -1;
    private int fontRevision = -1;
    private WrappedText wrapped;
    private WrappedText affinityLayout;
    private Point affinityCursor;
    private int affinityRow;
    void multiline(boolean multiline) { this.multiline = multiline; }

    OreTextFieldRenderer(TextFieldHandler handler) {
        super(handler);
    }

    void preedit(InputPreedit preedit) { this.preedit = preedit; }

    private InputPreedit.Presentation presentation() {
        return preedit.present(String.join("\n", handler.getText()), offset(handler.getStartCursor()), offset(handler.getEndCursor()));
    }

    private int offset(Point point) {
        return TextLines.offset(handler.getText(), point.x, point.y);
    }

    private float preeditScroll(PointF caret) {
        return !multiline && maxWidth > 0 ? Math.max(0, caret.x - x - maxWidth + 1) : 0;
    }

    private PointF preeditPosition(List<Line> measured, String text, int cursor) {
        // MUI's formatted substring counts code points, while IMM uses UTF-16 offsets.
        var position = TextLines.position(text, cursor);
        return getPosOf(measured, new Point(position.column(), position.line()));
    }

    PointF caretPosition() {
        if (multiline) {
            if (!preedit.active()) return wrappedPosition(wrap(handler.getText()), handler.getMainCursor());
            var display = presentation();
            return wrappedPosition(wrap(TextLines.split(display.text())), sourcePosition(display.text(), display.cursor()));
        }
        if (!preedit.active()) return getPosOf(measureStringLines(handler.getText()), handler.getMainCursor());
        var display = presentation();
        PointF caret = preeditPosition(measureStringLines(TextLines.split(display.text())), display.text(), display.cursor());
        caret.x -= preeditScroll(caret);
        return caret;
    }

    @Override protected void drawMeasuredLines(GuiGraphics graphics, List<Line> lines) {
        if (multiline) {
            drawMultiline(graphics);
            return;
        }
        if (!preedit.active()) {
            super.drawMeasuredLines(graphics, lines);
            return;
        }
        var display = presentation();
        List<Line> measured = measureStringLines(TextLines.split(display.text()));
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
        if (multiline) return wrap(lines.stream().map(Component::getString).toList()).rows().stream()
                .map(row -> new Line(OreFonts.text(Component.literal(row.text())).getVisualOrderText(), row.width())).toList();
        return super.measureLines(lines.stream().map(OreFonts::text).toList());
    }

    private WrappedText wrap(List<String> source) {
        if (wrapped == null || !wrappedSource.equals(source) || wrappedWidth != maxWidth
                || wrappedScale != scale || fontRevision != OreFonts.revision()) {
            wrappedSource = List.copyOf(source);
            wrappedWidth = maxWidth;
            wrappedScale = scale;
            fontRevision = OreFonts.revision();
            Style style = Style.EMPTY.withFont(OreFonts.BODY);
            wrapped = new WrappedText(source, Math.max(1, maxWidth - 1),
                    cp -> getFont().getSplitter().stringWidth(FormattedCharSequence.codepoint(cp, style)) * scale);
        }
        return wrapped;
    }

    private static Point sourcePosition(String text, int offset) {
        int start = text.lastIndexOf('\n', Math.max(-1, offset - 1)) + 1;
        return new Point(offset - start, TextLines.position(text, offset).line());
    }

    private PointF wrappedPosition(WrappedText layout, Point point) {
        WrappedText.Position position = visualPosition(layout, point);
        var row = layout.rows().get(position.row());
        return new PointF(getStartX(row.width()) + position.x(),
                getStartYOfLines(layout.rows().size()) + position.row() * getFontHeight());
    }

    private WrappedText.Position visualPosition(WrappedText layout, Point point) {
        // A soft line's end and the next line's start share a source offset. Preserve the
        // clicked/navigated side of that boundary until the source or cursor changes.
        if (!preedit.active() && layout == affinityLayout && point.x == affinityCursor.x && point.y == affinityCursor.y)
            return new WrappedText.Position(affinityRow, layout.xAt(layout.rows().get(affinityRow), point.x));
        return layout.locate(point.y, point.x);
    }

    private Point rememberVisualCursor(WrappedText layout, int row, int column, int line) {
        affinityLayout = layout;
        affinityRow = row;
        affinityCursor = new Point(column, line);
        return new Point(column, line);
    }

    @Override public PointF getPosOf(List<Line> lines, Point point) {
        return multiline ? wrappedPosition(wrap(handler.getText()), point) : super.getPosOf(lines, point);
    }

    @Override public Point getCursorPos(List<String> lines, int mouseX, int mouseY) {
        if (!multiline) return super.getCursorPos(lines, mouseX, mouseY);
        WrappedText layout = wrap(lines);
        int row = Math.max(0, Math.min(layout.rows().size() - 1,
                (int) Math.floor((mouseY - getStartYOfLines(layout.rows().size())) / getFontHeight())));
        var cursor = layout.hit(row, mouseX - getStartX(layout.rows().get(row).width()));
        return rememberVisualCursor(layout, row, cursor.column(), cursor.line());
    }

    Point moveVisual(int rows, boolean edge, boolean end) {
        WrappedText layout = wrap(handler.getText());
        var position = visualPosition(layout, handler.getMainCursor());
        int index = Math.max(0, Math.min(layout.rows().size() - 1, position.row() + rows));
        var row = layout.rows().get(index);
        if (edge) return rememberVisualCursor(layout, index, end ? row.end() : row.start(), row.line());
        var cursor = layout.hit(index, position.x());
        return rememberVisualCursor(layout, index, cursor.column(), cursor.line());
    }

    float visualHeight() {
        List<String> source = preedit.active() ? TextLines.split(presentation().text()) : handler.getText();
        return wrap(source).rows().size() * getFontHeight();
    }

    private void drawMultiline(GuiGraphics graphics) {
        var display = preedit.active() ? presentation() : null;
        WrappedText layout = wrap(display == null ? handler.getText() : TextLines.split(display.text()));
        int top = getStartYOfLines(layout.rows().size());
        if (!simulate) {
            if (display != null) {
                drawRange(graphics, layout, sourcePosition(display.text(), display.start()),
                        sourcePosition(display.text(), display.end()), true);
            } else if (handler.hasTextMarked()) {
                drawRange(graphics, layout, handler.getStartCursor(), handler.getEndCursor(), false);
            }
            for (int index = 0; index < layout.rows().size(); index++) {
                var row = layout.rows().get(index);
                draw(graphics, OreFonts.text(Component.literal(row.text())).getVisualOrderText(),
                        getStartX(row.width()), top + index * getFontHeight());
            }
            if (display != null || renderCursor) {
                PointF caret = wrappedPosition(layout, display == null ? handler.getMainCursor()
                        : sourcePosition(display.text(), display.cursor()));
                graphics.fill((int) caret.x, (int) caret.y, (int) caret.x + 1,
                        (int) Math.ceil(caret.y + getFontHeight()), cursorColor);
            }
        }
        lastWidth = Math.max(0, layout.rows().stream().map(WrappedText.Row::width).max(Float::compare).orElse(0.0F) - scale);
        lastHeight = Math.max(0, layout.rows().size() * getFontHeight() - scale);
    }

    private void drawRange(GuiGraphics graphics, WrappedText layout, Point start, Point end, boolean underline) {
        int top = getStartYOfLines(layout.rows().size());
        for (int index = 0; index < layout.rows().size(); index++) {
            var row = layout.rows().get(index);
            if (row.line() < start.y || row.line() > end.y) continue;
            int from = row.line() == start.y ? Math.max(row.start(), start.x) : row.start();
            int to = row.line() == end.y ? Math.min(row.end(), end.x) : row.end();
            boolean newline = !underline && row.line() < end.y
                    && (index + 1 == layout.rows().size() || layout.rows().get(index + 1).line() != row.line());
            if (to <= from && !newline) continue;
            int left = (int) Math.floor(getStartX(row.width()) + layout.xAt(row, from));
            int right = (int) Math.ceil(getStartX(row.width()) + layout.xAt(row, to) + (newline ? scale : 0));
            int bottom = (int) Math.ceil(top + (index + 1) * getFontHeight());
            graphics.fill(left, underline ? bottom - 1 : (int) (top + index * getFontHeight()),
                    Math.max(left + 1, right), bottom, underline ? cursorColor : markedColor);
        }
    }

    @Override public boolean wouldFit(List<String> lines, boolean checkWidth) {
        if (!multiline && maxHeight > 0 && maxHeight < lines.size() * getFontHeight() - scale) return false;
        if (checkWidth && maxWidth > 0)
            for (Line line : measureStringLines(lines))
                if (line.width() > maxWidth) return false;
        return true;
    }
}
