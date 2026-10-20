package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.geg.client.theme.GegThemes;
import io.github.luyingyilong.ore.render.*;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.debug.DebugBridge;

import brachy.modularui.api.widget.Interactable;
import io.github.luyingyilong.ore.render.DrawContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.widget.Widget;
import io.github.luyingyilong.ore.*;
import io.github.luyingyilong.ore.Button;
import io.github.luyingyilong.ore.Dialog;
import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.Dropdown;
import io.github.luyingyilong.ore.Form;
import io.github.luyingyilong.ore.Navbar;
import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.Modal;
import io.github.luyingyilong.ore.PlayerIdentityCell;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.input.Input;
import io.github.luyingyilong.ore.layout.*;
import io.github.luyingyilong.ore.layout.Box;
import io.github.luyingyilong.ore.layout.Container;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Padding;
import io.github.luyingyilong.ore.layout.Panel;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Scroll;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.SizeFlags;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationNetwork;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

final class OrganizationCanvasWidget extends Widget<OrganizationCanvasWidget> implements Interactable {
    private final Window window = (Window) new Window(true).setDebugName("Organization Window");
    private final Overlay overlay = new Overlay();
    private final Box options = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(4);
    private final Box memberships = new Box().setGap(4);
    private final Box organizationList = new Box().setGap(0);
    private final Scroll membershipsScroll = new Scroll().setHorizontal(Scroll.BarMode.DISABLED);
    private final Label emptyText;
    private final Form createForm;
    private final Dialog emptyDialog;
    private final Drawer createDrawer;
    private final Table<OrganizationSyncPacket.Member> memberTable = createMemberTable();
    private final OrganizationDetailsContent organizationDetails = new OrganizationDetailsContent(memberTable);
    private final Table<OrganizationSyncPacket.Application> applicationTable = createApplicationTable();
    private final Table<OrganizationSyncPacket.Directory> joinTable = createJoinTable();
    private final Drawer detailDrawer;
    private final Modal applicationsModal;
    private final Drawer joinDrawer;
    private final Modal disbandConfirmation;
    private final OrganizationEditForm editor;
    private final Modal editModal;
    private boolean pendingEdit;
    private UUID selectedOrganizationId;
    private UUID pendingDisbandId;
    private boolean pendingCreate;

    OrganizationCanvasWidget(Form createForm, Input name, Input description, OrganizationEditForm editor) {
        this.createForm = createForm;
        this.editor = editor;

        memberships.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        membershipsScroll.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        initializeMemberships();
        window.setContent(memberships).setOverlay(overlay);

        options.addChild(option(TextureSource.themed(GegThemes.ORGANIZATION_CREATE), "organization.geg.create", this::showCreate));
        options.addChild(option(TextureSource.themed(GegThemes.ORGANIZATION_JOIN), "organization.geg.join", this::showJoin));
        emptyText = new Label(Component.translatable("organization.geg.none"))
                .setWrap(Label.Wrap.WORD).setOverflow(Label.Overflow.CLIP)
                .setAlignment(Label.Horizontal.LEFT, Label.Vertical.CENTER);
        emptyText.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box emptyBody = new Box().setGap(8);
        emptyBody.addChild(emptyText);
        emptyBody.addChild(options);
        emptyDialog = Signals.configure(new Dialog(270, 0, 0, Component.translatable("organization.geg.title"))
                .setContent(emptyBody), connected -> connected.closeRequested().connect(connected, this::closeEmptyDialog));

        createForm.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        createDrawer = Signals.configure(new Drawer(Component.translatable("organization.geg.create"))
                .setContent(createForm), connected -> connected.closed().connect(connected, this::onCreateClosed))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.back")).setThemeTypeVariation("SecondaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::closeCreate)))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.create_action")).setThemeTypeVariation("PrimaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::submitCreate)));
        detailDrawer = Signals.configure(new Drawer(Component.empty()).setContent(organizationDetails), connected -> connected.closed().connect(connected, this::onDetailsClosed));
        organizationDetails.editRequested().connect(window, this::showEdit);
        editModal = Signals.configure(new Modal(Component.translatable("organization.geg.edit_title"))
                .setSize(300, 0).centered().setContent(editor.content()), connected -> connected.closeRequested().connect(connected, this::closeEdit))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.back")).setThemeTypeVariation("SecondaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::closeEdit)))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.save")).setThemeTypeVariation("PrimaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::submitEdit)))
                .setDepth(400.0F);
        applicationTable.setHeaderVisible(false).setMinimum(0, 60)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        applicationsModal = Signals.configure(new Modal(Component.translatable("organization.geg.applications"))
                .setContent(applicationTable), connected -> connected.closeRequested().connect(connected, this::closeApplications)).setDepth(300.0F);
        joinTable.setHeaderVisible(false).setMinimum(0, 60)
                .setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        joinDrawer = Signals.configure(new Drawer(Component.translatable("organization.geg.join"))
                .setContent(joinTable), connected -> connected.closed().connect(connected, this::onJoinClosed));
        Label warning = new Label(Component.translatable("organization.geg.confirm_disband_body"))
                .setWrap(Label.Wrap.WORD).setThemeTypeVariation("LabelSecondary");
        warning.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        disbandConfirmation = Signals.configure(new Modal(Component.translatable("organization.geg.confirm_disband"))
                .setContent(warning), connected -> connected.closeRequested().connect(connected, this::closeDisbandConfirmation))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.back")).setThemeTypeVariation("SecondaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::closeDisbandConfirmation)))
                .addAction(Signals.configure(new Button(Component.translatable("organization.geg.disband")).setThemeTypeVariation("DestructiveButton"), actionButton -> actionButton.pressed().connect(actionButton, this::confirmDisband)))
                .setDepth(400.0F);
        overlay.addChild(emptyDialog);
        overlay.addChild(createDrawer);
        overlay.addChild(detailDrawer);
        overlay.addChild(joinDrawer);
        overlay.addChild(applicationsModal);
        overlay.addChild(disbandConfirmation);
        overlay.addChild(editModal);
        createDrawer.setVisible(false);
        detailDrawer.setVisible(false);
        joinDrawer.setVisible(false);
        applicationsModal.setVisible(false);
        disbandConfirmation.setVisible(false);
        editModal.setVisible(false);
        createForm.setVisible(false);
        name.setInputVisible(false);
        description.setInputVisible(false);
    }

    private static Panel option(TextureSource icon, String label, Runnable action) {
        io.github.luyingyilong.ore.TextureRect image=new io.github.luyingyilong.ore.TextureRect().setTexture(icon).setStretchMode(io.github.luyingyilong.ore.TextureRect.StretchMode.KEEP_CENTERED);
        image.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_EXPAND_FILL);
        Button button = Signals.configure(new Button(Component.translatable(label)), connected -> connected.pressed().connect(connected, action));
        button.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box column = new Box().setGap(6);
        column.addChild(image);
        column.addChild(button);
        Panel panel = new Panel(null).setChild(new Padding(Padding.Insets.all(4)).setChild(column));
        panel.setMinimum(106, 130).setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        return panel;
    }

    private Table<OrganizationSyncPacket.Member> createMemberTable() {
        return new Table<OrganizationSyncPacket.Member>()
                .setRowKey(OrganizationSyncPacket.Member::id)
                .setEmpty(Component.translatable("organization.geg.no_members"))
                .addColumn(new Table.Column<>("member", Component.translatable("organization.geg.member"),
                        48, 110, 2, width -> 20, new PlayerIdentityCell<>(
                                OrganizationSyncPacket.Member::id, OrganizationSyncPacket.Member::name)))
                .addColumn(new Table.Column<>("role", Component.translatable("organization.geg.role"),
                        42, 64, 1, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Member>(
                                member -> roleLabel(member.role()))
                                .setOverflow(Label.Overflow.ELLIPSIS)))
                .addColumn(new Table.Column<>("action", Component.translatable("organization.geg.actions"),
                        70, 82, 1, width -> 20, new Table.ControlCell<>(MemberActions::new, MemberActions::bind)));
    }

    private final class MemberActions extends Container {
        private UUID memberId;
        private final Dropdown<OrganizationSavedData.Operation> menu = Signals.configure(new Dropdown<OrganizationSavedData.Operation>(null, 6)
                .setTriggerLabel(Component.translatable("organization.geg.actions"))
                .setCheckable(false), connected -> connected.itemActivated().connect(connected, operation -> chooseOperation(operation, memberId)));
        private MemberActions() { this.addChild(menu); }
        private void bind(OrganizationSyncPacket.Member member) {
            if(!java.util.Objects.equals(memberId,member.id()))menu.close();
            memberId=member.id();
            var operations=actionsFor(member);
            menu.setOptions(operations.stream().map(operation -> new Dropdown.Option<>(operation,
                    Component.translatable("organization.geg.action."+operation.name().toLowerCase(java.util.Locale.ROOT)))).toList());
            menu.setVisible(!operations.isEmpty());
        }
        @Override public void layout(Rect bounds,Font font) {
            super.layout(bounds,font);fitChild(menu,new Rect(0,0,bounds.width(),bounds.height()),font);
        }
    }

    private Table<OrganizationSyncPacket.Application> createApplicationTable() {
        return new Table<OrganizationSyncPacket.Application>()
                .setHeaderVisible(false)
                .setRowKey(OrganizationSyncPacket.Application::id)
                .setEmpty(Component.translatable("organization.geg.no_applications"))
                .addColumn(new Table.Column<>("player",
                        Component.translatable("organization.geg.player"),
                        48, 120, 1, width -> 20, new PlayerIdentityCell<>(
                                OrganizationSyncPacket.Application::id, OrganizationSyncPacket.Application::name)))
                .addColumn(new Table.Column<>("action",
                        Component.translatable("organization.geg.actions"),
                        112, 128, 0, width -> 20, new Table.ControlCell<>(ApplicationActions::new, ApplicationActions::bind)));
    }

    private final class ApplicationActions extends Container {
        private OrganizationSyncPacket.Application application;
        private final Button reject = Signals.configure(new Button(Component.translatable("organization.geg.reject"))
                .setSize(Button.Size.SMALL).setThemeTypeVariation("DestructiveButton"), connected -> connected.pressed().connect(connected, () -> review(OrganizationSavedData.Operation.REJECT)));
        private final Button approve = Signals.configure(new Button(Component.translatable("organization.geg.approve"))
                .setSize(Button.Size.SMALL).setThemeTypeVariation("PrimaryButton"), connected -> connected.pressed().connect(connected, () -> review(OrganizationSavedData.Operation.APPROVE)));

        private ApplicationActions() { this.addChild(reject); this.addChild(approve); }
        private void bind(OrganizationSyncPacket.Application application) { this.application = application; }
        private void review(OrganizationSavedData.Operation operation) {
            if (selectedOrganizationId != null)
                OrganizationNetwork.operate(operation, selectedOrganizationId, application.id());
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            int width = Math.max(1, (bounds.width() - 6) / 2);
            int top = Math.max(0, (bounds.height() - Button.Size.SMALL.getHeight()) / 2);
            fitChild(reject, new Rect(2, top, width, Button.Size.SMALL.getHeight()), font);
            fitChild(approve, new Rect(width + 4, top, width, Button.Size.SMALL.getHeight()), font);
        }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            reject.setDisabled(selectedOrganizationId == null);
            approve.setDisabled(selectedOrganizationId == null);
        }
    }

    private Table<OrganizationSyncPacket.Directory> createJoinTable() {
        return new Table<OrganizationSyncPacket.Directory>()
                .setHeaderVisible(false)
                .setRowKey(OrganizationSyncPacket.Directory::id)
                .setEmpty(Component.translatable("organization.geg.no_available_organizations"))
                .addColumn(new Table.Column<>("organization",
                        Component.translatable("organization.geg.name"),
                        50, 120, 1, width -> 20,
                        new Table.TextCell<OrganizationSyncPacket.Directory>(
                                entry -> Component.literal(entry.name()))
                                .setOverflow(Label.Overflow.ELLIPSIS)))
                .addColumn(new Table.Column<>("action",
                        Component.translatable("organization.geg.actions"),
                        72, 92, 0, width -> 20, new Table.ControlCell<>(JoinAction::new, JoinAction::bind)));
    }

    private final class JoinAction extends Container {
        private OrganizationSyncPacket.Directory entry;
        private final Button button = Signals.configure(new Button(), connected -> connected.pressed().connect(connected, () -> {
            if (joinDrawer.isOpen() && Minecraft.getInstance().player != null)
                OrganizationNetwork.operate(entry.pending()
                                ? OrganizationSavedData.Operation.CANCEL_APPLICATION
                                : OrganizationSavedData.Operation.APPLY,
                        entry.id(), Minecraft.getInstance().player.getUUID());
        }));

        private JoinAction() { this.addChild(button); }
        private void bind(OrganizationSyncPacket.Directory entry) {
            this.entry = entry;
            update(false);
        }

        private void update(boolean hovered) {
            button.setLabel(Component.translatable(entry.pending()
                            ? hovered ? "organization.geg.cancel_application" : "organization.geg.pending_application"
                            : entry.joinPolicy() == Organization.JoinPolicy.APPROVAL
                            ? "organization.geg.apply" : "organization.geg.join"))
                    .setThemeTypeVariation(entry.pending() && hovered ? "DestructiveButton" : "SecondaryButton");
        }

        @Override public void layout(Rect bounds, Font font) {
            super.layout(bounds, font);
            fitChild(button, actionBounds(new Rect(0, 0, bounds.width(), bounds.height())), font);
        }

        @Override protected void render(DrawContext context, int screenX, int screenY) {
            update(button.getScreenBounds(screenX, screenY).contains(context.getAbsMouseX(), context.getAbsMouseY()));
            button.setDisabled(!joinDrawer.isOpen() || Minecraft.getInstance().player == null);
        }
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



    private void chooseOperation(OrganizationSavedData.Operation operation, UUID targetId) {
        closeMenu();
        if (!detailDrawer.isOpen() || selectedOrganizationId == null) return;
        if (operation == OrganizationSavedData.Operation.DISBAND) {
            pendingDisbandId = selectedOrganizationId;
            disbandConfirmation.setVisible(true);
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
        disbandConfirmation.setVisible(false);
        pendingDisbandId = null;
    }

    private void closeMenu() { window.getContext().getPopups().cancel(); }

    private void showDetails(UUID organizationId) {
        OrganizationSyncPacket.Summary summary = ClientOrganizationState.organizations().stream()
                .filter(entry -> entry.id().equals(organizationId)).findFirst().orElse(null);
        if (summary == null) return;
        // Finish a previous close callback before assigning the newly selected organization.
        detailDrawer.open();
        selectedOrganizationId = organizationId;
        emptyDialog.setVisible(false);
        createDrawer.setVisible(false);
        joinDrawer.close();
        memberTable.scrollToTop();
        detailDrawer.getBodyScroll().scrollToTop();
        refreshDetails(summary);
        requestLayout();
    }

    private void refreshDetails(OrganizationSyncPacket.Summary summary) {
        closeMenu();
        detailDrawer.setTitle(Component.literal(summary.name()));
        organizationDetails.update(summary);
        memberTable.setRows(summary.members());
        detailDrawer.clearActions();
        Organization.Role ownRole = ownRole(summary);
        boolean canEdit = ownRole == Organization.Role.LEADER || ownRole == Organization.Role.OFFICER;
        organizationDetails.editable(canEdit);
        if (!canEdit) closeEdit();
        if (ownRole == Organization.Role.LEADER || ownRole == Organization.Role.OFFICER) {
            detailDrawer.addAction(Signals.configure(new Button(Component.translatable("organization.geg.applications")).setThemeTypeVariation("SecondaryButton"), actionButton -> actionButton.pressed().connect(actionButton, this::showApplications)));
            detailDrawer.addAction(Signals.configure(new Button(Component.translatable("mail.geg.announce")).setThemeTypeVariation("SecondaryButton"), actionButton -> actionButton.pressed().connect(actionButton, () -> io.github.luyingyilong.geg.mail.client.ClientMailScreen.open(summary.id()))));
            applicationTable.setRows(summary.applications());
        } else {
            applicationsModal.setVisible(false);
        }
        requestLayout();
    }

    private Organization.Role ownRole(OrganizationSyncPacket.Summary summary) {
        if (Minecraft.getInstance().player == null) return null;
        UUID self = Minecraft.getInstance().player.getUUID();
        return summary.members().stream().filter(member -> member.id().equals(self))
                .map(OrganizationSyncPacket.Member::role).findFirst().orElse(null);
    }

    private void showEdit() {
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (!detailDrawer.isOpen() || summary == null || pendingEdit) return;
        Organization.Role role = ownRole(summary);
        if (role != Organization.Role.LEADER && role != Organization.Role.OFFICER) return;
        closeMenu();
        applicationsModal.setVisible(false);
        editor.show(summary);
        editModal.setVisible(true);
        requestLayout();
    }

    private void closeEdit() {
        editModal.setVisible(false);
        editor.hide();
        requestLayout();
    }

    private void submitEdit() {
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (!editModal.getVisible() || pendingEdit || summary == null || !editor.validate()) return;
        pendingEdit = true;
        OrganizationNetwork.update(summary.id(), editor.description(), editor.policy());
    }

    private void showApplications() {
        if (!detailDrawer.isOpen()) return;
        OrganizationSyncPacket.Summary summary = selectedSummary();
        if (summary == null) return;
        Organization.Role ownRole = ownRole(summary);
        if (ownRole != Organization.Role.LEADER && ownRole != Organization.Role.OFFICER) return;
        closeMenu();
        applicationTable.setRows(summary.applications());
        applicationTable.scrollToTop();
        applicationsModal.setVisible(true);
        requestLayout();
    }

    private void closeApplications() {
        applicationsModal.setVisible(false);
        requestLayout();
    }

    private void showJoin() {
        closeEdit();
        joinDrawer.open();
        emptyDialog.setVisible(false);
        createDrawer.setVisible(false);
        detailDrawer.setVisible(false);
        applicationsModal.setVisible(false);
        closeDisbandConfirmation();
        closeMenu();
        joinTable.setRows(ClientOrganizationState.directory());
        joinTable.scrollToTop();
        joinDrawer.getBodyScroll().scrollToTop();
        requestLayout();
    }

    private void onJoinClosed() {
        if (createDrawer.getVisible() || detailDrawer.getVisible() || applicationsModal.getVisible()
                || disbandConfirmation.getVisible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    private void closeDetails() {
        closeEdit();
        applicationsModal.setVisible(false);
        closeDisbandConfirmation();
        closeMenu();
        detailDrawer.close();
        requestLayout();
    }

    private void onDetailsClosed() {
        closeEdit();
        selectedOrganizationId = null;
        closeMenu();
        if (createDrawer.getVisible() || joinDrawer.getVisible() || applicationsModal.getVisible()
                || disbandConfirmation.getVisible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    void showInitialState() {
        emptyDialog.setVisible(false);
        createDrawer.setVisible(false);
        createForm.setVisible(false);
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (!ClientOrganizationState.hasOrganizations()) showEmpty();
        else showMemberships();
    }

    void acceptSnapshot(String resultKey) {
        if (pendingEdit && !resultKey.isEmpty()) {
            pendingEdit = false;
            if ("organization.geg.updated".equals(resultKey)) closeEdit();
        }
        if (!resultKey.isEmpty()) pendingCreate = false;
        if (joinDrawer.isOpen()) {
            joinTable.setRows(ClientOrganizationState.directory());
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
                summary.name().equalsIgnoreCase(createForm.getValue(OrganizationFields.NAME).strip()))) {
            showMemberships();
        } else if (resultKey.isEmpty() && !createDrawer.getVisible()) {
            if (ClientOrganizationState.hasOrganizations()) showMemberships();
            else showEmpty();
        }
    }

    private void showEmpty() {
        closeEdit();
        createDrawer.setVisible(false);
        detailDrawer.setVisible(false);
        joinDrawer.setVisible(false);
        applicationsModal.setVisible(false);
        if (!ClientOrganizationState.loaded()) {
            showLoading();
            return;
        }
        rebuildMemberships();
        emptyText.setText(Component.translatable("organization.geg.none"));
        options.setVisible(true);
        emptyDialog.setVisible(!ClientOrganizationState.hasOrganizations());
        requestLayout();
    }

    private void closeEmptyDialog() {
        emptyDialog.setVisible(false);
        rebuildMemberships();
        requestLayout();
    }

    private void showLoading() {
        emptyText.setText(Component.translatable("organization.geg.loading"));
        options.setVisible(false);
        emptyDialog.setVisible(true);
        requestLayout();
    }

    private void showCreate() {
        closeEdit();
        createDrawer.open();
        emptyDialog.setVisible(false);
        detailDrawer.setVisible(false);
        joinDrawer.setVisible(false);
        applicationsModal.setVisible(false);
        createForm.setVisible(true);
        createDrawer.getBodyScroll().scrollToTop();
        requestLayout();
    }

    private void closeCreate() {
        createDrawer.close();
        requestLayout();
    }

    private void onCreateClosed() {
        // Keep the form visible throughout the exit animation, then restore the current base view.
        createForm.setVisible(false);
        if (detailDrawer.getVisible() || joinDrawer.getVisible() || applicationsModal.getVisible()
                || disbandConfirmation.getVisible()) return;
        if (!ClientOrganizationState.loaded()) showLoading();
        else if (ClientOrganizationState.hasOrganizations()) showMemberships();
        else showEmpty();
    }

    private void submitCreate() {
        if (!createDrawer.isOpen() || pendingCreate) return;
        var result = createForm.validate();
        if (!result.getValid()) return;
        var values = result.getValues();
        pendingCreate = true;
        OrganizationNetwork.create(values.getValue(OrganizationFields.NAME).strip(),
                values.getValue(OrganizationFields.DESCRIPTION).strip(), values.getValue(OrganizationFields.POLICY));
    }

    private void showMemberships() {
        closeEdit();
        rebuildMemberships();
        createDrawer.setVisible(false);
        detailDrawer.setVisible(false);
        joinDrawer.setVisible(false);
        applicationsModal.setVisible(false);
        emptyDialog.setVisible(false);
        requestLayout();
    }

    private void initializeMemberships() {
        Navbar navbar = new Navbar(Component.translatable("organization.geg.your_organizations"))
                ;
        memberships.addChild(navbar);
        Button createButton = Signals.configure(new Button(Component.translatable("organization.geg.create"))
                .setThemeTypeVariation("PrimaryButton"), connected -> connected.pressed().connect(connected, this::showCreate));
        createButton.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Button joinButton = Signals.configure(new Button(Component.translatable("organization.geg.join")), connected -> connected.pressed().connect(connected, this::showJoin));
        joinButton.setSizeFlagsHorizontal(SizeFlags.SIZE_EXPAND_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Box actions = new Box().setOrientation(Box.Orientation.HORIZONTAL).setGap(2);
        actions.addChild(createButton);
        actions.addChild(joinButton);
        actions.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        Padding createInset = new Padding(new Padding.Insets(0, 4, 0, 4));
        createInset.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
        createInset.setChild(actions);
        memberships.addChild(createInset);
        organizationList.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_FILL);
        membershipsScroll.setContent(organizationList);
        memberships.addChild(membershipsScroll);
    }

    private void rebuildMemberships() {
        // The shell and Scroll survive snapshots and Drawer close callbacks. Only rows are replaced.
        for (Control child : organizationList.getChildren()) { if (child.getParent() != null) child.getParent().removeChild(child); child.queueFree(); }
        for (OrganizationSyncPacket.Summary summary : ClientOrganizationState.organizations()) {
            OrganizationMembershipRow row = new OrganizationMembershipRow(summary);
            row.detailsRequested().connect(row, () -> showDetails(summary.id()));
            Panel panel = new Panel(OreTheme.BAR).setChild(new Padding(Padding.Insets.all(4)).setChild(row));
            panel.setSizeFlagsHorizontal(SizeFlags.SIZE_FILL).setSizeFlagsVertical(SizeFlags.SIZE_SHRINK_CENTER);
            organizationList.addChild(panel);
        }
    }

    private void requestLayout() {
        window.requestLayout();
    }

    @Override
    public void draw(brachy.modularui.screen.viewport.ModularGuiContext mui, WidgetThemeEntry<?> theme) {
        try (DrawContext frame = io.github.luyingyilong.ore.window.MuiDrawing.begin(mui, window.getContext())) {
            drawFrame(frame);
        }
    }

    private void drawFrame(DrawContext context) {
        if (!window.isAlive()) return;
        Rect area = new Rect(getArea().x, getArea().y, getArea().width, getArea().height);
        if (area.width() == 0 || area.height() == 0) return;
        applicationsModal.setSize(Math.min(320, area.width()), Math.min(220, area.height())).centered();
        disbandConfirmation.setSize(Math.min(240, area.width()), Math.min(120, area.height())).centered();
        options.setOrientation(area.width() < 252 ? Box.Orientation.VERTICAL : Box.Orientation.HORIZONTAL);
        window.prepareTree();
        window.layoutViewport(area.width(), area.height(), context.getFont());
        window.draw(context, area.x(), area.y());
    }



    @Override public @NotNull Interactable.Result onMousePressed(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.press(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,getContext(),button)
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }

    @Override public @NotNull Interactable.Result onCharTyped(char character,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.text(window,String.valueOf(character),modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }
    @Override public boolean onMouseScrolled(double delta) {
        return io.github.luyingyilong.ore.event.WindowInputs.scroll(window,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,delta,net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }



    @Override public void onMouseDrag(int button,double x,double y) {
        io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.MOVE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }

    @Override public boolean onMouseReleased(int button) {
        return io.github.luyingyilong.ore.event.WindowInputs.pointer(window,io.github.luyingyilong.ore.event.PointerEvent.Action.RELEASE,getContext().getAbsMouseX() - getArea().x,getContext().getAbsMouseY() - getArea().y,button);
    }

    @Override public boolean onKeyReleased(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.RELEASE,keyCode,scanCode,modifiers,getContext());
    }

    @Override public void dispose() {
        window.hostDetached();
        closeMenu();
        window.cancelInteractions();
        super.dispose();
    }

    @Override public @NotNull Interactable.Result onKeyPressed(int keyCode,int scanCode,int modifiers) {
        return io.github.luyingyilong.ore.event.WindowInputs.key(window,io.github.luyingyilong.ore.event.KeyEvent.Action.PRESS,keyCode,scanCode,modifiers,getContext())
                ?Interactable.Result.SUCCESS:Interactable.Result.IGNORE;
    }

}
