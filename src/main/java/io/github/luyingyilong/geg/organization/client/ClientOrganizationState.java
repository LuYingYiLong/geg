package io.github.luyingyilong.geg.organization.client;

import java.util.List;

/** Client-side membership snapshot. Organization persistence and sync are a later feature. */
final class ClientOrganizationState {
    private static List<String> organizations = List.of();

    private ClientOrganizationState() {
    }

    static boolean hasOrganizations() {
        return !organizations.isEmpty();
    }

    static void acceptOrganizations(List<String> ids) {
        organizations = List.copyOf(ids);
    }
}
