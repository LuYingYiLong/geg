package io.github.luyingyilong.ore.control.display;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.LifecycleScheduler;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Size;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.control.input.Button;
import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.render.TexturePainter;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.IconTheme;
import io.github.luyingyilong.ore.theme.Theme;
import io.github.luyingyilong.ore.theme.ThemeDB;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.event.WindowInputs;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Image geometry, intrinsic minimums, stable parts and owner-scoped theme resolution. */
public final class TextureRectTest {
    private static final TextureRegion REGION=new TextureRegion(ResourceLocation.fromNamespaceAndPath("test","atlas"),10,8,2,3,4,2);
    public static void main(String[] args) {
        regions(); modes(); flipping(); sizing(); themes(); partsAndInput();
        System.out.println("ORE_TEXTURE_RECT_PASS");
    }
    private static TextureRect image() { return new TextureRect().setTexture(TextureSource.region(REGION)); }
    private static void regions() {
        check(REGION.getSubRegion(1,0,2,1).equals(new TextureRegion(REGION.getTexture(),10,8,3,3,2,1)),"Atlas sub-regions are relative and bounded");
        rejects(()->REGION.getSubRegion(3,0,2,1)); rejects(()->REGION.getSubRegion(0,0,0,1));
        rejects(()->TextureRegion.full(REGION.getTexture(),0,8));
        rejects(()->new TextureRegion(REGION.getTexture(),10,8,Integer.MAX_VALUE,0,1,1));
        rejects(()->new TextureRect().setOpacity(Float.NaN)); rejects(()->new TextureRect().setOpacity(1.01F));
        rejects(()->new TextureRect().setOpacity(-0.1F));
    }
    private static void modes() {
        TextureRect image=image(); Rect area=new Rect(10,20,20,20);
        check(image.getOpacity()==1 && image.getModulate()==0xFFFFFFFF && !image.getFlipHorizontal()
                && !image.getFlipVertical() && image.getExpandMode()==TextureRect.ExpandMode.KEEP_SIZE,"Image defaults");
        Map<TextureRect.StretchMode,TexturePainter.Quad> expected=Map.of(
                TextureRect.StretchMode.SCALE,new TexturePainter.Quad(10,20,20,20,2,3,6,5),
                TextureRect.StretchMode.KEEP,new TexturePainter.Quad(10,20,4,2,2,3,6,5),
                TextureRect.StretchMode.KEEP_CENTERED,new TexturePainter.Quad(18,29,4,2,2,3,6,5),
                TextureRect.StretchMode.KEEP_ASPECT,new TexturePainter.Quad(10,20,20,10,2,3,6,5),
                TextureRect.StretchMode.KEEP_ASPECT_CENTERED,new TexturePainter.Quad(10,25,20,10,2,3,6,5),
                TextureRect.StretchMode.KEEP_ASPECT_COVERED,new TexturePainter.Quad(10,20,20,20,3,3,5,5));
        for(var entry:expected.entrySet()) {
            image.setStretchMode(entry.getKey());
            check(image.getDrawQuads(area).equals(List.of(entry.getValue())),"Stretch mode "+entry.getKey());
            check(image.getDrawQuads(area)==image.getDrawQuads(area),"Static paint geometry is reused");
            var retained=image.getDrawQuads(area); image.setStretchMode(entry.getKey()).setFlipHorizontal(false).setFlipVertical(false);
            check(image.getDrawQuads(area)==retained,"Controlled refresh preserves cached geometry");
        }
        check(image.getDrawQuads(new Rect(0,0,0,10)).isEmpty(),"Zero-width targets do not draw");
        image.setTexture(null); check(image.getDrawQuads(area).isEmpty(),"Missing image is transparent");
        image.queueFree(); LifecycleScheduler.flush();
    }
    private static void flipping() {
        TextureRect image=image().setStretchMode(TextureRect.StretchMode.TILE);
        Rect area=new Rect(10,20,10,5); var normal=image.getDrawQuads(area);
        check(normal.size()==9 && normal.get(8).equals(new TexturePainter.Quad(18,24,2,1,2,3,4,4)),"Partial final tiles crop within the atlas region");
        image.setFlipHorizontal(true).setFlipVertical(true);
        var flipped=image.getDrawQuads(area);
        check(flipped.get(8).equals(new TexturePainter.Quad(18,24,2,1,6,5,4,4)),"Flip the original tile before clipping the final tile");
        for(var q:flipped) check(q.width()>0 && q.height()>0 && q.u0()>=2 && q.u0()<=6 && q.u1()>=2 && q.u1()<=6
                && q.v0()>=3 && q.v0()<=5 && q.v1()>=3 && q.v1()<=5,"Flipping never samples a neighbouring atlas image");
        image.setStretchMode(TextureRect.StretchMode.KEEP_ASPECT_COVERED);
        check(image.getDrawQuads(new Rect(0,0,20,20)).get(0).equals(new TexturePainter.Quad(0,0,20,20,5,5,3,3)),"Cover crop and double flip compose");
        image.queueFree(); LifecycleScheduler.flush();
    }
    private static void sizing() {
        TextureRect image=image(); Box row=new Box().setOrientation(Box.Orientation.HORIZONTAL);
        image.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL); row.addChild(image);
        Window window=new Window(true).setContent(row); window.layoutViewport(2,1,null);
        check(image.getMinimumSize().equals(new Size(4,2)) && image.getBounds().width()==4 && image.getBounds().height()==2,"KEEP_SIZE preserves image minimums even in an undersized container");
        image.setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE); window.layoutViewport(2,1,null);
        check(image.getMinimumSize().equals(new Size(0,0)) && image.getBounds().width()==2 && image.getBounds().height()==1,"IGNORE_SIZE removes only intrinsic minimums");
        image.setMinimum(6,5); image.setExpandMode(TextureRect.ExpandMode.KEEP_SIZE); image.setExpandMode(TextureRect.ExpandMode.IGNORE_SIZE);
        check(image.getMinimumSize().equals(new Size(6,5)),"Image policies do not overwrite user minimums");
        window.queueFree(); LifecycleScheduler.flush();
    }
    private static void themes() {
        var source=TextureSource.themed(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN);
        check(source==TextureSource.themed(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN),"Theme source descriptions are shared, not allocated by frame");
        TextureRect image=new TextureRect().setTexture(source);
        Control owner=new Control(); owner.ownPart("image",image);
        TextureRegion wider=TextureRegion.full(REGION.getTexture(),9,4);
        owner.setThemeOverride(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN,wider);
        check(image.getResolvedTextureRegion()==wider && image.getMinimumWidth()==9,"Owned images resolve owner overrides");
        Window window=new Window(true).setContent(owner); window.layoutViewport(40,20,null);
        owner.removeThemeOverride(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN);
        Theme theme=ThemeDB.getDefault().getTheme(ResourceLocation.fromNamespaceAndPath("test","image")); owner.setTheme(theme);
        theme.set(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN,wider); window.layoutViewport(40,20,null);
        int connections=ThemeDB.getDefault().changed().connectionCount();
        for(int i=0;i<100;i++) { image.getResolvedTextureRegion(); image.setTexture(source); }
        check(!owner.getLayoutDirty() && ThemeDB.getDefault().changed().connectionCount()==connections,"Normal refresh neither recreates subscriptions nor invalidates layout");
        image.setTexture(TextureSource.region(new TextureRegion(REGION.getTexture(),12,8,1,1,9,4)));
        check(!owner.getLayoutDirty(),"Same-size animation frames update paint without relayout");
        image.setTexture(source);
        theme.set(io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN,TextureRegion.full(REGION.getTexture(),12,6));
        check(image.getMinimumSize().equals(new Size(12,6)) && owner.getLayoutDirty(),"Resource changes update measurement");
        window.queueFree(); LifecycleScheduler.flush(); check(image.isDisposed(),"Owned images are freed with the owner");
    }
    private static void partsAndInput() {
        Button button=io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { }); var image=button.getIconControl(); var trailing=button.getTrailingIconControl();
        TextureSource source=TextureSource.region(REGION);
        for(int i=0;i<100;i++) { button.setIcon(source); button.setTrailingIcon(source); }
        check(image==button.getIconControl() && trailing==button.getTrailingIconControl(),"Button keeps both image nodes across rebinding");
        Button other=io.github.luyingyilong.ore.control.input.ButtonTestAccess.create(cue -> { }).setIcon(source);
        check(other.getIconControl()!=image && other.getIcon()==source,"Only source data is shared between buttons");
        Control root=new Control(); button.setRequestedSize(20,20); root.addChild(button);
        TextureRect decoration=image(); decoration.setRequestedSize(20,20); root.addChild(decoration);
        Window window=new Window(true).setContent(root); window.layoutViewport(40,30,null);
        int[] clicks={0}; button.pressed().connect(button,()->clicks[0]++);
        WindowInputs.press(window,5,5,null); WindowInputs.release(window,5,5);
        check(clicks[0]==1 && !decoration.getFocusable(),"Decorative images never steal a business click or focus");
        window.queueFree(); other.queueFree(); LifecycleScheduler.flush();
        check(image.isDisposed() && trailing.isDisposed() && decoration.isDisposed(),"All image parts clean up exactly once");
        rejects(()->image.setTexture(source));
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void rejects(Runnable task) {
        try { task.run(); } catch(IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected invalid image operation rejection");
    }
}
