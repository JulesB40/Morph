# Evidence API

`morph_lab.evidence.write_report(root)` writes and returns `root/index.html`.
Pass one run directory containing `manifest.json`/`result.json`, or a parent of
run directories. The standalone HTML includes status and manifest-text filters,
media previews, artifact links, computed SHA-256 hashes and escaped raw manifest
and result details. Optional manifest `evidence` entries use `path` (relative to
the run) and `role` (for example `baseline actor`, `candidate observer`, or
`native reference`). All in-run files are linked, including events, scenario
results and process logs. Traversal and external symlinks are refused. Missing
media is reported independently of assertion status.

`encode_video(frames, output, *, framerate=20.0, ticks=None, dropped_frames=0,
ffmpeg="ffmpeg", timeout=60.0)` encodes the supplied PNG paths in order into a
new MP4, returns a metadata dictionary and writes `output.mp4.json`. Existing
clips/metadata are never overwritten. Input/encoding failures return
`infrastructure_failure`; bounded subprocess expiration returns `timeout`.
Invalid API arguments raise `ValueError`. FFmpeg runs without a shell or visible
Windows helper window. No game capture or placeholder media is created.

Metadata records copied input hashes, encoder executable hash and argv, declared
frame rate, per-frame ticks when supplied, adapter-reported dropped-frame count,
output hash and encoding diagnostics. The encoded frame rate is playback rate:
a tick-sampled replay does not measure real-time FPS. This module does not infer
frame loss or certify rendering, animation, audio, or numerical game assertions.
The report exposes raw telemetry links; it does not compute visual differences
or telemetry plots.

Run the focused tests from repository root (PowerShell):

```powershell
$env:PYTHONPATH='tools/morph-lab'
python -m unittest discover -s tools/morph-lab/tests -p test_evidence.py -v
```

The FFmpeg/ffprobe test uses a two-pixel synthetic image and verifies decoded
frame count and playback rate. It skips when the binaries are absent. Symlink
escape verification skips where the OS denies symlink creation.
