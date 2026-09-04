import SwiftUI

struct WeekScheduleView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let calendar: Calendar
    let onAddCourse: () -> Void
    let onSelectCourse: (CourseDTO) -> Void

    @State private var selectedWeek: Int
    @State private var selectedDay: Int

    init(
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        calendar: Calendar,
        onAddCourse: @escaping () -> Void,
        onSelectCourse: @escaping (CourseDTO) -> Void
    ) {
        self.semester = semester
        self.courses = courses
        self.now = now
        self.calendar = calendar
        self.onAddCourse = onAddCourse
        self.onSelectCourse = onSelectCourse
        _selectedWeek = State(initialValue: WeekSchedulePresentation.initialWeek(
            semester: semester,
            now: now,
            calendar: calendar
        ))
        let sundayBasedWeekday = calendar.component(.weekday, from: now)
        _selectedDay = State(initialValue: sundayBasedWeekday == 1 ? 7 : sundayBasedWeekday - 1)
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

    private var matrixPresentation: WeekMatrixPresentation {
        WeekMatrixPresentation(semester: semester, days: presentation.days)
    }

    private var selectedDayPresentation: WeekDayPresentation {
        presentation.days.first(where: { $0.dayOfWeek == selectedDay })
            ?? presentation.days[0]
    }

    var body: some View {
        ZStack {
            TerminalBackdrop()

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    TerminalBrandHeader(code: "MATRIX / 02")
                    screenTitle
                    weekControls
                    weekdayStrip
                    matrixOverview
                    dayManifest
                }
                .padding(.horizontal, 20)
                .padding(.top, 12)
                .padding(.bottom, 100)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("week-schedule")
            }

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    TerminalFloatingAction(
                        accessibilityIdentifier: "add-course-week-toolbar",
                        action: onAddCourse
                    )
                }
                .padding(.trailing, 20)
                .padding(.bottom, 18)
            }
        }
        .navigationBarHidden(true)
    }

    private var screenTitle: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 7) {
                Text("SCHEDULE :// WEEK MATRIX")
                    .font(.terminal(10, weight: .black, relativeTo: .caption))
                    .tracking(0.9)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(QingKeTheme.ink)
                Text("课表")
                    .font(.system(size: 40, weight: .black))
            }

            Spacer()

            VStack(alignment: .trailing, spacing: -4) {
                Text("WEEK")
                    .font(.terminal(9, weight: .black, relativeTo: .caption2))
                    .tracking(1.1)
                Text(String(format: "%02d", selectedWeek))
                    .font(.terminal(42, weight: .light, relativeTo: .title))
            }
        }
    }

    private var weekControls: some View {
        HStack(spacing: 0) {
            Button {
                selectedWeek = max(selectedWeek - 1, 1)
            } label: {
                Image(systemName: "chevron.left")
                    .frame(width: 44, height: 64)
            }
            .disabled(selectedWeek <= 1)
            .accessibilityLabel("上一周")
            .accessibilityIdentifier("week-previous")

            Rectangle()
                .fill(Color.primary.opacity(0.15))
                .frame(width: 1, height: 64)

            Button {
                if let currentWeek = presentation.currentWeek {
                    selectedWeek = currentWeek
                }
            } label: {
                VStack(spacing: 3) {
                    Text(semester.name)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    Text("第 \(String(format: "%02d", selectedWeek)) 教学周")
                        .font(.headline)
                    Text(selectedWeek.isMultiple(of: 2) ? "EVEN WEEK" : "ODD WEEK")
                        .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                        .tracking(1)
                        .foregroundStyle(QingKeTheme.cyan)
                }
                .frame(maxWidth: .infinity, minHeight: 64)
            }
            .buttonStyle(.plain)
            .disabled(presentation.currentWeek == nil || presentation.currentWeek == selectedWeek)
            .accessibilityLabel("第 \(selectedWeek) 周，点按返回本周")
            .accessibilityIdentifier("selected-week")

            Rectangle()
                .fill(Color.primary.opacity(0.15))
                .frame(width: 1, height: 64)

            Button {
                selectedWeek = min(selectedWeek + 1, semester.totalWeeks)
            } label: {
                Image(systemName: "chevron.right")
                    .frame(width: 44, height: 64)
            }
            .disabled(selectedWeek >= semester.totalWeeks)
            .accessibilityLabel("下一周")
            .accessibilityIdentifier("week-next")
        }
        .background { TerminalAcrylicSurface() }
        .overlay {
            Rectangle().stroke(Color.primary.opacity(0.2), lineWidth: 1)
        }
    }

    private var weekdayStrip: some View {
        HStack(spacing: 0) {
            ForEach(presentation.days) { day in
                Button {
                    selectedDay = day.dayOfWeek
                } label: {
                    VStack(spacing: 3) {
                        Text(String(ScheduleDisplayText.weekdayNames[day.dayOfWeek - 1].suffix(1)))
                            .font(.caption2)
                        Text(dayNumber(for: day))
                            .font(.terminal(17, weight: .semibold, relativeTo: .body))
                        Rectangle()
                            .fill(day.dayOfWeek == selectedDay ? QingKeTheme.signal : .clear)
                            .frame(height: 3)
                            .padding(.horizontal, 4)
                    }
                    .foregroundStyle(day.dayOfWeek == selectedDay ? Color.white : Color.primary)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(day.dayOfWeek == selectedDay ? QingKeTheme.ink : Color.clear)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(weekdayAccessibilityLabel(day))
                .accessibilityIdentifier("week-day-selector-\(day.dayOfWeek)")

                if day.dayOfWeek < 7 {
                    Rectangle()
                        .fill(Color.primary.opacity(0.12))
                        .frame(width: 1, height: 54)
                }
            }
        }
        .overlay(alignment: .top) {
            Rectangle().fill(Color.primary.opacity(0.26)).frame(height: 1)
        }
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.primary.opacity(0.26)).frame(height: 1)
        }
    }

    private var matrixOverview: some View {
        VStack(alignment: .leading, spacing: 10) {
            TerminalSectionHeader(
                index: "05",
                title: "周视图",
                detail: ScheduleDisplayText.weekMatrixSummary(
                    periodCount: matrixPresentation.periods.count
                )
            )

            GeometryReader { proxy in
                matrixCanvas(width: proxy.size.width)
            }
            .frame(height: matrixCanvasHeight)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("week-matrix")

            Text("周一至周五已适配在一屏内；点按课程方块可直接编辑。")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }

    private func matrixCanvas(width: CGFloat) -> some View {
        let matrix = matrixPresentation
        let dayColumnWidth = max((width - matrixTimeColumnWidth) / 5, 1)
        let height = matrixCanvasHeight

        return ZStack(alignment: .topLeading) {
            TerminalAcrylicSurface()

            ForEach(0...matrix.periods.count, id: \.self) { row in
                Rectangle()
                    .fill(Color.primary.opacity(row == 0 ? 0.3 : 0.13))
                    .frame(width: width, height: 1)
                    .offset(y: row == 0
                            ? matrixHeaderHeight
                            : matrixHeaderHeight + CGFloat(row) * matrixRowHeight)
            }

            ForEach(0...5, id: \.self) { column in
                Rectangle()
                    .fill(Color.primary.opacity(column == 0 ? 0.3 : 0.13))
                    .frame(width: 1, height: height)
                    .offset(x: column == 0
                            ? matrixTimeColumnWidth
                            : matrixTimeColumnWidth + CGFloat(column) * dayColumnWidth)
            }

            Text("TIME")
                .font(.terminal(9, weight: .black, relativeTo: .caption2))
                .tracking(0.8)
                .foregroundStyle(.secondary)
                .frame(width: matrixTimeColumnWidth, height: matrixHeaderHeight)

            ForEach(0..<5, id: \.self) { dayColumn in
                Text(ScheduleDisplayText.weekdayNames[dayColumn])
                    .font(.terminal(9, weight: .black, relativeTo: .caption))
                    .minimumScaleFactor(0.7)
                    .frame(width: dayColumnWidth, height: matrixHeaderHeight)
                    .offset(x: matrixTimeColumnWidth + CGFloat(dayColumn) * dayColumnWidth)
                    .accessibilityIdentifier("week-matrix-day-\(dayColumn + 1)")
            }

            ForEach(Array(matrix.periods.enumerated()), id: \.element.number) { row, period in
                VStack(spacing: 1) {
                    Text(String(format: "%02d", period.number))
                        .font(.terminal(13, weight: .black, relativeTo: .caption))
                    Text(period.startTime)
                        .font(.terminal(7, weight: .semibold, relativeTo: .caption2))
                        .foregroundStyle(.secondary)
                }
                .frame(width: matrixTimeColumnWidth, height: matrixRowHeight)
                .offset(y: matrixHeaderHeight + CGFloat(row) * matrixRowHeight)
            }

            ForEach(matrix.items) { item in
                matrixCourseBlock(item, dayColumnWidth: dayColumnWidth)
            }
        }
        .frame(width: width, height: height)
        .overlay {
            Rectangle()
                .stroke(Color.white.opacity(0.7), lineWidth: 1)
        }
    }

    private func matrixCourseBlock(
        _ item: WeekMatrixItem,
        dayColumnWidth: CGFloat
    ) -> some View {
        let laneWidth = dayColumnWidth / CGFloat(item.laneCount)
        let accent = item.isConflicting
            ? QingKeTheme.signal
            : Color(courseHex: item.occurrence.course.color)
        let blockHeight = matrixRowHeight * CGFloat(item.rowSpan) - 4

        return Button {
            onSelectCourse(item.occurrence.course)
        } label: {
            VStack(alignment: .leading, spacing: 3) {
                if item.isConflicting {
                    Text("CONFLICT")
                        .font(.terminal(7, weight: .black, relativeTo: .caption2))
                        .foregroundStyle(QingKeTheme.signal)
                }
                Text(item.occurrence.course.name)
                    .font(.terminal(9, weight: .bold, relativeTo: .caption))
                    .lineLimit(item.rowSpan > 1 ? 2 : 1)
                    .minimumScaleFactor(0.62)
                let details = ScheduleDisplayText.compactCourseDetails(
                    course: item.occurrence.course,
                    schedule: item.occurrence.schedule
                )
                if !details.isEmpty {
                    Text(details)
                        .font(.terminal(7, relativeTo: .caption2))
                        .foregroundStyle(.white.opacity(0.62))
                        .lineLimit(item.rowSpan > 1 ? 2 : 1)
                        .minimumScaleFactor(0.62)
                }
            }
            .foregroundStyle(.white)
            .padding(.leading, 8)
            .padding(.trailing, 4)
            .padding(.vertical, 5)
            .frame(
                width: laneWidth - 4,
                height: blockHeight,
                alignment: .topLeading
            )
            .background(QingKeTheme.ink.opacity(0.91))
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(accent)
                    .frame(width: 4)
            }
            .overlay {
                Rectangle()
                    .stroke(Color.white.opacity(0.42), lineWidth: 0.8)
            }
        }
        .buttonStyle(.plain)
        .offset(
            x: matrixTimeColumnWidth
                + CGFloat(item.dayColumn) * dayColumnWidth
                + CGFloat(item.lane) * laneWidth
                + 2,
            y: matrixHeaderHeight + CGFloat(item.startRow) * matrixRowHeight + 2
        )
        .accessibilityLabel(matrixCourseAccessibilityLabel(item))
        .accessibilityIdentifier("week-matrix-course-\(item.id)")
    }

    private var dayManifest: some View {
        VStack(alignment: .leading, spacing: 12) {
            TerminalSectionHeader(
                index: String(format: "%02d", selectedDay),
                title: ScheduleDisplayText.weekdayNames[selectedDay - 1],
                detail: "\(selectedDayPresentation.items.count) ENTRIES"
            )

            if selectedDayPresentation.items.isEmpty {
                emptyDay
            } else {
                ForEach(Array(selectedDayPresentation.items.enumerated()), id: \.element.id) { index, item in
                    weekCourseCard(item, index: index)
                }
            }

            Text(selectedDayPresentation.items.isEmpty
                 ? "NO MISSION ASSIGNED"
                 : "END OF MANIFEST // \(lastEndTime)")
                .font(.terminal(10, weight: .bold, relativeTo: .caption))
                .tracking(1.2)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity)
                .padding(.top, 5)
        }
        .accessibilityElement(children: .contain)
    }

    private var emptyDay: some View {
        HStack(spacing: 14) {
            Text("00")
                .font(.terminal(42, weight: .ultraLight, relativeTo: .title))
                .foregroundStyle(QingKeTheme.cyan)
            Rectangle()
                .fill(Color.primary.opacity(0.16))
                .frame(width: 1, height: 55)
            VStack(alignment: .leading, spacing: 4) {
                Text("该日无课程安排")
                    .font(.headline)
                Text("选择其他日期，或使用 ADD 添加课程。")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 110, alignment: .leading)
        .terminalPanel(accent: QingKeTheme.cyan)
        .accessibilityIdentifier("week-empty-day")
    }

    private func weekCourseCard(_ item: WeekCourseItem, index: Int) -> some View {
        let occurrence = item.occurrence
        let accent = item.isConflicting
            ? QingKeTheme.signal
            : Color(courseHex: occurrence.course.color)
        return Button {
            onSelectCourse(occurrence.course)
        } label: {
            HStack(spacing: 13) {
                Text(String(format: "%02d", index + 1))
                    .font(.terminal(11, weight: .black, relativeTo: .caption))
                    .foregroundStyle(.secondary)
                    .rotationEffect(.degrees(-90))
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 1) {
                    Text(startTime(of: occurrence))
                        .font(.terminal(24, weight: .bold, relativeTo: .title3))
                    Text(endTime(of: occurrence))
                        .font(.terminal(11, relativeTo: .caption))
                        .foregroundStyle(.secondary)
                }
                .frame(width: 66, alignment: .leading)

                Rectangle()
                    .fill(Color.primary.opacity(0.15))
                    .frame(width: 1, height: 55)

                VStack(alignment: .leading, spacing: 5) {
                    if item.isConflicting {
                        TerminalStatusTag(text: "冲突", tint: QingKeTheme.signal)
                    } else {
                        Text(ScheduleDisplayText.periodRange(occurrence.schedule).uppercased())
                            .font(.terminal(9, weight: .black, relativeTo: .caption2))
                            .tracking(0.8)
                            .foregroundStyle(.secondary)
                    }
                    Text(occurrence.course.name)
                        .font(.headline)
                        .lineLimit(2)
                    Text(courseDetails(occurrence))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, minHeight: 94, alignment: .leading)
            .terminalPanel(accent: accent)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("week-course-\(occurrence.course.id)-\(occurrence.schedule.id)")
    }

    private var lastEndTime: String {
        guard let occurrence = selectedDayPresentation.items.last?.occurrence else { return "--:--" }
        return endTime(of: occurrence)
    }

    private func dayNumber(for day: WeekDayPresentation) -> String {
        guard let date = day.date else { return "--" }
        return date.formatted(.dateTime.day(.twoDigits))
    }

    private func weekdayAccessibilityLabel(_ day: WeekDayPresentation) -> String {
        let weekday = ScheduleDisplayText.weekdayNames[day.dayOfWeek - 1]
        guard let date = day.date else { return weekday }
        return "\(weekday)，\(date.formatted(.dateTime.month().day()))"
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

    private func courseDetails(_ occurrence: CourseOccurrenceDTO) -> String {
        let values = [occurrence.schedule.classroom, occurrence.course.teacher]
            .filter { !$0.isEmpty }
        return values.isEmpty
            ? ScheduleDisplayText.periodRange(occurrence.schedule)
            : values.joined(separator: " / ")
    }

    private func matrixCourseAccessibilityLabel(_ item: WeekMatrixItem) -> String {
        let schedule = item.occurrence.schedule
        let parts = [
            ScheduleDisplayText.weekdayNames[schedule.dayOfWeek - 1],
            ScheduleDisplayText.periodRange(schedule),
            item.occurrence.course.name,
            item.isConflicting ? "存在冲突" : nil,
        ].compactMap { $0 }
        return parts.joined(separator: "，")
    }

    private var matrixCanvasHeight: CGFloat {
        matrixHeaderHeight + matrixRowHeight * CGFloat(matrixPresentation.periods.count)
    }

    private var matrixTimeColumnWidth: CGFloat { 44 }
    private var matrixHeaderHeight: CGFloat { 38 }
    private var matrixRowHeight: CGFloat { 68 }
}
