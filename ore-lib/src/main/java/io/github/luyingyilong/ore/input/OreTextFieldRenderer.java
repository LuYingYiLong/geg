package io.github.luyingyilong.ore.input;

import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.utils.Point;
import brachy.modularui.utils.PointF;
import brachy.modularui.widget.sizer.Area;
import brachy.modularui.widgets.textfield.TextFieldHandler;
import brachy.modularui.widgets.textfield.TextFieldRenderer;
import io.github.luyingyilong.ore.debug.DebugBridge;
import io.github.luyingyilong.ore.text.*;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.ore.util.TextLines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/** MUI owns the document; every visual coordinate comes from the same immutable layout. */
final class OreTextFieldRenderer extends TextFieldRenderer {
    private final Input owner;
    private InputPreedit preedit = InputPreedit.EMPTY;
    private boolean multiline;
    private TextLayout affinityLayout;
    private int affinityOffset = -1;
    private int affinityRow = -1;
    private float affinityX;

    OreTextFieldRenderer(TextFieldHandler handler, Input owner) { super(handler); this.owner = owner; }
    void multiline(boolean value) { multiline = value; }
    void preedit(InputPreedit value) { preedit = value; }
    private FontMetricsPolicy policy() { return owner.resolveTheme(OreTheme.FONT_METRICS); }
    @Override public float getFontHeight() { return OreText.metrics(owner.getThemeFont(), 8 * scale, policy()).lineHeight(); }
    @Override protected int getStartYOfLines(int count) { return y; }
    float textWidth() { return Math.max(1, maxWidth - 1); }
    private int offset(Point point) { return TextLines.offset(handler.getText(), point.x, point.y); }
    private TextLayout layout(List<String> source) { return layout(String.join("\n", source)); }
    private TextLayout layout(String text) {
        return OreText.layout(Component.literal(text), owner.getThemeFont(), 8 * scale,
                multiline ? textWidth() : Float.MAX_VALUE, multiline ? TextLayoutEngine.Wrap.WORD : TextLayoutEngine.Wrap.NONE,
                Integer.MAX_VALUE, false, policy(), getFont());
    }
    private InputPreedit.Presentation presentation() {
        return preedit.present(String.join("\n", handler.getText()), offset(handler.getStartCursor()), offset(handler.getEndCursor()));
    }
    private TextLayout.Position locate(TextLayout layout, int offset) {
        return layout.getCaret(offset, layout == affinityLayout && offset == affinityOffset ? affinityRow : -1, affinityX);
    }
    private Point remember(TextLayout layout, int row, int offset) {
        return remember(layout, row, new TextLayout.Stop(offset, layout.getLines().get(row).getX(offset, false)));
    }
    private Point remember(TextLayout layout, int row, TextLayout.Stop stop) {
        int offset = stop.offset();
        affinityLayout = layout; affinityRow = row; affinityOffset = offset; affinityX = stop.x();
        var source = TextLines.position(layout.getSource().text(), offset);
        return new Point(source.column(), source.line());
    }
    private PointF position(TextLayout layout, int offset) {
        var caret = locate(layout, offset);
        return new PointF(getStartX(layout.getLines().get(caret.line()).width()) + caret.x(), y + caret.y());
    }
    private float compositionScroll(TextLayout layout, int cursor) {
        return !multiline && maxWidth > 0 ? Math.max(0, position(layout, cursor).x - x - maxWidth + 1) : 0;
    }
    PointF caretPosition() {
        var display = preedit.active() ? presentation() : null;
        TextLayout layout = layout(display == null ? String.join("\n", handler.getText()) : display.text());
        PointF result = position(layout, display == null ? offset(handler.getMainCursor()) : display.cursor());
        if (display != null) result.x -= compositionScroll(layout, display.cursor());
        return result;
    }
    @Override public List<Line> measureLines(List<Component> source) {
        return layout(source.stream().map(Component::getString).toList()).getLines().stream()
                .map(row -> new Line(row.text().sequence(), row.width())).toList();
    }
    @Override public PointF getPosOf(List<Line> lines, Point point) { return position(layout(handler.getText()), offset(point)); }
    @Override public Point getCursorPos(List<String> source, int mouseX, int mouseY) {
        TextLayout layout = layout(source);
        int row = multiline ? Math.max(0, Math.min(layout.getLines().size() - 1,
                (int)Math.floor((mouseY - y) / getFontHeight()))) : 0;
        var line = layout.getLines().get(row);
        return remember(layout, row, line.hitStop(mouseX - getStartX(line.width())));
    }
    Point moveVisual(int rows, boolean edge, boolean end) {
        TextLayout layout = layout(handler.getText());
        var caret = locate(layout, offset(handler.getMainCursor()));
        int row = Math.max(0, Math.min(layout.getLines().size() - 1, caret.line() + rows));
        var line = layout.getLines().get(row);
        return remember(layout, row, edge ? line.hitStop(end ? Float.MAX_VALUE : -Float.MAX_VALUE) : line.hitStop(caret.x()));
    }
    Point moveHorizontal(boolean right) {
        TextLayout layout = layout(handler.getText());
        var caret = locate(layout, offset(handler.getMainCursor()));
        var stops = layout.getLines().get(caret.line()).stops().stream()
                .sorted(java.util.Comparator.comparing(TextLayout.Stop::x)).toList();
        TextLayout.Stop best = null;
        for (var stop : stops) if (right ? stop.x() > caret.x() + .001F : stop.x() < caret.x() - .001F) {
            if (best == null || (right ? stop.x() < best.x() : stop.x() > best.x())) best = stop;
        }
        if (best != null) return remember(layout, caret.line(), best);
        int next = caret.line() + (right ? 1 : -1);
        if (next < 0 || next >= layout.getLines().size()) return remember(layout, caret.line(), caret.offset());
        return remember(layout, next, layout.getLines().get(next).hitStop(right ? -Float.MAX_VALUE : Float.MAX_VALUE));
    }
    float visualHeight() { return layout(preedit.active() ? presentation().text() : String.join("\n", handler.getText())).getHeight(); }
    @Override protected void drawMeasuredLines(GuiGraphics graphics, List<Line> ignored) {
        var display = preedit.active() ? presentation() : null;
        TextLayout layout = layout(display == null ? String.join("\n", handler.getText()) : display.text());
        lastWidth = layout.getWidth(); lastHeight = layout.getHeight();
        if (simulate || graphics == null) return;
        graphics.pose().pushPose();
        try {
            if (display != null) graphics.pose().translate(-compositionScroll(layout, display.cursor()), 0, 0);
            if (display == null && handler.hasTextMarked()) drawRange(graphics, layout, offset(handler.getStartCursor()), offset(handler.getEndCursor()), false);
            for (int row = 0; row < layout.getLines().size(); row++) {
                float left = getStartX(layout.getLines().get(row).width()), top = y + row * getFontHeight();
                TextRenderer.draw(graphics, layout.getLineLayout(row), left, top, display == null ? color : cursorColor, shadow);
                if (DebugBridge.active() && owner.debugControl() != null) {
                    var metrics = layout.getMetrics();
                    DebugBridge.backend().text(owner.debugControl(), graphics, owner.inputMethodBounds(), left, top,
                            layout.getLines().get(row).width(), getFontHeight(), top, top + metrics.ascent(),
                            top + metrics.ascent() + metrics.opticalTop(), top + metrics.ascent() + metrics.opticalBottom(),
                            8 * scale, layout.getDiagnostics(), 0);
                }
            }
            if (display != null) drawRange(graphics, layout, display.start(), display.end(), true);
            if (display != null || renderCursor) drawCaret(graphics, position(layout, display == null ? offset(handler.getMainCursor()) : display.cursor()));
        } finally { graphics.pose().popPose(); }
    }
    @Override public void drawScrolling(GuiGraphics graphics, Line line, float scroll, Area area, GuiContext context) {
        context.getStencil().push(x, -500F, Math.max(0, (int)maxWidth), 1000F);
        graphics.pose().pushPose();
        try {
            if (!preedit.active()) graphics.pose().translate(-Math.max(0, line.width() - maxWidth) * scroll, 0, 0);
            drawMeasuredLines(graphics, List.of(line));
        } finally { graphics.pose().popPose(); context.getStencil().pop(); }
    }
    private void drawRange(GuiGraphics graphics, TextLayout layout, int start, int end, boolean underline) {
        graphics.flush();
        for (var rect : layout.getSelection(start, end)) {
            int row = (int)(rect.y() / getFontHeight());
            float left = getStartX(layout.getLines().get(row).width()) + rect.x();
            float top = y + rect.y() + (underline ? rect.height() - 1 : 0);
            brachy.modularui.drawable.GuiDraw.drawRect(graphics, left, top, rect.width(), underline ? 1 : rect.height(), underline ? cursorColor : markedColor);
        }
    }
    private void drawCaret(GuiGraphics graphics, PointF point) {
        var caret = InputGeometry.caret(point.x, point.y, scale, getFontHeight());
        graphics.flush();
        brachy.modularui.drawable.GuiDraw.drawRect(graphics, caret.x(), caret.y(), caret.width(), caret.height(), cursorColor);
    }
    @Override public boolean wouldFit(List<String> source, boolean checkWidth) {
        TextLayout layout = layout(source);
        return (multiline || maxHeight <= 0 || layout.getHeight() - scale <= maxHeight)
                && (!checkWidth || maxWidth <= 0 || layout.getWidth() <= maxWidth);
    }
}
