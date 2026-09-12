package me.ichun.mods.morph.progression;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Minimal player spending interface; policy and per-world storage are supplied by shared authority. */
public final class BiomassCommands {
    public interface Access {
        boolean enabled(ServerPlayer player);
        BiomassLedger ledger(ServerPlayer player);
        BiomassRuntime.Outcome purchase(ServerPlayer player, long expectedRevision, BiomassDefinitions.Upgrade upgrade);
    }
    private BiomassCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Access access) {
        var biomass = Commands.literal("biomass")
                .executes(c -> status(c.getSource(), access))
                .then(Commands.literal("status").executes(c -> status(c.getSource(), access)));
        var buy = Commands.literal("buy");
        for (var upgrade : BiomassDefinitions.Upgrade.values()) {
            buy.then(Commands.literal(upgrade.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("revision", LongArgumentType.longArg(0))
                            .executes(c -> {
                                var player = c.getSource().getPlayerOrException();
                                var result = access.purchase(player, LongArgumentType.getLong(c, "revision"), upgrade);
                                var message = Component.literal("Biomass purchase: "
                                        + result.status().name().toLowerCase(Locale.ROOT) + ". Balance "
                                        + result.ledger().balance() + "; revision " + result.ledger().revision() + ".");
                                if (result.accepted()) c.getSource().sendSuccess(() -> message, false);
                                else c.getSource().sendFailure(message);
                                return result.status() == BiomassRuntime.Status.CHANGED ? 1 : 0;
                            })));
        }
        biomass.then(buy);
        dispatcher.register(Commands.literal("morph").then(biomass));
    }

    private static int status(CommandSourceStack source, Access access) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var ledger = access.ledger(player);
        var definitions = BiomassRuntime.DEFAULTS;
        boolean enabled = access.enabled(player);
        source.sendSuccess(() -> Component.literal("Biomass " + (enabled ? "enabled" : "disabled")
                + "; " + (ledger.unlocked() ? "unlocked" : "locked") + "; balance " + ledger.balance()
                + "/" + definitions.criticalCapacity(ledger) + " (normal capacity " + definitions.capacity(ledger)
                + "); revision " + ledger.revision() + "."), false);
        if (enabled && !ledger.unlocked())
            source.sendSuccess(() -> Component.literal("Unlock biomass with a nearby eligible kill yielding at least one unit."), false);
        for (var upgrade : BiomassDefinitions.Upgrade.values()) {
            int level = ledger.level(upgrade);
            var definition = definitions.upgrades().get(upgrade);
            String name = upgrade.name().toLowerCase(Locale.ROOT);
            String cost = level == definition.maxLevel() ? "max level" : "next cost " + definition.costs().get(level);
            String prerequisites = definition.prerequisites().isEmpty() ? "" : "; requires " + definition.prerequisites();
            source.sendSuccess(() -> Component.literal(name + ": " + level + "/" + definition.maxLevel() + ", " + cost
                    + prerequisites + ". /morph biomass buy " + name + " " + ledger.revision()), false);
        }
        return 1;
    }
}
