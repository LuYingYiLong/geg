package io.github.luyingyilong.geg.mail;

import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** Human mail is literal text; only trusted server events may contain translation keys. */
public record MailMessage(UUID id, long sentAt, Kind kind, String sender, String title,
                          String body, List<String> arguments, long coin) {
    public enum Kind { ADMIN, ORGANIZATION, SYSTEM }
    public MailMessage { arguments = List.copyOf(arguments); }
    public Component titleText() { return kind == Kind.SYSTEM ? Component.translatable(title) : Component.literal(title); }
    public Component bodyText() { return kind == Kind.SYSTEM
            ? Component.translatable(body, arguments.stream().map(Component::literal).toArray()) : Component.literal(body); }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putLong("sentAt", sentAt); tag.putString("kind", kind.name());
        tag.putString("sender", sender); tag.putString("title", title); tag.putString("body", body); tag.putLong("coin", coin);
        ListTag args = new ListTag(); arguments.forEach(value -> args.add(StringTag.valueOf(value))); tag.put("args", args);
        return tag;
    }
    public static MailMessage load(CompoundTag tag) {
        List<String> args = new java.util.ArrayList<>();
        tag.getList("args", Tag.TAG_STRING).forEach(value -> args.add(value.getAsString()));
        return new MailMessage(tag.getUUID("id"), tag.getLong("sentAt"), Kind.valueOf(tag.getString("kind")),
                tag.getString("sender"), tag.getString("title"), tag.getString("body"), args, tag.getLong("coin"));
    }
}
