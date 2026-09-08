# Morph geometry (26.2)

`MorphDimensions` and `ShapeHooks` contain no loader or client dependencies. The
shared-side `AvatarDimensionsMixin` changes `Avatar.getDefaultDimensions` only for
players. This is deliberate: NeoForge's `EntityEvent.Size` changes the cached box
on refresh, but does not affect vanilla pose-fit or dismount queries. An event-only
implementation could let a large form stand up inside a ceiling.

## Integration

Register `me.ichun.mods.morph.shape.mixin.AvatarDimensionsMixin` in a required, shared
mixin configuration (Java 25; `defaultRequire: 1`), on both sides. Do not also
register a Size event handler. Fabric can compile these three files as shared
sources and register the same mixin.

Install the common resolver with `ShapeHooks.setFormResolver(...)`. The resolver
must resolve `ServerPlayer` through `MorphService.collection(player).activeForm()`
and client players through `setClientFormResolver(...)`. Keep the common
initializer free of direct client class references. The resolver is called on the
owning world's thread, including the integrated server thread.

Before selection changes authoritative state call `ShapeHooks.canFit(player,
formId)`. For an explicit reset, use an empty string and reject the reset with an
explanation if there is no room. Do not block death/respawn resets on this check.
After selection/reset/login synchronization call `ShapeHooks.refresh(player)`.
Client state packets must refresh the corresponding player after updating the
lookup, including remote players. Also refresh on entity join: a state packet may
arrive before the player entity. Constructor queries deliberately retain vanilla
dimensions until an explicit `refresh(player)` marks it ready in a weak-key map.

## Behavior and limits

- Standing dimensions and eye height come from a temporary vanilla LOAD entity,
  matching the renderer's default entity adapter rather than registry dimensions
  that can differ for slimes or pufferfish. Entities are never spawned or retained.
- Crouching preserves width and scales height/eye height by the player's vanilla
  standing-to-crouching ratio (5/6). This is a gameplay policy, not mob animation.
- Swimming/crawling, fall flying, spin attack, sleeping and dying retain vanilla
  pose dimensions. Full mob-specific movement and pose animation are future work.
- Vanilla player scale is applied once by `LivingEntity.getDimensions`. Vanilla
  pose-fit checks see the changed dimensions and still determine safe poses.
- A selection/reset collision preflight rejects expansion at the current position;
  it does not teleport the player or search neighboring positions. Forced lifecycle
  resets and external teleports still require normal game handling.
- Vanilla forms only; no variant NBT, baby sizes, or entity-specific abilities.
  Failed adapters retain vanilla geometry. Other mods changing dimensions may
  conflict and are not covered by this prototype.

## Validation required

Compile and run both a dedicated server/client pair and an integrated world.
Check chicken/slime/enderman eye height and F3+B boxes; select/reset under low
ceilings; crouch/stand, crawl, swim, sleep and dismount; tracking before spawn;
relog/dimension change; and player scale attributes. These are integration tests,
not claims of completed graphical validation. Source API was checked against
NeoForge 26.2.0.82 and its patched 26.2 sources.
