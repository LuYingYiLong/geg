package io.github.luyingyilong.ore.window;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.luyingyilong.ore.text.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Real shader/atlas/FBO regression, never enabled in a release client. */
public final class TextRasterClientTest {
    private static final int WIDTH=256, HEIGHT=64;
    public static void verify() {
        var mc=Minecraft.getInstance();
        try(var saved=new WindowRenderState()) {
            var target=new TextureTarget(WIDTH,HEIGHT,true,Minecraft.ON_OSX);
            try {
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,WIDTH,HEIGHT,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.disableScissor(); RenderSystem.disableDepthTest();
                var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
                for (String text : new String[]{"السَّلام", "שָׁלוֹם", "नमस्तेक्षि", "ภาษาไทยกำ"}) {
                    var script = OreText.layout(Component.literal(text), ResourceLocation.parse("ore_gallery:international"),
                            18,240,TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,mc.font);
                    check(script.getLines().get(0).glyphs().stream().allMatch(g -> g.font() != null
                            && g.glyphId() != g.font().getMissingGlyphCode()), "script has real shaped outline glyphs");
                    paint(target, graphics, script, 0, 0xFFFFFFFF, 1);
                }
                var layout=OreText.layout(Component.literal("白 Aa（é）"),ResourceLocation.parse("ore:ore_fallback"),24,240,
                        TextLayoutEngine.Wrap.NONE,1,false,FontMetricsPolicy.DEFAULT,mc.font);
                for(float scale:new float[]{1,1.25F}) {
                    graphics.pose().pushPose(); graphics.pose().scale(scale,scale,1);
                    int[] reference=paint(target,graphics,layout,0,0xFFFFFFFF,1);
                    boolean coverage=false;
                    for(int c:reference){int a=c>>>24;if(a>0&&a<26)coverage=true;check(Math.abs((c&255)-a)<=1,"premultiplied coverage");}
                    check(coverage,"continuous low-alpha raster coverage");
                    for(int background:new int[]{0xFFFFFFFF,0xFF000000,0xFF2359A3,0})
                        for(int foreground:new int[]{0xFFFFFF,0,0x1E80E3})
                            for(int alpha:new int[]{0,64,128,255}) {
                                int[] pixels=paint(target,graphics,layout,background,alpha<<24|foreground,.5F);
                                for(int i=0;i<pixels.length;i++) {
                                    float a=(reference[i]>>>24)/255F*(alpha/255F)*.5F;
                                    for(int shift:new int[]{0,8,16}) {
                                        float expected=((foreground>>>shift)&255)*a+((background>>>shift)&255)*(1-a);
                                        check(Math.abs(((pixels[i]>>>shift)&255)-expected)<=3,"source-over pixel alpha="+alpha+" index="+i);
                                    }
                                }
                            }
                    graphics.pose().popPose();
                }
                System.out.println("ORE_TEXT_RASTER_CLIENT_PASS "+layout.getBackend());
            } finally {target.destroyBuffers();}
        }
    }
    private static int[] paint(TextureTarget target,GuiGraphics graphics,TextLayout layout,int background,int color,float opacity) {
        target.setClearColor((background>>>16&255)/255F,(background>>>8&255)/255F,(background&255)/255F,(background>>>24)/255F);
        target.clear(Minecraft.ON_OSX);target.bindWrite(true);
        RenderSystem.setShaderColor(1,1,1,opacity);RenderSystem.disableBlend();
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        var shader=RenderSystem.getShader();int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        TextRenderer.draw(graphics,layout,4,4,color,false);
        check(!GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)==src&&GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)==dst
                &&RenderSystem.getShader()==shader&&GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,"text restores GL state");
        var bytes=org.lwjgl.system.MemoryUtil.memAlloc(WIDTH*HEIGHT*4);
        try {
            GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);
            int[] result=new int[WIDTH*HEIGHT];
            for(int i=0;i<result.length;i++)result[i]=(bytes.get(i*4+3)&255)<<24|(bytes.get(i*4)&255)<<16|(bytes.get(i*4+1)&255)<<8|(bytes.get(i*4+2)&255);
            return result;
        }finally{org.lwjgl.system.MemoryUtil.memFree(bytes);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
