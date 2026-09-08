# Classic gameplay traits on 26.2

NeoForge and Fabric share the same gameplay implementation. Mappings come from the preserved original `mobsupport.zip`; see [the legacy audit](LEGACY_GAMEPLAY_AUDIT.md) for the exact original species lists and parameters.

## Health and attributes

Forms use the species' vanilla default attributes, applied as Morph-owned transient modifiers. Alpha.8 removes the old 20-health and 0.1-speed caps. Supported values include health, movement speed, armor/toughness, knockback and explosion resistance, attack damage/knockback/speed, luck, jump strength, gravity, safe fall distance, fall damage multiplier, step height, movement efficiencies, oxygen bonus, burning time, absorption capacity, bounciness, air drag and friction. Existing equipment and other attribute modifiers remain in effect. Health changes preserve the current/max-health ratio, including when returning to human form. Attributes interpolate during the transformation. Saving writes baseline health units so reconnecting does not multiply a health change a second time.

These are species base attributes, not a copy of a killed individual's randomized statistics, equipment, age, size or temporary modifiers. Cube mobs use the default render size with vanilla size-derived health/armor/attack values; default phantom and wither-skeleton spawn-derived attack strength is initialized explicitly. Player controls and AI movement controllers differ, so matching movement attributes does not imply matching every mob's AI pathfinding or blocks-per-second speed.

The collection currently stores species IDs rather than individual acquired variants. Captured equipment, saddle state and unusual individual attribute modifiers are therefore not reproduced.

## Abilities and controls

| Forms | Behavior |
| --- | --- |
| Allay, bat, bee, blaze, dragon, ghast, happy ghast, parrot, phantom, vex, wither | Full flight; double-tap jump uses normal flight controls |
| Spider, cave spider | Climb walls through horizontal collision |
| Chicken | Slow falling and fall protection |
| Cat, ocelot, iron golem, magma cube | Fall protection; flight/flap forms also avoid fall damage |
| Fish, squid, tadpole, nautilus | Water breathing, swimming/land movement adjustments and eventual land suffocation |
| Turtle, frog, copper golem, undead | Water breathing without fish-style land suffocation |
| Guardian, elder guardian | Water breathing and faster swimming; survive on land |
| Zombie, husk, zombie villager, zombified piglin | Walk and sink underwater; cannot enter player sprint-swimming. Drowned retain swimming. |
| Dolphin | Faster swimming and moisture-dependent survival; still needs air |
| Axolotl, glow squid | Modern vanilla adoption of the original bundled backport mappings |
| Slime, magma cube | Float in water/lava |
| Iron golem | Sink in water |
| Original horse/llama variants, donkey, mule, drowned, ravager | One-block stepping |
| Original fire-immune forms | Extinguish and reject fire/lava damage |
| Blaze, enderman, snow golem, strider | Take damage from water and rain |
| Snow golem | Melts in environments with the vanilla snow-golem melting attribute |
| Sun-sensitive undead | Burn under exposed sun; water/rain and head equipment protect them, with helmet wear |
| Undead | Reject poison/regeneration; instant healing and harming are reversed |
| Wither, wither skeleton | Reject wither effects in addition to undead immunities |
| Spider/cave spider, nautilus/zombie nautilus | Poison immunity |
| Parched, silverfish, slime | Weakness, Infested, and Oozing immunity respectively |
| Witch | 85% resistance to native magic damage types; immunity to its own damage |
| Wither | Native damage-tag/friendly-mob immunity; arrows and player wind charges are blocked at half health, with powered armor rendering |
| Shulker | Closed-shell profile: 20 natural armor and arrow immunity |
| Bee | Poison sting on direct melee hits; water damage starts after 20 ticks in water |
| Cave spider, pufferfish, husk, wither skeleton | Successful direct melee attacks apply poison, poison, hunger or wither |
| Original hostile forms | Mobs ignore the disguise unless retaliating against it |
| Original intimidating forms | Relevant nearby creatures flee using original species/range/speed mappings |
| Horse, llama, skeleton horse, trader llama, zombie horse | Another player can right-click to ride; sneaking or demorphing ejects Morph-owned passengers |

Full flight does not force takeoff, grant invulnerability or alter build permissions. All flying forms now use sustained flight; old flap packets cannot add air-jump impulses. Flight uses player flight controls and speed, not each mob's AI steering controller.

Bee and cave-spider poison follow difficulty, while husk hunger follows local difficulty. Pufferfish retains a representative 60-tick melee poison and wither skeleton uses 200 ticks of wither. Stateful abilities such as creeper explosions, enderman teleportation, and mob projectiles remain separate work; see [the mob audit](MOB_TRAITS_AUDIT.md).

## Integration and remaining differences

`MorphAbilities.tick` runs before the server player tick and delegates attribute/trait lifecycle. Shared mixins implement movement prediction, potion rejection, successful hit effects, AI targeting and mounting. Loader damage hooks call `MorphTraits.preventsDamage`; tick-only extinguishing is not enough to stop same-tick lava/fire damage. Cleanup removes only Morph-owned modifiers, flight grants, timers and passengers. Ability and health save mixins prevent transient morph state from leaking into vanilla saved values.

Original trait strength can fade during morphing; current passive traits switch with authoritative form selection, while health/attributes interpolate. Original swimmer camera fog effects, biomass modes/upgrades, configurable external mob support and captured-variant traits are still outside this implementation. Pig/strider riding remains unavailable because their original saddle prerequisite requires captured variant data. Post-1.16 mobs without a migrated mapping retain default player traits.

Pre-existing flight, creative mode and spectator mode remain intact. Other mods granting flight while Morph already owns it should call `preserveExternalFlight(player)` or register `addExternalFlightResolver(predicate)`; vanilla's flight boolean does not identify competing owners.
