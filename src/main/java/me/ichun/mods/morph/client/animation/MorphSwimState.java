package me.ichun.mods.morph.client.animation;

/** Additional animation state; only detached morph snapshots set the adapter marker. */
public interface MorphSwimState {
    default boolean morph$isMorphAdapter() { return false; }
    default void morph$setMorphAdapter(boolean adapter) {}
    boolean morph$fastSwimming();
    void morph$setFastSwimming(boolean fast);
    float morph$swimBlend();
    void morph$setSwimBlend(float blend);
}
