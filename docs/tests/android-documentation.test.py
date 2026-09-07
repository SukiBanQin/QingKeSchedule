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
REVIEW_NAME = "p1-01-review.md"


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
        for name in (*NAMES, REVIEW_NAME):
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

    def test_p1_review_preserves_scope_evidence_and_unfinished_gate(self):
        review = (DOCS / REVIEW_NAME).read_text()
        handoff = (DOCS / "handoff.md").read_text()
        for marker in (
            "9b521db^..9b521db", "0a498b9", "85e3234", "P1-01-R1",
            "SDK location not found", "68 个任务", "各 12 项",
            "未知字段决定待确认", "安装／启动未验证", "用户未验收",
            "schemaVersion 字符串", "非法 UTF-8", "0000-01-01",
            "允许修改：Android/**", "建议模型／思考档位：Terra／中",
        ):
            self.assertIn(marker, review)
        for document in NAMES:
            self.assertIn(REVIEW_NAME, (DOCS / document).read_text())
        self.assertIn("P1 仍未完成", handoff)
        self.assertIn("首轮已审查，结论未通过", handoff)
        self.assertIn("暂不进入 P2", handoff)
        self.assertTrue((ROOT / "docs/tests/android-contract-review-probe.py").is_file())
        for commit in ("9b521db", "0a498b9", "85e3234"):
            subprocess.run(
                ["git", "cat-file", "-e", commit + "^{commit}"],
                cwd=ROOT, check=True, capture_output=True,
            )
        implementation_files = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "9b521db"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(len(implementation_files), 20)
        self.assertTrue(all(path.startswith("Android/") for path in implementation_files))

    def test_r1_rereview_records_actual_evidence_and_remaining_gates(self):
        review = (DOCS / REVIEW_NAME).read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        for marker in ("3924d26", "各 21 项", "R1/R2/R3 修正通过", "无连接设备", "P1-02"):
            self.assertIn(marker, review)
        self.assertIn("bef808b", handoff)
        self.assertIn("P1 仍未完成", handoff)
        self.assertNotIn("已提交待复审", plan)
        self.assertNotIn("未审查修正代码", handoff)

    def test_direct_handoff_templates_cover_both_roles(self):
        plan = (DOCS / "implementation-plan.md").read_text()
        for role in ("Terra → Astra", "Astra → Terra"):
            section = plan.split("### " + role, 1)[1].split("### ", 1)[0]
            blocks = re.findall(r"```text\n(.*?)\n```", section, re.S)
            self.assertEqual(len(blocks), 1, role)
            for marker in ("任务", "提交", "验证", "AGENTS.md", "最终", "交接块"):
                self.assertIn(marker, blocks[0], role)
        rules = (ROOT / "AGENTS.md").read_text()
        for marker in ("最终回复末尾", "唯一一段", "不得只指向文档位置", "使用中文", "无后续任务"):
            self.assertIn(marker, rules)
        handoff = (DOCS / "handoff.md").read_text()
        self.assertIn("用户只复制", handoff)
        self.assertIn("3924d26", handoff)


if __name__ == "__main__":
    unittest.main(verbosity=2)
