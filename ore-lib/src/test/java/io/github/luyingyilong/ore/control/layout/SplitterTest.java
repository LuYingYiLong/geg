package io.github.luyingyilong.ore.control.layout;

import io.github.luyingyilong.ore.control.base.Control;
import io.github.luyingyilong.ore.control.base.SizeFlags;
import io.github.luyingyilong.ore.window.Window;

import io.github.luyingyilong.ore.signal.Signals;

import java.util.ArrayList;
import java.util.List;

/** Exercises the Window event path as well as pane sizing and themed divider geometry. */
public final class SplitterTest {
    public static void main(String[] args) {
        Divider horizontal = new Divider(), vertical = new Divider().setLayout(Divider.Layout.VERTICAL);
        Box lines = new Box().setOrientation(Box.Orientation.HORIZONTAL);
        lines.addChild(vertical);
        lines.addChild(new Control().setMinimum(5, 5));
        new Window(true).setContent(lines).layoutViewport(100,80,null);
        check(horizontal.getThickness()==2 && vertical.getBounds().width()==1 && vertical.getBounds().height()==80,"Divider orientation and fixed vertical width");
        check(vertical.getSizeFlagsHorizontal()==SizeFlags.SIZE_SHRINK_CENTER,"Vertical divider shrinks across its thickness");
        vertical.setLayout(Divider.Layout.HORIZONTAL);
        check(vertical.getMinimumHeight()==2 && vertical.getMinimumWidth()==0,"Changing Divider layout resets axis minima");

        Control first = new Control().setMinimum(20,10), second = new Control().setMinimum(30,10);
        Splitter split = new Splitter(first,second).setRatio(0.5);
        Window root = new Window(true).setContent(split); root.layoutViewport(204,100,null);
        check(split.getSizes().equals(new Splitter.Sizes(100,100)),"Initial ratio");
        Divider line = (Divider)split.getChildren().get(2);
        check(line.getLayout()==Divider.Layout.VERTICAL && line.getBounds().width()==1,"Side-by-side panes use the shared vertical Divider");
        List<String> events = new ArrayList<>();
        Signals.configure(Signals.configure(Signals.configure(split, connected -> connected.resizeStarted().connect(connected, size->events.add("start"))), connected -> connected.resized().connect(connected, size->events.add("resize:"+size.first()))), connected -> connected.resizeEnded().connect(connected, size->events.add("end:"+size.first())));
        split.setShowDivider(false);
        check(!line.getVisible() && io.github.luyingyilong.ore.event.WindowInputs.press(root,101,40,null),"Invisible divider keeps its drag hit area");
        io.github.luyingyilong.ore.event.WindowInputs.move(root,141,40); io.github.luyingyilong.ore.event.WindowInputs.release(root,141,40); root.layoutViewport(204,100,null);
        check(split.getFirstSize()==140 && events.equals(List.of("start","resize:140","end:140")),"Captured drag and ordered resize callbacks");
        split.setResizable(false); double fixed = split.getRatio();
        io.github.luyingyilong.ore.event.WindowInputs.press(root,141,40,null);io.github.luyingyilong.ore.event.WindowInputs.move(root,180,40);io.github.luyingyilong.ore.event.WindowInputs.release(root,180,40);
        check(split.getRatio()==fixed,"Resizable flag");

        split.setResizable(true).setShowDivider(true).setRatio(0.5).setLazy(true); root.layoutViewport(204,100,null); events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(root,102,40,null);io.github.luyingyilong.ore.event.WindowInputs.move(root,152,40);root.layoutViewport(204,100,null);
        check(split.getFirstSize()==100 && split.getDividerBounds().x()==150,"Lazy dragging previews the line without resizing panes");
        io.github.luyingyilong.ore.event.WindowInputs.release(root,152,40);root.layoutViewport(204,100,null);
        check(split.getFirstSize()==150 && events.get(2).equals("end:150"),"Lazy release commits pane sizes");
        split.setRatio(0.5);root.layoutViewport(204,100,null);events.clear();
        io.github.luyingyilong.ore.event.WindowInputs.press(root,102,40,null);io.github.luyingyilong.ore.event.WindowInputs.move(root,152,40);root.cancelInteractions();root.layoutViewport(204,100,null);
        check(!split.getDragging() && split.getFirstSize()==100 && events.size()==2,"Cancelling lazy drag restores layout without end callback");

        split.setLazy(false).setFirstMinimum(50).setSecondMinimum(40).setFirstMaximum(120).setSecondMaximum(90).setRatio(0.5);
        root.layoutViewport(204,100,null);
        check(split.getSizes().equals(new Splitter.Sizes(110,90)),"Both pane bounds constrain ratio");
        root.layoutViewport(400,100,null);
        check(split.getSizes().equals(new Splitter.Sizes(120,90)),"Finite maxima leave excess space unused");
        root.layoutViewport(40,100,null);
        check(split.getFirstSize()>=50 && split.getSecondSize()>=40 && second.getBounds().right()>40,"Minimums overflow instead of shrinking");
        split.setSecondMaximum(Integer.MAX_VALUE).setFirstSize(80);root.layoutViewport(204,100,null);
        check(split.getFirstSize()==80,"Pixel sizing");
        root.layoutViewport(304,100,null);check(split.getFirstSize()==80,"Pixel size survives viewport resize");
        split.setFirstMaximum(Integer.MAX_VALUE).setRatio(0.5);root.layoutViewport(304,100,null);
        check(split.getFirstSize()==150,"Ratio switches out of pixel sizing");
        second.setVisible(false);root.layoutViewport(304,100,null);
        check(split.getFirstSize()==304&&!line.getVisible(),"One visible pane fills the splitter");

        Splitter stacked = new Splitter(new Control().setMinimum(10,10),new Control().setMinimum(10,10))
                .setOrientation(Splitter.Orientation.VERTICAL).setRatio(0.5);
        Window stackedRoot = new Window(true).setContent(stacked);stackedRoot.layoutViewport(100,264,null);
        Divider stackedLine=(Divider)stacked.getChildren().get(2);
        check(stackedLine.getLayout()==Divider.Layout.HORIZONTAL&&stackedLine.getBounds().height()==2,"Stacked panes use horizontal Divider");
        io.github.luyingyilong.ore.event.WindowInputs.press(stackedRoot,30,132,null);io.github.luyingyilong.ore.event.WindowInputs.move(stackedRoot,30,162);io.github.luyingyilong.ore.event.WindowInputs.release(stackedRoot,30,162);stackedRoot.layoutViewport(100,264,null);
        check(stacked.getFirstSize()==160,"Vertical dragging preserves grab offset");

        Control greedy = new Control(){@Override protected boolean pointerDown(int x,int y){throw new AssertionError("Pane stole separator press");}};
        Splitter priority=new Splitter(greedy,new Control()).setRatio(0.5);Window priorityRoot=new Window(true).setContent(priority);
        priorityRoot.layoutViewport(204,100,null);
        check(io.github.luyingyilong.ore.event.WindowInputs.press(priorityRoot,99,20,null)&&priority.getDragging(),"Expanded drag area takes precedence over pane contents");io.github.luyingyilong.ore.event.WindowInputs.release(priorityRoot,99,20);
        rejects(()->priority.setRatio(Double.NaN));rejects(()->priority.setGap(-1));rejects(()->priority.setDragSize(0));
        rejects(()->priority.setFirstMaximum(-1));rejects(()->priority.setFirstMinimum(-1));
        System.out.println("ORE_SPLITTER_TEST_PASS");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected invalid value");}catch(IllegalArgumentException expected){}}
}
