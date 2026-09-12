"""Validate traceability integrity, not gameplay or native implementation."""
import copy
import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
MANIFEST = ROOT / "testing/scenarios/coverage.json"
PROVENANCE = {
    "legacy", "correction", "modern", "optional", "legacy-unfinished",
    "verification", "existing-unverified",
}


def validate_coverage(data):
    """Raise ValueError for invalid or internally inconsistent contracts."""
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    require(isinstance(data, dict), "manifest must be an object")
    require(data.get("schema_version") == 1, "unsupported schema version")
    for field in ("audit_source", "audit_revision", "plan_source", "scope"):
        require(isinstance(data.get(field), str) and data[field].strip(), field)
    owners = data.get("owners")
    require(isinstance(owners, dict) and owners, "owners must be nonempty")
    scenarios = data.get("scenarios")
    entries = data.get("entries")
    require(isinstance(scenarios, list) and scenarios, "scenarios must be nonempty")
    require(isinstance(entries, list) and entries, "entries must be nonempty")
    scenario_ids = set()
    for scenario in scenarios:
        require(isinstance(scenario, dict), "scenario must be an object")
        ident = scenario.get("id")
        require(isinstance(ident, str) and re.fullmatch(r"[a-z0-9-]+", ident), "scenario ID")
        require(ident not in scenario_ids, "duplicate scenario ID")
        scenario_ids.add(ident)
        require(scenario.get("status") == "planned", "scenario evidence is planned only")
        require(scenario.get("kind") in {"future-contract", "initial-model-contract"}, "scenario kind")
    by_id = {}
    for entry in entries:
        require(isinstance(entry, dict), "entry must be an object")
        ident = entry.get("id")
        require(isinstance(ident, str) and re.fullmatch(r"[A-Z0-9-]+\.[a-z0-9-]+", ident), "entry ID")
        require(ident not in by_id, "duplicate entry ID")
        by_id[ident] = entry
        require(entry.get("audit_ref") == ident.split(".")[0], "audit reference mismatch")
        require(entry.get("owner") in owners, "unknown owner")
        require(entry.get("provenance") in PROVENANCE, "unknown provenance")
        for field in ("source_evidence", "expected_behavior"):
            require(isinstance(entry.get(field), str) and entry[field].strip(), field)
        for field in ("dependencies", "scenario_ids"):
            values = entry.get(field)
            require(isinstance(values, list) and all(isinstance(v, str) for v in values), field)
            require(len(values) == len(set(values)), "duplicate " + field)
        require(entry["scenario_ids"], "entry has no scenario")
        require(set(entry["scenario_ids"]) <= scenario_ids, "dangling scenario")
        applicability = entry.get("loader_applicability")
        evidence = entry.get("evidence_status")
        require(isinstance(applicability, dict) and set(applicability) == {"fabric", "neoforge"}, "loader applicability")
        require(set(applicability.values()) <= {"required"}, "both loaders require verification")
        require(isinstance(evidence, dict) and set(evidence) == set(applicability), "loader evidence")
        require(set(evidence.values()) == {"planned"}, "native completion cannot be inferred from contracts")
    for ident, entry in by_id.items():
        require(set(entry["dependencies"]) <= set(by_id), "dangling dependency")
        require(ident not in entry["dependencies"], "self dependency")
    visiting, complete = set(), set()
    def visit(ident):
        require(ident not in visiting, "dependency cycle")
        if ident in complete:
            return
        visiting.add(ident)
        for dependency in by_id[ident]["dependencies"]:
            visit(dependency)
        visiting.remove(ident)
        complete.add(ident)
    for ident in by_id:
        visit(ident)
    used = {scenario for entry in entries for scenario in entry["scenario_ids"]}
    require(used == scenario_ids, "orphan scenario contract")


class CoverageTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_manifest_integrity(self):
        validate_coverage(self.manifest)

    def test_all_numbered_audit_findings_have_contracts(self):
        expected = {f"L{i:02}" for i in range(1, 24)} | {f"A{i:02}" for i in range(1, 8)}
        actual = {entry["audit_ref"] for entry in self.manifest["entries"]}
        self.assertTrue(expected <= actual, expected - actual)

    def test_initial_scenarios_are_explicitly_planned(self):
        initial = {s["id"] for s in self.manifest["scenarios"] if s["kind"] == "initial-model-contract"}
        self.assertEqual(initial, {"dragon-renderer", "sniffer-middle-legs", "husk-empty-hand",
                                  "husk-held-item", "bat-bee-flight", "nametag", "transformation-clip",
                                  "intentional-assertion", "multiplayer-smoke"})

    def test_unfinished_and_preservation_are_not_legacy_loss(self):
        for entry in self.manifest["entries"]:
            if entry["audit_ref"] == "BIOMASS":
                self.assertEqual(entry["provenance"], "legacy-unfinished")
            if entry["audit_ref"] == "PRESERVE":
                self.assertEqual(entry["provenance"], "existing-unverified")

    def test_rejects_plausible_manifest_errors(self):
        # Mutate one invariant at a time. These are negative schema examples,
        # not candidate-generated gameplay oracles.
        mutations = {
            "duplicate entry": lambda d: d["entries"].append(copy.deepcopy(d["entries"][0])),
            "duplicate scenario": lambda d: d["scenarios"].append(copy.deepcopy(d["scenarios"][0])),
            "dangling dependency": lambda d: d["entries"][0].update(dependencies=["MISSING.case"]),
            "dangling scenario": lambda d: d["entries"][0].update(scenario_ids=["missing-case"]),
            "unknown owner": lambda d: d["entries"][0].update(owner="nobody"),
            "missing loader": lambda d: d["entries"][0]["evidence_status"].pop("fabric"),
            "false completion": lambda d: d["entries"][0]["evidence_status"].update(fabric="passed"),
            "missing expectation": lambda d: d["entries"][0].update(expected_behavior=""),
            "bad schema": lambda d: d.update(schema_version=99),
        }
        for name, mutate in mutations.items():
            with self.subTest(name=name):
                candidate = copy.deepcopy(self.manifest)
                mutate(candidate)
                with self.assertRaises(ValueError):
                    validate_coverage(candidate)

    def test_rejects_dependency_cycle(self):
        candidate = copy.deepcopy(self.manifest)
        a, b = candidate["entries"][:2]
        a["dependencies"] = [b["id"]]
        b["dependencies"] = [a["id"]]
        with self.assertRaisesRegex(ValueError, "dependency cycle"):
            validate_coverage(candidate)


if __name__ == "__main__":
    unittest.main()
