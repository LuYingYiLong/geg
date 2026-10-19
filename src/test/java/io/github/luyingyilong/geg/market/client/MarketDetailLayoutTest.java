package io.github.luyingyilong.geg.market.client;

import io.github.luyingyilong.ore.signal.Signals;

import brachy.modularui.theme.WidgetThemeKey;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.DescriptionItem;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.FormItem;
import io.github.luyingyilong.ore.Modal;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.Window;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.Map;

/** Stable descriptions replace compact sentences inside the market's framed secondary modal. */
public final class MarketDetailLayoutTest {
    public static void main(String[] args) throws ReflectiveOperationException {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0}; text.accept((index, style, codepoint) -> { count[0]++; return true; }); return count[0] * 6;
            }
        };
        MarketDetailDescriptions details = new MarketDetailDescriptions();
        Map<MarketDetailDescriptions.Field, Component> own = Map.of(
                MarketDetailDescriptions.Field.STATUS, Component.literal("售卖中"),
                MarketDetailDescriptions.Field.SELLER, Component.literal("LongPlayerName"),
                MarketDetailDescriptions.Field.QUANTITY, Component.literal("128"),
                MarketDetailDescriptions.Field.PRICE, Component.literal("1.25"),
                MarketDetailDescriptions.Field.FEE, Component.literal("2.50%"));
        details.show(own);
        DescriptionItem seller = details.descriptions().item("SELLER");
        check(seller.content().getString().equals("LongPlayerName"), "Seller text remains a separate description value");
        for (int width : new int[]{109, 110, 111, 240, 322}) {
            Size measured = details.measureSize(font, new Size(width, 1000));
            details.layout(new Rect(0, 0, width, measured.height()), font);
            for (DescriptionItem field : details.descriptions().items()) if (field.visible()) {
                check(field.absoluteBounds().bottom() <= measured.height(), "Description fields fit their measured height");
                check(field.contentNode().absoluteBounds().bottom() <= field.absoluteBounds().bottom(), "Wrapped field values remain inside the row");
            }
        }
        details.show(Map.of(MarketDetailDescriptions.Field.ESCROW, Component.literal("12.34")));
        check(!seller.visible() && details.descriptions().item("ESCROW").visible(), "Switching detail kinds hides unrelated fields");
        details.show(own);
        check(details.descriptions().item("SELLER") == seller, "Refresh keeps stable keyed field controls");

        // Inject empty themes just like ModalLayoutTest; this test does not bootstrap Forge/MUI textures.
        var constructor = Modal.class.getDeclaredConstructor(Component.class, WidgetThemeKey.class,
                WidgetThemeKey.class, WidgetThemeKey.class);
        constructor.setAccessible(true);
        Modal modal = constructor.newInstance(Component.literal("Item"), null, null, null).size(340, 224).content(details)
                .addAction(Signals.configure(new Button(Component.literal("撤单")).type(Button.Type.DESTRUCTIVE), actionButton -> actionButton.pressed().connect(actionButton, () -> { })))
                .addAction(Signals.configure(new Button(Component.literal("改价")).type(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })));
        Window window = new Window(true).content(new Control()).overlay(new Overlay().add(modal));
        for (int width : new int[]{127, 128, 129, 427, 640}) {
            window.layoutViewport(width, 240, font);
            check(modal.panelBounds().x() == (width - Math.min(340, width)) / 2, "Secondary modal stays centered");
            check(modal.contentBounds().x() == modal.panelBounds().x() + 9, "Frame and body inset retain eight pixels of inner padding");
            check(modal.contentBounds().bottom() <= modal.panelBounds().bottom(), "Description viewport stays inside the framed modal");
        }

        // Blocking the background search must not hide or disable its native MUI widget.
        Control search = new Control().position(10, 0).requestedSize(138, 18).focusable(true);
        window.content(new Control().addChild(search));
        Control quantityField = new Control().preferred(100, 20).focusable(true);
        Form quantityForm = new Form().add(new FormItem("quantity", Component.literal("Quantity"), quantityField, () -> "1"));
        quantityForm.setVisible(true);
        MarketCanvasWidget.QuantityContent quantityContent = new MarketCanvasWidget.QuantityContent(index -> { });
        quantityContent.bindForm(quantityForm);
        Modal quantity = constructor.newInstance(Component.literal("Quantity"), null, null, null)
                .size(224, 0).centered().depth(300).content(quantityContent)
                .addAction(Signals.configure(new Button(Component.literal("Cancel")).type(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })))
                .addAction(Signals.configure(new Button(Component.literal("Confirm")).type(Button.Type.PRIMARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })));
        Overlay layers = new Overlay();
        // Move the existing secondary modal into the new two-level overlay.
        modal.parent().removeChild(modal);
        window.overlay(layers.add(modal).add(quantity));
        quantity.visible(false);
        check(search.visible() && !window.focusManager().allowsNativeInput(search),
                "Search stays visible while the secondary modal blocks native input");
        check(window.focusManager().allowsNativeInput(seller.contentNode()), "Secondary content remains interactive");
        quantity.visible(true);
        for (int width : new int[]{127, 128, 129, 427, 640}) {
            window.layoutViewport(width, 240, font);
            check(quantity.panelBounds().x() == (width - Math.min(224, width)) / 2, "Quantity modal stays centered");
            check(quantity.contentBounds().x() == quantity.panelBounds().x() + 9, "Quantity uses the same framed body inset");
            check(quantity.contentBounds().bottom() <= quantity.panelBounds().bottom(), "Quantity viewport stays inside the panel");
            Rect body = quantity.contentBounds();
            check(body.height() == quantityContent.measureSize(font, new Size(body.width(), 240)).height(),
                    "Auto height includes all content at the fitted width, including wrapped quick buttons");
            check(quantityField.absoluteBounds().y() >= body.y() && quantityField.absoluteBounds().bottom() <= body.bottom(),
                    "The entire quantity field stays visible");
            for (Control child : quantityContent.children()) if (child instanceof Button) {
                check(child.absoluteBounds().y() >= body.y() && child.absoluteBounds().bottom() <= body.bottom(),
                        "Every quick quantity button stays inside the body");
            }
            checkFooterActions(quantity, quantityContent, body, quantity.panelBounds());
        }
        int regularHeight = quantity.panelBounds().height();
        window.layoutViewport(127, 240, font);
        check(quantity.panelBounds().height() > regularHeight, "Wrapping quick buttons increases modal height");
        quantityField.preferred(100, 30);
        window.layoutViewport(640, 240, font);
        check(quantity.panelBounds().height() == regularHeight + 10, "Changed form height updates the whole modal");
        check(quantity.contentDepth() > modal.contentDepth(), "Quantity modal draws above order details");
        check(window.focusManager().allowsNativeInput(quantityField), "Only the quantity layer accepts native input");
        check(!window.focusManager().allowsNativeInput(seller.contentNode()), "Quantity blocks underlying order fields");
        check(search.visible() && !window.focusManager().allowsNativeInput(search), "Search stays visible behind both modals");
        quantity.visible(false);
        check(window.focusManager().allowsNativeInput(seller.contentNode()), "Closing quantity restores detail interaction");
        modal.visible(false);
        check(search.visible() && window.focusManager().allowsNativeInput(search), "Closing details restores search interaction");
    }

    private static void checkFooterActions(Control node, Control bodyContent, Rect body, Rect panel) {
        if (node == bodyContent) return;
        if (node instanceof Button button && button.icon() == null) {
            check(button.absoluteBounds().y() >= body.bottom(), "Cancel and confirm stay below the body in the footer");
            check(button.absoluteBounds().bottom() <= panel.bottom(), "Footer actions stay inside the modal");
        }
        for (Control child : node.children()) checkFooterActions(child, bodyContent, body, panel);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
