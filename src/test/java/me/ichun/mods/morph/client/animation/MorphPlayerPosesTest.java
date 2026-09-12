package me.ichun.mods.morph.client.animation;

import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphPlayerPosesTest {
    @Test void transfersAndClearsPlayerPosesWithoutLosingCapturedAge() {
        var player = new AvatarRenderState();
        var target = new IllagerRenderState();
        target.isBaby = true;
        target.ageScale = 0.5F;
        player.pose = Pose.SLEEPING;
        player.bedOrientation = Direction.WEST;
        player.isCrouching = true;
        player.isPassenger = true;
        player.isFallFlying = true;
        player.isAutoSpinAttack = true;
        player.speedValue = 3F;
        MorphPlayerPoses.apply(player, target);
        assertEquals(Pose.SLEEPING, target.pose);
        assertEquals(Direction.WEST, target.bedOrientation);
        assertTrue(target.isCrouching);
        assertTrue(target.isPassenger);
        assertTrue(target.isRiding);
        assertTrue(target.isFallFlying);
        assertTrue(target.isAutoSpinAttack);
        assertEquals(3F, target.speedValue);
        assertTrue(target.isBaby);
        assertEquals(0.5F, target.ageScale);
        MorphPlayerPoses.apply(new AvatarRenderState(), target);
        assertEquals(Pose.STANDING, target.pose);
        assertNull(target.bedOrientation);
        assertFalse(target.isCrouching);
        assertFalse(target.isPassenger);
        assertFalse(target.isRiding);
        assertFalse(target.isFallFlying);
        assertFalse(target.isAutoSpinAttack);
        assertEquals(1F, target.speedValue);
    }
}
