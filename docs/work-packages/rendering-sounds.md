# Native sound forwarding: first stage

The shared `model.sound` hooks cover player step, swim/splash, hurt, death,
small/big fall, voice pitch/volume, and consumable sounds on both loaders.
Transformation sound scheduling remains the existing `MorphSounds` path.
Its duration overload centers the original 60-tick sample in the configured
transition; durations shorter than the sample begin playback immediately and
retain its full duration and original pitch. The old 100-tick default still
delays playback by exactly 20 ticks. Timing-bound tests are included.
No ambient schedule or flying/flapping sound schedule is implemented here.

The native lookup entity is never added to a level or ticked. The cache holds
weak references to both player keys and detached entities and checks species
and level before reuse. Native step emissions are captured in an exception-safe
thread-local scope before invoking the real player's sound method; this retains
player location, sound category, silence and client/server routing. A native
override that emits no step is an intentional silent result. Failed lookups keep
the original player sound. Lookups apply the validated active descriptor through
the shared FormCapture adapter. Cache reuse compares the complete immutable
descriptor, so switching between same-species baby/adult forms changes native
voice pitch. The probe asserts the nonoverlapping native adult 0.8–1.2 and baby
1.3–1.7 ranges while revisiting the adult form.

The 26.2 native class bytecode was inspected with `javap -p -c` against
`C:/Users/Jules/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar`.
Compared classes: Entity, LivingEntity, Player, Consumable, Pig, WanderingTrader.
Legacy equivalents are `legacy/1.16.5/.../MorphInfoImpl.java` and its Entity,
LivingEntity and Player mixins. There are no longer entity getEatSound/getDrinkSound
methods. `Consumable.emitParticlesAndSounds` uses its component sound unless the
consumer implements `OverrideConsumeSound`. Only WanderingTrader implements that
interface in this native JAR; it returns milk for milk buckets and potion otherwise.
The port follows that native behavior for food as well as drinks.

`testing/harness-common/.../NativeSoundChecks.verify(helper, fixture)` supplies
opt-in runtime probes for real installed mixin entrypoints. It records sound
method calls before network/mixer delivery, including exact expected identifiers,
step volume/pitch, suppression, and self fallback. This is neither a network
duplicate-delivery test nor audible-output evidence. Registration is the lab
integration owner's responsibility. No compilation or runtime result is claimed
until the central queue executes this commit. Remaining probes: remote/local
duplicate delivery, native intentionally silent steps, reload
and dimension changes, actual client sound-listener notifications and audible mix.
