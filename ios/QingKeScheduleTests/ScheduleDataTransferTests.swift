import Foundation
import SwiftData
import Testing
@testable import QingKeSchedule

@MainActor
@Suite("课表导入导出", .serialized)
struct ScheduleDataTransferTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("真实 Web 导出可生成完整预览")
    func importsWebExport() throws {
        let contents = try SharedFixtureLoader.data(named: "web-export.json")

        let preview = try ScheduleDataTransfer.previewImport(
            contents: contents,
            calendar: calendar
        )
        let expected = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")

        #expect(preview.semesterName == "2026 秋季学期")
        #expect(preview.courseCount == 6)
        #expect(preview.data == expected)
        #expect(preview.summary.contains("2026-09-02T12:00:00.000Z"))
    }

    @Test("拒绝未知版本、无效内容和超大文件")
    func rejectsInvalidImports() throws {
        let unknownVersion = try SharedFixtureLoader.data(named: "unknown-version.json")
        let invalidWeekRange = try SharedFixtureLoader.data(named: "invalid-week-range.json")

        #expect(throws: ScheduleDataTransferError.unsupportedSchemaVersion(2)) {
            try ScheduleDataTransfer.previewImport(
                contents: unknownVersion,
                calendar: calendar
            )
        }

        #expect(throws: ScheduleDataTransferError.self) {
            try ScheduleDataTransfer.previewImport(
                contents: invalidWeekRange,
                calendar: calendar
            )
        }

        #expect(throws: ScheduleDataTransferError.fileTooLarge(
            maximumBytes: ScheduleDataTransfer.maximumImportBytes
        )) {
            try ScheduleDataTransfer.previewImport(
                contents: Data(count: ScheduleDataTransfer.maximumImportBytes + 1),
                calendar: calendar
            )
        }
    }

    @Test("iOS 导出保持共享协议、文件名且不包含专属设置")
    func exportsSharedContract() throws {
        let fixture = try SharedFixtureLoader.scheduleData(named: "web-export.json")
        let exportedAt = try #require(
            ScheduleRules.localDate(from: "2026-09-03", calendar: calendar)
        )

        let document = try ScheduleDataTransfer.exportDocument(
            data: fixture,
            exportedAt: exportedAt,
            calendar: calendar
        )
        let decoded = try JSONDecoder().decode(ScheduleDataDTO.self, from: document.contents)
        let object = try #require(
            JSONSerialization.jsonObject(with: document.contents) as? [String: Any]
        )

        #expect(document.fileName == "qingke-schedule-2026-09-03.json")
        #expect(decoded == fixture)
        #expect(ScheduleValidator.validate(decoded, calendar: calendar).isEmpty)
        #expect(Set(object.keys) == ["schemaVersion", "semester", "courses", "updatedAt"])
        #expect(object["remindersEnabled"] == nil)
    }

    @Test("没有学期时给出明确导出失败")
    func missingSemesterCannotExport() throws {
        let empty = try SharedFixtureLoader.scheduleData(named: "empty-schedule.json")

        #expect(throws: ScheduleDataTransferError.missingSemester) {
            try ScheduleDataTransfer.exportDocument(data: empty, calendar: calendar)
        }
    }

    @Test("取消预览不写入，确认保存失败时保留原课表")
    func cancelAndFailurePreserveExistingData() throws {
        enum TestFailure: Error { case save }

        let container = try SwiftDataScheduleRepository.makeContainer(inMemory: true)
        let original = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let seedRepository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar
        )
        try seedRepository.replace(with: original)

        let failingRepository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar,
            beforeSave: { throw TestFailure.save }
        )
        let state = ScheduleAppState(repository: failingRepository, calendar: calendar)
        state.load()
        let preview = try state.previewImport(
            contents: SharedFixtureLoader.data(named: "empty-schedule.json")
        )

        #expect(state.data == original, "仅生成预览等同于用户取消，不应写入")
        #expect(!state.confirmImport(preview))
        #expect(state.data == original)
        let stored = try seedRepository.load()
        #expect(stored == original)
    }
}
