package me.ichun.mods.morph.client;

import com.mojang.logging.LogUtils;
import me.ichun.mods.morph.client.equipment.MorphEquipmentRendering;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;

/** Vanilla-only extraction adapter. No live entities are retained in submitted render data. */
public final class MorphRenderSnapshots {
    private record AdapterKey(UUID player, String form) {}
    private static final Map<AdapterKey, LivingEntity> ADAPTERS = new HashMap<>();
    private static final Set<String> FAILED_FORMS = new HashSet<>();

    private MorphRenderSnapshots() {}

    public static void invalidate(UUID playerId) {
        ADAPTERS.keySet().removeIf(key -> key.player.equals(playerId));
    }

    public static void retainPlayers(Set<UUID> present) {
        ADAPTERS.keySet().removeIf(key -> !present.contains(key.player));
        MorphTransitions.retainPlayers(present);
    }

    public static void clear() {
        me.ichun.mods.morph.client.transition.MorphTransitionRenderer.clearFailures();
        ADAPTERS.clear();
        FAILED_FORMS.clear();
        MorphTransitions.clear();
    }

    public static LivingEntityRenderState extract(Avatar avatar, AvatarRenderState source, String formId) {
        if (formId == null || formId.isEmpty()) return null;
        me.ichun.mods.morph.client.nametag.MorphNameTags.apply(avatar.getUUID(), source);
        if (source.isSpectator || FAILED_FORMS.contains(formId)) return null;
        var id = Identifier.tryParse(formId);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        try {
            var key = new AdapterKey(avatar.getUUID(), formId);
            var adapter = ADAPTERS.get(key);
            if (adapter == null || adapter.getType() != type || adapter.level() != avatar.level()) {
                var entity = type.create(avatar.level(), EntitySpawnReason.LOAD);
                if (!(entity instanceof LivingEntity living) || living instanceof Avatar) return null;
                adapter = living;
                // 26.2 does not assign IDs in constructors. Renderers use the ID for item seeds.
                // This adapter is never inserted into a level; reuse its owner's stable render ID.
                adapter.setId(avatar.getId());
                ADAPTERS.put(key, adapter);
            }
            adapter.setPos(avatar.position());
            adapter.xOld = avatar.xOld;
            adapter.yOld = avatar.yOld;
            adapter.zOld = avatar.zOld;
            adapter.xo = avatar.xo;
            adapter.yo = avatar.yo;
            adapter.zo = avatar.zo;
            adapter.tickCount = avatar.tickCount;
            adapter.setYRot(avatar.getYRot());
            adapter.yRotO = avatar.yRotO;
            adapter.setXRot(avatar.getXRot());
            adapter.xRotO = avatar.xRotO;
            adapter.setInvisible(avatar.isInvisible());
            adapter.setDeltaMovement(avatar.getDeltaMovement());
            MorphEquipmentRendering.prepare(avatar, adapter);
            float partialTick = source.ageInTicks - avatar.tickCount;
            EntityRenderState extracted = createState(adapter, partialTick);
            if (!(extracted instanceof LivingEntityRenderState target)) return null;
            copyPlayerMotion(source, target);
            me.ichun.mods.morph.client.animation.MorphWitherHeads.apply(target);
            me.ichun.mods.morph.client.animation.MorphFlyingAnimation.apply(target,
                    avatar.onGround() && avatar.getDeltaMovement().lengthSqr() < 1.0E-7);
            MorphEquipmentRendering.finish(avatar, source, target);
            me.ichun.mods.morph.client.animation.MorphSwimming.extract(avatar, source, target, formId);
            return target;
        } catch (RuntimeException failure) {
            // Unsupported renderers should leave the real player visible and log only once per session.
            FAILED_FORMS.add(formId);
            LogUtils.getLogger().warn("Morph cannot extract renderer for {}; using player appearance", formId, failure);
            return null;
        }
    }

    private static <T extends LivingEntity> EntityRenderState createState(T entity, float partialTick) {
        EntityRenderer<? super T, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer.createRenderState(entity, partialTick);
    }

    private static void copyPlayerMotion(AvatarRenderState source, LivingEntityRenderState target) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.ageInTicks = source.ageInTicks;
        target.distanceToCameraSq = source.distanceToCameraSq;
        target.lightCoords = source.lightCoords;
        target.outlineColor = source.outlineColor;
        target.isInvisible = source.isInvisible;
        target.isInvisibleToPlayer = source.isInvisibleToPlayer;
        target.isDiscrete = source.isDiscrete;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.walkAnimationPos = source.walkAnimationPos;
        target.walkAnimationSpeed = source.walkAnimationSpeed;
        target.deathTime = source.deathTime;
        target.hasRedOverlay = source.hasRedOverlay;
        target.isFullyFrozen = source.isFullyFrozen;
        target.isInWater = source.isInWater;
        target.isUpsideDown = source.isUpsideDown;
        target.nameTag = source.nameTag;
        target.scoreText = source.scoreText;
        target.nameTagAttachment = source.nameTagAttachment;
        target.leashStates = null;
    }
}
