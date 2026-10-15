package io.github.luyingyilong.geg.organization;

import io.github.luyingyilong.geg.organization.client.ClientOrganizationScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record OrganizationSyncPacket(List<Summary> organizations, String resultKey) {
    public record Member(UUID id, String name, Organization.Role role) { }

    public record Summary(UUID id, String name, String description,
                          Organization.JoinPolicy joinPolicy, UUID founder, List<Member> members) {
        public static Summary from(Organization organization, MinecraftServer server) {
            List<Member> members = organization.members().stream().map(id -> {
                var player = server.getPlayerList().getPlayer(id);
                String name = player != null ? player.getGameProfile().getName()
                        : server.getProfileCache().get(id).map(profile -> profile.getName())
                        .orElse(id.toString());
                return new Member(id, name, organization.roleOf(id));
            }).sorted(java.util.Comparator.comparing(Member::role)
                    .thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER)).toList();
            return new Summary(organization.id(), organization.name(), organization.description(),
                    organization.joinPolicy(), organization.founder(), members);
        }
    }

    public static void encode(OrganizationSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.organizations.size());
        for (Summary summary : packet.organizations) {
            buffer.writeUUID(summary.id);
            buffer.writeUtf(summary.name, 48);
            buffer.writeUtf(summary.description, 120);
            buffer.writeEnum(summary.joinPolicy);
            buffer.writeUUID(summary.founder);
            buffer.writeVarInt(summary.members.size());
            for (Member member : summary.members) {
                buffer.writeUUID(member.id);
                buffer.writeUtf(member.name, 40);
                buffer.writeEnum(member.role);
            }
        }
        buffer.writeUtf(packet.resultKey, 80);
    }

    public static OrganizationSyncPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 128) throw new IllegalArgumentException("Invalid organization count");
        List<Summary> organizations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(48);
            String description = buffer.readUtf(120);
            Organization.JoinPolicy policy = buffer.readEnum(Organization.JoinPolicy.class);
            UUID founder = buffer.readUUID();
            int memberCount = buffer.readVarInt();
            if (memberCount < 0 || memberCount > 1024)
                throw new IllegalArgumentException("Invalid organization member count");
            List<Member> members = new ArrayList<>(memberCount);
            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++)
                members.add(new Member(buffer.readUUID(), buffer.readUtf(40),
                        buffer.readEnum(Organization.Role.class)));
            organizations.add(new Summary(id, name, description, policy, founder, List.copyOf(members)));
        }
        return new OrganizationSyncPacket(List.copyOf(organizations), buffer.readUtf(80));
    }

    public static void handle(OrganizationSyncPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientOrganizationScreen.acceptSnapshot(packet)));
        context.setPacketHandled(true);
    }
}
