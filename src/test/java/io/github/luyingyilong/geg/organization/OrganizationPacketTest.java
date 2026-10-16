package io.github.luyingyilong.geg.organization;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Verifies that role and action packets survive their network encoding. */
public final class OrganizationPacketTest {
    private OrganizationPacketTest() {
    }

    public static void main(String[] args) {
        UUID organizationId = UUID.randomUUID();
        UUID leader = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        OrganizationSyncPacket.Summary summary = new OrganizationSyncPacket.Summary(
                organizationId, "Builders", "Build together", Organization.JoinPolicy.APPROVAL,
                leader, List.of(new OrganizationSyncPacket.Member(leader, "Alice", Organization.Role.LEADER),
                        new OrganizationSyncPacket.Member(officer, "Bob", Organization.Role.OFFICER)),
                List.of(new OrganizationSyncPacket.Application(UUID.randomUUID(), "Cara", 123L)));
        OrganizationSyncPacket snapshot = new OrganizationSyncPacket(List.of(summary),
                List.of(new OrganizationSyncPacket.Directory(UUID.randomUUID(), "Other",
                        Organization.JoinPolicy.APPROVAL, true)), "");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        OrganizationSyncPacket.encode(snapshot, buffer);
        check(OrganizationSyncPacket.decode(buffer).equals(snapshot));

        OrganizationActionPacket action = OrganizationActionPacket.operate(
                OrganizationSavedData.Operation.DEMOTE, organizationId, officer);
        FriendlyByteBuf actionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        OrganizationActionPacket.encode(action, actionBuffer);
        check(OrganizationActionPacket.decode(actionBuffer).equals(action));
        OrganizationActionPacket approval = OrganizationActionPacket.operate(
                OrganizationSavedData.Operation.APPROVE, organizationId, officer);
        FriendlyByteBuf approvalBuffer = new FriendlyByteBuf(Unpooled.buffer());
        OrganizationActionPacket.encode(approval, approvalBuffer);
        check(OrganizationActionPacket.decode(approvalBuffer).equals(approval));
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Organization packet round trip failed");
    }
}
