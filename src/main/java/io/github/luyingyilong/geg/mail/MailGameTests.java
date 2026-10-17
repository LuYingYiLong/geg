package io.github.luyingyilong.geg.mail;

import com.mojang.authlib.GameProfile;
import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.market.MarketDelivery;
import io.github.luyingyilong.geg.market.MarketState;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneySavedData;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GegMod.MODID)
@PrefixGameTestTemplate(false)
public final class MailGameTests {
    @GameTest(template = "empty")
    public static void marketMailMigrationAndItemClaims(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mail_items"));
        UUID owner = player.getUUID();
        MoneySavedData data = new MoneySavedData();
        var sample = new ItemStack(Items.PAPER);
        sample.getOrCreateTag().putString("variant", "mail-reward");
        var legacy = new CompoundTag();
        var accounts = new ListTag();
        var account = new CompoundTag(); account.putUUID("player", owner);
        var entries = new ListTag();
        entries.add(new MarketDelivery(1, sample, 70).save());
        account.put("entries", entries); accounts.add(account); legacy.put("deliveries", accounts);
        var credits = new ListTag();
        var credit = new CompoundTag(); credit.putUUID("player", owner); credit.putLong("amount", 250);
        credits.add(credit); legacy.put("pendingCoin", credits);
        var oldMarket = MarketState.load(legacy);
        oldMarket.migrateClaims(data); oldMarket.migrateClaims(data);
        helper.assertTrue(data.mail().list(owner, MailState.Filter.PENDING).size() == 2,
                "Legacy market claims were lost or delivered twice");
        helper.assertTrue(oldMarket.save().getList("deliveries", Tag.TAG_COMPOUND).isEmpty()
                        && oldMarket.save().getList("coinCredits", Tag.TAG_COMPOUND).isEmpty(),
                "Mail migration retained a second claim path");
        helper.assertTrue(data.audit().size() == 1 && data.audit().get(0).reason().equals("market_mail")
                        && data.audit().get(0).from().startsWith("pending:") && !data.audit().get(0).from().equals("system:mint"),
                "Migrating market funds issued new currency");
        UUID id = MarketState.mailId(owner, 1, false);
        int unread = data.mail().unread(owner);
        data.mail().markRead(data, owner, id);
        helper.assertTrue(!data.mail().receipt(owner, id).read() && data.mail().unread(owner) == unread,
                "Opening item mail marked it read before claiming its attachments");
        helper.assertTrue(data.mail().detail(UUID.randomUUID(), id) == null, "Another player could read item mail");
        helper.assertTrue(data.mail().delete(data, owner, id) == MailState.Result.UNCLAIMED, "Pending items were deletable");
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot,
                new ItemStack(Items.STONE, 64));
        var wrong = sample.copyWithCount(63); wrong.getOrCreateTag().putString("variant", "different");
        player.getInventory().setItem(0, wrong);
        helper.assertTrue(data.mail().claim(data, owner, id, player.getInventory()) == MailState.Result.NO_ROOM,
                "An attachment merged into different item NBT");
        player.getInventory().setItem(0, sample.copyWithCount(63));
        helper.assertTrue(data.mail().claim(data, owner, id, player.getInventory()) == MailState.Result.PARTIAL,
                "Partially available inventory did not accept one item");
        helper.assertTrue(player.getInventory().getItem(0).getCount() == 64
                        && data.mail().detail(owner, id).items().get(0).quantity() == 69,
                "Partial item claim lost its remainder");
        helper.assertTrue(!data.mail().receipt(owner, id).read() && data.mail().unread(owner) == unread,
                "Partial claim incorrectly marked item mail read");
        CompoundTag saved = data.mail().save();
        // Simulate a receipt created by the earlier open-to-read policy.
        for (var raw : saved.getList("receipts", Tag.TAG_COMPOUND)) {
            CompoundTag receipt = (CompoundTag) raw;
            if (receipt.getUUID("mail").equals(id)) receipt.putBoolean("read", true);
        }
        MailState restored = MailState.load(saved);
        helper.assertTrue(!restored.receipt(owner, id).read() && restored.unread(owner) == unread
                        && restored.revision() > saved.getLong("revision"),
                "Restart did not correct legacy read receipts for unclaimed item mail");
        helper.assertTrue(restored.detail(owner, id).items().get(0).quantity() == 69
                        && restored.detail(owner, id).items().get(0).item().getTag().equals(sample.getTag()),
                "Item attachment quantity or NBT did not survive restart");
        helper.assertTrue(restored.claim(data, owner, id, player.getInventory()) == MailState.Result.NO_ROOM,
                "Full inventory consumed pending items after reload");
        player.getInventory().setItem(1, ItemStack.EMPTY);
        player.getInventory().setItem(2, ItemStack.EMPTY);
        helper.assertTrue(restored.claim(data, owner, id, player.getInventory()) == MailState.Result.SUCCESS
                        && player.getInventory().getItem(1).getCount() == 64 && player.getInventory().getItem(2).getCount() == 5,
                "Remaining attachment did not split across inventory stacks");
        helper.assertTrue(restored.receipt(owner, id).read() && restored.unread(owner) == unread - 1,
                "Claiming the last item did not mark the mail read");
        helper.assertTrue(restored.claim(data, owner, id, player.getInventory()) == MailState.Result.ALREADY_CLAIMED,
                "Item attachment was duplicated");
        helper.assertTrue(restored.delete(data, owner, id) == MailState.Result.SUCCESS, "Claimed item mail could not be deleted");
        // Replay the old source after deleting the mail: its durable send key must still prevent redelivery.
        restored.deliverMarket(data, MarketState.mailKey(owner, 1, false),
                new MailMessage(id, 0, MailMessage.Kind.SYSTEM, "", "title", "body", List.of(), 0,
                        MailActionPacket.Scope.PLAYER, null, "", List.of(new MailItem(sample, 70))), owner, "");
        helper.assertTrue(restored.detail(owner, id) == null, "Deleted market mail reappeared during a retry");
        var preview = new MailMessage(UUID.randomUUID(), 0, MailMessage.Kind.SYSTEM, "", "title", "body", List.of(), 0,
                MailActionPacket.Scope.PLAYER, null, "", List.of(new MailItem(sample, 700)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new MailSyncPacket(MailSyncPacket.Kind.DETAIL, 1, 1, false, MailState.Filter.ALL,
                    0, false, List.of(new MailSyncPacket.Row(preview.id(), preview.titleText(), "", MailMessage.Kind.SYSTEM,
                    0, 0, false, false, MailActionPacket.Scope.PLAYER, true)), preview, false, preview.id(), 0, 0, "", "");
            MailSyncPacket.encode(packet, buffer);
            helper.assertTrue(MailSyncPacket.decode(buffer).equals(packet), "Item mail wire roundtrip lost item NBT or large quantities");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mixedMailDoesNotCreditCoinTwice(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mixed_mail"));
        MoneySavedData data = new MoneySavedData(); UUID id = UUID.randomUUID(), owner = player.getUUID();
        var item = new ItemStack(Items.DIAMOND);
        var mail = new MailMessage(id, 0, MailMessage.Kind.SYSTEM, "", "title", "body", List.of(), 100,
                MailActionPacket.Scope.PLAYER, null, "", List.of(new MailItem(item, 2)));
        data.mail().deliverMarket(data, "mixed:" + id, mail, owner, "pending:" + owner + ":1");
        data.put(owner, new MoneyBalance(Long.MAX_VALUE, 0));
        helper.assertTrue(data.mail().claim(data, owner, id, player.getInventory()) == MailState.Result.WALLET_FULL
                        && player.getInventory().getItem(0).isEmpty(), "Overflowing mixed claim moved items");
        data.put(owner, MoneyBalance.ZERO);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot,
                new ItemStack(Items.STONE, 64));
        helper.assertTrue(data.mail().claim(data, owner, id, player.getInventory()) == MailState.Result.PARTIAL
                        && data.balance(owner).coinMinorUnits() == 100 && data.mail().detail(owner, id).coin() == 0,
                "Mixed attachment did not retain only its unclaimed items");
        data.mail().markRead(data, owner, id);
        helper.assertTrue(!data.mail().receipt(owner, id).read(), "Claiming only coin marked mixed mail read");
        var restored = MailState.load(data.mail().save());
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(restored.claim(data, owner, id, player.getInventory()) == MailState.Result.SUCCESS
                        && data.balance(owner).coinMinorUnits() == 100, "Claiming remaining items credited coin again");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void attachmentIsIssuedOnceAndSurvivesOverflow(GameTestHelper helper) {
        MoneySavedData data = MoneySavedData.get(helper.getLevel().getServer());
        UUID player = UUID.randomUUID(), id = UUID.randomUUID();
        MailMessage mail = new MailMessage(id, System.currentTimeMillis(), MailMessage.Kind.ADMIN,
                "Test", "Gift", "Test gift", List.of(), 100);
        String key = "gametest:" + id;
        data.mail().deliver(data, key, mail, List.of(player));
        data.mail().deliver(data, key, mail, List.of(player));
        helper.assertTrue(data.audit().stream().filter(entry -> entry.reference().equals(id.toString())).count() == 1,
                "Duplicate mail confirmation issued coin twice");
        data.put(player, new MoneyBalance(Long.MAX_VALUE - 50, 0));
        helper.assertTrue(data.mail().claim(data, player, id) == MailState.Result.WALLET_FULL,
                "Full wallet consumed the attachment");
        MailState restored = MailState.load(data.mail().save());
        helper.assertTrue(restored.detail(player, id) != null && !restored.receipt(player, id).claimed(),
                "Pending mail did not survive save/load");
        data.put(player, MoneyBalance.ZERO);
        helper.assertTrue(data.mail().claim(data, player, id) == MailState.Result.SUCCESS
                && data.balance(player).coinMinorUnits() == 100, "Attachment did not credit the wallet");
        helper.assertTrue(data.mail().claim(data, player, id) == MailState.Result.ALREADY_CLAIMED,
                "Attachment was claimed twice");
        helper.succeed();
    }
}
