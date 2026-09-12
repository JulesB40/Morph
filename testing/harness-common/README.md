# Shared native-server probes

`src/main/java/me/ichun/mods/morph/lab/HuskDamageProbes.java` belongs only in loader test source sets. Do not include it in the published Morph jar. It imports Minecraft, Netty and Gson, with no loader or production Morph imports.

Register one GameTest per `HuskDamageProbes.Case` and invoke `HuskDamageProbes.run(helper, scenario, adapter)`. The adapter selects the authoritative Husk form, returns that player's active form, and resets it afterward. Use the loader's installed form resolver; do not install a replacement global resolver. No network join is needed: the probe uses the existing stability-test pattern of a real survival `ServerPlayer` with an embedded connection. This is a real-server damage-hook test, not an end-to-end client attack/network test.

The default adapter `damage` calls `LivingEntity.hurtServer` with a direct player-attack source. It must execute the installed damage pipeline. Adapters may override that action for a loader bridge, but must never invoke `MorphTraits.afterDamage` or inject Hunger themselves. Both damage acceptance and actual health loss are required before the effect comparison is evaluated.

Each case attacks separate fresh cows using a native Husk and the morphed player. Both attackers occupy the same block and both attacks/readbacks happen synchronously in one server tick. The native Husk's actual `doHurtTarget` effect supplies the duration oracle; no candidate helper or duplicated duration formula computes the expectation. Damage amounts need not match, since this scenario covers Hunger, not attack attributes.

| Case | Requirement | Expected alpha.8 defect |
| --- | --- | --- |
| `EMPTY_HAND_DURATION` | Immediate Hunger duration and amplifier equal native after successful empty-hand damage | Duration truncation differs |
| `HELD_ITEM` | A main-hand stick prevents Hunger despite successful damage | Morph incorrectly applies Hunger |
| `OFFHAND_ITEM` | An offhand stick leaves the empty-main-hand behavior intact | Same duration defect; preserves main/offhand distinction |
| `REJECTED_DAMAGE` | Invulnerable victims reject damage and receive no Hunger | Passing control |

Use a non-peaceful server with fractional effective local difficulty (a fresh normal/hard world normally qualifies). An integer difficulty makes the duration defect unobservable, so those cases explicitly report `infrastructure_failure` rather than pass. The probe does not change global difficulty/time or chunk age. Missing damage, wrong form setup and invalid native controls likewise invalidate the fixture; they are not evidence of a gameplay assertion failure.

Every evaluated case prints a `MORPH_LAB_EVENT ` JSON line with `schema: morph-lab.husk-damage.v1`, its case, status, native/morph before/after health and Hunger, damage acceptance, local difficulty, server tick and monotonic time. Set `-Dmorph.lab.events=<run-owned-path>/events.ndjson` to append raw JSON lines as well. Use a distinct path for each JVM. The runner must add source revision, loader and artifact provenance. Failed assertions are emitted before GameTest failure, and cases are registered separately so an empty-hand failure does not prevent held-item/control evidence.

The probes clean their spawned entities, mock connection and selected form synchronously, including assertion failures. They intentionally do not test expiry over subsequent ticks, client input, the attack cooldown, networking, or persistence. Compile and execute on both loaders before claiming a reproduced baseline or a candidate fix; source inspection alone is insufficient.

## Implementation evidence (not an execution result)

Baseline inspected: `8955f881295ef95b42976602ca0e646449573fe1`. Native bytecode inspected with `javap -classpath <Gradle-cache>/fabric-loom/26.2/minecraft-merged.jar -c net.minecraft.world.entity.monster.zombie.Husk`; that jar's SHA-256 was `ff303020a93703ef016ce8aea2a3e9c118d83df841c5e525f8eaac77de6134d0`. Its `doHurtTarget` checks successful damage and an empty main hand, then truncates effective difficulty before multiplying by 140. The baseline Morph code omits the main-hand condition and truncates after multiplication. Runtime expectations still come from the native attack, not this textual description.
