package io.github.luyingyilong.ore.window;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/**
 * A main-context transaction. Secondary presentation never touches RenderSystem caches.
 */
final class WindowRenderState implements AutoCloseable {
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final VertexSorting sorting = RenderSystem.getVertexSorting();
    private final ShaderInstance shader = RenderSystem.getShader();
    private final float[] color = RenderSystem.getShaderColor().clone();
    private final int[] viewport = new int[4], scissor = new int[4];
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int[] textures = new int[12], boundTextures = new int[12];
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend = GL11.glIsEnabled(GL11.GL_BLEND), clip = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC), stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
    private final int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK), stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
    private final int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL), stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL), stencilPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
    private final float[] clearColor = new float[4];
    private final boolean[] colorMask = new boolean[4];
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int clearStencil = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE);

    WindowRenderState() {
        this(true);
    }

    WindowRenderState(boolean resetModelView) {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int i = 0; i < 4; i++) colorMask[i] = mask.get(i) != 0;
        }
        for (int i = 0; i < textures.length; i++) {
            textures[i] = RenderSystem.getShaderTexture(i);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            boundTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GlStateManager._activeTexture(activeTexture);
        RenderSystem.getModelViewStack().pushPose();
        if (resetModelView) RenderSystem.getModelViewStack().setIdentity();
        RenderSystem.applyModelViewMatrix();
    }

    @Override
    public void close() {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        for (int i = 0; i < textures.length; i++) {
            RenderSystem.setShaderTexture(i, textures[i]);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(boundTextures[i]);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        if (depth) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        if (blend) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (clip) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        else RenderSystem.disableScissor();
        if (cull) RenderSystem.enableCull();
        else RenderSystem.disableCull();
        if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST);
        else GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager._activeTexture(activeTexture);
        GlStateManager._glUseProgram(program);
        RenderSystem.stencilFunc(stencilFunc, stencilRef, stencilValueMask);
        RenderSystem.stencilMask(stencilWriteMask);
        RenderSystem.stencilOp(stencilFail, stencilDepthFail, stencilPass);
        RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        RenderSystem.clearDepth(clearDepth);
        RenderSystem.clearStencil(clearStencil);
    }
}
