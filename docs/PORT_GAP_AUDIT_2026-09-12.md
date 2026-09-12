# Morph 26.2 missing-feature audit

Reviewed 2026-09-12 against port commit `8955f88` (alpha.8), using nine GPT-5.6 Luna reviewers with high reasoning and primary-agent source review. The original comparison is the preserved `legacy/1.16.5` snapshot of upstream commit `b6be1a31314fb1358b51e4aa70e4af5c2c77c4e5`, not an assertion about later upstream development. Native behavior references use the cached Minecraft 26.2 source artifact.

This is a source audit. No gameplay implementation was changed and no new runtime tests were run for this review. Existing test results are supporting evidence with their original scope, not proof of full feature parity. In particular, the 91-form registry check covers species attributes and selected passive rules; it does not establish that all 91 forms render, animate, move, or behave like their native mobs.

Status vocabulary:

- **Missing legacy feature:** behavior implemented in the original that has no equivalent in the port.
- **Partial:** part of the behavior exists, with specific missing cases.
- **Implementation issue:** a concrete source-level conflict or failure path; runtime reproduction is stated separately.
- **Modern addition:** native 26.2 behavior or a new convenience feature, not necessarily an original Morph feature.
- **Unverified:** support is uncertain because the relevant runtime evidence is absent.
- **Legacy unfinished:** scaffolding or disabled behavior in the original, not a working legacy feature to promise automatically.

## Working legacy features still missing or partial

Evidence paths below use these prefixes: **L** = `legacy/1.16.5/src/main/java/me/ichun/mods/morph/`; **LA** = `legacy/1.16.5/src/api/java/me/ichun/mods/morph/api/`; **P** = `src/main/java/me/ichun/mods/morph/`; **F** = `fabric/src/main/java/me/ichun/mods/morph/fabric/`. Line numbers refer to the audited snapshot.

| ID | Missing/partial feature | What remains | Source comparison |
| --- | --- | --- | --- |
| L01 | Captured individual variants | Store age, appearance/NBT, custom names, equipment, species-specific state and variant identity instead of one registry ID per species. | LA `morph/MorphVariant.java:44-50,640-680`; L `common/morph/MorphHandler.java:263-352`; P `model/MorphCollection.java:8-16,36-43` and `server/MorphService.java:131-134`. |
| L02 | Repeated-acquisition merging | Deduplicate matching variants, preserve multiple variants of a species, and merge improved acquired attributes under the configured policy. | LA `morph/MorphVariant.java:118-172,258-348`; L `common/morph/save/PlayerMorphData.java:45-75`; current storage has only sorted strings. |
| L03 | Player disguises | Acquire/select another player's identity, name/UUID and skin. A reset-to-self button already exists; it is not a substitute for other-player forms. | LA `morph/MorphVariant.java:380-478`; L `common/command/CommandMorph.java:132-143,169-180`; P `model/MorphCollection.java:31-34` rejects `minecraft:player`. |
| L04 | Modded mob forms | Remove the vanilla-only restriction only when compatible shape/render/trait adapters and bounded state capture exist. | L `common/morph/MorphHandler.java:280-301`; P `shape/MorphDimensions.java:33-39`; F `MorphFabric.java:49-56`. |
| L05 | Favorites and radial selection | Persist per-variant favorite state, toggle it, synchronize it and provide the radial favorites UI. | LA `morph/MorphVariant.java:640-680`; L `client/core/HudHandler.java:180-207,501-521` and `common/packet/PacketMorphInput.java:15-83`; P `ui/MorphScreen.java:39-61` has none. |
| L06 | Form/variant deletion | Add server-confirmed deletion, protection for the self form and cleanup when deleting the active entry. | L `client/core/HudHandler.java:325-388,427-439`; L `common/packet/PacketMorphInput.java:63-71`; P `model/MorphCollection.java` has no remove operation. |
| L07 | Legacy selector navigation | Restore variant navigation, next/previous-form keybindings, hold/release selection and the in-world HUD workflow. Current modal paging is a usable replacement for basic selection, not full interaction parity. | L `client/core/KeyBinds.java:16-30`; L `client/core/HudHandler.java:325-398,1353-1399`; P `ui/MorphUi.java:20-22,67-73`. |
| L08 | Selector presentation | Add live entity previews, translated species/custom names and favorite markers; restore localized group ordering and variant order. Current buttons show alphabetically sorted registry IDs. | L `client/core/HudHandler.java:684-854`; L `client/core/EventHandlerClient.java:122-146,169-171`; P `ui/MorphScreen.java:39-43`; P `model/MorphCollection.java:15,36`. |
| L09 | Command-only mode | Disable kill acquisition and optionally permit the selector according to configuration. | L `common/mode/CommandMode.java:24-49,115-119`; current P `server/MorphService.java:57-84,125-135` and F `MorphFabric.java:101-107` always use the same acquisition path. |
| L10 | Disguise mode | Automatically take the killed entity's disguise without building a persistent collection, with the mode's selector/acquisition restrictions. | L `common/mode/DisguiseMode.java:21-60`; no equivalent current mode. |
| L11 | Server/client configuration | Configurable morph duration, silent morphs, trait toggles/upgrades, attribute selection/merge/caps, size-recalculation policy, invisible-skin policy, selector position/scale/mouse behavior, radial scale, hand override and acquisition effects. Biomass-specific settings belong to the unfinished system below. | L `common/config/ConfigServer.java:24-81`; L `client/config/ConfigClient.java:23-50`; current timing, attributes and UI are fixed in code. |
| L12 | Player filters | Independent allow/deny policies for morphing and selector access; biomass filters if that mode is completed. | L `common/morph/MorphHandler.java:562-565`; L `common/mode/ClassicMode.java:39-69`; P `server/MorphService.java:57-72` has no configured player filters. |
| L13 | Mob and trait exclusions | Configurable mob regex blacklist (original default excludes armor stands), disabled traits and Classic trait-upgrade options. | L `common/config/ConfigServer.java:44,56-59,66-81,108-115`; L `common/mode/ClassicMode.java:83-107`; current mappings are static. |
| L14 | Admin command parity | Target-player forced morph/reset, acquire from an existing entity or player identity, optional bounded NBT, and unacquire individual/all variants. Fabric has target-player grant; NeoForge grant is self-only. | L `common/command/CommandMorph.java:116-185,197-255`; P `server/MorphService.java:190-215`; F `MorphFabric.java:127-182`. |
| L15 | Acquisition visual effect | Restore the killed entity's acquisition/tendril effect and its server-to-client payload. Transformation visuals already exist and are a separate effect. | L `common/morph/MorphHandler.java:230-233`; L `common/packet/PacketAcquisition.java:15-88`; current death acquisition only grants/synchronizes ownership. |
| L16 | Legacy save import | Define a tested importer for `morph_save`, player variants, attributes and any retained progression. Current schema 1 is an independent format. | L `common/morph/save/MorphSavedData.java:20-47`, `PlayerMorphData.java:100-139`; P `server/MorphSavedData.java:25-32` and `model/MorphCollection.java:21-28`. |

## Legacy systems that were already unfinished

These must not be advertised as working features lost from the original release:

- **Default/Biomass mode:** `L common/config/ConfigServer.java:96-101` forcibly replaces DEFAULT with CLASSIC. `L common/mode/DefaultMode.java:28-72` contains a debug kill path and commented intended flow; trait handling and ability-cost calculation remain TODO at `:120-130`.
- **Biomass upgrade purchases and ability UI:** resource loading is commented at `L common/resource/ResourceHandler.java:75-87`; upgrade requirements are TODO in `LA biomass/BiomassUpgradeInfo.java:13-23`; the purchase button is disabled and has an empty callback at `L client/gui/biomass/window/WindowBiomassUpgrades.java:40-50`; the abilities scene is empty.
- **If completed as new work:** implement biomass storage, advancement unlocking, kill-volume gain/efficiency, capacity and critical capacity, absorption size/reach limits, upgrade prerequisites/costs/levels, consumption, abilities, HUD and purchase UI, reloadable definitions, synchronization and save migration. Existing formulas/IDs are in `L common/mode/DefaultMode.java:132-200` and `L common/biomass/Upgrades.java:3-21`; class/JSON presence is not proof those flows worked.
- The old biomass-set command is commented out at `L common/command/CommandMorph.java:186-192`; do not count it as a working legacy command to restore.

## Further legacy parity gaps

| ID | Missing or partial feature | Evidence / scope |
|---|---|---|
| L17 | First-person mob hands and hand transformation | L `client/render/hand/HandHandler.java:47-276,391-436`; current equipment helpers only provide third-person state. |
| L18 | Morph-specific step, swim, fly, hurt, death, fall, eat/drink sounds and sound volume/pitch | L `common/morph/MorphInfoImpl.java:354-438` and legacy Entity/LivingEntity/Player mixins. Current `P model/MorphSounds.java:12-38` supplies transformation audio, not these forwarding hooks. Ambient calls were not established in the legacy implementation; ambient audio is an additional feature. |
| L19 | Smooth camera, eye-height and collision-size transformation | LA `morph/MorphInfo.java:89-155`; P `shape/MorphDimensions.java:26-30` and `shape/ShapeHooks.java:36-39` select destination dimensions immediately. Current visual deformation lasts 100 ticks. |
| L20 | Shadow-size transition | L `client/render/MorphRenderHandler.java:192-221`; no corresponding interpolation in P `client/MorphClient.java:53-75`. Actual dispatcher shadow appearance needs client verification. |
| L21 | Reloadable mob/trait definitions, NBT modifiers and hand metadata | L `common/resource/ResourceHandler.java:23-96`, `MobDataHandler.java:23-132`, `TraitHandler.java:15-86`, `NbtHandler.java:24-74`. Root `morph/` support JSON files are not loaded by current code. |
| L22 | Mob-data/trait and NBT editors, export/generation, resource reload/re-extraction commands | L `client/gui/mob/`, `client/gui/nbt/`, `common/command/CommandMorph.java:72-114`. Biomass UI remains a separate unfinished legacy system. |
| L23 | Public addon API, cancelable acquisition/morph events, resource-load events and synchronization/NBT callbacks | LA `IApi.java:24-115`, `event/MorphEvent.java:22-47`, `event/MorphLoadResourceEvent.java:5-25`; L `common/Morph.java:159-273` processes trait/mob/morphSync/variantNbtSetter/variantNbtReader IMC. No equivalent current extension contract; external-flight cooperation is only a narrow hook. |

The black transformation is **present**: original sound resources, skin, 12.5% fade-in / 75% deformation / 12.5% fade-out and cosine easing. Geometry is a mesh interpolation redesign, not exact legacy part/box interpolation. The middle phase captures `submitModel` only, so held items, blocks and arbitrary custom geometry are omitted (`P client/transition/MorphTransitionRenderer.java:143-170`). A byte-for-byte old renderer or iChunUtil dependency is not itself a requirement; behavioral parity is the goal.

## Source-level issues and multiplayer limits

These are source findings, not newly reproduced in-game failures.

- **Renderer recovery:** failed form extraction is quarantined until world/disconnect clearing; the current swimming-resource reload callback does not clear `FAILED_FORMS`. A repaired renderer/resource may remain unavailable until a world reset (`P client/MorphRenderSnapshots.java:25,38-43,92-96`; `client/MorphClient.java:32-35`).
- **Fallback visibility risk:** if transition capture and destination fallback both fail, `safeFallback` silently stops submitting while the caller cancels player rendering. The player can therefore disappear in that failure path (`P client/transition/MorphTransitionRenderer.java:118-140`; `client/MorphClient.java:61-65`). Needs a deliberately failing-renderer regression test.
- **Fabric broadcast scope:** appearance and nametag updates go to every connected player, including other dimensions (`F MorphFabric.java:28-43,111-117`). This is unnecessary global traffic compared with tracking-scoped delivery. It also means the absence of a start-tracking callback alone does **not** prove late observers have missing state: ordinary changes and joins already send it globally. A reviewer initially claimed that bug; primary review rejected that unsupported conclusion.
- **Observer cleanup:** NeoForge logout cleans server abilities/sounds but does not send observers an empty appearance. UUID client maps can retain old state after the entity disappears (`P server/MorphService.java:172-176`). No visible reconnect defect was reproduced.
- **Acquisition exclusions:** original server kill handling excluded fake/removed players; current NeoForge kill grant does not (`L common/core/EventHandlerServer.java:82-85`; `P server/MorphService.java:125-134`). Restore explicit policy alongside the missing modes/player filters; fake-player integrations were not run.
- **Multiplayer verification still needed:** two real clients covering first tracking, tracking loss/re-entry, dimension travel, reconnect, death/respawn, hidden nametags, team visibility and transition packet ordering. Codec round trips and a local client test do not cover these scenarios.
- **Save verification still needed:** actual server stop/restart/rejoin, legacy-file detection/import, unsupported schema handling and interactions with other attribute/flight mods. Existing ratio/save-unit tests are useful evidence and no health-save defect was confirmed in this audit.

## Animation, equipment and rendering checklist

| ID | Finding | Evidence and remaining work |
|---|---|---|
| A01 | **Ender Dragon rendering is unsupported by the current adapter** | Native `EnderDragonRenderState` extends `EntityRenderState`, whereas P `client/MorphRenderSnapshots.java:82` accepts only `LivingEntityRenderState`. The selectable form therefore falls back instead of displaying the dragon. Primary review verified the native superclass. This supersedes the weaker concern about dragon orientation. |
| A02 | Crouch, passenger, elytra, spin-attack and sleeping state transfer is incomplete | P `client/MorphRenderSnapshots.java:105-130` and `client/equipment/MorphEquipmentRendering.java:79-90` omit relevant pose/boolean fields consumed by native living/humanoid renderers. Add explicit supported pose mapping and verify each state; copied elytra angles alone do not enable the flying pose. |
| A03 | Chicken/parrot wings and broader action states need adapters | Detached entities are not ticked; only selected explicit helpers update special animation fields. Native chicken/parrot flap fields, allay dancing/spinning, vex charging, phantom flap/size, skeleton aggression, zombie conversion, creeper swelling, enderman agitation, ravager attack/roar, panda actions, feline/wolf sitting, equine eating/rearing, guardian target/tail/spikes, shulker peek and warden action states have no complete current control path. Some are variant or optional ability states, not animations that should always run. |
| A04 | Illager item layers/action flags are incomplete | P `client/equipment/MorphEquipmentRendering.java:63-77` maps bow/crossbow/attack poses but not aggression/riding/casting. Native vindicator/evoker layers are conditional, so copied held items alone do not establish their visibility. |
| A05 | Body gear and saddles | Current helper copies only main/off hand and head/chest/legs/feet (`:21-24`). Define body/saddle equipment and captured-state behavior for equines, llamas, camels, pigs, striders and modern rideables. New 26.2 slots are modern work, not slots that existed in the old six-slot enum. |
| A06 | Sniffer middle legs miss swimming fallback | P `client/animation/MorphOtherSwimming.java:19-22` searches middle-front/hind names, while native Sniffer/Snifflet use `right_mid_leg` and `left_mid_leg`. Primary review verified native names. Front/hind paddling is not full six-leg coverage. |
| A07 | All-mob visual verification | Normal and fast swimming helpers exist for humanoid, quadruped, villager/illager, aquatic and named-limb families. Bat/Bee flying states and Wither side-head rotation have explicit fixes. These do not establish correct rendering for every mob, variant, action and equipment combination. Run a model-by-model client matrix, including transitions and underwater walking zombies. |

Do not drive detached AI wholesale merely to animate models: attacks, sounds, world mutation and client/server disagreement need to remain controlled. Legacy state copying/ticking is evidence of behavior to compare, not an implementation prescription.

## Combat, attributes and modern mob behavior

- **Husk Hunger defect:** current `P ability/MorphTraits.java:94-96` ignores the empty-main-hand requirement and computes `(int)(140 * difficulty)`. Native `Husk.doHurtTarget` requires an empty hand and uses `140 * (int)difficulty`. At difficulty 2.5 the durations are 350 versus 280 ticks. Primary review verified the native code; a new before/after gameplay harness was not run in this read-only audit.
- **Hostile disguises miss newer forms:** the static `P ability/MorphInteractions.java:16-21` list omits bogged, breeze, creaking, parched and warden. Extend the defined disguise policy to modern forms.
- **Brain targeting is outside the current filter:** `P ability/mixin/MobTargetMixin.java:23-26` only intercepts `Mob.setTarget`. Native hoglin, zoglin, warden, breeze and creaking can use Brain attack-target memories. Add coverage for these paths before claiming all hostile mobs ignore a disguise; runtime targeting scenarios remain untested here.
- **New intimidation relationships:** native illagers avoid Creaking, but current `FEARS` has no Creaking rule. Legacy relationships already mapped should be retained.
- **Strider conditional fall behavior:** native Strider resets fall distance in lava; the current generic immunity mapping does not implement that conditional rule. Verify with actual lava landings before choosing the hook.
- **Pufferfish:** current direct melee always applies 60 poison ticks. Native contact damage/poison depends on puff state; puffing, contact-triggered effects and corresponding visuals remain missing.
- **Guardian/elder guardian:** thorns, beam attacks and elder mining-fatigue aura.
- **Shulker:** opening/closing, changing armor/immunity with shell state, levitation bullets and teleport rules. Closed-shell armor/arrow immunity already exist.
- **Wither:** startup invulnerability, regeneration, skull attacks and full lifecycle. Source immunities, half-health projectile blocking and powered visual flag already exist.
- **Enderman:** teleport-on-damage/rain, carried blocks and controlled active teleport.
- **Other stateful abilities:** creeper swelling/explosion; witch potion choices; evoker fangs/vex summons; blaze/ghast projectiles; skeleton/drowned ranged behavior; ravager roar; breeze wind charges/jump/reflection; warden sonic boom/darkness; Creaking heart linkage; armadillo curling defenses; dragon multipart damage and attacks.
- **Species lifecycle/state choices:** bee sting exhaustion/death/hive state, tame/sit/breed states, transformations/conversions, age/size and other captured variants need explicit scope. These are not automatically appropriate player penalties or autonomous AI behaviors.
- **Additional attribute policy:** current 23-attribute list is not every registered attribute. Native Dragon camera distance (16), flying speed and variant scale are meaningful missing candidates. AI-only follow/tempt ranges and player-specific reach/mining attributes require a deliberate rule, not indiscriminate copying. Base attribute equality does not prove matching physical movement.

The stateful attacks above are **modern/native feature completion**, not a claim that every one worked in original Morph. Passive fire/freezing/effect rules, native default health/armor and selected attack effects are already implemented and should not be listed as wholly absent.

## Movement, collision and riding checklist

- **Species flying speeds:** full flight works, but uses player `Abilities.flyingSpeed`. Copying `MOVEMENT_SPEED` does not reproduce Bee/Parrot/Allay flight speed (`P ability/MorphAttributes.java:20-32`; `MorphAbilities.java:51-67`). Measure acceleration and blocks/second under player control before defining native-equivalent values; setting `FLYING_SPEED` alone will not change player flight.
- **Species swimming speeds:** current `P ability/MorphTraits.java:196-208` multiplies player movement using largely legacy values. Fish/dolphin/turtle/nautilus travel controllers differ from player travel. Ground, sprint, water, fast-swim and air speed need measured scenarios; the 91-form attribute comparison does not supply these.
- **Flight in water:** legacy Fly/FlightFlap abilities optionally slowed forms in water; current active player flight bypasses fluid movement hooks (`P ability/MorphTraits.java:168-174`). Preserve sustained controls, but decide whether the original water slowdown should return.
- **Pose dimensions:** only standing/crouching use morph dimensions, with crouch forced to five-sixths height. Swimming, sleeping, elytra, spin and dying retain player dimensions/eyes (`P shape/MorphDimensions.java:26-31`). Fit checks inherit this limitation. This is physical collision work as well as animation work.
- **Passenger placement:** shared dimension reconstruction retains width/height/eye height but drops native attachments (`P shape/MorphDimensions.java:47-52`). Riding admission exists, but no native mount rider-position hook replaces player placement. Verify horse/llama passenger offsets and dismount clearance.
- **Saddled pig/strider riding:** missing working legacy ability. LA `mob/trait/ability/RideableAbility.java:67-85` and legacy Pig/Strider support JSON require saddle state; current `P ability/MorphInteractions.java:22-34` only lists five equine/llama forms. Modern camel/happy-ghast/nautilus riding is additional work.
- **Vex:** native no-gravity/no-physics flags are not reproduced by granting ordinary player flight. Wall passage is an optional ability requiring an explicit collision policy.
- **Edge cases to verify:** bubble-column equivalence to `isInWater`, chicken glide in water (current excludes it), lava landings for strider, cramped-space reset, riding cleanup and pose changes. These were not reproduced in this audit.
- **Transition traits and underwater view:** original transition trait fading and swimmer camera fog remain documented omissions (`docs/ABILITIES.md:55`). Define how capabilities change during the existing five-second visual transition.

Spider climbing, copied step-height/gravity/jump/fall attributes, slime/magma floating, iron-golem sinking, zombie underwater walking restrictions and flight lease cleanup already exist. Their presence should be retained while expanding measured coverage.

## Release, documentation and verification backlog

- **Performance:** profile total client frame time, capture/deferred rendering, allocation/GC, server tick time and many simultaneous morphs. `docs/STABILITY.md` measures only the interpolation kernel (2,048 vertices), not whole-game FPS. Preserve its useful before/after allocation benchmark; do not claim no meaningful comparison exists anywhere in the project.
- **Crash/compatibility matrix:** exercise every vanilla renderer, reload failures, shaders/resource packs, third-party renderers, attribute/flight mods and dedicated-server clients. Existing safe fallbacks do not guarantee crash freedom.
- **Release installation:** validate fresh standalone installs for both loaders, server/client version mismatch handling, upgrades and old saves. Add source-linked artifact checksums and a repeatable release procedure. Development CI already builds jars; absence of a publication workflow is release polish, not a missing gameplay feature.
- **CI:** both loaders already have build/test paths. NeoForge explicitly invokes its server GameTests. Fabric's `configureTests(enableGameTests = true)` wires its GameTest run into Gradle `check`, hence `build`. Primary review inspected cached Loom 1.17.20 `FabricApiTesting` bytecode and rejected the reviewer's claim that Fabric CI lacks server GameTests merely because the workflow says `build`. All-mob client and real multiplayer CI remain absent.
- **Evidence provenance:** existing alpha.8 records report NeoForge 44 unit / 20 server tests and Fabric 37 unit / 12 server tests. `docs/VALIDATION.md` records three Fabric client scenarios, before the final Wither/shulker defense edits. A log timestamp before a commit is normal and does not prove different code was tested; the missing piece is a durable tested-tree hash/manifest. Preserve source revisions, test commands and logs for future fixes.
- **Translations/accessibility:** only English translations ship; several command messages and registry-ID labels remain hardcoded. Add translated UI/messages and readable localized form names. Search/filter, configurable sorting and additional accessibility controls are useful additions, not all original features.
- **Stale documentation:** root README still mentions old health caps and flapping; Fabric README names alpha.3 and old flight behavior. Current alpha.8 has native default health and sustained flight. Historical validation sections should be clearly dated rather than mistaken for current requirements.
- **Packaging:** NeoForge includes gated GameTest classes in its main artifact; separating test fixtures is release polish. Root `build.bat` only builds NeoForge, so a convenient both-loader build command would help.

## Deliberate differences and scope decisions

- The user requested native health/attributes and actual bat/bee flight. Removing original caps and replacing flaps with sustained flight are accepted changes, not omissions to undo.
- The 256-form bound and one-second selection cooldown are current design decisions. Legacy generally blocked selection for the entire transition. Decide whether interruption is supported, then test chained transitions, sounds, traits and collision accordingly.
- Public self-service commands are intentional convenience; the missing server mode/filter policy is separate from operator-only grant operations.
- Biomass completion, ambient mob sounds, search/custom ordering and autonomous/native active abilities must be labeled as new or unfinished work rather than promised original parity.
- Modded entities are rejected by both loaders through shared dimension validation. An initial reviewer claim that NeoForge accepts them was rejected after checking the delegated validator.

## Recommended implementation order

1. Fix source-confirmed defects: dragon renderer acceptance, Sniffer middle legs, Husk Hunger rules, missing pose/action flags, renderer fallback/reload recovery and modern hostility targeting coverage. Add small meaningful regressions plus client scenarios for visible behavior.
2. Finish movement measurements and equipment/riding state; validate all vanilla model families in normal, fast-swim, air, crouch, riding and transition states on both loaders.
3. Restore variants and captured state, then favorites/deletion, selector previews/navigation, first-person hands, mob sounds and acquisition tendrils.
4. Restore configuration/modes/admin commands, reloadable support data, addon contracts and old-save migration.
5. Add chosen modern mob abilities with explicit controls, costs/cooldowns and server authority; finish multiplayer, performance and release-install verification before describing the port as complete.

The nine audit domains were collection/UI, progression/configuration, render/transitions, animation/equipment, movement/physics, traits/combat, multiplayer/saves, audio/integrations, and release/validation. Findings were consolidated and challenged by the primary agent; reviewer agreement and passing existing tests are not guarantees of exhaustive coverage.
