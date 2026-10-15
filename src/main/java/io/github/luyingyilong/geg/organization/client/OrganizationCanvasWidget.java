package io.github.luyingyilong.geg.organization.client;

import brachy.modularui.api.widget.Interactable;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.geg.client.ui.*;
import io.github.luyingyilong.geg.client.ui.layout.*;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class OrganizationCanvasWidget extends Widget<OrganizationCanvasWidget> implements Interactable {
    private final Window window = new Window(true);
    private final Overlay overlay = new Overlay();
    private final Box options = new Box().orientation(Box.Orientation.HORIZONTAL).gap(4);
    private final Box memberships = new Box().gap(4);
    private final Label emptyText;
    private final Form createForm;
    private final Dialog emptyDialog;
    private final Modal createModal;
    private final Table<OrganizationSyncPacket.Member> memberTable = createMemberTable();
    private final Modal detailModal;
    private final Modal disbandConfirmation;
    private final Map<UUID, Dropdown<OrganizationSavedData.Operation>> memberMenus = new HashMap<>();
    private UUID selectedOrganizationId;
    private UUID activeMenuMember;
    private Rect activeMenuBounds;
    private UUID pendingDisbandId;
    private boolean pendingCreate;

    OrganizationCanvasWidget(Form createForm, Input name, Input description) {
        this.createForm = createForm;

        memberships.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Panel background = new Panel(OreTheme.BACKGROUND).child(memberships);
        window.content(background).overlay(overlay);

        options.add(option(OreIcons.FLAG, "organization.geg.create",
                this::showCreate));
        options.add(option(OreIcons.ORG, "organization.geg.join",
                this::showJoinUnavailable));
        emptyText = new Label(Component.translatable("organization.geg.none"))
                .wrap(Label.Wrap.WORD).overflow(Label.Overflow.CLIP)
                .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        emptyText.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Box emptyBody = new Box().gap(8).add(emptyText).add(options);
        emptyDialog = new Dialog(270, 0, 0, Component.translatable("organization.geg.title"))
                .content(emptyBody).onClose(() -> Minecraft.getInstance().setScreen(null));

        createForm.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        createModal = new Modal(Component.translatable("organization.geg.create"))
                .content(createForm).onClose(this::showEmpty)
                .action(Component.translatable("organization.geg.back"), this::showEmpty)
                .action(Component.translatable("organization.geg.create_action"),
                        this::submitCreate, Button.Type.PRIMARY);
        memberTable.minimum(0, 60).policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        detailModal = new Modal(Component.empty()).content(memberTable)
                .onClose(this::closeDetails);
        Label warning = new Label(Component.translatable("organization.geg.confirm_disband_body"))
                .wrap(Label.Wrap.WORD).color(0xFFD8D9DC);
        warning.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        disbandConfirmation = new Modal(Component.translatable("organization.geg.confirm_disband"))
                .content(warning).onClose(this::closeDisbandConfirmation)
                .action(Component.translatable("organization.geg.back"),
                        this::closeDisbandConfirmation)
                .action(Component.translatable("organization.geg.disband"),
                        this::confirmDisband, Button.Type.DESTRUCTIVE)
                .depth(300.0F);
        overlay.add(emptyDialog).add(createModal).add(detailModal).add(disbandConfirmation);
        createModal.visible(false);
        detailModal.visible(false);
        disbandConfirmation.visible(false);
        createForm.setVisible(false);
        name.setInputVisible(false);
        description.setInputVisible(false);
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

    private static void notifyUnavailable(String key) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), false);
    }

    private Table<OrganizationSyncPacket.Member> createMemberTable() {
        return new Table<OrganizationSyncPacket.Member>()
                .rowKey(OrganizationSyncPacket.Member::id)
                .empty(Component.translatable("organization.geg.no_members"))
                .column(new Table.Column<>("member", Component.translatable("organization.geg.member"),
                        48, 110, 2, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Member>(
                                member -> Component.literal(member.name()), 0xFFFFFFFF)
                                .wrap(Label.Wrap.CHARACTER).maxLines(2)
                                .overflow(Label.Overflow.ELLIPSIS)))
                .column(new Table.Column<>("role", Component.translatable("organization.geg.role"),
                        42, 64, 1, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Member>(
                                member -> roleLabel(member.role()), 0xFFFFFFFF)
                                .overflow(Label.Overflow.ELLIPSIS)))
                .column(new Table.Column<>("action", Component.translatable("organization.geg.actions"),
                        70, 82, 1, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context,
                                               OrganizationSyncPacket.Member member, Rect bounds) {
                        if (actionsFor(member).isEmpty()) return;
                        Rect trigger = actionBounds(bounds);
                        Dropdown<OrganizationSavedData.Operation> menu = menuFor(member);
                        menu.drawTrigger(context, trigger.x(), trigger.y(),
                                trigger.width(), trigger.height());
                        if (member.id().equals(activeMenuMember))
                            activeMenuBounds = trigger.translated(-getArea().x, -getArea().y);
                    }

                    @Override public boolean click(OrganizationSyncPacket.Member member, Rect bounds,
                                                   int mouseX, int mouseY) {
                        if (actionsFor(member).isEmpty()) return false;
                        Rect trigger = actionBounds(bounds);
                        Dropdown<OrganizationSavedData.Operation> menu = menuFor(member);
                        if (activeMenuMember != null && !activeMenuMember.equals(member.id()))
                            closeMenu();
                        menu.click(trigger.x(), trigger.y(), trigger.width(), trigger.height(),
                                mouseX, mouseY);
                        activeMenuMember = menu.isOpen() ? member.id() : null;
                        activeMenuBounds = menu.isOpen() ? trigger : null;
                        return true;
                    }
                }));
    }

    private static Rect actionBounds(Rect cell) {
        return new Rect(cell.x() + 4, cell.y() + 1,
                Math.max(1, cell.width() - 8), Math.min(20, cell.height() - 2));
    }

    private static Component roleLabel(Organization.Role role) {
        return Component.translatable("organization.geg.role."
                + role.name().toLowerCase(java.util.Locale.ROOT));
    }

    private OrganizationSyncPacket.Summary selectedSummary() {
        if (selectedOrganizationId == null) return null;
        return ClientOrganizationState.organizations().stream()
                .filter(summary -> summary.id().equals(selectedOrganizationId))
                .findFirst().orElse(null);
    }

    private List<OrganizationSavedData.Operation> actionsFor(OrganizationSyncPacket.Member target) {
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (summary == null || Minecraft.getInstance().player == null) return List.of();
        UUID self = Minecraft.getInstance().player.getUUID();
        Organization.Role ownRole = summary.members().stream().filter(member -> member.id().equals(self))
                .map(OrganizationSyncPacket.Member::role).findFirst().orElse(null);
        if (ownRole == null) return List.of();
        if (target.id().equals(self)) return ownRole == Organization.Role.LEADER
                ? List.of(OrganizationSavedData.Operation.DISBAND)
                : List.of(OrganizationSavedData.Operation.LEAVE);
        if (ownRole == Organization.Role.LEADER) {
            if (target.role() == Organization.Role.MEMBER)
                return List.of(OrganizationSavedData.Operation.PROMOTE,
                        OrganizationSavedData.Operation.REMOVE);
            if (target.role() == Organization.Role.OFFICER)
                return List.of(OrganizationSavedData.Operation.DEMOTE,
                        OrganizationSavedData.Operation.REMOVE);
        }
        if (ownRole == Organization.Role.OFFICER && target.role() == Organization.Role.MEMBER)
            return List.of(OrganizationSavedData.Operation.REMOVE);
        return List.of();
    }

    private Dropdown<OrganizationSavedData.Operation> menuFor(OrganizationSyncPacket.Member member) {
        Dropdown<OrganizationSavedData.Operation> menu = memberMenus.computeIfAbsent(member.id(), id ->
                new Dropdown<OrganizationSavedData.Operation>(null, 4)
                        .triggerLabel(Component.translatable("organization.geg.actions"))
                        .checkable(false)
                        .onSelect(operation -> chooseOperation(operation, id)));
        menu.setOptions(actionsFor(member).stream().map(operation -> new Dropdown.Option<>(operation,
                Component.translatable("organization.geg.action."
                        + operation.name().toLowerCase(java.util.Locale.ROOT)))).toList());
        return menu;
    }

    private void chooseOperation(OrganizationSavedData.Operation operation, UUID targetId) {
        closeMenu();
        if (selectedOrganizationId == null) return;
        if (operation == OrganizationSavedData.Operation.DISBAND) {
            pendingDisbandId = selectedOrganizationId;
            disbandConfirmation.visible(true);
        } else {
            OrganizationNetwork.operate(operation, selectedOrganizationId, targetId);
        }
    }

    private void confirmDisband() {
        UUID organizationId = pendingDisbandId;
        closeDisbandConfirmation();
        if (organizationId != null && Minecraft.getInstance().player != null)
            OrganizationNetwork.operate(OrganizationSavedData.Operation.DISBAND,
                    organizationId, Minecraft.getInstance().player.getUUID());
    }

    private void closeDisbandConfirmation() {
        disbandConfirmation.visible(false);
        pendingDisbandId = null;
    }

    private void closeMenu() {
        if (activeMenuMember != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null) menu.close();
        }
        activeMenuMember = null;
        activeMenuBounds = null;
    }

    private void showDetails(UUID organizationId) {
        selectedOrganizationId = organizationId;
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (summary == null) {
            selectedOrganizationId = null;
            return;
        }
        emptyDialog.visible(false);
        createModal.visible(false);
        createForm.setVisible(false);
        memberTable.scrollToTop();
        detailModal.visible(true);
        refreshDetails(summary);
        requestLayout();
    }

    private void refreshDetails(OrganizationSyncPacket.Summary summary) {
        closeMenu();
        memberMenus.clear();
        detailModal.title(Component.literal(summary.name()));
        memberTable.rows(summary.members());
        detailModal.clearActions().action(Component.translatable("organization.geg.apply"),
                () -> notifyUnavailable("organization.geg.applications_unavailable"));
        requestLayout();
    }

    private void closeDetails() {
        detailModal.visible(false);
        closeDisbandConfirmation();
        selectedOrganizationId = null;
        closeMenu();
        requestLayout();
    }

    void showInitialState() {
        emptyDialog.visible(false);
        createModal.visible(false);
        createForm.setVisible(false);
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (!ClientOrganizationState.hasOrganizations()) showEmpty();
        else showMemberships();
    }

    void acceptSnapshot(String resultKey) {
        if (!resultKey.isEmpty()) pendingCreate = false;
        if (detailModal.visible()) {
            OrganizationSyncPacket.Summary selected = selectedSummary();
            if (selected != null) {
                rebuildMemberships();
                refreshDetails(selected);
            } else {
                closeDetails();
                if (ClientOrganizationState.hasOrganizations()) showMemberships();
                else showEmpty();
            }
            return;
        }
        if ("organization.geg.created".equals(resultKey)) {
            showMemberships();
        } else if ("organization.geg.duplicate_name".equals(resultKey)
                && ClientOrganizationState.organizations().stream().anyMatch(summary ->
                summary.name().equalsIgnoreCase(createForm.value("name").strip()))) {
            showMemberships();
        } else if (resultKey.isEmpty() && !createModal.visible()) {
            if (ClientOrganizationState.hasOrganizations()) showMemberships();
            else showEmpty();
        }
    }

    private void showEmpty() {
        createForm.setVisible(false);
        createModal.visible(false);
        detailModal.visible(false);
        if (!ClientOrganizationState.loaded()) {
            showLoading();
            return;
        }
        emptyText.text(Component.translatable("organization.geg.none"));
        options.visible(true);
        emptyDialog.visible(!ClientOrganizationState.hasOrganizations());
        requestLayout();
    }

    private void showLoading() {
        emptyText.text(Component.translatable("organization.geg.loading"));
        options.visible(false);
        emptyDialog.visible(true);
        requestLayout();
    }

    private void showCreate() {
        emptyDialog.visible(false);
        detailModal.visible(false);
        createModal.visible(true);
        createForm.setVisible(true);
        requestLayout();
    }

    private void showJoinUnavailable() {
        notifyUnavailable("organization.geg.join_unavailable");
    }

    private void submitCreate() {
        if (pendingCreate || !createForm.validate()) return;
        String name = createForm.value("name").strip();
        if (name.isBlank()) {
            notifyUnavailable("organization.geg.invalid_name");
            return;
        }
        Organization.JoinPolicy policy = "approval".equals(createForm.value("join_policy"))
                ? Organization.JoinPolicy.APPROVAL : Organization.JoinPolicy.UNRESTRICTED;
        pendingCreate = true;
        OrganizationNetwork.create(name, createForm.value("description").strip(), policy);
    }

    private void showMemberships() {
        rebuildMemberships();
        createForm.setVisible(false);
        createModal.visible(false);
        detailModal.visible(false);
        emptyDialog.visible(false);
        requestLayout();
    }

    private void rebuildMemberships() {
        for (Control child : memberships.children()) memberships.removeChild(child);
        Label heading = new Label(Component.translatable("organization.geg.your_organizations"))
                .color(0x000000).align(Label.Horizontal.CENTER, Label.Vertical.CENTER);
        Panel header = new Panel(OreTheme.HEADER_PANEL).child(heading);
        header.minimum(0, 25).preferred(0, 25)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        heading.minimum(0, 20).policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        memberships.add(header);
        Button createButton = new Button(Component.translatable("organization.geg.create"))
                .type(Button.Type.PRIMARY).onPress(this::showCreate);
        createButton.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Padding createInset = new Padding(new Padding.Insets(0, 4, 0, 4));
        createInset.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        createInset.child(createButton);
        memberships.add(createInset);
        Box orgList = new Box().gap(0);
        orgList.policy(Control.SizePolicy.FILL, Control.SizePolicy.FILL);
        memberships.add(orgList);
        for (OrganizationSyncPacket.Summary summary : ClientOrganizationState.organizations()) {
            Box row = new Box().orientation(Box.Orientation.HORIZONTAL);
            row.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
            Box details = new Box();
            details.policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
            Label name = new Label(Component.literal(summary.name())).color(0xFFFFFFFF);
            name.minimum(0, 16).policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
            details.add(name);
            if (!summary.description().isBlank()) {
                Label description = new Label(Component.literal(summary.description()))
                        .color(0xFFD8D9DC).overflow(Label.Overflow.ELLIPSIS);
                description.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
                details.add(description);
            }
            row.add(details);
            Button detailsButton = new Button(Component.translatable("organization.geg.details"))
                    .onPress(() -> showDetails(summary.id()));
            row.add(detailsButton);
            Panel panel = new Panel(OreTheme.BAR).child(new Padding(Padding.Insets.all(4)).child(row));
            panel.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
            orgList.add(panel);
        }
    }

    private void requestLayout() {
        window.requestLayout();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        Rect area = new Rect(getArea().x, getArea().y, getArea().width, getArea().height);
        if (area.width() == 0 || area.height() == 0) return;
        int modalWidth = Math.max(1, area.width() / 2);
        createModal.size(modalWidth, area.height()).position(area.width() - modalWidth, 0);
        detailModal.size(modalWidth, area.height()).position(area.width() - modalWidth, 0);
        disbandConfirmation.size(Math.min(240, area.width()), Math.min(120, area.height())).centered();
        options.orientation(area.width() < 252 ? Box.Orientation.VERTICAL : Box.Orientation.HORIZONTAL);
        window.layoutViewport(area.width(), area.height(), context.getFont());
        window.draw(context, area.x(), area.y());
        if (detailModal.visible() && !disbandConfirmation.visible() && activeMenuMember != null
                && activeMenuBounds != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.isOpen()) {
                GuiGraphics graphics = context.getGraphics();
                graphics.flush();
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 350.0F);
                try {
                    Rect anchor = activeMenuBounds.translated(area.x(), area.y());
                    menu.drawMenu(context, anchor.x(), menuY(menu, activeMenuBounds) + area.y(),
                            anchor.width(), anchor.height());
                    graphics.flush();
                } finally {
                    graphics.pose().popPose();
                }
            }
        }
    }

    private int menuY(Dropdown<OrganizationSavedData.Operation> menu, Rect anchor) {
        int bottom = detailModal.panelBounds().bottom();
        return anchor.bottom() + menu.menuHeight() <= bottom ? anchor.y()
                : anchor.y() - menu.menuHeight() - anchor.height();
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int button) {
        if (button != 0) return Interactable.Result.IGNORE;
        int mouseX = getContext().getAbsMouseX() - getArea().x;
        int mouseY = getContext().getAbsMouseY() - getArea().y;
        if (activeMenuMember != null && activeMenuBounds != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.isOpen()) {
                menu.click(activeMenuBounds.x(), menuY(menu, activeMenuBounds),
                        activeMenuBounds.width(), activeMenuBounds.height(), mouseX, mouseY);
                if (!menu.isOpen()) closeMenu();
                return Interactable.Result.STOP;
            }
        }
        return window.dispatchPointer(mouseX, mouseY)
                ? Interactable.Result.STOP : Interactable.Result.IGNORE;
    }

    @Override public boolean onMouseScrolled(double delta) {
        if (activeMenuMember != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.scroll(delta)) return true;
        }
        if (detailModal.visible()) {
            memberTable.scrollBy(-(int) Math.signum(delta) * (memberTable.compact() ? 36 : 23));
            closeMenu();
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && disbandConfirmation.visible()) {
            closeDisbandConfirmation();
            return Interactable.Result.STOP;
        }
        if (keyCode == 256 && detailModal.visible()) {
            if (activeMenuMember != null) closeMenu();
            else closeDetails();
            return Interactable.Result.STOP;
        }
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

        @Override
        public int measureWidth(Font font, int available) {
            return 16;
        }

        @Override
        public int measureHeight(Font font, int available) {
            return 16;
        }

        @Override
        protected void render(ModularGuiContext context, int screenX, int screenY) {
            Rect area = screenBounds(screenX, screenY);
            int x = area.x() + (area.width() - 16) / 2;
            int y = area.y() + (area.height() - 16) / 2;
            context.getGraphics().setColor(1.0F, 1.0F, 1.0F, 1.0F);
            context.getGraphics().blit(texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        }
    }
}
