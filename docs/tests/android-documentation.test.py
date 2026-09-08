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
        for name in (*NAMES, REVIEW_NAME, "d02-toolchain-review.md", "p1-03-review.md"):
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
        rows = re.findall(r"^\| (Sol|Astra|Terra) \| (低|中|高) \|", plan, re.M)
        self.assertEqual(len(rows), 7)
        self.assertEqual(set(rows), {
            ("Sol", "中"), ("Sol", "高"),
            ("Astra", "中"), ("Astra", "高"),
            ("Terra", "低"), ("Terra", "中"), ("Terra", "高")
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
        self.assertIn("不进入 P2", handoff)
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
        for role in ("执行窗口 → 分析审查窗口", "分析审查窗口 → 执行窗口"):
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

    def test_platform_branch_policy_is_consistent(self):
        for name in ("AGENTS.md", "docs/Android/handoff.md", "docs/Android/implementation-plan.md"):
            contents = (ROOT / name).read_text()
            for branch in ("`Android`", "`IOS`", "`main`"):
                self.assertIn(branch, contents, name)
        rules = (ROOT / "AGENTS.md").read_text()
        for marker in ("worktree", "不自动合并", "报告本地提交编号", "保留作历史"):
            self.assertIn(marker, rules)
        subprocess.run(
            ["git", "diff", "--exit-code", "bef808b", "8791dbe", "--", "ios"],
            cwd=ROOT, check=True, capture_output=True,
        )

    def test_github_sync_contract_is_consistent(self):
        for name in ("AGENTS.md", "docs/Android/handoff.md", "docs/Android/implementation-plan.md"):
            contents = (ROOT / name).read_text()
            self.assertIn("https://github.com/SukiBanQin/QingKeSchedule", contents)
            self.assertIn("推送", contents)
            self.assertIn("main", contents)
        rules = (ROOT / "AGENTS.md").read_text()
        for marker in ("不强制推送", "upstream", "未推送", "本地 HEAD 一致"):
            self.assertIn(marker, rules)
        self.assertIn("远程同步：", (DOCS / "implementation-plan.md").read_text())

    def test_current_workflow_uses_roles_and_sol_default(self):
        for name in ("AGENTS.md", "docs/Android/product-baseline.md", "docs/Android/handoff.md"):
            contents = (ROOT / name).read_text()
            self.assertIn("分析审查窗口（默认 Sol）", contents, name)
            self.assertNotIn("各一个 Astra 分析窗口", contents, name)
        plan = (DOCS / "implementation-plan.md").read_text()
        for marker in ("不绑定模型名称", "本次改动及相关依赖", "疑难问题", "默认 Sol"):
            self.assertIn(marker, plan)
        self.assertNotIn("复制给 Astra", plan)
        self.assertNotIn("由 Astra 复审时更新", plan)
        for name in ("AGENTS.md", "docs/Android/implementation-plan.md", "docs/Android/handoff.md"):
            contents = (ROOT / name).read_text()
            self.assertIn("执行为主、分析按需、关键点审查", contents, name)
            self.assertIn("P1-03", contents, name)
            self.assertIn("专项复审", contents, name)
        self.assertIn("审查要求：", plan)
        self.assertNotIn("交接记录默认由分析审查窗口维护", plan)
        self.assertIn("不需要转交时直接报告完成", plan)

    def test_d02_scope_sources_and_sdk_evidence_are_traceable(self):
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "55eff97"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(changed, ["docs/Android/d02-toolchain-review.md"])
        review = (DOCS / "d02-toolchain-review.md").read_text()
        for marker in ("2026-09-08", "55eff97^..55eff97", "9.1.1", "9.3.1",
                       "agp-9-4-0-release-notes", "升级可执行性仍未验证"):
            self.assertIn(marker, review)
        evidence = (DOCS / "evidence/d02-sdk-list-20260908.txt").read_text()
        self.assertIn("--channel=0", evidence)
        self.assertIn("退出码：0", evidence)
        for package in ("platforms;android-36", "platforms;android-36.1",
                        "platforms;android-37.0", "platforms;android-37.1",
                        "platforms;android-37.2", "build-tools;36.0.0",
                        "build-tools;36.1.0", "build-tools;37.0.0"):
            self.assertRegex(evidence, re.escape(package) + r"\s+\|")

    def test_supplemental_review_keeps_new_defect_and_prior_startup_distinct(self):
        review = (DOCS / REVIEW_NAME).read_text()
        latest = review.split("## 2026-09-08 补充复核", 1)[1]
        for marker in ("P1-01-R2", "16 个案例", "各 21 项", "优先级 P1",
                       "truncatedMultibyte", "23e0501", "525910f",
                       "不依赖工具链升级或未知字段决定"):
            self.assertIn(marker, latest)
        for name in ("handoff.md", "implementation-plan.md"):
            current = (DOCS / name).read_text()
            self.assertIn("P1-01-R2", current)
            self.assertIn("不进入 P2", current)
        probe = (ROOT / "docs/tests/android-contract-review-probe.py").read_text()
        for case in ("number-leading-plus", "number-leading-zero",
                     "number-trailing-point", "number-leading-point"):
            self.assertIn(case, probe)

    def test_r2_rereview_records_scope_independent_evidence_and_remaining_gate(self):
        review = (DOCS / REVIEW_NAME).read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        latest = review.split("## 2026-09-08 P1-01-R2 复审", 1)[1]
        for marker in (
            "985cdd6^..985cdd6", "P1-01-R2 独立复审通过", "69 个任务",
            "各 20 项", "qingke-r2-before-probe.log", "qingke-r2-after-probe.log",
            "未知字段策略", "不生成 P1-03", "不进入 P2",
        ):
            self.assertIn(marker, latest)
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "985cdd6"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(changed, [
            "Android/app/src/main/java/com/qingke/schedule/transfer/ScheduleDataDecoder.kt",
            "Android/app/src/test/java/com/qingke/schedule/transfer/ScheduleDataDecoderTest.kt",
        ])
        self.assertIn("P1-01-R2 已复审通过", handoff)
        self.assertIn("P1-01（含 R1/R2）审查通过", plan)
        self.assertIn("`Android`", handoff)
        self.assertIn("`c11bd2b`", handoff)

    def test_d02_authorization_defines_p1_03_without_claiming_completion(self):
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        d02 = (DOCS / "d02-toolchain-review.md").read_text()
        review = (DOCS / REVIEW_NAME).read_text()
        for contents in (baseline, design, plan, handoff, d02, review):
            self.assertIn("P1-03", contents)
            self.assertIn("API 37.0", contents)
        authorized = handoff.split("### P1-03 已授权范围", 1)[1]
        for marker in (
            "AGP 9.4.0", "Gradle 9.6.0", "Build Tools 36.0.0", "JDK 17",
            "minSdk` 26", "built-in Kotlin", "Kotlin serialization",
            "org.jetbrains.kotlin.plugin.compose", "离线 clean 构建",
            "16 例跨端契约探针", "targetSdk 37", "API 37 ARM64",
            "lintDebug", "不进入 P2",
        ):
            self.assertIn(marker, authorized)
        self.assertIn("P1-03 工具链和主机侧验证已经复审", review)
        self.assertIn("不表示候选组合已经成功构建", d02)

    def test_p1_03_execution_record_preserves_review_and_device_gate(self):
        handoff = (DOCS / "handoff.md").read_text()
        record = (DOCS / "p1-03-validation.md").read_text()
        evidence = (DOCS / "evidence/p1-03-api37-emulator-error-20260908.txt").read_text()
        readme = (ROOT / "Android/README.md").read_text()
        probe = (ROOT / "docs/tests/android-contract-review-probe.py").read_text()
        for marker in (
            "ec236b9", "274f3b9", "AGP 9.4.0", "Gradle 9.6.0",
            "Build Tools 36.0.0", "built-in Kotlin", "2.2.10",
            "android.onlyEnableUnitTestForTheTestedBuildType=false",
            "离线 clean 构建", "各 20 项 JVM 测试", "16 个输入案例",
            "targetSdk 37", "hvf is not enabled", "mprotect failed",
            "未执行 APK 安装", "独立专项复审", "不进入 P2",
        ):
            self.assertIn(marker, record)
        self.assertIn("P1-03 验证记录", handoff)
        self.assertIn("当前环境限制", handoff)
        self.assertIn("system-images;android-37.0;google_apis;arm64-v8a", evidence)
        self.assertIn("sys.boot_completed", evidence)
        self.assertEqual(missing_links(DOCS / "p1-03-validation.md", record), [])
        self.assertIn("AGP 9.4 release notes", readme)
        self.assertIn("intermediates/built_in_kotlinc", probe)
        self.assertIn("kotlin-stdlib/2.2.10", probe)

    def test_p1_03_special_review_records_scope_evidence_and_correction_gate(self):
        review = (DOCS / "p1-03-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        for marker in (
            "7d34c78b77462178ce2119c2fb21952ce187d18b",
            "工具链改动和主机侧验证通过专项复审",
            "P1-03 整体暂不通过",
            "100 个任务执行",
            "Debug／Release 各 20 项",
            "16 例实际执行并逐项核对",
            "android.onlyEnableUnitTestForTheTestedBuildType",
            "hvf is not enabled",
            "mprotect failed",
            "Android 17 行为变化",
            "P1-03-R1",
            "不进入 P2",
        ):
            self.assertIn(marker, review)
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "7d34c78"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(len(changed), 14)
        self.assertFalse(any(path.startswith("ios/") or path.startswith("web/") for path in changed))
        self.assertFalse(any(path.startswith("Android/app/src/main/") for path in changed))
        for contents in (handoff, plan, baseline, design):
            self.assertIn("P1-03-R1", contents)
            self.assertIn("p1-03-review.md", contents)
        self.assertIn("Terra／中", handoff)
        self.assertIn("API 37 设备", handoff)
        self.assertIn("P1-03 和 P1 均未完成", plan)


if __name__ == "__main__":
    unittest.main(verbosity=2)
