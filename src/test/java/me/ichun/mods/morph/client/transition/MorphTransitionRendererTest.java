package me.ichun.mods.morph.client.transition;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

class MorphTransitionRendererTest {
    @Test void endpointsRetainTheVisibleMeshAndMissingQuadsCollapse() {
        var oldMesh = mesh(0, 4);
        var newMesh = mesh(10, 8);
        var start = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0, false);
        var end = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 1, false);
        assertEquals(8, start.size());
        for (int i = 0; i < 4; i++) assertEquals(i, start.component(i, 0), 0.00001F);
        for (int i = 4; i < 8; i++) assertEquals(1.5F, start.component(i, 0), 0.00001F);
        for (int i = 0; i < 8; i++) assertEquals(10 + i, end.component(i, 0), 0.00001F);
    }

    @Test void middleDeformsGeometryWithoutMutatingCapturedEndpoints() {
        var oldMesh = mesh(0, 4);
        var newMesh = mesh(10, 4);
        var middle = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0.5F, false);
        for (int i = 0; i < 4; i++) assertEquals(5 + i, middle.component(i, 0), 0.00001F);
        var startAgain = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0, false);
        assertEquals(0, startAgain.component(0, 0), 0.00001F);
        assertEquals(1, middle.component(0, 6), 0.00001F);
    }

    @Test void originalEasingStartsAndEndsSmoothly() {
        assertEquals(0, MorphTransitionRenderer.ease(0), 0.00001F);
        assertEquals(0.5F, MorphTransitionRenderer.ease(0.5F), 0.00001F);
        assertEquals(1, MorphTransitionRenderer.ease(1), 0.00001F);
        assertTrue(MorphTransitionRenderer.ease(0.01F) < 0.001F);
    }

    @Test void rejectsOversizedAndInvalidGeometry() {
        var mesh = new MorphTransitionRenderer.Mesh();
        assertThrows(IllegalArgumentException.class, () -> mesh.addVertex(Float.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> mesh.addVertex(Float.POSITIVE_INFINITY, 0, 0));
        for (int i = 0; i < MorphTransitionRenderer.Mesh.MAX_VERTICES; i++) mesh.addVertex(0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> mesh.addVertex(0, 0, 0));
    }

    @Test void deferredGeometryDoesNotChangeWhenCaptureIsReused() {
        var source = mesh(0, 4);
        var output = MorphTransitionRenderer.interpolate(source, source, 0.5F, true);
        source.addVertex(900, 900, 900).setNormal(1, 0, 0);
        assertEquals(4, output.size());
        assertEquals(0.001F, output.component(0, 1), 0.00001F);
    }

    @Test void packedGeometryMatchesOriginalForUnequalPosedMeshes() {
        var random = new java.util.Random(42);
        for (int fromCount : new int[] {4, 8, 12}) for (int toCount : new int[] {4, 8, 12}) {
            var from = new MorphTransitionRenderer.Mesh();
            var to = new MorphTransitionRenderer.Mesh();
            List<float[]> oldFrom = new ArrayList<>(), oldTo = new ArrayList<>();
            for (int side = 0; side < 2; side++) {
                var packed = side == 0 ? from : to;
                var legacy = side == 0 ? oldFrom : oldTo;
                for (int i = 0; i < (side == 0 ? fromCount : toCount); i++) {
                    float[] vertex = new float[8];
                    for (int j = 0; j < 8; j++) vertex[j] = random.nextFloat() * 2 - 1;
                    legacy.add(vertex);
                    packed.addVertex(vertex[0], vertex[1], vertex[2]).setUv(vertex[3], vertex[4])
                        .setNormal(vertex[5], vertex[6], vertex[7]);
                }
            }
            for (float progress : new float[] {0, 0.2F, 0.5F, 0.9F, 1}) for (boolean expand : new boolean[] {false, true}) {
                var expected = legacyInterpolate(oldFrom, oldTo, progress, expand);
                var actual = MorphTransitionRenderer.interpolate(from, to, progress, expand);
                assertEquals(expected.size(), actual.size());
                for (int i = 0; i < actual.size(); i++) for (int j = 0; j < 8; j++)
                    assertEquals(expected.get(i)[j], actual.component(i, j), 0.00001F);
            }
        }
    }

    private static volatile Object benchmarkSink;

    /** Compares against the old per-vertex-array algorithm; allocation, unlike timing, is deterministic. */
    @Test void packedInterpolationReducesMeasuredAllocation() {
        var bean = ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean instanceof com.sun.management.ThreadMXBean);
        var allocation = (com.sun.management.ThreadMXBean) bean;
        org.junit.jupiter.api.Assumptions.assumeTrue(allocation.isThreadAllocatedMemorySupported());
        allocation.setThreadAllocatedMemoryEnabled(true);
        int count = 2048;
        var from = mesh(0, count);
        var to = mesh(10, count);
        var oldFrom = legacyMesh(0, count);
        var oldTo = legacyMesh(10, count);
        Runnable packed = () -> benchmarkSink = MorphTransitionRenderer.interpolate(from, to, 0.5F, false);
        Runnable legacy = () -> benchmarkSink = legacyInterpolate(oldFrom, oldTo, 0.5F, false);
        for (int i = 0; i < 1000; i++) { packed.run(); legacy.run(); }
        int repetitions = 200, rounds = 7;
        long thread = Thread.currentThread().threadId();
        long[] packedTimes = new long[rounds], legacyTimes = new long[rounds];
        long packedBytes = 0, legacyBytes = 0;
        // Alternate ordering so foreground work/JIT/GC does not consistently favor one path.
        for (int round = 0; round < rounds; round++) {
            for (int order = 0; order < 2; order++) {
                boolean usePacked = ((round + order) & 1) == 0;
                Runnable action = usePacked ? packed : legacy;
                long start = allocation.getThreadAllocatedBytes(thread), time = System.nanoTime();
                for (int i = 0; i < repetitions; i++) action.run();
                long elapsed = System.nanoTime() - time;
                long allocated = allocation.getThreadAllocatedBytes(thread) - start;
                if (usePacked) { packedTimes[round] = elapsed; packedBytes += allocated; }
                else { legacyTimes[round] = elapsed; legacyBytes += allocated; }
            }
        }
        java.util.Arrays.sort(packedTimes); java.util.Arrays.sort(legacyTimes);
        System.out.printf("Morph interpolation %,d vertices: packed %,d B/frame %.1f us/frame; legacy %,d B/frame %.1f us/frame (median of %d alternating rounds)%n",
            count, packedBytes / (repetitions * rounds), packedTimes[rounds / 2] / (repetitions * 1000.0),
            legacyBytes / (repetitions * rounds), legacyTimes[rounds / 2] / (repetitions * 1000.0), rounds);
        assertTrue(packedBytes * 1.25 < legacyBytes, "Packed geometry should materially reduce allocation");
        benchmarkSink = null;
    }

    private static List<float[]> legacyMesh(float x, int count) {
        List<float[]> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new float[] {x + i, 0, 0, 0, 0, 0, 1, 0});
        return result;
    }

    private static List<float[]> legacyInterpolate(List<float[]> from, List<float[]> to, float progress, boolean expandSkin) {
        int count = Math.max(from.size(), to.size());
        float[] fromCenter = legacyCenter(from), toCenter = legacyCenter(to);
        List<float[]> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float[] a = i < from.size() ? from.get(i) : fromCenter;
            float[] b = i < to.size() ? to.get(i) : toCenter;
            float[] vertex = new float[8];
            for (int j = 0; j < 8; j++) vertex[j] = a[j] + (b[j] - a[j]) * progress;
            float length = (float) Math.sqrt(vertex[5] * vertex[5] + vertex[6] * vertex[6] + vertex[7] * vertex[7]);
            if (length > 0.00001F) { vertex[5] /= length; vertex[6] /= length; vertex[7] /= length; }
            else vertex[6] = 1;
            if (expandSkin) {
                vertex[0] += vertex[5] * 0.001F;
                vertex[1] += vertex[6] * 0.001F;
                vertex[2] += vertex[7] * 0.001F;
            }
            result.add(vertex);
        }
        return List.copyOf(result);
    }

    private static float[] legacyCenter(List<float[]> mesh) {
        float[] center = new float[8];
        for (float[] vertex : mesh) {
            center[0] += vertex[0]; center[1] += vertex[1]; center[2] += vertex[2];
        }
        for (int i = 0; i < 3; i++) center[i] /= mesh.size();
        center[6] = 1;
        return center;
    }

    @Test void duplicateArmorPassesSkipOnlyIdenticalModelStateAndTransform() {
        var mesh = new MorphTransitionRenderer.Mesh();
        Object model = new Object(), state = new Object();
        var pose = new org.joml.Matrix4f();
        var normal = new org.joml.Matrix3f();
        assertFalse(mesh.duplicatePass(model, state, pose, normal));
        assertTrue(mesh.duplicatePass(model, state, pose, normal));
        pose.translate(0, 1, 0);
        assertFalse(mesh.duplicatePass(model, state, pose, normal));
        assertTrue(mesh.duplicatePass(model, state, pose, normal));
        assertFalse(mesh.duplicatePass(new Object(), state, pose, normal));
        assertFalse(mesh.duplicatePass(model, new Object(), pose, normal));
        normal.scale(2);
        assertFalse(mesh.duplicatePass(model, state, pose, normal));
    }

    private static MorphTransitionRenderer.Mesh mesh(float x, int vertices) {
        var mesh = new MorphTransitionRenderer.Mesh();
        for (int i = 0; i < vertices; i++) mesh.addVertex(x + i, 0, 0).setNormal(0, 1, 0).setUv(0, 0);
        return mesh;
    }
}
