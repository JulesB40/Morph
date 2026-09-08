package me.ichun.mods.morph.client.animation;

/** Marker carried only by detached morph render snapshots. */
public interface MorphSwimState {
    boolean morph$fastSwimming();
    void morph$setFastSwimming(boolean fast);
    float morph$swimBlend();
    void morph$setSwimBlend(float blend);
}
