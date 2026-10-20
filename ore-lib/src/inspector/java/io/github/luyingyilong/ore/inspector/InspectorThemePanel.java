package io.github.luyingyilong.ore.inspector;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.debug.DebugThemes;
import net.minecraft.network.chat.Component;

/** The third view is deliberately read-only; editing still belongs to the property panel. */
final class InspectorThemePanel {
    private final Box body=new Box().setGap(4);
    private DebugThemes.Snapshot previous;
    Box body() { return body; }
    void update(DebugThemes.Snapshot next) {
        if(next.equals(previous)) return;
        previous=next;
        for(var child:body.getChildren()) { body.removeChild(child); child.queueFree(); }
        line(Component.translatable("inspector.ore.theme.context",next.type(),next.variation(),next.state()),false);
        for(var item:next.items()) {
            line(Component.literal(item.key()+" = "+item.value()),false);
            line(Component.translatable("inspector.ore.theme.source",item.source()),true);
            for(var trace:item.chain()) line(Component.literal("  "+trace.kind()+" | "+trace.theme()+" | "+trace.resource()
                    +" | "+trace.node()+" | "+trace.item()+" | "+trace.reason()),true);
        }
    }
    private void line(Component text,boolean secondary) {
        Label label=new Label(text).setWrap(Label.Wrap.CHARACTER);
        if(secondary) label.setThemeTypeVariation("LabelSecondary");
        body.addChild(label);
    }
}
