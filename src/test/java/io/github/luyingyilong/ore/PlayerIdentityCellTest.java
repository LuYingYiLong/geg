package io.github.luyingyilong.ore;

import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.ore.theme.OreTheme;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

public final class PlayerIdentityCellTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(String text) { return text.codePointCount(0, text.length()) * 6; }
            @Override public int width(FormattedText text) { return width(text.getString()); }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0};
                text.accept((index, style, point) -> { count[0]++; return true; });
                return count[0] * 6;
            }
        };
        PlayerIdentityCell<OrganizationSyncPacket.Member> cell = new PlayerIdentityCell<>(
                OrganizationSyncPacket.Member::id, OrganizationSyncPacket.Member::name);
        Control identity = cell.createControl();
        var alice = new OrganizationSyncPacket.Member(UUID.randomUUID(), "LongPlayerName橡木", Organization.Role.MEMBER);
        cell.bindControl(identity, alice);
        Avatar avatar = avatar(identity);
        Label name = name(identity);
        check(avatar.getPlayerId().equals(alice.id()) && name.getText().getString().equals(alice.name()), "Identity bound from member UUID/name");
        for (int width : new int[]{48, 80, 127, 128, 129, 180}) {
            int height = Math.max(23, cell.measureHeight(identity, font, alice, width));
            identity.layout(new Rect(0, 0, width, height), font);
            Rect face = avatar.getAbsoluteBounds(), text = name.getAbsoluteBounds();
            check(avatar.getSize().equals(new Size(16, 16)), "Compact square avatar");
            check(face.x() == 4 && face.right() + 4 == text.x() && text.right() == width - 4, "Avatar stays left of bounded name");
            int textHeight = name.measureHeight(font, text.width());
            int textCenter = 2 * (text.y() + (text.height() - textHeight) / 2) + textHeight;
            check(Math.abs(face.y() * 2 + face.height() - textCenter) <= 1, "Face and rendered text vertically centered");
            check(textHeight <= 20 && height >= textHeight + 4, "Two-line names determine row height");
        }
        var bob = new OrganizationSyncPacket.Member(UUID.randomUUID(), "Bob", Organization.Role.OFFICER);
        cell.bindControl(identity, bob);
        check(avatar.getPlayerId().equals(bob.id()) && avatar.getName().equals("Bob") && name.getText().getString().equals("Bob"), "Recycled cell changes both avatar and name");

        int[] actions = {0};
        Table<OrganizationSyncPacket.Member> table = new Table<OrganizationSyncPacket.Member>()
                .setRowKey(OrganizationSyncPacket.Member::id)
                .addColumn(new Table.Column<>("member", Component.literal("Member"), 48, 110, 2, width -> 20, cell))
                .addColumn(new Table.Column<>("role", Component.literal("Role"), 42, 64, 1, width -> 20,
                        new Table.TextCell<>(member -> Component.literal("Member"), OreTheme.TEXT_WHITE)))
                .addColumn(new Table.Column<>("action", Component.literal("Action"), 70, 82, 1, width -> 20, new Table.Cell<>() {
                    @Override public void draw(io.github.luyingyilong.ore.render.DrawContext context,
                                               OrganizationSyncPacket.Member row, Rect area) { }
                    @Override public List<Rect> focusTargets(OrganizationSyncPacket.Member row, Rect area) { return List.of(area); }
                    @Override public boolean click(OrganizationSyncPacket.Member row, Rect area, int x, int y) { actions[0]++; return true; }
                }));
        var members = IntStream.range(0, 300).mapToObj(i -> new OrganizationSyncPacket.Member(
                new UUID(0, i + 1), "Player" + i, Organization.Role.MEMBER)).toList();
        table.setRows(members);
        Window window = new Window(true).setContent(table);
        window.layoutViewport(300, 120, font);
        check(table.acceptsFocus(), "Decorative retained cells do not disable action-column keyboard focus");
        window.getFocusManager().requestFocus(table, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_RIGHT, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyDown(window,GLFW.GLFW_KEY_ENTER, 0, null);
        io.github.luyingyilong.ore.event.WindowInputs.keyUp(window,GLFW.GLFW_KEY_ENTER, 0, null);
        check(actions[0] == 1, "Existing action target remains keyboard-accessible");
        for (int width : new int[]{127, 128, 129, 300}) {
            window.layoutViewport(width, 120, font);
            table.setScroll(table.getContentHeight() / 2);
            check(table.getActiveViews() < 20, "Only buffered identities are retained");
            for (Control row : table.getChildren()) {
                Avatar face = avatar(row);
                int rowTop = row.getBounds().y() - table.getHeaderHeight() + table.getScroll();
                var item = table.getLayoutRows().stream().filter(entry -> entry.bounds().y() == rowTop).findFirst().orElseThrow().item();
                check(face.getPlayerId().equals(item.id()) && face.getName().equals(item.name()), "Virtual rows rebind the correct profile");
            }
        }
        applications(font);
    }

    private static void applications(Font font) {
        PlayerIdentityCell<OrganizationSyncPacket.Application> cell = new PlayerIdentityCell<>(
                OrganizationSyncPacket.Application::id, OrganizationSyncPacket.Application::name);
        var applicant = new OrganizationSyncPacket.Application(UUID.randomUUID(), "申请者LongName橡木", 123L);
        Control identity = cell.createControl();
        cell.bindControl(identity, applicant);
        Avatar avatar = avatar(identity);
        Label name = name(identity);
        check(avatar.getPlayerId().equals(applicant.id()) && name.getText().getString().equals(applicant.name()), "Application accessors bind UUID and name");
        for (int width : new int[]{48, 127, 128, 129, 240}) {
            int height = Math.max(23, cell.measureHeight(identity, font, applicant, width));
            identity.layout(new Rect(0, 0, width, height), font);
            Rect face = avatar.getAbsoluteBounds(), text = name.getAbsoluteBounds();
            int textHeight = name.measureHeight(font, text.width());
            int textCenter = 2 * (text.y() + (text.height() - textHeight) / 2) + textHeight;
            check(face.width() == 16 && face.height() == 16 && face.right() + 4 == text.x(), "Same avatar/name spacing for applications");
            check(Math.abs(face.y() * 2 + face.height() - textCenter) <= 1, "Application face/name vertical centers agree");
            check(text.right() == width - 4 && textHeight <= 20, "Application names stay inside the cell");
        }
        var replacement = new OrganizationSyncPacket.Application(UUID.randomUUID(), "NewApplicant", 456L);
        cell.bindControl(identity, replacement);
        check(avatar.getPlayerId().equals(replacement.id()) && name.getText().getString().equals(replacement.name()), "Recycled application updates both controls");
    }

    private static Avatar avatar(Control node) {
        if (node instanceof Avatar avatar) return avatar;
        for (Control child : node.getChildren()) { Avatar found = avatar(child); if (found != null) return found; }
        return null;
    }

    private static Label name(Control node) {
        if (node instanceof Avatar) return null;
        if (node instanceof Label label) return label;
        for (Control child : node.getChildren()) { Label found = name(child); if (found != null) return found; }
        return null;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
