import Foundation
import SwiftData
import Testing
@testable import QingKeSchedule

@MainActor
@Suite("SwiftData 课表仓库", .serialized)
struct SwiftDataScheduleRepositoryTests {
    private let calendar = ScheduleRules.gregorianCalendar(
        timeZone: TimeZone(secondsFromGMT: 8 * 60 * 60)!
    )

    @Test("空容器返回版本 1 的空课表")
    func emptyContainerLoadsEmptyData() throws {
        let (_, repository) = try makeRepository()

        let data = try repository.load()

        #expect(data.schemaVersion == 1)
        #expect(data.semester == nil)
        #expect(data.courses.isEmpty)
        #expect(data.updatedAt == SwiftDataScheduleRepository.emptyUpdatedAt)
    }

    @Test("替换数据后完整保留学期、顺序与课程")
    func replacementRoundTripsFixture() throws {
        let (_, repository) = try makeRepository()
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")

        try repository.replace(with: fixture)

        #expect(try repository.load() == fixture)
    }

    @Test("学期与课程保存可新建和更新")
    func saveSemesterAndUpsertCourse() throws {
        let (_, repository) = try makeRepository()
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(fixture.semester)
        let original = try #require(fixture.courses.first)

        try repository.saveSemester(semester)
        try repository.saveCourse(original)
        var loaded = try repository.load()
        #expect(loaded.courses == [original])

        let renamed = CourseDTO(
            id: original.id,
            name: "数据结构（修改）",
            teacher: original.teacher,
            color: original.color,
            schedules: original.schedules
        )
        try repository.saveCourse(renamed)
        loaded = try repository.load()
        #expect(loaded.courses.count == 1)
        #expect(loaded.courses.first?.name == renamed.name)
    }

    @Test("删除课程级联删除上课安排")
    func deleteCourseCascadesSchedules() throws {
        let (container, repository) = try makeRepository()
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        try repository.replace(with: fixture)
        let target = try #require(fixture.courses.first)
        let deletedScheduleCount = target.schedules.count

        try repository.deleteCourse(id: target.id)

        let verificationContext = ModelContext(container)
        let schedules = try verificationContext.fetch(FetchDescriptor<CourseScheduleRecord>())
        #expect(try repository.load().courses.count == fixture.courses.count - 1)
        #expect(schedules.count == fixture.courses.flatMap(\.schedules).count - deletedScheduleCount)
    }

    @Test("无效替换在写入前被拒绝并保留旧数据")
    func invalidReplacementPreservesOldData() throws {
        let (_, repository) = try makeRepository()
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        try repository.replace(with: fixture)
        let invalid = ScheduleDataDTO(
            semester: nil,
            courses: fixture.courses,
            updatedAt: fixture.updatedAt
        )

        #expect(throws: ScheduleRepositoryError.self) {
            try repository.replace(with: invalid)
        }
        #expect(try repository.load() == fixture)
    }

    @Test("底层保存失败时回滚替换")
    func storageFailureRollsBackReplacement() throws {
        enum TestFailure: Error { case save }

        let container = try SwiftDataScheduleRepository.makeContainer(inMemory: true)
        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let initialRepository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar
        )
        try initialRepository.replace(with: fixture)

        let failingRepository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar,
            beforeSave: { throw TestFailure.save }
        )
        let empty = ScheduleDataDTO(
            semester: nil,
            courses: [],
            updatedAt: "2026-09-03T00:00:00.000Z"
        )
        #expect(throws: TestFailure.self) {
            try failingRepository.replace(with: empty)
        }

        let verificationRepository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar
        )
        #expect(try verificationRepository.load() == fixture)
    }

    @Test("应用状态只在保存成功后刷新并显示失败信息")
    func appStateReflectsRepositoryResult() throws {
        let (_, repository) = try makeRepository()
        let state = ScheduleAppState(repository: repository)
        state.load()
        #expect(state.needsOnboarding)

        let fixture = try SharedFixtureLoader.scheduleData(named: "complete-schedule.json")
        let semester = try #require(fixture.semester)
        #expect(state.saveSemester(semester))
        #expect(state.semester == semester)
        #expect(state.presentedError == nil)

        let invalidCourse = CourseDTO(
            id: "invalid-course",
            name: "",
            teacher: "",
            color: "#287B74",
            schedules: []
        )
        #expect(!state.saveCourse(invalidCourse))
        #expect(state.courses.isEmpty)
        #expect(state.presentedError != nil)
    }

    private func makeRepository() throws -> (ModelContainer, SwiftDataScheduleRepository) {
        let container = try SwiftDataScheduleRepository.makeContainer(inMemory: true)
        let repository = SwiftDataScheduleRepository(
            context: ModelContext(container),
            calendar: calendar,
            now: { Date(timeIntervalSince1970: 1_788_307_200) }
        )
        return (container, repository)
    }
}
