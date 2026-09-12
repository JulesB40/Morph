package me.ichun.mods.morph.lab;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.ichun.mods.morph.shape.MorphDimensions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/** Independent native pose/attachment oracle. Adapters are never added to or ticked in a world. */
public final class NativePoseProbes {
    private NativePoseProbes() {}

    public static void verify(GameTestHelper helper) {
        List<Map<String, Object>> failures = new ArrayList<>();
        int mobs = 0, poses = 0;
        EntityDimensions playerFallback = EntityDimensions.scalable(.6F, 1.8F).withEyeHeight(1.62F);
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals("minecraft") || !DefaultAttributes.hasSupplier(type)) continue;
            if (!(type.create(helper.getLevel(), EntitySpawnReason.LOAD) instanceof LivingEntity nativeMob)
                    || nativeMob instanceof Avatar) continue;
            mobs++;
            for (var pose : Pose.values()) {
                EntityDimensions expected = nativeMob.getDimensions(pose);
                EntityDimensions actual = MorphDimensions.forPose(helper.getLevel(), id.toString(), pose, playerFallback);
                boolean matches = close(expected.width(), actual.width()) && close(expected.height(), actual.height())
                        && close(expected.eyeHeight(), actual.eyeHeight());
                for (var attachment : EntityAttachment.values()) {
                    for (int index = 0; index < 8; index++) {
                        var a = expected.attachments().getNullable(attachment, index, 0);
                        var b = actual.attachments().getNullable(attachment, index, 0);
                        matches &= a == null ? b == null : b != null && a.distanceToSqr(b) < 1.0E-10;
                    }
                }
                poses++;
                if (!matches) failures.add(Map.of("form", id.toString(), "pose", pose.name(),
                        "native", dimensions(expected), "morph", dimensions(actual)));
            }
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", "morph-lab.native-poses.v1");
        evidence.put("scenario", "native_pose_dimensions");
        evidence.put("mob_count", mobs);
        evidence.put("pose_cases", poses);
        evidence.put("mismatches", failures);
        evidence.put("status", mobs >= 85 && failures.isEmpty() ? "pass" : "fail");
        evidence.put("scope", "default LOAD entity dimensions and first eight points of each attachment type; not riding gameplay");
        String json = new Gson().toJson(evidence);
        String output = System.getProperty("morph.lab.events");
        if (output != null) {
            try {
                Path path = Path.of(output);
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, json + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (java.io.IOException error) { throw new IllegalStateException("Cannot record pose evidence", error); }
        }
        System.out.println("MORPH_LAB_NATIVE_POSES mobs=" + mobs + " poses=" + poses + " mismatches=" + failures.size());
        helper.assertTrue(mobs >= 85, "Native pose fixture unexpectedly skipped mobs: " + mobs);
        helper.assertTrue(failures.isEmpty(), "Native pose/attachment mismatches: " + failures.size()
                + (failures.isEmpty() ? "" : "; first=" + failures.getFirst()));
        helper.succeed();
    }

    private static boolean close(float a, float b) { return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) < .00001F; }

    private static Map<String, Object> dimensions(EntityDimensions dimensions) {
        var attachments = new LinkedHashMap<String, Object>();
        for (var attachment : EntityAttachment.values()) {
            var points = new ArrayList<List<Double>>();
            for (int index = 0; index < 8; index++) {
                var point = dimensions.attachments().getNullable(attachment, index, 0);
                if (point == null) break;
                points.add(List.of(point.x, point.y, point.z));
            }
            attachments.put(attachment.name(), points);
        }
        return Map.of("width", dimensions.width(), "height", dimensions.height(), "eye", dimensions.eyeHeight(),
                "attachments", attachments);
    }
}
