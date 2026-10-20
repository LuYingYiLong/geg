package io.github.luyingyilong.ore.render;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** Immutable image metadata and a positive, in-bounds source rectangle. No GL ownership. */
public record TextureRegion(ResourceLocation getTexture, int getImageWidth, int getImageHeight,
                            int getX, int getY, int getWidth, int getHeight) {
    public TextureRegion {
        Objects.requireNonNull(getTexture);
        if(getImageWidth<=0 || getImageHeight<=0 || getX<0 || getY<0 || getWidth<=0 || getHeight<=0
                || (long)getX+getWidth>getImageWidth || (long)getY+getHeight>getImageHeight)
            throw new IllegalArgumentException("Invalid texture dimensions or source region");
    }
    public static TextureRegion full(ResourceLocation texture,int width,int height) {
        return new TextureRegion(texture,width,height,0,0,width,height);
    }
    public TextureRegion getSubRegion(int x,int y,int width,int height) {
        if(x<0 || y<0 || width<=0 || height<=0 || (long)x+width>getWidth || (long)y+height>getHeight)
            throw new IllegalArgumentException("Sub-region is outside the texture region");
        return new TextureRegion(getTexture,getImageWidth,getImageHeight,getX+x,getY+y,width,height);
    }
}
