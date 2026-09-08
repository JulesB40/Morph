# Legacy gameplay audit (1.16.5 baseline)

Audited from the preserved source and `legacy/1.16.5/src/main/resources/mobsupport.zip`. This describes the original implementation, not a claim that every listed feature has been ported to 26.2. The archive contains explicit per-entity definitions, including community mod support; trait existence alone does not assign it to any mob.

## Health and attributes

`common/config/ConfigServer.java` defaults to Classic mode, 100-tick morph duration, no disabled traits, and `classicUpgradeTraits=false`.

`api/morph/MorphVariant.writeSupportedAttributes` captures the acquired living entity's current attribute values, including modifiers, into `attr_<registry id>` NBT. Supported defaults are:

| Legacy attribute | Capture cap |
| --- | --- |
| generic.max_health | 20 health points (10 hearts) |
| generic.movement_speed | 0.1 |
| generic.knockback_resistance | None |
| generic.attack_damage | None |
| generic.attack_knockback | None |
| generic.attack_speed | None |
| generic.armor | None |
| generic.luck | None |
| horse.jump_strength | None |
| forge.swim_speed | None |
| forge.reach_distance | None |

Only attributes existing on the captured entity are recorded. `more` chooses the better value when merging acquisitions; it does not prevent a morph from lowering a player's health/speed. `common/morph/MorphInfoImpl.applyAttributeModifiers` installs Morph-owned additive modifiers equal to captured value minus player base. Other player modifiers remain. Attribute amounts interpolate between old/new states during transformation. Health changes preserve current/max-health ratio; changing form is not a free heal. Old-state modifiers are removed when no longer applicable.

The 26.2 collection initially stores entity IDs, not killed-entity NBT variants; default species attributes are a deliberate approximation unless variant capture is separately added. Legacy default maximum health does not give a golem 100 health or a wither 300 health: it caps either at 20.

## Exact vanilla trait mappings

The table below is grouped directly from archive `mob/*.json`. Identifiers omit `minecraft:`. Hostility and intimidation are listed separately to keep the mechanical differences clear.

| Behavior | Original assigned forms / parameters |
| --- | --- |
| Flight flap | bat, bee, parrot, vex (vertical impulse 0.42); phantom (0.52); ender_dragon (1.2). All slow in water. |
| Full flight | blaze, ghast, wither. Optional upgrade replaces flap for the six flap forms only when `classicUpgradeTraits=true`. |
| Climb | spider, cave_spider |
| Slow fall | chicken: downward velocity multiplier 0.6, resets fall distance |
| No fall damage | cat, ocelot, iron_golem, magma_cube; flight/flap also reset fall distance |
| Fire immunity | blaze, ender_dragon, ghast, magma_cube, shulker, strider, vex, wither, wither_skeleton, zoglin, zombified_piglin |
| Water/rain sensitivity | blaze, enderman, snow_golem, strider |
| Sunlight burning | drowned, phantom, skeleton, stray, zombie, zombie_villager |
| Undead | drowned, husk, phantom, skeleton, skeleton_horse, stray, wither, wither_skeleton, zoglin, zombie, zombie_horse, zombie_villager, zombified_piglin |
| Water breathing + land suffocation | cod, salmon, pufferfish, tropical_fish, squid, guardian, elder_guardian |
| Water breathing without land suffocation | iron_golem, turtle, plus all undead forms |
| Moist skin | dolphin: 2400-tick moisture reserve, restored by water/rain/bubbles. Dolphin does **not** receive water breathing. |
| Swim / grounded land multipliers | cod 3 / 0.1; salmon 3.5 / 0.1; pufferfish and tropical_fish 2 / 0.05; squid 2 / 0.025; guardian and elder_guardian 3 / 0.1; dolphin 4 / 0.1; turtle 2.5 / 0.05 |
| Float in water/lava | slime, magma_cube |
| Sink in water | iron_golem |
| Step height 1 block | donkey, drowned, horse, llama, mule, ravager, skeleton_horse, trader_llama, zombie_horse |
| Hit effect | cave_spider poison 140 ticks; pufferfish poison 60 ticks; husk hunger 140 ticks; wither_skeleton wither 200 ticks; all amplifier 0 |
| Effect resistance | wither all potion effects; wither_skeleton wither effect; undead poison and regeneration |
| Rideable, no saddle requirement | horse, llama, skeleton_horse, trader_llama, zombie_horse |
| Rideable, saddle required on captured variant | pig, strider |

Post-1.16 vanilla mobs have no original vanilla mapping. The bundled Caves & Cliffs Backport support supplies axolotl (moist skin, swim 4 / land 0.1, water breathing) and glow squid (swim 2 / land 0.025, water breathing with land suffocation); adopting these for modern vanilla IDs is a documented migration choice.

### Hostility and intimidation

`HostileTrait` cancels a mob's target assignment to the disguised player unless the player is that mob's revenge target or attacking entity. It is not blanket damage immunity. Assigned to blaze, cave_spider, creeper, drowned, elder_guardian, ender_dragon, enderman, endermite, evoker, ghast, giant, guardian, hoglin, husk, illusioner, magma_cube, phantom, piglin, piglin_brute, pillager, ravager, shulker, silverfish, skeleton, slime, spider, stray, vex, vindicator, witch, wither, wither_skeleton, zoglin, zombie, zombie_villager, zombified_piglin.

Intimidation steers eligible nearby creatures onto fleeing paths: cat/ocelot scare creepers (6 blocks); guardian/elder_guardian scare dolphins (8); llama/trader_llama scare wolves (24); polar_bear scares foxes (8); wolf scares skeletons (6), foxes (8), rabbits (10). Villager fear: drowned/husk/vex/zombie/zombie_villager 8; vindicator/zoglin 10; evoker/illusioner/ravager 12; pillager 15. Far/near flee speeds: creepers and skeletons 1.0/1.2; dolphins 1.0/1.0; wolves 1.5/1.5; foxes 1.6/1.4; rabbits 2.2/2.2; all villagers 0.5/0.5. Near speed applies within 7 blocks. Selection uses a range-wide bounding box with 3-block vertical expansion, random away positions within 16 horizontal / 7 vertical blocks, and requires a path leading farther from the player. Wolf skeleton matching uses AbstractSkeletonEntity (including stray and wither skeleton). Intimidated creatures also reject target assignment except retaliation.

## Source behavior details

- Climbing uses horizontal collision, clamps X/Z velocity to ±0.15, sets upward Y to 0.2 (sneaking stops Y), and clears fall distance.
- Flapping requires a fresh jump-key press while airborne, adds its impulse, and clears fall distance. Full flight is a distinct optional upgrade for flap forms, not the Classic default.
- Fire immunity extinguishes and cancels fire-tagged attacks; it does not remove unrelated damage.
- Water sensitivity attempts 1 drowning damage each wet tick; normal hurt cooldown still applies.
- Water breathing restores air by 4 per tick underwater. Land suffocation decrements an independent air counter (respecting respiration) and attempts 2 drowning damage at -20 before resetting air to 0.
- Moist skin attempts 1 dryout damage each dry tick after its timer expires.
- Sunburn checks day, brightness, sky access and a random vanilla-style threshold, ignites for 8 seconds. Head equipment prevents ignition and damageable helmets wear down.
- Undead removes/rejects poison and regeneration and grants water breathing. The original class does **not** reverse instant healing/harming.
- Floating adds Y=0.07 in water/lava unless flying. Sinking adds Y=-0.07 while descending slowly, uses Y=0.07 against a wall and gives +0.32 on exiting water.
- Rideable allows one other player to mount; sneaking or demorphing ejects riders. Saddle gates rely on captured entity state, absent from the initial ID-only port.
- Traits can fade across transitions; full-strength-only protections activate only at full strength. Modern immediate server state switching is not identical timing.

## Actual legacy limits and bugs

`TraitHandler` registers generic explosive immunity, magic immunity and arbitrary damage-source immunity, but no vanilla archive entry assigns these. Creeper has only hostility: there is no built-in explosion ability. Enderman has hostility and water sensitivity: no teleport ability. Ghast/blaze have flight and immunities, not projectile attacks. These should not be advertised as original features requiring parity.

The original `EffectAttackAbility.copy` copies only the effect ID, losing configured duration/amplifier, so normal copied instances fall back to 200 ticks/amplifier 0 despite explicit archive durations. A modern port may honor intended JSON parameters instead of preserving this bug. `EffectResistanceTrait` has an unreachable wildcard cleanup branch guarded by `effectObj != null`; wildcard application rejection exists, but existing-effect removal has a defect. Do not intentionally reproduce these bugs.

The source also contains biomass modes, upgrade systems, and external mob support infrastructure beyond the Classic mechanics described here. Their presence is not proof they are implemented in the 26.2 port.
