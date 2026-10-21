#version 150
uniform sampler2D Sampler0;
uniform int StraightMask;
uniform vec4 ColorModulator;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
void main() {
    // The atlas is premultiplied. Do not multiply its RGB by coverage again.
    vec4 glyph = texture(Sampler0, uv);
    if (StraightMask != 0) glyph = vec4(glyph.aaa, glyph.a);
    vec4 color = tint * ColorModulator;
    fragColor = vec4(glyph.rgb * color.rgb * color.a, glyph.a * color.a);
}
