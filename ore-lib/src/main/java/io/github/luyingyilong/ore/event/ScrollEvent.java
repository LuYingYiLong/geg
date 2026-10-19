package io.github.luyingyilong.ore.event;
import io.github.luyingyilong.ore.window.WindowContext;
import org.lwjgl.glfw.GLFW;
public final class ScrollEvent extends InputEvent {
    private final int x,y;
    private final double deltaX,deltaY;
    public ScrollEvent(WindowContext context,int x,int y,double deltaX,double deltaY,int modifiers) {
        super(context,modifiers);this.x=x;this.y=y;this.deltaX=deltaX;this.deltaY=deltaY;
    }
    public int windowX(){return x;} public int windowY(){return y;}
    public double deltaX(){return deltaX;} public double deltaY(){return deltaY;}
    public boolean horizontal(){return deltaX!=0 || (modifiers() & GLFW.GLFW_MOD_SHIFT)!=0;}
    public double delta(){return horizontal() && deltaX!=0 ? deltaX : deltaY;}
}
