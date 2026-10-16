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

public record OrganizationSyncPacket(List<Summary> organizations, List<Directory> directory,
                                     String resultKey) {
    public record Member(UUID id, String name, Organization.Role role) { }
    public record Application(UUID id, String name, long appliedAt) { }
    public record Directory(UUID id, String name, Organization.JoinPolicy joinPolicy,
                            boolean pending) { }

    public record Summary(UUID id, String name, String description,
                          Organization.JoinPolicy joinPolicy, UUID founder, List<Member> members,
                          List<Application> applications) {
        public static Summary from(Organization organization, MinecraftServer server,
                                   OrganizationSavedData data, UUID viewer) {
            List<Member> members = organization.members().stream().map(id -> {
                return new Member(id, playerName(server, id), organization.roleOf(id));
            }).sorted(java.util.Comparator.comparing(Member::role)
                    .thenComparing(Member::name, String.CASE_INSENSITIVE_ORDER)).toList();
            List<Application> applications = organization.roleOf(viewer) == Organization.Role.LEADER
                    || organization.roleOf(viewer) == Organization.Role.OFFICER
                    ? data.applications(organization.id()).stream()
                    .map(entry -> new Application(entry.applicant(),
                            playerName(server, entry.applicant()), entry.appliedAt())).toList()
                    : List.of();
            return new Summary(organization.id(), organization.name(), organization.description(),
                    organization.joinPolicy(), organization.founder(), members, applications);
        }
    }

    private static String playerName(MinecraftServer server, UUID id) {
        var player = server.getPlayerList().getPlayer(id);
        return player != null ? player.getGameProfile().getName()
                : server.getProfileCache().get(id).map(profile -> profile.getName())
                .orElse(id.toString());
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
            buffer.writeVarInt(summary.applications.size());
            for (Application application : summary.applications) {
                buffer.writeUUID(application.id);
                buffer.writeUtf(application.name, 40);
                buffer.writeLong(application.appliedAt);
            }
        }
        buffer.writeVarInt(packet.directory.size());
        for (Directory entry : packet.directory) {
            buffer.writeUUID(entry.id);
            buffer.writeUtf(entry.name, 48);
            buffer.writeEnum(entry.joinPolicy);
            buffer.writeBoolean(entry.pending);
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
            int applicationCount = buffer.readVarInt();
            if (applicationCount < 0 || applicationCount > 256)
                throw new IllegalArgumentException("Invalid application count");
            List<Application> applications = new ArrayList<>(applicationCount);
            for (int applicationIndex = 0; applicationIndex < applicationCount; applicationIndex++)
                applications.add(new Application(buffer.readUUID(), buffer.readUtf(40),
                        buffer.readLong()));
            organizations.add(new Summary(id, name, description, policy, founder,
                    List.copyOf(members), List.copyOf(applications)));
        }
        int directoryCount = buffer.readVarInt();
        if (directoryCount < 0 || directoryCount > 4096)
            throw new IllegalArgumentException("Invalid organization directory count");
        List<Directory> directory = new ArrayList<>(directoryCount);
        for (int index = 0; index < directoryCount; index++)
            directory.add(new Directory(buffer.readUUID(), buffer.readUtf(48),
                    buffer.readEnum(Organization.JoinPolicy.class), buffer.readBoolean()));
        return new OrganizationSyncPacket(List.copyOf(organizations), List.copyOf(directory),
                buffer.readUtf(80));
    }

    public static void handle(OrganizationSyncPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientOrganizationScreen.acceptSnapshot(packet)));
        context.setPacketHandled(true);
    }
}
