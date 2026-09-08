package me.ichun.mods.morph.ability.mixin;

import me.ichun.mods.morph.ability.MorphAbilities;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Do not serialize Morph's transient flight permission into vanilla player data. */
@Mixin(Player.class)
public abstract class PlayerAbilitiesSaveMixin {
    @Redirect(method = "addAdditionalSaveData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Abilities;pack()Lnet/minecraft/world/entity/player/Abilities$Packed;"))
    private Abilities.Packed morph$saveBaselineAbilities(Abilities abilities) {
        return MorphAbilities.packForSave((Player) (Object) this, abilities);
    }
}
