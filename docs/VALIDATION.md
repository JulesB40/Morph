# 26.2 development alpha validation

Date: 2026-09-08. This report covers the first implemented milestone, not original Morph feature parity.

## Reproducible builds

| Target | Pinned toolchain | Local result |
| --- | --- | --- |
| NeoForge | Minecraft 26.2, NeoForge 26.2.0.82, ModDevGradle 2.0.146, Gradle 9.2.1, Java 25 | `gradlew.bat build runGameTestServer` passed |
| Fabric | Minecraft 26.2, Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.17.20, Gradle 9.5.1, Java 25 | `fabric/gradlew.bat -p fabric build` passed, including native GameTests |

Both builds use published dependencies without `mavenLocal()`. The legacy source is not compiled or packaged. NeoForge's MDK baseline is commit `321f8e77e4cde74541368ceeb57620ce4410877b`. The independent wrappers avoid forcing incompatible Gradle plugin requirements into one build.

## Automated checks

NeoForge alpha.2: **16 JUnit tests**, **12 Morph GameTests** and one vanilla control passed. Fabric alpha.2: **8 shared JUnit tests**, **4 Morph GameTests** and one Fabric control passed. Shared model tests run on both targets; these counts are not all distinct test cases.

- Ownership rules, duplicate/bounded collections, one-second cooldown, reset behavior, and versioned restore.
- NeoForge packet round trips and rejection of oversized IDs/invalid collection lengths.
- UUID-isolated save codec round trips, malformed form IDs, and unsupported schema rejection.
- Actual mob death acquisition through each loader's registered event.
- Server command/selection validation, including unsupported/nonliving/unowned forms and spectator restrictions; Fabric's namespaced command syntax is executed in-game.
- Real player dimensions, eye height and reset. NeoForge additionally checks tall-form rejection under ceilings and safe crouch-to-stand transitions.
- Flight grant/cleanup and pre-existing flight preservation. NeoForge verifies actual player-save NBT omits temporary flight permission while the live grant remains intact, and survival-to-adventure transitions retain the form's flight.
- Fall protection ends after resetting. NeoForge aquatic breathing is checked using real player ticks underwater, with air decreasing after reset.

GameTests use isolated generated worlds and mock network connections provided/configured for the test environment. They verify server behavior; they do not substitute for a real two-client multiplayer session. NeoForge tests register only when `morph.enableGameTests=true`; Fabric uses a separate testmod excluded from its shipping JAR.

Local reports are generated under `build/test-results`, `build/gametest-smoke.log`, and Fabric's test output directories. GitHub Actions builds and tests both targets from checkout and uploads each loader's JARs.

## Manual client checks

Fabric was launched on Windows using Java 25 and its normal development client:

- Created a new local survival world with commands enabled.
- Granted `minecraft:pig`, opened the selector using a rebound key, selected the form, and observed the server-confirmed checkmark.
- Verified the pig appearance from both third-person camera directions. [In-game screenshot](screenshots/fabric-pig.png).
- Saved and reloaded the world with the same development identity. Both the owned form and selected appearance persisted.
- Used the selector's return-to-player action and observed the normal player model return.

The French test keyboard displays the default `[` binding as `^`; rebinding through Controls worked. Fabric's development run now uses a stable `MorphTester` name so its offline UUID is repeatable across launches.

NeoForge client startup and resource loading passed. In a new local survival world with commands enabled, `/morph grant minecraft:pig` and `/morph select minecraft:pig` succeeded; eye height changed, and the pig rendered in both third-person views. [In-game screenshot](screenshots/neoforge-pig.png). The close camera in the front-view screenshot is caused by nearby tree geometry. NeoForge's selector input itself was not manually exercised; its shared screen was verified on Fabric and its payload codecs were unit-tested.

## Bugs caught during validation

1. `EntityType.getBaseClass()` returns the generic Entity class in 26.2; using it for living-form validation rejected all mobs. Validation now examines a detached entity instance and caches safe dimensions.
2. Fabric's initial unquoted string command argument could not parse namespaced IDs. Replaced it with `IdentifierArgument` and added a command regression test.
3. The new rendering pipeline accesses entity IDs during extraction. Detached render adapters now receive their owner's stable ID without being inserted into the world.
4. Mixin classes must use a dedicated package; utility classes were separated to prevent illegal class loading.
5. Render adapter caches are cleared on client-world changes, preventing retention of old levels.
6. A survival/adventure switch reapplies vanilla flight defaults. Flight ownership now distinguishes that mode transition from an external revocation.

## Remaining validation and feature work

Not yet verified: two real clients on a dedicated server, late tracking/reconnect across all dimensions, Vulkan rendering, resource packs/shaders, all vanilla renderers, mod interoperability, or a standalone launcher loading the produced JAR outside development. No claim of full release readiness is made.

Still missing: individual variants, favorites/deletion, player forms, modded entities, exact legacy part/box animation correspondence, mob first-person hands, complete traits and active abilities, biomass progression, alternate modes, legacy editors, public addon compatibility, and 1.16.5 save import. Special poses retain vanilla player geometry; only standing and crouching use form dimensions. Overlapping flight grants from other mods require the explicit integration contract in ABILITIES.md.

Next release gate: complete the two-client matrix and remaining renderer/ability cases before labeling this a playable release rather than a development alpha.

## Alpha.2 transformation and audio checks

Both loaders now use explicit transformation payloads, a 100-tick visual timeline, and the six unchanged original OGG samples. The server schedules the positional sound at tick 20. Added integration checks cover rejection, accepted selection, redundant selection/synchronization, delayed sound consumption, and reset. NeoForge also exercises login and logout audio lifecycle. Three shared renderer tests cover eased timing, endpoint preservation, interpolation, and unmatched quad collapse.

Fabric manual checks verified human-to-pig and pig-to-human black transitions and final appearances. NeoForge manual checks verified a persisted pig appearance and the black return-to-human transition, ending on the normal player model. The intermediate geometry was captured with the local test world's tick rate temporarily reduced to 5, then restored to 20: [Fabric transition screenshot](screenshots/fabric-transformation.png). No missing sound/texture or transition renderer errors appeared in either client log. Sound scheduling and asset loading were verified; this automated session did not record/listen to speaker output.

The new mesh interpolation approximates the legacy box/part algorithm. First-person hands remain vanilla, collision dimensions switch at server selection, and item/block/custom geometry features are omitted during the fully black middle phase. A late observer receives the current destination appearance without replaying the transition. Real two-client visual/audio synchronization remains unverified.
