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

    private final Map<UUID, Organization> organizations = new HashMap<>();

    public enum CreateResult { CREATED, INVALID_NAME, INVALID_DESCRIPTION, DUPLICATE_NAME, TOO_MANY }
    public enum Operation { DISBAND, PROMOTE, DEMOTE, REMOVE, LEAVE }
    public enum OperationResult { SUCCESS, NOT_FOUND, NOT_MEMBER, FORBIDDEN, INVALID_TARGET }

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
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        organizations.values().stream().sorted(Comparator.comparing(Organization::createdAt)
                .thenComparing(Organization::id)).forEach(organization -> list.add(organization.save()));
        root.put("organizations", list);
        return root;
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

    public OperationResult operate(UUID actor, UUID organizationId, UUID target, Operation operation) {
        Organization organization = organizations.get(organizationId);
        if (organization == null) return OperationResult.NOT_FOUND;
        Organization.Role actorRole = organization.roleOf(actor);
        if (actorRole == null) return OperationResult.NOT_MEMBER;
        Organization.Role targetRole = organization.roleOf(target);
        if (operation == Operation.DISBAND) {
            if (actorRole != Organization.Role.LEADER || !actor.equals(target))
                return OperationResult.FORBIDDEN;
            organizations.remove(organizationId);
        } else if (operation == Operation.LEAVE) {
            if (!actor.equals(target) || actorRole == Organization.Role.LEADER)
                return OperationResult.FORBIDDEN;
            organizations.put(organizationId, organization.withoutMember(actor));
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
            }
        }
        setDirty();
        return OperationResult.SUCCESS;
    }

    public CreateResult create(UUID founder, String rawName, String rawDescription,
                               Organization.JoinPolicy policy) {
        if (rawName == null || !safeText(rawName)) return CreateResult.INVALID_NAME;
        if (rawDescription == null || !safeText(rawDescription)) return CreateResult.INVALID_DESCRIPTION;
        String name = rawName.strip();
        String description = rawDescription.strip();
        if (!validName(name)) return CreateResult.INVALID_NAME;
        if (!validDescription(description)) return CreateResult.INVALID_DESCRIPTION;
        if (memberships(founder).size() >= MAX_MEMBERSHIPS) return CreateResult.TOO_MANY;
        String key = name.toLowerCase(Locale.ROOT);
        for (Organization organization : organizations.values())
            if (organization.name().toLowerCase(Locale.ROOT).equals(key))
                return CreateResult.DUPLICATE_NAME;
        Organization organization = Organization.create(name, description, founder, policy);
        organizations.put(organization.id(), organization);
        setDirty();
        return CreateResult.CREATED;
    }

    public static boolean validName(String name) {
        return name != null && !name.isBlank() && name.equals(name.strip())
                && name.codePointCount(0, name.length()) <= 48 && safeText(name);
    }

    public static boolean validDescription(String description) {
        return description != null && description.codePointCount(0, description.length()) <= 120
                && safeText(description);
    }

    private static boolean safeText(String text) {
        return text.codePoints().noneMatch(c -> Character.isISOControl(c) || c == '\u00a7');
    }
}
