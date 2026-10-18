package io.github.luyingyilong.geg.chat.client;

import io.github.luyingyilong.geg.chat.*;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Session-only UI caches. Persistent history and read positions belong to the server. */
public final class ClientChatState {
    public static final class View {
        public final NavigableMap<Long, ChatMessage> messages = new TreeMap<>();
        public String draft = "";
        public String status = "";
        public int scroll;
        public boolean opened;
        public boolean more, loading, available = true;
        public UUID epoch = ChatChannel.NONE;
        public long readSent;
    }
    public record HudLine(Component text, long time, boolean system) { }
    private record Pending(ChatChannel channel, String text) { }
    private record History(ChatChannel channel, long before) { }
    private static final Map<ChatChannel, View> VIEWS = new HashMap<>();
    private static final Map<ChatChannel, ChatSyncPacket.Conversation> CHANNELS = new LinkedHashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, History> HISTORY = new HashMap<>();
    private static final Deque<HudLine> HUD = new ArrayDeque<>();
    private static final Set<UUID> RECEIVED = new LinkedHashSet<>();
    private static final List<ChatMessage> SYSTEM = new ArrayList<>();
    private static List<ChatSyncPacket.Player> players = List.of();
    private static ChatChannel selected = ChatChannel.WORLD;
    private static long revision, localSequence;
    private static boolean enabled;
    private static java.util.function.Consumer<ChatRequest> transport = ChatNetwork::send;
    static void transport(java.util.function.Consumer<ChatRequest> sender) { transport = sender; }
    private ClientChatState() { }
    public static boolean enabled() { return enabled; }
    public static long revision() { return revision; }
    public static ChatChannel selected() { return selected; }
    public static View view(ChatChannel channel) { return VIEWS.computeIfAbsent(channel, ignored -> new View()); }
    public static List<ChatSyncPacket.Conversation> channels() { return List.copyOf(CHANNELS.values()); }
    public static List<ChatSyncPacket.Player> players() { return players; }
    public static List<HudLine> hud() { return List.copyOf(HUD); }
    public static Component title(ChatChannel channel) {
        var value = CHANNELS.get(channel);
        return value == null ? Component.translatable("chat.geg.unavailable") : value.title();
    }
    public static boolean pending(ChatChannel channel) { return PENDING.values().stream().anyMatch(p -> p.channel.equals(channel)); }
    public static List<ChatMessage> messages(ChatChannel channel) {
        List<ChatMessage> result = new ArrayList<>(view(channel).messages.values());
        if (channel.type() == ChatChannel.Type.WORLD) result.addAll(SYSTEM);
        result.sort(Comparator.comparingLong(ChatMessage::time).thenComparingLong(ChatMessage::sequence));
        return result;
    }
    public static void select(ChatChannel channel) {
        selected = channel;
        if (!view(channel).loading) history(channel, 0);
        revision++;
    }
    public static void history(ChatChannel channel, long before) {
        View view = view(channel); if (view.loading || !view.available) return;
        UUID request = UUID.randomUUID(); view.loading = true;
        HISTORY.put(request, new History(channel, before));
        transport.accept(new ChatRequest(ChatRequest.Action.HISTORY, request, channel, before, ""));
    }
    public static void older() {
        View view = view(selected);
        if (view.more && !view.messages.isEmpty()) history(selected, view.messages.firstKey());
    }
    public static void markRead() {
        View view = view(selected);
        long latest = view.messages.isEmpty() ? 0 : view.messages.lastKey();
        if (latest > view.readSent && view.available) {
            view.readSent = latest; transport.accept(ChatRequest.query(ChatRequest.Action.READ, selected, latest));
        }
    }
    public static void send(String text) {
        View view = view(selected);
        if (pending(selected) || !view.available) return;
        UUID id = UUID.randomUUID(); PENDING.put(id, new Pending(selected, text)); view.status = "";
        transport.accept(new ChatRequest(ChatRequest.Action.SEND, id, selected, 0, text)); revision++;
    }
    public static void openDirect(String target) {
        transport.accept(new ChatRequest(ChatRequest.Action.OPEN_DIRECT, UUID.randomUUID(), ChatChannel.WORLD, 0, target));
    }
    private static void metadata(ChatSyncPacket.Conversation channel) {
        View view = view(channel.channel());
        if (!view.epoch.equals(channel.epoch())) {
            view.messages.clear(); view.readSent = 0; view.more = false; view.scroll = 0; view.epoch = channel.epoch();
        }
        view.available = true; CHANNELS.put(channel.channel(), channel);
    }
    public static void accept(ChatSyncPacket packet) {
        enabled = true;
        if (packet.kind() == ChatSyncPacket.Kind.DIRECTORY) {
            Set<ChatChannel> valid = new HashSet<>(); packet.conversations().forEach(c -> valid.add(c.channel()));
            for (var entry : VIEWS.entrySet()) if (entry.getKey().type() == ChatChannel.Type.ORGANIZATION && !valid.contains(entry.getKey())) {
                View v = entry.getValue(); v.available = false; v.messages.clear(); v.more = false; v.status = "chat.geg.forbidden";
            }
            CHANNELS.clear(); packet.conversations().forEach(ClientChatState::metadata); players = packet.players();
        } else if (packet.kind() == ChatSyncPacket.Kind.MESSAGE) {
            packet.conversations().forEach(ClientChatState::metadata);
            View view = view(packet.channel());
            for (ChatMessage message : packet.messages()) {
                view.messages.put(message.sequence(), message);
                if (RECEIVED.add(message.id())) {
                    Component prefix = Component.literal("[GEG - ").append(title(packet.channel())).append("] ")
                            .append(Component.literal(message.senderName() + ": ")).append(message.text());
                    hud(prefix, false);
                }
            }
            while (RECEIVED.size() > 500) RECEIVED.remove(RECEIVED.iterator().next());
            while (view.messages.size() > 100) view.messages.pollFirstEntry();
        } else if (packet.kind() == ChatSyncPacket.Kind.HISTORY) {
            History history = HISTORY.remove(packet.request());
            if (history != null) {
                packet.conversations().forEach(ClientChatState::metadata);
                View view = view(history.channel); view.loading = false;
                if (history.before == 0) view.messages.clear();
                for (ChatMessage message : packet.messages()) view.messages.put(message.sequence(), message);
                while (view.messages.size() > 100) view.messages.pollFirstEntry();
                view.more = packet.more();
            }
        } else if (packet.kind() == ChatSyncPacket.Kind.RESULT) {
            Pending pending = PENDING.remove(packet.request());
            History history = HISTORY.remove(packet.request());
            if (history != null) { view(history.channel).loading = false; view(history.channel).status = packet.result(); }
            if (pending != null) {
                View view = view(pending.channel);
                if (packet.result().equals("chat.geg.sent")) {
                    if (view.draft.equals(pending.text)) view.draft = "";
                    view.status = "";
                } else view.status = packet.result();
            } else if (history == null && !packet.result().isEmpty()) view(selected).status = packet.result();
        } else if (packet.kind() == ChatSyncPacket.Kind.SELECT) {
            select(packet.channel()); ClientChatScreen.selected();
        }
        revision++;
    }
    public static void temporary(Component text) {
        long now = System.currentTimeMillis();
        SYSTEM.add(new ChatMessage(UUID.randomUUID(), Long.MAX_VALUE / 2 + ++localSequence, now,
                ChatChannel.NONE, "", text));
        if (SYSTEM.size() > 100) SYSTEM.remove(0);
        hud(text, true); revision++;
    }
    private static void hud(Component text, boolean system) {
        HUD.addLast(new HudLine(text.copy(), System.nanoTime() / 1_000_000, system));
        while (HUD.size() > 20) HUD.removeFirst();
    }
    public static void clear() {
        VIEWS.clear(); CHANNELS.clear(); PENDING.clear(); HISTORY.clear(); HUD.clear(); RECEIVED.clear(); SYSTEM.clear();
        selected = ChatChannel.WORLD; players = List.of(); enabled = false; revision++; localSequence = 0;
    }
}
