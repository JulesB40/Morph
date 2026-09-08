# Morph — Minecraft 26.2 port

Community port of [iChun's Morph](https://github.com/iChun/Morph) for **Minecraft Java 26.2**, with separate **NeoForge** and **Fabric** builds. Requires **Java 25**. This is an early development port, not a feature-complete replacement for Morph 1.16.5.

## Current implementation

- Kill a supported vanilla living mob to acquire its entity type.
- Press **[** (rebindable in Controls) to select a collected form or return to the player.
- The server validates ownership and synchronizes appearance to observers.
- Transformations fade to the original black skin, deform between the two bodies, and reveal the new form over five seconds. Both loaders play one of the six original morph sounds.
- Collections persist per player UUID in the world's Morph saved data. Death resets appearance while retaining the collection.
- `/morph list`, `/morph select minecraft:pig`, and `/morph reset` are available to players. Operators can grant a test form with `/morph grant minecraft:pig` on NeoForge or `/morph grant @s minecraft:pig` on Fabric.
- Standing/crouching hitbox and eye height follow the form, with collision checks before expansion.
- Health, damage, armor, movement, knockback and jump attributes follow the form, preserving injuries and original default caps.
- Original Classic traits include flight/flapping, climbing, swimming, immunities and weaknesses, attack effects, hostile disguises, intimidation and eligible rideable forms. See [abilities and controls](docs/ABILITIES.md).
- Compatible vanilla mob layers display your held items and armor, including handedness and item-use poses. Models without the corresponding layers retain their native appearance.
- Swimming and sprint-swimming animations follow mob model families, including villagers, illagers, quadrupeds and other native rigs. Ordinary zombies walk underwater; drowned retain swimming. See [coverage and limits](docs/SWIMMING_COVERAGE.md) and [Blockbench animation sources](art/blockbench/README.md).

There is a one-second selection cooldown and a maximum of 256 collected entity types. Returning to the player requires sufficient space. No arbitrary entity NBT is accepted from clients.

## Building

From the repository root:

```powershell
# NeoForge 26.2.0.82, ModDevGradle 2.0.146, Gradle 9.2.1
.\gradlew.bat build
.\gradlew.bat runClient

# Fabric uses its own pinned toolchain and wrapper
cd fabric
.\gradlew.bat build
.\gradlew.bat runClient
```

On Linux/macOS use `./gradlew` in the corresponding directory. The configured toolchain resolver can provision Java 25 for builds. NeoForge outputs are in `build/libs`; Fabric outputs are in `fabric/build/libs`. Install only the JAR matching your loader, not the `-sources` JAR. Install the same loader and Morph version on client and server; Fabric also requires Fabric API. Do not install both Morph builds together. iChunUtil is not required by this port.

## Development status and limitations

The first milestone focuses on the classic acquisition/selection loop. It currently uses default entity appearances: individual variants, favorites/deletion, player forms, modded mobs, exact legacy part/box interpolation, first-person mob hands, biomass progression, variant-dependent traits and upgrade parity, and the legacy editors are unfinished. Only the traits explicitly listed above are implemented. Rendering and multiplayer runtime results are tracked in [the validation report](docs/VALIDATION.md).

The new versioned save format is separate from the legacy `morph_save` data. **There is no 1.16.5 save importer yet.** Test with a new world or a copy; this port does not promise legacy world migration.

## Repository layout

- `src/main`: NeoForge implementation plus explicitly shared vanilla/model code.
- `fabric`: independent Fabric build, using the shared model, save codec, selector, and rendering adapter.
- `legacy/1.16.5`: untouched upstream source retained for porting reference; excluded from both builds.
- [Porting plan](PORTING_26_2.md), [dependency audit](docs/DEPENDENCY_AUDIT.md), and [rendering audit](docs/RENDERING_26_2_AUDIT.md).

Original work by iChun and upstream contributors. LGPLv3 license texts are retained in `COPYING` and `COPYING.LESSER` and included in generated JARs. NeoForge MDK template attribution is retained in `TEMPLATE_LICENSE.txt`.
