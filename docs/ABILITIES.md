# Classic gameplay traits on 26.2

NeoForge and Fabric share the same gameplay implementation. Mappings come from the preserved original `mobsupport.zip`; see [the legacy audit](LEGACY_GAMEPLAY_AUDIT.md) for the exact original species lists and parameters.

## Health and attributes

Forms use the species' vanilla default attributes, applied as Morph-owned transient modifiers. Max health and movement speed retain the original default caps of 20 health points and 0.1. Supported values include health, movement speed, armor, knockback resistance, attack damage/knockback/speed, luck and jump strength. Existing equipment and other attribute modifiers remain in effect. Health changes preserve the current/max-health ratio, including when returning to human form. Attributes interpolate during the transformation. Saving writes baseline health units so reconnecting does not multiply a health change a second time.

The collection currently stores species IDs rather than individual acquired variants. Captured equipment, saddle state and unusual individual attribute modifiers are therefore not reproduced.

## Abilities and controls

| Forms | Behavior |
| --- | --- |
| Blaze, ghast, wither | Full flight; double-tap jump uses normal flight controls |
| Bat, bee, parrot, vex, phantom, dragon | Press jump again while airborne to flap; each fresh press adds the original impulse |
| Spider, cave spider | Climb walls through horizontal collision |
| Chicken | Slow falling and fall protection |
| Cat, ocelot, iron golem, magma cube | Fall protection; flight/flap forms also avoid fall damage |
| Aquatic fish, squid, guardians | Water breathing, faster swimming, slower land movement and eventual land suffocation |
| Turtle, iron golem, undead | Water breathing without fish-style land suffocation |
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
| Undead | Reject and remove poison/regeneration |
| Wither; wither skeleton | Reject all potion effects; reject wither respectively |
| Cave spider, pufferfish, husk, wither skeleton | Successful direct melee attacks apply poison, poison, hunger or wither |
| Original hostile forms | Mobs ignore the disguise unless retaliating against it |
| Original intimidating forms | Relevant nearby creatures flee using original species/range/speed mappings |
| Horse, llama, skeleton horse, trader llama, zombie horse | Another player can right-click to ride; sneaking or demorphing ejects Morph-owned passengers |

Full flight does not force takeoff, change flight speed, grant invulnerability or alter build permissions. Flapping follows the original Classic default rather than the previous alpha's full-flight substitute. The server chooses and validates flap strength; a client cannot request an arbitrary velocity.

The hit-effect durations follow the intended original configuration (cave spider/husk 140 ticks, pufferfish 60, wither skeleton 200), fixing an original copy bug that discarded configured durations. There is no creeper explosion, enderman teleport or blaze/ghast projectile ability in the original vanilla mappings, so none is added here.

## Integration and remaining differences

`MorphAbilities.tick` runs before the server player tick and delegates attribute/trait lifecycle. Shared mixins implement movement prediction, potion rejection, successful hit effects, AI targeting and mounting. Loader damage hooks call `MorphTraits.preventsDamage`; tick-only extinguishing is not enough to stop same-tick lava/fire damage. Cleanup removes only Morph-owned modifiers, flight grants, timers and passengers. Ability and health save mixins prevent transient morph state from leaking into vanilla saved values.

Original trait strength can fade during morphing; current passive traits switch with authoritative form selection, while health/attributes interpolate. Original swimmer camera fog effects, biomass modes/upgrades, configurable external mob support and captured-variant traits are still outside this implementation. Pig/strider riding remains unavailable because their original saddle prerequisite requires captured variant data. Post-1.16 mobs without a migrated mapping retain default player traits.

Pre-existing flight, creative mode and spectator mode remain intact. Other mods granting flight while Morph already owns it should call `preserveExternalFlight(player)` or register `addExternalFlightResolver(predicate)`; vanilla's flight boolean does not identify competing owners.
