package io.github.luyingyilong.geg.organization;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One server-wide organization store, shared by all dimensions. */
public final class OrganizationSavedData extends SavedData {
    private static final String DATA_NAME = "geg_organizations";
    private static final int MAX_MEMBERSHIPS = 128;
    private static final int MAX_APPLICATIONS = 256;

    private final Map<UUID, Organization> organizations = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> applications = new HashMap<>();
    private final Map<UUID, Map<UUID, UUID>> chatMemberships = new HashMap<>();

    /** A fresh epoch on every admission prevents history access across separate memberships. */
    public UUID chatEpoch(UUID organizationId, UUID player) {
        Organization organization = organizations.get(organizationId);
        if (organization == null || organization.roleOf(player) == null) return null;
        Map<UUID, UUID> members = chatMemberships.computeIfAbsent(organizationId, ignored -> new HashMap<>());
        UUID epoch = members.get(player);
        if (epoch == null) { epoch = UUID.randomUUID(); members.put(player, epoch); setDirty(); }
        return epoch;
    }
    private void beginChatMembership(UUID organization, UUID player) {
        chatMemberships.computeIfAbsent(organization, ignored -> new HashMap<>()).put(player, UUID.randomUUID());
    }
    private void endChatMembership(UUID organization, UUID player) {
        Map<UUID, UUID> members = chatMemberships.get(organization);
        if (members != null) members.remove(player);
    }

    public record MailEvent(UUID id, UUID recipient, String organization, String action) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("recipient", recipient);
            tag.putString("organization", organization); tag.putString("action", action); return tag;
        }
    }
    // Retained for replay after independent save-file failures; mail delivery deduplicates by event ID.
    private final List<MailEvent> mailEvents = new ArrayList<>();
    public List<MailEvent> mailEvents() { return List.copyOf(mailEvents); }
    private void notifyMail(Organization organization, UUID recipient, Operation operation) {
        mailEvents.add(new MailEvent(UUID.randomUUID(), recipient, organization.name(), operation.name().toLowerCase(Locale.ROOT)));
    }

    public enum CreateResult { CREATED, INVALID_NAME, INVALID_DESCRIPTION, DUPLICATE_NAME, TOO_MANY }
    public enum UpdateResult { UPDATED, NOT_FOUND, NOT_MEMBER, FORBIDDEN, INVALID_DESCRIPTION }
    public enum Operation { DISBAND, PROMOTE, DEMOTE, REMOVE, LEAVE, APPLY, APPROVE, REJECT, CANCEL_APPLICATION }
    public enum OperationResult {
        SUCCESS, NOT_FOUND, NOT_MEMBER, FORBIDDEN, INVALID_TARGET,
        ALREADY_MEMBER, ALREADY_APPLIED, TOO_MANY, NO_APPLICATION
    }
    public record Application(UUID applicant, long appliedAt) { }

    public static OrganizationSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                OrganizationSavedData::load, OrganizationSavedData::new, DATA_NAME);
    }

    public static OrganizationSavedData load(CompoundTag root) {
        OrganizationSavedData data = new OrganizationSavedData();
        for (Tag raw : root.getList("organizations", Tag.TAG_COMPOUND)) {
            Organization organization = Organization.load((CompoundTag) raw);
            if (organization != null && validName(organization.name())
                    && validDescription(organization.description())) {
                data.organizations.put(organization.id(), organization);
            }
        }
        for (Tag raw : root.getList("applications", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("organization") || !tag.hasUUID("applicant")) continue;
            UUID organizationId = tag.getUUID("organization");
            UUID applicant = tag.getUUID("applicant");
            Organization organization = data.organizations.get(organizationId);
            if (organization == null || organization.roleOf(applicant) != null) continue;
            Map<UUID, Long> pending = data.applications.computeIfAbsent(
                    organizationId, ignored -> new HashMap<>());
            if (pending.size() < MAX_APPLICATIONS)
                pending.putIfAbsent(applicant, tag.getLong("appliedAt"));
        }
        for (Tag raw : root.getList("mailEvents", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.hasUUID("id") && tag.hasUUID("recipient")) data.mailEvents.add(new MailEvent(
                    tag.getUUID("id"), tag.getUUID("recipient"), tag.getString("organization"), tag.getString("action")));
        }
        for (Tag raw : root.getList("chatMemberships", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("organization") || !tag.hasUUID("player") || !tag.hasUUID("epoch")) continue;
            Organization organization = data.organizations.get(tag.getUUID("organization"));
            if (organization != null && organization.roleOf(tag.getUUID("player")) != null)
                data.chatMemberships.computeIfAbsent(organization.id(), ignored -> new HashMap<>())
                        .put(tag.getUUID("player"), tag.getUUID("epoch"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        organizations.values().stream().sorted(Comparator.comparing(Organization::createdAt)
                .thenComparing(Organization::id)).forEach(organization -> list.add(organization.save()));
        root.put("organizations", list);
        ListTag pending = new ListTag();
        applications.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).forEach(application -> {
                            CompoundTag tag = new CompoundTag();
                            tag.putUUID("organization", entry.getKey());
                            tag.putUUID("applicant", application.getKey());
                            tag.putLong("appliedAt", application.getValue());
                            pending.add(tag);
                        }));
        root.put("applications", pending);
        ListTag events = new ListTag(); mailEvents.forEach(event -> events.add(event.save())); root.put("mailEvents", events);
        ListTag epochs = new ListTag();
        chatMemberships.forEach((organization, members) -> members.forEach((player, epoch) -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("organization", organization);
            tag.putUUID("player", player); tag.putUUID("epoch", epoch); epochs.add(tag);
        }));
        root.put("chatMemberships", epochs);
        return root;
    }

    public List<Organization> directory() {
        return organizations.values().stream().sorted(Comparator.comparing(Organization::name,
                String.CASE_INSENSITIVE_ORDER).thenComparing(Organization::id)).toList();
    }

    public List<Application> applications(UUID organizationId) {
        return applications.getOrDefault(organizationId, Map.of()).entrySet().stream()
                .map(entry -> new Application(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(Application::appliedAt)
                        .thenComparing(Application::applicant)).toList();
    }

    public boolean hasApplication(UUID organizationId, UUID applicant) {
        return applications.getOrDefault(organizationId, Map.of()).containsKey(applicant);
    }

    public List<Organization> memberships(UUID player) {
        List<Organization> result = new ArrayList<>();
        for (Organization organization : organizations.values())
            if (organization.members().contains(player)) result.add(organization);
        result.sort(Comparator.comparing(Organization::createdAt).thenComparing(Organization::id));
        return result;
    }

    public Organization organization(UUID id) {
        return organizations.get(id);
    }

    /** Profile updates intentionally have no organization name parameter. */
    public UpdateResult update(UUID actor, UUID organizationId, String rawDescription,
                               Organization.JoinPolicy policy) {
        Organization organization = organizations.get(organizationId);
        if (organization == null) return UpdateResult.NOT_FOUND;
        Organization.Role role = organization.roleOf(actor);
        if (role == null) return UpdateResult.NOT_MEMBER;
        if (role == Organization.Role.MEMBER) return UpdateResult.FORBIDDEN;
        if (!validDescription(rawDescription) || policy == null) return UpdateResult.INVALID_DESCRIPTION;
        organizations.put(organizationId, organization.withProfile(normalizeDescription(rawDescription), policy));
        // Changing admission rules does not discard applications already awaiting review.
        setDirty();
        return UpdateResult.UPDATED;
    }

    public OperationResult operate(UUID actor, UUID organizationId, UUID target, Operation operation) {
        Organization organization = organizations.get(organizationId);
        if (organization == null) return OperationResult.NOT_FOUND;
        if (operation == Operation.CANCEL_APPLICATION) {
            if (!actor.equals(target)) return OperationResult.FORBIDDEN;
            Map<UUID, Long> pending = applications.get(organizationId);
            if (pending == null || !pending.containsKey(actor)) return OperationResult.NO_APPLICATION;
            pending.remove(actor);
            if (pending.isEmpty()) applications.remove(organizationId);
            setDirty();
            return OperationResult.SUCCESS;
        }
        if (operation == Operation.APPLY) {
            if (!actor.equals(target)) return OperationResult.FORBIDDEN;
            if (organization.roleOf(actor) != null) return OperationResult.ALREADY_MEMBER;
            if (memberships(actor).size() >= MAX_MEMBERSHIPS) return OperationResult.TOO_MANY;
            if (organization.joinPolicy() == Organization.JoinPolicy.UNRESTRICTED) {
                organizations.put(organizationId, organization.withMember(actor));
                beginChatMembership(organizationId, actor);
                Map<UUID, Long> pending = applications.get(organizationId);
                if (pending != null) {
                    pending.remove(actor);
                    if (pending.isEmpty()) applications.remove(organizationId);
                }
            } else {
                Map<UUID, Long> pending = applications.computeIfAbsent(
                        organizationId, ignored -> new HashMap<>());
                if (pending.containsKey(actor)) return OperationResult.ALREADY_APPLIED;
                if (pending.size() >= MAX_APPLICATIONS) return OperationResult.TOO_MANY;
                pending.put(actor, System.currentTimeMillis());
            }
            setDirty();
            return OperationResult.SUCCESS;
        }
        Organization.Role actorRole = organization.roleOf(actor);
        if (actorRole == null) return OperationResult.NOT_MEMBER;
        if (operation == Operation.APPROVE || operation == Operation.REJECT) {
            if (actorRole == Organization.Role.MEMBER) return OperationResult.FORBIDDEN;
            Map<UUID, Long> pending = applications.get(organizationId);
            if (pending == null || !pending.containsKey(target)) return OperationResult.NO_APPLICATION;
            if (operation == Operation.APPROVE) {
                if (organization.roleOf(target) != null) return OperationResult.ALREADY_MEMBER;
                if (memberships(target).size() >= MAX_MEMBERSHIPS) return OperationResult.TOO_MANY;
                organizations.put(organizationId, organization.withMember(target));
                beginChatMembership(organizationId, target);
            }
            pending.remove(target);
            if (pending.isEmpty()) applications.remove(organizationId);
            notifyMail(organization, target, operation);
            setDirty();
            return OperationResult.SUCCESS;
        }
        Organization.Role targetRole = organization.roleOf(target);
        if (operation == Operation.DISBAND) {
            if (actorRole != Organization.Role.LEADER || !actor.equals(target))
                return OperationResult.FORBIDDEN;
            organizations.remove(organizationId);
            chatMemberships.remove(organizationId);
            applications.remove(organizationId);
        } else if (operation == Operation.LEAVE) {
            if (!actor.equals(target) || actorRole == Organization.Role.LEADER)
                return OperationResult.FORBIDDEN;
            organizations.put(organizationId, organization.withoutMember(actor));
            endChatMembership(organizationId, actor);
        } else {
            if (targetRole == null || target.equals(actor)) return OperationResult.INVALID_TARGET;
            if (operation == Operation.PROMOTE || operation == Operation.DEMOTE) {
                if (actorRole != Organization.Role.LEADER) return OperationResult.FORBIDDEN;
                if (operation == Operation.PROMOTE && targetRole != Organization.Role.MEMBER
                        || operation == Operation.DEMOTE && targetRole != Organization.Role.OFFICER)
                    return OperationResult.INVALID_TARGET;
                organizations.put(organizationId, organization.withRole(target,
                        operation == Operation.PROMOTE ? Organization.Role.OFFICER
                                : Organization.Role.MEMBER));
            } else if (operation == Operation.REMOVE) {
                if (actorRole == Organization.Role.MEMBER || targetRole == Organization.Role.LEADER
                        || actorRole == Organization.Role.OFFICER
                        && targetRole != Organization.Role.MEMBER)
                    return OperationResult.FORBIDDEN;
                organizations.put(organizationId, organization.withoutMember(target));
                endChatMembership(organizationId, target);
            }
        }
        if (operation == Operation.PROMOTE || operation == Operation.DEMOTE || operation == Operation.REMOVE)
            notifyMail(organization, target, operation);
        setDirty();
        return OperationResult.SUCCESS;
    }

    public CreateResult create(UUID founder, String rawName, String rawDescription,
                               Organization.JoinPolicy policy) {
        if (rawName == null || !safeText(rawName)) return CreateResult.INVALID_NAME;
        if (rawDescription == null || !safeDescription(rawDescription)) return CreateResult.INVALID_DESCRIPTION;
        String name = rawName.strip();
        String description = normalizeDescription(rawDescription);
        if (!validName(name)) return CreateResult.INVALID_NAME;
        if (!validDescription(description)) return CreateResult.INVALID_DESCRIPTION;
        if (memberships(founder).size() >= MAX_MEMBERSHIPS) return CreateResult.TOO_MANY;
        String key = name.toLowerCase(Locale.ROOT);
        for (Organization organization : organizations.values())
            if (organization.name().toLowerCase(Locale.ROOT).equals(key))
                return CreateResult.DUPLICATE_NAME;
        Organization organization = Organization.create(name, description, founder, policy);
        organizations.put(organization.id(), organization);
        beginChatMembership(organization.id(), founder);
        setDirty();
        return CreateResult.CREATED;
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.equals(name.strip())
                && name.codePointCount(0, name.length()) <= 48 && safeText(name);
    }

    public static boolean validDescription(String description) {
        return description != null && description.codePointCount(0, description.length()) <= 120
                && safeDescription(description);
    }

    private static String normalizeDescription(String description) {
        return description.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private static boolean safeDescription(String description) {
        return description.codePoints().noneMatch(c -> c == '\u00a7'
                || (Character.isISOControl(c) && c != '\n' && c != '\r'));
    }

    private static boolean safeText(String text) {
        return text.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '\u00a7');
    }
}
