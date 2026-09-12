package me.ichun.mods.morph.client.transition;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MorphRenderFallbackTest {
    @AfterEach void clearFailures() { MorphTransitionRenderer.clearFailures(); }

    @Test void failedSubmissionLeavesAvatarAndRestoresRecursionGuard() {
        var poses = new BrokenSubmissionPose();
        assertFalse(MorphTransitionRenderer.renderSnapshot(new EntityRenderState(), poses, null, null));
        assertEquals(1, poses.reads);
        assertFalse(MorphTransitionRenderer.isRendering());
    }

    @Test void failedTransitionCaptureAndFailedDestinationLeaveAvatar() {
        var poses = new BrokenSubmissionPose();
        var state = new EntityRenderState();
        assertFalse(MorphTransitionRenderer.render(new MorphTransitionRenderer.Frame(state, state, .5F), poses, null, null));
        assertEquals(2, poses.reads, "Capture and destination fallback must both have been attempted");
        assertFalse(MorphTransitionRenderer.isRendering());
    }

    @Test void invisibleNonLivingStateKeepsVanillaInvisibilityWithoutSubmittingDragon() {
        var state = new EntityRenderState();
        state.isInvisible = true;
        var poses = new BrokenSubmissionPose();
        assertFalse(MorphTransitionRenderer.renderSnapshot(state, poses, null, null));
        assertEquals(0, poses.reads);
    }

    /** Fails the actual submission setup, before a Minecraft singleton or GPU is needed. */
    private static final class BrokenSubmissionPose extends PoseStack {
        int reads;
        @Override public Pose last() {
            reads++;
            throw new IllegalStateException("Injected submission setup failure");
        }
    }
}
