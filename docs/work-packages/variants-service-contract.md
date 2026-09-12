# Variants and authoritative service contract — draft 1

Prepared 2026-09-12 against main source `0d3b2bf5e2c9303e36a3c61967ae1672eb48b064`.
This is a concrete proposed interface freeze for the next implementation wave,
not implemented storage, protocol compatibility or generic variant support.
Changes to these fields or limits require one coordinated contract revision.

## Current behavior to preserve

`model/MorphCollection.java` uses schema 1, a sorted set of species strings,
256 owned forms, 256-character identifiers, an empty active string for self,
and a 20-tick selection cooldown. Restore never grants an orphan active species.
`server/MorphSavedData.java` stores `schema_version`, UUID-keyed `players` with
`forms`/`active`, and an independent optional `nametags` map in overworld saved data.
Its current codec rejects unsupported schema versions. Neither class accepts
entity NBT. `ShapeHooks`/`MorphDimensions` additionally reject non-vanilla forms;
the collection's lexical validator alone does not establish support.

NeoForge `network/MorphNetwork.java` registers protocol `5`: State has UUID,
species and nametag; Collection has species list and active species; Transition
has UUID, source/destination species and duration 1–1200. Fabric's `MorphAppearance`
uses `appearance_v2`; `MorphOwned` and `MorphTransition` carry equivalent species
data. The two loaders do not yet share a variant protocol or authoritative service.
`server/MorphService.java` and Fabric's service code independently perform fit,
ownership, acquisition, attributes and synchronization. Keep native attribute
defaults, external modifiers, health ratio, sustained flight and nametag preference.

Paths above are relative to `src/main/java/me/ichun/mods/morph/`, except Fabric
classes under `fabric/src/main/java/me/ichun/mods/morph/fabric/`.

## Immutable DTOs and limits

Implement shared Java records with defensive copies and validated constructors;
no record may retain an Entity, ItemStack, CompoundTag, world, mutable map or list.
All lengths below are UTF-8 bytes checked before allocation, not UTF-16 length.
Reject duplicate fields, duplicate map keys, invalid UTF-8, unpaired surrogates,
unknown fields, nonfinite numbers and trailing wire bytes. Do not silently truncate.

| Record/field | Exact draft-1 representation and bound |
| --- | --- |
| `FormDescriptor.version` | Integer exactly 1 |
| `species` | Lowercase namespaced identifier, ASCII pattern `[a-z0-9_.-]+:[a-z0-9/._-]+`, 1–256 bytes |
| `adapter`, `adapterVersion` | Identifier under the same bound; positive integer 1–65535 |
| `variant` | At most 32 unique ASCII keys, each 1–64 bytes; values are Boolean, signed 32-bit integer or NFC string at most 256 bytes. No lists, floats or nested objects. Adapter declares exact keys/types/ranges; this is not arbitrary NBT. |
| `customName` | Optional plain NFC text, 1–256 bytes; absent instead of empty. No rich components, click events or selector expansion. |
| `equipment` | Map with at most eight keys: `mainhand`, `offhand`, `head`, `chest`, `legs`, `feet`, `body`, `saddle`; absent means no captured gear |
| `attributes` | At most 32 unique registered attribute IDs, each at most 256 bytes; finite double base values in [-1,000,000, 1,000,000], further constrained by registered attribute range and policy |
| `profile` | Absent for non-player; required for `minecraft:player`: UUID, ASCII account `name` matching `[A-Za-z0-9_]{1,16}`, optional `skinHash` of exactly 64 lowercase hex digits, `model` enum `WIDE`/`SLIM` |
| Complete descriptor | At most 16,384 canonical JSON bytes, including every field; no compression-dependent limit |
| `EntryId` | `v1:` followed by 64 lowercase SHA-256 hex digits, exactly 67 bytes |
| `CollectionEntry` | `id`, descriptor, `revision` nonnegative signed long, `favorite` Boolean, `order` integer 0–255 |
| `CollectionSnapshot` | `schemaVersion=2`, `revision` nonnegative signed long, at most 256 entries, optional `activeEntryId`; no self entry |
| Per-player serialized collection | At most 4,300,000 UTF-8 bytes; checked before save and decode |

Attributes capture base values only, never current health, modifiers, temporary
effects, or a mod's effective total. Initial admissible IDs are exactly the 23
entries returned by current `MorphAttributes.copiedAttributes()`. The 32-entry
storage limit permits a future explicit policy revision; it does not enable
unknown or AI/player-only attributes automatically. Missing attributes retain
current species defaults. Native values must not be capped to historical health
defaults. Acquired attribute merging is a service policy, not capture identity.

Each equipment value is `CapturedEquipment(item, damage, dyedRgb, enchanted)`:
`item` is a registered 1–256-byte identifier; `damage` is optional integer
0–1,000,000; `dyedRgb` is optional integer 0–16,777,215; `enchanted` is Boolean.
These four fields are the complete initial allowlist. Render copies always have
count one and cannot be dropped, transferred, consumed or used for gameplay.
No container contents, attribute modifiers, enchantment list, custom data,
text components, arbitrary item components or profile components are captured.
Unsupported components produce a capture report; they are not silently promised
as equipment parity. Player live hands/ordinary armor remain the visible source
by default. Captured body/saddle gear is separate; a captured-equipment preview
setting may display other slots without changing player inventory.

Profile texture resolution is server-controlled: the wire accepts no arbitrary
URL, token, property bag or client-supplied signature. `skinHash` resolves only
through the implementation's trusted skin cache/provider. Pending/missing skins
use an explicit default model. Account name/model/skin are refreshable presentation
fields; a player UUID is the identity. Player support requires a separate approved
profile adapter and policy; adding the DTO alone must not bypass today's rejection.

## Entry identity and semantic deduplication

Identity JSON has exactly these keys: `adapter`, `adapter_version`, `custom_name`,
`equipment`, `profile_uuid`, `species`, `variant`, `version`. Explicit nulls are
required for absent custom name/profile UUID. `version=1`. Profile UUID is lowercase
hyphenated text. Equipment uses its four snake-case-free field names exactly as
specified above, omitting absent optionals. Identity includes captured gear.

Canonical encoding: sort object keys by ASCII key order at every level; emit no
whitespace, decimal integers without leading zeros, lowercase Boolean/null, and
NFC string values as UTF-8. Escape only quote, backslash and U+0000–001F; controls
use lowercase `\u00xx` escapes (not short escapes). Do not escape slash or other
Unicode. Identity has no floating-point fields. Capture normalizes text to NFC;
decoders reject noncanonical encodings when accepting precomputed IDs. Compute
`EntryId = "v1:" + lowercaseHex(SHA256(identityBytes))`.

Attributes, favorite, order, acquisition time, profile name/skin/model and entry
revision are excluded. Thus improved attributes or refreshed profile appearance
replace the immutable descriptor, increment revision and invalidate caches without
changing selection identity. A different color, age class, size, name or captured
gear produces a different identity. Full canonical bytes must also compare equal
on hash collision; a mismatching hash collision rejects acquisition. IDs are
collection-independent; they are not entity UUIDs or transferable ownership grants.

An adapter must serialize explicit defaults identically on every capture; absence
must never alternately mean the same value as an explicit default. Base
`morph:species@1` descriptors have an empty variant/equipment and no name/profile.
They preserve species-only migrated entries. Detailed captures use a separate
adapter identity, so a migrated default does not unpredictably merge with a
captured individual. Adapter version changes require an explicit ID remapping
migration, never silent rehashing on load.

## Initial adapter scope and capture boundary

`morph:species@1` is the compatibility adapter for currently supported vanilla
species, including species-only admin grants. It promises current default state
only. Initial detailed acceptance fixtures use `morph:sheep@1` with required
`baby` Boolean and `color` integer 0–15; and `morph:slime@1` with required `size`
integer 1–16. Other species, age/variant registries and dynamic data-pack values
need named adapters, documented bounds and independent fixtures before capture
is enabled. This is a first contract slice, not a claim to cover all variants.

Capture adapters read specific entity getters or explicitly named validated data
fields. Never round-trip `Entity.save` into another entity. Exclude position,
motion, rotation, source entity UUID, dimension, current health, AI/Brain memories,
targets, passengers, vehicle, leash, inventories, loot tables, command text and
active cooldown/action phases. A custom name is captured only through plain text.
`CaptureResult` returns either a validated descriptor plus a list of omitted
supported-report fields, or a typed rejection; any failed mandatory identity
field rejects the capture. Reports are bounded to 32 items of 256 bytes each.

Modded adapters require an explicit registry entry declaring capture schema,
shape provider, renderer support, trait policy and compatible adapter version on
both ends. Syntactically valid non-vanilla IDs alone remain unsupported. Missing
adapters on load retain bounded saved descriptors as unavailable entries; they
cannot select or activate, and do not fall through to generic NBT application.

## Schema-1 migration and save policy

New root save shape is `{schema_version:2, players:{uuid:CollectionSnapshot},
nametags:{uuid:boolean}}`. Do not duplicate the root schema marker inside the
serialized per-player object: `CollectionSnapshot.schemaVersion` is its in-memory
codec selector. Serialize each entry's `id`, `descriptor`, `revision`, `favorite`
and `order`, plus collection `revision` and nullable `active_entry_id`.

Read schema 1 through its existing lexical validation and 256-form limit before
migration. Deduplicate/sort the species exactly as the current TreeSet does.
For each species create `morph:species@1`, empty variant/equipment/attributes,
no name/profile, entry revision 0, favorite false and sequential order starting
at 0. Collection revision is 0. Map the active species only if owned; otherwise
use null/self. Preserve nametags exactly, including default true when absent.
Do not capture new species attributes during migration: that changes old semantics
and makes output depend on the current world's entity initialization.

Invalid schema-1 IDs, invalid player UUIDs and over-limit collections fail migration
without overwriting input. A well-formed unavailable species remains unavailable
in the new collection; an unavailable active form resolves safely to self. Do not
delete ownership when a registry or addon is temporarily missing. Migration must
be idempotent: schema 2 reload never resets favorite/order/revision or rehashes IDs.
Unknown schema versions are a reported load failure, not an empty save that will
later overwrite original data. Preserve an original-file backup before replacing
schema 1 through the saved-data integration; verify actual stop/restart behavior.

Legacy 1.16 `morph_save` is a separate importer with explicit field conversions,
original-file preservation and an import report. Do not label schema-1 migration
as legacy NBT import or accept legacy compounds through the descriptor decoder.
The total player count remains subject to server storage policy; per-player caps
do not imply the whole world save is bounded to 4.3 MB.

## Shared authoritative service

Expose `capture(actor, sourceEntity)`, `grantSpecies(actor, target, species)`,
`grantDescriptor(actor, target, trustedCapture)`, `select(actor, entryId)`,
`reset(actor)`, `delete(actor, entryId)`, `favorite(actor, entryId, value)` and
`snapshot(actor)` from one loader-independent server-thread service. Operator
target operations use the same service with an explicit authorized target.
Clients cannot invoke `grantDescriptor` or submit capture state.

Return `ActionResult(code, collectionRevision, activeEntryId, changedEntryIds)`;
codes are `CHANGED`, `UNCHANGED`, `NOT_OWNED`, `UNSUPPORTED`, `INVALID`, `FULL`,
`COOLDOWN`, `DENIED`, `NO_SPACE`, `STALE`, `CANCELED`. Check expected revision
for mutating client requests; a stale action does not silently apply to another
entry. Same-ID repeated acquisition checks canonical identity before merging
attributes under the revisioned policy. Default merge is KEEP_EXISTING; optional
MAX_BASE is per allowed attribute, bounded and never sums values.

Validate permissions/mode/player state, descriptor support, ownership, cooldown,
fit and cancelable hooks before committing any state. A rejected operation leaves
ownership, active state, attributes, costs and transition generation unchanged.
Delete-active performs safe reset and deletion atomically; no-space rejects both.
Self is not owned and cannot be deleted. Unchanged selection does not restart
attributes or sound. Preserve the 20-tick selection policy and reset behavior
until the separate configuration contract changes them.

Commit increments collection revision once; descriptor changes increment that
entry revision once. Favorites/order changes do not change descriptor revision.
Only then publish a snapshot/change acknowledgement and appearance/transition,
schedule sounds and dirty saved data. Exceptions in observer delivery cannot undo
already committed state; retry synchronization from the authoritative snapshot.
Publish typed events with bounded immutable values, never mutable collection internals.

## Wire revision and transition contract

Introduce protocol `6` for NeoForge and new versioned Fabric payload IDs ending
`_v3` for this descriptor protocol. Never append bytes to current packets under
the same decoder. Both loaders call the same DTO validation and canonical codec.
No silent species downgrade for older clients: reject incompatible negotiated
versions with an explicit message. Health synchronization retains its current
ordering separately; collection changes must not reorder health attributes/values.

Full descriptors use length-prefixed UTF-8 canonical JSON, max 16,384 bytes,
with exact fields `version`, `species`, `adapter`, `adapterVersion`, `variant`,
`customName`, `equipment`, `attributes`, `profile`; optionals are explicit null
except equipment optionals. Full JSON uses the same key/string rules as identity;
double attribute values use Java `Double.toString`, finite values only, normalize
negative zero to 0.0. Identity hashing never depends on these double spellings.
Unknown fields or invalid nested fields reject the whole descriptor.

| Payload | Fields and bounded handling |
| --- | --- |
| Owner snapshot page | collection revision (long), page index/count (VarInt), nullable active ID, 1–3 entries (0 allowed only for empty collection); at most 60 KiB total packet |
| Entry | ID (67 ASCII bytes), entry revision (long), favorite Boolean, order (VarInt), descriptor length and bytes |
| Client action | request sequence (nonnegative long), expected collection revision, opcode SELECT/RESET/DELETE/FAVORITE/REQUEST_SNAPSHOT, entry ID for non-reset mutations, Boolean for FAVORITE; at most 256 bytes |
| Action acknowledgement | request sequence, result code, new revision, nullable active ID; at most 256 bytes; changed data arrives in a new owner snapshot |
| Observer appearance | subject UUID, connection epoch UUID, monotonically increasing appearance sequence, showNametag Boolean, nullable entry ID and descriptor revision/descriptor |
| TransitionSpec | subject UUID, epoch UUID, generation (long), start server tick (long), duration 1–1200 ticks; source and destination each nullable descriptor plus ID/revision |

Nullable values have an explicit presence byte; null endpoints mean self. Count
and length prefixes are validated before allocation; no packet exceeds 60 KiB.
At most 86 pages assemble a 256-entry snapshot. Only one pending revision per
owner connection, bounded to 4.3 MB and a 5-second assembly deadline. Reject
duplicate page indexes, conflicting headers/IDs, excess entries or totals. Publish
the snapshot to UI only when every page is present and IDs/active ownership
validate; retain the previous complete snapshot on timeout. REQUEST_SNAPSHOT is
rate-limited to one per second and mutations to at most 20 per second per connection.

Observer delivery is tracking-scoped plus owner, with complete current state on
start tracking. Connection epoch prevents sequence reuse across reconnects;
world/connection teardown clears corresponding maps. Transition endpoints carry
full descriptor values even when one entry was just deleted. Renderer, shape and
attribute caches key by ID plus descriptor revision and resource-definition
revision. Same-species different-color endpoints must never collapse to one cache
entry. Live animation/action phase is separate server-owned state, not saved
variant identity. Rendering failure must retain original-player fallback.

## Compatibility entry points and file ownership

Keep `activeForm()` returning selected descriptor species or `""` for self, and
`ownedForms()` returning sorted distinct species for existing commands/helpers.
Add `activeDescriptor()`, `entries()` and ID-based operations; never substitute
entry IDs into species-only attribute/trait hooks. `unlock(species)` grants the
species adapter. `select(species,tick)` resolves the species-only entry first;
otherwise the lexicographically smallest EntryId for that species, independent
of favorites/UI ordering. Document this deterministic convenience behavior while
new variant UI selects exact IDs. Player profiles require descriptor-aware callers;
the old `isFormId` player rejection is retained for old species-only entry points.

| Owner | Exclusive implementation boundary |
| --- | --- |
| Store/variants agent 2 | DTOs, canonical identity, codecs, collection model, schema migration and capture fixtures; only writer of saved schema |
| Authority/network agent 3 | Shared operation sequencing, loader lifecycle adapters, payload registration, paging, tracking and transition generation; consumes DTOs without duplicating their schema |
| Renderer agent 5 | Descriptor endpoints/cache keys, player profile presentation, fallback and transition consumption; no saved identity mutation |
| Animation/equipment agent 6 | Applies approved captured equipment/variant fields to render snapshots; contributes adapter fixtures |
| Movement agent 7 | Descriptor-aware dimensions/fit and attribute policy; does not rewrite collection serialization |
| UI agent 4 | ID-based selection, favorites/order, previews; immutable snapshots only |
| Resources/API agent 9 | Versioned adapter registry/schema validation and addon events; coordinates modded enablement with shape/render owners |

Integrate DTO/codec tests first, then migration/service, then both packet adapters,
then descriptor-aware caches and concrete capture adapters. Do not enable detailed
capture merely because storage compiles. An agent needing a DTO extension submits
a contract diff to its owner before editing another domain's serialization.

## Hard fixtures and rejection oracles

These are fixed expected identities, not implementation-produced golden files.
Canonical identity bytes for the default pig are exactly this one-line UTF-8 text:

```json
{"adapter":"morph:species","adapter_version":1,"custom_name":null,"equipment":{},"profile_uuid":null,"species":"minecraft:pig","variant":{},"version":1}
```

Expected EntryId:
`v1:4066fd35e02757b46702c7638e66f7b58aafe86b2479d5bcc0f164b90aad7390`.

Additional identity fixtures use the same exact keys/defaults above:

| Fixture overrides | Expected SHA-256 after `v1:` |
| --- | --- |
| adapter `morph:sheep`, species `minecraft:sheep`, custom_name `Rosé` (NFC), variant `{"baby":false,"color":14}` | `c81a2ed04901caf434530c5c4f8a7724e2553e6682ee177cfaa82b7db9185d9e` |
| adapter `morph:slime`, species `minecraft:slime`, variant `{"size":2}` | `a4540a1bad43d61ccbb89b2fc1f0064953518b97727fc539c2266ef9f20ca8ee` |
| adapter `morph:player`, species `minecraft:player`, profile_uuid `00000000-0000-0000-0000-000000000001` | `bdf8ed969dfe5761b4b3349512023da62e6c175ba7cb97ec75ff9245d3c1f592` |

Round-trip tests must independently assert these bytes/IDs through save codec and
both loader packet codecs. Exercise full sheep descriptor with empty attributes,
empty gear, absent profile; then add `minecraft:max_health=8.0`: ID stays equal,
decoded attributes must equal 8.0 exactly. Change color 14 to 0: ID differs.
Change only favorite/order: descriptor revision and ID stay equal. Normalize
decomposed `Rose` plus combining acute on the final e before capture: it produces
the same NFC name/ID, while a noncanonical supplied identity packet is rejected.

Migrate `{forms:["minecraft:pig","minecraft:pig"],active:"minecraft:pig"}` to one
entry with the pig ID above, order/revisions 0 and favorite false. An active sheep
with only pig owned becomes self; a false nametag stays false. Re-encoding/reloading
the schema-2 result changes no IDs or metadata. Compare migration without a world
to ensure no accidental live entity capture. Player fixture remains decode-only
until the profile adapter is enabled; it never validates legacy player IDs.

Reject 257 entries, 257-byte identifiers/names, 33 variant fields/attributes,
16,385-byte descriptors, size 0/17, sheep color -1/16, duplicate keys, NaN/infinity,
arbitrary equipment components, unknown adapter keys, ID/content mismatches,
invalid UUIDs, forged profile URLs, orphan wire active IDs and unsupported schema.
Assert exact-boundary acceptance separately. Shuffled map insertion order must
yield identical canonical bytes. A failed delete-active fit check must preserve
the complete old snapshot, active attributes and transition generation. Snapshot
pages arriving out of order publish only when complete; missing/duplicate pages
cannot become a partial UI collection. Run the same fixture bytes on both loaders
and preserve source hashes and failures before accepting this contract as implemented.
