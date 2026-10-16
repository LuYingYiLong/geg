package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.organization.Organization;
import java.util.*;

/** Pure authorization and recipient resolution, shared by preview and final confirmation. */
public final class MailPolicy {
    public static final class Rejected extends RuntimeException {
        Rejected(String key) { super("mail.geg." + key); }
    }
    public record Targets(List<UUID> players, String organizationName) { }
    private MailPolicy() { }
    public static boolean validText(String text, int limit, boolean multiline) {
        return text != null && !text.isBlank() && text.codePointCount(0, text.length()) <= limit
                && text.codePoints().noneMatch(c -> Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\t')));
    }
    public static boolean mayAnnounce(Organization org, UUID actor) {
        return org != null && (org.roleOf(actor) == Organization.Role.LEADER || org.roleOf(actor) == Organization.Role.OFFICER);
    }
    public static Targets resolve(UUID actor, boolean admin, Collection<Organization> organizations,
            Map<UUID, String> known, MailActionPacket request, long now, long lastAnnouncement) {
        if (!validText(request.title(), 64, false) || !validText(request.body(), 2048, true)) throw new Rejected("invalid_text");
        if (request.coin() < 0) throw new Rejected("invalid_coin");
        Set<UUID> recipients = new TreeSet<>();
        Organization org = organizations.stream().filter(entry -> entry.id().equals(request.organization())).findFirst().orElse(null);
        if (request.organizationMail()) {
            if (!mayAnnounce(org, actor) || request.scope() != MailActionPacket.Scope.ORGANIZATION || request.coin() != 0)
                throw new Rejected("forbidden");
            if (now - lastAnnouncement < 30_000) throw new Rejected("cooldown");
            recipients.addAll(org.members());
        } else {
            if (!admin) throw new Rejected("forbidden");
            switch (request.scope()) {
                case ALL -> recipients.addAll(known.keySet());
                case PLAYER -> {
                    UUID id = null;
                    try { id = UUID.fromString(request.target().strip()); } catch (IllegalArgumentException ignored) { }
                    if (id == null) id = known.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(request.target().strip()))
                            .map(Map.Entry::getKey).findFirst().orElse(null);
                    if (id == null || !known.containsKey(id)) throw new Rejected("invalid_target");
                    recipients.add(id);
                }
                case ORGANIZATION -> {
                    if (org == null) org = organizations.stream().filter(entry -> entry.name().equalsIgnoreCase(request.target().strip())
                            || entry.id().toString().equals(request.target().strip())).findFirst().orElse(null);
                    if (org == null) throw new Rejected("invalid_target");
                    recipients.addAll(org.members());
                }
            }
        }
        if (recipients.isEmpty()) throw new Rejected("no_recipients");
        return new Targets(List.copyOf(recipients), org == null ? "" : org.name());
    }
}
