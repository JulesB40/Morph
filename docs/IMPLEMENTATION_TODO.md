# Morph 26.2 implementation TODO

Feature inventory reviewed 2026-09-12 at `3aae1cb` on `codex/port-26.2`. Feature implementation remains paused; CI repair and automatic downloadable releases have resumed separately. “In progress” below means that source work exists but the required evidence is incomplete; it does not mean all feature work is actively running.

This is an evidence-backed inventory, not a completion percentage. The project has no claim of correctness for every mob, renderer, addon, resource pack, or multiplayer combination. NeoForge and Fabric share most gameplay source, but a result on one loader does not certify the other loader’s client hooks, rendering, networking, or startup.

## Done — verified behavior

These entries have a retained run or test result for the stated scope. “Done” is deliberately narrow: it does not close the wider work area.

- **NeoForge CI and lab package repair:** at `69fb239`, [both loader build jobs passed](https://github.com/JulesB40/Morph/actions/runs/34720694104). NeoForge passed the same 20 native server GameTests with and without the lab mod, including native-pig crouch geometry and cramped-reset rejection. Fabric also compiled its client harness. This clears the duplicate-package server startup failure; loaded-client equipment assertions remain unverified.
- **Download packaging:** both loader JARs are [available in the latest development release](https://github.com/JulesB40/Morph/releases/latest), with matching embedded versions, source manifests, and verified SHA-256 hashes. Six release-tool tests cover packaging and publication retries, including a draft-lookup regression reproduced before the fix. The workflow publishes after both loader jobs pass and marks the download as Latest for sidebar visibility. Builds remain unfinished alpha versions; full installation and gameplay certification remain below.
- **Harness fundamentals:** the Morph Lab Python suite reports 91 infrastructure tests, with two Windows symlink-privilege skips. It exercises queue leases, cleanup, source mutation detection, controller bridges, failed assertions, and memory admission. These are harness checks, not 91 Minecraft gameplay scenarios.
- **Flight and nametag controls (Fabric):** the hidden-framebuffer pilot passed bat/bee flight and nametag controls (`a0a0783...`); the result is Fabric client evidence only.
- **Husk Hunger correction (NeoForge and Fabric):** the five native cases passed after the empty-hand and local-difficulty fixes (`b784364...` NeoForge and `408a78a...` Fabric). This closes those cases, not every combat or loader path.
- **Wither/Sniffer/dragon and renderer recovery (NeoForge client plus server):** one client/server run verified Wither head/model angles, six Sniffer limbs, dragon state, extraction/submission fault recovery, capture-failure recovery, and cleanup (`2be18d4...`). This is a bounded component/client scenario, not all-mob visual parity.
- **Dragon capture and transformation clip (Fabric):** the candidate produced a native `EnderDragonRenderState`, a dragon-containing PNG, and a 26-frame tick-sampled MP4 (`6b62bd...`). This proves the exercised path; it does not prove dragon attacks, navigation, multipart interaction, or cross-loader parity.
- **NeoForge real multiplayer/save lifecycle:** two separate clients and a dedicated server verified independent actor movement, authoritative form/nametag agreement, clean shutdown, restart, world reload, and reconnect persistence (`a92203...`). The images do not establish visual form parity.
- **Transformation sound notification (Fabric):** the client observed one `morph:morph` sound event in `PLAYERS` (`cbf681...`). This is event evidence, not proof of audible mixing or complete sound forwarding.
- **Native geometry pose candidate (NeoForge):** the native-geometry candidate `b4a5d4cae6a74b27b51f22540cd8ee6d` passed all 1,638 pose cases for 91 mobs, compared with 1,604 mismatches in baseline `c02552e605a64c2ea2c1386f4872a2c0`, with source verification recorded. This verifies that candidate's native pose geometry scope; it does not establish full rendered behavior, equipment, variants, or both loaders.

## In progress — implemented, but unverified or only partially verified

The following source exists on the current branch, but the required independent runtime evidence is missing, loader-specific, or narrower than the feature claim.

- **Collection and descriptors:** variant DTO/schema-2 collection work, authoritative `FormDescriptor`/`EntryId` protocol, request/appearance sequencing, schema migration scaffolding, and captured sheep color, baby state, and slime size are present. Verify round trips, deduplication, multiple variants of one species, deletion, and reload/reconnect on both loaders. Targeted entity capture and bulk deletion are still missing and listed below.
- **Attributes, native authority, and Biomass backend:** the 23-base-attribute capture/application path, unavailable-attribute rejection, authenticated authority, collision checks, persisted Biomass ledger, costs, and commands are wired. Native authority/command probes were added, but loader-native equipment/bootstrap and the Biomass runtime are not all executed. Verify health ratios, modifiers, save/reload, movement consequences, permissions, server/client rejection, and opt-in Biomass behavior on both loaders.
- **Selector and collection UI:** selector pagination, favorites/wheel models, actions, previews, labels, favorite persistence, and client request waiting are present. Verify actual input, server-confirmed favorite/delete behavior, variant identity, localization, navigation, preview rendering, and search in real clients on both loaders. Legacy HUD controls and custom settings remain separate missing work.
- **Rendering, transitions, and fallback:** dragon/flap clocks, render snapshots, native pose/animation adapters, `EntityRenderState` acceptance, transition capture, safe fallback, recovery state, and reload quarantine are present, with selected paths verified. The 91-form model inventory and authored native probes are contracts, not full visual results. Verify remaining custom geometry/deferred GPU paths, equipment layers, all applicable forms, and both loaders.
- **Animation and equipment:** common poses, illager flags, chicken/parrot flight clocks, aquatic/flight helpers, named limbs, six-slot live equipment, and captured body/saddle validation code exist. Captured rideable gear and rendered saddle/body layers remain open; action states, age/size variants, first-person hands, and all-mob motion have not been established.
- **Shape and attachments:** native all-pose dimension handling and attachment preservation, collision hooks, and transition descriptors are implemented or integrated. Verify the remaining actual-player hooks, sleep scaling, passenger placement, mount/dismount clearance, shadows, transition interpolation, and native rider attachments in runtime scenarios.
- **Sound forwarding:** native sound capture/forwarding hooks and transformation sounds exist. Only a client notification has been observed. Verify step/swim/fly/hurt/death/fall/eat and ambient policy, volume/pitch/routing, suppression, and actual audible output where claimed, on both loaders.
- **Configuration and policy:** configuration/policy snapshots and Classic, Command, and Biomass registration paths exist, including configurable duration and command paths. Biomass is opt-in; Disguise is declared but not enabled. Verify reload/atomicity, player/mob filters, mode restrictions, and the shared targeted commands. Trait controls, client settings, and configured `MAX_BASE` merging still need implementation.
- **Definition/API groundwork:** definition parsing/reload and addon/event registry classes exist. Validate these contracts with a fixture addon on both loaders. Legacy editors, export/re-extract tools, broader callbacks, and modded-form support are still missing.

## To do — known missing or explicitly unfinished

### Current test blockers

- Verify the already-written Fabric test working-directory correction in `fabric/build.gradle`: the previous run created unexpected `fabric/logs/latest.log`. Rerun from a frozen snapshot and require source verification.

### 1. Collection and selector

- Extend the existing descriptor identity/capture system beyond sheep and slime: other ages, colors, names, species states, compatible body gear/saddles, and player disguises. Preserve native base attributes without copying temporary effects or equipment modifiers. Add targeted entity capture and bulk entry deletion.
- Add remaining legacy selector navigation/HUD controls and configurable ordering. Search is implemented and awaits real-client verification. The collection has a `MAX_BASE` policy enum, but `MorphAuthority.grantDescriptor` currently uses `KEEP_EXISTING`, so configured improved-attribute merging is not wired through the grant path.
- Add configurable selector position/scale and mouse behavior; validate translated settings and messages.
- Decide and implement bounded support for modded entities only where shape, renderer, capture, and trait adapters exist; current validators reject unsupported modded forms.

### 2. Progression and configuration

- Add Biomass HUD/progression screens, reloadable economy definitions, ability costs, advancement integration, and legacy progression import. The current ledger, upgrade purchases, and morph costs need runtime validation. Keep optional Biomass distinct from Classic functionality.
- Implement Disguise mode, configurable trait exclusions/upgrades, attribute merge/cap rules, size policy, and remaining client settings. Classic/Command modes, player/form filters, duration/silence settings, and shared targeted commands are already implemented and need verification.
- Implement tested legacy save import, including `morph_save`, variant data, attributes, progression, unsupported-schema handling, source preservation, and idempotence.

### 3. Rendering and transitions

- Complete custom-geometry/held-item transition decisions, deferred-GPU error handling, shadow-size and camera/underwater-view behavior, acquisition tendrils, and remaining transition timing checks.
- Add the remaining loader-specific extraction, submit, reload, and partial-failure scenarios; current fallback/reload quarantine and general entity-state acceptance are implemented and selected paths are verified.

### 4. Animation and equipment

- Run the model-by-model 91-form matrix for normal/fast swimming, walking/look, flying, age/size/variant, equipment, transition, and underwater-walking cases on both loaders.
- Add or explicitly classify action state coverage for wings, allay/vex, breeze, phantom, skeleton aggression, zombie conversion, creeper swelling, enderman, ravager, panda, feline/wolf sitting, equine actions, guardian/shulker/warden states, dragon multipart behavior, and other native stateful models.
- Finish body/saddle/horse armor/wolf armor capture and rendered layer checks, passenger offsets, and first-person mob hands. No detached AI tick, fabricated item, or false attack/spell flag should be used to make a probe pass.

### 5. Movement, collision, and riding

- Measure and implement native-equivalent ground, sprint, water, fast-swim, flight, and species speed behavior; base attribute equality does not establish player movement equivalence.
- Verify the implemented all-pose dimension/attachment behavior through actual-player hooks, sleep scaling, fit checks, cramped-space reset, shadow, attachments, and passenger positions.
- Restore saddle-gated pig/strider riding and decide modern camel, happy-ghast, and nautilus riding. Verify Vex no-gravity/wall policy, strider lava fall behavior, bubble columns, chicken water glide, flight cleanup, and mount lifecycle.

### 6. Traits, combat, and native abilities

- Add modern hostility/Brain targeting coverage and relationships for bogged, breeze, creaking, parched, warden, and newer forms; static target hooks do not cover every Brain memory path.
- Implement selected, explicitly scoped active abilities with server authority, costs/cooldowns, and controls: creeper, enderman, witch, evoker/vex, blaze/ghast, ranged undead, ravager, breeze, warden, Creaking, armadillo, guardian, shulker, Wither lifecycle, dragon multipart attacks, and related stateful behavior.
- Verify conditional rules such as pufferfish puff/contact poison, Strider lava behavior, bee lifecycle, tame/sit/breed/conversion states, and additional native attributes (dragon camera/flying speed/scale candidates). Do not copy AI-only or player-specific attributes without a policy decision.

### 7. Multiplayer and saves

- Repeat authoritative select/reset/grant/delete/mode/permission behavior, packet ordering, owner/observer synchronization, respawn, disconnect/rejoin, dimension changes, and malformed or stale requests on both loaders.
- Extend the successful NeoForge two-client lifecycle to visual actor/observer evidence and a comparable Fabric external-server session. Verify save migration and restart with variant/equipment/Biomass data.
- Preserve immutable source/JAR/test manifests for every accepted run; current pilot evidence is split across frozen commits and does not certify current HEAD.

### 8. Audio and integrations

- Run the new native event-sound probes and fill any demonstrated gaps in step/swim/fly/hurt/death/fall/eat/drink forwarding. Define ambient-sound behavior and remaining sound interpolation. Verify volume/pitch, routing, suppression, and audible mixing where claimed.
- Finish public addon API/events, resource-load and synchronization/NBT callbacks, external mob support, and cooperation with other attribute/flight mods. Add addon fixtures and both-loader compatibility tests. Implement mob/trait and NBT editors, hand metadata support, export/generation, and resource re-extraction tools.

### 9. Release and validation

- Run the full finite coverage matrix on both loaders, retaining pass, fail, unsupported, skipped, timeout, and infrastructure-failure distinctions. The 375 planned scenario references are a catalog, not completed coverage.
- Add all-mob client, real multiplayer, resource-pack/shader/third-party renderer, attribute/flight-mod, performance, allocation/GC, server-tick, and many-morph capacity evidence. Current captures are tick-sampled clips, not FPS measurements; sound events are not audible proof.
- Verify clean fresh installs, upgrades, old saves, version mismatch handling, artifact hashes, packaging/test-fixture separation, translated UI/messages, and a repeatable both-loader release build. The current root `build.bat` builds NeoForge only.
- Preserve and rerun the frozen build evidence against current HEAD: NeoForge `d28b6be...` compiled and ran 123 tests in 37 suites with two failures moved to native-loader probes; earlier `27afb092...` passed after moving runtime logs under `build/`; Fabric `3aedcce...` had 122 JUnit tests with no test failures but an overall infrastructure error from unexpected `logs/latest.log`. These are historical snapshots, not current-tree certification.

## Loader and evidence limits

The source layout is shared (`src/main`) with separate Fabric and NeoForge adapters. A source-level implementation or a unit test does not certify both loaders. The retained evidence includes Fabric client probes, NeoForge client/server probes, and one NeoForge real two-client session; it does not yet provide equivalent full client/render/multiplayer matrices for both loaders. The NeoForge duplicate-package correction passed server startup; the Fabric stray-log correction still needs a frozen lab rerun. These checks do not establish client feature passes.

Material validation remains limited: the 91-form/native-model inventory is source metadata and authored probe contracts; screenshots prove only the exercised scene; MP4s are tick-sampled; sound events do not prove audible output; committed memory is not process RSS; and no result guarantees every mob, variant, renderer, addon, resource pack, or multiplayer combination.

## Pause and ownership note

All nine work areas above remain accounted for. The implementation wave used seven Astra workers plus the root agent covering two retained areas because of available slots; there are no active feature implementation workers now. CI repairs and release automation are proceeding separately. Before broadening feature work, rerun frozen baseline/candidate scenarios with the harness startup corrections.
