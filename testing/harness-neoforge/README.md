# NeoForge client bridge

This test-only mod is a process-local adapter, not a scenario oracle. Its Java/resources
directories must be included only in the dedicated lab source set and lab client run;
they must not enter the production JAR. There are no production hooks or mixins.

Required JVM properties:

* `morph.lab.enabled=true`
* `morph.lab.runDir=<absolute, fresh per-client evidence directory>`
* `morph.lab.server=127.0.0.1:<port>` (only literal loopback hosts accepted)

Optional: `morph.lab.role=actor` or `observer`, `morph.lab.hidden=true`, and
`morph.lab.timeoutSeconds=180` (10–3600). Supply a separate game directory and player
identity for every process. The bridge never starts or modifies a server/world. It
connects once after client resource loading, bypassing initial menu screens. It
disables pause-on-focus-loss in memory, without saving global options.

Hidden mode hides this process's GLFW window on its first client tick. It does not
call focus APIs or control another application. The startup window may be visible
before that tick; this is not a guarantee of an invisible launch. `started`, `ready`
and state details record actual `windowHidden`, `hiddenRequested`, `gpuVendor`,
`gpuRenderer`, `gpuBackend` and `gpuDriver`. These use the active Minecraft GPU device
and GLFW visibility attribute, rather than assuming the requested GPU/visibility.

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

`{"id":"0007","op":"disconnect"}` closes the current server connection and
acknowledges with `connected:false`, keeping this client process alive. While
explicitly disconnected, only `state`, `reconnect` and `exit` are accepted.
`{"id":"0008","op":"reconnect"}` connects once to the same configured endpoint;
its correlated `completed` arrives only after the new playable world is ready (also
emitting a new `ready` event). Await the disconnect acknowledgement before stopping
the external server and wait for server readiness before requesting reconnect.
Unexpected connection loss after readiness remains fatal. The original overall
timeout continues across disconnect/reconnect; the controller must budget for restart.

Named component probes use `{"id":"0009","op":"probe","name":"wither-heads"}`.
Supported names are `wither-heads`, `sniffer-middle-legs`, and `dragon-renderer`.
They return numeric/type evidence with `detail.passed` and explicit component scope;
an unmet assertion emits a recoverable `failed` event so later observations can run.
Wither checks side-head world angles and actual native model rotations for three
body/look/pitch combinations, including an untreated-state control that must differ.
Sniffer samples all six native leg rotations at four swim ages. Dragon compares
native renderer state type with the production extraction adapter's result.
These do not prove visible pixels, actual movement or complete rendering behavior.

`{"id":"0010","op":"probe","name":"capture-failure"}` injects one test-owned
I/O failure at the next capture frame, before framebuffer readback. It emits
`failed` with `injected:true`, `recoverable:true`, and the capture stage, then clears
pending capture state. Submit a new `state` and ordinary `capture` to establish
recovery from their own acknowledgements and PNG. This tests the bridge's request
recovery path; it does not inject a production renderer or actual GPU failure.

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
unexpected disconnect after readiness, or timeout fails the bridge and stops its own client.
A JVM exit code of zero alone is never evidence of a passing scenario. Require the
expected correlated events, artifacts, and independent scenario assertions. Startup
failure may occur before any event file exists. The outer runner must enforce a
wall-clock timeout as well, including before tick events start.

API provenance: NeoForge 26.2.0.82 source JAR documents `ClientTickEvent.Pre` and
`RenderFrameEvent.Post`; Minecraft 26.2 cached client bytecode exposes
`ConnectScreen.startConnecting`, `Minecraft.gui`, `GameRenderer.mainRenderTarget`,
`Screenshot.takeScreenshot` and `NativeImage.writeToFile`. Hidden/device inspection
uses `Window.handle`, GLFW 3.4.1 `glfwHideWindow`/`glfwGetWindowAttrib`, and
`RenderSystem.getDevice().getDeviceInfo()`. The first pilot must verify
background focus/input and actual GPU capture behavior; source inspection alone does
not establish either. No desktop automation is used.
