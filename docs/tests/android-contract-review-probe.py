#!/usr/bin/env python3
"""Observe Android/iOS import boundaries; not a pass/fail product acceptance test.

Run a fresh Android assembleDebug first. Requires JDK 17, swiftc, and the
Gradle dependency cache for the versions in P1-01. All generated files are
temporary; this script never edits app sources or shared fixtures.
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
    classes = ROOT / "Android/app/build/tmp/kotlin-classes/debug"
    if not (classes / "com/qingke/schedule/transfer/ScheduleDataDecoder.class").is_file():
        raise RuntimeError("Run Android assembleDebug against the reviewed source first")
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle"))) / "caches/modules-2/files-2.1"
    jars = []
    for spec in (
        "org.jetbrains.kotlin/kotlin-stdlib/2.0.21",
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
