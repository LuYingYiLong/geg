package io.github.luyingyilong.geg.organization.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.Button;
import io.github.luyingyilong.geg.client.ui.Form;
import io.github.luyingyilong.geg.client.ui.Input;
import io.github.luyingyilong.geg.client.ui.Label;
import io.github.luyingyilong.geg.client.ui.Dialog;
import io.github.luyingyilong.geg.client.ui.Modal;
import io.github.luyingyilong.geg.client.ui.OreIcons;
import io.github.luyingyilong.geg.client.ui.OreTheme;
import io.github.luyingyilong.geg.client.ui.layout.Box;
import io.github.luyingyilong.geg.client.ui.layout.Control;
import io.github.luyingyilong.geg.client.ui.layout.Overlay;
import io.github.luyingyilong.geg.client.ui.layout.Padding;
import io.github.luyingyilong.geg.client.ui.layout.Panel;
import io.github.luyingyilong.geg.client.ui.layout.Rect;
import io.github.luyingyilong.geg.client.ui.layout.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

final class OrganizationCanvasWidget extends Widget<OrganizationCanvasWidget> implements Interactable {
    private final Window window = new Window(true);
    private final Overlay overlay = new Overlay();
    private final Box options = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
    private final Form createForm;
    private final Dialog emptyDialog;
    private final Modal createModal;

    OrganizationCanvasWidget(Form createForm, Input name, Input description) {
        this.createForm = createForm;

        Label title = new Label(Component.translatable("organization.geg.title"))
                .color(0xFF1E1E1F).align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        title.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Panel background = new Panel(OreTheme.BACKGROUND).child(
                new Box().add(new Padding(new Padding.Insets(5, 8, 5, 8)).child(title)));
        window.content(background).overlay(overlay);

        options.add(option(OreIcons.FLAG, "organization.geg.create",
                this::showCreate));
        options.add(option(OreIcons.ORG, "organization.geg.join",
                this::showJoinUnavailable));
        Label emptyText = new Label(Component.translatable("organization.geg.none"))
                .wrap(Label.Wrap.WORD).overflow(Label.Overflow.CLIP)
                .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        emptyText.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Box emptyBody = new Box().gap(8).add(emptyText).add(options);
        emptyDialog = new Dialog(270, 0, 0, Component.translatable("organization.geg.title"))
                .content(emptyBody).onClose(() -> Minecraft.getInstance().setScreen(null));

        Label preview = new Label(Component.translatable("organization.geg.preview"))
                .wrap(Label.Wrap.WORD).overflow(Label.Overflow.CLIP)
                .color(0xFFD8D9DC);
        preview.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        createForm.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Box createBody = new Box().gap(4).add(createForm).add(preview);
        createModal = new Modal(Component.translatable("organization.geg.create"))
                .content(createBody).onClose(this::showEmpty)
                .action(Component.translatable("organization.geg.back"), this::showEmpty)
                .action(Component.translatable("organization.geg.create_action"),
                        this::showCreateUnavailable, Button.Type.PRIMARY);
        overlay.add(emptyDialog).add(createModal);
        createModal.visible(false);
        createForm.setVisible(false);
        name.setInputVisible(false);
        description.setInputVisible(false);
    }

    void showInitialState() {
        emptyDialog.visible(false);
        createModal.visible(false);
        createForm.setVisible(false);
        if (!ClientOrganizationState.hasOrganizations()) showEmpty();
    }

    private static Panel option(ResourceLocation icon, String label, Runnable action) {
        IconControl image = new IconControl(icon);
        image.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        Button button = new Button(Component.translatable(label)).onPress(action);
        button.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Box column = new Box().gap(6).add(image).add(button);
        Panel panel = new Panel(null).child(new Padding(Padding.Insets.all(4)).child(column));
        panel.minimum(106, 130).policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
        return panel;
    }

    private void showEmpty() {
        createForm.setVisible(false);
        createModal.visible(false);
        emptyDialog.visible(true);
        requestLayout();
    }

    private void showCreate() {
        emptyDialog.visible(false);
        createModal.visible(true);
        createForm.setVisible(true);
        requestLayout();
    }

    private void showJoinUnavailable() {
        notifyUnavailable("organization.geg.join_unavailable");
    }

    private void showCreateUnavailable() {
        notifyUnavailable("organization.geg.create_unavailable");
    }

    private static void notifyUnavailable(String key) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), false);
    }

    private void requestLayout() {
        window.requestLayout();
    }

    @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        Rect area = new Rect(getArea().x, getArea().y, getArea().width, getArea().height);
        if (area.width() == 0 || area.height() == 0) return;
        int modalWidth = Math.max(1, area.width() / 2);
        createModal.size(modalWidth, area.height()).position(area.width() - modalWidth, 0);
        options.orientation(area.width() < 252 ? Box.Orientation.VERTICAL : Box.Orientation.HORIZONTAL);
        window.layoutViewport(area.width(), area.height(), context.getFont());
        window.draw(context, area.x(), area.y());
    }

    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        if (button != 0) return Interactable.Result.IGNORE;
        return window.dispatchPointer(getContext().getAbsMouseX() - getArea().x,
                getContext().getAbsMouseY() - getArea().y)
                ? Interactable.Result.STOP : Interactable.Result.IGNORE;
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && createModal.visible()) {
            showEmpty();
            return Interactable.Result.STOP;
        }
        return Interactable.Result.IGNORE;
    }

    private static final class IconControl extends Control {
        private final ResourceLocation texture;

        private IconControl(ResourceLocation texture) {
            this.texture = texture;
            minimum(16, 16);
        }

        @Override public int measureWidth(Font font, int available) { return 16; }
        @Override public int measureHeight(Font font, int available) { return 16; }

        @Override protected void render(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
            int x = area.x() + (area.width() - 16) / 2;
            int y = area.y() + (area.height() - 16) / 2;
            context.getGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.getGraphics().blit(texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }
}
