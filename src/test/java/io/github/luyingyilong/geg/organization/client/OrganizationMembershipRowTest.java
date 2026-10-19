package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.Label;
import io.github.luyingyilong.ore.layout.Rect;
import io.github.luyingyilong.ore.layout.Size;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSyncPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.UUID;

/** Measure the real list row at its final text width, with the details action still beside it. */
public final class OrganizationMembershipRowTest {
    public static void main(String[] args) {
        Font font = new Font(id -> null, false) {
            @Override public int width(FormattedText text) { return textWidth(text.getString()); }
            @Override public int width(FormattedCharSequence text) { return textWidth(asString(text)); }
        };
        for (String text : new String[]{"共同建设美好家园".repeat(15), "第一行\n第二行\n第三行", "简短介绍", ""}) {
            UUID leader = UUID.randomUUID();
            var summary = new OrganizationSyncPacket.Summary(UUID.randomUUID(), "公社", text,
                    Organization.JoinPolicy.UNRESTRICTED, leader, List.of(), List.of());
            OrganizationMembershipRow row = new OrganizationMembershipRow(summary);
            Label introduction = row.descriptionNode();
            for (int width : new int[]{127, 128, 129, 200, 640}) {
                Size measured = row.measureSize(font, new Size(width, 1000));
                row.layout(new Rect(0, 0, width, measured.height()), font);
                if (text.isBlank()) {
                    check(!introduction.getVisible(), "Empty descriptions do not occupy a line");
                    continue;
                }
                Rect area = introduction.getAbsoluteBounds();
                var displayed = introduction.displayedLines(font, area.width(), area.height());
                int fullLines = introduction.lines(font, area.width()).size();
                check(displayed.size() == Math.min(2, fullLines), "The row reserves enough space for up to two lines");
                check(area.bottom() <= measured.height(), "Wrapped text cannot overlap the next list row");
                check(area.height() <= introduction.lineHeight(font) * 2, "List introductions never exceed two lines");
                if (fullLines > 2) check(asString(displayed.get(1)).endsWith("…"), "Omitted lines add a trailing ellipsis");
                check(introduction.getText().getString().equals(text), "The preview never changes the full description");
            }
        }
    }

    private static int textWidth(String value) {
        return value.equals("organization.geg.details") ? 36 : value.codePointCount(0, value.length()) * 6;
    }

    private static String asString(FormattedCharSequence text) {
        StringBuilder value = new StringBuilder();
        text.accept((index, style, codepoint) -> { value.appendCodePoint(codepoint); return true; });
        return value.toString();
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
