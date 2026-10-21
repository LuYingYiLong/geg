package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TexturePainter;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.control.base.Rect;
import io.github.luyingyilong.ore.control.base.Offset;
import java.util.*;

/** Independent source borders. Destination borders shrink proportionally for tiny rectangles. */
public record StyleBoxTexture(TextureRegion getTextureRegion, ThemeInsets textureBorders, ThemeInsets getContentMargin,
        ThemeInsets getExpandMargin, AxisMode horizontalMode, AxisMode verticalMode, boolean drawCenter,
        float opacity, ThemeInsets getDrawInset, Offset getContentOffset) implements StyleBox {
    public enum AxisMode { STRETCH, TILE }
    public StyleBoxTexture(TextureRegion region, ThemeInsets textureBorders, ThemeInsets contentMargin, ThemeInsets expandMargin,
                           AxisMode horizontalMode, AxisMode verticalMode, boolean drawCenter, float opacity) {
        this(region, textureBorders, contentMargin, expandMargin,
                horizontalMode, verticalMode, drawCenter, opacity, ThemeInsets.ZERO, Offset.ZERO);
    }
    public StyleBoxTexture {
        Objects.requireNonNull(getTextureRegion); Objects.requireNonNull(textureBorders); Objects.requireNonNull(getContentMargin);
        Objects.requireNonNull(getExpandMargin); Objects.requireNonNull(horizontalMode); Objects.requireNonNull(verticalMode);
        Objects.requireNonNull(getDrawInset); Objects.requireNonNull(getContentOffset);
        if (textureBorders.getHorizontal()>getTextureRegion.getWidth() || textureBorders.getVertical()>getTextureRegion.getHeight()
                || !Float.isFinite(opacity) || opacity<0 || opacity>1) throw new IllegalArgumentException("Invalid texture region/borders/opacity");
    }
    public record Patch(Rect target, Rect source) { }
    public List<Patch> getPatches(Rect area) {
        Rect r=getDrawRect(area);
        int x=getTextureRegion.getX(),y=getTextureRegion.getY(),width=getTextureRegion.getWidth(),height=getTextureRegion.getHeight();
        int[] sx={x,x+textureBorders.left(),x+width-textureBorders.right(),x+width};
        int[] sy={y,y+textureBorders.top(),y+height-textureBorders.bottom(),y+height};
        int l=fit(textureBorders.left(),textureBorders.right(),r.width()), rr=Math.min(textureBorders.right(),r.width()-l);
        int t=fit(textureBorders.top(),textureBorders.bottom(),r.height()), b=Math.min(textureBorders.bottom(),r.height()-t);
        int[] dx={r.x(),r.x()+l,r.right()-rr,r.right()}, dy={r.y(),r.y()+t,r.bottom()-b,r.bottom()};
        List<Patch> out=new ArrayList<>();
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
            if(!drawCenter && row==1 && col==1) continue;
            int sw=sx[col+1]-sx[col], sh=sy[row+1]-sy[row], dw=dx[col+1]-dx[col], dh=dy[row+1]-dy[row];
            if(sw<=0 || sh<=0 || dw<=0 || dh<=0) continue;
            int tw=col==1 && horizontalMode==AxisMode.TILE ? sw : dw;
            int th=row==1 && verticalMode==AxisMode.TILE ? sh : dh;
            for(int yy=0;yy<dh;yy+=th) for(int xx=0;xx<dw;xx+=tw) {
                int pw=Math.min(tw,dw-xx), ph=Math.min(th,dh-yy);
                out.add(new Patch(new Rect(dx[col]+xx,dy[row]+yy,pw,ph),new Rect(sx[col],sy[row],
                        tw==sw ? pw : sw, th==sh ? ph : sh)));
            }
        }
        return out;
    }
    private static int fit(int a,int b,int available) { return a+b<=available ? a : (int)((long)a*available/Math.max(1,a+b)); }
    public void draw(DrawContext context, Rect area) {
        if(opacity==0) return;
        List<TexturePainter.Quad> quads=new ArrayList<>();
        for(Patch p:getPatches(area)) {
            Rect d=p.target(),s=p.source();
            quads.add(new TexturePainter.Quad(d.x(),d.y(),d.width(),d.height(),s.x(),s.y(),s.right(),s.bottom()));
        }
        TexturePainter.draw(context,getTextureRegion,quads,0xFFFFFFFF,opacity);
    }
}
