# Rendering feasibility: Minecraft 26.2

Inspected 2026-09-08. This is an API/source audit, not a successful visual test. A pig replacement is feasible to prototype through published hooks; transition and arbitrary mob rendering parity remain unproven.

## Verified target and evidence

The live [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml) reported `26.2.0.82` as latest. The exact [26.2.0.82 sources JAR](https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.82/neoforge-26.2.0.82-sources.jar) downloaded and expanded successfully. All NeoForge APIs below were read from that artifact, rather than inferred from search snippets or 26.1 documentation. Metadata is mutable; the versioned JAR is the reproducible reference. Its version has no beta suffix; that alone does not establish a stability guarantee.

The [26.2 migration primer](https://docs.neoforged.net/primer/docs/26.2/) is explicitly about vanilla changes, not loader compatibility. It describes feature submission replacing direct buffer rendering, separate extraction and submission responsibilities, changed picture-in-picture rendering, GUI/HUD separation, and a Vulkan backend. Its sample custom-feature design uses `SubmitNode`, `FeatureRenderer`, `FeatureRenderPhase`, and `FeatureRenderDispatcher`. These are design guidance; concrete call signatures still require compilation against the selected target.

## Exact loader hooks

| Verified class in the sources JAR | Contract relevant to Morph |
| --- | --- |
| `net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent` | Client mod-bus event. `registerAvatarEntityModifier(AvatarRenderStateModifier)` avoids intersection-type inference problems. Modifier runs after vanilla extraction. |
| `net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier` | Override `<T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState renderState)`. Live entity access belongs here, before deferred rendering. |
| `net.neoforged.neoforge.client.extensions.IRenderStateExtension` | Render states support keyed custom data; modifier documentation explicitly directs use of `setRenderData(ContextKey, Object)`. Clear absent data each extraction to avoid reused-state contamination. |
| `net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre` | Cancellable client game-bus event. Uses `AvatarRenderer<T>`, `AvatarRenderState`, `PoseStack`, and `SubmitNodeCollector`. Inherited getters expose state, renderer, partial tick, pose stack, and collector. There is no live-player getter. Cancellation suppresses the paired Post event. |
| `net.neoforged.neoforge.client.event.RenderArmEvent<?>` | Cancellable client game-bus event for replacing the arm separately from held-item rendering. Exposes avatar, arm, `ModelPart`, skin `Identifier`, packed light, pose stack, and collector. Generic parameter must be a wildcard for event listeners. |
| `net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent` | Client **game-bus**, not mod-bus, event. `register(FeatureRendererType<S>, FeatureRenderer<S>)` registers custom submit-node rendering; duplicate types throw. Relevant only if a custom transition needs its own feature renderer. |

Minecraft player rendering now names `net.minecraft.client.renderer.entity.player.AvatarRenderer` and `net.minecraft.client.renderer.entity.state.AvatarRenderState`. Copying an older `PlayerRenderer` example would therefore be misleading. A stale `PlayerRenderer` reference even remains in the modifier event's own example Javadoc; prefer its actual imports and signatures.

## Legacy incompatibilities

Paths below refer to the preserved `legacy/1.16.5/src/main/java/me/ichun/mods/morph/` source tree after migration.

* `client/render/MorphRenderHandler.java` directly calls entity renderers with a live entity and `IRenderTypeBuffer`. It mutates dummy-entity UUID/invisibility, modifies shared player-renderer shadow size, and briefly inserts spoofed player info for skins. None of this should be copied into a deferred submission callback. Use per-render snapshots and explicit skin data; shadow and nameplate ownership must be investigated in the target dispatcher.
* Its static `currentCapture`, `isRenderingMorph`, and `denyRenderNameplate` flags assume immediate, serial rendering. Exceptions can leak them; deferred work can observe the wrong morph. An immutable per-player snapshot and scoped, exception-safe submission eliminate this dependency.
* `mixin/ModelRendererMixin.java` injects at the old `ModelRenderer.doRender` invocation, cancels it, manually traverses child models, and pops the pose stack. The exact target and stack assumptions do not survive. Transition capture converts model boxes through iChunUtil `ModelHelper` and Tabula `Project.Part`, matching box counts between forms. Modern custom renderers are not guaranteed to be box models at all.
* `mixin/PlayerRendererMixin.java` intercepts old `renderItem` for arms. `client/render/hand/HandHandler.java` renders whole entities to infer model transforms, then renders copied hand parts through raw vertex builders. Replace the entrypoint with `RenderArmEvent`; a usable arm mapping still needs a separate implementation per compatible model family.
* Acquisition and biomass effects depend on the same capture and direct-buffer approach. A working pig switch does not imply those effects work. Selector entity previews likewise need the target GUI/picture-in-picture path.

## Minimal pig prototype

1. Keep an explicitly selected server-authoritative pig form and synchronize it by player identity. The rendering feature reads that client state, never changes it. This prevents an attractive but local-only demonstration from being mistaken for multiplayer support.
2. At avatar render-state extraction, check whether the avatar represents a morphed player. Prepare an independently owned pig render-state snapshot from vanilla pig rendering, transferring position interpolation, facing/head angles, walk animation, age/time, light, and visibility. Resolve the actual vanilla pig renderer and extraction signatures from the generated 26.2 sources before coding. A temporary pig entity may be an extraction adapter only: never spawn it in the world, tick AI, insert it into tracking, or retain it in submitted data.
3. Attach the result to the avatar render state using a Morph `ContextKey`. Return no replacement when extraction fails or no form is selected. Never use one mutable pig state for all players or mutate an already submitted snapshot for the next player.
4. In `RenderPlayerEvent.Pre<?>`, use that snapshot and the event's pose stack/collector to submit the vanilla pig model through the target renderer. Cancel the vanilla avatar only after replacement data is usable. Use push/pop in `try/finally`; do not rely on Post for cleanup because cancellation prevents it. Avoid calling the avatar renderer recursively. Keep pig submission out of the global entity lifecycle.
5. Start with an immediate switch. Preserve normal player rendering when returning to player form. Treat nameplate, shadow placement/size, invisibility, and spectator behavior as explicit prototype tests rather than assuming cancellation controls dispatcher-level effects.
6. Once third person is proven, use `RenderArmEvent<?>` for a deliberately defined pig-arm presentation. Keep held-item behavior as a separate test. A command-selected third-person prototype can initially retain vanilla hands, provided the limitation is explicit.

The first implementation now lives in `src/main/java/me/ichun/mods/morph/client/`. `MorphRenderSnapshots` is a loader-independent vanilla extraction adapter; `MorphClient` supplies the NeoForge event hooks, and `ClientMorphState` stores server-synchronized selections. Exact vanilla signatures were subsequently checked with Java 25 `javap` against `minecraft_26.2_client.jar`: `EntityRenderer.createRenderState(entity, partialTick)` allocates a fresh state and extracts/finalizes it; `submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)` submits it. `GameRenderer.gameRenderState().levelRenderState.cameraRenderState` supplies the current world camera for the NeoForge callback. This is initially a world-rendering prototype; GUI previews must supply their own camera context.

The adapter supports vanilla living-entity types whose normal renderer accepts a default constructed entity, starting with pig. It copies player motion, visibility, light, nameplate data, and facing into a freshly extracted mob state. It deliberately retains vanilla first-person hands and does not implement model transitions or mob-specific action animations. Shadow geometry and hitbox behavior are not fixed by this renderer. Extraction failures log once per form and preserve player rendering. Compilation and an actual client run remain distinct evidence gates.

## Transition decision and acceptance

Do not promise universal geometry interpolation. Prototype one pair of known vanilla models with copied model-part transforms first. If deferred vanilla submissions cannot expose a safe reusable mesh, submit a Morph-owned transition feature with copied geometry, or use an immediate switch/fade for unsupported models. Such a fallback is a visible compatibility policy, not equivalent to legacy visual parity.

Acceptance requires two clients (local and remote third person), walking/head animation, correct texture and lighting, switching repeatedly between pig/player, distinct simultaneously morphed players, late join, resource reload, disconnect/reconnect, and no leaked state. Inspect shadow and player nameplate separately. Exercise available OpenGL and Vulkan backends. Record screenshots/runtime logs and failures. Compilation alone cannot meet this rendering milestone.

## Alpha.2 follow-up

The original prototype limitations above describe alpha.1. Alpha.2 now captures both posed meshes, fades the original black skin, interpolates vertices, and reveals the destination. Both loaders share this renderer and explicit transition events. See TRANSITION_DESIGN.md for its differences from legacy part/box interpolation and VALIDATION.md for actual client checks. First-person hands remain unchanged.
