package io.github.luyingyilong.geg.organization;

import java.util.UUID;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Covers create validation, membership, and save/load without a running server. */
public final class OrganizationSavedDataTest {
    private OrganizationSavedDataTest() {
    }

    public static void main(String[] args) {
        OrganizationSavedData data = new OrganizationSavedData();
        UUID founder = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        check(data.create(founder, "  Builders  ", "Public projects",
                Organization.JoinPolicy.APPROVAL) == OrganizationSavedData.CreateResult.CREATED);
        check(data.create(other, "builders", "",
                Organization.JoinPolicy.UNRESTRICTED) == OrganizationSavedData.CreateResult.DUPLICATE_NAME);
        check(data.create(founder, "   ", "",
                Organization.JoinPolicy.UNRESTRICTED) == OrganizationSavedData.CreateResult.INVALID_NAME);
        check(data.create(founder, "Another", "\ninvalid",
                Organization.JoinPolicy.UNRESTRICTED) == OrganizationSavedData.CreateResult.INVALID_DESCRIPTION);
        check(data.create(founder, "Explorers", "",
                Organization.JoinPolicy.UNRESTRICTED) == OrganizationSavedData.CreateResult.CREATED);
        check(data.memberships(founder).size() == 2);
        check(data.memberships(other).isEmpty());

        OrganizationSavedData restored = OrganizationSavedData.load(data.save(new CompoundTag()));
        check(restored.memberships(founder).size() == 2);
        Organization organization = restored.memberships(founder).stream()
                .filter(value -> value.name().equals("Builders")).findFirst().orElseThrow();
        check(organization.name().equals("Builders"));
        check(organization.description().equals("Public projects"));
        check(organization.joinPolicy() == Organization.JoinPolicy.APPROVAL);
        check(organization.founder().equals(founder));
        check(organization.members().contains(founder));
        check(organization.roleOf(founder) == Organization.Role.LEADER);

        testRolePermissions();
        testLegacyMembers();
    }

    private static void testRolePermissions() {
        UUID leader = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Organization organization = new Organization(UUID.randomUUID(), "Town", "", leader,
                Organization.JoinPolicy.APPROVAL, 1L, Map.of(leader, Organization.Role.LEADER,
                officer, Organization.Role.OFFICER, member, Organization.Role.MEMBER));
        OrganizationSavedData data = seeded(organization);
        UUID id = organization.id();
        check(data.operate(outsider, id, member, OrganizationSavedData.Operation.REMOVE)
                == OrganizationSavedData.OperationResult.NOT_MEMBER);
        check(data.operate(officer, id, leader, OrganizationSavedData.Operation.REMOVE)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(officer, id, member, OrganizationSavedData.Operation.PROMOTE)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(member, id, member, OrganizationSavedData.Operation.DISBAND)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(leader, id, member, OrganizationSavedData.Operation.PROMOTE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.organization(id).roleOf(member) == Organization.Role.OFFICER);
        check(data.operate(officer, id, member, OrganizationSavedData.Operation.REMOVE)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(leader, id, member, OrganizationSavedData.Operation.DEMOTE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(officer, id, member, OrganizationSavedData.Operation.REMOVE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.organization(id).roleOf(member) == null);
        check(data.operate(leader, id, officer, OrganizationSavedData.Operation.REMOVE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(leader, id, leader, OrganizationSavedData.Operation.LEAVE)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(leader, id, leader, OrganizationSavedData.Operation.DISBAND)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.organization(id) == null);
        OrganizationSavedData leaveData = seeded(organization);
        check(leaveData.operate(officer, id, officer, OrganizationSavedData.Operation.LEAVE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(leaveData.organization(id).roleOf(officer) == null);
        check(OrganizationSavedData.load(leaveData.save(new CompoundTag()))
                .organization(id).roleOf(officer) == null);
    }

    private static void testLegacyMembers() {
        UUID founder = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Organization organization = new Organization(UUID.randomUUID(), "Legacy", "", founder,
                Organization.JoinPolicy.UNRESTRICTED, 1L,
                Map.of(founder, Organization.Role.LEADER, member, Organization.Role.OFFICER));
        CompoundTag legacy = organization.save();
        legacy.remove("roles");
        Organization loaded = Organization.load(legacy);
        check(loaded != null && loaded.roleOf(founder) == Organization.Role.LEADER);
        check(loaded.roleOf(member) == Organization.Role.MEMBER);
    }

    private static OrganizationSavedData seeded(Organization organization) {
        CompoundTag root = new CompoundTag();
        ListTag organizations = new ListTag();
        organizations.add(organization.save());
        root.put("organizations", organizations);
        return OrganizationSavedData.load(root);
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Organization data invariant failed");
    }
}
