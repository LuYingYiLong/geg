package io.github.luyingyilong.geg.mail;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** Human mail is literal text; only trusted server events may contain translation keys. */
public record MailMessage(UUID id, long sentAt, Kind kind, String sender, String title,
                          String body, List<String> arguments, long coin, MailActionPacket.Scope scope,
                          UUID authorId, String authorName, List<MailItem> items) {
    public enum Kind { ADMIN, ORGANIZATION, SYSTEM }
    public MailMessage {
        arguments = List.copyOf(arguments);
        items = List.copyOf(items);
        authorName = java.util.Objects.requireNonNullElse(authorName, "");
    }
    public MailMessage(UUID id, long sentAt, Kind kind, String sender, String title,
                       String body, List<String> arguments, long coin, MailActionPacket.Scope scope,
                       UUID authorId, String authorName) {
        this(id, sentAt, kind, sender, title, body, arguments, coin, scope, authorId, authorName, List.of());
    }
    public boolean hasAttachments() { return coin > 0 || !items.isEmpty(); }
    public MailMessage attachments(long remainingCoin, List<MailItem> remainingItems) {
        return new MailMessage(id, sentAt, kind, sender, title, body, arguments, remainingCoin,
                scope, authorId, authorName, remainingItems);
    }
    public MailMessage(UUID id, long sentAt, Kind kind, String sender, String title,
                       String body, List<String> arguments, long coin, MailActionPacket.Scope scope) {
        this(id, sentAt, kind, sender, title, body, arguments, coin, scope, null, kind == Kind.ADMIN ? sender : "");
    }
    public MailMessage(UUID id, long sentAt, Kind kind, String sender, String title,
                       String body, List<String> arguments, long coin) {
        this(id, sentAt, kind, sender, title, body, arguments, coin, legacyScope(kind));
    }
    /** Old admin mail did not persist its audience; keep it in the server-wide category. */
    public static MailActionPacket.Scope legacyScope(Kind kind) {
        return kind == Kind.ADMIN ? MailActionPacket.Scope.ALL : MailActionPacket.Scope.ORGANIZATION;
    }
    public Component titleText() { return kind == Kind.SYSTEM ? Component.translatable(title) : Component.literal(title); }
    public Component bodyText() { return kind == Kind.SYSTEM
            ? Component.translatable(body, arguments.stream().map(Component::literal).toArray()) : Component.literal(body); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putLong("sentAt", sentAt); tag.putString("kind", kind.name());
        tag.putString("scope", scope.name());
        tag.putString("sender", sender); tag.putString("title", title); tag.putString("body", body); tag.putLong("coin", coin);
        if (authorId != null) tag.putUUID("authorId", authorId);
        if (!authorName.isEmpty()) tag.putString("authorName", authorName);
        ListTag args = new ListTag(); arguments.forEach(value -> args.add(StringTag.valueOf(value))); tag.put("args", args);
        ListTag attachments = new ListTag(); items.forEach(value -> attachments.add(value.save())); tag.put("items", attachments);
        return tag;
    }
    public static MailMessage load(CompoundTag tag) {
        List<String> args = new java.util.ArrayList<>();
        tag.getList("args", Tag.TAG_STRING).forEach(value -> args.add(value.getAsString()));
        Kind kind = Kind.valueOf(tag.getString("kind"));
        return new MailMessage(tag.getUUID("id"), tag.getLong("sentAt"), kind,
                tag.getString("sender"), tag.getString("title"), tag.getString("body"), args, tag.getLong("coin"),
                tag.contains("scope", Tag.TAG_STRING) ? MailActionPacket.Scope.valueOf(tag.getString("scope")) : legacyScope(kind),
                tag.hasUUID("authorId") ? tag.getUUID("authorId") : null,
                tag.contains("authorName", Tag.TAG_STRING) ? tag.getString("authorName")
                        : kind == Kind.ADMIN ? tag.getString("sender") : "",
                tag.getList("items", Tag.TAG_COMPOUND).stream().map(raw -> MailItem.load((CompoundTag) raw)).toList());
    }
}
