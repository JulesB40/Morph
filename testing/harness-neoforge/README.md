# NeoForge client bridge

This test-only mod is a process-local adapter, not a scenario oracle. Its Java/resources
directories must be included only in the dedicated lab source set and lab client run;
they must not enter the production JAR. There are no production hooks or mixins.

Required JVM properties:

* `morph.lab.enabled=true`
* `morph.lab.runDir=<absolute, fresh per-client evidence directory>`
* `morph.lab.server=127.0.0.1:<port>` (only literal loopback hosts accepted)

Optional: `morph.lab.role=actor` or `observer`, and
`morph.lab.timeoutSeconds=180` (10–3600). Supply a separate game directory and player
identity for every process. The bridge never starts or modifies a server/world. It
connects once after client resource loading, bypassing initial menu screens. It
disables pause-on-focus-loss in memory, without saving global options.

The controller writes a UTF-8 JSON request to a temporary file, closes it, then
atomically renames it to `requests/<sortable-sequence>.json`. One request is handled
at a time, in filename order. Leave requests in place as evidence. Every request has
a unique `id` matching `[a-zA-Z0-9_-]{1,80}`. Limit each file to 16 KiB.

```json
{"id":"0001","op":"state"}
{"id":"0002","op":"command","command":"morph reset"}
{"id":"0003","op":"look","yaw":0,"pitch":0}
{"id":"0004","op":"input","keys":["forward","jump"],"ticks":20}
{"id":"0005","op":"capture"}
{"id":"0006","op":"exit"}
```

`input` changes the Minecraft key mappings consumed by normal client input. It holds
the specified keys for 1–1200 client ticks and releases them before completing. Keys:
`forward`, `back`, `left`, `right`, `jump`, `sneak`, `sprint`. Empty keys implement a
tick wait. `release` clears held keys. `look` sets player view angles. The bridge does
not set position, velocity, movement abilities or Morph state to manufacture results.

Read `events.ndjson` for `started`, `connecting`, `ready`, `input_started`,
`completed`, `failed`, and `finished`. Rows contain `schema:1`, `loader`, `role`,
`tick`, optional request `id`, and `detail`. Wait for `ready` before submitting actions.
Capture completion is emitted only after framebuffer PNG writing succeeds, with
`detail.png` relative to the run directory. Captures occur on a completed render frame.
State snapshots contain observed client position, velocity, pose, flight/swim flags,
and client-visible players, including each player's replicated `form` and
`showNameTag` preference read through existing production accessors. Unmorphed form
is normalized to `minecraft:player`. They are not authoritative server measurements.

`command` sends an ordinary command packet using the connected player's permissions;
its completion means sent, not accepted. The controller must assert resulting server
and observer state independently. Exit emits `finished` with status `completed`, which
means bridge actions completed, **not gameplay passed**. Any invalid command file,
disconnect after readiness, or timeout fails the bridge and stops its own client.
A JVM exit code of zero alone is never evidence of a passing scenario. Require the
expected correlated events, artifacts, and independent scenario assertions. Startup
failure may occur before any event file exists. The outer runner must enforce a
wall-clock timeout as well, including before tick events start.

API provenance: NeoForge 26.2.0.82 source JAR documents `ClientTickEvent.Pre` and
`RenderFrameEvent.Post`; Minecraft 26.2 cached client bytecode exposes
`ConnectScreen.startConnecting`, `Minecraft.gui`, `GameRenderer.mainRenderTarget`,
`Screenshot.takeScreenshot` and `NativeImage.writeToFile`. The first pilot must verify
background focus/input and actual GPU capture behavior; source inspection alone does
not establish either. No desktop automation is used.
