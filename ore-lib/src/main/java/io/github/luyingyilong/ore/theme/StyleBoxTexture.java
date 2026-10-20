package io.github.luyingyilong.ore.theme;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Offset;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Independent source borders. Destination borders shrink proportionally for tiny rectangles. */
public record StyleBoxTexture(ResourceLocation location, int imageWidth, int imageHeight,
        int x, int y, int width, int height, ThemeInsets textureBorders, ThemeInsets getContentMargin,
        ThemeInsets getExpandMargin, AxisMode horizontalMode, AxisMode verticalMode, boolean drawCenter,
        float opacity, ThemeInsets getDrawInset, Offset getContentOffset) implements StyleBox {
    public enum AxisMode { STRETCH, TILE }
    public StyleBoxTexture(ResourceLocation location, int imageWidth, int imageHeight, int x, int y, int width, int height,
                           ThemeInsets textureBorders, ThemeInsets contentMargin, ThemeInsets expandMargin,
                           AxisMode horizontalMode, AxisMode verticalMode, boolean drawCenter, float opacity) {
        this(location, imageWidth, imageHeight, x, y, width, height, textureBorders, contentMargin, expandMargin,
                horizontalMode, verticalMode, drawCenter, opacity, ThemeInsets.ZERO, Offset.ZERO);
    }
    public StyleBoxTexture {
        Objects.requireNonNull(location); Objects.requireNonNull(textureBorders); Objects.requireNonNull(getContentMargin);
        Objects.requireNonNull(getExpandMargin); Objects.requireNonNull(horizontalMode); Objects.requireNonNull(verticalMode);
        Objects.requireNonNull(getDrawInset); Objects.requireNonNull(getContentOffset);
        if (imageWidth<=0 || imageHeight<=0 || x<0 || y<0 || width<=0 || height<=0
                || (long)x+width>imageWidth || (long)y+height>imageHeight
                || textureBorders.getHorizontal()>width || textureBorders.getVertical()>height
                || !Float.isFinite(opacity) || opacity<0 || opacity>1) throw new IllegalArgumentException("Invalid texture region/borders/opacity");
    }
    public record Patch(Rect target, Rect source) { }
    public List<Patch> getPatches(Rect area) {
        Rect r=getDrawRect(area);
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
        var g=context.getGraphics(); g.flush();
        float[] previous=RenderSystem.getShaderColor().clone();
        boolean blend=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int srcRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            g.setColor(previous[0],previous[1],previous[2],previous[3]*opacity);
            for(Patch p:getPatches(area)) {
                Rect d=p.target(), s=p.source();
                g.blit(location,d.x(),d.y(),d.width(),d.height(),(float)s.x(),(float)s.y(),s.width(),s.height(),imageWidth,imageHeight);
            }
            g.flush();
        } finally { g.setColor(previous[0],previous[1],previous[2],previous[3]); RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha); if(!blend) RenderSystem.disableBlend(); }
    }
}
