package me.ichun.mods.morph.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class MorphCommands {
    private MorphCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("morph")
            .then(Commands.literal("nametag").executes(c -> MorphAuthority.nametag(c.getSource().getPlayerOrException(), null))
                .then(Commands.literal("toggle").executes(c -> MorphAuthority.nametag(c.getSource().getPlayerOrException(), null)))
                .then(Commands.literal("on").executes(c -> MorphAuthority.nametag(c.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("off").executes(c -> MorphAuthority.nametag(c.getSource().getPlayerOrException(), false))))
            .then(Commands.literal("menu").executes(c -> { MorphAuthority.requestCollection(c.getSource().getPlayerOrException()); return 1; }))
            .then(Commands.literal("list").executes(c -> {
                var player = c.getSource().getPlayerOrException();
                var forms = MorphAuthority.collection(player).ownedForms();
                c.getSource().sendSuccess(() -> Component.literal("Morphs: " + String.join(", ", forms)), false);
                MorphAuthority.requestCollection(player);
                return forms.size();
            }))
            .then(Commands.literal("select").then(Commands.argument("form", IdentifierArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(MorphAuthority.collection(c.getSource().getPlayerOrException()).ownedForms(), b))
                .executes(c -> MorphAuthority.select(c.getSource().getPlayerOrException(), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0)))
            .then(Commands.literal("reset").executes(c -> MorphAuthority.reset(c.getSource().getPlayerOrException()) ? 1 : 0)
                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(c -> MorphAuthority.reset(EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
            .then(Commands.literal("grant").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("form", IdentifierArgument.id()).executes(c -> MorphAuthority.grant(c.getSource().getPlayerOrException(), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("form", IdentifierArgument.id())
                    .executes(c -> MorphAuthority.grant(EntityArgument.getPlayer(c, "player"), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0))))
            .then(Commands.literal("force").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("form", IdentifierArgument.id())
                    .executes(c -> MorphAuthority.select(EntityArgument.getPlayer(c, "player"), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0)))));
    }
}
