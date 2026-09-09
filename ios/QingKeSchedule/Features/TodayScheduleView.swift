import SwiftUI

struct TodayScheduleView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let academicCalendarSettings: AcademicCalendarSettings
    let calendar: Calendar
    let isRefreshing: Bool
    let onRefresh: () async -> Void
    let onAddCourse: () -> Void
    let onSelectCourse: (CourseDTO) -> Void

    private var presentation: TodaySchedulePresentation {
        TodaySchedulePresentation(
            semester: semester,
            courses: courses,
            now: now,
            academicCalendarSettings: academicCalendarSettings,
            calendar: calendar
        )
    }

    private var featuredItem: TodayCourseItem? {
        presentation.items.first(where: { $0.status == .ongoing })
            ?? presentation.items.first(where: \.isNext)
    }

    var body: some View {
        ZStack {
            TerminalBackdrop()

            VStack(spacing: 0) {
                TerminalPinnedBrandHeader(
                    code: "LOCAL / 01",
                    accessibilityIdentifier: "today-brand-header"
                )

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 18) {
                        if isRefreshing {
                            TerminalRefreshFeedback(accessibilityIdentifier: "today-refresh-status")
                        }
                        dateHero

                        if presentation.items.isEmpty {
                            emptyState
                        } else {
                            if let featuredItem {
                                activityRail(for: featuredItem)
                                featuredCard(featuredItem)
                            }
                            scheduleSequence
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 12)
                    .padding(.bottom, 100)
                }
                .scrollBounceBehavior(.always)
                .refreshable { await onRefresh() }
            }

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    TerminalFloatingAction(
                        accessibilityIdentifier: "add-course-today-toolbar",
                        action: onAddCourse
                    )
                }
                .padding(.trailing, 20)
                .padding(.bottom, 18)
            }
        }
        .navigationBarHidden(true)
    }

    private var dateHero: some View {
        HStack(alignment: .bottom, spacing: 16) {
            VStack(alignment: .leading, spacing: -7) {
                Text(monthCode)
                    .font(.terminal(10, weight: .black, relativeTo: .caption))
                    .tracking(2)
                Text(dayNumber)
                    .font(.terminal(74, weight: .ultraLight, relativeTo: .largeTitle))
                    .minimumScaleFactor(0.65)
                Text(yearAndWeekday)
                    .font(.terminal(11, weight: .bold, relativeTo: .caption))
                    .tracking(1.2)
                    .foregroundStyle(QingKeTheme.textSecondary)
            }
            .frame(width: 106, alignment: .leading)

            Rectangle()
                .fill(QingKeTheme.borderStrong)
                .frame(width: 1, height: 116)

            VStack(alignment: .leading, spacing: 7) {
                Text("SCHEDULE :// TODAY")
                    .font(.terminal(10, weight: .black, relativeTo: .caption))
                    .tracking(0.8)
                    .foregroundStyle(QingKeTheme.textOnInverse)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(QingKeTheme.inverseSurface)
                Text("今日")
                    .font(.system(size: 42, weight: .black))
                Text(teachingWeekText)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(QingKeTheme.textSecondary)
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing, spacing: 2) {
                Text("COURSE")
                    .font(.terminal(9, weight: .black, relativeTo: .caption2))
                    .tracking(1)
                Text(String(format: "%02d", presentation.items.count))
                    .font(.terminal(38, weight: .light, relativeTo: .title))
                Text("/ DAY")
                    .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                    .foregroundStyle(QingKeTheme.textSecondary)
            }
        }
    }

    private func activityRail(for item: TodayCourseItem) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(item.status == .ongoing ? QingKeTheme.signal : QingKeTheme.cyan)
                .frame(width: 7, height: 7)
            Text(item.status == .ongoing ? "当前课程" : "下一门课程")
                .font(.terminal(10, weight: .black, relativeTo: .caption))
                .tracking(1)
            Spacer()
            Text("进度更新于 \(clockText)")
                .font(.terminal(9, weight: .bold, relativeTo: .caption2))
                .tracking(0.7)
                .foregroundStyle(QingKeTheme.textOnInverse.opacity(0.68))
        }
        .foregroundStyle(QingKeTheme.textOnInverse)
        .padding(.horizontal, 12)
        .frame(height: 34)
        .background(QingKeTheme.inverseSurface)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(item.status == .ongoing ? QingKeTheme.signal : QingKeTheme.cyan)
                .frame(height: 4)
        }
    }

    private func featuredCard(_ item: TodayCourseItem) -> some View {
        let occurrence = item.occurrence
        let accent = item.status == .ongoing ? QingKeTheme.signal : QingKeTheme.cyan
        return Button {
            onSelectCourse(occurrence.course)
        } label: {
            VStack(spacing: 0) {
                HStack {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(QingKeTheme.textOnAccent)
                            .frame(width: 8, height: 8)
                        Text(item.status == .ongoing ? "CURRENT" : "NEXT")
                            .font(.terminal(11, weight: .black, relativeTo: .caption))
                            .tracking(1)
                    }
                    Spacer()
                    Text(featuredPosition(item))
                        .font(.terminal(10, weight: .black, relativeTo: .caption))
                        .tracking(1)
                }
                .foregroundStyle(QingKeTheme.textOnAccent)
                .padding(.horizontal, 12)
                .frame(height: 36)
                .background(accent)

                HStack(spacing: 15) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text(startTime(of: occurrence))
                            .font(.terminal(32, weight: .bold, relativeTo: .title))
                        Text("– \(endTime(of: occurrence))")
                            .font(.terminal(13, weight: .regular, relativeTo: .caption))
                            .foregroundStyle(QingKeTheme.textSecondary)
                    }
                    .frame(width: 82, alignment: .leading)

                    Rectangle()
                        .fill(QingKeTheme.divider)
                        .frame(width: 1, height: 70)

                    VStack(alignment: .leading, spacing: 6) {
                        Text(occurrence.course.name)
                            .font(.title3.weight(.bold))
                            .lineLimit(2)
                        Text(details(for: occurrence))
                            .font(.subheadline)
                            .foregroundStyle(QingKeTheme.textSecondary)
                            .lineLimit(2)
                    }

                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.headline)
                }
                .padding(16)

                if let progress = item.timingProgress {
                    VStack(spacing: 0) {
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(QingKeTheme.progressTrack)
                                Rectangle()
                                    .fill(QingKeTheme.cyan)
                                    .frame(width: proxy.size.width * progress.fraction)
                            }
                        }
                        .frame(height: 5)

                        HStack {
                            Label("已进行 \(progress.elapsedMinutes) 分钟", systemImage: "clock")
                            Spacer()
                            Text("剩余 \(progress.remainingClockText)")
                                .fontWeight(.bold)
                                .monospacedDigit()
                        }
                        .font(.terminal(10, weight: .semibold, relativeTo: .caption))
                        .foregroundStyle(QingKeTheme.textOnInverse)
                        .padding(.horizontal, 12)
                        .frame(height: 36)
                        .background(QingKeTheme.inverseSurface)
                    }
                }
            }
            .background { TerminalAcrylicSurface() }
            .overlay {
                Rectangle().stroke(QingKeTheme.panelEdge, lineWidth: 1)
            }
            .shadow(color: QingKeTheme.shadow, radius: 12, y: 6)
        }
        .buttonStyle(.plain)
        .foregroundStyle(QingKeTheme.textPrimary)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("today-featured-course")
    }

    private var scheduleSequence: some View {
        VStack(alignment: .leading, spacing: 12) {
            TerminalSectionHeader(
                index: String(format: "%02d", presentation.items.count),
                title: "课程序列",
                detail: "QUEUE / ALL DAY"
            )
            ForEach(Array(presentation.items.enumerated()), id: \.element.id) { index, item in
                courseRow(item, index: index)
            }
            Text("END OF SCHEDULE // \(presentation.items.last.map { endTime(of: $0.occurrence) } ?? "--:--")")
                .font(.terminal(10, weight: .bold, relativeTo: .caption))
                .tracking(1.3)
                .foregroundStyle(QingKeTheme.textSecondary)
                .frame(maxWidth: .infinity)
                .padding(.top, 5)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("today-course-sequence")
    }

    private func courseRow(_ item: TodayCourseItem, index: Int) -> some View {
        let occurrence = item.occurrence
        let accent = item.status == .ongoing
            ? QingKeTheme.signal
            : Color(courseHex: occurrence.course.color)
        let tagContentColor = item.status == .ongoing
            ? QingKeTheme.textOnAccent
            : QingKeTheme.courseContentColor(for: occurrence.course.color)
        return Button {
            onSelectCourse(occurrence.course)
        } label: {
            HStack(spacing: 13) {
                Text(String(format: "%02d", index + 1))
                    .font(.terminal(11, weight: .black, relativeTo: .caption))
                    .foregroundStyle(QingKeTheme.textSecondary)
                    .rotationEffect(.degrees(-90))
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 1) {
                    Text(startTime(of: occurrence))
                        .font(.terminal(24, weight: .bold, relativeTo: .title3))
                    Text(endTime(of: occurrence))
                        .font(.terminal(11, relativeTo: .caption))
                        .foregroundStyle(QingKeTheme.textSecondary)
                }
                .frame(width: 66, alignment: .leading)

                Rectangle()
                    .fill(QingKeTheme.divider)
                    .frame(width: 1, height: 52)

                VStack(alignment: .leading, spacing: 5) {
                    TerminalStatusTag(
                        text: statusLabel(for: item),
                        tint: accent,
                        contentColor: tagContentColor
                    )
                    Text(occurrence.course.name)
                        .font(.headline)
                    Text(details(for: occurrence))
                        .font(.caption)
                        .foregroundStyle(QingKeTheme.textSecondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(QingKeTheme.textPrimary)
            .frame(maxWidth: .infinity, minHeight: 92, alignment: .leading)
            .terminalPanel(accent: accent)
        }
        .buttonStyle(.plain)
        .opacity(item.status == .finished ? 0.56 : 1)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("today-course-\(occurrence.course.id)-\(occurrence.schedule.id)")
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 12) {
            TerminalSectionHeader(index: "00", title: "课程序列", detail: "QUEUE EMPTY")
            VStack(alignment: .leading, spacing: 9) {
                TerminalStatusTag(text: "STANDBY", tint: QingKeTheme.cyan)
                Text("今天没有课程")
                    .font(.title2.bold())
                Text(presentation.emptyMessage)
                    .foregroundStyle(QingKeTheme.textSecondary)
                Text("使用右下角 ADD 录入一门新课程。")
                    .font(.subheadline)
                    .foregroundStyle(QingKeTheme.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .terminalPanel(accent: QingKeTheme.cyan)
        }
        .accessibilityIdentifier("today-empty")
    }

    private var teachingWeekText: String {
        guard let week = presentation.teachingWeek,
              ScheduleRules.isTeachingWeekInSemester(week, semester: semester)
        else {
            return "学期外 · \(semester.name)"
        }
        return "第 \(String(format: "%02d", week)) 教学周 · \(week.isMultiple(of: 2) ? "双周" : "单周")"
    }

    private var monthCode: String {
        now.formatted(.dateTime.month(.abbreviated)).uppercased()
    }

    private var dayNumber: String {
        now.formatted(.dateTime.day(.twoDigits))
    }

    private var yearAndWeekday: String {
        "\(calendar.component(.year, from: now)) / \(now.formatted(.dateTime.weekday(.abbreviated)).uppercased())"
    }

    private var clockText: String {
        now.formatted(.dateTime.hour(.twoDigits(amPM: .omitted)).minute(.twoDigits))
    }

    private func startTime(of occurrence: CourseOccurrenceDTO) -> String {
        semester.periods.first {
            $0.number == occurrence.schedule.startPeriod
        }?.startTime ?? "--:--"
    }

    private func endTime(of occurrence: CourseOccurrenceDTO) -> String {
        semester.periods.first {
            $0.number == occurrence.schedule.endPeriod
        }?.endTime ?? "--:--"
    }

    private func details(for occurrence: CourseOccurrenceDTO) -> String {
        let details = [occurrence.schedule.classroom, occurrence.course.teacher]
            .filter { !$0.isEmpty }
        return details.isEmpty
            ? ScheduleDisplayText.periodRange(occurrence.schedule)
            : details.joined(separator: " / ")
    }

    private func statusLabel(for item: TodayCourseItem) -> String {
        switch item.status {
        case .finished: "COMPLETE"
        case .ongoing: "CURRENT"
        case .upcoming: item.isNext ? "NEXT" : "UPCOMING"
        }
    }

    private func featuredPosition(_ item: TodayCourseItem) -> String {
        let index = presentation.items.firstIndex(where: { $0.id == item.id })
            .map { $0 + 1 } ?? 0
        return "\(String(format: "%02d", index)) // \(String(format: "%02d", presentation.items.count))"
    }
}
