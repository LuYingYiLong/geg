package io.github.luyingyilong.geg.chat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Bounded conversations and read cursors, separate from wallets and organization state.
 */
public final class ChatSavedData extends SavedData {
    private final Map<ChatChannel, List<ChatMessage>> messages = new LinkedHashMap<>();
    private final Map<UUID, String> players = new LinkedHashMap<>();
    private final Map<UUID, Map<ChatChannel, Long>> reads = new HashMap<>();
    private final Map<UUID, Map<UUID, Scope>> scopes = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, Long>> requests = new HashMap<>();
    private long nextSequence = 1;

    public static ChatSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ChatSavedData::load, ChatSavedData::new, "geg_chat");
    }

    public static ChatSavedData load(CompoundTag root) {
        ChatSavedData data = new ChatSavedData();
        data.nextSequence = Math.max(1, root.getLong("nextSequence"));
        for (Tag raw : root.getList("conversations", Tag.TAG_COMPOUND))
            try {
                CompoundTag t = (CompoundTag) raw;
                ChatChannel channel = ChatChannel.load(t);
                List<ChatMessage> values = new ArrayList<>();
                for (Tag entry : t.getList("messages", Tag.TAG_COMPOUND)) {
                    ChatMessage message = ChatMessage.load((CompoundTag) entry);
                    values.add(message);
                    if (message.sequence() < Long.MAX_VALUE)
                        data.nextSequence = Math.max(data.nextSequence, message.sequence() + 1);
                }
                values.sort(Comparator.comparingLong(ChatMessage::sequence));
                data.messages.put(channel, new ArrayList<>(values.subList(Math.max(0, values.size() - 100), values.size())));
            } catch (RuntimeException ignored) { /* A malformed conversation must not grant access to another. */ }
        for (Tag raw : root.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (t.hasUUID("id")) data.remember(t.getUUID("id"), t.getString("name"));
        }
        for (Tag raw : root.getList("reads", Tag.TAG_COMPOUND))
            try {
                CompoundTag t = (CompoundTag) raw;
                data.reads.computeIfAbsent(t.getUUID("player"), ignored -> new HashMap<>()).put(ChatChannel.load(t), Math.max(0, t.getLong("read")));
            } catch (RuntimeException ignored) {
            }
        for (Tag raw : root.getList("scopes", Tag.TAG_COMPOUND))
            try {
                CompoundTag t = (CompoundTag) raw;
                data.scopes.computeIfAbsent(t.getUUID("organization"), ignored -> new HashMap<>()).put(t.getUUID("player"), new Scope(t.getUUID("epoch"), Math.max(1, t.getLong("first"))));
            } catch (RuntimeException ignored) {
            }
        for (Tag raw : root.getList("requests", Tag.TAG_COMPOUND))
            try {
                CompoundTag t = (CompoundTag) raw;
                LinkedHashMap<UUID, Long> ids = data.requests.computeIfAbsent(t.getUUID("player"), ignored -> new LinkedHashMap<>());
                ids.put(t.getUUID("request"), t.getLong("sequence"));
            } catch (RuntimeException ignored) {
            }
        return data;
    }

    public Map<UUID, String> players() {
        return Collections.unmodifiableMap(players);
    }

    public void remember(UUID id, String name) {
        if (name == null || name.length() > 40) return;
        if (!Objects.equals(players.put(id, name), name)) setDirty();
    }

    public List<ChatChannel> directChannels(UUID player) {
        return messages.keySet().stream().filter(c -> c.includes(player)).sorted(Comparator.comparingLong((ChatChannel c) -> latest(c)).reversed()).toList();
    }

    public void openDirect(ChatChannel channel) {
        if (channel.type() != ChatChannel.Type.DIRECT) throw new IllegalArgumentException("Not a direct channel");
        if (!messages.containsKey(channel)) {
            messages.put(channel, new ArrayList<>());
            setDirty();
        }
    }

    public long latest(ChatChannel channel) {
        List<ChatMessage> values = messages.getOrDefault(channel, List.of());
        return values.isEmpty() ? 0 : values.get(values.size() - 1).sequence();
    }

    public Scope scope(UUID organization, UUID player, UUID epoch) {
        Map<UUID, Scope> members = scopes.computeIfAbsent(organization, ignored -> new HashMap<>());
        Scope old = members.get(player);
        if (old == null || !old.epoch().equals(epoch)) {
            old = new Scope(epoch, nextSequence);
            members.put(player, old);
            reads.computeIfAbsent(player, ignored -> new HashMap<>()).remove(ChatChannel.organization(organization));
            setDirty();
        }
        return old;
    }

    public void initializeWorld(UUID player) {
        Map<ChatChannel, Long> values = reads.computeIfAbsent(player, ignored -> new HashMap<>());
        if (!values.containsKey(ChatChannel.WORLD)) {
            values.put(ChatChannel.WORLD, latest(ChatChannel.WORLD));
            setDirty();
        }
    }

    public List<ChatMessage> history(ChatChannel channel, long firstSequence, long before) {
        List<ChatMessage> all = messages.getOrDefault(channel, List.of()).stream().filter(m -> m.sequence() >= firstSequence && (before <= 0 || m.sequence() < before)).toList();
        return List.copyOf(all.subList(Math.max(0, all.size() - ChatPolicy.PAGE_SIZE), all.size()));
    }

    public boolean hasBefore(ChatChannel channel, long first, long before) {
        return messages.getOrDefault(channel, List.of()).stream().anyMatch(m -> m.sequence() >= first && m.sequence() < before);
    }

    public int unread(UUID player, ChatChannel channel, long first) {
        long read = reads.getOrDefault(player, Map.of()).getOrDefault(channel, 0L);
        return (int) messages.getOrDefault(channel, List.of()).stream().filter(m -> m.sequence() >= first && m.sequence() > read && !m.sender().equals(player)).count();
    }

    public void markRead(UUID player, ChatChannel channel, long sequence) {
        long bounded = Math.max(0, Math.min(sequence, latest(channel)));
        Map<ChatChannel, Long> values = reads.computeIfAbsent(player, ignored -> new HashMap<>());
        if (bounded > values.getOrDefault(channel, 0L)) {
            values.put(channel, bounded);
            setDirty();
        }
    }

    public boolean duplicate(UUID player, UUID request) {
        return requests.getOrDefault(player, new LinkedHashMap<>()).containsKey(request);
    }

    public Append append(UUID player, UUID request, ChatChannel channel, String name, Component text, long time) {
        LinkedHashMap<UUID, Long> sent = requests.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        Long previous = sent.get(request);
        if (previous != null)
            return new Append(messages.values().stream().flatMap(Collection::stream).filter(m -> m.sequence() == previous).findFirst().orElse(null), true);
        if (nextSequence == Long.MAX_VALUE) throw new IllegalStateException("Chat sequence exhausted");
        ChatMessage message = new ChatMessage(UUID.randomUUID(), nextSequence++, time, player, name, text);
        List<ChatMessage> values = messages.computeIfAbsent(channel, ignored -> new ArrayList<>());
        values.add(message);
        if (values.size() > ChatPolicy.HISTORY_LIMIT) values.remove(0);
        sent.put(request, message.sequence());
        // Keep request identities after message eviction, so an old retry cannot recreate it.
        setDirty();
        return new Append(message, false);
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putLong("nextSequence", nextSequence);
        ListTag conversations = new ListTag();
        messages.forEach((channel, values) -> {
            CompoundTag tag = channel.save();
            ListTag list = new ListTag();
            values.forEach(m -> list.add(m.save()));
            tag.put("messages", list);
            conversations.add(tag);
        });
        root.put("conversations", conversations);
        ListTag identities = new ListTag();
        players.forEach((id, name) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("name", name);
            identities.add(t);
        });
        root.put("players", identities);
        ListTag cursors = new ListTag();
        reads.forEach((player, channels) -> channels.forEach((channel, sequence) -> {
            CompoundTag t = channel.save();
            t.putUUID("player", player);
            t.putLong("read", sequence);
            cursors.add(t);
        }));
        root.put("reads", cursors);
        ListTag memberships = new ListTag();
        scopes.forEach((organization, members) -> members.forEach((player, scope) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("organization", organization);
            t.putUUID("player", player);
            t.putUUID("epoch", scope.epoch());
            t.putLong("first", scope.firstSequence());
            memberships.add(t);
        }));
        root.put("scopes", memberships);
        ListTag dedup = new ListTag();
        requests.forEach((player, ids) -> ids.forEach((id, sequence) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("player", player);
            t.putUUID("request", id);
            t.putLong("sequence", sequence);
            dedup.add(t);
        }));
        root.put("requests", dedup);
        return root;
    }

    public record Scope(UUID epoch, long firstSequence) {
    }

    public record Append(ChatMessage message, boolean duplicate) {
    }
}
