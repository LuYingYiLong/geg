package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.Button;

import io.github.luyingyilong.ore.signal.Signals;

import io.github.luyingyilong.ore.Drawer;
import io.github.luyingyilong.ore.PlayerIdentityCell;
import io.github.luyingyilong.ore.Table;
import io.github.luyingyilong.ore.layout.Control;
import io.github.luyingyilong.ore.layout.Overlay;
import io.github.luyingyilong.ore.layout.Window;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** Real drawer composition: facts precede the retained member table and remain reachable on narrow screens. */
public final class OrganizationDetailsLayoutTest {
    public static void main(String[] args) {
        Font font = font();
        List<OrganizationSyncPacket.Member> members = IntStream.range(0, 100).mapToObj(index ->
                new OrganizationSyncPacket.Member(UUID.randomUUID(), "Member" + index, Organization.Role.MEMBER)).toList();
        Table<OrganizationSyncPacket.Member> table = new Table<OrganizationSyncPacket.Member>()
                .setRowKey(OrganizationSyncPacket.Member::id)
                .column(new Table.Column<>("member", Component.literal("成员"), 60, 60, 1, width -> 20,
                        new PlayerIdentityCell<>(OrganizationSyncPacket.Member::id, OrganizationSyncPacket.Member::name)));
        table.setRows(members);
        OrganizationDetailsContent content = new OrganizationDetailsContent(table);
        OrganizationSyncPacket.Summary summary = new OrganizationSyncPacket.Summary(UUID.randomUUID(), "公社", "共同建设",
                Organization.JoinPolicy.APPROVAL, members.get(0).id(), members, List.of());
        content.update(summary);
        check(!content.editButton().getVisible(), "Read-only viewers do not receive an edit action");
        content.editable(true);
        check(content.descriptions().getItem("name").getContent().getString().equals("公社"), "Bind the organization name");
        check(content.descriptions().getItem("description").getContent().getString().equals("共同建设"), "Bind the original description");
        check(((TranslatableContents) content.descriptions().getItem("joinPolicy").getContent().getContents()).getKey()
                .equals("organization.geg.join_approval"), "Joining policy remains localized");

        Drawer drawer = new Drawer(Component.literal("公社")).setAnimationDuration(0).setContent(content)
                .addAction(Signals.configure(new Button(Component.literal("加入申请")).setType(Button.Type.SECONDARY), actionButton -> actionButton.pressed().connect(actionButton, () -> { })));
        Window window = new Window(true).setContent(new Control()).setOverlay(new Overlay().add(drawer));
        drawer.open();
        for (int width : new int[]{127, 128, 129, 427, 640}) {
            drawer.setWidth(Math.max(1, width / 2));
            window.layoutViewport(width, 240, font);
            check(content.descriptions().getAbsoluteBounds().bottom() + 8 <= table.getAbsoluteBounds().y(),
                    "Facts are above the member table at width " + width);
            check(table.getSize().height() >= 60, "Keep the member table's minimum height");
            var edit = content.editButton().getAbsoluteBounds();
            var facts = content.descriptions().getAbsoluteBounds();
            check(edit.x() >= facts.right() + 8, "Edit button remains to the right of descriptions");
            check(Math.abs(edit.y() * 2 + edit.height() - facts.y() * 2 - facts.height()) <= 1,
                    "Edit button shrinks and centers vertically beside the facts");
            int top = content.descriptions().getAbsoluteBounds().y();
            table.scrollBy(46);
            check(table.getScroll() > 0 && content.descriptions().getAbsoluteBounds().y() == top,
                    "Member scrolling does not move the description fields");
            table.scrollToTop();
        }
        content.update(new OrganizationSyncPacket.Summary(summary.id(), "新名称", "详细介绍".repeat(150),
                Organization.JoinPolicy.UNRESTRICTED, summary.founder(), members, List.of()));
        window.layoutViewport(320, 200, font);
        check(drawer.getBodyScroll().getMaxScrollY() > 0, "A long introduction uses the existing drawer body scroll");
        check(table.getAbsoluteBounds().y() >= content.descriptions().getAbsoluteBounds().bottom(), "Long text does not overlap members");
        var fullDescription = content.descriptions().getItem("description").getContentNode();
        var descriptionArea = fullDescription.getAbsoluteBounds();
        check(fullDescription.displayedLines(font, descriptionArea.width(), descriptionArea.height()).size()
                        == fullDescription.lines(font, descriptionArea.width()).size(),
                "Descriptions keeps every wrapped line, without the list preview's two-line limit");
        check(descriptionArea.bottom() <= content.descriptions().getItem("joinPolicy").getAbsoluteBounds().y(),
                "The full introduction leaves enough space before the next fact");
        check(content.descriptions().getItem("joinPolicy").getAbsoluteBounds().bottom() <= table.getAbsoluteBounds().y(),
                "The edit action's reserved width is included when measuring the full profile");
        content.update(new OrganizationSyncPacket.Summary(summary.id(), "新名称", " ", Organization.JoinPolicy.UNRESTRICTED,
                summary.founder(), members, List.of()));
        check(((TranslatableContents) content.descriptions().getItem("description").getContent().getContents()).getKey()
                .equals("organization.geg.no_description"), "Blank descriptions have a localized fallback");
    }

    private static Font font() {
        return new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().codePointCount(0, text.getString().length()) * 6; }
            @Override public int width(FormattedCharSequence text) {
                int[] count = {0}; text.accept((index, style, codepoint) -> { count[0]++; return true; }); return count[0] * 6;
            }
        };
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
