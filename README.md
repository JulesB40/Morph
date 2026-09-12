# Morph — Minecraft 26.2 port

Community port of [iChun's Morph](https://github.com/iChun/Morph) for **Minecraft Java 26.2**, with separate **NeoForge** and **Fabric** builds. Requires **Java 25**. This is an early development port with substantial implementation and validation still in progress.

**Status — 2026-09-12:** the current branch is a work-in-progress checkpoint. Feature implementation is paused, and the latest integrated changes have not completed both-loader runtime validation. See the [Done / In progress / To do list](docs/IMPLEMENTATION_TODO.md) for the current status and test blockers.

## Downloads and updates

Download the **NeoForge or Fabric JAR** from [GitHub Releases](https://github.com/JulesB40/Morph/releases). Releases are development prereleases while the port remains unfinished. Requires Java 25 and the matching loader; Fabric also requires Fabric API. Use the same Morph version on client and server.

Each successful push to the default branch, `codex/port-26.2`, automatically publishes both loader JARs after their builds and tests pass. NeoForge also runs native server GameTests and checks startup with the optional lab mod; Fabric compiles its client test harness. These checks do not replace full client, visual, and multiplayer validation. Failed runs and pull requests do not publish releases.

Both loaders read the base version from `gradle.properties` (`mod_version`). CI appends `.dev.<run number>.<short commit>` to the current alpha version, so every update has a distinct version inside its JAR. Each release includes `manifest.json` and `SHA256SUMS.txt` recording the source commit, workflow run, and download hashes. Maintainers can also run **Build 26.2** manually on the default branch.

## Current implementation

The source includes the following features. New collection, UI, sound, configuration and progression paths still need the runtime checks described below.

- In default **Classic** mode, kill a supported vanilla living mob to collect a form. Individual capture currently retains sheep color/age/custom name and slime size/custom name; other species use their default appearance. Supported native base attributes are captured separately from temporary effects and equipment modifiers.
- Press **[** to open the searchable selector, with translated names, previews, favorites and confirmed deletion. Hold **]** for the favorites wheel. Both keys are rebindable in Controls.
- The shared server authority validates ownership, collection revisions, requests and collision space before accepting changes. Appearance and exact variant transitions synchronize to clients.
- Transformations use the original black skin and six morph sound recordings, with a default duration of 100 ticks (five seconds). Duration and transformation sound playback are configurable. The deformation uses mesh interpolation; exact legacy part/box interpolation is not reproduced.
- Collections, favorites and nametag settings persist per player UUID. Death resets appearance while retaining the collection. The new descriptor format includes migration from this port's earlier species-only saves.
- Native pose dimensions, eye heights and attachment offsets follow the form. Actual-player pose, scaling and riding interactions remain part of runtime validation.
- Health, damage, armor, movement, knockback and other supported attributes follow native base values and captured variants. Morph uses transient modifiers and preserves the health ratio. Native registered attribute ranges apply; previous additional Morph caps were removed. Base speed equality does not imply identical native mob movement.
- Classic traits include sustained flight for bat/bee and other flying forms, climbing, swimming, immunities and weaknesses, attack effects, hostile disguises, intimidation and eligible rideable forms. Many stateful attacks and boss abilities remain missing. See [abilities and controls](docs/ABILITIES.md) and the current [TODO list](docs/IMPLEMENTATION_TODO.md).
- In the selector, click **Morphed nametag: Shown/Hidden** to toggle your tag for other players while transformed. You can also use `/morph nametag on`, `off`, or `toggle`. The setting is saved per player in the world, defaults to shown, and leaves normal-player nametags unchanged. Vanilla visibility/team rules still apply when shown.
- Compatible vanilla mob layers display live held items and armor, including handedness and item-use poses. Body/saddle display validation exists, but capturing that gear and verifying every rendered layer remain unfinished.
- Swimming and sprint-swimming helpers cover humanoid, villager/illager, quadruped, aquatic and other model families. Ordinary zombies walk underwater; drowned retain swimming. Wither head rotation, Sniffer middle legs, bird/illager poses and a controlled dragon wing clock have dedicated fixes. This is not an all-mob animation guarantee. See [swimming coverage](docs/SWIMMING_COVERAGE.md), [native animation probes](docs/ANIMATION_NATIVE_PROBES.md) and [Blockbench sources](art/blockbench/README.md).
- Native event-sound forwarding hooks are implemented for further testing, including hurt/death, movement and consumption sounds.
- **Biomass** has an opt-in ledger, kill gains, upgrade purchases and morph costs. Its gameplay validation, HUD, ability costs and reloadable economy definitions remain unfinished. This completes parts of an unfinished legacy concept as new work; Classic mode has no biomass costs.

There is a one-second selection cooldown and a limit of up to 256 collected entries, including variants. Returning to the player requires sufficient space. Clients submit validated actions rather than arbitrary entity NBT.

## Commands and configuration

Both loaders use the shared command implementation:

```text
/morph list
/morph select minecraft:pig
/morph reset
/morph nametag on|off|toggle
```

Operators can grant forms with `/morph grant minecraft:pig` or `/morph grant @s minecraft:pig`, select an owned form for a target with `/morph force @s minecraft:pig`, and reset a target with `/morph reset @s`. Exact-entry selection and operator removal are also implemented; targeted entity capture and bulk removal remain on the TODO list.

Server configuration is created at `config/morph/definitions.json` in the game/server directory. Defaults use Classic mode, 100-tick transformations and enabled morph sounds. The implemented policies include player/form filters and Command mode; Disguise mode is not enabled. Biomass requires both `"mode": "BIOMASS"` and `"biomass_opt_in": true` in the policy object. `/morph biomass status` reports progression and available purchase commands.

Administrators can inspect `/morph config status` and apply `/morph config reload`. Rejected reloads retain the previous valid configuration. These new configuration and command paths still need complete both-loader runtime verification.

## Building

From the repository root:

```powershell
# NeoForge 26.2.0.82, ModDevGradle 2.0.146, Gradle 9.2.1
.\gradlew.bat build
.\gradlew.bat runClient

# Fabric uses its own pinned toolchain and wrapper
cd fabric
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS use `./gradlew` in the corresponding directory. The configured toolchain resolver can provision Java 25 for builds. NeoForge outputs are in `build/libs`; Fabric outputs are in `fabric/build/libs`. Install only the JAR matching your loader, not the `-sources` JAR. Install the same loader and Morph version on client and server; Fabric also requires Fabric API. Do not install both Morph builds together. iChunUtil is not required by this port.

## Development status and limitations

Selected frozen snapshots have passed native Husk checks on both loaders, Fabric flight/nametag checks, NeoForge renderer recovery probes, and a real NeoForge server/two-client save-and-restart scenario. A native geometry candidate passed 1,638 pose comparisons across 91 mobs after reproducing 1,604 baseline mismatches. These results establish their stated scenarios, not correctness of every feature in the current branch.

The NeoForge lab's duplicate-package conflict is fixed, and its crouching test now compares against a native pig instead of expecting player-style shrinking. [Both loader build jobs passed at `69fb239`](https://github.com/JulesB40/Morph/actions/runs/34720694104); NeoForge passed all 20 native server GameTests both with and without the lab mod. Loaded-client equipment assertions still need verification. A previous Fabric harness result was invalidated by a log written outside the expected build directory; the working-directory correction still needs a frozen lab rerun. Full details are in the [current TODO list](docs/IMPLEMENTATION_TODO.md) and [retained pilot results](docs/MORPH_LAB_RESULTS_2026-09-12.md).

Major remaining work includes broader variants, player and modded forms, acquisition tendrils, first-person mob hands, stateful mob/boss abilities, native movement and riding parity, Disguise mode, the complete biomass UI/economy, legacy editors and addon compatibility. Model-by-model visual checks, current-protocol multiplayer tests, performance tests and final artifact validation are still required.

The versioned save format is separate from legacy `morph_save` data. **There is no 1.16.5 save importer yet.** The current loader adds strict decoding and a backup before migrating this port's schema-1 files; that migration path still needs final runtime validation. Use a new world or a copy for testing.

## Verification harness

[Morph Lab](tools/morph-lab/README.md) lets contributors submit isolated jobs using frozen source snapshots, separate game worlds/control channels, resource admission and owned-process cleanup. Reports retain source hashes, logs, native assertions, screenshots and tick-sampled videos. Agents can prepare and inspect jobs concurrently; rendered jobs are limited by measured memory/GPU capacity and do not share desktop input.

The lab is opt-in through `-PmorphLab=true`; its classes are excluded from release JARs. The [parallel implementation plan](docs/PARALLEL_IMPLEMENTATION_PLAN.md) describes the workflow, and the [coverage matrix](docs/MORPH_LAB_COVERAGE.md) records planned scenarios. Planned scenarios are not passed tests. Current lab startup issues are listed above.

## Repository layout

- `src/main`: NeoForge implementation plus explicitly shared vanilla/model code.
- `fabric`: independent Fabric build, using the shared model, save codec, selector, and rendering adapter.
- `testing` and `tools/morph-lab`: optional native probes, scenario contracts and the isolated job runner.
- `docs/IMPLEMENTATION_TODO.md`: current Done / In progress / To do inventory; older audits describe their original snapshots.
- `legacy/1.16.5`: untouched upstream source retained for porting reference; excluded from both builds.
- [Porting plan](PORTING_26_2.md), [dependency audit](docs/DEPENDENCY_AUDIT.md), and [rendering audit](docs/RENDERING_26_2_AUDIT.md).

Original work by iChun and upstream contributors. LGPLv3 license texts are retained in `COPYING` and `COPYING.LESSER` and included in generated JARs. NeoForge MDK template attribution is retained in `TEMPLATE_LICENSE.txt`.
