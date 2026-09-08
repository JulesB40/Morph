# Swimming animation coverage in 26.2

This matrix describes implementation routes, not a claim that every vanilla form has been visually tested. The shared eligibility rule takes precedence over every animation hook. Normal world mobs have a zero Morph swim blend and are unchanged.

The integrated client scenario passed for pig, cow, villager, pillager, cat, wolf, horse, spider, drowned, dolphin, turtle, guardian, squid, axolotl and zombie. It checks sustained player input, swimming eligibility and valid render snapshots. A focused follow-up additionally verifies pillager/villager horizontal body matrices and native drowned behavior. Captures are in `docs/screenshots/swimming`; this representative matrix does not validate every variant or mod combination.

| Family / forms | Implementation | Validation status / limits |
| --- | --- | --- |
| Zombie, husk, zombie villager, zombified piglin | Ineligible under `MorphSwimmingRules`: sink and retain walking/idle poses underwater | No swimming or fast-swimming stroke should be applied |
| Drowned | Native humanoid swim fields and drowned renderer | Explicit exception to zombie exclusion; native drowned stroke retained |
| Other humanoids: skeleton variants, piglins, enderman, giant, armor stand | Shared humanoid state and renderer path | Subclass attack/held-item poses may override individual arms; not every form visually verified |
| Villager, wandering trader, witch | Villager-family paddling and body-pitch hooks | Implemented by dedicated family mixins; folded-arm geometry preserved |
| Pillager, vindicator, evoker, illusioner | Illager-family strokes and pitch | Dedicated hooks preserve equipment and arm-pose rules |
| Standard quadrupeds: pig variants, cow variants/mooshroom, sheep, goat, polar bear, panda | Blockbench-authored normal/fast quadruped tracks | Existing quadruped hook; subclass-specific animations can take precedence |
| Cat, ocelot, wolf, fox | Generic final model hook: four-leg paddles, native rest offsets, tail stroke | Added; normal/fast strokes differ; body is never forced into a human pose |
| Horse, donkey, mule, skeleton horse, zombie horse, llama/trader llama, camel | Generic final model hook finds named leg parts including nested rigs | Added; saddles/head geometry retain native orientation |
| Spider, cave spider | Eight-leg phase-shifted paddles | Added; native leg spread retained, no human skeleton substitution |
| Chicken, rabbit, armadillo, sniffer, hoglin/zoglin, ravager, strider | Named native leg/foot paddles where the rig exposes those parts | Added fallback; visual validation still needed for each rig and baby variants |
| Fish, dolphin, squid/glow squid, guardian/elder guardian, axolotl, turtle, frog/tadpole, nautilus/zombie nautilus | Preserve native aquatic models; populate movement/animation inputs that detached adapters cannot tick, using a continuous normal/fast animation clock | Squid tentacles, guardian tail/spikes, axolotl factors/timers, frog swimming/idle and dolphin movement explicitly driven; each family's native geometry retained; individual visual validation remains separate |
| Bat, bee, parrot, allay, vex, phantom, ghast/happy ghast | Preserve native wings/tentacles and body orientation; bounded look pitch while fast swimming | No invented human limbs; native appendage animation retained |
| Golems, creeper, warden, creaking | Native named legs can receive paddle motion | Bespoke attack/body animations remain; per-form visual validation needed |
| Slime/magma cube/sulfur cube, shulker, silverfish/endermite, blaze/breeze, wither | Preserve native segmented/shell/body animation; bounded fast-swim look pitch | No fake limbs or generic body flattening; distinct limb stroke is not applicable |
| Dragon or any renderer returning a non-living render-state type | Existing Morph snapshot fallback governs support | No new support claim; unsupported adapters retain player appearance |

The generic final-model hook runs after the concrete model's `setupAnim`, so family overrides cannot accidentally erase its paddle. The same helper runs during black transition mesh capture. It excludes humanoid, quadruped, villager and illager bases to avoid applying two different strokes. Missing limb names are ignored; model topology and part positions are never altered.

Fast fallback strokes use a larger amplitude and faster cycle; non-biped fast-swim body pitch follows look direction within 55 degrees. Native aquatic rigs and dedicated biped families are excluded from that extra pitch to prevent duplicate rotations. Bodies without paddle limbs retain their existing body/appendage animation, so coverage does not mean every mob receives an identical swimming animation.
