package io.github.luyingyilong.geg.organization;

import java.util.function.Supplier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record OrganizationActionPacket(Action action, String name, String description,
                                       Organization.JoinPolicy joinPolicy,
                                       UUID organizationId, UUID targetId) {
    private static final UUID EMPTY_ID = new UUID(0L, 0L);
    public enum Action {
        REQUEST, CREATE, DISBAND, PROMOTE, DEMOTE, REMOVE, LEAVE,
        APPLY, APPROVE, REJECT, CANCEL_APPLICATION, UPDATE
    }

    public static OrganizationActionPacket request() {
        return new OrganizationActionPacket(Action.REQUEST, "", "",
                Organization.JoinPolicy.UNRESTRICTED, EMPTY_ID, EMPTY_ID);
    }

    public static OrganizationActionPacket create(String name, String description,
                                                  Organization.JoinPolicy policy) {
        return new OrganizationActionPacket(Action.CREATE, name, description, policy, EMPTY_ID, EMPTY_ID);
    }

    public static OrganizationActionPacket update(UUID organizationId, String description,
                                                  Organization.JoinPolicy policy) {
        return new OrganizationActionPacket(Action.UPDATE, "", description, policy, organizationId, EMPTY_ID);
    }

    public static OrganizationActionPacket operate(OrganizationSavedData.Operation operation,
                                                   UUID organizationId, UUID targetId) {
        return new OrganizationActionPacket(Action.valueOf(operation.name()), "", "",
                Organization.JoinPolicy.UNRESTRICTED, organizationId, targetId);
    }

    public static void encode(OrganizationActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
        buffer.writeUtf(packet.name, 48);
        buffer.writeUtf(packet.description, 120);
        buffer.writeEnum(packet.joinPolicy);
        buffer.writeUUID(packet.organizationId);
        buffer.writeUUID(packet.targetId);
    }

    public static OrganizationActionPacket decode(FriendlyByteBuf buffer) {
        return new OrganizationActionPacket(buffer.readEnum(Action.class), buffer.readUtf(48),
                buffer.readUtf(120), buffer.readEnum(Organization.JoinPolicy.class),
                buffer.readUUID(), buffer.readUUID());
    }

    public static void handle(OrganizationActionPacket packet,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (packet.action == Action.REQUEST) {
                OrganizationNetwork.sync(player, "");
                return;
            }
            OrganizationSavedData data = OrganizationSavedData.get(player.server);
            if (packet.action == Action.CREATE) {
                OrganizationSavedData.CreateResult result = data.create(player.getUUID(),
                        packet.name, packet.description, packet.joinPolicy);
                String createResultKey = switch (result) {
                    case CREATED -> "organization.geg.created";
                    case INVALID_NAME -> "organization.geg.invalid_name";
                    case INVALID_DESCRIPTION -> "organization.geg.invalid_description";
                    case DUPLICATE_NAME -> "organization.geg.duplicate_name";
                    case TOO_MANY -> "organization.geg.too_many";
                };
                if (result == OrganizationSavedData.CreateResult.CREATED) {
                    for (ServerPlayer online : player.server.getPlayerList().getPlayers())
                        OrganizationNetwork.sync(online,
                                online.getUUID().equals(player.getUUID()) ? createResultKey : "");
                } else OrganizationNetwork.sync(player, createResultKey);
                return;
            }
            if (packet.action == Action.UPDATE) {
                OrganizationSavedData.UpdateResult result = data.update(player.getUUID(),
                        packet.organizationId, packet.description, packet.joinPolicy);
                String resultKey = switch (result) {
                    case UPDATED -> "organization.geg.updated";
                    case NOT_FOUND -> "organization.geg.result.not_found";
                    case NOT_MEMBER -> "organization.geg.result.not_member";
                    case FORBIDDEN -> "organization.geg.result.forbidden";
                    case INVALID_DESCRIPTION -> "organization.geg.invalid_description";
                };
                if (result == OrganizationSavedData.UpdateResult.UPDATED) {
                    for (ServerPlayer online : player.server.getPlayerList().getPlayers())
                        OrganizationNetwork.sync(online, online == player ? resultKey : "");
                } else OrganizationNetwork.sync(player, resultKey);
                return;
            }
            Organization old = data.organization(packet.organizationId);
            OrganizationSavedData.Operation operation = OrganizationSavedData.Operation.valueOf(
                    packet.action.name());
            int firstMailEvent = data.mailEvents().size();
            OrganizationSavedData.OperationResult result = data.operate(player.getUUID(),
                    packet.organizationId, packet.targetId, operation);
            String resultKey = switch (result) {
                case SUCCESS -> operation == OrganizationSavedData.Operation.APPLY
                        && old != null && old.joinPolicy() == Organization.JoinPolicy.APPROVAL
                        ? "organization.geg.result.application_sent"
                        : "organization.geg.result." + operation.name().toLowerCase(java.util.Locale.ROOT);
                case NOT_FOUND -> "organization.geg.result.not_found";
                case NOT_MEMBER -> "organization.geg.result.not_member";
                case FORBIDDEN -> "organization.geg.result.forbidden";
                case INVALID_TARGET -> "organization.geg.result.invalid_target";
                case ALREADY_MEMBER -> "organization.geg.result.already_member";
                case ALREADY_APPLIED -> "organization.geg.result.already_applied";
                case TOO_MANY -> "organization.geg.result.too_many";
                case NO_APPLICATION -> "organization.geg.result.no_application";
            };
            if (result != OrganizationSavedData.OperationResult.SUCCESS) {
                OrganizationNetwork.sync(player, resultKey);
                return;
            }
            Organization updated = data.organization(packet.organizationId);
            List<OrganizationSavedData.MailEvent> events = data.mailEvents();
            List<OrganizationSavedData.MailEvent> newEvents = events.subList(firstMailEvent, events.size());
            Set<UUID> affected = new HashSet<>();
            affected.add(player.getUUID());
            if (old != null) affected.addAll(old.members());
            if (updated != null) affected.addAll(updated.members());
            if (operation == OrganizationSavedData.Operation.APPROVE
                    || operation == OrganizationSavedData.Operation.REJECT)
                affected.add(packet.targetId);
            if (operation == OrganizationSavedData.Operation.DISBAND)
                for (ServerPlayer online : player.server.getPlayerList().getPlayers())
                    affected.add(online.getUUID());
            for (UUID member : affected) {
                ServerPlayer online = player.server.getPlayerList().getPlayer(member);
                if (online != null) {
                    String notice = member.equals(player.getUUID()) ? resultKey
                            : operation == OrganizationSavedData.Operation.DISBAND
                            && old != null && old.members().contains(member)
                            ? "organization.geg.result.disbanded_notice"
                            : operation == OrganizationSavedData.Operation.REMOVE
                            && member.equals(packet.targetId)
                            ? "organization.geg.result.removed_notice"
                            : operation == OrganizationSavedData.Operation.APPROVE
                            && member.equals(packet.targetId)
                            ? "organization.geg.result.approved_notice"
                            : operation == OrganizationSavedData.Operation.REJECT
                            && member.equals(packet.targetId)
                            ? "organization.geg.result.rejected_notice" : "";
                    OrganizationNetwork.sync(online, notice);
                    if (!notice.isEmpty()) {
                        for (OrganizationSavedData.MailEvent event : newEvents) {
                            if (event.recipient().equals(member))
                                io.github.luyingyilong.geg.mail.MailService.coverSystemNotification(online, event.id());
                        }
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
