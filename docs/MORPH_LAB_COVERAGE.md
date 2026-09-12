# Morph Lab audit traceability

`testing/scenarios/coverage.json` records finite acceptance contracts derived from
`PORT_GAP_AUDIT_2026-09-12.md` and `PARALLEL_IMPLEMENTATION_PLAN.md`. It covers all
L01–L23 and A01–A07 findings, plus the unnumbered rendering, combat, movement,
multiplayer, save, configuration, unfinished Biomass, compatibility, performance,
release and deliberate-policy findings. The audit describes alpha.8 source commit
`8955f88`; that is provenance for the findings, not a tested revision of this lab.

The first manifest contains 391 entries and 375 planned scenario references.
Each row has a stable ID, audit reference, provenance, domain owner, concrete
dependencies, source evidence, expected behavior and loader-specific applicability
and evidence status. The owner numbers follow the implementation plan's domain
allocation; they do not imply that an agent has implemented the feature.

All evidence statuses are **planned** for both Fabric and NeoForge. A scenario
reference is a contract name, not proof that an executable scenario exists or has
run. The catalog intentionally includes future contracts without requiring files
for them. Its initial model-contract names are:

- `dragon-renderer`, `sniffer-middle-legs`
- `husk-empty-hand`, `husk-held-item`
- `bat-bee-flight`, `nametag`
- `transformation-clip`, `intentional-assertion`, `multiplayer-smoke`

Model tests cannot establish native rendering, actual limb motion, authoritative
damage, audiovisual output, real multiplayer isolation or restart persistence.
In particular, `multiplayer-smoke` remains a planned contract for two real clients;
`transformation-clip` requires an actual playable capture, and Husk cases require
independent native/server observations. The intentional assertion contract expects
a deliberately wrong assertion to fail; a model unit test for this manifest does
not satisfy that worker acceptance gate.

`legacy` identifies restored behavior; `correction` identifies audited defects;
`modern` identifies additions. `optional` means an explicit gameplay or setting
decision requires verification, not that the behavior is implemented.
`legacy-unfinished` prevents Biomass scaffolding from being advertised as lost
working functionality. `existing-unverified` identifies preservation checks for
behavior already present in source, without claiming native-runtime completeness.
`verification` covers evidence and release work. Initial proposed policy defaults
are acceptance targets from the plan, not observations of current behavior.

Rows split major actions: for example, Wither startup, regeneration, skulls and
lifecycle have separate IDs; equipment and saddle applicability are separate per
rideable family. Species matrices still need explicit per-form applicable and
not-applicable cases, reviewed native action lists and equipment/variant cases.
The manifest is finite audit coverage, not an exhaustive native capability census.
No missing scenario or absent per-species capability implies implementation.

Run the manifest checks without a build or game launch:

```powershell
python -m unittest discover -s tools/morph-lab/tests -p test_coverage.py -v
```

The checks validate schema, numbered-finding coverage, owner references, unique
IDs, scenario references, dependency existence and cycles, both-loader evidence,
and preservation/unfinished classification. Deliberately corrupted manifests
exercise rejection paths. They do not copy candidate behavior into gameplay
expectations and do not assert that all prose requirements are implemented.

Before promoting any row beyond planned, introduce a reviewed evidence schema
and link immutable baseline/candidate source and JAR hashes, scenario revision,
independent expectations, loader, raw results and relevant native artifacts.
Unsupported, skipped, timeout and infrastructure-failed results must remain
distinct from pass. Keep this planned catalog separate from run results until
that evidence schema exists; do not rewrite statuses based on unit-test success.
