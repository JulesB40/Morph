package me.ichun.mods.morph.client;

import com.mojang.logging.LogUtils;
import me.ichun.mods.morph.client.equipment.MorphEquipmentRendering;
import me.ichun.mods.morph.model.CollectionEntry;
import me.ichun.mods.morph.model.FormDescriptor;
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
    private record RenderIdentity(String entryId, long revision, long resources) {}
    private record AdapterKey(UUID player, RenderIdentity identity) {}
    private static final Map<AdapterKey, LivingEntity> ADAPTERS = new HashMap<>();
    private static final Set<RenderIdentity> FAILED_FORMS = new HashSet<>();
    private static long resourceRevision;

    private MorphRenderSnapshots() {}

    public static void invalidate(UUID playerId) {
        ADAPTERS.keySet().removeIf(key -> key.player.equals(playerId));
    }

    public static void retainPlayers(Set<UUID> present) {
        ADAPTERS.keySet().removeIf(key -> !present.contains(key.player));
        MorphTransitions.retainPlayers(present);
    }

    public static void clear() {
        reload(null);
        MorphTransitions.clear();
    }

    public static void reload(net.minecraft.server.packs.resources.ResourceManager manager) {
        resourceRevision++;
        me.ichun.mods.morph.client.transition.MorphTransitionRenderer.clearFailures();
        ADAPTERS.clear();
        FAILED_FORMS.clear();
    }

    public static EntityRenderState extract(Avatar avatar, AvatarRenderState source, String formId) {
        return extract(avatar, source, formId, null, new RenderIdentity("species:" + formId, 0, resourceRevision));
    }

    public static EntityRenderState extract(Avatar avatar, AvatarRenderState source, CollectionEntry entry) {
        if (entry == null) return null;
        return extract(avatar, source, entry.descriptor().species(), entry.descriptor(),
                new RenderIdentity(entry.id().value(), entry.revision(), resourceRevision));
    }

    public static EntityRenderState extractCurrent(Avatar avatar, AvatarRenderState source, String formId) {
        var entry = DescriptorState.active(avatar.getUUID());
        return entry != null && entry.descriptor().species().equals(formId)
                ? extract(avatar, source, entry) : extract(avatar, source, formId);
    }

    private static EntityRenderState extract(Avatar avatar, AvatarRenderState source, String formId,
            FormDescriptor descriptor, RenderIdentity identity) {
        if (formId == null || formId.isEmpty()) return null;
        me.ichun.mods.morph.client.nametag.MorphNameTags.apply(avatar.getUUID(), source);
        if (source.isSpectator || FAILED_FORMS.contains(identity)) return null;
        var id = Identifier.tryParse(formId);
        if (id == null || !id.getNamespace().equals("minecraft")) return null;
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return null;
        try {
            var key = new AdapterKey(avatar.getUUID(), identity);
            var adapter = ADAPTERS.get(key);
            if (adapter == null || adapter.getType() != type || adapter.level() != avatar.level()) {
                var entity = type.create(avatar.level(), EntitySpawnReason.LOAD);
                if (!(entity instanceof LivingEntity living) || living instanceof Avatar) return null;
                adapter = living;
                // 26.2 does not assign IDs in constructors. Renderers use the ID for item seeds.
                // This adapter is never inserted into a level; reuse its owner's stable render ID.
                adapter.setId(avatar.getId());
                if (descriptor != null && !me.ichun.mods.morph.server.FormCapture.applyVariant(adapter, descriptor)) return null;
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
            MorphEquipmentRendering.prepare(avatar, adapter, descriptor);
            float partialTick = source.ageInTicks - avatar.tickCount;
            EntityRenderState extracted = createState(adapter, partialTick);
            copyCommonState(source, extracted);
            if (extracted instanceof net.minecraft.client.renderer.entity.state.EnderDragonRenderState dragon) {
                dragon.deathTime = source.deathTime;
                dragon.hasRedOverlay = source.hasRedOverlay;
                // Detached dragons never tick their flight history. Supply the player's heading
                // without running dragon AI or borrowing the native boss's world state.
                for (int i = 0; i < net.minecraft.world.entity.boss.enderdragon.DragonFlightHistory.LENGTH; i++)
                    dragon.flightHistory.record(source.y, source.bodyRot);
            }
            if (!(extracted instanceof LivingEntityRenderState target)) return extracted;
            copyPlayerMotion(source, target);
            if (descriptor == null || descriptor.customName() == null) target.isUpsideDown = source.isUpsideDown;
            me.ichun.mods.morph.client.animation.MorphWitherHeads.apply(target);
            if (target instanceof net.minecraft.client.renderer.entity.state.WitherRenderState wither)
                wither.isPowered = avatar.getHealth() <= avatar.getMaxHealth() * .5F;
            me.ichun.mods.morph.client.animation.MorphFlyingAnimation.apply(target, avatar.onGround(),
                    avatar.onGround() && avatar.getDeltaMovement().lengthSqr() < 1.0E-7);
            MorphEquipmentRendering.finish(avatar, source, target);
            me.ichun.mods.morph.client.animation.MorphSwimming.extract(avatar, source, target, formId);
            return target;
        } catch (RuntimeException failure) {
            // Suppress repeated failures for this descriptor until its revision or resources change.
            FAILED_FORMS.add(identity);
            LogUtils.getLogger().warn("Morph cannot extract renderer for {}; using player appearance", formId, failure);
            return null;
        }
    }

    private static <T extends LivingEntity> EntityRenderState createState(T entity, float partialTick) {
        EntityRenderer<? super T, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer.createRenderState(entity, partialTick);
    }

    private static void copyCommonState(AvatarRenderState source, EntityRenderState target) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.ageInTicks = source.ageInTicks;
        target.distanceToCameraSq = source.distanceToCameraSq;
        target.lightCoords = source.lightCoords;
        target.outlineColor = source.outlineColor;
        target.isInvisible = source.isInvisible;
        target.isDiscrete = source.isDiscrete;
        target.nameTag = source.nameTag;
        target.scoreText = source.scoreText;
        target.nameTagAttachment = source.nameTagAttachment;
        target.leashStates = null;
    }

    private static void copyPlayerMotion(AvatarRenderState source, LivingEntityRenderState target) {
        target.isInvisibleToPlayer = source.isInvisibleToPlayer;
        target.bodyRot = source.bodyRot;
        target.yRot = source.yRot;
        target.xRot = source.xRot;
        target.walkAnimationPos = source.walkAnimationPos;
        target.walkAnimationSpeed = source.walkAnimationSpeed;
        target.deathTime = source.deathTime;
        target.hasRedOverlay = source.hasRedOverlay;
        target.isFullyFrozen = source.isFullyFrozen;
        target.isInWater = source.isInWater;
    }
}
