package io.github.luyingyilong.ore.event;
import io.github.luyingyilong.ore.window.WindowContext;
public final class CancelEvent extends InputEvent {
    public enum Reason { LOST_FOCUS, DETACHED, BLOCKED, REBOUND, CLOSED, PICKING }
    private final Reason reason;
    public CancelEvent(WindowContext context,Reason reason){super(context,0);this.reason=reason;}
    public Reason reason(){return reason;}
}
