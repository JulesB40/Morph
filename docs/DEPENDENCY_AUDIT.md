# iChunUtil dependency decision

Audited 2026-09-08. Scope: the upstream Morph 1.16.5 sources, now preserved under `legacy/1.16.5/src`, and published upstream iChunUtil releases. This is an implementation decision for the 26.2 port, not a claim of gameplay parity.

## Decision

Do not make the new 26.2 source set depend on iChunUtil. Replace networking, configuration, input, resource loading and ordinary utilities with Minecraft/NeoForge/JDK APIs. Reimplement Morph-specific rendering and UI incrementally. Keep the original source and author/license metadata intact as reference.

The legacy build requests `ichunutil:iChunUtil:10.7.0` through `mavenLocal()`; its mod descriptor requires `[10.7.0,11)`. This is a 1.16.5 library requirement, not a usable modern artifact coordinate. A modern iChunUtil release cannot be substituted based only on its similar name.

## Verified upstream availability

- `git ls-remote --heads https://github.com/iChun/iChunUtil.git` returned version branches from 1.7.10 through 1.21.5, with no 26.2 branch. The 1.21.5 head was `74249240d62745757005f64f82ff1ef011402d64`. [Upstream repository](https://github.com/iChun/iChunUtil/branches).
- The live [Modrinth versions API](https://api.modrinth.com/v2/project/ichunutil/version) returned zero entries whose `game_versions` contain `26.2`. Its latest NeoForge entry is iChunUtil `1.0.7` for Minecraft `1.21.5`, published `2025-05-09T22:14:11.75629Z`.
- The upstream [1.21.5 build properties](https://github.com/iChun/iChunUtil/blob/74249240d62745757005f64f82ff1ef011402d64/gradle.properties) identify `me.ichun.mods`, iChunUtil, and LGPL v3. The new version numbering does not imply compatibility with Morph's old 10.7 API.

These checks establish that no compatible release was found in the checked upstream branch list and publication API; they cannot prove that no private or unindexed build exists. No unverified 26.2 Maven coordinate is proposed.

## Import inventory and replacement mapping

The legacy tree contains **124 direct iChunUtil import statements in 46 Java files**, representing 37 distinct import declarations, including a wildcard. Counts concern source imports rather than transitive dependencies or runtime calls. Paths below are relative to `legacy/1.16.5/src/main/java/me/ichun/mods/morph/`.

| Area | Imported iChunUtil APIs | Main callers | Port action |
| --- | --- | --- | --- |
| Networking | `AbstractPacket`, `PacketChannel` | `common/Morph.java`; all 11 classes under `common/packet` | Use typed custom payloads and stream codecs, register direction-specific handlers, and use NeoForge distributors. Keep the server authoritative for unlocks, selected form and ability use; do not directly transplant old packet handlers. |
| Configuration | `ConfigBase`, `CategoryDivider`, `Prop` | `client/config/ConfigClient.java`, `common/config/ConfigServer.java` | Define validated NeoForge config specs with equivalent defaults and bounds. Document migration of option names. Replace the config's embedded GUI callbacks separately. |
| GUI | `Workspace`, `Theme`, `Window`, `WindowPopup`, `Fragment`, `Constraint`, `View`; `Element`, `ElementButton`, `ElementFertile`, `ElementList`, `ElementNumberInput`, `ElementScrollBar`, `ElementTextField`, `ElementTextWrapper`, `ElementToggle`, `ElementToggleRotatable`, `ElementToggleTextured`, and element wildcard imports | `client/gui/**`, `client/config/ConfigClient.java` | Build native screens/widgets for selecting forms first. The biomass workspace, mob/trait/NBT editors, constraint layout and theme system need deliberate redesign; they are not provided by changing a superclass. |
| Input and HUD | `KeyBind`, `MouseHelper`, `NativeImageTexture`, `RenderHelper`, `iChunUtil` | `client/core/{KeyBinds,EventHandlerClient,HudHandler}.java`, GUI elements | Use native key mappings and input events; native texture lifecycle and current GUI rendering submission. Own the animation clock currently read from `iChunUtil.eventHandlerClient.ticks`. Reproduce press/release and radial mouse behavior explicitly. |
| Models and hands | `ModelHelper`, Tabula `Project`, `HandInfo`, `PlacementCorrector`, `RenderHelper` | `client/render/MorphRenderHandler.java`, `client/render/hand/HandHandler.java` | Start with ordinary entity renderer dispatch, then implement Morph-owned pose/geometry snapshots and hand placement data. Legacy box-count matching, interim model construction and matrix interpolation have no assumed drop-in replacement. |
| Client effects | `ClientEntityTracker` | `client/entity/{EntityAcquisition,EntityBiomassAbility}.java` | Replace fake entity-ID allocation with client-owned effect lifecycle; register real entities only if synchronization is required. |
| General entity helpers | `EntityHelper` | command, morph handler, default mode, packets, HUD, effects and GUI | Implement bounded ray selection, advancement checks, profile lookup and animation math using the respective game services and small local helpers. Profile lookup must respect modern asynchronous/cache behavior. |
| Resource I/O | `IOUtil` | `common/{resource,biomass,mob,morph/nbt}` handlers and hand loader | Use resource reload listeners for bundled/data-driven resources; use JDK file walking only for explicit external configuration. If ZIP extraction is retained, validate normalized targets remain inside the destination. |
| Data generation | `AdvancementGen` | `common/Morph.java` | Use the current advancement data provider or checked-in advancement JSON with matching identifiers. |

NeoForge's [payload documentation](https://docs.neoforged.net/docs/networking/payload/) describes `CustomPacketPayload`, `StreamCodec`, registrars and side-separated handlers; its [configuration documentation](https://docs.neoforged.net/docs/misc/config/) covers native specs. At audit time those unversioned docs display **26.1**, so exact 26.2 signatures must be confirmed by compilation against the pinned 26.2 development bundle. They are architectural references, not evidence of a successful 26.2 build.

## Rendering dependency details

The largest library coupling is not packet serialization. `MorphRenderHandler` uses `createPartFor`, `matchBoxesCount`, `createInterimPart`, and `createModelRenderer` with Tabula `Project.Part`/`Box` objects. The hand path additionally uses `matchBoxAndChildrenCount`, inherited model-class lookup and JSON `HandInfo` placement correctors. Replacing iChunUtil therefore removes neither the model-transition work nor first-person-hand compatibility work. A renderer that merely displays the selected mob is a useful prototype, but must be labeled as such.

## License and acceptance checks

The legacy Morph descriptor declares `GNU Lesser General Public License v3.0`; the [iChunUtil project metadata](https://api.modrinth.com/v2/project/ichunutil) declares `LGPL-3.0-only`. Preserve upstream attribution and license declarations. This audit introduces no copied iChunUtil implementation. If any implementation is later vendored, record its exact source revision and retain its notices and applicable license text rather than silently presenting it as new code.

Before treating the dependency removal as complete:

1. Confirm no `me.ichun.mods.ichunutil` imports or mandatory iChunUtil metadata remain in the **new** source set; legacy references are intentional.
2. Build with an empty iChunUtil local Maven cache and test a dedicated server without client classes loading.
3. Verify config load/reload, bounded payload decoding, player reconnect/tracking synchronization and resource reload.
4. Track each legacy GUI, model transition, hand feature and effect as a parity item. A successful standalone build does not satisfy these items.
