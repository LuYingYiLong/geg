package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.organization.*;
import io.github.luyingyilong.geg.organization.Organization;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;

import java.util.*;

public final class MailPolicyTest {
    public static void main(String[] args) {
        UUID leader = UUID.randomUUID(), officer = UUID.randomUUID(), member = UUID.randomUUID(), outsider = UUID.randomUUID();
        Organization org = new Organization(UUID.randomUUID(), "Builders", "", leader, Organization.JoinPolicy.APPROVAL, 1,
                Map.of(leader, Organization.Role.LEADER, officer, Organization.Role.OFFICER, member, Organization.Role.MEMBER));
        Map<UUID, String> known = new HashMap<>(Map.of(leader, "Leader", officer, "Officer", member, "Member"));
        MailActionPacket orgMail = request(MailActionPacket.Scope.ORGANIZATION, true, org.id(), "", 0);
        for (UUID actor : List.of(leader, officer)) check(resolve(actor, false, org, known, orgMail).players().size() == 3);
        rejected("forbidden", () -> resolve(member, false, org, known, orgMail));
        rejected("forbidden", () -> resolve(outsider, true, org, known, orgMail)); // OP cannot forge membership identity.
        rejected("forbidden", () -> resolve(leader, false, org, known, request(MailActionPacket.Scope.ORGANIZATION, true, org.id(), "", 1)));
        rejected("forbidden", () -> resolve(leader, false, org, known, request(MailActionPacket.Scope.ALL, true, org.id(), "", 0)));
        rejected("cooldown", () -> MailPolicy.resolve(leader, false, List.of(org), known, orgMail, 10000, 9999));
        MailActionPacket everyone = request(MailActionPacket.Scope.ALL, false, MailActionPacket.EMPTY, "", Long.MAX_VALUE);
        rejected("forbidden", () -> resolve(leader, false, org, known, everyone));
        var snapshot = resolve(leader, true, org, known, everyone).players();
        known.put(outsider, "Late");
        check(snapshot.size() == 3 && resolve(leader, true, org, known, everyone).players().size() == 4);
        check(resolve(leader, true, org, known, request(MailActionPacket.Scope.PLAYER, false, MailActionPacket.EMPTY, "mEmBeR", 50)).players().equals(List.of(member)));
        check(resolve(leader, true, org, known, request(MailActionPacket.Scope.PLAYER, false, MailActionPacket.EMPTY, member.toString(), 50)).players().equals(List.of(member)));
        rejected("invalid_target", () -> resolve(leader, true, org, Map.of(), request(MailActionPacket.Scope.PLAYER, false, MailActionPacket.EMPTY, member.toString(), 50)));
        rejected("invalid_coin", () -> resolve(leader, true, org, known, request(MailActionPacket.Scope.ALL, false, MailActionPacket.EMPTY, "", -1)));
        rejected("forbidden", () -> MailPolicy.resolve(leader, false, List.of(), known, orgMail, 100000, 0));
        Organization demoted = org.withRole(officer, Organization.Role.MEMBER);
        rejected("forbidden", () -> resolve(officer, false, demoted, known, orgMail));
        Organization larger = org.withMember(outsider);
        check(!resolve(leader, false, org, known, orgMail).players().equals(resolve(leader, false, larger, known, orgMail).players()));
        check(MailPolicy.validText("😀".repeat(64), 64, false));
        check(!MailPolicy.validText("😀".repeat(65), 64, false));
        check(MailPolicy.validText("你好\nSecond line", 2048, true));
        check(!MailPolicy.validText("a\nb", 64, false) && !MailPolicy.validText("a\0b", 2048, true));
        organizationOutbox();
    }
    private static void organizationOutbox() {
        UUID leader = UUID.randomUUID(), applicant = UUID.randomUUID();
        OrganizationSavedData data = new OrganizationSavedData();
        data.create(leader, "Notices", "", Organization.JoinPolicy.APPROVAL);
        UUID org = data.memberships(leader).get(0).id();
        data.operate(applicant, org, applicant, OrganizationSavedData.Operation.APPLY);
        check(data.mailEvents().isEmpty());
        data.operate(leader, org, applicant, OrganizationSavedData.Operation.APPROVE);
        data.operate(leader, org, applicant, OrganizationSavedData.Operation.PROMOTE);
        data.operate(leader, org, applicant, OrganizationSavedData.Operation.DEMOTE);
        data.operate(leader, org, applicant, OrganizationSavedData.Operation.REMOVE);
        data.operate(leader, org, applicant, OrganizationSavedData.Operation.REMOVE);
        check(data.mailEvents().size() == 4);
        var restored = OrganizationSavedData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        check(restored.mailEvents().equals(data.mailEvents()));
        check(restored.mailEvents().stream().allMatch(event -> event.recipient().equals(applicant)));
        check(restored.mailEvents().stream().map(OrganizationSavedData.MailEvent::id).distinct().count() == 4);
    }
    private static MailPolicy.Targets resolve(UUID actor, boolean admin, Organization org, Map<UUID, String> known, MailActionPacket p) {
        return MailPolicy.resolve(actor, admin, List.of(org), known, p, 100000, 0);
    }
    private static MailActionPacket request(MailActionPacket.Scope scope, boolean org, UUID id, String target, long coin) {
        return new MailActionPacket(MailActionPacket.Action.PREVIEW, UUID.randomUUID(), MailState.Filter.ALL, 0, scope, org, id, target, "Title", "Body", coin);
    }
    private static void rejected(String key, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + key); }
        catch (MailPolicy.Rejected rejection) { check(rejection.getMessage().equals("mail.geg." + key)); }
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Mail authorization/outbox regression"); }
}
