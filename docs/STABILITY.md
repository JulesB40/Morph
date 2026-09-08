# Stability and server tick audit

These changes are shared by Fabric and NeoForge.

- Attribute sampling now catches a failed entity adapter constructor and caches an empty result for that form/world. Shape sampling already handled this case. A failed constructor therefore no longer propagates through the attribute player tick, nor is retried every tick.
- Passive weakness damage stops processing immediately if it kills the player. Death callbacks may reset the form and clean its state synchronously; continuing the old form tick could otherwise recreate its dry-air state or run a second weakness after cleanup.
- Shape cache hits return before parsing an identifier or consulting the entity registry. Cache values contain dimensions only, and weak world keys remain in use.
- Fear rules are immutable cached lists/records rather than freshly allocated per target check, candidate scan, and goal continuation check. Exact vanilla namespace matching is preserved. Searches remain limited to once per 20 ticks per applicable mob; pathfinding remains owned by that mob's goal.
- Effect-list snapshots are only created for forms with effect immunities. Ordinary forms no longer copy their active-effect collection every server tick.

The audit found no entity construction in steady-state attribute ticks: defaults are cached per form/world. Attribute modifiers are only updated when their amount changes. Flight permission updates are sent when a lease is acquired or released, rather than each tick. Flap requests validate the server form, life/spectator/riding/ground/flight state and a server tick cooldown.

These are code-path observations, not profiler measurements or a promise that no other mod combination can crash. Validation results are recorded after the build and server tests run.

## Transition interpolation measurement

On the development machine (Java 25, 2,048 vertices), the allocation regression benchmark measured:

| Interpolation implementation | Allocated per frame | Median time per frame |
| --- | ---: | ---: |
| Original per-vertex arrays and immutable list | 123,072 bytes | 40.9 microseconds |
| Packed immutable float buffer | 65,568 bytes | 19.7 microseconds |

This is approximately 47% less allocation and 52% less interpolation time in this run. The test warms both implementations for 1,000 iterations and takes the median of seven alternating 200-frame rounds; it asserts only the allocation reduction to avoid a flaky timing threshold. The reference retains the original center calculations, unmatched-quad behavior, normal normalization and optional shell expansion. Eight renderer tests passed, including randomized geometric parity and capture-budget checks.

These measurements cover the interpolation kernel, not total client frame time, mesh capture, GPU rendering, or an FPS promise. Foreground application load and JIT warmup materially affected earlier single-round timings, which is why the final measurement uses alternating warmed rounds. The separate code-path improvements also reuse the collector proxy, skip unused endpoint captures, and avoid duplicate armor foil/trim geometry; they are not included in this timing comparison. See `docs/TRANSITION_DESIGN.md` for deferred-draw ownership and fallback limits.
