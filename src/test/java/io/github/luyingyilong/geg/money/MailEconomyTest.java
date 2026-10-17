package io.github.luyingyilong.geg.money;

import io.github.luyingyilong.geg.mail.*;
import io.github.luyingyilong.geg.mail.MailMessage;
import io.github.luyingyilong.geg.mail.MailState;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

public final class MailEconomyTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), outsider = UUID.randomUUID(), id = UUID.randomUUID();
        MoneySavedData data = new MoneySavedData();
        MailMessage message = new MailMessage(id, 100, MailMessage.Kind.ADMIN, "Operator", "Gift", "Hello", List.of(), 125);
        data.mail().remember(a, "Alice"); data.mail().remember(b, "Bob");
        data.mail().deliver(data, "gift", message, List.of(a, b, a));
        check(data.audit().size() == 2 && data.audit().stream().allMatch(entry -> entry.from().equals("system:mint")));
        data.mail().deliver(data, "gift", message, List.of(a, b));
        check(data.audit().size() == 2 && data.mail().unread(a) == 1);
        check(data.mail().detail(outsider, id) == null);
        check(data.mail().claim(data, outsider, id) == MailState.Result.NOT_FOUND);
        check(data.mail().delete(data, a, id) == MailState.Result.UNCLAIMED);
        check(data.mail().markRead(data, a, id) && data.mail().unread(a) == 0 && data.mail().unread(b) == 1);
        data.put(a, new MoneyBalance(Long.MAX_VALUE - 100, 2));
        check(data.mail().claim(data, a, id) == MailState.Result.WALLET_FULL);
        check(!data.mail().receipt(a, id).claimed() && data.audit().size() == 2);
        data = MoneySavedData.load(data.save(new CompoundTag()));
        check(data.mail().sent("gift").equals(id) && data.mail().players().get(a).equals("Alice"));
        check(data.balance(a).coinMinorUnits() == Long.MAX_VALUE - 100);
        data.put(a, new MoneyBalance(0, 2));
        check(data.mail().claim(data, a, id) == MailState.Result.SUCCESS);
        check(data.balance(a).coinMinorUnits() == 125 && data.balance(a).gem() == 2);
        check(data.mail().claim(data, a, id) == MailState.Result.ALREADY_CLAIMED);
        check(data.audit().size() == 3 && data.audit().get(2).from().equals(MailState.pending(a, id))
                && data.audit().get(2).to().equals(MoneyService.wallet(a)));
        check(data.mail().delete(data, a, id) == MailState.Result.SUCCESS);
        data = MoneySavedData.load(data.save(new CompoundTag()));
        data.mail().deliver(data, "gift", message, List.of(a, b));
        check(data.mail().detail(a, id) == null && data.mail().unread(a) == 0);
        check(data.mail().detail(b, id) != null && data.mail().claim(data, b, id) == MailState.Result.SUCCESS);
        check(data.audit().stream().filter(entry -> entry.reason().equals("mail_issue")).count() == 2);
        check(data.audit().stream().filter(entry -> entry.reason().equals("mail_claim")).count() == 2);
        check(MoneySavedData.load(new CompoundTag()).mail().unread(a) == 0);
        data.mail().announcementSent(a, 999);
        check(MoneySavedData.load(data.save(new CompoundTag())).mail().lastAnnouncement(a) == 999);
        UUID zero = UUID.randomUUID();
        data.mail().deliver(data, "text", new MailMessage(zero, 200, MailMessage.Kind.ORGANIZATION, "Org", "Notice", "Body", List.of(), 0), List.of(a));
        check(data.mail().delete(data, a, zero) == MailState.Result.SUCCESS);
        for (int i = 0; i < 120; i++) data.mail().deliver(data, "page:" + i,
                new MailMessage(UUID.randomUUID(), 1000 + i, MailMessage.Kind.ADMIN, "OP", "Mail " + i, "Body", List.of(), 0), List.of(a));
        var list = data.mail().list(a, MailState.Filter.ALL);
        check(list.size() == 120 && list.get(0).sentAt() == 1119 && list.get(119).sentAt() == 1000);
        data.mail().markRead(data, a, list.get(0).id());
        check(data.mail().list(a, MailState.Filter.UNREAD).size() == 119);
        check(data.mail().list(a, MailState.Filter.PENDING).isEmpty());
    }
    private static void check(boolean value) { if (!value) throw new AssertionError("Mail economics/persistence regression"); }
}
