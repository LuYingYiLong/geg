package io.github.luyingyilong.geg.client.ui.theme;

/** Exercises the actual alpha/state bridge without requiring a Minecraft/OpenGL bootstrap. */
public final class OreTextAppearanceTest {
    private static final class Canvas implements OreFonts.AlphaCanvas {
        int draws, color;
        float alpha;
        float[] shader = {0.5F, 0.6F, 0.7F, 0.4F};
        boolean fail;
        public void flush() { }
        public float[] color() { return shader; }
        public void color(float r, float g, float b, float a) { shader = new float[]{r, g, b, a}; }
        public void draw(int color) {
            draws++; this.color = color; alpha = shader[3];
            if (fail) throw new IllegalStateException("Paint failed");
        }
    }
    public static void main(String[] args) {
        Canvas canvas = new Canvas();
        for (int alpha : new int[]{0, 1, 3, 32, 128, 255}) {
            int before = canvas.draws;
            OreFonts.draw(alpha << 24 | 0x1E1E1F, canvas);
            check(canvas.draws == before + (alpha == 0 ? 0 : 1));
            if (alpha != 0) {
                check(canvas.color == 0xFF1E1E1F);
                check(Math.abs(canvas.alpha - 0.4F * alpha / 255.0F) < 0.00001F);
            }
            check(canvas.shader[0] == 0.5F && canvas.shader[1] == 0.6F && canvas.shader[2] == 0.7F && canvas.shader[3] == 0.4F);
        }
        canvas.fail = true;
        try { OreFonts.draw(0x80FFFFFF, canvas); }
        catch (IllegalStateException expected) { }
        check(canvas.shader[3] == 0.4F);
        check(OreTheme.TEXT_DARK == 0xFF1E1E1F && OreTheme.TEXT_WHITE == 0xFFFFFFFF && OreTheme.TEXT_SECONDARY == 0xFFD0D1D4);
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Ore text alpha/state regression"); }
}
