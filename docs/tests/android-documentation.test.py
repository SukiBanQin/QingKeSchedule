#!/usr/bin/env python3
"""Validate the Android handoff contract without installing app dependencies."""

import re
import subprocess
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs" / "Android"
NAMES = (
    "product-baseline.md",
    "technical-design.md",
    "implementation-plan.md",
    "handoff.md",
)


def missing_links(path, contents):
    missing = []
    for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", contents):
        url = urlsplit(target)
        if url.scheme or not url.path:
            continue
        resolved = (path.parent / unquote(url.path)).resolve()
        if not resolved.exists():
            missing.append(target)
    return missing


class AndroidDocumentationTests(unittest.TestCase):
    def test_required_documents_and_local_references(self):
        for name in NAMES:
            with self.subTest(document=name):
                path = DOCS / name
                self.assertTrue(path.is_file(), name)
                contents = path.read_text(encoding="utf-8")
                self.assertEqual(len(re.findall(r"^# ", contents, re.M)), 1)
                self.assertEqual(missing_links(path, contents), [])

    def test_link_checker_rejects_missing_links_and_directory_case_is_canonical(self):
        path = DOCS / "handoff.md"
        self.assertEqual(missing_links(path, "[rules](../../AGENTS.md)"), [])
        self.assertEqual(
            missing_links(path, "[missing](not-an-existing-document.md)"),
            ["not-an-existing-document.md"],
        )
        # Check canonical spelling separately: macOS can resolve wrong-case paths.
        self.assertIn("Android", [p.name for p in (ROOT / "docs").iterdir()])

    def test_baseline_commit_exists_and_matches_handoff(self):
        hashes = []
        for name in ("product-baseline.md", "handoff.md"):
            found = re.findall(r"`([0-9a-f]{40})`", (DOCS / name).read_text())
            self.assertEqual(len(found), 1, name)
            hashes.extend(found)
        self.assertEqual(hashes[0], hashes[1])
        subprocess.run(
            ["git", "cat-file", "-e", hashes[0] + "^{commit}"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        )

    def test_acceptance_and_phase_ids_are_unique_and_complete(self):
        baseline = (DOCS / "product-baseline.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        acceptance = re.findall(r"^\| (A\d{2}) \|", baseline, re.M)
        self.assertEqual(acceptance, [f"A{i:02}" for i in range(1, 12)])
        phases = re.findall(r"^\| (P\d+) ", plan, re.M)
        self.assertEqual(phases, [f"P{i}" for i in range(7)])
        references = set(re.findall(r"\bA\d{2}\b", plan))
        self.assertTrue(references <= set(acceptance))
        handoff = (DOCS / "handoff.md").read_text()
        for decision in ("D01", "D02", "D03", "D04"):
            self.assertIn(decision, baseline)
            self.assertIn(decision, handoff)

    def test_rules_point_to_handoff_and_preserve_delivery_requirements(self):
        rules = (ROOT / "AGENTS.md").read_text()
        self.assertIn("docs/Android/handoff.md", rules)
        self.assertIn("Git commit", rules)
        self.assertIn("编写或更新相关测试", rules)
        self.assertIn("人工交接", rules)
        for name in NAMES:
            self.assertIn(name, rules)

    def test_model_guidance_and_review_gate_are_present(self):
        plan = (DOCS / "implementation-plan.md").read_text()
        rows = re.findall(r"^\| (Astra|Terra) \| (低|中|高) \|", plan, re.M)
        self.assertEqual(len(rows), 6)
        self.assertEqual(set(rows), {
            (model, effort)
            for model in ("Astra", "Terra")
            for effort in ("低", "中", "高")
        })
        for marker in (
            "建议模型／思考档位：", "选择理由：", "提交编号／范围：",
            "实际模型／档位（如已知）：", "证据不足", "不修改应用代码",
        ):
            self.assertIn(marker, plan)
        rules = (ROOT / "AGENTS.md").read_text()
        self.assertIn("建议设置不等于已应用设置", rules)
        self.assertIn("自动开始下一阶段开发", rules)
        handoff = (DOCS / "handoff.md").read_text()
        self.assertIn("实际窗口模型和档位未核实", handoff)


if __name__ == "__main__":
    unittest.main(verbosity=2)
