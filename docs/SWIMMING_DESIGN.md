# Swimming animation integration (Minecraft 26.2)

Verified against `build/moddev/artifacts/minecraft-patched-26.2.0.82-sources.jar`. These are vanilla API observations and implementation recommendations, not a claim that all proposed hooks are implemented.

## Snapshot fields

`HumanoidRenderState` has public `float swimAmount` and `boolean isVisuallySwimming`, `isCrouching`, `isFallFlying`, `isPassenger`, `isUsingItem`. Copy the avatar snapshot swim fields into humanoid replacements after native extraction: detached adapter entities never advance the original player swim interpolation. Keep source `walkAnimationPos`, `walkAnimationSpeed`, `ageInTicks`, and `isInWater`. `HumanoidModel.setupAnim(T state)` already blends native arm strokes and alternating leg kicks from `swimAmount`; item use and attacking arms have native suppression. Avoid forcing animation over those poses.

`AvatarRenderer.setupRotations(AvatarRenderState, PoseStack, float bodyRot, float entityScale)` supplies horizontal swim body rotation. **HumanoidMobRenderer does not inherit that avatar-specific implementation.** Merely copying swimAmount produces upright limb strokes. A morph-only LivingEntityRenderer setupRotations tail can apply the avatar formula after vanilla body yaw: X rotation by `lerp(swimAmount, 0, isInWater ? -90 - xRot : -90)` degrees, followed by local `(0, -1, 0.3)` translation when visually swimming. Gate to a morph humanoid snapshot so actual mobs and human avatar renderers are untouched, and account for DrownedRenderer, which already overrides swim rotation to avoid doubling it. Avoid applying this global humanoid rotation to quadrupeds or aquatics.

## Native aquatic animations

| State/model | Required state | Native behavior |
| --- | --- | --- |
| DolphinRenderState / animal.dolphin.DolphinModel | isMoving from avatar horizontal velocity squared > 1e-7; ageInTicks, xRot, yRot | Body pitch pulse and tail/tail-fin flapping while moving |
| TurtleRenderState / animal.turtle.TurtleModel | isOnLand = !source.isInWater && avatar.onGround(); walkAnimationPos/Speed | Front flipper Z rotations and hind flipper X rotations in water; land paddle otherwise |
| LivingEntityRenderState / animal.fish.CodModel | isInWater, ageInTicks | Tail-fin Y swing; larger out-of-water amplitude |
| SalmonRenderState / animal.fish.SalmonModel | isInWater, ageInTicks | Back-body Y swing; faster/larger out of water |

Do not rely on detached adapter `isInWater()` / `onGround()` for turtle classification. Native renderer extraction sees un-ticked environmental state. Clear laying-egg state for uncaptured default turtle forms. Velocity synchronization alone is sufficient for native dolphin isMoving extraction.

## Blockbench quadruped clip

Preserve native meshes/textures; author a looping paddling clip that targets vanilla quadruped bones: `head`, `body`, `right_hind_leg`, `left_hind_leg`, `right_front_leg`, `left_front_leg`. `QuadrupedModel` exposes these via its public `root()` and named children. Validate with `root.hasChild(name)` before `getChild`, since unsupported variants can have different layouts. Suggested clip length 1 second, opposing diagonal leg pairs with foreleg reach and recovery; blend by water/movement amount. Idle water should have a reduced paddle instead of land walking. Sample time from snapshot ageInTicks / 20, rather than wall clock.

Blockbench project rotations are degrees; ModelPart rotation fields are radians. Explicitly record the chosen coordinate convention in the exported runtime data. Apply rotation offsets or blend targets after vanilla setupAnim, retaining original per-form pivots and baby scaling. The preview rig is an authoring aid, not replacement game geometry. Unknown bone tracks should be ignored, not crash. Malformed resource data should log once and fall back to vanilla animation.

## Deferred rendering constraint

`net.minecraft.client.renderer.feature.ModelFeatureRenderer` calls `model.setupAnim(submit.state())` at draw time. Mutating bones around entity renderer submit gets overwritten. Use a wrapper `Model<S>` with `super(delegate.root(), delegate.renderType())`; its `setupAnim(S state)` calls `delegate.setupAnim(state)` and then samples/applies the clip. Keep wrapper immutable and state-specific or cache only model-independent clip data. Shared native roots are safe only with normal per-draw setup/reset; never retain a mutated pose as the next frame baseline.

The collector interception method is:

```java
<S> void submitModel(Model<? super S> model, S state, PoseStack poseStack,
    RenderType renderType, int lightCoords, int overlayCoords, int tintedColor,
    TextureAtlasSprite sprite, int outlineColor,
    ModelFeatureRenderer.CrumblingOverlay crumblingOverlay);
```

`Model.root()` and `Model.renderType()` are public final. `Model.setupAnim(S)` resets pose by default. `ModelPart.storePose()` / `loadPose(PartPose)` and public xRot/yRot/zRot are available. Scope collector wrapping to morphed snapshots and compatible quadruped models, leaving equipment/other submitted models intact. Compose with black transformation collector without dropping its snapshot/geometry handling.

## Validation targets

Verify humanoid swimming body orientation and limb strokes, attack/item-use coexistence, turtle water/land switching, dolphin moving/stationary tail, pig and cow paddle, remote player state, leave-water reset, resource reload, and black transformation while swimming. Automated keyframe tests should cover loop seam, finite values, interpolation, missing bone safety, and zero blend preserving vanilla pose. Test both loaders; shared client source inclusion must cover new animation classes and resources.

## Implemented integration

The shared runtime uses `morph.animation.mixins.json`: a render-state marker, a morph-only `QuadrupedModel.setupAnim` tail, and a humanoid rotation tail. This applies quadruped keyframes at deferred draw time without replacing meshes or wrapping shared models. `MorphSwimming.extract` supplies native humanoid, dolphin, and turtle fields. `MorphSwimAnimation.reload(ResourceManager)` reads the shipped Blockbench animation export with bounded size/keyframe count, rejects nonfinite rotations/times, and falls back to vanilla poses on invalid data. A lazy initial read supports startup before reload registration. The clip is sampled as rest-pose rotation offsets and is active only for morph snapshots with water movement/swim blend. Drowned retains its own native swim rotation. The authored project and exported asset are provided separately by the main task.

Fast sprint-swimming selects `animation.morph.quadruped_fast_swim` from the same Blockbench export when the avatar snapshot is visually swimming in water. The authored fast cycle is 0.65 seconds (13 ticks), compared with the normal one-second paddle, with stronger leg strokes. Both clips retain per-form rest pivots. Native humanoids use the avatar's continuous swimAmount; native turtle and dolphin animations remain separate.

## Live client regression

`fabric/gradlew.bat runClientGameTest` launches an isolated integrated-world test using the official Fabric Client GameTest API. It creates a water pool, grants/selects forms, holds real forward/sprint key mappings, waits for actual player swimming, and checks extracted snapshots while client frames render. The final full run passed pig, cow, villager, pillager, cat, wolf, horse, spider, drowned, dolphin, turtle, guardian, squid, axolotl, and ordinary zombie. Screenshots use night vision, an angled view, and a 120-tick wait plus an explicit inactive-transition assertion so they show final form appearance. Ordinary zombies are expected to remain walking/sinking, with no swim marker or humanoid stroke; drowned remains eligible. Screenshots are saved under `fabric/build/run/clientGameTest/screenshots`. This verifies sustained input and renderer integration, not just synthetic animation values.

The client test additionally invokes the real villager/pillager renderer rotation and checks the resulting body axis is approximately horizontal. This catches duplicate superclass/family rotations, including the illager inheritance regression found during visual QA. Drowned is excluded from that assertion because its native renderer deliberately uses a much smaller lean. Set `MORPH_CLIENT_TEST_FORMS=pillager,villager,drowned` for the focused scenario; the default remains all 15 forms. The focused run passed after the illager fix.
