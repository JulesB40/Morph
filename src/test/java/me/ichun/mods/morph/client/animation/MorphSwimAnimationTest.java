package me.ichun.mods.morph.client.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphSwimAnimationTest {
    private String clip(String length, String rotation) {
        return "{\"animations\":{\"animation.morph.quadruped_swim\":{\"animation_length\":" + length
            + ",\"bones\":{\"right_front_leg\":{\"rotation\":" + rotation + "}}}}}";
    }
    @Test void loopSeamReturnsToFirstKeyframe() {
        assertEquals(0F, MorphSwimAnimation.loopTime(0F, 1F));
        assertEquals(0F, MorphSwimAnimation.loopTime(20F, 1F));
        assertEquals(.25F, MorphSwimAnimation.loopTime(25F, 1F));
        assertEquals(0F, MorphSwimAnimation.loopTime(13F, .65F), .00001F);
    }
    @Test void shippedAnimationParses() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/morph/animations/swim.animation.json")) {
            assertNotNull(stream);
            String json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(1F, MorphSwimAnimation.parse(json).length());
            assertEquals(.65F, MorphSwimAnimation.parse(json, "animation.morph.quadruped_fast_swim").length());
        }
    }
    @Test void zeroBlendDoesNotLoadAssetsOrTouchModel() {
        assertDoesNotThrow(() -> MorphSwimAnimation.apply(null, 10F, 0F));
    }
    @Test void acceptsBlockbenchRotationKeyframes() {
        assertDoesNotThrow(() -> MorphSwimAnimation.parse(clip("1", "{\"0\":[35,0,0],\"0.5\":{\"post\":[-35,0,0]},\"1\":[35,0,0]}")));
    }
    @Test void rejectsZeroDurationAndOutOfRangeTime() {
        assertThrows(IllegalArgumentException.class, () -> MorphSwimAnimation.parse(clip("0", "[0,0,0]")));
        assertThrows(IllegalArgumentException.class, () -> MorphSwimAnimation.parse(clip("1", "{\"2\":[0,0,0]}")));
    }
    @Test void rejectsNonFiniteAndExcessiveRotation() {
        assertThrows(IllegalArgumentException.class, () -> MorphSwimAnimation.parse(clip("1", "[\"NaN\",0,0]")));
        assertThrows(IllegalArgumentException.class, () -> MorphSwimAnimation.parse(clip("1", "[10000,0,0]")));
    }
}
