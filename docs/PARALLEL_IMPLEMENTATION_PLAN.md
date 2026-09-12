# Morph 26.2: harness-first parallel implementation plan

Date: 2026-09-12. Baseline: `8955f881295ef95b42976602ca0e646449573fe1` (alpha.8). Scope: all items in `PORT_GAP_AUDIT_2026-09-12.md`, both NeoForge and Fabric, including explicitly identified new native abilities and completion of unfinished biomass. Prepared with nine GPT-6 Astra planning subagents and primary-agent integration review. The initial planning turn made no implementation or gameplay changes. Implementation status updated 2026-09-12: the lab runner, queue, snapshots, process supervisor, reports and opt-in loader probes now exist. Real Fabric baseline execution produced hidden-window framebuffer captures and a transformation MP4, with dragon/Sniffer/deliberate-assertion failures and passing bat/bee and nametag controls. The expanded five-case NeoForge Husk baseline recorded four mismatches (including Easy zero-duration Hunger), while the candidate passed all five with source verification and cleanup. A real NeoForge external-server/two-client session passed numeric form/nametag synchronization, independent movement and true server exit/restart persistence; all four Java lifetimes and the wrapper cleaned up. Its screenshots do not yet prove visual form correctness. Memory evidence distinguishes job committed memory from per-process working set, and does not authorize additional rendered slots. NeoForge capture/fallback recovery, Wither/Sniffer checks, Fabric sound evidence and the broader acceptance matrix remain pending; fake-process session tests are not Minecraft evidence. See `tools/morph-lab/README.md` for commands, run IDs and evidence limits. The architecture and waves below remain the target plan, not a completion claim.

## Outcome and sequencing

Build a verification harness first. Nine agents will be able to submit, inspect and rerun independent jobs without sharing Windows keyboard/mouse or editing the code being tested. After that gate, implement feature packages concurrently against agreed contracts, integrating in small dependency-ordered batches. Parallel development does not mean nine Minecraft clients or nine edits to the same central file at once.

Completion means every audited requirement has an owner, implementation, independent scenario and evidence on both loaders. New design choices must be explicit; a missing or unsupported required feature cannot be relabeled as passing. Compatibility with arbitrary third-party mods and every renderer combination is not a finite guarantee; declare and test an explicit compatibility matrix.

## Verified starting point

- Fabric already has client GameTests for swimming, bat/bee takeoff/hover and nametag controls. Cached Fabric client test API exposes synthetic keyboard/mouse input, window sizing, screenshots and image comparisons. These avoid operating the desktop cursor.
- Current screenshot/render-state checks do not prove all limbs, equipment, speed or final rendering are correct. Video and per-process audible capture are not established by these APIs.
- Fabric's test-owned dedicated server runs in the client JVM and is unsuitable by itself for real process stop/restart evidence. Add independent server/client process orchestration.
- NeoForge has server tests but needs its own client test adapter; Fabric client success cannot certify NeoForge rendering/input/network hooks.
- Machine inventory: approximately 16 GB RAM, 32 logical processors, Intel UHD and NVIDIA RTX 4070 Laptop GPU. Roughly 3.3 GiB free physical memory at inspection. FFmpeg is installed. GPU selection, hidden/background rendering and capture throughput still require a pilot.
- No promise of nine simultaneous rendered clients. Start with one rendered-client slot and one heavy build/server job; the scheduler admits work only when memory headroom permits. A two-client session reserves both clients and its server as one job, after capacity proof. Benchmarks run exclusively.

## 1. Harness architecture

### 1.1 Controller and isolated workers

Proposed developer-only tooling:

- `tools/morph-lab/`: Python standard-library runner/queue, immutable snapshot preparation, process ownership, job CLI, report assembly and FFmpeg invocation.
- `testing/harness-common/`: scenario contracts, event schema, fixtures, assertions and bounded fault injection.
- `testing/harness-fabric/` and `testing/harness-neoforge/`: loader-specific startup, synthetic input, render capture, server/client events and shutdown adapters.
- `testing/scenarios/`: versioned acceptance definitions and mapping to audit IDs.
- The implemented default is external `Morph-lab-runs/runs/<run-id>/`, beside the checkout containing the CLI, for disposable run worlds, process logs and evidence. Use `--root` to select a shared external root explicitly. Export retained release/baseline bundles to a separate explicit archive.

The runner and opt-in harness source sets now exist; the remaining architecture is still a target. Keep source-set/build configuration under one owner. Test controllers and fault injection must not ship in release jars.

Each job owns its source snapshot/worktree, build outputs, game directory, options, world, ports, player identities, resources, controller channel and evidence directory. Two clients in a multiplayer job share only that job's server, not game directories. Use process-owned synthetic input and framebuffer capture; never global SendInput, desktop screenshots or system-wide hotkeys.

Use a small durable job queue (for example SQLite) with atomic worker leases, heartbeat, resource reservations, timeout and cancel state. A local per-run control channel carries allowlisted test actions with sequence IDs; game actions execute on the correct client/server thread. Do not expose the controller on external interfaces or package it in ordinary server/client mods.

The scheduler tracks memory reservations, worker RSS, actual rendering backend, CPU-heavy builds and GPU slots. Low headroom leaves jobs queued with a clear reason. It must not close the user's apps, kill unrelated Java processes, change global graphics settings or delete unrelated worlds. Launch background helpers hidden; prove any Minecraft window/focus behavior separately.

On Windows, record the owned process tree and use a process group/job mechanism for cleanup. Reserve ports through a race-resistant broker and retry binding failures. Resolve and validate run paths under the harness root before deleting disposable data. Shared dependency caches may remain shared where supported, but build directories, Gradle project caches and runtime state cannot be shared across different working trees. Bound Gradle workers/daemons to avoid multiplying memory usage.

### 1.2 Immutable inputs and reproducibility

A job freezes exact source plus harness content before build. Include commit, dirty patch/untracked source manifest, SHA-256 hashes, scenario revision, loader/JDK/Gradle/dependency versions, command, fixture hash, seed, configuration and actual tested JAR hashes. A commit alone is insufficient for uncommitted work.

Never run against a checkout an agent is still changing. Hash before/after and invalidate unexpected mutation. Test a baseline and candidate with the same scenario contract; new feature scenarios may legitimately report baseline unsupported/fail. Preserve executable provenance and raw failures. Final release artifacts must match tested hashes, including after any integration edits.

### 1.3 Execution lanes

| Lane | Purpose | Concurrency policy |
|---|---|---|
| Unit/codec/model | Pure rules, schemas, math, canonicalization | Parallel within CPU/RAM budget |
| Native server | Real damage/effects, AI, collision, saves, attributes | Isolated server JVMs; measured memory budget |
| Rendered client | Actual input, framebuffer, equipment, animation, UI | Start one; expand only after background/isolation pilot |
| Real multiplayer | Separate actor and observer clients plus server | Atomic reservation; no half-started sessions waiting for capacity |
| Performance/audio/manual | Frame timing, audible output, launcher/driver checks | Exclusive resource/desktop lease as needed |

Nine agents can write scenarios, submit jobs, inspect artifacts and perform code review simultaneously. The controller serializes scarce resources. Different jobs always use different game instances; repeated scenarios within a worker require proven cleanup or restart.

### 1.4 Evidence bundle

Each result contains:

- `manifest.json`: exact source/artifacts/environment and scenario expectations.
- `result.json` plus JUnit XML: pass, fail, infrastructure failure, timeout, skipped or unsupported, with distinct reasons.
- `events.ndjson`: scenario/sequence/server tick/client tick/frame index/monotonic timestamp; positions, velocity, health/effects, dimensions, equipment, variant, action/transition state, packet ordering and render/fallback route.
- PNG checkpoints and short frame sequences from each client's framebuffer; labeled actor/observer/native reference and baseline/candidate.
- MP4/WebM review clips encoded by installed FFmpeg. Preserve frame/tick metadata and dropped-frame counts. A tick-sampled replay is not a real-time FPS measurement.
- Sound ID/location/volume/pitch/routing/timing events. Actual WAV audio requires verified per-process capture; otherwise audio listening gets an exclusive test slot. System-wide loopback cannot certify independent concurrent clients.
- Logs, crash reports, failure context and limited fixture snapshots; bounded rolling frame/event buffers for failures.
- Static local HTML index with form/feature/loader/status filters, side-by-side images, overlays/differences, video, telemetry plots, commands and hashes.

Capture is bounded: short clips around important actions rather than recording every test indefinitely; retain required baseline/failure/release evidence and apply explicit retention to disposable successes. Encoding uses a bounded queue and cannot block the render thread.

Screenshots/video are supporting evidence. Numeric damage, cooldown, persistence and network assertions remain independent. A static image cannot establish animation; a changing animation-state flag cannot establish visible limb motion. A played sound event cannot establish audible mixing.

### 1.5 Deterministic scenarios and real inputs

Pin fixture seed, chunks, daylight/weather, camera/FOV, resolution, equipment, difficulty, health/food/effects and controlled actions. Wait for meaningful readiness barriers rather than arbitrary sleeps. Record tick and wall-clock time separately; overloaded workers must not appear to change blocks-per-tick speed.

Use actual synthetic key/cursor input for end-to-end movement/selector tests. Direct calls to button handlers or render-state helpers remain useful component tests but are labeled accordingly. Server setup commands may establish fixtures, but the action under test must use the real player/network path.

Compare native entities in equivalent controlled states where meaningful. Do not use candidate helpers to calculate their own expected output. Visual references need reviewed baselines and declared tolerances/masks; never regenerate expected screenshots automatically after failure. Keep platform/renderer-specific references where pixels differ legitimately.

## 2. Harness acceptance gate before feature implementation

1. Run one existing Fabric swimming/flight case with synthetic input, actual framebuffer PNGs and synchronized telemetry; implement the equivalent NeoForge bridge.
2. Run two clients with different scenes and inputs while an unrelated app remains foreground. Verify no focus/cursor competition, paused/background render failure, crossed commands, port/file collisions or shared screenshots. If hidden rendering fails, retain a queued graphics lane; do not claim Windows virtual desktops solve isolation.
3. Produce a playable short transformation clip with known tick/frame coverage. Verify sound-event evidence; mark audible output separately until captured/listened to.
4. Run an external dedicated server with two real client JVMs, observer nametag/form checks, disconnect/rejoin and true stop/process-exit/restart persistence.
5. Reproduce baseline defects using independent oracles: dragon visible render path, actual Sniffer middle-leg movement, Husk empty-hand/duration behavior. Include passing controls for bat/bee hover, Wither head orientation and nametag persistence.
6. Inject renderer extraction/capture/submission failures and a worker timeout. Preserve diagnostics, classify failure accurately, clean only that job, and prove a later job succeeds.
7. Verify source/JAR hashes, deterministic repeat tolerance and queue recovery after runner interruption. A deliberately wrong assertion must fail.
8. Measure capacity. Record actual GPU vendor/renderer, peak memory, frame/capture throughput and server tick health. Enable additional render/server slots only with evidence. Do not run contention-sensitive benchmarks concurrently.

The harness gate itself is engineering work. Do not start the entire production feature swarm before it passes; domain agents can prepare fixtures/contracts and failing acceptance cases alongside harness development.

## 3. Shared contracts before broad parallel edits

Freeze a small first revision of these interfaces, then integrate adapters incrementally:

| Contract | Responsibility |
|---|---|
| `EntryId` / `FormDescriptor` | Stable collection identity separate from species; immutable versioned captured appearance/profile/attributes |
| Capture adapters | Bounded canonical state capture/apply; strip transient position, UUID, current health, AI/passengers and unsafe inventory state |
| Collection metadata | Favorites/order separate from captured identity; live actions never mutate saved variant identity |
| `MorphPolicySnapshot` | Revisioned server rules; client receives only relevant capabilities/UI settings |
| Shared action/acquisition service | Identical authoritative select/reset/grant/delete/mode/permission outcomes for both loaders |
| `TransitionSpec` | Source/destination descriptors, server start/duration/generation consumed by renderer, camera, collision and sounds |
| Capability/action state | Server-owned phase/target/cooldown; client sends intent, never damage or authoritative form state |
| `DefinitionSnapshot` | Validated, atomic mob/trait/hand/upgrade definitions and addon registration |
| Render outcome | Replacement submitted, intentional invisibility, or keep original player; failures cannot silently cancel all drawing |
| Harness scenario/result | Stable expectations and evidence schema shared across loaders |

Central files get one integration owner. Helpers and feature adapters can be developed independently. Variant descriptors require renderer/shape cache keys and both transition endpoints to carry identity/revision; changing storage alone is insufficient. Widen dragon rendering through the entire chain to `EntityRenderState`, keeping living-only logic gated.

## 4. Nine-agent work allocation

Use nine Astra implementation agents plus the primary integrator. Assign exclusive file ownership per package and separate `codex/` worktrees. Each agent writes tests/scenarios for its feature; a harness owner is not the sole tester. Rotate available capacity between waves so the largest domains do not become permanent single-agent bottlenecks.

| Agent | Initial harness/foundation work | Feature ownership after harness gate |
|---|---|---|
| 1 | Queue, immutable runner, process/resource isolation, build adapters | Harness maintenance, CI/release manifests, standalone installs and performance orchestration |
| 2 | Capture/save fixtures, bounded codecs and schema contracts | Variants, species/player identities, canonical merging, captured attributes, schema-1 and legacy migration |
| 3 | External server plus two-client coordination, lifecycle probes | Shared authoritative service, network/protocol revisions, owner/observer sync, admin commands and multiplayer lifecycle |
| 4 | UI input scenarios and collection/progression view contracts | Selector/radial/favorites/delete/search, previews/localization, modes and biomass progression/HUD; economy first, UI after |
| 5 | Framebuffer/clip evidence and injected renderer failures | Dragon/general render path, transitions/acquisition tendrils, fallback/reload recovery, sound events/audio integration |
| 6 | Native model/part coverage and pose/equipment fixtures | All-mob animation adapters, normal/fast swim, equipment/body/saddles, first-person hands |
| 7 | Actual movement/collision/damage reference scenarios | Ground/water/flight profiles, pose dimensions/eyes/attachments, riding, native attributes/passive defenses and Husk fix |
| 8 | Ability-state/AI scenarios and capability inventory | Modern attacks/stateful defenses, hostility/Brain targeting/intimidation, boss mechanics and controlled lifecycle features |
| 9 | Validated resource/config/API contracts and fixture addon | Configuration/filter policy, definition reload, addon API/events, modded support coordination, editor backend/export |

UI/editor presentation belongs to 4; definition validation belongs to 9. Renderer entrypoints belong to 5; animation contributors 6 submit adapters. Dimension/camera physics belongs to 7; 5 consumes its shared timeline/view values. Loader service/packet registration belongs to 3. Agent 2 owns saved schema, with progression/network contributors supplying DTOs rather than concurrent rewrites. Agent 1 owns Gradle/CI/harness launch configuration. Primary handles integration order, independent review, shared resource keys and any desktop-only test lease.

Heavy follow-on packages (biomass economy, first-person hands, dragon multipart mechanics, addon editor UX) get dedicated bounded tasks as earlier agents finish. Nine workers remain the ceiling, not a reason to split one fragile file among several agents.

In particular, biomass is not incidental UI work: once the shared network/service foundation is stable, assign agent 3 a dedicated economy/backend package, with agent 4 retaining progression screens, agent 2 persistence DTO integration and agent 9 definition validation. Likewise, completed early movement/passive work can free agent 7 to help agent 8 with a separately owned boss package. Update the ownership map before reassignment; do not leave simultaneous owners on the same service or trait files.

## 5. Implementation waves and full scope

### Wave A — correctness and enabling contracts

Deliver the shared descriptors/service/timeline; dragon renderer acceptance; Sniffer leg mapping; Husk rules; common poses and illager flags; renderer fallback/reload recovery; current flight/health/nametag regression preservation. Complete native/passive discrepancy probes and shared capability requests with one harmless test action. No uncontrolled detached AI ticking.

### Wave B — legacy functionality in parallel

- Capture age/color/size/name/gear/profile variants; stable deduplication and configured species attribute merging; player disguises and tested modded adapters.
- Favorites/delete/radial/navigation, localized names/previews and optional search/order controls.
- First-person hands, all animation families, body gear/saddles and passenger positions.
- Acquisition tendrils, original event sounds, configurable transformation, camera/eye/hitbox/shadow/trait timing and underwater view.
- Classic/Command/Disguise rules, filters, exclusions, admin target/acquire/unacquire operations.
- Reloadable mob/trait/NBT/hand definitions, public API/events and editor/export/reextract functions.
- Current-schema migration plus explicit idempotent legacy NBT conversion, preserving original files and reporting unsupported fields/overflow.

### Wave C — modern behavior and unfinished systems

Maintain an audit-derived capability inventory plus a per-species review across the pinned native source. Every supported form receives explicit applicable/not-applicable capability entries; absent entries never mean implemented.

Implement stateful defenses first (pufferfish, shulker, guardian, armadillo), then projectiles/channels (guardian/elder, blaze/ghast, skeleton/drowned item integration, witch, breeze, warden), then teleport/explosion/summons (enderman, creeper, evoker, ravager), and finally complex Wither/Dragon/Creaking systems. Include bee lifecycle/hive decisions, domesticated/cosmetic states, size/age and optional conversions. Fullflight remains available; Vex phasing is a separate ability.

Complete biomass as a new opt-in mode: persisted ledger, reachable starter progression, gain/capacity/absorption, upgrade DAG/requirements/max levels, atomic purchases, ability/morph costs, HUD/screens, definitions/reload and migration. Classic mode does not acquire new biomass costs. Numerical balance tables must be authored and tested, not inferred from unfinished old code.

### Wave D — integration and release evidence

Both-loader full registry/model coverage, real multiplayer state/late tracking/reconnect/dimension/death/restart, external attribute/flight integration, declared modded fixture support, shader/resource-pack matrix, fault recovery, whole-game performance and fresh installation. Reconcile documentation with tested behavior. Build and test the exact final artifact once integration stops changing it; publish only matching artifacts when release publication is requested.

## 6. Proposed gameplay policies to make the plan implementable

These are explicit defaults to encode and review during specification, not already implemented behavior:

- Preserve native species health and sustained flight, current nametag preference and normal player controls. Legacy caps are configurable compatibility options, not restored defaults.
- Classic remains default; Command/Disguise/Biomass are selectable server modes. Switching modes preserves owned forms/progression. Keep current armor-stand availability unless a configured blacklist excludes it.
- Keep transformation at 100 ticks initially. Support interruption through generation IDs and safe cleanup; expose original non-interruptible compatibility behavior. Acquisition policy during transition is explicit.
- Player equipment drives ordinary visible hands/armor; captured body/saddle gear is separate form state and cannot generate transferable inventory. Saved metadata and live action state remain separate.
- Self reset is always available where physically safe. Deleting an active form must reset atomically or leave the collection unchanged if reset cannot fit; never partially delete state.
- Intrinsic powers use server-validated actions and configurable cooldowns; item-based attacks retain ordinary item/ammunition requirements. Cooldowns survive form swaps/reconnects under a defined clock policy. Channels cancel on form/death/dimension change.
- Boss terrain damage, lethal native penalties and automatic conversions are implemented as explicit settings, off by default. Creaking invulnerability requires a valid defined linkage, not unconditional immunity. Native AI does not take control of the player.
- Biomass is opt-in; reset is free; insufficient balance rejects paid actions without partial effects; starter progression must be reachable. Costs, cooldowns and critical-capacity semantics need concrete tables before acceptance.
- Ambient sounds are an additional configurable feature. First-person/reduced-motion/acquisition effects get clear client settings. English/French coverage is the initial localization target, with placeholder parity tests.

## 7. Coverage and completion rules

Create a traceability manifest from every audit item: feature ID, provenance (legacy/current correction/modern/unfinished), owner, prerequisites, source evidence, expected behavior, scenarios, both-loader evidence and status. Split each prose bundle into finite checklist rows; do not hide unimplemented pieces behind a broad completed heading.

All registry forms get extraction/visible appearance, relevant movement/pose/action coverage and explicit equipment/variant applicability. Use systematic family coverage plus per-form smoke scenarios; pairwise combinations reduce cost but do not replace high-risk cases. Keep dedicated cases for dragon, Wither, Sniffer/Snifflet, illagers, native aquatics, flying forms and underwater-walking zombies.

For substantive fixes, require same baseline/candidate cases, old-defect failure, nearby preservation cases and smallest durable regression tests. For new features, show an absent or plausibly wrong implementation fails the oracle. AI tests need a control mob that demonstrably attacks a human; movement tests need actual input and authoritative positions; audio/visual tests need their own evidence.

Each merge batch requires domain checks, independent review of actual diff and expectations, source freeze, combined loader checks, evidence bundle and updated traceability. A skipped, flaky, infrastructure-failed or unsupported required case never becomes a pass. Retry records stay visible. The primary agent reviews evidence and public claims; nine agent approvals do not establish correctness.

## First executable milestone

Implement the small Morph Lab runner, one Fabric and one NeoForge client bridge, the evidence manifest/browser, and baseline dragon/Sniffer/Husk plus preserved bat/bee/nametag scenarios. Produce PNGs, one playable short clip and numeric failures. Prove two-client isolation and resource admission before distributing broad feature implementation.
