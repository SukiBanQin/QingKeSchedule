#!/usr/bin/env python3
"""Validate the Android handoff contract without installing app dependencies."""

import re
import json
import struct
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
        for name in (
            *NAMES,
            REVIEW_NAME,
            "d02-toolchain-review.md",
            "p1-03-review.md",
            "p1-04-unknown-fields.md",
            "p2-01-persistence-state.md",
            "p2-03-form-drafts.md",
            "p2-04-application-state-composition.md",
            "p2-04-review.md",
            "p3-01-schedule-presentation.md",
        ):
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
        baseline_hashes = re.findall(
            r"`([0-9a-f]{40})`", (DOCS / "product-baseline.md").read_text()
        )
        self.assertEqual(len(baseline_hashes), 1)
        handoff_hashes = re.findall(
            r"`([0-9a-f]{40})`", (DOCS / "handoff.md").read_text()
        )
        self.assertIn(baseline_hashes[0], handoff_hashes)
        for commit_hash in set(handoff_hashes):
            subprocess.run(
                ["git", "cat-file", "-e", commit_hash + "^{commit}"],
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
        self.assertIn("P1 授权范围和审查门槛已完成，并已获用户确认；P2 已获明确授权", handoff)
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
        self.assertIn("P1 授权范围和审查门槛已完成，并已获用户确认；P2 已获明确授权", handoff)
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
            self.assertIn("P2-01", current)
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
        self.assertIn("P1-01-R2", handoff)
        self.assertIn("P1-01 的已授权工程、规则和版本 1 契约基础范围通过", plan)
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
        self.assertIn("P1-03-R4", review)
        self.assertIn("P1-04 均已通过独立复审", review)
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
        self.assertIn("p1-03-validation.md", handoff)
        self.assertIn("P1-03 授权范围完成", handoff)
        self.assertIn("system-images;android-37.0;google_apis;arm64-v8a", evidence)
        self.assertIn("sys.boot_completed", evidence)
        self.assertEqual(missing_links(DOCS / "p1-03-validation.md", record), [])
        self.assertIn("AGP 9.4 release notes", readme)
        self.assertIn("intermediates/built_in_kotlinc", probe)
        self.assertIn("kotlin-stdlib/2.2.10", probe)

    def test_p1_03_r1_records_target_behavior_and_unmet_device_gate(self):
        handoff = (DOCS / "handoff.md").read_text()
        validation = (DOCS / "p1-03-validation.md").read_text()
        readme = (ROOT / "Android/README.md").read_text()
        evidence = (DOCS / "evidence/p1-03-r1-api37-host-attempt-20260908.txt").read_text()
        for marker in (
            "P1-03-R1", "全部应用行为变化", "目标 Android 17 的行为变化",
            "sys.boot_completed", "未安装 APK", "两次冷启动", "不进入 P2",
            "targetSdk 37", "大屏方向", "RemoteViews", "局域网权限",
        ):
            self.assertIn(marker, validation)
        for marker in ("ro.build.version.sdk 返回 37", "arm64-v8a", "不再列出 emulator-5556"):
            self.assertIn(marker, evidence)
        self.assertIn("Android 17（targetSdk 37）行为适用性", readme)
        self.assertIn("sys.boot_completed", readme)
        self.assertIn("P1-03-R4", handoff)
        self.assertIn("设备运行门槛均已通过专项复审", handoff)

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
            self.assertIn("P1-03", contents)
            self.assertIn("API 37", contents)
        self.assertIn("Terra／中", handoff)
        self.assertIn("API 37 设备", handoff)
        self.assertIn("P1-03 授权范围完成", plan)
        self.assertIn("P2 已获授权", plan)

    def test_p1_03_r1_review_preserves_dependency_and_device_gates(self):
        review = (DOCS / "p1-03-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        latest = review.split("## 2026-09-08 P1-03-R1 独立复审", 1)[1]
        for marker in (
            "3ab9d4d^..3ab9d4d", "5 个", "设备失败的记录通过复审",
            "当前源码范围仅为 MainActivity", "Compose／AndroidX",
            "libandroidx.graphics.path.so", "第三方库已经存在",
            "30 秒内 ADB 持续 `offline`", "P1-03-R2", "不进入 P2",
        ):
            self.assertIn(marker, latest)
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "3ab9d4d"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(changed, [
            "Android/README.md",
            "docs/Android/evidence/p1-03-r1-api37-host-attempt-20260908.txt",
            "docs/Android/handoff.md",
            "docs/Android/p1-03-validation.md",
            "docs/tests/android-documentation.test.py",
        ])
        for contents in (handoff, plan):
            self.assertIn("P1-03", contents)
            self.assertIn("API 37", contents)
            self.assertIn("P2 已获授权", contents)

    def test_p1_03_r2_preserves_project_and_dependency_boundaries(self):
        handoff = (DOCS / "handoff.md").read_text()
        validation = (DOCS / "p1-03-validation.md").read_text()
        readme = (ROOT / "Android/README.md").read_text()
        evidence = (DOCS / "evidence/p1-03-r2-api37-host-attempt-20260908.txt").read_text()
        for contents in (readme, validation):
            for marker in (
                "领域模型", "JSON 解码源码", "项目自编写", "Compose／AndroidX",
                "lib/*/libandroidx.graphics.path.so", "MessageQueue", "static final",
                "依赖层", "API 37 安装启动", "应用内存限制",
            ):
                self.assertIn(marker, contents)
            self.assertNotIn("唯一直接相关", contents)
        for marker in ("emulator-5558", "只尝试了一次", "sys.boot_completed", "未安装 Debug APK"):
            self.assertIn(marker, evidence)
        self.assertIn("P1-03-R4", handoff)
        self.assertIn("设备运行门槛均已通过专项复审", handoff)

    def test_p1_03_r2_review_accepts_boundaries_and_keeps_device_gate(self):
        review = (DOCS / "p1-03-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        evidence = (DOCS / "evidence/p1-03-r2-review-api37-attempt-20260908.txt").read_text()
        latest = review.split("## 2026-09-08 P1-03-R2 独立专项复审", 1)[1]
        for marker in (
            "2e5b8ec^..2e5b8ec", "R2 的文档修正范围通过专项复审",
            "用户提供的 09:36 桌面截图", "libandroid-emu-tracing.dylib",
            "连续 8 次", "本轮未重复", "不写成本轮新执行",
            "P1-03 整体仍不通过", "P1-03-R3", "不进入 P2",
        ):
            self.assertIn(marker, latest)
        for marker in (
            "官方 emulator 启动器", "-no-snapshot -no-window", "-gpu software",
            "hvf is not enabled", "mprotect failed", "不能推出应用通过或应用缺陷",
        ):
            self.assertIn(marker, evidence)
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "2e5b8ec"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(changed, [
            "Android/README.md",
            "docs/Android/evidence/p1-03-r2-api37-host-attempt-20260908.txt",
            "docs/Android/handoff.md",
            "docs/Android/p1-03-validation.md",
            "docs/tests/android-documentation.test.py",
        ])
        for contents in (handoff, plan, baseline, design):
            self.assertIn("P1-03", contents)
            self.assertIn("API 37", contents)
        self.assertIn("P1-03 授权范围完成", handoff)
        self.assertIn("不进入 P2", handoff)

    def test_p1_03_r3_records_persistent_sdk_without_claiming_device_success(self):
        handoff = (DOCS / "handoff.md").read_text()
        validation = (DOCS / "p1-03-validation.md").read_text()
        evidence = (DOCS / "evidence/p1-03-r3-api37-persistent-sdk-attempt-20260908.txt").read_text()
        for contents in (validation, evidence):
            self.assertIn("P1-03-R3", contents)
            self.assertIn("sys.boot_completed", contents)
            self.assertIn("不进入 P2", contents)
        self.assertIn("P1-03-R4", handoff)
        self.assertIn("API 37 模拟器环境阻塞已解除", handoff)
        for marker in (
            "sdk-qingke-api37", "Emulator：37.1.11.0", "revision 6",
            "-no-snapshot -no-window -gpu software", "mprotect failed",
            "未安装", "另一可用宿主",
        ):
            self.assertIn(marker, evidence)

    def test_p1_03_r3_review_keeps_device_gate_blocked(self):
        review = (DOCS / "p1-03-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        for marker in (
            "P1-03-R3 的范围和失败证据记录通过专项复审",
            "API 37 设备启动门槛仍未通过",
            "23743be",
            "origin/Android",
            "sys.boot_completed=1",
            "API 37 ARM64 真机或另一台可正常完成 API 37 开机的宿主",
        ):
            self.assertIn(marker, review)
        self.assertIn("R3 环境失败记录 `23743be`", handoff)
        self.assertIn("不进入 P2", handoff)


    def test_r4_device_evidence_is_consistent_and_reviewed(self):
        evidence = DOCS / "evidence/p1-03-r4-target-fix"
        result = json.loads((evidence / "result.json").read_text())
        commands = (evidence / "device-validation.txt").read_text()
        review_evidence = (DOCS / "evidence/p1-03-r4-review-20260908.txt").read_text()
        review = (DOCS / "p1-03-review.md").read_text()
        self.assertEqual(result["sdk"], 37)
        self.assertEqual(result["abi"], "arm64-v8a")
        self.assertEqual(result["boot_completed"], "1")
        self.assertEqual(commands.count("LaunchState: COLD"), 2)
        self.assertEqual(commands.count("Status: ok"), 2)
        self.assertIn(result["apk_sha256"], commands)
        self.assertIn("targetSdk 37", commands)
        for n, pid in enumerate(result["cold_start_pids"], 1):
            activity = (evidence / f"activity-{n}.txt").read_text()
            self.assertIn("topResumedActivity=", activity)
            self.assertIn("com.qingke.schedule/.MainActivity", activity)
            self.assertIn("visible=true", activity)
            self.assertIn(str(pid) + ":com.qingke.schedule", activity)
            png = (evidence / f"cold-start-{n}.png").read_bytes()
            self.assertEqual(png[:8], b"\x89PNG\r\n\x1a\n")
            self.assertEqual(struct.unpack(">II", png[16:24]), (1080, 1920))
        log = (evidence / "logcat.txt").read_text()
        self.assertIn("com.qingke.schedule", log)
        self.assertIsNone(re.search(r"FATAL EXCEPTION|ANR in|am_anr\s*:|am_crash\s*:|Fatal signal", log))
        comparison = (evidence / "startup-comparison.txt").read_text()
        self.assertIn("API level: 3 ", comparison)
        self.assertIn("API level: 37 ", comparison)
        self.assertIn("-enable-hvf", comparison)
        for marker in (
            "target=android-0", "target=android-37", "API level: 3", "API level: 37",
            "-enable-hvf", "emulator-5588", "pidof 返回 3155", "70 个任务实际执行",
            "Debug 与 Release JVM 报告各 20 项", "不进入 P2",
        ):
            self.assertIn(marker, review_evidence)
        latest = review.split("## 2026-09-08 P1-03-R4 独立专项复审", 1)[1]
        for marker in (
            "729fbaa^..729fbaa", "P1-03-R4 通过独立专项复审",
            "P1-03 的工具链升级与 API 37 设备运行门槛完成",
            "P1 阶段和用户验收不随本次专项审查自动完成",
        ):
            self.assertIn(marker, latest)
        changed = subprocess.check_output(
            ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", "729fbaa"],
            cwd=ROOT, text=True,
        ).splitlines()
        self.assertEqual(len(changed), 15)
        self.assertFalse(any(path.startswith("Android/app/") for path in changed))
        self.assertFalse(any(path.startswith("ios/") or path.startswith("web/") for path in changed))
        for name in ("handoff.md", "p1-03-validation.md", "p1-03-review.md", "implementation-plan.md"):
            contents = (DOCS / name).read_text()
            if name == "handoff.md":
                current = contents.split("## P1-03-R4 最新专项复审状态", 1)[1].split("\n## ", 1)[0]
            else:
                current = "\n".join(contents.splitlines()[:35])
            self.assertIn("独立专项复审", current)
            self.assertIn("P1-03 授权范围完成", current)
            self.assertTrue("不进入 P2" in current or "P2 已获授权" in current)
            self.assertNotIn("P1-03 待独立复审", current)

    def test_p1_04_records_strict_unknown_field_decision_and_scope(self):
        task = (DOCS / "p1-04-unknown-fields.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        normalized_task = re.sub(r"\s+", " ", task)

        for marker in (
            "用户确认版本 1 课表 JSON 采用严格未知字段策略",
            "additionalProperties: false",
            "ScheduleDataTransfer.previewImport",
            "ScheduleDataTransferError.malformedFile",
            "unsupportedSchemaVersion",
            "顶层以及学期、节次、",
            "课程和课程安排对象",
            "不进入 P2",
        ):
            self.assertIn(marker, normalized_task)
        for field in (
            "schemaVersion", "totalWeeks", "periods", "schedules",
            "startPeriod", "endWeek", "classroom",
        ):
            self.assertIn(field, task)

        current = handoff.split(
            "## P1-04 未知字段复审状态", 1
        )[1].split("\n## ", 1)[0]
        self.assertIn("P1-04 已从基准", current)
        self.assertIn("实施完成", current)
        self.assertIn("独立专项复审通过", current)
        self.assertNotIn("待独立复审", current)
        self.assertIn("两端严格拒绝", handoff)
        for contents in (plan, baseline, design):
            self.assertIn("P1-04", contents)
            self.assertIn("严格拒绝", contents)
            self.assertIn("P1-04", contents)

        decoder = (
            ROOT / "Android/app/src/main/java/com/qingke/schedule/transfer/ScheduleDataDecoder.kt"
        ).read_text()
        schema = (ROOT / "ios/Shared/schedule-data.schema.json").read_text()
        self.assertIn("ignoreUnknownKeys = false", decoder)
        self.assertGreaterEqual(schema.count('"additionalProperties": false'), 5)

    def test_p1_04_execution_records_cross_platform_evidence_and_review_gate(self):
        task = (DOCS / "p1-04-unknown-fields.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        transfer = (ROOT / "ios/QingKeSchedule/Transfer/ScheduleDataTransfer.swift").read_text()
        tests = (ROOT / "ios/QingKeScheduleTests/ScheduleDataTransferTests.swift").read_text()
        probe = (ROOT / "docs/tests/android-contract-review-probe.py").read_text()
        for marker in (
            "70 个任务实际执行", "19 例", "93 项通过", "失败/跳过均为 0",
            "独立专项复审", "不进入 P2",
        ):
            self.assertIn(marker, task + handoff)
        self.assertLess(
            transfer.index("version == ScheduleDataDTO.supportedSchemaVersion"),
            transfer.index("hasOnlyVersion1Fields"),
        )
        for name in (
            "rejectsUnknownTopLevelField", "rejectsUnknownSemesterField",
            "rejectsUnknownPeriodField", "rejectsUnknownCourseField",
            "rejectsUnknownCourseScheduleField", "acceptsNullSemesterAndCompleteInput",
            "unsupportedVersionKeepsErrorPriority",
        ):
            self.assertIn(name, tests)
        for case in ("period-unknown", "course-unknown", "course-schedule-unknown"):
            self.assertIn(case, probe)


    def test_analysis_role_handoff_records_safe_p2_01_boundary(self):
        handoff = (DOCS / "handoff.md").read_text()
        current = handoff.split(
            "## 分析审查窗口切换状态（最新，2026-09-08）", 1
        )[1].split("\n## ", 1)[0]
        normalized_current = re.sub(r"\s+", " ", current)
        for marker in (
            "d4f7f6478c2e007dea5e3d60774a5e3e2525c535",
            "81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587",
            "当前分支 `Android`",
            "P1 已完成独立审查并获用户确认",
            "P2 已获授权",
            "P2 尚无应用实施提交",
            "P2-01",
            "不授权分析窗口修改应用代码",
            "没有启动子 Agent",
            "没有发现连接中的 Android 设备",
            "没有发现正在运行的 Gradle",
            "D01、D03 和正式发行范围仍未决定",
        ):
            self.assertIn(marker, normalized_current)
        self.assertIn("不提前实施页面、导入导出、通知或发布能力", normalized_current)
        subprocess.run(
            ["git", "cat-file", "-e", "d4f7f64^{commit}"],
            cwd=ROOT,
            check=True,
            capture_output=True,
        )

    def test_p2_01_analysis_defines_atomic_storage_and_state_contract(self):
        task = (DOCS / "p2-01-persistence-state.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        normalized = re.sub(r"\s+", " ", task)

        for marker in (
            "cba042bc68cb6254c9410fc9f84c6767bc6715e9",
            "81ae16f7f4ddc9acd51c67ffb8f66482c6d3d587",
            "P2-01 尚未实现、测试或审查",
            "`androidx.room` 2.8.4",
            "KSP 2.3.11",
            "ScheduleRepository",
            "已经提交的完整 `ScheduleData` 快照",
            "返回即已提交，抛错即事务未提交",
            "replace",
            "saveSemester",
            "saveCourse",
            "deleteCourse",
            "NotLoaded",
            "Loading",
            "Ready",
            "Failed",
            "StateFlow",
            "API 37 Room 集成测试",
            "connectedDebugAndroidTest",
            "必须由分析审查窗口进行独立审查",
        ):
            self.assertIn(marker, normalized)

        for boundary in (
            "不得通过 DTO ID 唯一索引",
            "删除也只 删除首个匹配项",
            "状态层不得在成功写入后再额外 `load`",
            "不得 自动返回默认课表",
            "不实现 DataStore",
            "不修改 `MainActivity` 的页面内容",
            "不修改 iOS、Web、共享 schema／fixtures",
            "不自动开始其余 P2 子任务、P3 或后续阶段",
        ):
            self.assertIn(boundary, normalized)

        current = handoff.split(
            "## P2-01 分析完成与执行边界（最新，2026-09-08）", 1
        )[1].split("\n## ", 1)[0]
        for marker in (
            "P2-01 尚未实现、测试或审查",
            "Room 2.8.4 + KSP 2.3.11",
            "没有修改应用代码",
            "没有启动子 Agent",
            "不得自动进入其余 P2 或后续阶段",
        ):
            self.assertIn(marker, re.sub(r"\s+", " ", current))

        self.assertTrue(
            "P2-01 已分析、待执行" in plan
            or "P2-01 已实施但独立复审未通过" in plan
            or "P2-01 R1 代码复审通过，设备验证未通过" in plan
            or "P2-01 R2-R1 通过，整体复审未通过" in plan
            or "P2-01 独立复审通过" in plan
            or "P2-01 已审查通过" in plan
        )
        self.assertIn("p2-01-persistence-state.md", plan)
        self.assertIn("p2-01-persistence-state.md", design)

    def test_p2_01_review_keeps_cancellation_and_device_gates_open(self):
        review = (DOCS / "p2-01-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        for marker in (
            "独立复审未通过",
            "CancellationException",
            "catch (Throwable)",
            "connectedDebugAndroidTest",
            "hvf is not enabled on this aarch64 host",
            "qemu_mprotect__osdep: mprotect failed: Permission denied",
            "不得进入",
        ):
            self.assertIn(marker, review)
        self.assertIn("P2-01 独立复审未通过，等待修正", handoff)
        self.assertIn("当前不得标记 P2-01 已审查通过", handoff)

    def test_p2_01_r1_review_closes_code_issue_but_keeps_device_gate(self):
        review = (DOCS / "p2-01-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        latest_review = review.split(
            "## P2-01-R1 重新独立复审结论（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_review = re.sub(r"\s+", " ", latest_review)
        for marker in (
            "932f4b5367c641e3d1abc5a5ba1f7286283b2613",
            "c96658a3a5c8943a820893ff8c46d1079185a0ea",
            "协程取消修正通过代码复审",
            "Debug／Release JVM 各 28 项",
            "0 failures、0 errors、0 skipped",
            "0 errors",
            "9 个 warnings",
            "DeviceException: No connected devices!",
            "实际 Room 测试数仍为 0",
            "P2-01 整体仍未达到“已验证／已审查通过”",
            "没有新的应用代码修正任务",
            "不进入 P2-02、P3",
        ):
            self.assertIn(marker, normalized_review)

        latest_handoff = handoff.split(
            "## P2-01-R1 代码复审通过，设备门槛仍开放（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_handoff = re.sub(r"\s+", " ", latest_handoff)
        for marker in (
            "P2-01-R1 协程取消修正通过代码复审",
            "P2-01 整体仍未验证、未审查通过",
            "实际 Room 集成测试数为 0",
            "没有修改应用代码",
            "没有启动子 Agent",
            "不得进入 P2-02",
        ):
            self.assertIn(marker, normalized_handoff)

    def test_p2_01_r2_records_working_emulator_and_test_entry_failure(self):
        review = (DOCS / "p2-01-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        evidence_path = DOCS / "evidence/p2-01-r2-connected-debug-android-test-20260909.txt"
        evidence = evidence_path.read_text()

        latest_review = review.split(
            "## P2-01-R2 设备环境已恢复，测试入口缺陷待修正（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_review = re.sub(r"\s+", " ", latest_review)
        for marker in (
            "08aaab1b2e4550f05fcdaf6180400ffdd61736fc",
            "ANDROID_AVD_HOME=/Users/takagisan/.android/qingke-api37-r3-avd",
            "sys.boot_completed=1",
            "SDK 37",
            "arm64-v8a",
            "InvalidTestClassError",
            "initializationError",
            "四个 Room 测试方法均未进入测试体",
            "P2-01 整体仍未验证、未审查通过",
            "不得进入 P2-02、P3",
            "文档验证 32 项",
        ):
            self.assertIn(marker, normalized_review)

        latest_handoff = handoff.split(
            "## P2-01-R2 模拟器已可用，Android 测试入口待修正（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_handoff = re.sub(r"\s+", " ", latest_handoff)
        for marker in (
            "无需实体真机",
            "target=android-0",
            "四个 Room 测试均未执行",
            "不修改生产代码",
            "没有修改 Android 应用或测试源码",
            "没有启动子 Agent",
            "文档验证 32 项",
        ):
            self.assertIn(marker, normalized_handoff)

        for marker in (
            "Emulator：37.1.11.0",
            "adb get-state：device",
            "ro.build.version.sdk：37",
            "tests=1，failures=1，errors=0，skipped=0",
            "should be void",
            "public final boolean cancellationPropagatesFromReadAndRollsBackWrite();",
            "四个 Room 功能用例均未进入测试体",
            "ADB 不再列出 emulator-5588",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])
        self.assertIn("P2-01-R1 代码复审通过", handoff)
        self.assertIn("三个表达式 `@Test`", handoff)

    def test_p2_01_r2_r1_review_accepts_fix_but_keeps_contract_gate(self):
        review = (DOCS / "p2-01-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        evidence_path = DOCS / "evidence/p2-01-r2-r1-review-20260909.txt"
        evidence = evidence_path.read_text()

        latest_review = review.split(
            "## P2-01-R2-R1 修正通过，P2-01 最终复审仍未通过（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_review = re.sub(r"\s+", " ", latest_review)
        for marker in (
            "886bca62061081a71144e7dcb4cccddf967554d7",
            "P2-01-R2-R1 的测试入口与故障注入修正通过独立复审",
            "BUILD SUCCESSFUL in 19s",
            "106 executed",
            "Debug／Release JVM 各 28 项",
            "API 37 ARM64 Room 测试 4 项",
            "现有 API 37 Room 类实际只有 4 个测试方法",
            "InvalidData",
            "InconsistentStore",
            "P2-01 整体最终独立复审仍未通过",
            "P2-01-R3",
            "不得进入 P2-02、P3",
            "文档验证 33 项",
        ):
            self.assertIn(marker, normalized_review)

        latest_handoff = handoff.split(
            "## P2-01-R2-R1 复审通过，P2-01-R3 待修正（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized_handoff = re.sub(r"\s+", " ", latest_handoff)
        for marker in (
            "没有超出 R2-R1 授权",
            "四个 `@Test` 的 JVM 签名均为 `void`",
            "Room 设备测试 4 项",
            "P2-01-R2-R1 聚焦修正通过独立复审",
            "P2-01 整体最终复审未通过",
            "不得宣称 P2-01、A09、P2 或完整 App",
            "没有修改应用代码",
            "没有启动子 Agent",
            "文档验证 33 项",
        ):
            self.assertIn(marker, normalized_handoff)

        for marker in (
            "109 actionable tasks：106 executed，3 up-to-date",
            "API 37 Room：4 tests，0 failures，0 errors，0 skipped",
            "多安排顺序与重复安排 ID",
            "外键级联本身",
            "当前错误类型是 InvalidData",
            "契约要求识别为 InconsistentStore",
            "android-documentation.test.py：33 项通过",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])
        self.assertTrue(
            "P2-01 R2-R1 通过，整体复审未通过" in plan
            or "P2-01 独立复审通过" in plan
            or "P2-01 已审查通过" in plan
        )
        self.assertTrue(
            "存储损坏错误分类" in plan
            or "P2-01 Room 与最小状态边界已完成" in plan
            or "损坏回退" in plan
            or "P2-01 已审查通过" in plan
            or "P2 阶段结果已获用户确认" in plan
        )

    def test_p2_01_r3_review_closes_gate_without_claiming_user_acceptance(self):
        review = (DOCS / "p2-01-review.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        latest = review.split(
            "## P2-01-R3 通过，P2-01 独立复审关闭（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "fc1c7f716df32a528317f13455b2c9c0e2f077e3",
            "f79ac57",
            "85098ac8140109eb6782ad129375dfff00e4362c",
            "实际复审范围为 `fc1c7f7..85098ac`",
            "P2-01 独立复审通过",
            "11 项 Room 测试",
            "145 actionable tasks",
            "未能再次启动同一 AVD",
            "不代表 A09、P2、完整 App 或用户验收完成",
            "不得因本结论自动实施 P2-02 或 P3",
        ):
            self.assertIn(marker, normalized)
        self.assertIn("P2-01-R3 独立复审通过（最新，2026-09-09）", handoff)
        self.assertIn("P2-01 独立复审通过", handoff)
        self.assertTrue(
            "P2-01 独立复审通过" in plan
            or "P2-01 已审查通过" in plan
        )

    def test_p2_02_authorization_and_execution_define_datastore_only_boundary(self):
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        latest = handoff.split(
            "## P2-02 偏好设置持久化已获授权，待执行（最新，2026-09-09）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "P2-02",
            "DataStore",
            "默认值／未知值回退",
            "不连接 Compose 页面",
            "通知调度",
            "不决定 D03",
            "不扩展版本 1 JSON 或 iOS 协议",
            "跨存储原子事务",
            "Terra／中",
            "独立复审",
        ):
            self.assertIn(marker, normalized)
        self.assertTrue(
            "P2-02 已实施待独立复审" in plan
            or "P2-02-R1 已审查" in plan
        )
        for marker in (
            "P2-02 已采用",
            "DataStore",
            "AppearanceMode",
            "教学日历",
            "未知枚举值时",
            "关闭并重建 DataStore",
            "不接页面、`ScheduleAppState` 或通知调度",
        ):
            self.assertIn(marker, design)

    def test_p2_02_execution_records_real_datastore_device_evidence(self):
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        evidence_path = DOCS / "evidence/p2-02-datastore-connected-debug-android-test-20260910.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P2-02 偏好设置持久化已实施，等待独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "63b019a24b91021aad83e7528fcadbaa1fcca554",
            "datastore-preferences:1.2.1",
            "18 tests",
            "DataStore 7",
            "SDK 37",
            "arm64-v8a",
            "P2-02 实施尚未独立审查",
            "不得据此自动进入 P2-03 或 P3",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "18 tests",
            "DataStore 7",
            "0 failures",
            "0 errors",
            "0 skipped",
            "emulator-5588",
            "SDK 37",
            "arm64-v8a",
        ):
            self.assertIn(marker, evidence)
        self.assertTrue(
            "P2-02 已实施待独立复审" in plan
            or "P2-02-R1 已审查" in plan
        )
        self.assertIn("DataStore 偏好边界已在 P2-02", design)

    def test_p2_02_r1_records_reminder_semantics_and_final_device_execution(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p2-02-r1-connected-debug-android-test-20260910.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P2-02-R1 提醒规范化修正已实施，等待独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "8ae63295ca16cb22f5e233ef27f5e17d943991f3",
            "remindersEnabled",
            "0—180",
            "P2-02-R1 实施，尚未独立审查",
            "不得自动进入 P2-03 或 P3",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "19 tests",
            "RoomScheduleRepositoryTest：11 tests",
            "DataStoreSchedulePreferencesRepositoryTest：8 tests",
            "0 failures",
            "0 errors",
            "0 skipped",
            "emulator-5584",
            "ro.build.version.sdk：37",
            "arm64-v8a",
            "publicInvalidReminderLeadSaveAndUpdateKeepEnabledAfterReopen",
        ):
            self.assertIn(marker, evidence)

    def test_p2_03_execution_records_pure_kotlin_scope_and_jvm_evidence(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence = (DOCS / "evidence/p2-03-form-drafts-jvm-20260910.txt").read_text()
        latest = handoff.split(
            "## P2-03 课程／学期表单草稿与保存评估已实施，等待独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "ee2decac5849f7047f40d8b8638586b9d810ef83",
            "纯 Kotlin",
            "ScheduleRules",
            "不代表 A04、A05、A06、P2 或完整 App 完成",
            "不得自动进入 P3",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "41 tests",
            "0 failures",
            "0 errors",
            "0 skipped",
            "assembleDebugAndroidTest：成功",
            "不单独运行 connectedDebugAndroidTest",
        ):
            self.assertIn(marker, evidence)

    def test_p2_03_final_review_and_user_confirmation_keep_product_gates_open(self):
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        analysis = (DOCS / "p2-03-form-drafts.md").read_text()
        evidence = (DOCS / "evidence/p2-03-final-review-20260910.txt").read_text()
        latest = handoff.split(
            "## P2-03 通过最终独立复审并获用户确认（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "ee2decac5849f7047f40d8b8638586b9d810ef83..5c5c08a5772b1c3792406ee2fc5aa6d0eefff9b8",
            "P2-03 已实现、已测试、已独立复审并获用户确认",
            "不等于 A04、A05、A06",
            "不等于 P2 或完整 App 完成",
            "不得自动进入 P3",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "01b2965151aa8f3bf89748ae3abcdcd88e06374b",
            "37b53e6d870748cfecf826852133af6143558195",
            "BUILD SUCCESSFUL in 59s",
            "48 tests、0 failures、0 errors、0 skipped",
            "lintDebug 为 0 errors、11 warnings",
            "用户随后明确确认 P2-03 本子任务结果",
            "不自动授权 P3",
        ):
            self.assertIn(marker, evidence)
        self.assertIn("P2-03", plan)
        self.assertIn("用户接受 P2-04 同窗口限制并确认 P2", plan)
        self.assertIn("P2-03", baseline)
        self.assertIn("P2-03 已通过最终独立复审", design)
        self.assertIn("最终独立复审通过，用户已确认 P2-03 本子任务结果", analysis)

    def test_p2_04_analysis_defines_joint_state_and_production_composition_boundary(self):
        analysis = (DOCS / "p2-04-application-state-composition.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        normalized = re.sub(r"\s+", " ", analysis)
        for marker in (
            "9f7bf013a64c42ddaf7966987542728ce6f91df1",
            "fc3ddfb8ffa14b205a591ffdbed5632d5f975001",
            "时间刷新属于 P3 展示／生命周期范围",
            "ScheduleRepository",
            "SchedulePreferencesRepository",
            "SchedulePreferences.defaults",
            "一次性发布完整 `READY` 快照",
            "不得先发布另一个仓库的新值",
            "CancellationException",
            "恢复操作前的完整状态并原样重新抛出",
            "savePreferences",
            "updatePreferences",
            "不再额外 `load`",
            "不是 Room 与 DataStore 的跨存储事务",
            "applicationContext",
            "Application 级容器",
            "多次取得容器必须返回同一实例",
            "Application 本身不 启动加载协程",
            "Activity 级 ViewModel",
            "不修改 `MainActivity`",
            "Room 11 项和 DataStore 8 项",
            "API 37 ARM64",
            "P2 整体验收仍需用户确认",
            "不 自动授权 P3",
        ):
            self.assertIn(marker, normalized)
        latest = handoff.split(
            "## P2-04 应用状态与生产依赖装配分析完成，等待实施授权（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized_handoff = re.sub(r"\s+", " ", latest)
        for marker in (
            "P2-04“应用状态与生产依赖装配”",
            "没有修改任何应用代码",
            "不修改 `MainActivity`",
            "Sol／高",
            "实施尚未获单独授权",
            "不得自动进入 P3",
        ):
            self.assertIn(marker, normalized_handoff)
        self.assertTrue(
            "P2-04 已分析待授权" in plan
            or "P2-04 已实施待独立复审" in plan
            or "P2-04 已通过同窗口复审" in plan
        )
        self.assertIn("P2-04 应用状态与生产依赖装配", baseline)
        self.assertIn("P2-04 负责把两个已审查仓库连接到一个可观察应用状态", design)

    def test_p2_04_execution_records_joint_state_and_real_device_composition(self):
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        analysis = (DOCS / "p2-04-application-state-composition.md").read_text()
        evidence_path = DOCS / "evidence/p2-04-application-state-connected-debug-android-test-20260910.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P2-04 应用状态与生产依赖装配已实施，等待独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "205831819ff1343b5f736ea011e6817f9b7e5b55",
            "两个读取均成功后才一次发布 `READY`",
            "取消恢复完整前态并传播",
            "懒加载进程单例 `ScheduleAppDependencies`",
            "schedule.db",
            "Debug／Release JVM 各 55 项",
            "`ScheduleAppStateTest` 各 21 项",
            "实际运行 21 项（装配 2、Room 11、DataStore 8）",
            "0 failures、0 errors、0 skipped",
            "未修改 `MainActivity`",
            "P2-04 尚未独立审查或用户验收",
            "不自动实施 P3",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "BUILD SUCCESSFUL in 45s",
            "145 actionable tasks，142 executed、3 up-to-date",
            "Debug JVM：55 tests",
            "Release JVM：55 tests",
            "lintDebug：0 errors、11 warnings",
            "emulator-5584",
            "ro.build.version.sdk：37",
            "arm64-v8a",
            "总计：21 tests，0 failures，0 errors，0 skipped",
            "ScheduleAppDependenciesTest：2 tests",
            "RoomScheduleRepositoryTest：11 tests",
            "DataStoreSchedulePreferencesRepositoryTest：8 tests",
            "manifestApplicationLazilyProvidesOneProductionDependencyContainer",
            "realRoomAndDataStoreCompositionRestoresJointStateAfterReopen",
            "两个 @Test 方法的 JVM 签名均为 public final void",
            "SDK location not found",
            "adb devices -l 为空",
            "仍须独立复审",
            "不自动授权 P3",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])
        self.assertIn("用户接受 P2-04 同窗口限制并确认 P2", plan)
        self.assertIn("P2-04 应用状态与生产依赖装配", baseline)
        self.assertIn("当前分析角色同窗口复审", design)
        self.assertIn("当前分析角色完成同窗口复审", analysis)

    def test_p2_04_same_window_review_records_findings_evidence_and_limit(self):
        review = (DOCS / "p2-04-review.md").read_text()
        evidence = (DOCS / "evidence/p2-04-same-window-review-20260910.txt").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        for marker in (
            "205831819ff1343b5f736ea011e6817f9b7e5b55",
            "e6a3513d720fe39f3b3f10aca1acc55208d9d820",
            "同窗口复审",
            "没有发现阻断问题",
            "另一窗口或另一审查者",
            "Debug／Release JVM 各 55 项",
            "21 tests、0 failures、0 errors、0 skipped",
            "ScheduleAppDependenciesTest",
            "public final void",
            "P2 整体的验收",
            "不自动授权 P3",
        ):
            self.assertIn(marker, review)
        for marker in (
            "BUILD SUCCESSFUL in 42s",
            "145 actionable tasks，141 executed、4 up-to-date",
            "lintDebug：0 errors、13 warnings",
            "emulator-5584",
            "ro.build.version.sdk：37",
            "ro.product.cpu.abi：arm64-v8a",
            "总计：21 tests，0 failures，0 errors，0 skipped",
            "adb devices -l：为空",
            "组织性独立审查限制保留",
        ):
            self.assertIn(marker, evidence)
        self.assertIn("P2-04 通过当前分析角色复审，组织性独立限制保留", handoff)
        self.assertIn("用户接受 P2-04 同窗口限制并确认 P2", plan)

    def test_p3_01_analysis_defines_pure_presentation_boundary_after_p2_confirmation(self):
        analysis = (DOCS / "p3-01-schedule-presentation.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        normalized = re.sub(r"\s+", " ", analysis)
        for marker in (
            "322227ef29bf1eab08ec74ecf0eb66b19c79b77c",
            "fc3ddfb8ffa14b205a591ffdbed5632d5f975001",
            "用户已接受 P2-04 同窗口复审的限制并确认 P2 阶段结果",
            "Terra／高",
            "LocalDateTime",
            "内部展示键不得假设 `course.id` 或 `schedule.id` 唯一",
            "指定停课日 → 指定调课日 → “周末默认停课”",
            "46.0 / 110.0",
            "zh_CN",
            "displayDayOfWeek",
            "一组通过 重叠链连接的课程使用相同 `laneCount`",
            "MON–SUN / N PERIODS",
            "不修改 `MainActivity`",
            "不要求 `connectedDebugAndroidTest`",
            "必须由分析窗口独立复审",
            "不等于 A02、A03、A07、P3 或完整 App 验收",
        ):
            self.assertIn(marker, normalized)
        latest = handoff.split(
            "## 用户确认 P2，P3-01 展示模型分析完成待 Terra 实施（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized_handoff = re.sub(r"\s+", " ", latest)
        for marker in (
            "用户已明确接受 P2-04 当前分析角色同窗口复审",
            "确认 P2 阶段结果",
            "没有授权一次性实施完整 P3",
            "P3-01“今日与周课表展示模型”",
            "Terra／高",
            "没有修改应用代码",
            "不自动开始后续页面任务",
        ):
            self.assertIn(marker, normalized_handoff)
        self.assertIn("P2 阶段结果已获用户确认", plan)
        self.assertIn("P3-01 展示模型已分析待实施", plan)
        self.assertIn("P3-01 今日与周课表展示模型", baseline)
        self.assertIn("P3-01", design)

    def test_p3_01_execution_records_pure_kotlin_fixture_and_review_boundary(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence = (DOCS / "evidence/p3-01-schedule-presentation-jvm-20260910.txt").read_text()
        latest = handoff.split(
            "## P3-01 今日与周课表展示模型已实施，等待独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "93e1005263871c25a87cd8d8dae4a8f822dbb52b",
            "CourseOccurrence",
            "OccurrenceKey",
            "停课→调课→周末→正常星期",
            "P3-02",
            "61 tests",
            "不代表 P3-01、A02、A03、A07、 P3、完整 App 或用户验收完成",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "complete-schedule.json",
            "schedule-every、schedule-odd、schedule-alpha、schedule-beta",
            "46、64、46.0/110.0",
            "BUILD SUCCESSFUL in 53s",
            "145 actionable tasks",
            "testDebugUnitTest：tests=61",
            "testReleaseUnitTest：tests=61",
            "0 errors，11 warnings",
            "connectedDebugAndroidTest",
            "P3-01、A02、A03、A07、P3、完整 App 和用户验收均未完成",
        ):
            self.assertIn(marker, evidence)

    def test_p3_01_review_records_real_coverage_gaps_without_claiming_completion(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p3-01-review-20260911.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P3-01 独立复审未通过，待 P3-01-R1 补齐关键回归（最新，2026-09-11）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "93e1005263871c25a87cd8d8dae4a8f822dbb52b",
            "37851bd283029b1218de561fa633ccad33b40286",
            "没有构造两个来源课程位置但相同 `course.id`",
            "没有让 `TodaySchedulePresentation` 在调课日取来源星期课程",
            "输入本身已经 处于期望顺序",
            "无对应节次",
            "P3-01-R1 应只修改 JVM 测试",
            "P3-01 已实现并通过现有测试，但尚未独立复审通过",
            "不得自动进入 P3-02 或页面实现",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "实际 diff 为 7 个文件",
            "生产实现没有发现需要修改应用代码的语义缺陷",
            "重复 course.id／schedule.id 均已覆盖",
            "TodaySchedulePresentation",
            "逆序或扰乱输入",
            "BUILD SUCCESSFUL in 53s",
            "145 actionable tasks：141 executed、4 up-to-date",
            "Debug JVM：61 tests",
            "Release JVM：61 tests",
            "独立复审未通过",
            "不得进入 P3-02 或页面实现",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])

    def test_p3_01_r1_records_real_regressions_without_claiming_completion(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p3-01-r1-jvm-20260911.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P3-01-R1 测试与证据已补齐，等待最终独立复审（最新，2026-09-11）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "e980238d4c2bc8d5fd2d7bcf063c1f9eaaad0cf7",
            "相同 `course.id`／重复 `schedule.id`",
            "2026-09-05",
            "zh_CN",
            "UPCOMING",
            "Debug／Release JVM XML 各 65 tests",
            "0 failures、0 errors、0 skipped",
            "仍需分析审查窗口最终独立复审",
            "不得自动进入 P3-02 或后续阶段",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "duplicateBusinessIdsKeepEverySourceOccurrenceAndConflictByCourseId",
            "makeupDateUsesSameSourceScheduleInTodayAndWeek",
            "todayWeekAndMatrixSortDisruptedInputsDeterministically",
            "missingPeriodsDegradeStatusAndProgressSafely",
            "Debug JVM XML：tests=65，failures=0，errors=0，skipped=0",
            "Release JVM XML：tests=65，failures=0，errors=0，skipped=0",
            "connectedDebugAndroidTest",
            "P3-01、A02、A03、A07、P3、完整 App 或用户验收完成",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])

    def test_p3_01_r1_review_keeps_stable_key_gate_open(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p3-01-r1-review-20260911.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P3-01-R1 最终复审保留一项稳定键缺口，待 P3-01-R2（最新，2026-09-11）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "e980238d4c2bc8d5fd2d7bcf063c1f9eaaad0cf7..fd06b752cdcbc6925491e1742474d9c8360cf15f",
            "没有任何两项同时具有相同 `startRow` 和 `endRow`",
            "不能支持 证据中“乱序矩阵项目按稳定键排序”的表述",
            "P3-01-R2",
            "不得修改生产代码",
            "Debug／Release JVM XML 各 65 tests",
            "P3-01 尚未独立复审通过",
            "不得进入 P3-02 或页面实现",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "实际只有 4 个文件",
            "duplicateBusinessIdsKeepEverySourceOccurrenceAndConflictByCourseId",
            "makeupDateUsesSameSourceScheduleInTodayAndWeek",
            "zh_CN 名称排序",
            "missingPeriodsDegradeStatusAndProgressSafely",
            "没有两项的 startRow 与 endRow 同时相同",
            "P3-01-R2 最小要求",
            "BUILD SUCCESSFUL in 40s",
            "Debug JVM：65 tests",
            "Release JVM：65 tests",
            "最终独立复审未通过",
            "不得进入 P3-02",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])

    def test_p3_01_r2_records_same_interval_occurrence_key_regression(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p3-01-r2-jvm-20260911.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P3-01-R2 矩阵稳定来源键测试与证据已实施，等待最终独立复审（最新，2026-09-11）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "4aae401181dd4ef563d087dcaea89eb4d00fe8e3",
            "`OccurrenceKey(2,1)`、`OccurrenceKey(1,9)`、`OccurrenceKey(1,3)`",
            "`courseIndex` 与 `scheduleIndex` 两级比较",
            "lane 为 0／1／2",
            "laneCount 均为 3",
            "Debug／Release JVM XML 各 66 tests",
            "仍需分析审查窗口最终独立复审",
            "不得自动进入 P3-02 或后续阶段",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "matrixUsesOccurrenceKeyToOrderSameIntervalSources",
            "startRow=0、endRow=0",
            "(2,1)、(1,9)、(1,3)",
            "(1,3)、(1,9)、(2,1)",
            "courseIndex 和 scheduleIndex 两级排序",
            "lane 分别为 0／1／2",
            "Debug JVM XML：tests=66，failures=0，errors=0，skipped=0",
            "Release JVM XML：tests=66，failures=0，errors=0，skipped=0",
            "connectedDebugAndroidTest",
            "P3-01、A02、A03、A07、P3、完整 App 或用户验收完成",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])

    def test_p3_01_final_review_closes_evidence_gate_without_user_acceptance(self):
        handoff = (DOCS / "handoff.md").read_text()
        evidence_path = DOCS / "evidence/p3-01-final-review-20260911.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P3-01-R2 通过，P3-01 独立复审关闭（最新，2026-09-11）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "4aae401181dd4ef563d087dcaea89eb4d00fe8e3..8eaf32aed98c814cadae2ea6a78f4c834c3b8268",
            "`courseIndex` 和 `scheduleIndex` 两级比较",
            "lane 0／1／2",
            "P3-01 分析契约中的已知证据缺口均已关闭",
            "Debug／Release JVM XML 各 66 tests",
            "P3-01 已实现、测试并通过独立复审，但尚未获用户验收",
            "不得自动进入后续任务",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "R2 实际只有",
            "startRow=0、endRow=0",
            "OccurrenceKey(2,1)、OccurrenceKey(1,9)、OccurrenceKey(1,3)",
            "lane=0／1／2",
            "P3-01 独立复审通过",
            "BUILD SUCCESSFUL in 42s",
            "Debug JVM：66 tests，0 failures，0 errors，0 skipped",
            "Release JVM：66 tests，0 failures，0 errors，0 skipped",
            "未运行 connectedDebugAndroidTest",
            "尚未获用户验收",
            "不自动进入任何后续任务",
        ):
            self.assertIn(marker, evidence)
        self.assertEqual(missing_links(evidence_path, evidence), [])

    def test_p2_02_r1_review_closes_known_blocker_without_claiming_acceptance(self):
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        evidence_path = DOCS / "evidence/p2-02-r1-review-20260910.txt"
        evidence = evidence_path.read_text()
        latest = handoff.split(
            "## P2-02-R1 聚焦修正通过独立复审（最新，2026-09-10）", 1
        )[1].split("\n## ", 1)[0]
        normalized = re.sub(r"\s+", " ", latest)
        for marker in (
            "8ae63295ca16cb22f5e233ef27f5e17d943991f3",
            "2ba11ec8ec2fe95b9e34a210e4597f52ee05805a",
            "P2-02-R1 聚焦修正通过独立复审",
            "Debug／Release JVM 各 34",
            "API 37 ARM64",
            "实际运行 19 项",
            "Room 11、DataStore 8",
            "不等于 P2-02 用户验收、A09、P2 或完整 App 完成",
        ):
            self.assertIn(marker, normalized)
        for marker in (
            "BUILD SUCCESSFUL in 44s",
            "145 actionable tasks",
            "emulator-5584",
            "19 tests",
            "Room 11 tests",
            "DataStore 8 tests",
            "0 failures",
            "0 errors",
            "0 skipped",
            "publicInvalidReminderLeadSaveAndUpdateKeepEnabledAfterReopen",
            "不自动授权 P2-03 或 P3",
        ):
            self.assertIn(marker, evidence)
        self.assertIn("P2-02-R1 已审查", plan)

    def test_p2_03_analysis_defines_pure_kotlin_drafts_and_save_evaluation(self):
        analysis = (DOCS / "p2-03-form-drafts.md").read_text()
        handoff = (DOCS / "handoff.md").read_text()
        plan = (DOCS / "implementation-plan.md").read_text()
        baseline = (DOCS / "product-baseline.md").read_text()
        design = (DOCS / "technical-design.md").read_text()
        normalized = re.sub(r"\s+", " ", analysis)
        for marker in (
            "2ba11ec8ec2fe95b9e34a210e4597f52ee05805a",
            "CourseDraft",
            "CourseScheduleDraft",
            "SemesterDraft",
            "PeriodDraft",
            "#287B74",
            "LocalDate",
            "LocalTime",
            "ID 生成器",
            "Invalid(issues)",
            "Conflicting(conflicts)",
            "Ready",
            "courses.0.schedules",
            "该上课安排已存在，请勿重复添加",
            "历史重复安排在数量没有增加时仍可编辑保存",
            "闭区间节次范围相交",
            "08:00–08:45",
            "19:55–20:40",
            "至少保留一节",
            "不修改 `MainActivity`",
            "不修改 Room schema",
            "不修改 iOS、Web、共享 schema／fixtures",
            "P2-03 实施仍需用户单独授权",
            "P2-03 实施完成后 必须独立复审",
        ):
            self.assertIn(marker, normalized)
        self.assertRegex(analysis, r"至少一个共同\s+生效的教学周")
        for contents in (handoff, plan, baseline, design):
            self.assertIn("P2-03", contents)
        self.assertIn("P2-03 表单草稿与保存评估分析完成，等待实施授权", handoff)
        self.assertTrue(
            "P2-03 已分析待授权" in plan
            or "P2-03 已审查并获用户确认" in plan
        )
        self.assertTrue(
            "尚待实施授权" in baseline
            or "已实现、测试、通过最终独立复审并获用户确认" in baseline
            or "用户已确认本文档及 P1、P2 阶段结果" in baseline
        )
        self.assertTrue(
            "P2-03 表单草稿与保存评估已完成分析、等待实施授权" in design
            or "P2-03 表单草稿与保存评估已通过最终独立复审并获用户确认" in design
            or "P2-03 已通过最终独立复审" in design
        )
        self.assertIn("Terra／高", handoff)


if __name__ == "__main__":
    unittest.main(verbosity=2)
