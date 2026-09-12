package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphNamedLimbsTest {
    private static final class GolemSwim extends IronGolemRenderState implements MorphSwimState {
        float blend = 1;
        boolean fast;
        public float morph$swimBlend() { return blend; }
        public void morph$setSwimBlend(float value) { blend = value; }
        public boolean morph$fastSwimming() { return fast; }
        public void morph$setFastSwimming(boolean value) { fast = value; }
    }

    private static final class ParrotSwim extends ParrotRenderState implements MorphSwimState {
        float blend = 1;
        boolean fast;
        public float morph$swimBlend() { return blend; }
        public void morph$setSwimBlend(float value) { blend = value; }
        public boolean morph$fastSwimming() { return fast; }
        public void morph$setFastSwimming(boolean value) { fast = value; }
    }

    @Test void nativeGolemArmsPaddleAtBothSpeedsAndPreserveActionPoses() {
        var root = IronGolemModel.createBodyLayer().bakeRoot();
        var model = new IronGolemModel(root);
        var state = new GolemSwim();
        state.ageInTicks = 3F;
        for (boolean fast : new boolean[] {false, true}) {
            state.fast = fast;
            model.setupAnim(state);
            float nativeArm = root.getChild("right_arm").xRot;
            float nativeX = root.getChild("right_arm").x;
            MorphOtherSwimming.apply(model, state);
            assertNotEquals(nativeArm, root.getChild("right_arm").xRot);
            assertEquals(nativeX, root.getChild("right_arm").x);
        }
        state.attackTicksRemaining = 5F;
        model.setupAnim(state);
        float attacking = root.getChild("right_arm").xRot;
        MorphOtherSwimming.apply(model, state);
        assertEquals(attacking, root.getChild("right_arm").xRot);
        state.attackTicksRemaining = 0;
        state.offerFlowerTick = 5;
        assertFalse(MorphOtherSwimming.canPaddleArms(state));
        var warden = new WardenRenderState();
        warden.sonicBoomAnimationState.start(0);
        assertFalse(MorphOtherSwimming.canPaddleArms(warden));
    }

    @Test void nativeBirdWingsUseOppositeZStrokesAndRestoreDryPose() {
        var root = ParrotModel.createBodyLayer().bakeRoot();
        var model = new ParrotModel(root);
        var state = new ParrotSwim();
        state.pose = ParrotModel.Pose.STANDING;
        state.ageInTicks = 3F;
        float normal = 0;
        for (boolean fast : new boolean[] {false, true}) {
            state.fast = fast;
            model.setupAnim(state);
            float nativeWing = root.getChild("right_wing").zRot;
            MorphOtherSwimming.apply(model, state);
            float wing = root.getChild("right_wing").zRot;
            assertNotEquals(nativeWing, wing);
            assertEquals(-wing, root.getChild("left_wing").zRot, 0.00001F);
            if (!fast) normal = wing;
            else assertNotEquals(normal, wing);
        }
        state.blend = 0;
        model.setupAnim(state);
        float dryWing = root.getChild("right_wing").zRot;
        MorphOtherSwimming.apply(model, state);
        assertEquals(dryWing, root.getChild("right_wing").zRot);
    }
}
