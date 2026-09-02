import Foundation
import Testing
@testable import QingKeSchedule

private final class FixtureBundleToken {}

private struct FixtureManifest: Decodable {
    let valid: [String]
    let invalid: [String]
}

enum SharedFixtureLoader {
    static func scheduleData(named fileName: String) throws -> ScheduleDataDTO {
        try JSONDecoder().decode(ScheduleDataDTO.self, from: data(named: fileName))
    }

    static func manifest() throws -> (valid: [String], invalid: [String]) {
        let manifest = try JSONDecoder().decode(
            FixtureManifest.self,
            from: data(named: "manifest.json")
        )
        return (manifest.valid, manifest.invalid)
    }

    static func data(named fileName: String) throws -> Data {
        let fileURL = URL(fileURLWithPath: fileName)
        guard let resourceURL = Bundle(for: FixtureBundleToken.self).url(
            forResource: fileURL.deletingPathExtension().lastPathComponent,
            withExtension: fileURL.pathExtension
        ) else {
            throw FixtureError.missingResource(fileName)
        }
        return try Data(contentsOf: resourceURL)
    }

    private enum FixtureError: Error {
        case missingResource(String)
    }
}

@Suite("共享 schemaVersion 1 契约")
struct SharedFixtureTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("所有有效 fixture 均可解码、校验和往返编码")
    func validFixtures() throws {
        let manifest = try SharedFixtureLoader.manifest()

        for fileName in manifest.valid {
            let data = try SharedFixtureLoader.data(named: fileName)
            let decoded = try JSONDecoder().decode(ScheduleDataDTO.self, from: data)
            let issues = ScheduleValidator.validate(decoded, calendar: calendar)
            #expect(issues.isEmpty, "有效 fixture 不应产生校验错误：\(fileName)，\(issues)")

            let encoded = try JSONEncoder().encode(decoded)
            let roundTripped = try JSONDecoder().decode(ScheduleDataDTO.self, from: encoded)
            #expect(roundTripped == decoded, "Codable 往返应保留全部字段：\(fileName)")
        }
    }

    @Test("空课表编码保留必需的 semester null 字段")
    func emptyScheduleEncodingKeepsNullSemester() throws {
        let decoded = try SharedFixtureLoader.scheduleData(named: "empty-schedule.json")
        let encoded = try JSONEncoder().encode(decoded)
        let object = try #require(JSONSerialization.jsonObject(with: encoded) as? [String: Any])

        #expect(Set(object.keys) == ["schemaVersion", "semester", "courses", "updatedAt"])
        #expect(object["semester"] is NSNull)
    }

    @Test("所有无效 fixture 均被解码或业务校验拒绝")
    func invalidFixtures() throws {
        let manifest = try SharedFixtureLoader.manifest()

        for fileName in manifest.invalid {
            let data = try SharedFixtureLoader.data(named: fileName)
            let wasRejected: Bool
            do {
                let decoded = try JSONDecoder().decode(ScheduleDataDTO.self, from: data)
                wasRejected = !ScheduleValidator.validate(decoded, calendar: calendar).isEmpty
            } catch {
                wasRejected = true
            }
            #expect(wasRejected, "无效 fixture 必须被拒绝：\(fileName)")
        }
    }

    @Test("共享 schema 声明版本 1")
    func schemaDeclaresVersionOne() throws {
        let data = try SharedFixtureLoader.data(named: "schedule-data.schema.json")
        let object = try #require(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let properties = try #require(object["properties"] as? [String: Any])
        let version = try #require(properties["schemaVersion"] as? [String: Any])
        #expect(version["const"] as? Int == ScheduleDataDTO.supportedSchemaVersion)
    }
}
