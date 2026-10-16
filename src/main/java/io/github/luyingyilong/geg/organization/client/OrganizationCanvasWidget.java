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
import org.lwjgl.glfw.GLFW;

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
    private final Drawer createDrawer;
    private final Table<OrganizationSyncPacket.Member> memberTable = createMemberTable();
    private final Table<OrganizationSyncPacket.Application> applicationTable = createApplicationTable();
    private final Table<OrganizationSyncPacket.Directory> joinTable = createJoinTable();
    private final Drawer detailDrawer;
    private final Modal applicationsModal;
    private final Drawer joinDrawer;
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
                this::showJoin));
        emptyText = new Label(Component.translatable("organization.geg.none"))
                .wrap(Label.Wrap.WORD).overflow(Label.Overflow.CLIP)
                .align(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        emptyText.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Box emptyBody = new Box().gap(8).add(emptyText).add(options);
        emptyDialog = new Dialog(270, 0, 0, Component.translatable("organization.geg.title"))
                .content(emptyBody).onClose(() -> Minecraft.getInstance().setScreen(null));

        createForm.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        createDrawer = new Drawer(Component.translatable("organization.geg.create"))
                .content(createForm).onClose(this::onCreateClosed)
                .action(Component.translatable("organization.geg.back"), this::closeCreate)
                .action(Component.translatable("organization.geg.create_action"),
                        this::submitCreate, Button.Type.PRIMARY);
        memberTable.minimum(0, 60).policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        detailDrawer = new Drawer(Component.empty()).content(memberTable)
                .onClose(this::onDetailsClosed);
        applicationTable.headerVisible(false).minimum(0, 60)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        applicationsModal = new Modal(Component.translatable("organization.geg.applications"))
                .content(applicationTable).onClose(this::closeApplications).depth(300.0F);
        joinTable.headerVisible(false).minimum(0, 60)
                .policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        joinDrawer = new Drawer(Component.translatable("organization.geg.join"))
                .content(joinTable).onClose(this::onJoinClosed);
        Label warning = new Label(Component.translatable("organization.geg.confirm_disband_body"))
                .wrap(Label.Wrap.WORD).color(0xFFD8D9DC);
        warning.policy(Control.SizePolicy.FILL, Control.SizePolicy.EXPAND);
        disbandConfirmation = new Modal(Component.translatable("organization.geg.confirm_disband"))
                .content(warning).onClose(this::closeDisbandConfirmation)
                .action(Component.translatable("organization.geg.back"),
                        this::closeDisbandConfirmation)
                .action(Component.translatable("organization.geg.disband"),
                        this::confirmDisband, Button.Type.DESTRUCTIVE)
                .depth(400.0F);
        overlay.add(emptyDialog).add(createDrawer).add(detailDrawer)
                .add(joinDrawer).add(applicationsModal).add(disbandConfirmation);
        createDrawer.visible(false);
        detailDrawer.visible(false);
        joinDrawer.visible(false);
        applicationsModal.visible(false);
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

                    @Override public List<Rect> focusTargets(OrganizationSyncPacket.Member member,
                                                              Rect bounds) {
                        return actionsFor(member).isEmpty() ? List.of()
                                : List.of(actionBounds(bounds));
                    }
                }));
    }

    private Table<OrganizationSyncPacket.Application> createApplicationTable() {
        return new Table<OrganizationSyncPacket.Application>()
                .headerVisible(false)
                .rowKey(OrganizationSyncPacket.Application::id)
                .empty(Component.translatable("organization.geg.no_applications"))
                .column(new Table.Column<>("player",
                        Component.translatable("organization.geg.player"),
                        48, 120, 1, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Application>(
                                application -> Component.literal(application.name()), 0xFFFFFFFF)
                                .overflow(Label.Overflow.ELLIPSIS)))
                .column(new Table.Column<>("action",
                        Component.translatable("organization.geg.actions"),
                        112, 128, 0, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context,
                                               OrganizationSyncPacket.Application application,
                                               Rect bounds) {
                        Rect reject = reviewButtonBounds(bounds, false);
                        Rect approve = reviewButtonBounds(bounds, true);
                        Button.draw(context, reject.x(), reject.y(), reject.width(), reject.height(),
                                Component.translatable("organization.geg.reject"),
                                Button.Type.DESTRUCTIVE);
                        Button.draw(context, approve.x(), approve.y(), approve.width(), approve.height(),
                                Component.translatable("organization.geg.approve"), Button.Type.PRIMARY);
                    }

                    @Override public boolean click(OrganizationSyncPacket.Application application,
                                                   Rect bounds, int mouseX, int mouseY) {
                        if (selectedOrganizationId == null) return false;
                        if (reviewButtonBounds(bounds, false).contains(mouseX, mouseY)) {
                            OreSounds.play(OreSounds.Cue.CLICK);
                            OrganizationNetwork.operate(OrganizationSavedData.Operation.REJECT,
                                    selectedOrganizationId, application.id());
                            return true;
                        }
                        if (reviewButtonBounds(bounds, true).contains(mouseX, mouseY)) {
                            OreSounds.play(OreSounds.Cue.CLICK);
                            OrganizationNetwork.operate(OrganizationSavedData.Operation.APPROVE,
                                    selectedOrganizationId, application.id());
                            return true;
                        }
                        return false;
                    }

                    @Override public List<Rect> focusTargets(OrganizationSyncPacket.Application application,
                                                              Rect bounds) {
                        return List.of(reviewButtonBounds(bounds, false),
                                reviewButtonBounds(bounds, true));
                    }
                }));
    }

    private static Rect reviewButtonBounds(Rect cell, boolean right) {
        int width = Math.max(1, (cell.width() - 6) / 2);
        return new Rect(cell.x() + (right ? width + 4 : 2), cell.y() + 2,
                width, Math.min(Button.Size.SMALL.height(), Math.max(1, cell.height() - 4)));
    }

    private Table<OrganizationSyncPacket.Directory> createJoinTable() {
        return new Table<OrganizationSyncPacket.Directory>()
                .headerVisible(false)
                .rowKey(OrganizationSyncPacket.Directory::id)
                .empty(Component.translatable("organization.geg.no_available_organizations"))
                .column(new Table.Column<>("organization",
                        Component.translatable("organization.geg.name"),
                        50, 120, 1, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Directory>(
                                entry -> Component.literal(entry.name()), 0xFFFFFFFF)
                                .overflow(Label.Overflow.ELLIPSIS)))
                .column(new Table.Column<>("action",
                        Component.translatable("organization.geg.actions"),
                        72, 92, 0, width -> 20, new Table.Cell<>() {
                    @Override public void draw(ModularGuiContext context,
                                               OrganizationSyncPacket.Directory entry,
                                               Rect bounds) {
                        Rect button = actionBounds(bounds);
                        boolean hovered = button.contains(context.getAbsMouseX(), context.getAbsMouseY());
                        Button.draw(context, button.x(), button.y(), button.width(), button.height(),
                                Component.translatable(entry.pending()
                                        ? hovered ? "organization.geg.cancel_application"
                                        : "organization.geg.pending_application"
                                        : entry.joinPolicy() == Organization.JoinPolicy.APPROVAL
                                        ? "organization.geg.apply" : "organization.geg.join"),
                                entry.pending() && hovered ? Button.Type.DESTRUCTIVE : Button.Type.SECONDARY);
                    }

                    @Override public boolean click(OrganizationSyncPacket.Directory entry,
                                                   Rect bounds, int mouseX, int mouseY) {
                        if (!joinDrawer.isOpen() || !actionBounds(bounds).contains(mouseX, mouseY)
                                || Minecraft.getInstance().player == null) return false;
                        OreSounds.play(OreSounds.Cue.CLICK);
                        OrganizationNetwork.operate(entry.pending()
                                        ? OrganizationSavedData.Operation.CANCEL_APPLICATION
                                        : OrganizationSavedData.Operation.APPLY,
                                entry.id(), Minecraft.getInstance().player.getUUID());
                        return true;
                    }

                    @Override public List<Rect> focusTargets(OrganizationSyncPacket.Directory entry,
                                                              Rect bounds) {
                        return List.of(actionBounds(bounds));
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
        if (!detailDrawer.isOpen() || selectedOrganizationId == null) return;
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
        OrganizationSyncPacket.Summary summary = ClientOrganizationState.organizations().stream()
                .filter(entry -> entry.id().equals(organizationId)).findFirst().orElse(null);
        if (summary == null) return;
        // Finish a previous close callback before assigning the newly selected organization.
        detailDrawer.open();
        selectedOrganizationId = organizationId;
        emptyDialog.visible(false);
        createDrawer.visible(false);
        joinDrawer.close();
        memberTable.scrollToTop();
        detailDrawer.bodyScroll().scrollToTop();
        refreshDetails(summary);
        requestLayout();
    }

    private void refreshDetails(OrganizationSyncPacket.Summary summary) {
        closeMenu();
        memberMenus.clear();
        detailDrawer.title(Component.literal(summary.name()));
        memberTable.rows(summary.members());
        detailDrawer.clearActions();
        Organization.Role ownRole = ownRole(summary);
        if (ownRole == Organization.Role.LEADER || ownRole == Organization.Role.OFFICER) {
            detailDrawer.action(Component.translatable("organization.geg.applications"),
                    this::showApplications);
            applicationTable.rows(summary.applications());
        } else {
            applicationsModal.visible(false);
        }
        requestLayout();
    }

    private Organization.Role ownRole(OrganizationSyncPacket.Summary summary) {
        if (Minecraft.getInstance().player == null) return null;
        UUID self = Minecraft.getInstance().player.getUUID();
        return summary.members().stream().filter(member -> member.id().equals(self))
                .map(OrganizationSyncPacket.Member::role).findFirst().orElse(null);
    }

    private void showApplications() {
        if (!detailDrawer.isOpen()) return;
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (summary == null) return;
        Organization.Role ownRole = ownRole(summary);
        if (ownRole != Organization.Role.LEADER && ownRole != Organization.Role.OFFICER) return;
        closeMenu();
        applicationTable.rows(summary.applications());
        applicationTable.scrollToTop();
        applicationsModal.visible(true);
        requestLayout();
    }

    private void closeApplications() {
        applicationsModal.visible(false);
        requestLayout();
    }

    private void showJoin() {
        joinDrawer.open();
        emptyDialog.visible(false);
        createDrawer.visible(false);
        detailDrawer.visible(false);
        applicationsModal.visible(false);
        closeDisbandConfirmation();
        closeMenu();
        joinTable.rows(ClientOrganizationState.directory());
        joinTable.scrollToTop();
        joinDrawer.bodyScroll().scrollToTop();
        requestLayout();
    }

    private void onJoinClosed() {
        if (createDrawer.visible() || detailDrawer.visible() || applicationsModal.visible()
                || disbandConfirmation.visible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    private void closeDetails() {
        applicationsModal.visible(false);
        closeDisbandConfirmation();
        closeMenu();
        detailDrawer.close();
        requestLayout();
    }

    private void onDetailsClosed() {
        selectedOrganizationId = null;
        closeMenu();
        if (createDrawer.visible() || joinDrawer.visible() || applicationsModal.visible()
                || disbandConfirmation.visible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    void showInitialState() {
        emptyDialog.visible(false);
        createDrawer.visible(false);
        createForm.setVisible(false);
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (!ClientOrganizationState.hasOrganizations()) showEmpty();
        else showMemberships();
    }

    void acceptSnapshot(String resultKey) {
        if (!resultKey.isEmpty()) pendingCreate = false;
        if (joinDrawer.isOpen()) {
            joinTable.rows(ClientOrganizationState.directory());
            if ("organization.geg.result.apply".equals(resultKey)
                    || "organization.geg.result.approved_notice".equals(resultKey)) {
                showMemberships();
            } else {
                rebuildMemberships();
                requestLayout();
            }
            return;
        }
        if (detailDrawer.isOpen()) {
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
        } else if (resultKey.isEmpty() && !createDrawer.visible()) {
            if (ClientOrganizationState.hasOrganizations()) showMemberships();
            else showEmpty();
        }
    }

    private void showEmpty() {
        createDrawer.visible(false);
        detailDrawer.visible(false);
        joinDrawer.visible(false);
        applicationsModal.visible(false);
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
        createDrawer.open();
        emptyDialog.visible(false);
        detailDrawer.visible(false);
        joinDrawer.visible(false);
        applicationsModal.visible(false);
        createForm.setVisible(true);
        createDrawer.bodyScroll().scrollToTop();
        requestLayout();
    }

    private void closeCreate() {
        createDrawer.close();
        requestLayout();
    }

    private void onCreateClosed() {
        // Keep the form visible throughout the exit animation, then restore the current base view.
        createForm.setVisible(false);
        if (detailDrawer.visible() || joinDrawer.visible() || applicationsModal.visible()
                || disbandConfirmation.visible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    private void submitCreate() {
        if (!createDrawer.isOpen() || pendingCreate || !createForm.validate()) return;
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
        createDrawer.visible(false);
        detailDrawer.visible(false);
        joinDrawer.visible(false);
        applicationsModal.visible(false);
        emptyDialog.visible(false);
        requestLayout();
    }

    private void rebuildMemberships() {
        for (Control child : memberships.children()) memberships.removeChild(child);
        Header header = new Header(Component.translatable("organization.geg.your_organizations"))
                .titleColor(0xFF000000);
        memberships.add(header);
        Button createButton = new Button(Component.translatable("organization.geg.create"))
                .type(Button.Type.PRIMARY).onPress(this::showCreate);
        createButton.policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
        Button joinButton = new Button(Component.translatable("organization.geg.join"))
                .onPress(this::showJoin);
        joinButton.policy(Control.SizePolicy.EXPAND, Control.SizePolicy.CONTENT);
        Box actions = new Box().orientation(Box.Orientation.HORIZONTAL).gap(2)
                .add(createButton).add(joinButton);
        actions.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        Padding createInset = new Padding(new Padding.Insets(0, 4, 0, 4));
        createInset.policy(Control.SizePolicy.FILL, Control.SizePolicy.CONTENT);
        createInset.child(actions);
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
        applicationsModal.size(Math.min(320, area.width()), Math.min(220, area.height())).centered();
        disbandConfirmation.size(Math.min(240, area.width()), Math.min(120, area.height())).centered();
        options.orientation(area.width() < 252 ? Box.Orientation.VERTICAL : Box.Orientation.HORIZONTAL);
        window.layoutViewport(area.width(), area.height(), context.getFont());
        window.draw(context, area.x(), area.y());
        if (!detailDrawer.isOpen()) closeMenu();
        if (detailDrawer.isOpen() && !joinDrawer.visible() && !applicationsModal.visible()
                && !disbandConfirmation.visible() && activeMenuMember != null
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
        int bottom = detailDrawer.panelBounds().bottom();
        return anchor.bottom() + menu.menuHeight() <= bottom ? anchor.y()
                : anchor.y() - menu.menuHeight() - anchor.height();
    }

    @Override
    public @NotNull Interactable.Result onMousePressed(int button) {
        window.focusManager().pointerActivity();
        if (button != 0) return Interactable.Result.IGNORE;
        int mouseX = getContext().getAbsMouseX() - getArea().x;
        int mouseY = getContext().getAbsMouseY() - getArea().y;
        if (!detailDrawer.isOpen() || joinDrawer.visible()) closeMenu();
        if (activeMenuMember != null && activeMenuBounds != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.isOpen()) {
                menu.click(activeMenuBounds.x(), menuY(menu, activeMenuBounds),
                        activeMenuBounds.width(), activeMenuBounds.height(), mouseX, mouseY);
                if (!menu.isOpen()) closeMenu();
                return Interactable.Result.STOP;
            }
        }
        return window.pointerPressed(mouseX, mouseY, getContext())
                ? Interactable.Result.STOP : Interactable.Result.IGNORE;
    }

    @Override public boolean onMouseScrolled(double delta) {
        if (createDrawer.visible() && !applicationsModal.visible() && !joinDrawer.visible()
                && !detailDrawer.visible() && !disbandConfirmation.visible()) {
            return createDrawer.dispatchScroll(getContext().getAbsMouseX() - getArea().x,
                    getContext().getAbsMouseY() - getArea().y, delta, false);
        }
        if (applicationsModal.visible()) {
            applicationTable.scrollBy(-(int) Math.signum(delta) *
                    (applicationTable.compact() ? 36 : 23));
            return true;
        }
        if (joinDrawer.visible()) {
            return scrollTableDrawer(joinDrawer, joinTable, delta);
        }
        if (activeMenuMember != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.scroll(delta)) return true;
        }
        if (detailDrawer.visible()) {
            closeMenu();
            return scrollTableDrawer(detailDrawer, memberTable, delta);
        }
        return false;
    }

    private boolean scrollTableDrawer(Drawer drawer, Table<?> table, double delta) {
        Scroll body = drawer.bodyScroll();
        int mouseX = getContext().getAbsMouseX() - getArea().x;
        int mouseY = getContext().getAbsMouseY() - getArea().y;
        if (drawer.isOpen() && body.hit(mouseX, mouseY)) {
            if (body.maxScrollY() > 0 || body.maxScrollX() > 0)
                drawer.dispatchScroll(mouseX, mouseY, delta, false);
            else table.scrollBy(-(int) Math.signum(delta) * (table.compact() ? 36 : 23));
        }
        return true;
    }

    @Override public void onMouseDrag(int button, double dragX, double dragY) {
        if (button != 0 || applicationsModal.visible()
                || disbandConfirmation.visible()) return;
        Drawer drawer = joinDrawer.visible() ? joinDrawer : detailDrawer.visible() ? detailDrawer
                : createDrawer.visible() ? createDrawer : null;
        if (drawer != null && drawer.isOpen()) drawer.bodyScroll().dragTo(getContext().getAbsMouseX() - getArea().x,
                getContext().getAbsMouseY() - getArea().y);
    }

    @Override public boolean onMouseReleased(int button) {
        if (button != 0) return false;
        boolean released = window.pointerReleased(getContext().getAbsMouseX() - getArea().x,
                getContext().getAbsMouseY() - getArea().y);
        boolean details = detailDrawer.bodyScroll().endDrag();
        boolean create = createDrawer.bodyScroll().endDrag();
        boolean join = joinDrawer.bodyScroll().endDrag();
        return details || create || join || released;
    }

    @Override public boolean onKeyReleased(int keyCode, int scanCode, int modifiers) {
        return window.keyReleased(keyCode, modifiers, getContext());
    }

    @Override public void dispose() {
        window.cancelInteractions();
        super.dispose();
    }

    @Override
    public @NotNull Interactable.Result onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && activeMenuMember != null
                && !applicationsModal.visible() && !joinDrawer.visible() && !disbandConfirmation.visible()) {
            closeMenu();
            return Interactable.Result.STOP;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && window.keyPressed(keyCode, modifiers, getContext()))
            return Interactable.Result.STOP;
        if (activeMenuMember != null) {
            Dropdown<OrganizationSavedData.Operation> menu = memberMenus.get(activeMenuMember);
            if (menu != null && menu.isOpen()) {
                if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                    window.focusManager().keyboardNavigation();
                    menu.onFocusMove(keyCode == GLFW.GLFW_KEY_UP
                            ? FocusManager.Direction.UP : FocusManager.Direction.DOWN);
                    return Interactable.Result.STOP;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                        || keyCode == GLFW.GLFW_KEY_SPACE) {
                    menu.activate();
                    return Interactable.Result.STOP;
                }
            }
        }
        if (keyCode == 256 && disbandConfirmation.visible()) {
            closeDisbandConfirmation();
            return Interactable.Result.STOP;
        }
        if (keyCode == 256 && applicationsModal.visible()) {
            closeApplications();
            return Interactable.Result.STOP;
        }
        return window.keyPressed(keyCode, modifiers, getContext())
                ? Interactable.Result.STOP : Interactable.Result.IGNORE;
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
