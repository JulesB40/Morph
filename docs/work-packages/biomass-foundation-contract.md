# Biomass foundation — authored opt-in economy, revision 1

This package completes a foundation and command interface for a legacy system
that was unfinished. Runtime/storage wiring belongs to the service/persistence
owners; this branch alone does not establish a working mode. Numerical defaults
below are newly authored, not recovered working legacy balance. Runtime
validation has not been run for this patch.

## State and transactions

`progression.BiomassLedger` is immutable: nonnegative long `revision`, Boolean
`unlocked`, integer `balance`, and an immutable map of positive upgrade levels.
Missing upgrades mean level zero. A locked ledger must have zero balance and no
upgrades. `locked()` supplies migration/default state; `unlock()` records an
authoritatively checked advancement or configured bypass, but does not itself
check Minecraft advancements or filters. Unlock alone gives no currency. The
first runtime milestone is a policy-eligible nearby kill yielding at least one
unit: `prepareGain` unlocks and credits it in one revision/commit. Zero-yield,
out-of-range and oversized kills do not unlock. Minecraft advancement resource
and progression-filter integration remain separate follow-up work. Switching
server modes must preserve the ledger.

`BiomassDefinitions` is an immutable validated snapshot. It has a starter
capacity, a complete upgrade table and complete action-cost table. Constructors
reject negative/out-of-bound data, missing enum definitions, cyclic/unreachable
prerequisites and non-free reset. A published ledger also needs
`definitions.validate(ledger)`: this checks configured level limits,
prerequisites and balance capacity. Definition reload must validate *all* stored
ledgers before publication, reject incompatibility or perform an explicit
migration. The package never silently clamps saved balances during reload.

`acquire`, `purchase`, and `charge` return `Result(status, before, after)`.
Failures return the unchanged ledger. `complete(currentLedger, actionSucceeded)`
checks that the candidate still belongs to the current ledger, and returns the
candidate only on success. A changed ledger advances revision once; exhaustion
throws instead of wrapping. Free/unchanged actions do not advance revision.

The service must serialize each player's mutations on the server thread, check
request sequence/replay, eligibility, ownership, fit and native action
preconditions, prepare the ledger candidate, and publish collection/ledger and
gameplay effects together. This function does **not** roll back a spawned
projectile, selected form, or native attribute mutation. Failed actions retain
the old ledger; service integration must prevent or reverse partial game effects.
Do not send a success response or save candidate state before commit. Revisions
reject stale candidates; they do not provide persistent network replay tracking.
No unbounded transaction-ID history is stored. `BiomassRuntime.purchase` requires
an explicit expected revision; a repeated purchase request for an old revision
rejects, even when enough funds remain to buy the next level. Since revision is
persisted, this remains true after restart. `gain` is a trusted server kill-event
entrypoint, not a network operation; each death event must be delivered once.

## Runtime and command integration

`BiomassRuntime.Store` reads a player's ledger and commits a replacement only
against its expected revision. `MorphSavedData` integration must check sequential
revision, validate the ledger and mark dirty only on actual changes. A runtime
instance holds no mutable session state and can use the overworld's saved store.

`BiomassCommands.register(dispatcher, Access)` provides `/morph biomass status`
and `/morph biomass buy <upgrade> <revision>`. Status shows balance, normal/reserve
capacity, unlock state, each upgrade's level/next cost/prerequisite and exact
purchase command using the current revision. The command accepts no target,
currency amount, fabricated ledger, or unlock request. This is the minimal usable
spending interface; translated HUD/screens and automatic state packets remain
follow-up work.

Shared service owns Access callbacks and hooks. Biomass mode is
`MorphPolicySnapshot.ServerMode.BIOMASS` and additionally requires
`biomass_opt_in=true` in config. Register its availability only after persistence,
kill gains, morph charges and commands are installed. Default CLASSIC remains
unchanged. For selection, prepare the charge after all policy/fit/event checks,
commit only on collection CHANGED in a callback-free server-thread gap, and then
publish. Failed/unchanged selections spend nothing. Reset remains free. Eligible
duplicate kills and collection-full kills still earn biomass; canceled,
unsupported, filtered or otherwise denied captures do not.

Only morph costs are wired in this slice. Basic/powerful/channel cost definitions
are reserved for future concrete ability transactions, not claimed as active
paid abilities.

## Concrete defaults

- Biomass mode must be explicitly enabled. `charge(..., false, ...)` bypasses all
  costs. Acquisition and purchase callers must also require Biomass mode.
- Starter capacity: **100**. Eligible kills can grant biomass even if their
  captured variant is already owned. Zero-current-health corpses still use the
  native species/base-health input supplied by server policy, not remaining
  health, a player's Morph attributes, or an untrusted client value.
- Gain: `floor(min(nativeBaseHealth, 100) * efficiencyPercent / 100)`. Input health
  must be finite and nonnegative. Health below one can yield zero. This is a new
  health-based balance formula, not legacy volume-based currency generation.
- Eligibility requires server policy approval, positive finite captured body
  volume no greater than `8 + 8 * ABSORPTION level`, and finite nonnegative death
  distance no greater than `3 + REACH level`. Volume is width squared times
  height in blocks cubed; distance is Euclidean blocks, calculated by the caller.
  Exact boundaries are inclusive. Rejected eligibility never spends/gains.
- Normal capacity: `100 + 100 * CAPACITY level`. Efficiency:
  `100 + 25 * EFFICIENCY level` percent.
- Critical capacity is extra reserve storage:
  `floor(normalCapacity * (100 + 25 * CRITICAL_CAPACITY level) / 100)`.
  Gains saturate at this storage limit; currency above normal capacity triggers
  no automatic damage, decay, spending or free powers. HUD may distinguish this
  reserve. Maximum default normal/critical storage is **600 / 1050**.
- Reset costs **0**, including before unlock. Morph costs **5**, basic ability
  **10**, powerful ability **25**, and channel tick **1**. These are abstract
  action categories: ability owners must author each concrete action's category
  and charge timing. The package does not infer costs from ability names.
- All currency is whole units. Total serialized currency is bounded to
  1,000,000 and tighter configured capacity; no NaN/infinity or integer wrapping.

| Upgrade | Per-level purchase costs | Maximum level | Prerequisite |
| --- | --- | --- | --- |
| CAPACITY | 50, 100, 200, 300, 400 | 5 | None |
| EFFICIENCY | 25, 75, 150, 250 | 4 | None |
| ABSORPTION | 50, 100, 200, 300 | 4 | None |
| REACH | 25, 75, 150 | 3 | None |
| CRITICAL_CAPACITY | 100, 200, 300 | 3 | CAPACITY level 1 |

Costs debit atomically with the new level. The default progression is reachable
by repeated eligible kills and capacity-first purchases. Definitions allow up to
16 levels per upgrade and starter capacity up to 10,000; tuning arbitrary custom
cost tables can make progression unreachable and needs separate balance review.
Dynamic additional upgrade IDs/effects and JSON resource decoding are not
implemented by this fixed-enum foundation.

## Save/wire DTO

`BiomassCodec.encode(ledger, definitions)` and `decode(bytes, definitions)` use
this independent canonical payload, maximum input **128 bytes**:

| Field | Representation |
| --- | --- |
| Schema | Unsigned byte, exactly 1 |
| Revision | Big-endian nonnegative signed 64-bit integer |
| Unlocked | Byte exactly 0 or 1 |
| Balance | Big-endian signed 32-bit integer, validated nonnegative |
| Upgrade count | Unsigned byte, 0–5 |
| Repeated upgrade | Ascending unique unsigned-byte ID, positive unsigned-byte level |

IDs are explicitly assigned: CAPACITY=1, EFFICIENCY=2, ABSORPTION=3, REACH=4,
CRITICAL_CAPACITY=5. Changing enum declaration order must not change IDs. Unknown
schema/ID, duplicate/unsorted pairs, zero levels, malformed state, oversized,
truncated or trailing bytes reject the complete payload. No partial restore.
Player identity and outer saved-data/protocol framing belong to their owners.
An equivalent structured saved DTO can use the ledger constructor followed by
`definitions.validate`; the binary codec is not a mandatory outer-save format.

## Verification handoff

Added JUnit scenarios cover earning/purchase reachability, independent expected
gain/cost/capacity numbers, duplicate eligible kills, spatial bounds, nonfinite
measurements, failed/stale transaction handling, mode bypass/free reset,
prerequisites/max levels, immutable state, revision exhaustion, exact wire bytes,
truncation and malformed payloads. Runtime scenarios also cover first-kill atomic
unlock/credit, repeated purchase rejection, failed compare-and-commit,
cross-player isolation, codec-reconstructed restart/replay and currency
conservation. A Brigadier parse test verifies required nonnegative long revision
arguments; it does not execute commands with a player. No test/build has been executed by this agent;
the integrator owns the frozen-source queue. Fabric currently needs its explicit
source/test includes extended by the build owner to include `progression/**`.

Next acceptance must exercise unlock → eligible duplicate kill → gain → purchase
→ paid morph/ability and failed-action rollback, real server save/restart,
observer/owner synchronization and HUD on both loaders. Until then this package
is an unexecuted domain foundation, not a working Biomass mode.
