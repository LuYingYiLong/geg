package io.github.luyingyilong.geg.chat;

import io.github.luyingyilong.geg.organization.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** Retention, cursor boundaries, membership epochs, and save/reload without a running server. */
public final class ChatSavedDataTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), outsider = UUID.randomUUID();
        ChatSavedData data = new ChatSavedData(); ChatChannel direct = ChatChannel.direct(a, b);
        check(direct.equals(ChatChannel.direct(b, a)) && direct.includes(a) && !direct.includes(outsider));
        data.remember(a, "Alice"); data.remember(b, "Bob");
        data.initializeWorld(b);
        UUID first = UUID.randomUUID();
        var message = data.append(a, first, direct, "Alice", Component.literal("one"), 1);
        check(!message.duplicate() && data.append(a, first, direct, "Alice", Component.literal("ignored"), 2).duplicate());
        check(data.history(direct, 1, 0).size() == 1 && data.unread(b, direct, 1) == 1);
        for (int i = 2; i <= 101; i++) data.append(a, UUID.randomUUID(), direct, "Alice", Component.literal("line " + i), i);
        List<ChatMessage> last = data.history(direct, 1, 0);
        check(last.size() == 50 && last.get(0).sequence() == 52 && last.get(49).sequence() == 101);
        List<ChatMessage> older = data.history(direct, 1, last.get(0).sequence());
        check(older.size() == 50 && older.get(0).sequence() == 2 && older.get(49).sequence() == 51);
        check(!data.hasBefore(direct, 1, 2) && data.unread(b, direct, 1) == 100);
        data.markRead(b, direct, Long.MAX_VALUE);
        check(data.unread(b, direct, 1) == 0);
        data.markRead(b, direct, 0); check(data.unread(b, direct, 1) == 0);
        ChatSavedData restored = ChatSavedData.load(data.save(new CompoundTag()));
        check(restored.players().get(b).equals("Bob") && restored.unread(b, direct, 1) == 0);
        check(restored.history(direct, 1, 0).equals(last) && restored.directChannels(a).equals(List.of(direct)));
        check(restored.directChannels(outsider).isEmpty());
        check(restored.duplicate(a, first));
        check(restored.append(a, first, direct, "Alice", Component.literal("old retry"), 102).duplicate());
        check(restored.latest(direct) == 101);

        OrganizationSavedData organizations = new OrganizationSavedData();
        check(organizations.create(a, "Builders", "", Organization.JoinPolicy.UNRESTRICTED) == OrganizationSavedData.CreateResult.CREATED);
        UUID org = organizations.memberships(a).get(0).id(); ChatChannel channel = ChatChannel.organization(org);
        UUID ownerEpoch = organizations.chatEpoch(org, a);
        data.scope(org, a, ownerEpoch);
        data.append(a, UUID.randomUUID(), channel, "Alice", Component.literal("before joining"), 200);
        check(organizations.operate(b, org, b, OrganizationSavedData.Operation.APPLY) == OrganizationSavedData.OperationResult.SUCCESS);
        UUID epoch = organizations.chatEpoch(org, b);
        long start = data.scope(org, b, epoch).firstSequence();
        check(data.history(channel, start, 0).isEmpty());
        data.append(a, UUID.randomUUID(), channel, "Alice", Component.literal("during membership"), 201);
        check(data.history(channel, start, 0).size() == 1);
        check(organizations.operate(b, org, b, OrganizationSavedData.Operation.LEAVE) == OrganizationSavedData.OperationResult.SUCCESS);
        check(organizations.chatEpoch(org, b) == null);
        organizations.operate(b, org, b, OrganizationSavedData.Operation.APPLY);
        UUID secondEpoch = organizations.chatEpoch(org, b);
        check(!epoch.equals(secondEpoch) && data.history(channel, data.scope(org, b, secondEpoch).firstSequence(), 0).isEmpty());
        check(OrganizationSavedData.load(organizations.save(new CompoundTag())).chatEpoch(org, b).equals(secondEpoch));
        ChatSavedData scopes = ChatSavedData.load(data.save(new CompoundTag()));
        check(scopes.scope(org, b, secondEpoch).firstSequence() == data.scope(org, b, secondEpoch).firstSequence());
        check(organizations.chatEpoch(org, outsider) == null);
    }
    private static void check(boolean condition) { if (!condition) throw new AssertionError("Chat persistence or membership regression"); }
}
