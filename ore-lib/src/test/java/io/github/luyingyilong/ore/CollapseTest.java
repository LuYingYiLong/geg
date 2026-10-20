package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.event.*;
import io.github.luyingyilong.ore.layout.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;

/** Real Window routing plus a deterministic clock, with the body retaining full layout geometry. */
public final class CollapseTest {
    private static final Font FONT=new Font(id->null,false){@Override public int width(FormattedText text){return text.getString().length()*6;}};
    public static void main(String[] args) {
        long[] time={0};
        Button action=new Button();int[] activations={0};action.pressed().connect(action,()->activations[0]++);
        Control content=new Control().setMinimum(80,80);content.addChild(action);
        action.setPosition(5,5).setRequestedSize(40,20);
        Collapse collapse=new Collapse(Component.empty(),()->time[0]).setContent(content).setAnimationDuration(300);
        Button next=new Button();Box column=new Box();column.addChild(collapse);column.addChild(next);
        Window window=Window.embedded().setContent(column);
        List<Boolean> changes=new ArrayList<>();collapse.expandedChanged().connect(window,changes::add);
        try {
            window.layoutViewport(160,240,FONT);
            int header=collapse.getHeaderButton().getBounds().height();
            var panelStyle=new io.github.luyingyilong.ore.theme.StyleBoxFlat(0xFF123456,
                    io.github.luyingyilong.ore.theme.ThemeInsets.ZERO,0,
                    new io.github.luyingyilong.ore.theme.ThemeInsets(4,4,4,4),io.github.luyingyilong.ore.theme.ThemeInsets.ZERO);
            collapse.setThemeOverride(Collapse.PANEL,panelStyle);
            check(((Panel)collapse.getChild(1).getChild(0)).getStyleBox()==panelStyle,"internal panel inherits local Collapse overrides");
            collapse.removeThemeOverride(Collapse.PANEL);
            check(!collapse.getExpanded()&&collapse.getSize().height()==header,"default closed height");
            check(!action.isInputAccessible()&&content.isAlive(),"closed body retained but inert");
            click(window,collapse.getHeaderButton());
            check(collapse.getExpanded()&&changes.equals(List.of(true)),"release opens once");
            time[0]=75;window.layoutViewport(160,240,FONT);
            double quarter=collapse.getVisibleBodyHeight();
            check(Math.abs(quarter-88*0.15625)<0.001,"nonlinear quarter point");
            check(content.getBounds().height()==80,"content is never scaled or compressed");
            check(next.getBounds().y()==collapse.getSize().height(),"siblings follow animated height");
            double angle=collapse.getHeaderButton().getTrailingIconControl().getRenderTransform().getRotationDegrees();
            check(angle==0,"arrow remains unrotated throughout the body animation");
            check(collapse.getHeaderButton().getTrailingIconControl().getResolvedTextureRegion().equals(
                    io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP_LIGHT.defaultValue()),"opening uses the up texture immediately");
            collapse.setExpanded(false);
            check(collapse.getVisibleBodyHeight()==quarter&&!action.isInputAccessible(),"reverse keeps current height and blocks input immediately");
            time[0]=225;window.layoutViewport(160,240,FONT);
            check(Math.abs(collapse.getVisibleBodyHeight()-quarter/2)<0.001,"reverse halfway");
            time[0]=375;window.layoutViewport(160,240,FONT);
            check(!collapse.getAnimating()&&collapse.getVisibleBodyHeight()==0,"reverse ends closed");

            collapse.setExpandedNoSignal(true);time[0]=675;window.layoutViewport(160,240,FONT);
            check(changes.equals(List.of(true,false))&&action.isInputAccessible(),"silent opening preserves input and signals");
            window.getFocusManager().requestFocus(action,null);
            Rect rect=action.getAbsoluteBounds();
            window.dispatchInput(new PointerEvent(window.getContext(),PointerEvent.Action.PRESS,rect.x()+2,rect.y()+2,0,0));
            check(window.getPointerCaptured(0),"body gesture captured");
            collapse.setExpanded(false);
            check(!window.getPointerCaptured(0)&&window.getFocusManager().getFocused()==collapse.getHeaderButton(),"closing cancels capture and restores header focus");
            window.dispatchInput(new PointerEvent(window.getContext(),PointerEvent.Action.RELEASE,rect.x()+2,rect.y()+2,0,0));
            check(activations[0]==0,"old release cannot activate a closing child");
            collapse.setAnimationDuration(0).setExpanded(true).setDisabled(true);
            window.layoutViewport(160,240,FONT);
            check(collapse.getExpanded()&&!collapse.getHeaderButton().acceptsFocus()&&action.acceptsFocus(),"disabled affects only the header");
            click(window,action);check(activations[0]==1,"disabled open body stays interactive");
            click(window,collapse.getHeaderButton());check(collapse.getExpanded(),"disabled header cannot collapse");
            collapse.setDisabled(false).setShowArrow(false);
            check(collapse.getHeaderButton().getTrailingIcon()==null,"optional arrow");
            for(int width:new int[]{127,128,129,427}) {
                window.layoutViewport(width,240,FONT);
                check(content.getBounds().width()==width-16,"panel padding consumed once, with wider horizontal insets");
            }
            collapse.setExpanded(false);check(content.isAlive(),"folding never frees the content");
            Control replacement=new Control().setMinimum(10,20);
            collapse.setContent(replacement);
            check(collapse.getContent()==replacement&&content.getParent()==null&&content.isAlive(),"slot replacement detaches without destroying");
            content.queueFree();
        } finally {window.queueFree();LifecycleScheduler.flush();}
        check(action.isDisposed(),"retained body and connections clean up with their owner");
        initialOpen();
        arrowStates();
        System.out.println("ORE_COLLAPSE_PASS");
    }

    private static void initialOpen() {
        Collapse collapse=new Collapse().setContent(new Control().setMinimum(20,40)).setExpandedNoSignal(true);
        Window window=Window.embedded().setContent(collapse);window.layoutViewport(100,100,FONT);
        check(collapse.getVisibleBodyHeight()==48&&!collapse.getAnimating(),"initial expanded value has no entrance animation");
        try {collapse.setAnimationDuration(-1);throw new AssertionError("negative duration");}catch(IllegalArgumentException expected){}
        window.queueFree();LifecycleScheduler.flush();
    }
    private static void arrowStates() {
        long[] now={0};
        Collapse collapse=new Collapse(Component.empty(),()->now[0]).setContent(new Control().setMinimum(20,40)).setAnimationDuration(300);
        Window window=Window.embedded().setContent(collapse);
        TextureRect icon=collapse.getHeaderButton().getTrailingIconControl();
        try {
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN_LIGHT);
            collapse.setDisabled(true);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN);
            collapse.setExpandedNoSignal(true);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP);
            collapse.setDisabled(false);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP_LIGHT);
            window.layoutViewport(127,160,FONT);
            collapse.setExpanded(false);
            check(collapse.getAnimating()&&collapse.getVisibleBodyHeight()==48,"body still animates");
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN_LIGHT);
            now[0]=75;window.layoutViewport(127,160,FONT);
            check(collapse.getVisibleBodyHeight()>0&&collapse.getVisibleBodyHeight()<48,"body height advances independently");
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_DOWN_LIGHT);
            collapse.setDisabled(true).setExpanded(true);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP);
            collapse.setShowArrow(false);
            check(icon.getTexture()==null,"hidden arrow clears the existing image");
            collapse.setShowArrow(true);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP);
            var custom=io.github.luyingyilong.ore.render.TextureRegion.full(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("test","expanded.png"),7,7);
            collapse.setThemeOverride(Collapse.DISABLED_EXPANDED_ARROW,custom);
            check(icon.getResolvedTextureRegion()==custom,"each arrow texture supports a local theme override");
            collapse.removeThemeOverride(Collapse.DISABLED_EXPANDED_ARROW);
            arrow(collapse,io.github.luyingyilong.ore.theme.IconTheme.ARROW_UP);
            check(icon==collapse.getHeaderButton().getTrailingIconControl(),"all states reuse one image node");
        } finally {window.queueFree();LifecycleScheduler.flush();}
    }
    private static void arrow(Collapse collapse,io.github.luyingyilong.ore.theme.ThemeKey<io.github.luyingyilong.ore.render.TextureRegion> key) {
        TextureRect icon=collapse.getHeaderButton().getTrailingIconControl();
        check(icon.getResolvedTextureRegion().equals(key.defaultValue()),"correct texture for "+key.getId());
        check(icon.getRenderTransform().equals(io.github.luyingyilong.ore.render.Transform2D.IDENTITY),"state changes never rotate or animate the arrow");
    }
    private static void click(Window window,Control control) {
        Rect r=EventGeometry.displayed(control);int x=r.x()+r.width()/2,y=r.y()+r.height()/2;
        window.dispatchInput(new PointerEvent(window.getContext(),PointerEvent.Action.PRESS,x,y,0,0));
        window.dispatchInput(new PointerEvent(window.getContext(),PointerEvent.Action.RELEASE,x,y,0,0));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
