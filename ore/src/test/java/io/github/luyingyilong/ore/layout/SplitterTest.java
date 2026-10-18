package io.github.luyingyilong.ore.layout;

import io.github.luyingyilong.ore.Divider;
import java.util.ArrayList;
import java.util.List;

/** Exercises the Window event path as well as pane sizing and themed divider geometry. */
public final class SplitterTest {
    public static void main(String[] args) {
        Divider horizontal = new Divider(), vertical = new Divider().layout(Divider.Layout.VERTICAL);
        Box lines = new Box().orientation(Box.Orientation.HORIZONTAL).add(vertical).add(new Control().minimum(5,5));
        new Window(true).content(lines).layoutViewport(100,80,null);
        check(horizontal.thickness()==2 && vertical.bounds().width()==1 && vertical.bounds().height()==80,"Divider orientation and fixed vertical width");
        check(vertical.sizeFlagsHorizontal()==SizeFlags.SIZE_SHRINK_CENTER,"Vertical divider shrinks across its thickness");
        vertical.layout(Divider.Layout.HORIZONTAL);
        check(vertical.minimumHeight()==2 && vertical.minimumWidth()==0,"Changing Divider layout resets axis minima");

        Control first = new Control().minimum(20,10), second = new Control().minimum(30,10);
        Splitter split = new Splitter(first,second).ratio(0.5);
        Window root = new Window(true).content(split); root.layoutViewport(204,100,null);
        check(split.sizes().equals(new Splitter.Sizes(100,100)),"Initial ratio");
        Divider line = (Divider)split.children().get(2);
        check(line.layout()==Divider.Layout.VERTICAL && line.bounds().width()==1,"Side-by-side panes use the shared vertical Divider");
        List<String> events = new ArrayList<>();
        split.onResizeStart(size->events.add("start")).onResize(size->events.add("resize:"+size.first())).onResizeEnd(size->events.add("end:"+size.first()));
        split.showDivider(false);
        check(!line.visible() && root.pointerPressed(101,40,null),"Invisible divider keeps its drag hit area");
        root.pointerDragged(141,40); root.pointerReleased(141,40); root.layoutViewport(204,100,null);
        check(split.firstSize()==140 && events.equals(List.of("start","resize:140","end:140")),"Captured drag and ordered resize callbacks");
        split.resizable(false); double fixed = split.ratio();
        root.pointerPressed(141,40,null);root.pointerDragged(180,40);root.pointerReleased(180,40);
        check(split.ratio()==fixed,"Resizable flag");

        split.resizable(true).showDivider(true).ratio(0.5).lazy(true); root.layoutViewport(204,100,null); events.clear();
        root.pointerPressed(102,40,null);root.pointerDragged(152,40);root.layoutViewport(204,100,null);
        check(split.firstSize()==100 && split.dividerBounds().x()==150,"Lazy dragging previews the line without resizing panes");
        root.pointerReleased(152,40);root.layoutViewport(204,100,null);
        check(split.firstSize()==150 && events.get(2).equals("end:150"),"Lazy release commits pane sizes");
        split.ratio(0.5);root.layoutViewport(204,100,null);events.clear();
        root.pointerPressed(102,40,null);root.pointerDragged(152,40);root.cancelInteractions();root.layoutViewport(204,100,null);
        check(!split.dragging() && split.firstSize()==100 && events.size()==2,"Cancelling lazy drag restores layout without end callback");

        split.lazy(false).firstMinimum(50).secondMinimum(40).firstMaximum(120).secondMaximum(90).ratio(0.5);
        root.layoutViewport(204,100,null);
        check(split.sizes().equals(new Splitter.Sizes(110,90)),"Both pane bounds constrain ratio");
        root.layoutViewport(400,100,null);
        check(split.sizes().equals(new Splitter.Sizes(120,90)),"Finite maxima leave excess space unused");
        root.layoutViewport(40,100,null);
        check(split.firstSize()>=50 && split.secondSize()>=40 && second.bounds().right()>40,"Minimums overflow instead of shrinking");
        split.secondMaximum(Integer.MAX_VALUE).firstSize(80);root.layoutViewport(204,100,null);
        check(split.firstSize()==80,"Pixel sizing");
        root.layoutViewport(304,100,null);check(split.firstSize()==80,"Pixel size survives viewport resize");
        split.firstMaximum(Integer.MAX_VALUE).ratio(0.5);root.layoutViewport(304,100,null);
        check(split.firstSize()==150,"Ratio switches out of pixel sizing");
        second.visible(false);root.layoutViewport(304,100,null);
        check(split.firstSize()==304&&!line.visible(),"One visible pane fills the splitter");

        Splitter stacked = new Splitter(new Control().minimum(10,10),new Control().minimum(10,10))
                .orientation(Splitter.Orientation.VERTICAL).ratio(0.5);
        Window stackedRoot = new Window(true).content(stacked);stackedRoot.layoutViewport(100,264,null);
        Divider stackedLine=(Divider)stacked.children().get(2);
        check(stackedLine.layout()==Divider.Layout.HORIZONTAL&&stackedLine.bounds().height()==2,"Stacked panes use horizontal Divider");
        stackedRoot.pointerPressed(30,132,null);stackedRoot.pointerDragged(30,162);stackedRoot.pointerReleased(30,162);stackedRoot.layoutViewport(100,264,null);
        check(stacked.firstSize()==160,"Vertical dragging preserves grab offset");

        Control greedy = new Control(){@Override protected boolean onPointerPressed(int x,int y){throw new AssertionError("Pane stole separator press");}};
        Splitter priority=new Splitter(greedy,new Control()).ratio(0.5);Window priorityRoot=new Window(true).content(priority);
        priorityRoot.layoutViewport(204,100,null);
        check(priorityRoot.pointerPressed(99,20,null)&&priority.dragging(),"Expanded drag area takes precedence over pane contents");priorityRoot.pointerReleased(99,20);
        rejects(()->priority.ratio(Double.NaN));rejects(()->priority.gap(-1));rejects(()->priority.dragSize(0));
        rejects(()->priority.firstMaximum(-1));rejects(()->priority.firstMinimum(-1));
        System.out.println("GEG_SPLITTER_TEST_PASS");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected invalid value");}catch(IllegalArgumentException expected){}}
}
