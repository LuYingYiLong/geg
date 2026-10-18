package io.github.luyingyilong.geg.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.luyingyilong.geg.organization.OrganizationSavedData;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ChatCommands {
    private ChatCommands() { }
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chat")
                .then(branch("world").executes(ctx -> select(ctx.getSource(), ChatChannel.WORLD))
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx ->
                                send(ctx.getSource(), ChatChannel.WORLD, StringArgumentType.getString(ctx, "message")))))
                .then(branch("org").then(Commands.argument("organization", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            var player = ctx.getSource().getPlayerOrException();
                            for (var org : OrganizationSavedData.get(player.server).memberships(player.getUUID()))
                                builder.suggest(StringArgumentType.escapeIfRequired(org.name()));
                            return builder.buildFuture();
                        }).executes(ctx -> selectOrganization(ctx.getSource(), StringArgumentType.getString(ctx, "organization"), null))
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx ->
                                selectOrganization(ctx.getSource(), StringArgumentType.getString(ctx, "organization"), StringArgumentType.getString(ctx, "message"))))))
                .then(branch("tell").then(Commands.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            ChatSavedData.get(ctx.getSource().getServer()).players().values().stream()
                                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                            return builder.buildFuture();
                        }).executes(ctx -> tell(ctx.getSource(), StringArgumentType.getString(ctx, "player"), null))
                        .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx ->
                                tell(ctx.getSource(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "message"))))))
                .then(branch("reply").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    var channels = ChatSavedData.get(player.server).directChannels(player.getUUID());
                    if (channels.isEmpty()) return fail(ctx.getSource(), "chat.geg.no_contact");
                    return send(ctx.getSource(), channels.get(0), StringArgumentType.getString(ctx, "message"));
                }))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> branch(String name) { return Commands.literal(name); }
    private static int select(CommandSourceStack source, ChatChannel channel) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ChatService.select(source.getPlayerOrException(), channel); return 1;
    }
    private static int send(CommandSourceStack source, ChatChannel channel, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String result = ChatService.send(source.getPlayerOrException(), channel, UUID.randomUUID(), text);
        return result.equals("chat.geg.sent") ? 1 : fail(source, result);
    }
    private static int tell(CommandSourceStack source, String target, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException(); UUID other = ChatService.resolvePlayer(player.server, target);
        if (other == null || other.equals(player.getUUID())) return fail(source, "chat.geg.unknown_player");
        ChatChannel channel = ChatChannel.direct(player.getUUID(), other);
        return text == null ? select(source, channel) : send(source, channel, text);
    }
    private static int selectOrganization(CommandSourceStack source, String target, String text) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        for (var org : OrganizationSavedData.get(player.server).memberships(player.getUUID()))
            if (org.id().toString().equals(target) || org.name().equalsIgnoreCase(target))
                return text == null ? select(source, ChatChannel.organization(org.id())) : send(source, ChatChannel.organization(org.id()), text);
        return fail(source, "chat.geg.forbidden");
    }
    private static int fail(CommandSourceStack source, String key) { source.sendFailure(Component.translatable(key)); return 0; }
}
