package me.ichun.mods.morph.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-synchronized selection, accessed on the client game thread only. */
public final class ClientMorphState {
    private static final Map<UUID, String> FORMS = new HashMap<>();

    private ClientMorphState() {}

    public static void update(UUID playerId, String formId) {
        MorphTransitions.reconcile(playerId, formId);
        if (formId == null || formId.isEmpty() || formId.equals("minecraft:player")) {
            FORMS.remove(playerId);
        } else {
            FORMS.put(playerId, formId);
        }
        MorphRenderSnapshots.invalidate(playerId);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            var player = level.getPlayerByUUID(playerId);
            if (player != null) me.ichun.mods.morph.shape.ShapeHooks.refresh(player);
        }
    }

    public static String lookup(UUID playerId) {
        return FORMS.get(playerId);
    }

    public static void clear() {
        FORMS.clear();
        me.ichun.mods.morph.client.nametag.MorphNameTags.clear();
        MorphRenderSnapshots.clear();
    }
}
