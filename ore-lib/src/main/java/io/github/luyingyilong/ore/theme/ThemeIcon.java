package io.github.luyingyilong.ore.theme;

import io.github.luyingyilong.ore.render.DrawContext;
import io.github.luyingyilong.ore.layout.Rect;
import java.util.*;

/** Immutable texture data, with frame slices cached on first use rather than allocated each frame. */
public final class ThemeIcon {
    private final StyleBoxTexture texture;
    private final Map<Integer,List<StyleBoxTexture>> frames=new HashMap<>();
    public ThemeIcon(StyleBoxTexture texture) { this.texture=Objects.requireNonNull(texture); }
    public StyleBoxTexture getTexture() { return texture; }
    public int getWidth() { return texture.width(); }
    public int getHeight() { return texture.height(); }
    public void draw(DrawContext context,Rect rect) { texture.draw(context,rect); }
    public void drawFrame(DrawContext context,Rect rect,int frame,int frameHeight) {
        if(frameHeight<=0) throw new IllegalArgumentException("Frame height must be positive");
        var slices=frames.computeIfAbsent(frameHeight,height -> {
            List<StyleBoxTexture> result=new ArrayList<>();
            for(int y=0;y<texture.height();y+=height) result.add(new StyleBoxTexture(texture.location(),texture.imageWidth(),texture.imageHeight(),
                    texture.x(),texture.y()+y,texture.width(),Math.min(height,texture.height()-y),ThemeInsets.ZERO,ThemeInsets.ZERO,
                    ThemeInsets.ZERO,StyleBoxTexture.AxisMode.STRETCH,StyleBoxTexture.AxisMode.STRETCH,true,texture.opacity(),
                    texture.getDrawInset(),texture.getContentOffset()));
            return List.copyOf(result);
        });
        slices.get(Math.floorMod(frame,slices.size())).draw(context,rect);
    }
    @Override public boolean equals(Object other) { return other instanceof ThemeIcon icon && texture.equals(icon.texture); }
    @Override public int hashCode() { return texture.hashCode(); }
    @Override public String toString() { return "ThemeIcon["+texture+"]"; }
}
