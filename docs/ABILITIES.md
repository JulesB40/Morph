# Initial shared traits

This is a limited trait milestone, not the original Morph ability system. Both loaders use the same `FormTraits` table and `MorphAbilities` lifecycle.

| Forms | Implemented traits |
| --- | --- |
| Bat, bee, parrot | Flight permission using Minecraft's normal flight controls; fall immunity while the form is active |
| Cod, salmon, tropical fish, pufferfish, squid, glow squid, axolotl, turtle, guardian, elder guardian, drowned | Replenished air underwater |
| All other forms | No additional traits in this milestone |

Flight does not force takeoff, change flight speed, grant invulnerability, or alter creative build permissions. Double-tap jump to start/stop flying. Selecting a non-flying form removes only Morph's own flight grant. Fall immunity ends when the flying form ends. Aquatic forms do not receive a lasting potion effect, and no air/land movement penalty is implemented. Dolphins deliberately do not get underwater breathing: they breathe air.

## Integration contract

- Call `MorphAbilities.tick(player, activeForm)` before each server player tick and immediately after synchronizing a changed form. Empty form means the player.
- Call `cleanup(player)` on death and logout. Also call it before discarding/replacing a player instance when appropriate.
- Cancel fall damage for living players when `preventsFallDamage(activeForm)` is true. Resetting accumulated fall distance during tick alone cannot guarantee protection from a same-tick landing.
- Install `me.ichun.mods.morph.ability.mixin.PlayerAbilitiesSaveMixin` on both loaders. It redirects the `Abilities.pack()` call inside `Player.addAdditionalSaveData`, creating a separate saved record with the original flight booleans. It never temporarily mutates the live player and never changes network ability packets.

Flight leases use player-instance weak keys and are not saved. Autosaving while morphed writes the original vanilla flight permission; reconnecting recreates the transient grant from the separately saved active form. All unrelated ability fields retain their current values.

## Other flight providers

Pre-existing flight, creative mode, and spectator mode are left intact. A detected external revocation is respected. Vanilla's `mayfly` boolean has no owner identity, so another mod setting it to `true` while Morph already holds it cannot be detected automatically. Integrations must call `preserveExternalFlight(player)` to transfer the grant, or register `addExternalFlightResolver(predicate)` while their own grant is active. Without such cooperation, overlapping grants made during Morph flight can be revoked on unmorph; do not claim universal flight-mod compatibility.

Fire immunity, spider climbing, hostile/passive AI changes, active attacks, biomass upgrades, hunger differences, dry-land suffocation for fish, and remaining original traits are not implemented here. Regression checks must exercise acquisition/selection separately from these trait helpers and verify that saving while flying does not leave persistent flight behind.
