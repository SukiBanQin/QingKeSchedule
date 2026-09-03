import SwiftUI

struct TodayScheduleView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let calendar: Calendar
    let onAddCourse: () -> Void
    let onSelectCourse: (CourseDTO) -> Void

    private var presentation: TodaySchedulePresentation {
        TodaySchedulePresentation(
            semester: semester,
            courses: courses,
            now: now,
            calendar: calendar
        )
    }

    var body: some View {
        Group {
            if presentation.items.isEmpty {
                ContentUnavailableView {
                    Label("今天没有课程", systemImage: "sun.max")
                } description: {
                    Text(presentation.emptyMessage)
                } actions: {
                    Button("添加课程", action: onAddCourse)
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("add-course-today")
                }
                .accessibilityIdentifier("today-empty")
            } else {
                List {
                    if let next = presentation.items.first(where: \.isNext) {
                        Section("下一门课程") {
                            courseRow(next, emphasized: true)
                        }
                    }

                    Section("今日安排") {
                        ForEach(presentation.items) { item in
                            courseRow(item, emphasized: false)
                        }
                    }
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle("今日")
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 1) {
                    Text("今日").font(.headline)
                    if let week = presentation.teachingWeek,
                       ScheduleRules.isTeachingWeekInSemester(week, semester: semester) {
                        Text("第 \(week) 周")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: onAddCourse) {
                    Label("添加课程", systemImage: "plus")
                }
                .accessibilityIdentifier("add-course-today-toolbar")
            }
        }
    }

    @ViewBuilder
    private func courseRow(_ item: TodayCourseItem, emphasized: Bool) -> some View {
        let occurrence = item.occurrence
        Button {
            onSelectCourse(occurrence.course)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color(courseHex: occurrence.course.color))
                    .frame(width: 5)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(occurrence.course.name)
                            .font(emphasized ? .headline : .body.weight(.semibold))
                        Spacer()
                        Label(item.status.displayName, systemImage: item.status.systemImage)
                            .font(.caption)
                            .foregroundStyle(
                                item.status == .ongoing ? Color.accentColor : Color.secondary
                            )
                    }
                    Text("\(ScheduleDisplayText.periodRange(occurrence.schedule)) · \(ScheduleDisplayText.timeRange(occurrence.schedule, semester: semester))")
                        .font(.subheadline.monospacedDigit())
                    let details = [occurrence.schedule.classroom, occurrence.course.teacher]
                        .filter { !$0.isEmpty }
                        .joined(separator: " · ")
                    if !details.isEmpty {
                        Text(details)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    if emphasized {
                        Text("下一门")
                            .font(.caption.bold())
                            .foregroundStyle(.tint)
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .padding(.vertical, 5)
        .opacity(item.status == .finished ? 0.62 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("today-course-\(occurrence.course.id)-\(occurrence.schedule.id)")
    }
}
