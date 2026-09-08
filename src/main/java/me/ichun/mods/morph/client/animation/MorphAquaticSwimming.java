package me.ichun.mods.morph.client.animation;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.Avatar;

/** Drives vanilla aquatic animation inputs which detached extraction adapters cannot tick. */
public final class MorphAquaticSwimming {
    private static final Map<Avatar, Clock> CLOCKS = new WeakHashMap<>();
    private static final class Clock { float age; float phase; }
    private MorphAquaticSwimming() {}

    public static void extract(Avatar avatar, AvatarRenderState source, LivingEntityRenderState target) {
        if (!MorphOtherSwimming.hasNativeAquaticPose(target) || source.deathTime > 0) return;
        boolean water = source.isInWater;
        boolean moving = avatar.getDeltaMovement().lengthSqr() > 1.0E-5;
        boolean grounded = avatar.onGround();
        boolean fast = water && ((MorphSwimState) target).morph$fastSwimming();
        float speed = Math.min(1F, (float) avatar.getDeltaMovement().length() * 8F);
        Clock clock = CLOCKS.computeIfAbsent(avatar, ignored -> {
            Clock created = new Clock(); created.age = source.ageInTicks; created.phase = source.ageInTicks; return created;
        });
        clock.phase = advance(clock.phase, source.ageInTicks - clock.age, fast);
        clock.age = source.ageInTicks;
        if (water) {
            target.ageInTicks = clock.phase;
            // Player walk animation can be idle during a horizontal swimming pose. Native
            // turtle/frog/nautilus strokes must follow actual movement in all three axes.
            target.walkAnimationPos = clock.phase * 0.6F;
            target.walkAnimationSpeed = speed;
        }
        if (target instanceof SquidRenderState squid && water) {
            squid.tentacleAngle = tentacleAngle(clock.phase);
            squid.xBodyRot = moving ? -90F - Math.clamp(source.xRot, -85F, 85F) : 0F;
            squid.zBodyRot = 0F;
        }
        if (target instanceof GuardianRenderState guardian && water) {
            guardian.tailAnimation = clock.phase * (moving ? 0.9F : 0.12F);
            guardian.spikesAnimation = moving ? 0F : 1F;
            // Attack target, beam time and gaze remain the independently extracted values.
        }
        if (target instanceof DolphinRenderState dolphin) dolphin.isMoving = moving;
        if (target instanceof TurtleRenderState turtle) turtle.isOnLand = !water && grounded;
        if (target instanceof FrogRenderState frog) {
            frog.isSwimming = water;
            frog.swimIdleAnimationState.animateWhen(water && !moving, 0);
            // No fabricated croak, tongue attack, or jump animation.
        }
        if (target instanceof AxolotlRenderState axolotl) {
            axolotl.inWaterFactor = water ? 1F : 0F;
            axolotl.onGroundFactor = !water && grounded ? 1F : 0F;
            axolotl.movingFactor = moving ? 1F : 0F;
            axolotl.swimAnimation.animateWhen(water && moving && !grounded, 0);
            axolotl.walkAnimationState.animateWhen(!water && moving && grounded, 0);
            axolotl.walkUnderWaterAnimationState.animateWhen(water && moving && grounded, 0);
            axolotl.idleUnderWaterAnimationState.animateWhen(water && !moving && !grounded, 0);
            axolotl.idleUnderWaterOnGroundAnimationState.animateWhen(water && !moving && grounded, 0);
            axolotl.idleOnGroundAnimationState.animateWhen(!water && !moving && grounded, 0);
            // Playing-dead state is not invented from player swimming input.
        }
    }

    static float advance(float phase, float elapsed, boolean fast) {
        // Two form snapshots can be extracted for the same avatar at the same frame.
        return phase + Math.clamp(elapsed, 0F, 5F) * (fast ? 1.7F : 1F);
    }

    static float tentacleAngle(float phase) {
        return (float) ((1.0 - Math.cos(phase * 0.22)) * Math.PI * 0.25);
    }
}
