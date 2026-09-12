package me.ichun.mods.morph.progression;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BiomassCommandsTest {
    @BeforeAll static void bootstrapNativeCommands() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        BiomassCommands.register(dispatcher, new BiomassCommands.Access() {
            public boolean enabled(ServerPlayer player) { throw new AssertionError("Parse must not mutate or inspect a player"); }
            public BiomassLedger ledger(ServerPlayer player) { throw new AssertionError("Parse must not read a ledger"); }
            public BiomassRuntime.Outcome purchase(ServerPlayer player, long revision, BiomassDefinitions.Upgrade upgrade) {
                throw new AssertionError("Parse must not purchase");
            }
        });
        return dispatcher;
    }

    @Test void spendingCommandsRequireAnExplicitBoundedRevision() {
        var dispatcher = dispatcher();
        for (String command : new String[]{"morph biomass", "morph biomass status",
                "morph biomass buy capacity 0", "morph biomass buy critical_capacity 9223372036854775807"}) {
            var parsed = dispatcher.parse(command, (CommandSourceStack) null);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
            assertTrue(parsed.getExceptions().isEmpty(), command);
        }
        for (String command : new String[]{"morph biomass buy capacity", "morph biomass buy capacity -1",
                "morph biomass buy capacity 9223372036854775808", "morph biomass buy unknown 1"}) {
            var parsed = dispatcher.parse(command, (CommandSourceStack) null);
            assertTrue(parsed.getReader().canRead() || parsed.getContext().getCommand() == null, command);
        }
    }
}
