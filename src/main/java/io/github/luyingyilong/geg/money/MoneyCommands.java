package io.github.luyingyilong.geg.money;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.luyingyilong.geg.money.integration.LightmansMigrationCommands;
import io.github.luyingyilong.geg.market.MarketRules;
import io.github.luyingyilong.geg.market.MarketNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class MoneyCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger(MoneyCommands.class);

    private MoneyCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("money")
                .executes(context -> showOwnBalance(context.getSource()))
                .then(Commands.literal("balance")
                        .executes(context -> showOwnBalance(context.getSource()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(MoneyCommands::showOtherBalance)))
                .then(Commands.literal("pay")
                        .requires(source -> source.getEntity() instanceof ServerPlayer)
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(payBranch(MoneyCurrency.COIN))
                                .then(payBranch(MoneyCurrency.GEM))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(adminBranch(MoneyCurrency.COIN))
                        .then(adminBranch(MoneyCurrency.GEM)))
                .then(Commands.literal("market")
                        .executes(context -> showMarketSettings(context.getSource()))
                        .then(Commands.literal("fee")
                                .executes(context -> showMarketFee(context.getSource()))
                                .then(Commands.argument("percent", StringArgumentType.word())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(MoneyCommands::setMarketFee))))
                .then(Commands.literal("audit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("summary")
                                .executes(context -> auditSummary(context.getSource(), 7, null))
                                .then(Commands.argument("days", IntegerArgumentType.integer(1, 36500))
                                        .executes(context -> auditSummary(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "days"), null))))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(context -> auditPlayer(context, 7))
                                        .then(Commands.argument("days", IntegerArgumentType.integer(1, 36500))
                                                .executes(context -> auditPlayer(context,
                                                        IntegerArgumentType.getInteger(context, "days"))))))
                        .then(Commands.literal("export")
                                .then(Commands.argument("date", StringArgumentType.word())
                                        .executes(MoneyCommands::auditExport))));

        if (ModList.get().isLoaded("lightmanscurrency")) {
            root.then(LightmansMigrationCommands.branch());
        }

        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> payBranch(MoneyCurrency currency) {
        return Commands.literal(currency.id())
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(context -> pay(context, currency)));
    }

    private static int showMarketSettings(CommandSourceStack source) {
        boolean enabled = MarketRules.enabled(source.getServer());
        int basisPoints = MoneySavedData.get(source.getServer()).marketFeeBasisPoints();
        source.sendSuccess(() -> Component.translatable("commands.geg.money.market.settings",
                Component.translatable(enabled ? "commands.geg.money.market.enabled"
                        : "commands.geg.money.market.disabled"), MoneyCurrency.COIN.format(basisPoints)), false);
        return 1;
    }

    private static int showMarketFee(CommandSourceStack source) {
        int basisPoints = MoneySavedData.get(source.getServer()).marketFeeBasisPoints();
        source.sendSuccess(() -> Component.translatable("commands.geg.money.market.fee",
                MoneyCurrency.COIN.format(basisPoints)), false);
        return 1;
    }

    private static int setMarketFee(CommandContext<CommandSourceStack> context) {
        String input = StringArgumentType.getString(context, "percent");
        if (!input.matches("(?:0|[1-9][0-9]?|100)(?:\\.[0-9]{1,2})?")) {
            context.getSource().sendFailure(Component.translatable("commands.geg.money.market.invalid_fee"));
            return 0;
        }
        int basisPoints;
        try {
            basisPoints = new BigDecimal(input).movePointRight(2).intValueExact();
        } catch (ArithmeticException exception) {
            context.getSource().sendFailure(Component.translatable("commands.geg.money.market.invalid_fee"));
            return 0;
        }
        if (basisPoints > 10000) {
            context.getSource().sendFailure(Component.translatable("commands.geg.money.market.invalid_fee"));
            return 0;
        }
        MoneySavedData.get(context.getSource().getServer()).setMarketFeeBasisPoints(basisPoints);
        MarketNetwork.syncViewers(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.translatable("commands.geg.money.market.fee_set",
                MoneyCurrency.COIN.format(basisPoints)), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> adminBranch(MoneyCurrency currency) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal(currency.id());
        for (Action action : Action.values()) {
            branch.then(Commands.literal(action.id)
                    .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("amount", StringArgumentType.word())
                                    .executes(context -> adminChange(context, currency, action)))));
        }
        return branch;
    }

    private static int showOwnBalance(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        sendBalance(source, player);
        return 1;
    }

    private static int showOtherBalance(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        sendBalance(context.getSource(), player);
        return 1;
    }

    private static void sendBalance(CommandSourceStack source, ServerPlayer player) {
        MoneyBalance balance = MoneyService.balance(source.getServer(), player.getUUID());
        source.sendSuccess(() -> Component.translatable(
                "commands.geg.money.balance",
                player.getGameProfile().getName(),
                MoneyCurrency.COIN.format(balance.coinMinorUnits()),
                MoneyCurrency.GEM.format(balance.gem())
        ), false);
    }

    private static int pay(CommandContext<CommandSourceStack> context, MoneyCurrency currency) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("commands.geg.money.pay.self"));
            return 0;
        }

        Long amount = parseAmount(source, context, currency, false);
        if (amount == null) {
            return 0;
        }

        try {
            if (!MoneyService.transfer(source.getServer(), sender.getUUID(), target.getUUID(), currency, amount)) {
                source.sendFailure(Component.translatable("commands.geg.money.insufficient"));
                return 0;
            }
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.translatable("commands.geg.money.pay.recipient_overflow"));
            return 0;
        }

        String formatted = currency.format(amount);
        Component currencyName = Component.translatable(currency.translationKey());
        source.sendSuccess(() -> Component.translatable(
                "commands.geg.money.pay.sent",
                target.getGameProfile().getName(),
                formatted,
                currencyName
        ), false);
        target.sendSystemMessage(Component.translatable(
                "commands.geg.money.pay.received",
                sender.getGameProfile().getName(),
                formatted,
                currencyName
        ));
        return 1;
    }

    private static int adminChange(
            CommandContext<CommandSourceStack> context,
            MoneyCurrency currency,
            Action action
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        Long amount = parseAmount(source, context, currency, action == Action.SET);
        if (amount == null) {
            return 0;
        }

        try {
            switch (action) {
                case ADD -> MoneyService.add(source.getServer(), target.getUUID(), currency, amount);
                case TAKE -> {
                    if (!MoneyService.take(source.getServer(), target.getUUID(), currency, amount)) {
                        source.sendFailure(Component.translatable("commands.geg.money.admin.insufficient"));
                        return 0;
                    }
                }
                case SET -> MoneyService.set(source.getServer(), target.getUUID(), currency, amount);
            }
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.translatable("commands.geg.money.overflow"));
            return 0;
        }

        Component currencyName = Component.translatable(currency.translationKey());
        source.sendSuccess(() -> Component.translatable(
                "commands.geg.money.admin." + action.id,
                target.getGameProfile().getName(),
                currency.format(amount),
                currencyName
        ), true);
        long updatedAmount = MoneyService.balance(source.getServer(), target.getUUID()).amount(currency);
        target.sendSystemMessage(Component.translatable(
                "commands.geg.money.admin.target",
                currencyName,
                currency.format(updatedAmount)
        ));
        return 1;
    }

    private static Long parseAmount(
            CommandSourceStack source,
            CommandContext<CommandSourceStack> context,
            MoneyCurrency currency,
            boolean allowZero
    ) {
        String text = StringArgumentType.getString(context, "amount");
        try {
            return currency.parseAmount(text, allowZero);
        } catch (IllegalArgumentException exception) {
            String key = "commands.geg.money.invalid." + currency.id()
                    + (allowZero ? ".nonnegative" : ".positive");
            source.sendFailure(Component.translatable(key));
            return null;
        }
    }

    private static int auditPlayer(CommandContext<CommandSourceStack> context, int days) {
        String raw = StringArgumentType.getString(context, "player");
        UUID id;
        try {
            id = UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            var profile = context.getSource().getServer().getProfileCache().get(raw);
            if (profile.isEmpty()) {
                context.getSource().sendFailure(Component.translatable("commands.geg.money.audit.unknown_player"));
                return 0;
            }
            id = profile.get().getId();
        }
        return auditSummary(context.getSource(), days, id);
    }

    private static int auditSummary(CommandSourceStack source, int days, UUID player) {
        CoinAuditService.Summary summary = CoinAuditService.summary(source.getServer(), days, player);
        source.sendSuccess(() -> Component.translatable("commands.geg.money.audit.summary",
                days, summary.count(), CoinAuditService.format(summary.opening()),
                CoinAuditService.format(summary.minted()),
                CoinAuditService.format(summary.sunk()), CoinAuditService.format(summary.transferred())), false);
        return 1;
    }

    private static int auditExport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            LocalDate day = LocalDate.parse(StringArgumentType.getString(context, "date"));
            var path = CoinAuditService.export(source.getServer(), day);
            source.sendSuccess(() -> Component.translatable("commands.geg.money.audit.export", path.toString()), false);
            return 1;
        } catch (DateTimeParseException exception) {
            source.sendFailure(Component.translatable("commands.geg.money.audit.invalid_date"));
        } catch (IOException exception) {
            LOGGER.error("Could not export GEG money audit report", exception);
            source.sendFailure(Component.translatable("commands.geg.money.audit.io_error"));
        }
        return 0;
    }


    private enum Action {
        ADD("add"),
        TAKE("take"),
        SET("set"),
        ;

        private final String id;


        Action(String id) {
            this.id = id;
        }
    }
}
