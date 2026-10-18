package io.github.luyingyilong.ore.window;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Only raw GL in the secondary context, with a context-local VAO.
 */
final class NativePresentation implements AutoCloseable {
    private final int program, vao;

    NativePresentation() {
        int vertex = shader(GL20.GL_VERTEX_SHADER, "#version 150\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}");
        // The output and source framebuffer have identical sizes. Fetch the exact source texel;
        // normalized UV sampling can interpolate if shared texture filtering changes elsewhere.
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 150\nuniform sampler2D image; out vec4 color; void main(){color=vec4(texelFetch(image,ivec2(gl_FragCoord.xy),0).rgb,1.0);}");
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
            throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
        vao = GL30.glGenVertexArrays();
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    void draw(int texture, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "image"), 0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    @Override
    public void close() {
        GL30.glDeleteVertexArrays(vao);
        GL20.glDeleteProgram(program);
    }
}
