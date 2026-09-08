# 26.2 development alpha validation

Date: 2026-09-08. This report covers the first implemented milestone, not original Morph feature parity.

## Reproducible builds

| Target | Pinned toolchain | Local result |
| --- | --- | --- |
| NeoForge | Minecraft 26.2, NeoForge 26.2.0.82, ModDevGradle 2.0.146, Gradle 9.2.1, Java 25 | `gradlew.bat build runGameTestServer` passed |
| Fabric | Minecraft 26.2, Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.17.20, Gradle 9.5.1, Java 25 | `fabric/gradlew.bat -p fabric build` passed, including native GameTests |

Both builds use published dependencies without `mavenLocal()`. The legacy source is not compiled or packaged. NeoForge's MDK baseline is commit `321f8e77e4cde74541368ceeb57620ce4410877b`. The independent wrappers avoid forcing incompatible Gradle plugin requirements into one build.

## Automated checks

NeoForge alpha.3: **21 JUnit tests**, **18 Morph GameTests** and one vanilla control passed. Fabric alpha.3: **12 shared JUnit tests**, **10 Morph GameTests** and one Fabric control passed. Shared model tests run on both targets; these counts are not all distinct test cases.

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

Still missing: individual variants, favorites/deletion, player forms, modded entities, exact legacy part/box animation correspondence, mob first-person hands, variant-dependent traits, saddle prerequisites and upgrades, biomass progression, alternate modes, legacy editors, public addon compatibility, and 1.16.5 save import. Special poses retain vanilla player geometry; only standing and crouching use form dimensions. Overlapping flight grants from other mods require the explicit integration contract in ABILITIES.md.

Next release gate: complete the two-client matrix and remaining renderer/ability cases before labeling this a playable release rather than a development alpha.

## Alpha.2 transformation and audio checks

Both loaders now use explicit transformation payloads, a 100-tick visual timeline, and the six unchanged original OGG samples. The server schedules the positional sound at tick 20. Added integration checks cover rejection, accepted selection, redundant selection/synchronization, delayed sound consumption, and reset. NeoForge also exercises login and logout audio lifecycle. Three shared renderer tests cover eased timing, endpoint preservation, interpolation, and unmatched quad collapse.

Fabric manual checks verified human-to-pig and pig-to-human black transitions and final appearances. NeoForge manual checks verified a persisted pig appearance and the black return-to-human transition, ending on the normal player model. The intermediate geometry was captured with the local test world's tick rate temporarily reduced to 5, then restored to 20: [Fabric transition screenshot](screenshots/fabric-transformation.png). No missing sound/texture or transition renderer errors appeared in either client log. Sound scheduling and asset loading were verified; this automated session did not record/listen to speaker output.

The new mesh interpolation approximates the legacy box/part algorithm. First-person hands remain vanilla, collision dimensions switch at server selection, and item/block/custom geometry features are omitted during the fully black middle phase. A late observer receives the current destination appearance without replaying the transition. Real two-client visual/audio synchronization remains unverified.

## Alpha.3 health, attributes and Classic traits

Both loaders share the original default attribute policy and trait mappings documented in ABILITIES.md. Health transitions preserve injury ratio. Autosaves normalize health into unmorphed units and omit owned transient attribute modifiers, with a real save/load regression check. Tests cover pig/bat health, the original 20-point cap, zombie combat values, external modifiers, death safety, and animated select/reset.

Passive/action integration checks exercise actual loaded-player damage, climbing, undead effect rejection, attack effects, fish dryness versus turtle safety, flap validation, hostile target refusal and retaliation, and player mounting/ejection. Intimidation runs as a priority 1 movement goal and is tested with a real creeper fleeing a cat disguise on an isolated platform. The mock network fixtures now mark clients loaded so login protection cannot mask damage failures.

NeoForge manual validation observed 10 human hearts becoming 5 pig hearts and returning to 10: [pig health screenshot](screenshots/neoforge-pig-health.png). This exposed a false damage flash from vanilla health packets during rescaling. The fix sends the changed maximum attribute before an explicit health conversion message, preserving real pending damage while suppressing conversion-only hurt animation.

Health and attributes use vanilla default species values, not captured individual variants. The original default health cap is 20, so boss forms do not gain their uncapped boss health. Legacy Forge reach has no direct shared equivalent and is not copied; swimming uses shared movement hooks. Two-client packet latency, all mob-specific environments, and mod compatibility still require broader release testing.

Final Fabric client check verified persisted pig form at five hearts, reset to ten hearts, and a new pig conversion finishing at five hearts without the earlier camera tilt/false hurt flash. All health/attribute packet registrations loaded successfully. The flight/flap keyboard path and every special environment were not manually exercised; authoritative flap behavior and damage rules are covered by the integration tests above.

## Alpha.4 equipment, swimming and stability

NeoForge: 36 unit tests and 20 server GameTests passed. Fabric: 27 unit tests and 12 server GameTests passed. Both loader artifacts include the animation resources and shared equipment code; the Fabric client test mod is excluded from the shipping JAR. The new server sweep exercises more than 50 supported vanilla living adapters, finite health/attributes, cleanup and unsupported identifiers.

Fabric's client GameTest creates an integrated water pool and holds actual forward/sprint input through Fabric's test API. It exercises pig, cow, villager, pillager, cat, wolf, horse, spider, drowned, dolphin, turtle, guardian, squid, axolotl and zombie. The first 14 must enter fast swimming and provide a valid matching render snapshot; ordinary zombies must reject swimming. Captures wait for the 100-tick transformation to finish. Run it with `cd fabric; ./gradlew runClientGameTest` on a machine with a graphics environment. This is one client test scenario covering 15 forms, not 15 independent test methods.

Manual Fabric inspection verified a zombie wearing full diamond armor and holding a sword and shield: [equipment screenshot](screenshots/fabric-zombie-equipment.png). Both development clients started successfully with the shared swimming configuration. Blockbench 5.1.6 was installed; both the normal and fast quadruped clips were imported, played and saved in the editable [source project](../art/blockbench/swim_reference.bbmodel).

Visual review caught a double rotation on illagers: their state inherits `HumanoidRenderState`, so both the humanoid and illager hooks ran. The humanoid hook now explicitly excludes illagers. Aquatic adapters also needed explicit time/movement inputs for native squid, guardian and axolotl animation fields. Ordinary zombie water-jump input is blocked while grounded jumps and external velocity remain intact; drowned swimming is preserved.

A focused pillager/villager/drowned client rerun passed after the rotation fix. It checks the actual renderer pose matrix for a horizontal pillager/villager body; drowned deliberately retains its native swimming lean. Corrected captures: [pillager](screenshots/swimming/0003_morph-fast-swim-pillager.png), [villager](screenshots/swimming/0002_morph-fast-swim-villager.png), [pig](screenshots/swimming/0000_morph-fast-swim-pig.png), [squid](screenshots/swimming/0012_morph-fast-swim-squid.png). The same source rig provides both editable Blockbench clips.

See [swimming coverage](SWIMMING_COVERAGE.md) for family-specific behavior and remaining visual gaps, and [stability measurements](STABILITY.md) for the measured interpolation allocation/CPU changes. The test matrix does not cover every mob variant, two real networked clients, shaders or arbitrary mod combinations.


## Alpha.5 nametag toggle

Added the selector's **Morphed nametag: Shown/Hidden** button and `/morph nametag on|off|toggle`. The preference is authoritative, saved per player in the overworld data, and included in appearance synchronization for the subject and observers (including initial tracking/join). Missing fields in older saves default to shown. Hiding also suppresses below-name score text during morph rendering and transformation endpoints; returning fully to player form restores vanilla behavior. Shown continues to respect vanilla visibility/team rules.

Both builds passed: NeoForge 37 unit tests and 20 server GameTests; Fabric 30 unit tests and 12 server GameTests. The Fabric live client scenario exercised command-to-packet synchronization, renderer snapshots with observer-style name/score fields, both selector-button directions, and reset. A pig sprint-swim regression also passed. [Selector screenshot](screenshots/nametag-toggle.png). Shared codec tests cover old-save defaults and independent player preference persistence. A real two-client nametag session was not performed.

Use alpha.5 on client and server: NeoForge's protocol is now 5 and Fabric's appearance channel is versioned as `appearance_v2` to avoid decoding the old packet layout as the new one.

## Alpha 6: Wither side-head rotation

The shared morph snapshot now supplies world-space side-head yaw from the copied body yaw plus relative look yaw, and copies look pitch. Unticked adapter head arrays previously stayed at zero, making the heads face south. This applies to both loaders and transition snapshots. A regression test runs the vanilla Wither model across 63 body/look/pitch combinations and asserts that both side heads match the center head. No new in-game visual verification is claimed for this fix.

## Alpha.8 native mob attributes and passive traits

A GPT-5.6 Luna subagent with high reasoning audited species mappings; integration review and registry tests corrected additional omissions. Both loaders now pass checks across all 91 supported vanilla living forms: copied native attributes, every registered potion effect's eligibility, fire/freezing immunity, underwater breathing and instant healing/harming inversion. The generated values are in [MOB_ATTRIBUTES.csv](MOB_ATTRIBUTES.csv), and [MOB_TRAITS_AUDIT.md](MOB_TRAITS_AUDIT.md) records source evidence and remaining stateful behavior gaps.

Final validation: NeoForge build, 44 unit tests and 20 server GameTests passed; Fabric build, 37 unit tests and 12 server GameTests passed. Server checks additionally verify health ratios across every form, cleanup, native witch magic damage reduction, powered Wither arrow immunity, closed-shell shulker armor/arrow immunity, and grants/cleanup for all 11 flying forms. A Fabric client run passed three scenarios: pig sprint-swimming, nametag controls, and actual bat/bee double-jump takeoff and sustained hovering with live animation states. That client run preceded the final Wither/shulker defense additions; their final server tests and builds passed, while powered Wither visual verification remains pending.

This is species-default parity for the tested attributes and passive rules, not parity for every mob AI action or captured individual variant. Flight continues to use player flight speed and controls. Client tests do not cover every mob visually or third-party mod combinations.
