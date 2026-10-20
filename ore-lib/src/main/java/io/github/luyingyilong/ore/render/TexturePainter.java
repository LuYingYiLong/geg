package io.github.luyingyilong.ore.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import java.util.List;

/** One state scope and one GPU batch per image, including tiled images and nine-slices. */
public final class TexturePainter {
    private TexturePainter() { }
    /** UVs are absolute source-image pixels, and may be reversed without negative rectangles. */
    public record Quad(float x,float y,float width,float height,float u0,float v0,float u1,float v1) {
        public Quad {
            if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                    || !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
                    || width<0 || height<0) throw new IllegalArgumentException("Invalid textured quad");
        }
    }
    public static void draw(DrawContext context,TextureRegion image,List<Quad> quads,int modulate,float opacity) {
        if(!Float.isFinite(opacity) || opacity<0 || opacity>1) throw new IllegalArgumentException("Invalid texture opacity");
        if(quads.isEmpty() || opacity==0 || modulate>>>24==0) return;
        var graphics=context.getGraphics(); graphics.flush();
        float[] color=RenderSystem.getShaderColor().clone();
        var shader=RenderSystem.getShader(); int texture=RenderSystem.getShaderTexture(0);
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int boundTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(active);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0,image.getTexture());
            RenderSystem.setShaderColor(color[0]*((modulate>>>16)&255)/255F,color[1]*((modulate>>>8)&255)/255F,
                    color[2]*(modulate&255)/255F,color[3]*(modulate>>>24)/255F*opacity);
            var matrix=graphics.pose().last().pose();
            var buffer=Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            for(Quad q:quads) {
                float u0=q.u0()/image.getImageWidth(),u1=q.u1()/image.getImageWidth();
                float v0=q.v0()/image.getImageHeight(),v1=q.v1()/image.getImageHeight();
                buffer.vertex(matrix,q.x(),q.y()+q.height(),0).uv(u0,v1).endVertex();
                buffer.vertex(matrix,q.x()+q.width(),q.y()+q.height(),0).uv(u1,v1).endVertex();
                buffer.vertex(matrix,q.x()+q.width(),q.y(),0).uv(u1,v0).endVertex();
                buffer.vertex(matrix,q.x(),q.y(),0).uv(u0,v0).endVertex();
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderTexture(0,texture); RenderSystem.setShader(()->shader);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(boundTexture);
            GlStateManager._activeTexture(active); GlStateManager._glUseProgram(program);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(!blend) RenderSystem.disableBlend();
        }
    }
}
