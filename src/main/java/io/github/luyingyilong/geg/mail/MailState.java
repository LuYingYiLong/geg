package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.money.*;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.github.luyingyilong.geg.money.MoneyService;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Wallet, receipt, deduplication and journal mutations share the geg_money save boundary. */
public final class MailState {
    public enum Filter { ALL, UNREAD, PENDING }
    public enum Result { SUCCESS, PARTIAL, NO_ROOM, NOT_FOUND, ALREADY_CLAIMED, WALLET_FULL, UNCLAIMED }
    public record Receipt(boolean read, boolean claimed, boolean deleted) {
        public static final Receipt NEW = new Receipt(false, false, false);
    }
    private final Map<UUID, MailMessage> messages = new HashMap<>();
    private final Map<UUID, Map<UUID, Receipt>> inboxes = new HashMap<>();
    private record Attachments(long coin, List<MailItem> items) {
        Attachments { items = List.copyOf(items); }
    }
    private final Map<UUID, Map<UUID, Attachments>> pendingItems = new HashMap<>();
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
        if (r == null || r.deleted) return null;
        MailMessage message = messages.get(mail);
        Attachments remaining = pendingItems.getOrDefault(player, Map.of()).get(mail);
        return remaining == null ? message : message.attachments(remaining.coin, remaining.items);
    }
    public List<MailMessage> list(UUID player, Filter filter) {
        return inboxes.getOrDefault(player, Map.of()).entrySet().stream()
                .filter(entry -> !entry.getValue().deleted)
                .filter(entry -> filter != Filter.UNREAD || !entry.getValue().read)
                .map(entry -> messages.get(entry.getKey())).filter(Objects::nonNull)
                .filter(mail -> filter != Filter.PENDING || mail.hasAttachments() && !receipt(player, mail.id()).claimed)
                .sorted(Comparator.comparingLong(MailMessage::sentAt).reversed().thenComparing(MailMessage::id)).toList();
    }
    public List<MailMessage> list(UUID player, Filter filter, MailActionPacket.Scope scope) {
        return list(player, filter).stream().filter(mail -> mail.scope() == scope).toList();
    }
    public int unread(UUID player) { return (int) inboxes.getOrDefault(player, Map.of()).values().stream()
            .filter(r -> !r.deleted && !r.read).count(); }
    public void forEachCoinHolding(java.util.function.BiConsumer<String, Long> consumer) {
        inboxes.forEach((player, entries) -> entries.forEach((id, receipt) -> {
            if (!receipt.claimed && !receipt.deleted) {
                MailMessage mail = detail(player, id);
                if (mail != null && mail.coin() > 0) consumer.accept(pending(player, id), mail.coin());
            }
        }));
    }
    public UUID deliver(MoneySavedData data, String key, MailMessage message, Collection<UUID> recipients) {
        return deliver(data, key, message, recipients, null);
    }
    /** Market funds already exist; move their custody instead of issuing new coin. */
    public UUID deliverMarket(MoneySavedData data, String key, MailMessage message, UUID recipient, String source) {
        return deliver(data, key, message, List.of(recipient), source);
    }
    private UUID deliver(MoneySavedData data, String key, MailMessage message, Collection<UUID> recipients, String source) {
        UUID existing = sends.get(key);
        if (existing != null) return existing;
        Set<UUID> unique = Set.copyOf(recipients);
        if (message.coin() < 0 || unique.isEmpty() || messages.containsKey(message.id()))
            throw new IllegalArgumentException("Invalid mail delivery");
        data.requireAuditCapacity(message.coin() == 0 ? 0 : unique.size());
        for (UUID player : unique) if (message.coin() > 0)
            data.record(source == null ? "mail_issue" : "market_mail", MoneyCurrency.COIN,
                    source == null ? "system:mint" : source, pending(player, message.id()),
                    message.coin(), message.id().toString());
        messages.put(message.id(), message);
        for (UUID player : unique) inboxes.computeIfAbsent(player, ignored -> new HashMap<>()).put(message.id(), Receipt.NEW);
        sends.put(key, message.id()); revision++; data.markChanged();
        return message.id();
    }
    public boolean markRead(MoneySavedData data, UUID player, UUID mail) {
        Receipt r = receipt(player, mail);
        if (detail(player, mail) == null) return false;
        if (!messages.get(mail).items().isEmpty() && !r.claimed) return true;
        if (!r.read) { inboxes.get(player).put(mail, new Receipt(true, r.claimed, false)); revision++; data.markChanged(); }
        return true;
    }
    public Result claim(MoneySavedData data, UUID player, UUID id) {
        return claim(data, player, id, null);
    }
    public Result claim(MoneySavedData data, UUID player, UUID id, Inventory inventory) {
        MailMessage mail = detail(player, id);
        if (mail == null) return Result.NOT_FOUND;
        Receipt r = receipt(player, id);
        if (r.claimed || !mail.hasAttachments()) return Result.ALREADY_CLAIMED;
        MoneyBalance balance = data.balance(player);
        if (Long.MAX_VALUE - balance.coinMinorUnits() < mail.coin()) return Result.WALLET_FULL;
        data.requireAuditCapacity(mail.coin() > 0 ? 1 : 0);
        List<MailItem> remaining = new ArrayList<>();
        boolean moved = mail.coin() > 0;
        for (MailItem item : mail.items()) {
            int rest = inventory == null ? item.quantity() : insert(inventory, item.item(), item.quantity());
            if (rest < item.quantity()) moved = true;
            if (rest > 0) remaining.add(new MailItem(item.item(), rest));
        }
        if (!moved) return Result.NO_ROOM;
        if (mail.coin() > 0) {
            data.record("mail_claim", MoneyCurrency.COIN, pending(player, id), MoneyService.wallet(player), mail.coin(), id.toString());
            data.put(player, balance.with(MoneyCurrency.COIN, balance.coinMinorUnits() + mail.coin()));
        }
        boolean complete = remaining.isEmpty();
        if (complete) {
            Map<UUID, Attachments> account = pendingItems.get(player);
            if (account != null) account.remove(id);
        } else pendingItems.computeIfAbsent(player, ignored -> new HashMap<>()).put(id, new Attachments(0, remaining));
        boolean read = complete || messages.get(id).items().isEmpty();
        inboxes.get(player).put(id, new Receipt(read, complete, false)); revision++; data.markChanged();
        if (inventory != null) inventory.setChanged();
        return complete ? Result.SUCCESS : Result.PARTIAL;
    }
    /** Never drops items; armor and offhand are deliberately excluded. */
    private static int insert(Inventory inventory, ItemStack item, int quantity) {
        int remaining = quantity;
        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!current.isEmpty() && io.github.luyingyilong.geg.market.MarketKey.of(current)
                    .equals(io.github.luyingyilong.geg.market.MarketKey.of(item))) {
                int room = Math.max(0, Math.min(current.getMaxStackSize(), inventory.getMaxStackSize()) - current.getCount());
                int taken = Math.min(room, remaining);
                current.grow(taken); remaining -= taken;
            }
        }
        for (int slot = 0; slot < 36 && remaining > 0; slot++) if (inventory.getItem(slot).isEmpty()) {
            int taken = Math.min(remaining, Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()));
            inventory.setItem(slot, item.copyWithCount(taken)); remaining -= taken;
        }
        return remaining;
    }
    public Result delete(MoneySavedData data, UUID player, UUID id) {
        MailMessage mail = detail(player, id);
        if (mail == null) return Result.NOT_FOUND;
        Receipt r = receipt(player, id);
        if (mail.hasAttachments() && !r.claimed) return Result.UNCLAIMED;
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
        ListTag pending = new ListTag();
        pendingItems.forEach((player, entries) -> entries.forEach((id, value) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("player", player); tag.putUUID("mail", id);
            tag.putLong("coin", value.coin);
            ListTag items = new ListTag(); value.items.forEach(item -> items.add(item.save())); tag.put("items", items);
            pending.add(tag);
        })); root.put("pendingAttachments", pending);
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
        boolean corrected = false;
        for (Map<UUID, Receipt> entries : state.inboxes.values()) {
            for (var entry : entries.entrySet()) {
                Receipt receipt = entry.getValue();
                if (receipt.read && !receipt.claimed && !receipt.deleted && !state.messages.get(entry.getKey()).items().isEmpty()) {
                    entry.setValue(new Receipt(false, false, false)); corrected = true;
                }
            }
        }
        if (corrected) state.revision++;
        for (Tag raw : root.getList("sends", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw; state.sends.put(tag.getString("key"), tag.getUUID("mail"));
        }
        for (Tag raw : root.getList("pendingAttachments", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            Receipt receipt = state.receipt(tag.getUUID("player"), tag.getUUID("mail"));
            if (receipt != null && !receipt.claimed && !receipt.deleted)
                state.pendingItems.computeIfAbsent(tag.getUUID("player"), ignored -> new HashMap<>())
                        .put(tag.getUUID("mail"), new Attachments(tag.getLong("coin"),
                                tag.getList("items", Tag.TAG_COMPOUND).stream().map(item -> MailItem.load((CompoundTag) item)).toList()));
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
