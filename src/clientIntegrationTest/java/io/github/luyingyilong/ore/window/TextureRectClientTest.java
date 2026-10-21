package io.github.luyingyilong.ore.window;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.render.TextureRegion;
import io.github.luyingyilong.ore.render.TextureSource;
import io.github.luyingyilong.ore.theme.StyleBoxTexture;
import io.github.luyingyilong.ore.theme.ThemeInsets;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.luyingyilong.ore.control.display.TextureRect;
import io.github.luyingyilong.ore.control.base.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import java.util.Arrays;

/** Pixel readback and GL state checks in the real Minecraft renderer, in the isolated smoke client. */
@Mod.EventBusSubscriber(modid="geg",value=Dist.CLIENT)
public final class TextureRectClientTest {
    private static boolean ran;
    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(ran || !Boolean.getBoolean("geg.native.inspector.smoke") || mc.player==null) return;
        ran=true;
        try { verify(mc); System.out.println("ORE_TEXTURE_RECT_CLIENT_PASS"); }
        catch(Throwable failure) { failure.printStackTrace(); System.out.println("ORE_TEXTURE_RECT_CLIENT_FAIL"); mc.stop(); }
    }
    private static void verify(Minecraft mc) {
        try(var initialState=new WindowRenderState(false)) { verifyTarget(mc); }
    }
    private static void verifyTarget(Minecraft mc) {
        var pixels=new NativeImage(2,2,false);
        pixels.setPixelRGBA(0,0,0x800000FF); pixels.setPixelRGBA(1,0,0xFFFF0000);
        pixels.setPixelRGBA(0,1,0xFFFFFFFF); pixels.setPixelRGBA(1,1,0x00000000);
        ResourceLocation id=ResourceLocation.fromNamespaceAndPath("geg","test/texture_rect");
        var texture=new DynamicTexture(pixels); mc.getTextureManager().register(id,texture);
        TextureRect image=new TextureRect().setTexture(TextureSource.region(TextureRegion.full(id,2,2)))
                .setStretchMode(TextureRect.StretchMode.SCALE).setOpacity(0.5F);
        image.layout(new Rect(1,1,4,4),mc.font);
        try(var state=new WindowRenderState()) {
            var target=new TextureTarget(8,8,true,Minecraft.ON_OSX);
            try {
                target.setClearColor(0,1,0,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,8,8,0,-1,1),VertexSorting.ORTHOGRAPHIC_Z);
                RenderSystem.disableDepthTest(); RenderSystem.enableScissor(0,0,8,8);
                RenderSystem.setShaderColor(1,1,1,1); RenderSystem.disableBlend();
                GuiGraphics graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
                try(var context=new DrawContext(graphics,mc.font,new WindowContext(false),new Rect(0,0,8,8),0,0,0)) {
                    graphics.flush();
                    var shader=RenderSystem.getShader(); float[] color=RenderSystem.getShaderColor().clone();
                    int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                    int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
                    Matrix4f pose=new Matrix4f(graphics.pose().last().pose());
                    image.draw(context,0,0);
                    check(RenderSystem.getShader()==shader && Arrays.equals(color,RenderSystem.getShaderColor())
                            && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==program
                            && GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==active && !GL11.glIsEnabled(GL11.GL_BLEND)
                            && GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)==src && GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)==dst
                            && GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) && pose.equals(graphics.pose().last().pose()),"Drawing restores borrowed render state");
                    near(pixel(2,2),64,191,0,"Texture alpha multiplies control opacity");
                    near(pixel(4,2),0,128,128,"Opaque pixels respect opacity");
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    image.setModulate(0x80FFFFFF); image.draw(context,0,0);
                    near(pixel(2,2),32,223,0,"Modulation alpha also multiplies texture and control opacity");
                    image.setModulate(0xFFFFFFFF);
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    image.setFlipHorizontal(true); image.draw(context,0,0);
                    near(pixel(2,2),0,128,128,"Horizontal flip uses reversed UVs");
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    image.setFlipHorizontal(false).setFlipVertical(true); image.draw(context,0,0);
                    near(pixel(2,2),128,255,128,"Vertical flip uses reversed UVs");
                    target.clear(Minecraft.ON_OSX); target.bindWrite(true);
                    var background=new StyleBoxTexture(TextureRegion.full(id,2,2),ThemeInsets.ZERO,ThemeInsets.ZERO,ThemeInsets.ZERO,
                            StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,0.5F);
                    background.draw(context,new Rect(1,1,4,4));
                    near(pixel(2,2),64,191,0,"StyleBox uses the same transparent texture pipeline");
                }
            } finally { target.destroyBuffers(); }
        } finally { image.queueFree(); mc.getTextureManager().release(id); }
    }
    private static int[] pixel(int x,int y) {
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()) {
            var result=stack.malloc(4); GL11.glReadPixels(x,7-y,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,result);
            return new int[]{result.get(0)&255,result.get(1)&255,result.get(2)&255};
        }
    }
    private static void near(int[] color,int r,int g,int b,String message) {
        check(Math.abs(color[0]-r)<=3 && Math.abs(color[1]-g)<=3 && Math.abs(color[2]-b)<=3,
                message+": "+Arrays.toString(color));
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
