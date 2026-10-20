package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.Label;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;

public final class BoxWrapTest {
    public static void main(String[] args) {
        for (Box.Orientation orientation : Box.Orientation.values()) {
            boolean horizontal = orientation == Box.Orientation.HORIZONTAL;
            Box box = new Box().setOrientation(orientation).setGap(2);
            Control a = child(horizontal, 30, 10), b = child(horizontal, 30, 15), c = child(horizontal, 30, 8);
            box.addChild(a); box.addChild(b); box.addChild(c);
            Rect area = horizontal ? new Rect(0, 0, 62, 100) : new Rect(0, 0, 100, 62);
            box.layout(area, null);
            check((horizontal ? c.getBounds().x() : c.getBounds().y()) == 64, "No-wrap overflow");
            box.setWrap(true);
            Size measured = box.measureSize(null, new Size(area.width(), area.height()));
            box.layout(area, null);
            check((horizontal ? b.getBounds().x() : b.getBounds().y()) == 32, "Exact fit remains on line");
            check((horizontal ? c.getBounds().y() : c.getBounds().x()) == 17, "Wrap and inherited gap");
            check((horizontal ? measured.height() : measured.width()) == 25, "Measured lines equal arranged extent");
            box.setLineGap(7).layout(area, null);
            check((horizontal ? c.getBounds().y() : c.getBounds().x()) == 22, "Explicit line gap");
            box.setGap(3); check(box.getLineGap() == 7, "Independent override");
            box.clearLineGap(); check(box.getLineGap() == 3, "Restore dynamic inheritance");
            b.setVisible(false);
            box.layout(area, null);
            check((horizontal ? c.getBounds().y() : c.getBounds().x()) == 13, "Hidden items do not occupy a slot");
            box.layout(horizontal ? new Rect(0,0,0,100) : new Rect(0,0,100,0), null);
            check((horizontal ? a.getSize().width() : a.getSize().height()) == 30, "Zero available preserves minima");
            Size unbounded = box.measureSize(null, new Size(Integer.MAX_VALUE, Integer.MAX_VALUE));
            check((horizontal ? unbounded.width() : unbounded.height()) == 63, "Unbounded does not wrap");
        }
        Box weighted = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(2);
        for (int i=0; i<3; i++) {
            Control child = new Control().setMinimum(20, 10).setPreferred(30, 10)
                    .setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsStretchRatio(i == 0 ? 2 : 1);
            weighted.addChild(child);
        }
        weighted.layout(new Rect(0,0,75,60), null);
        check(weighted.getChild(0).getSize().width() == 49 && weighted.getChild(1).getSize().width() == 24, "Per-line ratio and stable remainder");
        check(weighted.getChild(2).getBounds().equals(new Rect(0,12,75,10)), "Last row expands independently");
        weighted.getChild(0).setMinimum(90,10);
        weighted.layout(new Rect(0,0,75,60),null);
        check(weighted.getChild(0).getSize().width()==90, "Oversize item overflows on its own row");
        check(weighted.getChild(1).getBounds().y()==12,"Following item starts a new row");
        Box preferred = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true);
        preferred.addChild(new Control().setMinimum(10,10).setPreferred(150,10));
        preferred.layout(new Rect(0,0,50,30),null);
        check(preferred.getChild(0).getSize().width()==50,"Preference is not a hard minimum");
        Box shrink = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true);
        Control responsive = new Control() {
            @Override public Size measureSize(Font ignored, Size available) { return new Size(20, available.width() < 30 ? 40 : 10); }
        };
        responsive.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND | SizeFlags.SIZE_SHRINK_END)
                .setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_BEGIN);
        shrink.addChild(responsive);
        check(shrink.measureSize(null,new Size(100,100)).height()==40,"Cross measurement uses actual shrunken main size");
        shrink.layout(new Rect(0,0,100,40),null);
        check(responsive.getBounds().equals(new Rect(80,0,20,40)),"Non-fill alignment and measured height agree");
        check(shrink.measureSize(null,new Size(Integer.MAX_VALUE,100)).width()==20,"Unbounded expansion stays natural");
        Font font = new Font(id -> null, false) {
            @Override public int width(String value) { return value.codePointCount(0,value.length())*6; }
            @Override public int width(FormattedText value) { return width(value.getString()); }
            @Override public int width(FormattedCharSequence value) {
                int[] length={0}; value.accept((i,s,c)->{length[0]++;return true;});return length[0]*6;
            }
        };
        Box text = new Box().setOrientation(Box.Orientation.HORIZONTAL).setWrap(true).setGap(2);
        Label label = new Label(Component.literal("中英文MixedText")).setWrap(Label.Wrap.CHARACTER);
        label.setMinimum(10,0); label.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        text.addChild(label);
        Size measured = text.measureSize(font,new Size(32,100));
        text.layout(new Rect(0,0,32,measured.height()),font);
        check(label.getSize().height()==measured.height() && measured.height()>10,"Remeasure wrapped text at its allocated width");
        Scroll scroll = new Scroll().setEmbedded(false).setContent(weighted);
        scroll.layout(new Rect(0,0,75,20),null);
        check(scroll.getVerticalVisible() && weighted.getChild(0).getSize().width()==90, "Scroll reflow preserves minima");
        weighted.setWrap(false); check(weighted.getLayoutDirty(),"Wrap invalidation");
        System.out.println("ORE_BOX_WRAP_PASS");
    }
    private static Control child(boolean horizontal,int main,int cross) {
        return new Control().setMinimum(horizontal?main:cross,horizontal?cross:main);
    }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
}

