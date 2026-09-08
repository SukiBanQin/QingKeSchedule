#!/usr/bin/env python3
"""观察 Android/iOS 导入边界，不作为产品验收通过断言。

先针对待审源码重新运行 Android assembleDebug。需要 JDK 17、swiftc 和
P1-01 固定版本的 Gradle 依赖缓存。生成物均在临时目录，不修改应用和共享样例。
"""

import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    base = json.loads((ROOT / "ios/Shared/fixtures/valid/complete-schedule.json").read_text())
    cases = {"valid": base}

    def case(name, mutate):
        data = copy.deepcopy(base)
        mutate(data)
        cases[name] = data

    case("string-version", lambda d: d.update(schemaVersion="1"))
    case("string-totalWeeks", lambda d: d["semester"].update(totalWeeks="18"))
    case("decimal-version", lambda d: d.update(schemaVersion=1.0))
    case("unknown-field", lambda d: d.update(extra=True))
    case("nested-unknown", lambda d: d["semester"].update(extra=True))
    case("duplicate-course-id", lambda d: d["courses"].append(copy.deepcopy(d["courses"][0])))
    case("reverse-period-numbers", lambda d: [
        p.update(number=len(d["semester"]["periods"]) - i)
        for i, p in enumerate(d["semester"]["periods"])
    ])
    case("year-zero", lambda d: d["semester"].update(startDate="0000-01-01"))
    class_candidates = (
        ROOT / "Android/app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
        ROOT / "Android/app/build/tmp/kotlin-classes/debug",
    )
    classes = next((path for path in class_candidates if (
        path / "com/qingke/schedule/transfer/ScheduleDataDecoder.class"
    ).is_file()), None)
    if classes is None:
        raise RuntimeError("Run Android assembleDebug against the reviewed source first")
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1"
    jars = []
    for spec in (
        "org.jetbrains.kotlin/kotlin-stdlib/2.2.10",
        "org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.7.3",
        "org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.7.3",
    ):
        matches = list((cache / spec).glob("*/*.jar"))
        if len(matches) != 1:
            raise RuntimeError(f"Expected one cached jar for {spec}, got {matches}")
        jars.extend(matches)

    with tempfile.TemporaryDirectory(prefix="qingke-contract-review-") as directory:
        tmp = Path(directory)
        for name, data in cases.items():
            (tmp / (name + ".json")).write_text(json.dumps(data, ensure_ascii=False))
        # 保留原始数字文本，避免生成样例时先把非法表示规范化。
        source = json.dumps(base, ensure_ascii=False)
        for name, token in {
            "number-leading-plus": "+1",
            "number-leading-zero": "01",
            "number-trailing-point": "1.",
            "number-leading-point": ".1e1",
            "number-valid-exponent": "1e0",
            "number-valid-exponent-plus": "1e+0",
        }.items():
            (tmp / (name + ".json")).write_text(
                source.replace('"schemaVersion": 1', '"schemaVersion": ' + token, 1)
            )
        damaged = copy.deepcopy(base)
        damaged["semester"]["id"] = "BADBYTE"
        (tmp / "invalid-utf8.json").write_bytes(
            json.dumps(damaged).encode().replace(b"BADBYTE", b"\xff")
        )
        files = sorted(map(str, tmp.glob("*.json")))
        (tmp / "ReviewProbe.java").write_text('''import java.nio.file.*;
import com.qingke.schedule.transfer.ScheduleDataDecoder;
public class ReviewProbe {
  public static void main(String[] args) throws Exception {
    for (String arg : args) {
      try {
        ScheduleDataDecoder.INSTANCE.decode(Files.readAllBytes(Path.of(arg)));
        System.out.println(Path.of(arg).getFileName() + ": ACCEPT");
      } catch (Exception e) {
        System.out.println(Path.of(arg).getFileName() + ": REJECT " + e.getClass().getSimpleName());
      }
    }
  }
}
''')
        print("ANDROID: freshly built decoder", flush=True)
        subprocess.run([
            "java", "--class-path", os.pathsep.join(map(str, [classes, *jars])),
            str(tmp / "ReviewProbe.java"), *files,
        ], check=True)
        (tmp / "main.swift").write_text('''import Foundation
let calendar = ScheduleRules.gregorianCalendar(timeZone: TimeZone(secondsFromGMT: 28800)!)
for path in CommandLine.arguments.dropFirst() {
    do {
        let bytes = try Data(contentsOf: URL(fileURLWithPath: path))
        _ = try ScheduleDataTransfer.previewImport(contents: bytes, calendar: calendar)
        print(URL(fileURLWithPath: path).lastPathComponent + ": ACCEPT")
    } catch {
        print(URL(fileURLWithPath: path).lastPathComponent + ": REJECT " + String(describing: error))
    }
}
''')
        subprocess.run([
            "swiftc", "-enable-bare-slash-regex",
            *map(str, sorted((ROOT / "ios/QingKeSchedule/Domain").glob("*.swift"))),
            str(ROOT / "ios/QingKeSchedule/Transfer/ScheduleDataTransfer.swift"),
            str(tmp / "main.swift"), "-o", str(tmp / "swift-probe"),
        ], check=True)
        print("SWIFT: original previewImport compiled on macOS; not iOS app/device tests", flush=True)
        subprocess.run([str(tmp / "swift-probe"), *files], check=True)


if __name__ == "__main__":
    main()
