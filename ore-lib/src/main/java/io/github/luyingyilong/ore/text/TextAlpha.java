package io.github.luyingyilong.ore.text;

/** Pure arithmetic shared by atlas upload and GPU regression fixtures. */
public final class TextAlpha {
    private TextAlpha() { }
    public static int premultiply(int argb) {
        int a = argb >>> 24;
        return a << 24 | multiply(argb >>> 16 & 255, a) << 16 | multiply(argb >>> 8 & 255, a) << 8 | multiply(argb & 255, a);
    }
    private static int multiply(int value, int alpha) { return (value * alpha + 127) / 255; }
    public static float coverage(float coverage, float fontAlpha, float contextAlpha) { return coverage * fontAlpha * contextAlpha; }
    public static float over(float premultipliedSource, float alpha, float destination) { return premultipliedSource + destination * (1 - alpha); }
}
