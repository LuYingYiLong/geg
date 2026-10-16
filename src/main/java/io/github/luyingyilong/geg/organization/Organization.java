package io.github.luyingyilong.geg.organization;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public record Organization(UUID id, String name, String description, UUID founder,
                           JoinPolicy joinPolicy, long createdAt, Map<UUID, Role> roles) {
    public enum JoinPolicy { UNRESTRICTED, APPROVAL }
    public enum Role { LEADER, OFFICER, MEMBER }

    public Organization {
        roles = Map.copyOf(roles);
        if (roles.get(founder) != Role.LEADER
                || roles.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(founder)
                        && entry.getValue() == Role.LEADER))
            throw new IllegalArgumentException("Only the founder may be the leader");
    }

    public static Organization create(String name, String description, UUID founder, JoinPolicy policy) {
        return new Organization(UUID.randomUUID(), name, description, founder, policy,
                System.currentTimeMillis(), Map.of(founder, Role.LEADER));
    }

    public Set<UUID> members() { return roles.keySet(); }
    public Role roleOf(UUID member) { return roles.get(member); }

    public Organization withRole(UUID member, Role role) {
        if (member.equals(founder) || !roles.containsKey(member) || role == Role.LEADER)
            throw new IllegalArgumentException("Invalid role change");
        Map<UUID, Role> updated = new HashMap<>(roles);
        updated.put(member, role);
        return new Organization(id, name, description, founder, joinPolicy, createdAt, updated);
    }

    public Organization withoutMember(UUID member) {
        if (member.equals(founder) || !roles.containsKey(member))
            throw new IllegalArgumentException("Invalid member removal");
        Map<UUID, Role> updated = new HashMap<>(roles);
        updated.remove(member);
        return new Organization(id, name, description, founder, joinPolicy, createdAt, updated);
    }

    public Organization withMember(UUID member) {
        if (roles.containsKey(member)) throw new IllegalArgumentException("Already a member");
        Map<UUID, Role> updated = new HashMap<>(roles);
        updated.put(member, Role.MEMBER);
        return new Organization(id, name, description, founder, joinPolicy, createdAt, updated);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("description", description);
        tag.putUUID("founder", founder);
        tag.putString("joinPolicy", joinPolicy.name());
        tag.putLong("createdAt", createdAt);
        ListTag memberTags = new ListTag();
        ListTag roleTags = new ListTag();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            memberTags.add(StringTag.valueOf(entry.getKey().toString()));
            CompoundTag roleTag = new CompoundTag();
            roleTag.putUUID("member", entry.getKey());
            roleTag.putString("role", entry.getValue().name());
            roleTags.add(roleTag);
        }
        tag.put("members", memberTags);
        tag.put("roles", roleTags);
        return tag;
    }

    public static Organization load(CompoundTag tag) {
        if (!tag.hasUUID("id") || !tag.hasUUID("founder")) return null;
        UUID founder = tag.getUUID("founder");
        Map<UUID, Role> roles = new HashMap<>();
        for (Tag raw : tag.getList("roles", Tag.TAG_COMPOUND)) {
            CompoundTag memberTag = (CompoundTag) raw;
            if (!memberTag.hasUUID("member")) continue;
            UUID member = memberTag.getUUID("member");
            try {
                Role role = Role.valueOf(memberTag.getString("role"));
                roles.put(member, member.equals(founder) ? Role.LEADER
                        : role == Role.LEADER ? Role.MEMBER : role);
            } catch (IllegalArgumentException ignored) {
                roles.put(member, Role.MEMBER);
            }
        }
        if (roles.isEmpty()) {
            for (Tag memberTag : tag.getList("members", Tag.TAG_STRING)) {
                try {
                    roles.put(UUID.fromString(memberTag.getAsString()), Role.MEMBER);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed legacy member IDs.
                }
            }
        }
        roles.put(founder, Role.LEADER);
        JoinPolicy policy;
        try {
            policy = JoinPolicy.valueOf(tag.getString("joinPolicy"));
        } catch (IllegalArgumentException ignored) {
            policy = JoinPolicy.UNRESTRICTED;
        }
        return new Organization(tag.getUUID("id"), tag.getString("name"),
                tag.getString("description"), founder, policy, tag.getLong("createdAt"), roles);
    }
}
