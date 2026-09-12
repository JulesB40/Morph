# Descriptor extraction and transition stage

Snapshots now accept `CollectionEntry` and apply the shared, typed
`FormCapture.applyVariant` helper before native extraction. An unsupported
adapter returns no replacement. Detached adapters are keyed by player UUID,
entry ID, descriptor revision and resource reload revision. Failures are scoped
to the same descriptor key; a failing variant does not poison its whole species.
Resources clear both extraction and submission failure caches. Reload does not
cancel an otherwise valid active transition.

Observer extraction consumes `DescriptorState.active(UUID)` when it matches the
current synchronized species. Versioned transition payloads use
`MorphTransitions.start(UUID, CollectionEntry, CollectionEntry, long, int)`;
null endpoints are self, and the long is the server start tick. Endpoint equality
uses ID/revision, so white-to-red sheep does not collapse into a no-op. The old
species overload remains for compatibility helpers. The renderer retains the
existing boolean submission result and only suppresses the avatar after success.

Supported body state is presently the shared capture helper's species defaults,
sheep baby/color/name and slime size/name. Native special-name state is retained
while the player's nameplate policy still controls the visible nameplate.
Player-profile rendering, arbitrary addon adapters and acquisition tendrils are
not implemented by this stage. Equipment preparation belongs to the animation
owner's descriptor overload. Native AI is never ticked.

`NativeRenderDescriptorChecks.verify(avatar, sourceState)` is a test-only client
probe with fixed white/red/baby sheep, `jeb_`, slime size 2/16, repeat-cache and
reload expectations. It inspects actual native render states; it does not assert
visible pixels, moving parts, transition mesh submission or remote synchronization.
The central lab owner must register and execute it on both loaders, then review
the visual checkpoints separately. No local compilation or game was launched by
this work package.

Dependencies are the variants DTO/FormCapture stage, service DescriptorState,
the animation descriptor equipment overload and three-argument flying helper,
plus the UI owner's Fabric resource-listener/descriptor-packet wiring.
