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
        testApplications();
        testCancelApplications();
        testLegacyMembers();
    }

    private static void testCancelApplications() {
        UUID leader = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Organization organization = new Organization(UUID.randomUUID(), "Cancel requests", "", leader,
                Organization.JoinPolicy.APPROVAL, 1L, Map.of(leader, Organization.Role.LEADER));
        UUID id = organization.id();
        OrganizationSavedData data = seeded(organization);
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(other, id, other, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        for (UUID actor : new UUID[]{outsider, leader, other}) {
            check(data.operate(actor, id, applicant, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                    == OrganizationSavedData.OperationResult.FORBIDDEN);
            check(data.hasApplication(id, applicant) && data.hasApplication(id, other));
        }
        data.setDirty(false);
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.isDirty());
        check(!data.hasApplication(id, applicant) && data.hasApplication(id, other));
        check(data.organization(id).roleOf(applicant) == null);
        data = OrganizationSavedData.load(data.save(new CompoundTag()));
        check(!data.hasApplication(id, applicant) && data.hasApplication(id, other));
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                == OrganizationSavedData.OperationResult.NO_APPLICATION);
        // Cancellation wins: a later stale approval cannot add the withdrawn applicant.
        check(data.operate(leader, id, applicant, OrganizationSavedData.Operation.APPROVE)
                == OrganizationSavedData.OperationResult.NO_APPLICATION);
        check(data.organization(id).roleOf(applicant) == null);
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        // Approval wins: cancellation must not remove a player who has already joined.
        check(data.operate(leader, id, applicant, OrganizationSavedData.Operation.APPROVE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                == OrganizationSavedData.OperationResult.NO_APPLICATION);
        check(data.organization(id).roleOf(applicant) == Organization.Role.MEMBER);
        check(data.operate(other, id, other, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.applications(id).isEmpty());
        check(OrganizationSavedData.load(data.save(new CompoundTag())).applications(id).isEmpty());
        check(data.operate(other, UUID.randomUUID(), other, OrganizationSavedData.Operation.CANCEL_APPLICATION)
                == OrganizationSavedData.OperationResult.NOT_FOUND);
    }

    private static void testApplications() {
        UUID leader = UUID.randomUUID();
        UUID officer = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID applicant = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        Organization organization = new Organization(UUID.randomUUID(), "Applicants", "", leader,
                Organization.JoinPolicy.APPROVAL, 1L, Map.of(leader, Organization.Role.LEADER,
                officer, Organization.Role.OFFICER, member, Organization.Role.MEMBER));
        OrganizationSavedData data = seeded(organization);
        UUID id = organization.id();
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(applicant, id, applicant, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.ALREADY_APPLIED);
        check(data.operate(applicant, id, rejected, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.organization(id).roleOf(applicant) == null);
        data = OrganizationSavedData.load(data.save(new CompoundTag()));
        check(data.hasApplication(id, applicant));
        check(data.operate(member, id, applicant, OrganizationSavedData.Operation.APPROVE)
                == OrganizationSavedData.OperationResult.FORBIDDEN);
        check(data.operate(officer, id, applicant, OrganizationSavedData.Operation.APPROVE)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.organization(id).roleOf(applicant) == Organization.Role.MEMBER);
        check(!data.hasApplication(id, applicant));
        check(data.operate(leader, id, applicant, OrganizationSavedData.Operation.APPROVE)
                == OrganizationSavedData.OperationResult.NO_APPLICATION);
        check(data.operate(rejected, id, rejected, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(leader, id, rejected, OrganizationSavedData.Operation.REJECT)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.organization(id).roleOf(rejected) == null && !data.hasApplication(id, rejected));
        check(data.operate(rejected, id, rejected, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.operate(leader, id, leader, OrganizationSavedData.Operation.DISBAND)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(data.applications(id).isEmpty());

        Organization open = new Organization(UUID.randomUUID(), "Open", "", leader,
                Organization.JoinPolicy.UNRESTRICTED, 2L, Map.of(leader, Organization.Role.LEADER));
        OrganizationSavedData openData = seeded(open);
        check(openData.operate(applicant, open.id(), applicant, OrganizationSavedData.Operation.APPLY)
                == OrganizationSavedData.OperationResult.SUCCESS);
        check(openData.organization(open.id()).roleOf(applicant) == Organization.Role.MEMBER);
        check(openData.applications(open.id()).isEmpty());
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
