# Morph Lab pilot results — 2026-09-12

The harness is usable for the feature implementation wave. Game processes use
their own control channels and worlds; workers admit immutable source snapshots
through the resource queue. Graphics jobs remain limited to one lane. The
two-client session reserves its server and both clients together.

Evidence is retained outside the checkout in `../Morph-lab-runs/runs/`.
`index.html` links manifests, source hashes, raw logs, scenario results, PNGs
and encoded clips. IDs below name directories under that root.

| Run | Observed result |
| --- | --- |
| `a0a0783fa76c4de6b66a1b929bfbfbd2` | Fabric baseline: dragon render state rejected; Sniffer middle legs static; deliberate wrong assertion failed. Bat/bee flight and nametag controls passed. Actual hidden framebuffer sequence captured. |
| `3a334f39eaaf4b49a053dc07f1fb408f` | Native NeoForge Husk baseline: four discrepancies; rejected-damage control passed. Normal native Hunger 140 ticks versus Morph 210; Easy native zero versus Morph 105; held-item rule differed. |
| `b78436414e85490a91f59a5bbe813032` | All five Husk cases passed after the hand/duration fixes on NeoForge. |
| `408a78a5e0444024978963f81baa9574` | The same five native Husk cases passed on Fabric. |
| `cbf681c398e24a048e7f988f8084b7ea` | Fabric candidate: all six Sniffer legs varied; flight and nametag controls passed; the client observed one `morph:morph` sound event in `PLAYERS`. |
| `6b62bd83164545f2a734de1396efbd71` | Fabric dragon now produces native `EnderDragonRenderState`; inspected PNG contains dragon geometry. Transformation capture succeeded; 26 frames encoded to MP4 with tick metadata. |
| `a92203a9eb6749609e3ccc2845804fd8` | Actual NeoForge server plus two separate clients: actor moved 4.719 blocks, observer 0; authoritative and both-client bat/hidden-nametag state agreed; a new server process loaded the same saved world and both clients reconnected with the same appearance. All four game processes exited cleanly. |
| `27afb092d0bb4fc8b45b2b33004497c2` | NeoForge unit tests and build passed with source verification after moving test runtime logs under `build/`. The preceding run `2184dfc744b846609f3bfab469af83fe` was correctly invalidated for unexpected root `logs/latest.log`, despite successful tests. |
| `2be18d4a52ca4608ab41c2d03b9b0179` | One NeoForge client plus server: Wither head/model angles, six Sniffer limbs, dragon state and real PigRenderer fault probes passed. Scoped extraction/submission errors recovered; failed replacement retained the avatar route; clearing failure state restored actual submit nodes. An injected capture failure was followed by connected state and a valid PNG. Inspected final frame shows the bat. Source and launcher hashes matched; both processes cleaned up. |

The Python suite currently has 91 tests, with two Windows symlink-privilege skips.
These include queue contention, stale lease accounting, controller/descendant
cleanup, unrelated-process survival, invalid source mutation, strict failed
assertions, real harmless subprocess bridge sessions, and memory-floor aborts.
They are infrastructure tests, not 91 Minecraft gameplay scenarios.

## Capacity and evidence limits

- The actual graphics backend was Intel OpenGL 3.3. NVIDIA selection was not
  established. Hidden rendering worked; the final one-client probe explicitly
  recorded `windowHidden=true` and `windowFocused=false`. Startup can briefly
  create a visible window. No desktop keyboard/mouse input was used.
- The first two-client run peaked near 5.93 GB of job committed memory. Individual
  Java peak working sets were about 0.71 GB server and 1.91/1.88 GB clients.
  Committed memory is not physical RSS; summed process peaks are not simultaneous
  whole-job RSS. The ordinary multiplayer reservation was increased to 4608 MiB.
- A separate 768 MiB/client heap pilot remains capacity-dependent. The successful
  one-client lane measured about 0.71 GB server and 1.81 GB client working sets,
  with 3.21 GB overall committed memory. It reserves 3072 MiB and retains queue
  headroom plus a 384 MiB physical-memory abort floor. No further graphics slots
  are enabled from these measurements.
- The two-client PNGs establish independent capture, but their original camera
  framing/chat overlays do not prove visual appearance parity. Server and client
  state assertions establish synchronization separately. The later framed bat
  capture is a single-client image, not a replacement multiplayer test.
- Component pose/state tests are not full-frame animation or all-mob gameplay
  tests. Dragon wing/tail parity, deferred GPU errors, partial submit rollback,
  arbitrary addons/resource packs, and per-process audible audio remain outside
  this pilot's guarantees.
- Sound evidence proves a client notification, not audible mixing. MP4 files are
  tick-sampled replays, not FPS measurements. Loopback ports use an availability
  probe; the owned server must reach ready, and a bind race fails the run.

The 375 scenario contracts in the coverage manifest remain a planned feature
matrix. These pilot results do not mark all contracts implemented or passed.
