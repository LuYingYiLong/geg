package io.github.luyingyilong.geg.mail;

import io.github.luyingyilong.geg.GegMod;
import io.github.luyingyilong.geg.money.*;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneySavedData;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

import java.util.*;

@GameTestHolder(GegMod.MODID)
@PrefixGameTestTemplate(false)
public final class MailGameTests {
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
