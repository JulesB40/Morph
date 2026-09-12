# Morph Lab

Developer-only verification tooling for immutable source snapshots, resource
admission, owned process cleanup and browsable evidence. Baseline failure is a
useful result: it is never converted to pass merely because it was anticipated.

## Recorded implementation status — 2026-09-12

The runner, SQLite queue, Windows process supervisor, snapshot hashing, evidence
report and FFmpeg encoding are implemented. Loader probes are opt-in with
`-PmorphLab=true`. The following actual runs exist in the external run
root; their manifests identify the exact tested source, including dirty changes.

| Run | Observed result | Evidence limit |
| --- | --- | --- |
| Fabric `a0a0783fa76c4de6b66a1b929bfbfbd2` | Real client framebuffer PNGs and transformation MP4; dragon, Sniffer middle legs and deliberate assertion fail; bat/bee and nametag controls pass | Dragon checks render-state acceptance; Sniffer checks native model-part motion. These are not exhaustive pixel or animation acceptance. |
| NeoForge Husk baseline `3a334f39eaaf4b49a053dc07f1fb408f` | Five native cases: four fail; rejected-damage control passes | Extends the earlier four-case run `22ecf5c5e8d74053b6c682fb3c46667b` with Easy zero-duration coverage. |
| NeoForge Husk candidate `b78436414e85490a91f59a5bbe813032` | All five native cases pass; source verified and cleanup confirmed | Native damage callback comparison, not synthetic client combat input or all combat parity. |
| NeoForge multiplayer `a92203a9eb6749609e3ccc2845804fd8` | Two real clients and an external server pass state synchronization, independent input and exit/restart persistence; source verified | Numeric form/nametag state is verified. Captured camera/chat framing is inadequate for visual form proof. |

The Fabric environment record reports a hidden, unfocused owned window after
GameTest startup and the Intel renderer. This proves capture worked in that
observed state. Startup focus behavior and an unrelated foreground application remain unproven.
A separate NeoForge run now covers two simultaneous real clients. The MP4 is tick-sampled playback,
not a real-time FPS benchmark; audio-event evidence does not certify audible output.

The five-case Husk baseline used effective local difficulty 1.5 for normal
empty-hand/offhand cases: native Hunger lasted 140 ticks while Morph applied 210.
A held main-hand item suppressed native Hunger but not Morph's effect. On Easy
at effective difficulty 0.75, native Hunger was present immediately with duration
0 while Morph applied 105 ticks. The candidate matches all five native expectations,
including zero-duration effect presence and rejected damage. Both runs have verified
source snapshots and confirmed cleanup. Assertions compare Hunger behavior;
recorded attack damage amounts differ and are not claimed equal.

The real NeoForge session moved the actor 4.719333868 blocks horizontally while
the observer moved 0. Both clients reported hidden windows on Intel OpenGL. Server,
actor and observer state agreed on bat form and hidden nametag before and after
an observed server process exit, new server launch and client reconnect. Cleanup
was confirmed for all four Java lifetimes (including the restarted server) and
the wrapper. The screenshots contain real game frames, but their camera/chat
framing does not establish visible bat geometry or hidden nametag pixels.

The wrapper's Windows job peak committed memory was 5,925,285,888 bytes; committed
memory is not resident working set. Direct Java peak working sets were 712,904,704
bytes (initial server), 1,912,754,176 (actor), 1,879,703,552 (observer), and
673,660,928 (restarted server). The initial server plus client peaks sum to about
4.20 GiB; peaks are not synchronized measurements, and the two server lifetimes
must not be added as simultaneous residents. This makes the 4096 MiB reservation
inadequate as a conservative bound. A 4608 MiB reservation adjustment is planned;
no additional rendered-job slots are enabled or justified by this single run.

Fake-process channel/supervisor tests remain useful protocol checks alongside
this native session; they are not its evidence. Broader tracking loss/re-entry,
dimension/death lifecycle cases and Fabric real multiplayer still need evidence.
NeoForge capture/fallback recovery, Wither orientation and Sniffer coverage, plus
Fabric sound-event acceptance, remain pending. The planned coverage catalog's
hundreds of contracts are not hundreds of implemented cases. See
[coverage scope](../../docs/MORPH_LAB_COVERAGE.md).

## Windows usage

Use Python 3.13, the project's required JDK on `PATH`, and FFmpeg/ffprobe on `PATH`
for clip work. Start PowerShell at the repository root. `doctor` reports resolved
tools and available physical memory; it does not certify the game environment.

```powershell
python tools/morph-lab/lab.py doctor
python tools/morph-lab/lab.py submit --repo . --spec testing/scenarios/fabric-baseline-job.json
python tools/morph-lab/lab.py worker --memory-mb 6144 --reserve-mb 2048 --cpu 2 --gpu 1
python tools/morph-lab/lab.py status
python tools/morph-lab/lab.py report
```

Run the native Husk baseline separately:

```powershell
python tools/morph-lab/lab.py submit --repo . --spec testing/scenarios/neoforge-server-baseline-job.json
python tools/morph-lab/lab.py worker --memory-mb 6144 --reserve-mb 2048 --cpu 2 --gpu 0
python tools/morph-lab/lab.py report
```

`submit` prints the job ID and run directory. Each worker invocation executes at
most one fitting job; it is not a persistent daemon. It prints `idle` when nothing
fits. Baseline scenarios currently fail by design of their independent acceptance
expectations, so a worker exit code of 1 requires inspection rather than retrying
until green. Exit code 2 indicates a CLI/setup error. Fabric scenario failures are
read from `probes/result.json` even when Gradle exits successfully.

By default, the root is `Morph-lab-runs` beside the checkout containing the CLI.
For the main checkout on this machine that is
`C:\Users\Jules\Documents\ChatGPT\Morph mod\Morph-lab-runs`.
It contains `queue.sqlite3` and `runs/<run-id>/`. A CLI in another worktree derives
a different sibling root. To share one queue, pass the same explicit root to
every command, before the subcommand:

```powershell
$labRoot = 'C:\Users\Jules\Documents\ChatGPT\Morph mod\Morph-lab-runs'
python tools/morph-lab/lab.py --root $labRoot status
python tools/morph-lab/lab.py --root $labRoot report
python tools/morph-lab/lab.py --root $labRoot cancel JOB_ID_FROM_SUBMIT
```

Replace the cancellation placeholder with the returned ID. Cancellation requests
owned-process cleanup; expired leases and unconfirmed cleanup retain reservations.
Do not delete queue rows to release potentially live processes. Use the queue's
recovery API only after independently confirming cleanup; there is no recovery
CLI command yet.

The memory budget is a reservation ceiling, not measured peak JVM memory. Admission
also considers free physical memory minus the reserve. Keep consistent capacity
arguments across workers sharing a queue. Start with one rendered job; additional
slots require measured headroom. Reserve all roles of a multiplayer session as
one job, and run performance work exclusively.

## Job contracts and evidence

Specs use an argument array, `cwd`, timeout, resource reservations and optional
environment values. `{source}` resolves to the frozen source and `{run}` to its
run directory. Checked-in baseline specs bound Gradle workers and heap settings.
`scenario_results` lists JSON result files relative to the run; `artifacts` lists
glob patterns relative to frozen source for SHA-256 recording. Never run a job
against an agent's actively edited checkout: submit captures a separate snapshot
and the worker verifies it before and after execution.

Inspect `manifest.json`, `result.json`, logs, events, scenario results and captured
media together. `report` prints the path to `runs/index.html`. Its static browser
exposes failures and raw evidence; it does not independently certify visuals.
See [the evidence API](EVIDENCE.md) for clip encoding and metadata. Preserve failing
baseline bundles and tested artifact hashes when preparing a candidate comparison.

`morph_lab.session.run_session(spec, run, source)` is a Python API, not a CLI
subcommand. It requires explicit server/actor/observer argv and cwd values, two
distinct identities and a caller-reserved port. Role templates support `{source}`,
`{run}`, `{role}`, `{port}`, `{control}` and `{game}`. The caller must prepare worlds,
launch configuration, atomic admission and provenance. The NeoForge two-client/restart run above demonstrates this smoke contract; it
does not establish the full multiplayer matrix or Fabric equivalence.

Run tooling tests without starting Minecraft:

```powershell
$env:PYTHONPATH = 'tools/morph-lab'
python -m unittest discover -s tools/morph-lab/tests -v
```

These tests include synthetic media and fake subprocess/channel fixtures. They
cannot substitute for the remaining native acceptance gates.
