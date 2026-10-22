package io.github.luyingyilong.ore.gui;

import io.github.luyingyilong.ore.control.base.Container;
import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Stable mount boundary: host ownership survives replacing the XML content root.
 */
final class UiMount extends Container {
    Runnable afterLayout = () -> {
    };

    UiMount() {
        establishIdScope();
        setDebugName("XML document");
    }

    void transact(Runnable action) {
        transactChildren(action);
    }

    void setContent(Control content) {
        updateChildren(content == null ? List.of() : List.of(content));
    }

    @Override
    protected void validateChildren(List<Control> children) {
        if (!isUpdatingChildren() || children.size() > 1)
            throw new IllegalStateException("UiDocument owns its content slot");
    }

    @Override
    public Size measureSize(Font font, Size available) {
        return getChildCount() == 0 ? getMinimumSize() : getChild(0).desiredSize(font, available);
    }

    @Override
    public int measureWidth(Font font, int available) {
        return measureSize(font, new Size(available, Integer.MAX_VALUE)).width();
    }

    @Override
    public int measureHeight(Font font, int available) {
        return measureSize(font, new Size(Integer.MAX_VALUE, available)).height();
    }

    @Override
    public int getMinimumWidth() {
        return Math.max(super.getMinimumWidth(), getChildCount() == 0 ? 0 : getChild(0).getMinimumWidth());
    }

    @Override
    public int getMinimumHeight() {
        return Math.max(super.getMinimumHeight(), getChildCount() == 0 ? 0 : getChild(0).getMinimumHeight());
    }

    @Override
    public void layout(Rect bounds, Font font) {
        super.layout(bounds, font);
        if (getChildCount() != 0) fitChild(getChild(0), new Rect(0, 0, bounds.width(), bounds.height()), font);
        afterLayout.run();
    }
}
