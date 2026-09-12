package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.animal.bee.AdultBeeModel;
import net.minecraft.client.model.animal.bee.BabyBeeModel;
import net.minecraft.client.model.animal.bee.BeeModel;
import net.minecraft.client.renderer.entity.state.BeeRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphBeeSwimmingTest {
    private static final class BeeSwim extends BeeRenderState implements MorphSwimState {
        float blend = 1;
        boolean fast;
        public float morph$swimBlend() { return blend; }
        public void morph$setSwimBlend(float value) { blend = value; }
        public boolean morph$fastSwimming() { return fast; }
        public void morph$setFastSwimming(boolean value) { fast = value; }
    }

    @Test void bothNativeAgeRigsPaddleAllThreeJoinedPairsAndKeepFlightWings() {
        BeeModel[] models = {new AdultBeeModel(AdultBeeModel.createBodyLayer().bakeRoot()),
            new BabyBeeModel(BabyBeeModel.createBodyLayer().bakeRoot())};
        for (var model : models) {
            var bone = model.root().getChild("bone");
            var state = new BeeSwim();
            state.ageInTicks = 3F;
            for (boolean fast : new boolean[] {false, true}) {
                state.fast = fast;
                for (var name : new String[] {"front_legs", "middle_legs", "back_legs"}) {
                    model.setupAnim(state);
                    float original = bone.getChild(name).xRot;
                    float originalWing = bone.getChild("right_wing").zRot;
                    MorphOtherSwimming.apply(model, state);
                    assertNotEquals(original, bone.getChild(name).xRot, name);
                    assertEquals(originalWing, bone.getChild("right_wing").zRot);
                }
            }
            state.blend = 0;
            model.setupAnim(state);
            float dry = bone.getChild("middle_legs").xRot;
            MorphOtherSwimming.apply(model, state);
            assertEquals(dry, bone.getChild("middle_legs").xRot);
        }
    }
}
