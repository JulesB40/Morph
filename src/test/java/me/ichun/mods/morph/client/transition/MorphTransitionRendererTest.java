package me.ichun.mods.morph.client.transition;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MorphTransitionRendererTest {
    @Test void endpointsRetainTheVisibleMeshAndMissingQuadsCollapse() {
        var oldMesh = mesh(0, 4);
        var newMesh = mesh(10, 8);
        var start = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0, false);
        var end = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 1, false);
        assertEquals(8, start.size());
        for (int i = 0; i < 4; i++) assertEquals(i, start.get(i)[0], 0.00001F);
        for (int i = 4; i < 8; i++) assertEquals(1.5F, start.get(i)[0], 0.00001F);
        for (int i = 0; i < 8; i++) assertEquals(10 + i, end.get(i)[0], 0.00001F);
    }

    @Test void middleDeformsGeometryWithoutMutatingCapturedEndpoints() {
        var oldMesh = mesh(0, 4);
        var newMesh = mesh(10, 4);
        var middle = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0.5F, false);
        for (int i = 0; i < 4; i++) assertEquals(5 + i, middle.get(i)[0], 0.00001F);
        var startAgain = MorphTransitionRenderer.interpolate(oldMesh, newMesh, 0, false);
        assertEquals(0, startAgain.getFirst()[0], 0.00001F);
        assertEquals(1, middle.getFirst()[6], 0.00001F);
    }

    @Test void originalEasingStartsAndEndsSmoothly() {
        assertEquals(0, MorphTransitionRenderer.ease(0), 0.00001F);
        assertEquals(0.5F, MorphTransitionRenderer.ease(0.5F), 0.00001F);
        assertEquals(1, MorphTransitionRenderer.ease(1), 0.00001F);
        assertTrue(MorphTransitionRenderer.ease(0.01F) < 0.001F);
    }

    private static MorphTransitionRenderer.Mesh mesh(float x, int vertices) {
        var mesh = new MorphTransitionRenderer.Mesh();
        for (int i = 0; i < vertices; i++) mesh.addVertex(x + i, 0, 0).setNormal(0, 1, 0).setUv(0, 0);
        return mesh;
    }
}
