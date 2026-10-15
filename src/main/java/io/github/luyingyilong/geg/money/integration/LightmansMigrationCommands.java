package io.github.luyingyilong.geg.money.integration;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.IWalletHandler;
import io.github.lightman314.lightmanscurrency.common.capability.wallet.WalletCapability;
import io.github.lightman314.lightmanscurrency.common.items.WalletItem;
import io.github.luyingyilong.geg.money.MoneyBalance;
import io.github.luyingyilong.geg.money.MoneyCurrency;
import io.github.luyingyilong.geg.money.MoneyService;
import io.github.luyingyilong.geg.money.MoneyRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class LightmansMigrationCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(LightmansMigrationCommands.class);
    private static final String MOD_ID = "lightmanscurrency";
    private static final String[] COIN_IDS = {
            "coin_copper", "coin_iron", "coin_gold", "coin_emerald", "coin_diamond", "coin_netherite"
    };
    // GEG coin uses hundredths; LC's main coin chain increases tenfold at each tier.
    private static final long[] COIN_MINOR_UNITS = {1L, 10L, 1000L, 1_000L, 10_000L, 100_000L};

    private LightmansMigrationCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("migrate")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.literal("lightmans")
                        .executes(context -> preview(context.getSource()))
                        .then(Commands.literal("confirm")
                                .executes(context -> confirm(context.getSource())))
                        .then(Commands.literal("rates")
                                .executes(context -> rates(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource()))));
    }

    private static int preview(CommandSourceStack source) {
        if (!MoneyRules.lightmansMigrationEnabled(source.getServer())) {
            source.sendFailure(Component.translatable("commands.geg.money.migrate.disabled"));
            return 0;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            MigrationPlan plan = prepare(player);
            if (plan.totalCoins == 0L) {
                source.sendFailure(Component.translatable("commands.geg.money.migrate.empty"));
                return 0;
            }
            Math.addExact(MoneyService.balance(source.getServer(), player.getUUID()).coinMinorUnits(), plan.minorUnits);
            source.sendSuccess(() -> Component.translatable("commands.geg.money.migrate.preview",
                    plan.inventoryCoins, plan.walletCoins, MoneyCurrency.COIN.format(plan.minorUnits)), false);
            source.sendSuccess(() -> Component.translatable("commands.geg.money.migrate.breakdown",
                    plan.counts[0], plan.counts[1], plan.counts[2],
                    plan.counts[3], plan.counts[4], plan.counts[5]), false);
            source.sendSuccess(() -> Component.translatable("commands.geg.money.migrate.confirm_hint"), false);
            return 1;
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.translatable("commands.geg.money.overflow"));
            return 0;
        } catch (Exception exception) {
            LOGGER.error("Could not preview Lightman's Currency migration", exception);
            source.sendFailure(Component.translatable("commands.geg.money.migrate.failed"));
            return 0;
        }
    }

    private static int confirm(CommandSourceStack source) {
        if (!MoneyRules.lightmansMigrationEnabled(source.getServer())) {
            source.sendFailure(Component.translatable("commands.geg.money.migrate.disabled"));
            return 0;
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            MigrationPlan plan = prepare(player);
            if (plan.totalCoins == 0L) {
                source.sendFailure(Component.translatable("commands.geg.money.migrate.empty"));
                return 0;
            }
            MoneyBalance before = MoneyService.balance(source.getServer(), player.getUUID());
            long updated = Math.addExact(before.coinMinorUnits(), plan.minorUnits);
            apply(player, plan, before.coinMinorUnits(), updated);
            source.sendSuccess(() -> Component.translatable("commands.geg.money.migrate.done",
                    plan.totalCoins, MoneyCurrency.COIN.format(plan.minorUnits),
                    MoneyCurrency.COIN.format(updated)), true);
            return 1;
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.translatable("commands.geg.money.overflow"));
            return 0;
        } catch (Exception exception) {
            LOGGER.error("Could not complete Lightman's Currency migration", exception);
            source.sendFailure(Component.translatable("commands.geg.money.migrate.failed"));
            return 0;
        }
    }

    private static int rates(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("commands.geg.money.migrate.rates"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        String key = MoneyRules.lightmansMigrationEnabled(source.getServer())
                ? "commands.geg.money.migrate.enabled" : "commands.geg.money.migrate.disabled_status";
        source.sendSuccess(() -> Component.translatable(key), false);
        return 1;
    }

    private static MigrationPlan prepare(ServerPlayer player) {
        MigrationPlan plan = new MigrationPlan();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack original = inventory.getItem(slot);
            int coin = coinIndex(original);
            if (coin >= 0) {
                plan.add(coin, original.getCount(), false);
                plan.slots.add(new SlotChange(slot, original.copy(), ItemStack.EMPTY));
            } else if (WalletItem.isWallet(original)) {
                ItemStack changed = removeWalletCoins(original, plan);
                if (changed != null) {
                    plan.slots.add(new SlotChange(slot, original.copy(), changed));
                }
            }
        }

        IWalletHandler handler = WalletCapability.lazyGetWalletHandler(player);
        if (handler != null) {
            ItemStack equipped = handler.getWallet();
            if (WalletItem.isWallet(equipped)) {
                ItemStack changed = removeWalletCoins(equipped, plan);
                if (changed != null) {
                    plan.equippedWallet = new WalletChange(handler, equipped.copy(), changed);
                }
            }
        }
        return plan;
    }

    private static ItemStack removeWalletCoins(ItemStack wallet, MigrationPlan plan) {
        ItemStack result = wallet.copy();
        SimpleContainer contents = WalletItem.getWalletInventory(result);
        boolean changed = false;
        for (int slot = 0; slot < contents.getContainerSize(); slot++) {
            ItemStack coins = contents.getItem(slot);
            int coin = coinIndex(coins);
            if (coin >= 0) {
                plan.add(coin, coins.getCount(), true);
                contents.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (!changed) {
            return null;
        }
        WalletItem.putWalletInventory(result, contents);
        return result;
    }

    private static int coinIndex(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !MOD_ID.equals(id.getNamespace())) {
            return -1;
        }
        for (int index = 0; index < COIN_IDS.length; index++) {
            if (COIN_IDS[index].equals(id.getPath())) {
                return index;
            }
        }
        return -1;
    }

    private static void apply(ServerPlayer player, MigrationPlan plan, long oldBalance, long newBalance) {
        Inventory inventory = player.getInventory();
        try {
            for (SlotChange slot : plan.slots) {
                inventory.setItem(slot.index, slot.after.copy());
            }
            if (plan.equippedWallet != null) {
                plan.equippedWallet.handler.setWallet(plan.equippedWallet.after.copy());
            }
            MoneyService.addWithReason(player.server, player.getUUID(), MoneyCurrency.COIN,
                    newBalance - oldBalance, "lightmans_migration", "coins=" + plan.totalCoins);
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        } catch (RuntimeException exception) {
            for (SlotChange slot : plan.slots) {
                inventory.setItem(slot.index, slot.before.copy());
            }
            if (plan.equippedWallet != null) {
                plan.equippedWallet.handler.setWallet(plan.equippedWallet.before.copy());
            }
            if (MoneyService.balance(player.server, player.getUUID()).coinMinorUnits() != oldBalance) {
                MoneyService.setWithReason(player.server, player.getUUID(), MoneyCurrency.COIN,
                        oldBalance, "lightmans_migration_rollback", "");
            }
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
            throw exception;
        }
    }

    private record SlotChange(int index, ItemStack before, ItemStack after) {
    }

    private record WalletChange(IWalletHandler handler, ItemStack before, ItemStack after) {
    }

    private static final class MigrationPlan {
        private final List<SlotChange> slots = new ArrayList<>();
        private final long[] counts = new long[COIN_IDS.length];
        private WalletChange equippedWallet;
        private long minorUnits;
        private long totalCoins;
        private long inventoryCoins;
        private long walletCoins;

        private void add(int coin, int count, boolean wallet) {
            minorUnits = Math.addExact(minorUnits, Math.multiplyExact((long) count, COIN_MINOR_UNITS[coin]));
            counts[coin] = Math.addExact(counts[coin], count);
            totalCoins = Math.addExact(totalCoins, count);
            if (wallet) {
                walletCoins = Math.addExact(walletCoins, count);
            } else {
                inventoryCoins = Math.addExact(inventoryCoins, count);
            }
        }
    }
}
