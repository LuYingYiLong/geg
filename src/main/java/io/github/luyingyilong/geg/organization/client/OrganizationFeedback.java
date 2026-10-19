package io.github.luyingyilong.geg.organization.client;

import io.github.luyingyilong.ore.Toast;
import io.github.luyingyilong.ore.util.OreToasts;
import net.minecraft.network.chat.Component;

/** Shared presentation for server results and local organization form validation. */
final class OrganizationFeedback {
    private OrganizationFeedback() { }

    static void show(String key) {
        if (key.isEmpty()) return;
        Toast.Type type = switch (key) {
            case "organization.geg.created", "organization.geg.updated",
                    "organization.geg.result.disband", "organization.geg.result.promote",
                    "organization.geg.result.demote", "organization.geg.result.remove",
                    "organization.geg.result.leave", "organization.geg.result.apply",
                    "organization.geg.result.application_sent", "organization.geg.result.cancel_application",
                    "organization.geg.result.approve", "organization.geg.result.reject",
                    "organization.geg.result.approved_notice" -> Toast.Type.PRIMARY;
            default -> Toast.Type.WARNING;
        };
        OreToasts.show("geg:organization:feedback", new Toast(Component.translatable(key)).setType(type));
    }
}
