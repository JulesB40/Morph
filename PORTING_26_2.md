# Morph port to Minecraft Java 26.2

Prepared 2026-09-08. Implementation has started on `codex/port-26.2`; see README and docs/VALIDATION.md for current scope and verified results. The original milestones below remain the full-parity backlog.

## Recommendation and scope

Port directly from the upstream `1.16` branch to **Minecraft Java Edition 26.2, NeoForge and Fabric, Java 25**. The user requested both loaders during implementation. Use independent pinned loader builds and explicitly shared vanilla/model code, migrating subsystems incrementally. Both builds need their own runtime verification before release.

First deliver a multiplayer-safe classic morph loop: kill a supported mob, acquire its form, select it, transform, return to player, and retain the collection after reconnect/restart. Full parity then restores variants, favorites, traits, abilities, biomass progression, other modes, and authoring tools. A first playable milestone is explicitly not full parity.

Do not spend time making successive public ports for every intervening Minecraft release. Consult the intermediate migration primers when a subsystem needs them.

## Baseline inspected

- Upstream: https://github.com/iChun/Morph, branch `1.16`, commit `b6be1a31314fb1358b51e4aa70e4af5c2c77c4e5`.
- Fork: https://github.com/JulesB40/Morph; local remotes `origin` = fork, `upstream` = iChun/Morph.
- `build.gradle`: Morph 10.2.1, Minecraft 1.16.5, Forge 36.0.55, Java 8, ForgeGradle 4.1.+, snapshot MCP mappings, iChunUtil 10.7.0. Wrapper: Gradle 6.8.1.
- `src/main/resources/META-INF/mods.toml` requires iChunUtil `[10.7.0,11)`.
- `src/main/resources/morph.mixins.json` declares seven common and three client mixins, with Java 8 compatibility.
- `src/api/java` is a separate API source set, including morph state, variants, events, traits, abilities, and biomass contracts.
- The build references `src/api/resources/META-INF/accesstransformer.cfg`, but that file is absent in this checkout. Audit access requirements instead of copying that configuration blindly.
- No AGENTS.md was found in the cloned repository. The legacy build was inspected but not executed.

## Target facts and unresolved dependencies

The [NeoForge setup guide](https://docs.neoforged.net/docs/gettingstarted/) specifies Java 25 and a generated workspace using ModDevGradle or NeoGradle. Its current default documentation is labeled 26.1: do not treat every example as a verified 26.2 API. Select and pin an actual 26.2 loader and compatible Gradle/plugin combination during milestone 0, recording whether the loader is beta.

The [26.2 vanilla migration primer](https://docs.neoforged.net/primer/docs/26.2/) documents major rendering changes: feature submission replaces direct buffer rendering, picture-in-picture rendering changes, GUI/HUD responsibilities move, and Vulkan adds another backend. Morph's renderer, model capture, and UI therefore need architectural work. Use render state snapshots and target-supported submission APIs; avoid retaining mutable live entities as deferred render data. Validate OpenGL and Vulkan where supported.

The [iChunUtil repository](https://github.com/iChun/iChunUtil) has modern common/Fabric/Forge/NeoForge modules, but its public branch list inspected today ends at `1.21.5`; a compatible 26.2 artifact has **not** been established. Branch names alone do not prove release availability. This is the first dependency gate.

## Work packages and exit criteria

### 0. Resolve dependencies and establish a reproducible build

Inventory every `me.ichun.mods.ichunutil` import, grouped into networking, configuration, GUI framework, model utilities, and general helpers. Check published artifacts and source compatibility for 26.2. Prefer a supported release; if unavailable, choose between a scoped iChunUtil port and replacing only required services with native APIs. Record the choice and license obligations before migrating dependent code. Do not silently turn this into a full library rewrite.

Create an implementation branch from this planning branch, preserve upstream history and LGPL notices, replace the legacy build with a pinned 26.2 MDK, preserve API packaging deliberately, and establish client/server separation. Remove obsolete repositories, remapping, and refmap settings only as dictated by the new toolchain. Add CI build and artifact generation with Java 25.

**Exit:** a clean checkout resolves dependencies without a developer's Maven local cache, builds a JAR, and starts both a client and dedicated server. Record exact Minecraft, loader, plugin, wrapper, and library versions.

### 1. Prove the highest-risk renderer early

Investigate `client/render/MorphRenderHandler.java`, `client/render/hand/HandHandler.java`, `client/model/ModelAcquisition.java`, the acquisition/biomass renderers, and `ModelRendererMixin`/`PlayerRendererMixin`. Prototype a command-selected pig form using target render states and submission hooks. Check animation, texture, lighting, shadow, nameplate, and third-person rendering for both local and remote players.

Begin with an immediate visual switch. Then prototype the model transition separately; legacy geometry capture is not assumed portable. Document a fallback for renderers that cannot supply compatible geometry. Rework first-person hands and UI previews through supported entry points.

**Exit:** two clients see the same form without recursive rendering or leaked render state. A written decision establishes whether the classic transition can be reproduced and what fallback is needed. Resolve this before promising visual parity.

### 2. Port authoritative state, storage, and networking

Migrate `common/Morph.java`, `common/morph/MorphHandler.java`, `MorphInfoImpl.java`, `common/core/EventHandlerServer.java`, `common/morph/save/*`, `common/morph/nbt/*`, and API morph types. Replace the old capability registration with the appropriate 26.2 player attachment/state mechanism. Keep persistent collection data distinct from transient rendered state.

Replace `common/packet/*` and iChunUtil packet wrappers with the chosen target networking implementation and explicit codecs. The server validates ownership, mode, permissions, and cooldowns; clients send requests, not authoritative entity NBT. Bound payloads and handle malformed, unknown, or stale IDs. Synchronize on login, tracking start, transformation, respawn, and dimension changes.

Define a versioned save schema and test fixtures. `MorphSavedData` currently stores UUID-indexed collections under `morph_save`; preserve identity and define handling for missing entity types. Legacy 1.16 entity NBT must not be assumed valid in 26.2. Keep legacy import separate from normal saves and test only on copies; vanilla world upgrades do not establish custom Morph data compatibility.

**Exit:** acquisition and transformation work on a dedicated server; collections survive restart; late observers see the correct form; forged selection requests cannot acquire unowned forms.

### 3. Complete the classic gameplay loop and selector

Port `common/mode/ClassicMode.java`, `client/core/KeyBinds.java`, `HudHandler.java`, `EventHandlerClient.java`, and relevant commands. Restore selection, cancel, return to player, variant navigation, favorites, and deletion with server confirmation. Update input, HUD, text, and entity preview APIs. Recalculate dimensions, eye height, and collision consistently on both sides; handle crouching, swimming, riding, sleeping, death, and disconnect cleanup.

**Exit:** a player can kill, acquire, select, use, delete, and persist a form using the UI. Test small, tall, aquatic, flying, and variant-bearing mobs; no suffocation or client/server size disagreement on transforming.

### 4. Restore traits, abilities, and mob definitions

Migrate `src/api/java/.../mob/trait/*`, `common/mob/*`, and the entity/player/invoker mixins. Make an explicit parity checklist per trait and ability. Prefer loader hooks and attributes over invasive injections when possible; re-audit each mixin's target and side.

Validate bundled `mobsupport.zip`, NBT modifiers, registry IDs, and resource loading against current mobs. Define safe behavior for unknown modded mobs. Restore temporary attributes, flight flags, effects, and event subscriptions when changing form or leaving the world.

**Exit:** automated behavior tests cover representative flight, water breathing, fire immunity, climbing, and fall handling, including cleanup and interaction with creative/spectator modes. Each remaining ability has a recorded test result.

### 5. Restore full feature parity and API

Port `common/biomass/*`, `common/mode/*`, biomass GUI workspaces, NBT/mob editors, configuration, commands, advancements, sounds, and resource metadata. Inventory existing behavior rather than assuming every legacy screen is complete. Adapt the GUI to the dependency decision from milestone 0.

Version the public API intentionally: preserve source concepts where practical, document breaking contracts, and provide an addon example. Old compiled addons are not expected to be binary-compatible across this Minecraft jump. Document configuration and save migration behavior.

**Exit:** a feature matrix covers classic/default/command/disguise modes, biomass progression, editors, configuration, and addon integration, with no silently omitted features.

### 6. Validate and prepare release

- CI: clean build, codec/save round trips, malformed input, ownership checks, and trait cleanup tests.
- Runtime: single-player and dedicated server with two clients; joining late, reconnect, death, respawn, dimension travel, tracking range changes, and server restart.
- Visual: both supported graphics backends, first/third person, UI previews, transformation effects, armor, invisibility, nameplates, and resource reload.
- Compatibility: vanilla mob matrix first, then explicitly selected modded entities and renderers; document unsupported cases.
- Performance: measure client frame time during transitions and selector rendering, server tick cost with multiple morphed players, and memory across repeated transforms/reloads. Set numeric budgets from the milestone 1 baseline.
- Distribution: test the produced JAR outside development, verify dependency ranges and client/server loading, retain attribution/licenses, and document installation and known limitations.

**Exit:** all required parity items and regression scenarios pass, with reproducible results. Publish only after the port actually meets those criteria.

## Sequencing and planning estimate

Critical path: dependency/build gate → rendering feasibility → authoritative core → classic UI/gameplay → complete traits and parity → release validation. Rendering and dependency feasibility determine whether later estimates remain credible.

Provisional estimate for one experienced Minecraft mod developer: 3–5 days for dependency/build investigation, 1–2 weeks for rendering feasibility, 2–3 weeks for core and classic gameplay, 2–4 weeks for traits and full parity, and 1–2 weeks for stabilization. Rough total: **7–12 developer-weeks**, excluding a substantial standalone iChunUtil port. This is an engineering estimate, not a measured commitment; re-estimate after milestones 0–1.

Next implementation task: complete milestone 0's dependency inventory and pin a bootable NeoForge 26.2 workspace, then immediately run the renderer proof of concept.
