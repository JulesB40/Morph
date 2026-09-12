# Morph Lab

Developer-only verification tooling for immutable source snapshots, resource
admission, owned process cleanup and browsable evidence. Baseline failure is a
useful result: it is never converted to pass merely because it was anticipated.

## Recorded implementation status — 2026-09-12

The runner, SQLite queue, Windows process supervisor, snapshot hashing, evidence
report and FFmpeg encoding are implemented. Loader probes are opt-in with
`-PmorphLab=true`. The following actual baseline runs exist in the external run
root; their manifests identify the exact tested source, including dirty changes.

| Run | Observed result | Evidence limit |
| --- | --- | --- |
| Fabric `a0a0783fa76c4de6b66a1b929bfbfbd2` | Real client framebuffer PNGs and transformation MP4; dragon, Sniffer middle legs and deliberate assertion fail; bat/bee and nametag controls pass | Dragon checks render-state acceptance; Sniffer checks native model-part motion. These are not exhaustive pixel or animation acceptance. |
| NeoForge `22ecf5c5e8d74053b6c682fb3c46667b` | Real server Husk probes: empty-hand, held-item and offhand cases fail; rejected-damage control passes | Native damage callback comparison, not synthetic client combat input. |

The Fabric environment record reports a hidden, unfocused owned window after
GameTest startup and the Intel renderer. This proves capture worked in that
observed state. Startup focus behavior, an unrelated foreground application and
two simultaneous real clients remain unproven. The MP4 is tick-sampled playback,
not a real-time FPS benchmark; audio-event evidence does not certify audible output.

The Husk run used effective local difficulty 1.5: native Hunger lasted 140 ticks,
while the morphed player applied 210. A held main-hand item suppressed native
Hunger but not Morph's effect. Rejected damage applied neither damage nor Hunger.
The assertions compare Hunger behavior; different attack damage amounts are
recorded and are not claimed equal.

The session controller and role-specific file channels have fake-process tests.
Those tests establish protocol/supervisor behavior, not Minecraft multiplayer.
Two real client JVMs with an external server, tracking/reconnect checks, true
server exit/restart persistence, full NeoForge rendered acceptance, renderer fault
recovery and measured capacity gates still require native evidence. The planned
coverage catalog's hundreds of contracts are not hundreds of implemented cases.
See [coverage scope](../../docs/MORPH_LAB_COVERAGE.md).

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
launch configuration, atomic admission and provenance. No turnkey real multiplayer
spec or completed two-client/restart acceptance is claimed here.

Run tooling tests without starting Minecraft:

```powershell
$env:PYTHONPATH = 'tools/morph-lab'
python -m unittest discover -s tools/morph-lab/tests -v
```

These tests include synthetic media and fake subprocess/channel fixtures. They
cannot substitute for the remaining native acceptance gates.
