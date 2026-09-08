package me.ichun.mods.morph.ability.mixin;
import me.ichun.mods.morph.ability.MorphInteractions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(Mob.class)
public abstract class MobTargetMixin {
    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    protected net.minecraft.world.entity.ai.goal.GoalSelector goalSelector;
    @org.spongepowered.asm.mixin.Unique private boolean morph$fearInstalled;
    @org.spongepowered.asm.mixin.injection.Inject(method = "tick", at = @At("HEAD"))
    private void morph$installFear(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Mob mob = (Mob)(Object)this;
        if (!morph$fearInstalled && !mob.level().isClientSide()) {
            morph$fearInstalled = true;
            if (mob instanceof net.minecraft.world.entity.PathfinderMob pathfinder)
                MorphInteractions.installFearGoal(pathfinder, goalSelector);
        }
    }

    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private LivingEntity morph$disguiseTarget(LivingEntity target) {
        return MorphInteractions.shouldIgnoreTarget((Mob)(Object)this, target) ? null : target;
    }
}
