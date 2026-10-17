package io.github.luyingyilong.geg.mail;

import com.mojang.logging.LogUtils;
import io.github.luyingyilong.geg.money.*;
import io.github.luyingyilong.geg.money.MoneyNetwork;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.github.luyingyilong.geg.organization.*;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** All public server operations execute on the Minecraft server thread. */
public final class MailService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static final class Session {
        final Map<String, Draft> drafts = new HashMap<>();
        final Map<UUID, long[]> requests = new HashMap<>();
        final MailNotificationCoverage<ServerGamePacketListenerImpl> systemNotifications = new MailNotificationCoverage<>();
        int eventCursor;
    }
    private record Draft(MailActionPacket request, List<UUID> recipients, String sender, long createdAt) { }
    private static final class Failure extends RuntimeException {
        Failure(String key) { super("mail.geg." + key); }
    }
    private MailService() { }
    private static Session session(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new Session()); }
    private static void thread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Mail requires the server thread");
    }
    private static String sendKey(UUID actor, UUID token) { return "manual:" + actor + ":" + token; }
    public static void handle(ServerPlayer player, MailActionPacket request) {
        thread(player.server);
        long now = System.currentTimeMillis();
        Session session = session(player.server);
        long[] quota = session.requests.computeIfAbsent(player.getUUID(), ignored -> new long[]{now, 0});
        if (now - quota[0] >= 1000) { quota[0] = now; quota[1] = 0; }
        if (++quota[1] > 80) return;
        MoneySavedData data = MoneySavedData.get(player.server);
        MailState state = data.mail();
        try {
            switch (request.action()) {
                case LIST -> {
                    if (request.offset() < 0 || request.offset() % 50 != 0) throw new Failure("invalid_target");
                    var all = state.list(player.getUUID(), request.filter(), request.scope());
                    int start = Math.min(request.offset(), all.size());
                    int end = Math.min(start + 50, all.size());
                    List<MailSyncPacket.Row> rows = all.subList(start, end).stream().map(mail -> {
                        var receipt = state.receipt(player.getUUID(), mail.id());
                        return new MailSyncPacket.Row(mail.id(), mail.titleText(), mail.sender(), mail.kind(), mail.sentAt(),
                                mail.coin(), receipt.read(), receipt.claimed(), mail.scope(), !mail.items().isEmpty());
                    }).toList();
                    MailNetwork.reply(player, new MailSyncPacket(MailSyncPacket.Kind.LIST, state.revision(), state.unread(player.getUUID()),
                            player.hasPermissions(2), request.filter(), request.offset(), end < all.size(), rows, null, false,
                            request.id(), 0, 0, "", ""));
                }
                case DETAIL, READ -> {
                    MailMessage mail = state.detail(player.getUUID(), request.id());
                    if (mail == null) throw new Failure("not_found");
                    state.markRead(data, player.getUUID(), request.id());
                    MailNetwork.reply(player, new MailSyncPacket(MailSyncPacket.Kind.DETAIL, state.revision(), state.unread(player.getUUID()),
                            player.hasPermissions(2), request.filter(), request.offset(), false, List.of(), mail,
                            state.receipt(player.getUUID(), mail.id()).claimed(), MailActionPacket.EMPTY, 0, 0, "", ""));
                }
                case CLAIM -> {
                    MailState.Result result = state.claim(data, player.getUUID(), request.id(), player.getInventory());
                    if (result == MailState.Result.SUCCESS || result == MailState.Result.PARTIAL) {
                        MoneyNetwork.sync(player, data.balance(player.getUUID()));
                        player.inventoryMenu.broadcastChanges();
                    }
                    result(player, result.name().toLowerCase(Locale.ROOT), request.id());
                }
                case DELETE -> result(player, state.delete(data, player.getUUID(), request.id()).name().toLowerCase(Locale.ROOT), request.id());
                case PREVIEW -> {
                    String key = sendKey(player.getUUID(), request.id());
                    if (state.sent(key) != null) { result(player, "sent", request.id()); break; }
                    session.drafts.entrySet().removeIf(entry -> now - entry.getValue().createdAt > 600_000);
                    Draft draft = resolve(player, request, now);
                    // One active composer per player; retain only its current preview.
                    session.drafts.keySet().removeIf(existing -> existing.startsWith("manual:" + player.getUUID() + ":"));
                    session.drafts.put(key, draft);
                    preview(player, draft, "");
                }
                case CONFIRM -> {
                    String key = sendKey(player.getUUID(), request.id());
                    if (state.sent(key) != null) { result(player, "sent", request.id()); break; }
                    Draft original = session.drafts.get(key);
                    if (original == null || now - original.createdAt > 600_000) throw new Failure("expired");
                    Draft current = resolve(player, original.request, now);
                    if (!current.recipients.equals(original.recipients)) {
                        session.drafts.put(key, current); preview(player, current, "mail.geg.recipients_changed"); break;
                    }
                    var p = original.request;
                    MailMessage mail = new MailMessage(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)), now,
                            p.organizationMail() ? MailMessage.Kind.ORGANIZATION : MailMessage.Kind.ADMIN,
                            current.sender, p.title().strip(), p.body(), List.of(), p.coin(), p.scope(),
                            player.getUUID(), player.getGameProfile().getName());
                    state.deliver(data, key, mail, current.recipients);
                    if (p.organizationMail()) state.announcementSent(player.getUUID(), now);
                    data.markChanged(); session.drafts.remove(key);
                    result(player, "sent", request.id());
                    for (UUID id : current.recipients) {
                        ServerPlayer online = player.server.getPlayerList().getPlayer(id);
                        if (online != null) MailNetwork.status(online);
                    }
                }
            }
        } catch (Failure | MailPolicy.Rejected exception) {
            result(player, exception.getMessage().substring("mail.geg.".length()), request.id());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Mail request failed for {}", player.getUUID(), exception);
            result(player, "unavailable", request.id());
        }
    }
    private static void result(ServerPlayer player, String key, UUID token) {
        var p = MailNetwork.packet(player, MailSyncPacket.Kind.RESULT, "mail.geg." + key);
        MailNetwork.reply(player, new MailSyncPacket(p.kind(), p.revision(), p.unread(), p.admin(), p.filter(),
                0, false, List.of(), null, false, token, 0, 0, "", p.result()));
    }
    private static Draft resolve(ServerPlayer player, MailActionPacket request, long now) {
        var state = MoneySavedData.get(player.server).mail();
        var targets = MailPolicy.resolve(player.getUUID(), player.hasPermissions(2),
                OrganizationSavedData.get(player.server).directory(), state.players(), request, now,
                state.lastAnnouncement(player.getUUID()));
        return new Draft(request, targets.players(), request.organizationMail()
                ? targets.organizationName() : player.getGameProfile().getName(), now);
    }
    private static void preview(ServerPlayer player, Draft draft, String result) {
        var state = MoneySavedData.get(player.server).mail();
        long coin = draft.request.coin();
        String total = new java.math.BigDecimal(BigInteger.valueOf(coin).multiply(BigInteger.valueOf(draft.recipients.size())), 2).toPlainString();
        MailNetwork.reply(player, new MailSyncPacket(MailSyncPacket.Kind.PREVIEW, state.revision(), state.unread(player.getUUID()),
                player.hasPermissions(2), MailState.Filter.ALL, 0, false, List.of(), null, false,
                draft.request.id(), draft.recipients.size(), coin, total, result));
    }
    /** Called only after sending a result toast to this online recipient for this persisted event. */
    public static void coverSystemNotification(ServerPlayer recipient, UUID event) {
        thread(recipient.server);
        session(recipient.server).systemNotifications.cover(event, recipient.connection);
    }

    public static void system(MinecraftServer server, UUID event, UUID recipient, String organization, String action) {
        thread(server);
        String key = "organization:" + event;
        MoneySavedData data = MoneySavedData.get(server);
        if (data.mail().sent(key) != null) {
            session(server).systemNotifications.consume(event, null);
            return;
        }
        var mail = new MailMessage(event, System.currentTimeMillis(), MailMessage.Kind.SYSTEM, organization,
                "mail.geg.system.title", "mail.geg.system." + action, List.of(organization), 0);
        data.mail().deliver(data, key, mail, List.of(recipient));
        ServerPlayer player = server.getPlayerList().getPlayer(recipient);
        boolean covered = session(server).systemNotifications.consume(event, player == null ? null : player.connection);
        if (player != null) MailNetwork.status(player, !covered);
    }
    public static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        MoneySavedData data = MoneySavedData.get(server);
        Path folder = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(folder)) try (var paths = Files.list(folder)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".dat")).forEach(path -> {
                String name = path.getFileName().toString();
                try {
                    UUID id = UUID.fromString(name.substring(0, name.length() - 4));
                    String display = server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString());
                    data.mail().remember(id, display);
                } catch (IllegalArgumentException ignored) { }
            });
            data.markChanged();
        } catch (java.io.IOException exception) { LOGGER.warn("Cannot enumerate existing mail recipients", exception); }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) remember(player);
    }
    private static void remember(ServerPlayer player) {
        MoneySavedData data = MoneySavedData.get(player.server);
        if (data.mail().remember(player.getUUID(), player.getGameProfile().getName())) data.markChanged();
    }
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { remember(player); MailNetwork.status(player); }
    }
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var session = session(player.server); session.requests.remove(player.getUUID());
            session.systemNotifications.forget(player.connection);
            session.drafts.keySet().removeIf(key -> key.startsWith("manual:" + player.getUUID() + ":"));
        }
    }
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        var server = event.getServer(); var session = session(server);
        var events = OrganizationSavedData.get(server).mailEvents();
        for (int i = 0; i < 100 && session.eventCursor < events.size(); i++) {
            var next = events.get(session.eventCursor);
            system(server, next.id(), next.recipient(), next.organization(), next.action()); session.eventCursor++;
        }
    }
    public static void onStopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
