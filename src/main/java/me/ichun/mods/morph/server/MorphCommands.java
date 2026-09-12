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
    private static me.ichun.mods.morph.model.EntryId entry(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try { return new me.ichun.mods.morph.model.EntryId(com.mojang.brigadier.arguments.StringArgumentType.getString(context, "entry")); }
        catch (IllegalArgumentException invalid) { throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Invalid morph entry ID")).create(); }
    }
    private MorphCommands() {}
    private static java.util.UUID actor(CommandSourceStack source) {
        return source.getEntity() == null ? new java.util.UUID(0, 0) : source.getEntity().getUUID();
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        me.ichun.mods.morph.config.MorphConfigCommands.register(dispatcher);
        me.ichun.mods.morph.progression.BiomassCommands.register(dispatcher, new me.ichun.mods.morph.progression.BiomassCommands.Access() {
            public boolean enabled(net.minecraft.server.level.ServerPlayer player) { return MorphAuthority.biomassEnabled(player); }
            public me.ichun.mods.morph.progression.BiomassLedger ledger(net.minecraft.server.level.ServerPlayer player) { return MorphAuthority.data(player).biomass(player.getUUID()); }
            public me.ichun.mods.morph.progression.BiomassRuntime.Outcome purchase(net.minecraft.server.level.ServerPlayer player, long revision,
                    me.ichun.mods.morph.progression.BiomassDefinitions.Upgrade upgrade) {
                boolean enabled = enabled(player) && player.isAlive() && !player.isRemoved() && !player.isSpectator();
                return MorphAuthority.biomass(player).purchase(player.getUUID(), enabled, revision, upgrade);
            }
        });
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
                    .executes(c -> MorphAuthority.reset(actor(c.getSource()), EntityArgument.getPlayer(c, "player")) ? 1 : 0)))
            .then(Commands.literal("grant").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("form", IdentifierArgument.id()).executes(c -> MorphAuthority.grant(c.getSource().getPlayerOrException(), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("form", IdentifierArgument.id())
                    .executes(c -> MorphAuthority.grant(actor(c.getSource()), EntityArgument.getPlayer(c, "player"), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0))))
            .then(Commands.literal("selectentry")
                .then(Commands.argument("entry", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(c -> MorphAuthority.report(c.getSource().getPlayerOrException(), MorphAuthority.selectEntry(c.getSource().getPlayerOrException(), entry(c))) ? 1 : 0)))
            .then(Commands.literal("unacquire").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("entry", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(c -> MorphAuthority.report(EntityArgument.getPlayer(c, "player"), MorphAuthority.deleteEntry(actor(c.getSource()), EntityArgument.getPlayer(c, "player"), entry(c))) ? 1 : 0))))
            .then(Commands.literal("forceentry").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("entry", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(c -> MorphAuthority.report(EntityArgument.getPlayer(c, "player"), MorphAuthority.selectEntry(actor(c.getSource()), EntityArgument.getPlayer(c, "player"), entry(c))) ? 1 : 0))))
            .then(Commands.literal("force").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("form", IdentifierArgument.id())
                    .executes(c -> MorphAuthority.select(actor(c.getSource()), EntityArgument.getPlayer(c, "player"), IdentifierArgument.getId(c, "form").toString()) ? 1 : 0)))));
    }
}
