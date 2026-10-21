package io.github.luyingyilong.ore.control.base;

import io.github.luyingyilong.ore.control.display.Label;
import io.github.luyingyilong.ore.control.form.Form;
import io.github.luyingyilong.ore.control.input.Button;

import io.github.luyingyilong.ore.control.layout.Box;
import io.github.luyingyilong.ore.control.layout.Padding;
import io.github.luyingyilong.ore.control.layout.Scroll;
import io.github.luyingyilong.ore.window.Window;
import net.minecraft.network.chat.Component;

/** Public property access is unambiguous; actions and signal subscription keep their semantics. */
public final class AccessorContractTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        Control control = new Control();
        control.setMinimum(12, 8).setPreferred(20, 10).setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL);
        control.setVisible(false);
        check(control.getMinimumWidth() == 12 && control.getMinimumHeight() == 8 && !control.getVisible(), "Control accessors");
        Label label = new Label().setText(Component.literal("Text")).setHero(true);
        label.setThemeOverride(Label.FONT_SIZE, 12.0F);
        check(label.getText().getString().equals("Text") && label.getThemeItem(Label.FONT_SIZE) == 12 && label.getHero(), "Label accessors");
        Button button = new Button().setThemeTypeVariation("PrimaryButton").setToggleButton(true).setButtonPressed(true);
        check(button.getThemeTypeVariation().equals("PrimaryButton") && button.getButtonPressed(), "Button accessors");
        Box box = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
        check(box.getOrientation() == Box.Orientation.HORIZONTAL && box.getGap() == 4, "Container accessors");
        Scroll scroll = new Scroll().setEmbedded(true).setContent(box);
        check(scroll.getEmbedded(), "Scroll accessors");
        Form form = new Form();
        Control polymorphic = form;
        polymorphic.setVisible(true);
        check(form.getVisible() && form.validate().getValid(), "Setter dispatch is polymorphic without a second visibility state");

        absent(Control.class, "visible", boolean.class);
        absent(Control.class, "visible");
        absent(Control.class, "minimum", int.class, int.class);
        absent(Label.class, "text", Component.class);
        absent(Label.class, "text");
        absent(Button.class, "getType");
        absent(Button.class, "setType", String.class);
        absent(Box.class, "gap", int.class);
        absent(Padding.class, "insets", Padding.Insets.class);
        absent(Scroll.class, "embedded", boolean.class);
        // These are actions and notifications, not properties.
        Control.class.getMethod("addChild", Control.class);
        Control.class.getMethod("queueFree");
        Button.class.getMethod("pressed");
        Window.class.getMethod("dispatchInput", io.github.luyingyilong.ore.event.InputEvent.class);
        control.queueFree(); label.queueFree(); button.queueFree(); scroll.queueFree(); form.queueFree();
        io.github.luyingyilong.ore.control.base.LifecycleScheduler.flush();
        System.out.println("ORE_ACCESSOR_CONTRACT_PASS");
    }

    private static void absent(Class<?> type, String name, Class<?>... parameters) {
        try { type.getMethod(name, parameters); }
        catch (NoSuchMethodException expected) { return; }
        throw new AssertionError("Ambiguous legacy accessor remains: " + type.getSimpleName() + "." + name);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
