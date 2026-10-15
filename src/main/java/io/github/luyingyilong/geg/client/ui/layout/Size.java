package io.github.luyingyilong.geg.client.ui.layout;

/** Width and height in GUI pixels. */
public record Size(int width, int height) {
    public Size {
        width = Math.max(0, width);
        height = Math.max(0, height);
    }
}
