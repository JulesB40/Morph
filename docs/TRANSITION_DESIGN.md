# 26.2 transformation rendering audit

The legacy implementation uses a configurable visual sequence, defaulting to 100 ticks (five seconds): 12.5% covering the old body with the morph skin, 75% changing its geometry, then 12.5% uncovering the destination body. Its older `10 - 60 - 10` comment describes the phase ratio, not the configured default duration. Each phase uses cosine easing (`(1 - cos(pi * progress)) / 2`). Sources: `legacy/1.16.5/src/api/java/me/ichun/mods/morph/api/morph/MorphInfo.java`, legacy `ConfigServer.morphTime`, and `legacy/1.16.5/src/main/java/me/ichun/mods/morph/client/render/MorphRenderHandler.java`.

The original skin is `assets/morph/textures/skin/morphskin.png`. Legacy geometry pairing pads the smaller model with empty parts, equalizes box counts, then interpolates both part matrices and box dimensions. Particles alone do not reproduce this behavior.

## Verified 26.2 APIs

Inspected the generated `build/moddev/artifacts/minecraft-patched-26.2.0.82-sources.jar`.

- `SubmitNodeCollector extends OrderedSubmitNodeCollector`; its `order(int)` returns an ordered collector. Wrappers must also intercept this path.
- The abstract model entry point is `<S> void submitModel(Model<? super S> model, S state, PoseStack poses, RenderType type, int light, int overlay, int tintedColor, TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling)`. Default model and model-part overloads funnel through it.
- `Model.setupAnim(state)` and `Model.renderToBuffer(poses, consumer, light, overlay, color)` permit immediate capture of the submitted model's posed vertices. Capture produces immutable data suitable for the deferred submission architecture. Rendering it immediately also honors `ModelPart.visible` and `skipDraw`.
- `Model.root().visit(poses, visitor)` exposes a path, cube index, pose, and cube. `Cube.polygons`, `Polygon.vertices()/normal()` and `Vertex.worldX()/worldY()/worldZ()` are public. However, `visit` ignores visibility and `skipDraw`, making direct render capture safer.
- A capture `VertexConsumer` implements `addVertex`, both `setColor` overloads, `setUv`, `setUv1`, `setUv2`, `setNormal`, and `setLineWidth`.
- `submitCustomGeometry(poses, type, callback)` accepts immutable captured geometry. Its callback receives `PoseStack.Pose` and `VertexConsumer`. Apply the root transform exactly once: captured positions may already include the original submit pose.

## Recommended implementation

Capture the old and new renderer submissions synchronously, with model animation setup, including their vanilla scaling and orientation. During the initial/final skin phases submit the matching textured form plus its translucent morph-skin geometry. During the middle phase submit only opaque morph-skin geometry with eased vertex interpolation. Pair complete quads, padding unmatched geometry with degenerate quads at the opposite body's center. This restores visible shape change while avoiding dependence on iChunUtil internals; it is an approximation of the original part-and-box correspondence.

Keep render snapshots immutable and local to the submitted frame. Do not retain mutable vanilla model instances for deferred custom rendering. Suppress repeated name tags and unrelated feature submissions during capture, and keep fully invisible players invisible. Preserve vanilla fallback for unsupported renderers or empty captures. Verify human-to-small-mob, mob-to-mob, return-to-human, late tracking, rapid selection, and both loader clients.

## Limits

Vertex correspondence does not reproduce the legacy matrix/box algorithm exactly, especially for dissimilar mesh topology. Vanilla item/block/custom feature submissions need an explicit capture or omission policy. A black collector wrapper by itself can tint or fade bodies but cannot produce geometry deformation. First-person hands are a distinct rendering path requiring separate support if parity is desired.

## Packed capture and equipment safety (alpha.4)

The deferred custom draw owns one private packed float array (eight components per vertex). It retains no model, render state, capture consumer, or live pose stack. Interpolation visits shared components contiguously, then normalizes normals and applies the small fade shell. Body-center scans run only for unmatched quads. Regression tests compare every component against the original implementation across unequal meshes, endpoint/middle progress, varied normals/UVs, and shell expansion.

The capture collector proxy is reused per rendering thread. The two fade phases capture only the visible endpoint, and exact progress zero/one uses the normal renderer without mesh capture. Armor remains supported through vanilla model submissions. Consecutive texture-only passes for the same model/state/pose (dye, foil, trim) contribute their geometry once, preventing unnecessary duplicated black surfaces. Pose and normal matrices used for this comparison are copied, not retained from the caller. Held-item, block-item and unrelated custom-geometry submissions are intentionally omitted during the fully black middle; normal equipment rendering remains active at the textured endpoints and after the transition.

Each capture is limited to 32,768 vertices and rejects nonfinite or unreasonable coordinates and incomplete quads. Failures quarantine only the failing entity type, preserving unrelated forms and the human endpoint. If its normal fallback also throws, subsequent fallback attempts are suppressed. Resource reload clears these failure caches so corrected resources can recover. These guards cover synchronous capture/submission failures; they cannot guarantee compatibility with every external renderer or GPU driver.
