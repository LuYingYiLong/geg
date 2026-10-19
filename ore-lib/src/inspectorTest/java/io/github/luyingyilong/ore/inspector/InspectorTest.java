package io.github.luyingyilong.ore.inspector;

import io.github.luyingyilong.ore.debug.*;
import io.github.luyingyilong.ore.layout.*;
import java.util.*;

/** Property discovery, overlay and layout tests, with no running game required. */
public final class InspectorTest {
    public static void main(String[] args) throws Exception {
        check(java.util.ServiceLoader.load(io.github.luyingyilong.ore.OreClientExtension.class).stream()
                .anyMatch(provider -> provider.type() == InspectorClientExtension.class),
                "Development Inspector publishes its own client extension");
        InspectorPropertiesTest.run();
        InspectorBackend lifecycle=new InspectorBackend();
        Window inspector=Window.nativeWindow(io.github.luyingyilong.ore.window.WindowOptions.defaults("Inspector"));
        inspector.setInspectable(false);
        lifecycle.close(inspector);
        DebugOverrides repeated=new DebugOverrides();Control repeatedControl=new Control();
        repeated.set(repeatedControl,"minimumWidth",10);repeated.set(repeatedControl,"minimumWidth",10);repeated.undo();
        check(!repeated.contains(repeatedControl,"minimumWidth")&&!repeated.canUndo(),"Equivalent live values do not create redundant undo steps");
        lifecycle.close(inspector);
        lifecycle.close(new Window(true));
        lifecycle.clear();
        lifecycle.close(inspector);
        DebugOverrides changes=new DebugOverrides();Control control=new Control().setMinimum(10,12);
        DebugBridge.install(new DebugBridge.Backend(){@Override public Object value(Control c,String key,Object base){return changes.value(c,key,base);}});
        try {
            changes.set(control,"minimumWidth",40);check(control.getMinimumWidth()==40,"Override used by getter");
            control.setMinimum(22,12);check(control.getMinimumWidth()==40,"Application refresh cannot overwrite debug overlay");
            changes.set(control,"minimumWidth",null);check(control.getMinimumWidth()==22,"Reset restores latest base");
            changes.undo();check(control.getMinimumWidth()==40,"Undo reset");changes.undo();check(control.getMinimumWidth()==22,"Undo edit to latest base");
            changes.redo();check(control.getMinimumWidth()==40,"Redo");
            var properties=new ArrayList<>(InspectorProperties.properties(control));
            DebugProperty flags=properties.stream().filter(p->p.key().equals("sizeFlagsHorizontal")).findFirst().orElseThrow();
            rejects(()->flags.parse("7"));rejects(()->flags.parse("16"));rejects(()->flags.parse("1.5"));
            DebugProperty ratio=properties.stream().filter(p->p.key().equals("sizeFlagsStretchRatio")).findFirst().orElseThrow();
            rejects(()->ratio.parse("0"));rejects(()->ratio.parse("NaN"));rejects(()->ratio.parse("Infinity"));
            Box box=new Box().setOrientation(Box.Orientation.HORIZONTAL).add(control);control.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);
            changes.set(box,"gap",8);changes.set(box,"orientation",Box.Orientation.VERTICAL);
            Control next=new Control().setMinimum(20,10);box.add(next);box.layout(new Rect(0,0,100,80),null);
            check(next.getBounds().y()==20,"Box orientation/gap use effective values");
            Padding padding=new Padding(Padding.Insets.all(1)).setChild(next=new Control().setMinimum(3,4));
            changes.set(padding,"left",5);padding.layout(new Rect(0,0,30,30),null);check(next.getBounds().x()==5,"Padding effective inset");
            properties.clear();properties.addAll(InspectorProperties.properties(next));check(properties.stream().noneMatch(p->p.key().equals("x")),"Container-managed placement is read-only");
            Control expandingA = new Control().setMinimum(10,30).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            Control expandingB = new Control().setMinimum(10,10).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
            Box switched = new Box().setOrientation(Box.Orientation.HORIZONTAL).add(expandingA).add(expandingB);
            changes.set(switched,"orientation",Box.Orientation.VERTICAL);
            switched.layout(new Rect(0,0,20,50),null);
            check(expandingA.getBounds().height()==30 && expandingB.getBounds().height()==20,"Switched axis uses matching minimums");
            Control natural = new Control(){@Override public Size measureSize(net.minecraft.client.gui.Font font,Size available){return new Size(10,10);}};
            natural.setSizeFlagsHorizontal(SizeFlags.SIZE_SHRINK_BEGIN);changes.set(natural,"preferredWidth",30);
            new Panel(null).setChild(natural).layout(new Rect(0,0,100,30),null);
            check(natural.getBounds().width()==30,"Preferred override applies to custom natural measurement");
            changes.removeTree(box);check(control.getMinimumWidth()==22,"Binding removal clears subtree overrides and undo history");
            changes.undo();check(control.getMinimumWidth()==22,"Old undo cannot resurrect recycled node edits");
        } finally { DebugBridge.install(null); }
        System.out.println("ORE_INSPECTOR_TEST_PASS");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected invalid value to be rejected");}catch(IllegalArgumentException expected){}}
}
