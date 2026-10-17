package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.money.*;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.github.luyingyilong.geg.money.MoneyService;
import net.minecraft.nbt.*;

import java.util.*;

/** Wallet, receipt, deduplication and journal mutations share the geg_money save boundary. */
public final class MailState {
    public enum Filter { ALL, UNREAD, PENDING }
    public enum Result { SUCCESS, NOT_FOUND, ALREADY_CLAIMED, WALLET_FULL, UNCLAIMED }
    public record Receipt(boolean read, boolean claimed, boolean deleted) {
        public static final Receipt NEW = new Receipt(false, false, false);
    }
    private final Map<UUID, MailMessage> messages = new HashMap<>();
    private final Map<UUID, Map<UUID, Receipt>> inboxes = new HashMap<>();
    private final Map<String, UUID> sends = new HashMap<>();
    private final Map<UUID, String> players = new HashMap<>();
    private final Map<UUID, Long> lastAnnouncement = new HashMap<>();
    private long revision;
    public long revision() { return revision; }
    public Map<UUID, String> players() { return Collections.unmodifiableMap(players); }
    public boolean remember(UUID id, String name) { return !Objects.equals(players.put(id, name), name); }
    public long lastAnnouncement(UUID actor) { return lastAnnouncement.getOrDefault(actor, Long.MIN_VALUE / 2); }
    public void announcementSent(UUID actor, long now) { lastAnnouncement.put(actor, now); }
    public UUID sent(String key) { return sends.get(key); }
    public static String pending(UUID player, UUID mail) { return "mail_pending:" + player + ":" + mail; }
    public Receipt receipt(UUID player, UUID mail) { return inboxes.getOrDefault(player, Map.of()).get(mail); }
    public MailMessage detail(UUID player, UUID mail) {
        Receipt r = receipt(player, mail);
        return r == null || r.deleted ? null : messages.get(mail);
    }
    public List<MailMessage> list(UUID player, Filter filter) {
        return inboxes.getOrDefault(player, Map.of()).entrySet().stream()
                .filter(entry -> !entry.getValue().deleted)
                .filter(entry -> filter != Filter.UNREAD || !entry.getValue().read)
                .map(entry -> messages.get(entry.getKey())).filter(Objects::nonNull)
                .filter(mail -> filter != Filter.PENDING || mail.coin() > 0 && !receipt(player, mail.id()).claimed)
                .sorted(Comparator.comparingLong(MailMessage::sentAt).reversed().thenComparing(MailMessage::id)).toList();
    }
    public List<MailMessage> list(UUID player, Filter filter, MailActionPacket.Scope scope) {
        return list(player, filter).stream().filter(mail -> mail.scope() == scope).toList();
    }
    public int unread(UUID player) { return (int) inboxes.getOrDefault(player, Map.of()).values().stream()
            .filter(r -> !r.deleted && !r.read).count(); }
    public UUID deliver(MoneySavedData data, String key, MailMessage message, Collection<UUID> recipients) {
        UUID existing = sends.get(key);
        if (existing != null) return existing;
        Set<UUID> unique = Set.copyOf(recipients);
        if (message.coin() < 0 || unique.isEmpty() || messages.containsKey(message.id()))
            throw new IllegalArgumentException("Invalid mail delivery");
        data.requireAuditCapacity(message.coin() == 0 ? 0 : unique.size());
        for (UUID player : unique) if (message.coin() > 0)
            data.record("mail_issue", MoneyCurrency.COIN, "system:mint", pending(player, message.id()),
                    message.coin(), message.id().toString());
        messages.put(message.id(), message);
        for (UUID player : unique) inboxes.computeIfAbsent(player, ignored -> new HashMap<>()).put(message.id(), Receipt.NEW);
        sends.put(key, message.id()); revision++; data.markChanged();
        return message.id();
    }
    public boolean markRead(MoneySavedData data, UUID player, UUID mail) {
        Receipt r = receipt(player, mail);
        if (detail(player, mail) == null) return false;
        if (!r.read) { inboxes.get(player).put(mail, new Receipt(true, r.claimed, false)); revision++; data.markChanged(); }
        return true;
    }
    public Result claim(MoneySavedData data, UUID player, UUID id) {
        MailMessage mail = detail(player, id);
        if (mail == null) return Result.NOT_FOUND;
        Receipt r = receipt(player, id);
        if (r.claimed || mail.coin() == 0) return Result.ALREADY_CLAIMED;
        MoneyBalance balance = data.balance(player);
        if (Long.MAX_VALUE - balance.coinMinorUnits() < mail.coin()) return Result.WALLET_FULL;
        data.requireAuditCapacity(1);
        data.record("mail_claim", MoneyCurrency.COIN, pending(player, id), MoneyService.wallet(player), mail.coin(), id.toString());
        data.put(player, balance.with(MoneyCurrency.COIN, balance.coinMinorUnits() + mail.coin()));
        inboxes.get(player).put(id, new Receipt(true, true, false)); revision++; data.markChanged();
        return Result.SUCCESS;
    }
    public Result delete(MoneySavedData data, UUID player, UUID id) {
        MailMessage mail = detail(player, id);
        if (mail == null) return Result.NOT_FOUND;
        Receipt r = receipt(player, id);
        if (mail.coin() > 0 && !r.claimed) return Result.UNCLAIMED;
        inboxes.get(player).put(id, new Receipt(r.read, r.claimed, true)); revision++; data.markChanged();
        return Result.SUCCESS;
    }
    public CompoundTag save() {
        CompoundTag root = new CompoundTag(); root.putLong("revision", revision);
        ListTag mail = new ListTag(); messages.values().forEach(value -> mail.add(value.save())); root.put("messages", mail);
        ListTag receipts = new ListTag(); inboxes.forEach((player, entries) -> entries.forEach((id, receipt) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("player", player); tag.putUUID("mail", id);
            tag.putBoolean("read", receipt.read); tag.putBoolean("claimed", receipt.claimed); tag.putBoolean("deleted", receipt.deleted);
            receipts.add(tag);
        })); root.put("receipts", receipts);
        ListTag operations = new ListTag(); sends.forEach((key, id) -> {
            CompoundTag tag = new CompoundTag(); tag.putString("key", key); tag.putUUID("mail", id); operations.add(tag);
        }); root.put("sends", operations);
        ListTag known = new ListTag(); players.forEach((id, name) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putString("name", name); known.add(tag);
        }); root.put("players", known);
        ListTag cooldowns = new ListTag(); lastAnnouncement.forEach((id, time) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putLong("time", time); cooldowns.add(tag);
        }); root.put("cooldowns", cooldowns);
        return root;
    }
    public static MailState load(CompoundTag root) {
        MailState state = new MailState(); state.revision = root.getLong("revision");
        for (Tag raw : root.getList("messages", Tag.TAG_COMPOUND)) {
            MailMessage mail = MailMessage.load((CompoundTag) raw); state.messages.put(mail.id(), mail);
        }
        for (Tag raw : root.getList("receipts", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (state.messages.containsKey(tag.getUUID("mail"))) state.inboxes.computeIfAbsent(tag.getUUID("player"), ignored -> new HashMap<>())
                    .put(tag.getUUID("mail"), new Receipt(tag.getBoolean("read"), tag.getBoolean("claimed"), tag.getBoolean("deleted")));
        }
        for (Tag raw : root.getList("sends", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw; state.sends.put(tag.getString("key"), tag.getUUID("mail"));
        }
        for (Tag raw : root.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw; state.players.put(tag.getUUID("id"), tag.getString("name"));
        }
        for (Tag raw : root.getList("cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw; state.lastAnnouncement.put(tag.getUUID("id"), tag.getLong("time"));
        }
        return state;
    }
}
