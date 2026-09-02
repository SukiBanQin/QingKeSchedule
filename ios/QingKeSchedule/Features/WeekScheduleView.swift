import SwiftUI

struct WeekScheduleView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let calendar: Calendar

    @State private var selectedWeek: Int

    init(
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        calendar: Calendar
    ) {
        self.semester = semester
        self.courses = courses
        self.now = now
        self.calendar = calendar
        _selectedWeek = State(initialValue: WeekSchedulePresentation.initialWeek(
            semester: semester,
            now: now,
            calendar: calendar
        ))
    }

    private var presentation: WeekSchedulePresentation {
        WeekSchedulePresentation(
            week: selectedWeek,
            semester: semester,
            courses: courses,
            now: now,
            calendar: calendar
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            weekControls
            Divider()
            ScrollView(.horizontal) {
                LazyHStack(alignment: .top, spacing: 12) {
                    ForEach(presentation.days) { day in
                        dayColumn(day)
                            .containerRelativeFrame(.horizontal, count: 1, spacing: 12)
                    }
                }
                .scrollTargetLayout()
                .padding()
            }
            .scrollTargetBehavior(.viewAligned)
        }
        .navigationTitle("课表")
        .accessibilityIdentifier("week-schedule")
    }

    private var weekControls: some View {
        HStack(spacing: 16) {
            Button {
                selectedWeek = max(selectedWeek - 1, 1)
            } label: {
                Image(systemName: "chevron.left")
            }
            .disabled(selectedWeek <= 1)
            .accessibilityLabel("上一周")

            VStack(spacing: 2) {
                Text("第 \(selectedWeek) 周")
                    .font(.headline)
                    .accessibilityIdentifier("selected-week")
                Text(semester.name)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)

            Button {
                selectedWeek = min(selectedWeek + 1, semester.totalWeeks)
            } label: {
                Image(systemName: "chevron.right")
            }
            .disabled(selectedWeek >= semester.totalWeeks)
            .accessibilityLabel("下一周")

            if let currentWeek = presentation.currentWeek {
                Button("本周") {
                    selectedWeek = currentWeek
                }
                .disabled(selectedWeek == currentWeek)
                .accessibilityIdentifier("current-week")
            }
        }
        .padding()
    }

    private func dayColumn(_ day: WeekDayPresentation) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(ScheduleDisplayText.weekdayNames[day.dayOfWeek - 1])
                    .font(.title3.bold())
                if let date = day.date {
                    Text(date.formatted(.dateTime.month(.twoDigits).day(.twoDigits)))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }

            if day.items.isEmpty {
                ContentUnavailableView(
                    "无课",
                    systemImage: "cup.and.saucer",
                    description: Text("这一天没有课程安排。")
                )
                .frame(maxWidth: .infinity, minHeight: 280)
            } else {
                ForEach(day.items) { item in
                    weekCourseCard(item)
                }
                Spacer(minLength: 0)
            }
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
        .accessibilityIdentifier("week-day-\(day.dayOfWeek)")
    }

    private func weekCourseCard(_ item: WeekCourseItem) -> some View {
        let occurrence = item.occurrence
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(occurrence.course.name)
                    .font(.headline)
                Spacer()
                if item.isConflicting {
                    Label("冲突", systemImage: "exclamationmark.triangle.fill")
                        .font(.caption.bold())
                        .foregroundStyle(.red)
                }
            }
            Text("\(ScheduleDisplayText.periodRange(occurrence.schedule)) · \(ScheduleDisplayText.timeRange(occurrence.schedule, semester: semester))")
                .font(.subheadline.monospacedDigit())
            if !occurrence.schedule.classroom.isEmpty {
                Label(occurrence.schedule.classroom, systemImage: "mappin.and.ellipse")
                    .font(.subheadline)
            }
            if !occurrence.course.teacher.isEmpty {
                Label(occurrence.course.teacher, systemImage: "person")
                    .font(.caption)
            }
        }
        .foregroundStyle(.primary)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(courseHex: occurrence.course.color).opacity(0.18))
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 3)
                .fill(Color(courseHex: occurrence.course.color))
                .frame(width: 5)
                .padding(.vertical, 8)
                .accessibilityHidden(true)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("week-course-\(occurrence.course.id)-\(occurrence.schedule.id)")
    }
}
