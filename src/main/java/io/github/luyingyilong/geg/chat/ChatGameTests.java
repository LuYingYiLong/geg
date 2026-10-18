package io.github.luyingyilong.geg.chat;

import com.mojang.authlib.GameProfile;
import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.organization.*;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(GegMod.MODID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ChatGameTests {
    @GameTest(template = "empty")
    public static void privacyOfflineAndMembership(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var alice = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chat_alice"));
        var bob = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chat_bob"));
        var other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chat_other"));
        ChatSavedData data = ChatSavedData.get(server); data.remember(bob.getUUID(), "chat_bob");
        var organizations = OrganizationSavedData.get(server);
        organizations.create(alice.getUUID(), "Chat" + UUID.randomUUID().toString().substring(0, 8), "", Organization.JoinPolicy.UNRESTRICTED);
        var org = organizations.memberships(alice.getUUID()).get(0); ChatChannel channel = ChatChannel.organization(org.id());
        int[] publicEvents = {0};
        java.util.function.Consumer<ServerChatEvent> listener = event -> publicEvents[0]++;
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            helper.assertTrue(ChatService.send(alice, channel, UUID.randomUUID(), "before") .equals("chat.geg.sent"), "Org send failed");
            helper.assertTrue(ChatService.firstSequence(server, bob.getUUID(), channel) == -1, "Nonmember can read organization");
            organizations.operate(bob.getUUID(), org.id(), bob.getUUID(), OrganizationSavedData.Operation.APPLY);
            long first = ChatService.firstSequence(server, bob.getUUID(), channel);
            helper.assertTrue(data.history(channel, first, 0).isEmpty(), "New member can read pre-admission messages");
            UUID request = UUID.randomUUID();
            ChatService.send(alice, channel, request, "during"); ChatService.send(alice, channel, request, "duplicate");
            helper.assertTrue(data.history(channel, first, 0).size() == 1, "Duplicate message was stored");
            organizations.operate(bob.getUUID(), org.id(), bob.getUUID(), OrganizationSavedData.Operation.LEAVE);
            helper.assertTrue(ChatService.firstSequence(server, bob.getUUID(), channel) == -1, "Former member can read");
            organizations.operate(bob.getUUID(), org.id(), bob.getUUID(), OrganizationSavedData.Operation.APPLY);
            helper.assertTrue(data.history(channel, ChatService.firstSequence(server, bob.getUUID(), channel), 0).isEmpty(), "Rejoin exposes old membership history");
            ChatChannel dm = ChatChannel.direct(alice.getUUID(), bob.getUUID());
            helper.assertTrue(server.getPlayerList().getPlayer(bob.getUUID()) == null, "Test recipient should be offline");
            helper.assertTrue(ChatService.send(alice, dm, UUID.randomUUID(), "offline private").equals("chat.geg.sent"), "Offline private send failed");
            helper.assertTrue(ChatService.firstSequence(server, other.getUUID(), dm) == -1
                            && ChatService.send(other, dm, UUID.randomUUID(), "forged").equals("chat.geg.forbidden"), "Forged direct channel was authorized");
            helper.assertTrue(data.unread(bob.getUUID(), dm, 1) == 1 && publicEvents[0] == 0, "Private/org text reached public hook");
            ChatSavedData restored = ChatSavedData.load(data.save(new CompoundTag()));
            helper.assertTrue(restored.history(dm, 1, 0).size() == 1 && restored.unread(bob.getUUID(), dm, 1) == 1, "Offline history lost on reload");
            data.markRead(bob.getUUID(), dm, data.latest(dm));
            helper.assertTrue(ChatSavedData.load(data.save(new CompoundTag())).unread(bob.getUUID(), dm, 1) == 0, "Read cursor not persisted");
            helper.assertTrue(ChatService.send(alice, ChatChannel.WORLD, UUID.randomUUID(), "world").equals("chat.geg.sent") && publicEvents[0] == 1, "Public message did not invoke Forge exactly once");
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        helper.succeed();
    }
}
