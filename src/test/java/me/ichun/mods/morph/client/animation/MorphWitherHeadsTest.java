package me.ichun.mods.morph.client.animation;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.monster.wither.WitherBossModel;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MorphWitherHeadsTest {
    @Test
    void sideHeadsMatchCenterThroughTurnsAndPitchChanges() {
        var root = WitherBossModel.createBodyLayer(CubeDeformation.NONE).bakeRoot();
        var model = new WitherBossModel(root);
        var state = new WitherRenderState();
        for (float body : new float[]{0, 90, 180, -90, 179, -179, 720}) {
            for (float look : new float[]{-65, 0, 65}) {
                for (float pitch : new float[]{-80, 0, 80}) {
                    state.bodyRot = body;
                    state.yRot = look;
                    state.xRot = pitch;
                    MorphWitherHeads.apply(state);
                    model.setupAnim(state);
                    var center = root.getChild("center_head");
                    for (String name : new String[]{"right_head", "left_head"}) {
                        var side = root.getChild(name);
                        assertEquals(center.yRot, side.yRot, 0.00001F);
                        assertEquals(center.xRot, side.xRot, 0.00001F);
                    }
                }
            }
        }
    }
}
