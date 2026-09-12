package me.ichun.mods.morph.config;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Loader-independent admin commands; registration merges into the existing morph root. */
public final class MorphConfigCommands {
    private MorphConfigCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("morph")
                .then(Commands.literal("config").requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("status").executes(context -> {
                            var path = MorphConfiguration.file();
                            if (path == null) {
                                context.getSource().sendFailure(Component.literal("No Morph server configuration is active."));
                                return 0;
                            }
                            var policy = MorphPolicies.current();
                            context.getSource().sendSuccess(() -> Component.literal("Morph configuration revision "
                                    + policy.revision() + ", mode " + policy.mode() + ", duration " + policy.durationTicks()
                                    + " ticks, file " + path), false);
                            return 1;
                        }))
                        .then(Commands.literal("reload").executes(context -> {
                            var result = MorphConfiguration.reload();
                            if (!result.accepted()) {
                                context.getSource().sendFailure(Component.literal("Morph reload rejected; revision "
                                        + result.revision() + " retained: " + String.join("; ", result.errors())));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Morph configuration loaded, revision "
                                    + result.revision() + ". New actions use the updated policy."), true);
                            return 1;
                        }))));
    }
}
