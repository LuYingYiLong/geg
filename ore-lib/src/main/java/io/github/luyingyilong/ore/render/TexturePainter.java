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
    public record Vertex(float x, float y, float u, float v) {
        public Vertex {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(u) || !Float.isFinite(v))
                throw new IllegalArgumentException("Invalid textured vertex");
        }
    }
    public record Triangle(Vertex a, Vertex b, Vertex c) {
        public Triangle { java.util.Objects.requireNonNull(a); java.util.Objects.requireNonNull(b); java.util.Objects.requireNonNull(c); }
    }
    /** UVs are absolute source-image pixels, and may be reversed without negative rectangles. */
    public record Quad(float x,float y,float width,float height,float u0,float v0,float u1,float v1) {
        public Quad {
            if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                    || !Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)
                    || width<0 || height<0) throw new IllegalArgumentException("Invalid textured quad");
        }
    }
    public static void draw(DrawContext context,TextureRegion image,List<Quad> quads,int modulate,float opacity) {
        validateOpacity(opacity);
        if (quads.isEmpty()) return;
        drawVertices(context,image,modulate,opacity,VertexFormat.Mode.QUADS,(buffer,matrix)->{
            for(Quad q:quads) {
                float u0=q.u0()/image.getImageWidth(),u1=q.u1()/image.getImageWidth();
                float v0=q.v0()/image.getImageHeight(),v1=q.v1()/image.getImageHeight();
                buffer.vertex(matrix,q.x(),q.y()+q.height(),0).uv(u0,v1).endVertex();
                buffer.vertex(matrix,q.x()+q.width(),q.y()+q.height(),0).uv(u1,v1).endVertex();
                buffer.vertex(matrix,q.x()+q.width(),q.y(),0).uv(u1,v0).endVertex();
                buffer.vertex(matrix,q.x(),q.y(),0).uv(u0,v0).endVertex();
            }
        });
    }
    /** Uses the same straight-alpha sampling and state scope as ordinary images. */
    public static void drawTriangles(DrawContext context,TextureRegion image,List<Triangle> triangles,int modulate,float opacity) {
        validateOpacity(opacity);
        if (triangles.isEmpty()) return;
        drawVertices(context,image,modulate,opacity,VertexFormat.Mode.TRIANGLES,(buffer,matrix)->{
            for (Triangle triangle:triangles) {
                emitVertex(buffer,matrix,image,triangle.a());
                emitVertex(buffer,matrix,image,triangle.b());
                emitVertex(buffer,matrix,image,triangle.c());
            }
        });
    }
    private static void emitVertex(BufferBuilder buffer,org.joml.Matrix4f matrix,TextureRegion image,Vertex vertex) {
        buffer.vertex(matrix,vertex.x(),vertex.y(),0).uv(vertex.u()/image.getImageWidth(),vertex.v()/image.getImageHeight()).endVertex();
    }
    private static void validateOpacity(float opacity) {
        if(!Float.isFinite(opacity) || opacity<0 || opacity>1) throw new IllegalArgumentException("Invalid texture opacity");
    }
    private static void drawVertices(DrawContext context,TextureRegion image,int modulate,float opacity,
                                     VertexFormat.Mode mode,java.util.function.BiConsumer<BufferBuilder,org.joml.Matrix4f> emit) {
        if(opacity==0 || modulate>>>24==0) return;
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
        int equationRgb=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try {
            RenderSystem.enableBlend();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0,image.getTexture());
            RenderSystem.setShaderColor(color[0]*((modulate>>>16)&255)/255F,color[1]*((modulate>>>8)&255)/255F,
                    color[2]*(modulate&255)/255F,color[3]*(modulate>>>24)/255F*opacity);
            var matrix=graphics.pose().last().pose();
            var buffer=Tesselator.getInstance().getBuilder();
            buffer.begin(mode,DefaultVertexFormat.POSITION_TEX);
            emit.accept(buffer,matrix);
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderTexture(0,texture); RenderSystem.setShader(()->shader);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0); GlStateManager._bindTexture(boundTexture);
            GlStateManager._activeTexture(active); GlStateManager._glUseProgram(program);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            GL20.glBlendEquationSeparate(equationRgb,equationAlpha);
            if(!blend) RenderSystem.disableBlend();
        }
    }
}
