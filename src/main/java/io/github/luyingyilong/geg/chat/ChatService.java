package io.github.luyingyilong.geg.chat;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.organization.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** All routing and authorization runs on the server thread, including offline mailboxes. */
@Mod.EventBusSubscriber(modid = GegMod.MODID)
public final class ChatService {
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> BRIDGING = ThreadLocal.withInitial(() -> false);
    private static final class Session {
        final Map<UUID, ChatPolicy.RateLimit> rates = new HashMap<>();
        final Map<UUID, String> signatures = new HashMap<>();
        final Map<UUID, long[]> requests = new HashMap<>();
        int ticks;
    }
    private ChatService() { }
    private static Session session(MinecraftServer server) { return SESSIONS.computeIfAbsent(server, ignored -> new Session()); }
    private static void thread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Chat must run on the server thread");
    }
    private static void reply(ServerPlayer player, ChatSyncPacket packet) {
        if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) ChatNetwork.reply(player, packet);
    }

    public static long firstSequence(MinecraftServer server, UUID player, ChatChannel channel) {
        ChatSavedData data = ChatSavedData.get(server);
        return switch (channel.type()) {
            case WORLD -> 1;
            case DIRECT -> channel.includes(player) ? 1 : -1;
            case ORGANIZATION -> {
                UUID epoch = OrganizationSavedData.get(server).chatEpoch(channel.first(), player);
                yield epoch == null ? -1 : data.scope(channel.first(), player, epoch).firstSequence();
            }
        };
    }
    public static ChatSyncPacket.Conversation conversation(MinecraftServer server, UUID player, ChatChannel channel) {
        long first = firstSequence(server, player, channel);
        if (first < 0) return null;
        ChatSavedData data = ChatSavedData.get(server);
        Component title; UUID epoch = ChatChannel.NONE;
        if (channel.type() == ChatChannel.Type.WORLD) title = Component.translatable("chat.geg.world");
        else if (channel.type() == ChatChannel.Type.ORGANIZATION) {
            var organizations = OrganizationSavedData.get(server);
            title = Component.literal(organizations.organization(channel.first()).name());
            epoch = organizations.chatEpoch(channel.first(), player);
        } else title = Component.literal(data.players().getOrDefault(channel.other(player), channel.other(player).toString()));
        return new ChatSyncPacket.Conversation(channel, title, epoch, data.unread(player, channel, first), data.latest(channel));
    }
    public static List<ChatSyncPacket.Conversation> directory(MinecraftServer server, UUID player) {
        ChatSavedData data = ChatSavedData.get(server); data.initializeWorld(player);
        List<ChatSyncPacket.Conversation> values = new ArrayList<>();
        values.add(conversation(server, player, ChatChannel.WORLD));
        for (Organization org : OrganizationSavedData.get(server).memberships(player))
            values.add(conversation(server, player, ChatChannel.organization(org.id())));
        for (ChatChannel channel : data.directChannels(player)) values.add(conversation(server, player, channel));
        return List.copyOf(values);
    }
    public static void syncDirectory(ServerPlayer player) {
        ChatSavedData data = ChatSavedData.get(player.server);
        List<ChatSyncPacket.Player> players = data.players().entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).limit(4096)
                .map(entry -> new ChatSyncPacket.Player(entry.getKey(), entry.getValue(),
                        player.server.getPlayerList().getPlayer(entry.getKey()) != null)).toList();
        reply(player, new ChatSyncPacket(ChatSyncPacket.Kind.DIRECTORY, directory(player.server, player.getUUID()),
                players, ChatChannel.WORLD, List.of(), false, ChatChannel.NONE, ""));
    }
    public static UUID resolvePlayer(MinecraftServer server, String target) {
        var data = ChatSavedData.get(server);
        try { UUID id = UUID.fromString(target); if (data.players().containsKey(id)) return id; }
        catch (IllegalArgumentException ignored) { }
        return data.players().entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(target))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public static void select(ServerPlayer player, ChatChannel channel) {
        if (firstSequence(player.server, player.getUUID(), channel) < 0) return;
        if (channel.type() == ChatChannel.Type.DIRECT) ChatSavedData.get(player.server).openDirect(channel);
        syncDirectory(player);
        reply(player, new ChatSyncPacket(ChatSyncPacket.Kind.SELECT, List.of(), List.of(), channel,
                List.of(), false, ChatChannel.NONE, ""));
    }
    public static void handle(ServerPlayer player, ChatRequest request) {
        thread(player.server);
        long now = System.currentTimeMillis();
        long[] budget = session(player.server).requests.computeIfAbsent(player.getUUID(), ignored -> new long[]{now, 0});
        if (now - budget[0] >= 1000) { budget[0] = now; budget[1] = 0; }
        if (++budget[1] > 40) return;
        switch (request.action()) {
            case DIRECTORY -> syncDirectory(player);
            case OPEN_DIRECT -> {
                UUID target = resolvePlayer(player.server, request.text().strip());
                if (target == null || target.equals(player.getUUID())) reply(player, ChatSyncPacket.result(request.request(), "chat.geg.unknown_player"));
                else select(player, ChatChannel.direct(player.getUUID(), target));
            }
            case SEND -> reply(player, ChatSyncPacket.result(request.request(), send(player, request.channel(), request.request(), request.text())));
            case HISTORY, READ -> {
                long first = firstSequence(player.server, player.getUUID(), request.channel());
                if (first < 0) { reply(player, ChatSyncPacket.result(request.request(), "chat.geg.forbidden")); syncDirectory(player); return; }
                var data = ChatSavedData.get(player.server);
                if (request.action() == ChatRequest.Action.READ) {
                    data.markRead(player.getUUID(), request.channel(), request.cursor()); syncDirectory(player);
                } else {
                    List<ChatMessage> page = data.history(request.channel(), first, request.cursor());
                    boolean more = !page.isEmpty() && data.hasBefore(request.channel(), first, page.get(0).sequence());
                    reply(player, new ChatSyncPacket(ChatSyncPacket.Kind.HISTORY,
                            List.of(conversation(player.server, player.getUUID(), request.channel())), List.of(), request.channel(), page,
                            more, request.request(), ""));
                }
            }
        }
    }
    public static String send(ServerPlayer player, ChatChannel channel, UUID request, String raw) {
        thread(player.server);
        String text = ChatPolicy.normalize(raw);
        if (!ChatPolicy.valid(text)) return "chat.geg.invalid_message";
        if (player.getChatVisibility() != ChatVisiblity.FULL) return "chat.geg.chat_hidden";
        if (firstSequence(player.server, player.getUUID(), channel) < 0) return "chat.geg.forbidden";
        ChatSavedData data = ChatSavedData.get(player.server);
        if (channel.type() == ChatChannel.Type.DIRECT && !data.players().containsKey(channel.other(player.getUUID())))
            return "chat.geg.unknown_player";
        if (data.duplicate(player.getUUID(), request)) return "chat.geg.sent";
        if (!session(player.server).rates.computeIfAbsent(player.getUUID(), ignored -> new ChatPolicy.RateLimit())
                .take(System.nanoTime() / 1_000_000)) return "chat.geg.rate_limited";
        Component content = ForgeHooks.newChatWithLinks(text);
        if (channel.type() == ChatChannel.Type.WORLD) {
            BRIDGING.set(true);
            try { content = ForgeHooks.onServerChatSubmittedEvent(player, text, content); }
            finally { BRIDGING.remove(); }
            if (content == null) return "chat.geg.cancelled";
        }
        if (Component.Serializer.toJson(content).length() > 8192) return "chat.geg.invalid_message";
        publish(player, channel, request, content);
        return "chat.geg.sent";
    }
    private static void publish(ServerPlayer sender, ChatChannel channel, UUID request, Component content) {
        MinecraftServer server = sender.server; ChatSavedData data = ChatSavedData.get(server);
        data.remember(sender.getUUID(), sender.getGameProfile().getName());
        // Establish all current members' sequence boundaries before allocating this message's sequence.
        if (channel.type() == ChatChannel.Type.ORGANIZATION)
            for (UUID member : OrganizationSavedData.get(server).organization(channel.first()).members()) firstSequence(server, member, channel);
        var append = data.append(sender.getUUID(), request, channel, sender.getGameProfile().getName(), content, System.currentTimeMillis());
        if (append.duplicate() || append.message() == null) return;
        ChatMessage message = append.message();
        data.markRead(sender.getUUID(), channel, message.sequence());
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.getChatVisibility() != ChatVisiblity.FULL || firstSequence(server, viewer.getUUID(), channel) < 0) continue;
            ChatNetwork.reply(viewer, new ChatSyncPacket(ChatSyncPacket.Kind.MESSAGE,
                    List.of(conversation(server, viewer.getUUID(), channel)), List.of(), channel, List.of(message), false, request, ""));
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void vanillaChat(ServerChatEvent event) {
        if (BRIDGING.get()) return;
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer(); String raw = event.getRawText(); Component message = event.getMessage().copy();
        // Forge's native chat decorator runs on its background executor. SavedData must not.
        Runnable route = () -> {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) != player
                    || player.getChatVisibility() != ChatVisiblity.FULL || !ChatPolicy.valid(raw)) return;
            if (!session(player.server).rates.computeIfAbsent(player.getUUID(), ignored -> new ChatPolicy.RateLimit())
                    .take(System.nanoTime() / 1_000_000)) {
                player.sendSystemMessage(Component.translatable("chat.geg.rate_limited")); return;
            }
            if (Component.Serializer.toJson(message).length() <= 8192) publish(player, ChatChannel.WORLD, UUID.randomUUID(), message);
        };
        if (player.server.isSameThread()) route.run(); else player.server.execute(route);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        MinecraftServer server = event.getServer(); ChatSavedData data = ChatSavedData.get(server);
        io.github.luyingyilong.geg.money.MoneySavedData.get(server).mail().players().forEach(data::remember);
        var folder = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(folder)) try (var files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().endsWith(".dat")).forEach(path -> {
                try {
                    UUID id = UUID.fromString(path.getFileName().toString().replace(".dat", ""));
                    data.remember(id, server.getProfileCache().get(id).map(p -> p.getName())
                            .orElse(data.players().getOrDefault(id, id.toString())));
                } catch (IllegalArgumentException ignored) { }
            });
        } catch (java.io.IOException exception) { org.slf4j.LoggerFactory.getLogger(ChatService.class).warn("Could not index known chat players", exception); }
        for (Organization org : OrganizationSavedData.get(server).directory())
            for (UUID member : org.members()) firstSequence(server, member, ChatChannel.organization(org.id()));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChatSavedData.get(player.server).remember(player.getUUID(), player.getGameProfile().getName()); syncDirectory(player);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer(); Session session = session(server);
        if (++session.ticks % 20 != 0) return;
        String online = server.getPlayerList().getPlayers().stream().map(p -> p.getUUID().toString()).sorted().toList().toString();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<ChatSyncPacket.Conversation> channels = directory(server, player.getUUID());
            String signature = channels + online;
            if (!signature.equals(session.signatures.put(player.getUUID(), signature))) syncDirectory(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Retain short-window quotas across reconnects; only directory delivery state resets.
            session(player.server).signatures.remove(player.getUUID());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
}
