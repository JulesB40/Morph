# Vanilla mob trait audit (Minecraft 26.2.0.82)

This audit covers the vanilla `LivingEntity` forms available in the 26.2 NeoForge and Fabric runtime. The evidence was read from the generated ModDev source artifact:

`build/moddev/artifacts/minecraft-patched-26.2.0.82-sources.jar`

The audit separates traits that can be represented by the shared player hooks from stateful entity behavior that needs a dedicated implementation. Unknown and modded identifiers continue to use the normal player defaults.

## Implemented mappings

`FormTraits` now follows the relevant vanilla entity tags and source overrides:

| Trait | Forms covered | Evidence |
| --- | --- | --- |
| Sustained flying and no fall | allay, bat, bee, blaze, ender dragon, ghast, happy ghast, parrot, phantom, vex, wither | `Allay.travel`/`checkFallDamage`, `Bat.checkFallDamage`, `Bee.checkFallDamage`, `Ghast.travel`, `HappyGhast.travel`, `Parrot.checkFallDamage`, `Phantom.travel`, `Vex.setNoGravity`, and `EntityTypeTags/fall_damage_immune.json` |
| Water breathing | axolotl, copper golem, frog, guardian, elder guardian, turtle, glow squid, cod, pufferfish, salmon, squid, tropical fish, tadpole, nautilus, and the undead tag | `data/minecraft/tags/entity_type/can_breathe_under_water.json` and `undead.json`; the old iron golem entry was removed |
| Fall immunity | the members of `data/minecraft/tags/entity_type/fall_damage_immune.json`, plus flying ender dragon and vex forms whose native movement never accumulates fall damage | the local 26.2 tag and flying entity source hooks |

`MorphTraits` now covers the following passive families:

* The undead tag includes `camel_husk`, `parched`, and `zombie_nautilus`, which were missing. It drives poison/regeneration rejection and the water-breathing mapping.
* Fire immunity includes the native `EntityType.Builder.fireImmune()` living forms: blaze, ender dragon, ghast, magma cube, shulker, strider, vex, warden, wither, wither skeleton, zoglin, and zombified piglin. Warden was added during integration review.
* Water/rain sensitivity includes blaze, enderman, snow golem, and strider. Bee uses a separate counter matching `Bee.customServerAiStep`: damage begins after 20 consecutive ticks in water, with no rain damage.
* Daylight burning follows `data/minecraft/tags/entity_type/burn_in_daylight.json`: drowned, phantom, skeleton, stray, bogged, zombie, zombie villager, zombie horse, and zombie nautilus. Husk, wither, wither skeleton, skeleton horse, and zombified piglin are therefore not incorrectly made sun-sensitive.
* Land air loss applies to cod, salmon, pufferfish, tropical fish, squid, glow squid, tadpole, and nautilus. Guardian and elder guardian were removed from this set because their native classes survive on land; dolphin and axolotl retain their separate native moisture/air timers.
* Effect rejection now covers undead poison/regeneration, spider and cave spider poison, nautilus and zombie nautilus poison, parched weakness, silverfish Infested, slime Oozing, and wither/wither skeleton wither immunity. The old rule that made every effect fail on wither was removed. Armor stands also receive their native underwater-breathing tag.
* Native movement multipliers include tadpole and nautilus forms. The legacy per-form step-height modifier was removed because the copied native `STEP_HEIGHT` attribute is authoritative.
* Bee sting, cave-spider poison, and husk hunger durations now use the local difficulty where vanilla does. Existing pufferfish and wither-skeleton melee effects remain supported.

## Source-backed findings that remain outside the shared trait hook

These are real vanilla behaviors, but they are stateful attacks or interactions rather than passive species traits. They are recorded here so they are not mistaken for omissions in the mapping:

* Bee poison depends on difficulty and bee sting state; the shared melee hook reproduces the difficulty duration and stinger count, while hive state and the post-sting bee death lifecycle are not player traits.
* Pufferfish contact poison depends on puff state. The morph hook retains the existing representative poison effect because a player form has no native puff-state field.
* Guardian and elder guardian beam/mining-fatigue behavior, shulker levitation bullets, witch potion selection, evoker fangs/vex summoning, creeper swelling/explosion, enderman teleportation, breeze long jumps, wither/dragon ranged attacks, and aquatic entity AI are not passive mappings.
* Chickens have native fall immunity and a falling glide slowdown; they are intentionally kept outside the sustained-flight set. Frogs have native jumping AI while still receiving the water-breathing mapping.
* Dolphin moisture damage and axolotl dry-out use their own native timers. Water animals use different air thresholds, so their exact damage cadence is documented in the source classes rather than collapsed into one generic timer.

## Verification scope and limitations

The generated [per-form attribute table](MOB_ATTRIBUTES.csv) contains all 91 supported living forms, including armor stand. Both loader registry checks compare copied attributes, every registered potion effect's eligibility, fire/freezing immunity, underwater breathing and healing/harming inversion against real native entities. Spawn/AI-derived exceptions are explicit: cube size, phantom/wither-skeleton attack strength, and the closed shulker's +20 covered-shell armor. The audit also exercises real witch magic resistance, wither arrow immunity at half health, and shulker arrow immunity. Wither armor rendering follows the player's health ratio.

The armor, fire, freezing and potion checks cover passive resistance rules. They do not establish parity for every stateful defensive interaction, such as breeze projectile reflection, creaking heart linkage, armadillo rolling, dragon multipart damage, or shulker opening/teleportation. Forms retain player movement/flight controls and default species state; captured individual variants and randomized attributes are not stored.

The shared trait unit tests cover all sustained-flight IDs, the new 26.2 undead IDs, aquatic mappings, removal of iron-golem water breathing, and representative swim/land multipliers. The loader GameTests and the registry-wide native attribute audit remain the authoritative verification for spawned entity attributes, effect tags, freeze immunity, healing inversion, dimensions, and loader integration.

This file does not claim that a player can reproduce every entity's AI, attack state, animation, equipment rule, or data-driven conversion. Those require separate hooks and should be added only with a source-backed behavior test.
