import SwiftUI

struct WeekScheduleSelection: Equatable {
    private(set) var selectedWeek: Int
    private(set) var selectedDay: Int
    private(set) var followsCurrentWeek: Bool
    private(set) var followsCurrentDay: Bool

    init(semester: SemesterDTO, now: Date, calendar: Calendar) {
        selectedWeek = WeekSchedulePresentation.initialWeek(
            semester: semester,
            now: now,
            calendar: calendar
        )
        selectedDay = Self.dayOfWeek(for: now, calendar: calendar)
        followsCurrentWeek = Self.currentTeachingWeek(
            semester: semester,
            now: now,
            calendar: calendar
        ) != nil
        followsCurrentDay = true
    }

    mutating func refresh(for now: Date, semester: SemesterDTO, calendar: Calendar) {
        if followsCurrentWeek,
           let currentWeek = Self.currentTeachingWeek(
               semester: semester,
               now: now,
               calendar: calendar
           ) {
            selectedWeek = currentWeek
        }
        if followsCurrentDay {
            selectedDay = Self.dayOfWeek(for: now, calendar: calendar)
        }
    }

    mutating func selectPreviousWeek() {
        selectedWeek = max(selectedWeek - 1, 1)
        followsCurrentWeek = false
    }

    mutating func selectNextWeek(totalWeeks: Int) {
        selectedWeek = min(selectedWeek + 1, totalWeeks)
        followsCurrentWeek = false
    }

    mutating func returnToCurrentWeek(
        semester: SemesterDTO,
        now: Date,
        calendar: Calendar
    ) {
        guard let currentWeek = Self.currentTeachingWeek(
            semester: semester,
            now: now,
            calendar: calendar
        ) else {
            return
        }
        selectedWeek = currentWeek
        followsCurrentWeek = true
    }

    mutating func selectDay(_ day: Int, now: Date, calendar: Calendar) {
        selectedDay = day
        followsCurrentDay = day == Self.dayOfWeek(for: now, calendar: calendar)
    }

    private static func currentTeachingWeek(
        semester: SemesterDTO,
        now: Date,
        calendar: Calendar
    ) -> Int? {
        guard let week = ScheduleRules.teachingWeek(
            for: now,
            semester: semester,
            calendar: calendar
        ), ScheduleRules.isTeachingWeekInSemester(week, semester: semester) else {
            return nil
        }
        return week
    }

    private static func dayOfWeek(for date: Date, calendar: Calendar) -> Int {
        let sundayBasedWeekday = calendar.component(.weekday, from: date)
        return sundayBasedWeekday == 1
            ? ScheduleDisplayText.weekdayNames.count
            : sundayBasedWeekday - 1
    }
}

struct WeekScheduleView: View {
    let semester: SemesterDTO
    let courses: [CourseDTO]
    let now: Date
    let academicCalendarSettings: AcademicCalendarSettings
    let calendar: Calendar
    let isRefreshing: Bool
    let onRefresh: () async -> Void
    let onAddCourse: () -> Void
    let onSelectCourse: (CourseDTO) -> Void

    @State private var selection: WeekScheduleSelection

    init(
        semester: SemesterDTO,
        courses: [CourseDTO],
        now: Date,
        academicCalendarSettings: AcademicCalendarSettings,
        calendar: Calendar,
        isRefreshing: Bool = false,
        onRefresh: @escaping () async -> Void = {},
        onAddCourse: @escaping () -> Void,
        onSelectCourse: @escaping (CourseDTO) -> Void
    ) {
        self.semester = semester
        self.courses = courses
        self.now = now
        self.academicCalendarSettings = academicCalendarSettings
        self.calendar = calendar
        self.isRefreshing = isRefreshing
        self.onRefresh = onRefresh
        self.onAddCourse = onAddCourse
        self.onSelectCourse = onSelectCourse
        _selection = State(initialValue: WeekScheduleSelection(
            semester: semester,
            now: now,
            calendar: calendar
        ))
    }

    private var presentation: WeekSchedulePresentation {
        WeekSchedulePresentation(
            week: selection.selectedWeek,
            semester: semester,
            courses: courses,
            now: now,
            academicCalendarSettings: academicCalendarSettings,
            calendar: calendar
        )
    }

    private var matrixPresentation: WeekMatrixPresentation {
        WeekMatrixPresentation(
            semester: semester,
            days: presentation.days,
            academicCalendarSettings: academicCalendarSettings
        )
    }

    private var selectedDayPresentation: WeekDayPresentation {
        presentation.days.first(where: { $0.dayOfWeek == selection.selectedDay })
            ?? presentation.days[0]
    }

    var body: some View {
        ZStack {
            TerminalBackdrop()

            VStack(spacing: 0) {
                TerminalPinnedBrandHeader(
                    code: "MATRIX / 02",
                    accessibilityIdentifier: "week-brand-header"
                )

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        if isRefreshing {
                            TerminalRefreshFeedback(accessibilityIdentifier: "week-refresh-status")
                        }
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
                .scrollBounceBehavior(.always)
                .refreshable { await onRefresh() }
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
        .onChange(of: now) { _, updatedNow in
            selection.refresh(
                for: updatedNow,
                semester: semester,
                calendar: calendar
            )
        }
    }

    private var screenTitle: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 7) {
                Text("SCHEDULE :// WEEK MATRIX")
                    .font(.terminal(10, weight: .black, relativeTo: .caption))
                    .tracking(0.9)
                    .foregroundStyle(QingKeTheme.textOnInverse)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(QingKeTheme.inverseSurface)
                Text("课表")
                    .font(.system(size: 40, weight: .black))
            }

            Spacer()

            VStack(alignment: .trailing, spacing: -4) {
                Text("WEEK")
                    .font(.terminal(9, weight: .black, relativeTo: .caption2))
                    .tracking(1.1)
                Text(String(format: "%02d", selection.selectedWeek))
                    .font(.terminal(42, weight: .light, relativeTo: .title))
            }
        }
    }

    private var weekControls: some View {
        HStack(spacing: 0) {
            Button {
                selection.selectPreviousWeek()
            } label: {
                Image(systemName: "chevron.left")
                    .frame(width: 44, height: 64)
                    .contentShape(Rectangle())
            }
            .disabled(selection.selectedWeek <= 1)
            .accessibilityLabel("上一周")
            .accessibilityIdentifier("week-previous")

            Rectangle()
                .fill(QingKeTheme.divider)
                .frame(width: 1, height: 64)

            Button {
                selection.returnToCurrentWeek(
                    semester: semester,
                    now: now,
                    calendar: calendar
                )
            } label: {
                VStack(spacing: 3) {
                    Text(semester.name)
                        .font(.caption)
                        .foregroundStyle(QingKeTheme.textSecondary)
                        .lineLimit(1)
                    Text("第 \(String(format: "%02d", selection.selectedWeek)) 教学周")
                        .font(.headline)
                    Text(selection.selectedWeek.isMultiple(of: 2) ? "EVEN WEEK" : "ODD WEEK")
                        .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                        .tracking(1)
                        .foregroundStyle(QingKeTheme.cyan)
                }
                .frame(maxWidth: .infinity, minHeight: 64)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(
                presentation.currentWeek == nil
                    || (presentation.currentWeek == selection.selectedWeek
                        && selection.followsCurrentWeek)
            )
            .accessibilityLabel("第 \(selection.selectedWeek) 周，点按返回本周")
            .accessibilityIdentifier("selected-week")

            Rectangle()
                .fill(QingKeTheme.divider)
                .frame(width: 1, height: 64)

            Button {
                selection.selectNextWeek(totalWeeks: semester.totalWeeks)
            } label: {
                Image(systemName: "chevron.right")
                    .frame(width: 44, height: 64)
                    .contentShape(Rectangle())
            }
            .disabled(selection.selectedWeek >= semester.totalWeeks)
            .accessibilityLabel("下一周")
            .accessibilityIdentifier("week-next")
        }
        .background { TerminalAcrylicSurface() }
        .overlay {
            Rectangle().stroke(QingKeTheme.border, lineWidth: 1)
        }
    }

    private var weekdayStrip: some View {
        HStack(spacing: 0) {
            ForEach(presentation.days) { day in
                Button {
                    selection.selectDay(day.dayOfWeek, now: now, calendar: calendar)
                } label: {
                    VStack(spacing: 3) {
                        Text(String(ScheduleDisplayText.weekdayNames[day.dayOfWeek - 1].suffix(1)))
                            .font(.caption2)
                        Text(dayNumber(for: day))
                            .font(.terminal(17, weight: .semibold, relativeTo: .body))
                        Rectangle()
                            .fill(weekdayIndicatorColor(for: day))
                            .frame(height: 3)
                            .padding(.horizontal, 4)
                    }
                    .foregroundStyle(weekdayForegroundColor(for: day))
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(day.dayOfWeek == selection.selectedDay ? QingKeTheme.inverseSurface : Color.clear)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(weekdayAccessibilityLabel(day))
                .accessibilityValue(day.dayOfWeek == selection.selectedDay ? "已选择" : "未选择")
                .accessibilityIdentifier("week-day-selector-\(day.dayOfWeek)")

                if day.dayOfWeek < ScheduleDisplayText.weekdayNames.count {
                    Rectangle()
                        .fill(QingKeTheme.divider)
                        .frame(width: 1, height: 54)
                }
            }
        }
        .overlay(alignment: .top) {
            Rectangle().fill(QingKeTheme.borderStrong).frame(height: 1)
        }
        .overlay(alignment: .bottom) {
            Rectangle().fill(QingKeTheme.borderStrong).frame(height: 1)
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

            Text("周一至周日已适配在一屏内；点按课程方块可直接编辑。")
                .font(.caption2)
                .foregroundStyle(QingKeTheme.textSecondary)
        }
    }

    private func matrixCanvas(width: CGFloat) -> some View {
        let matrix = matrixPresentation
        let weekdayCount = ScheduleDisplayText.weekdayNames.count
        let dayColumnWidth = max((width - matrixTimeColumnWidth) / CGFloat(weekdayCount), 1)
        let height = matrixCanvasHeight

        return ZStack(alignment: .topLeading) {
            TerminalAcrylicSurface()

            ForEach(0...matrix.periods.count, id: \.self) { row in
                Rectangle()
                    .fill(row == 0 ? QingKeTheme.borderStrong : QingKeTheme.divider)
                    .frame(width: width, height: 1)
                    .offset(y: matrixY(forRow: row))
            }

            ForEach(0...weekdayCount, id: \.self) { column in
                Rectangle()
                    .fill(column == 0 ? QingKeTheme.borderStrong : QingKeTheme.divider)
                    .frame(width: 1, height: height)
                    .offset(x: column == 0
                            ? matrixTimeColumnWidth
                            : matrixTimeColumnWidth + CGFloat(column) * dayColumnWidth)
            }

            Text("TIME")
                .font(.terminal(9, weight: .black, relativeTo: .caption2))
                .tracking(0.8)
                .foregroundStyle(QingKeTheme.textSecondary)
                .frame(width: matrixTimeColumnWidth, height: matrixHeaderHeight)

            ForEach(0..<weekdayCount, id: \.self) { dayColumn in
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
                        .foregroundStyle(QingKeTheme.textSecondary)
                }
                .frame(width: matrixTimeColumnWidth, height: matrixRowHeight)
                .offset(y: matrixY(forRow: row))
            }

            if let scheduleBreak = matrix.scheduleBreak {
                HStack(spacing: 8) {
                    Text(scheduleBreak.title)
                        .font(.terminal(9, weight: .black, relativeTo: .caption2))
                        .tracking(1)
                    Rectangle()
                        .fill(QingKeTheme.textOnAccent.opacity(0.4))
                        .frame(height: 1)
                    Text("\(scheduleBreak.startTime)–\(scheduleBreak.endTime)")
                        .font(.terminal(8, weight: .bold, relativeTo: .caption2))
                }
                .foregroundStyle(QingKeTheme.textOnAccent)
                .padding(.horizontal, 8)
                .frame(width: width, height: matrixBreakHeight)
                .background(QingKeTheme.cyan.opacity(0.92))
                .overlay {
                    Rectangle().stroke(QingKeTheme.textOnAccent.opacity(0.7), lineWidth: 0.8)
                }
                .offset(
                    y: matrixHeaderHeight
                        + CGFloat(scheduleBreak.insertionRow) * matrixRowHeight
                )
                .accessibilityElement(children: .combine)
                .accessibilityLabel(
                    "\(scheduleBreak.title)，\(scheduleBreak.startTime)到\(scheduleBreak.endTime)"
                )
                .accessibilityIdentifier("week-matrix-break")
            }

            ForEach(matrix.items) { item in
                matrixCourseBlock(item, dayColumnWidth: dayColumnWidth)
            }
        }
        .frame(width: width, height: height)
        .overlay {
            Rectangle()
                .stroke(QingKeTheme.panelEdge, lineWidth: 1)
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
        let crossesBreak = matrixPresentation.scheduleBreak.map {
            item.startRow < $0.insertionRow
                && item.startRow + item.rowSpan > $0.insertionRow
        } ?? false
        let blockHeight = matrixRowHeight * CGFloat(item.rowSpan)
            + (crossesBreak ? matrixBreakHeight : 0)
            - 4

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
                        .foregroundStyle(QingKeTheme.textOnInverse.opacity(0.62))
                        .lineLimit(item.rowSpan > 1 ? 2 : 1)
                        .minimumScaleFactor(0.62)
                }
            }
            .foregroundStyle(QingKeTheme.textOnInverse)
            .padding(.leading, 8)
            .padding(.trailing, 4)
            .padding(.vertical, 5)
            .frame(
                width: laneWidth - 4,
                height: blockHeight,
                alignment: .topLeading
            )
            .background(QingKeTheme.inverseSurface)
            .overlay(alignment: .leading) {
                Rectangle()
                    .fill(accent)
                    .frame(width: 4)
            }
            .overlay {
                Rectangle()
                    .stroke(QingKeTheme.border, lineWidth: 0.8)
            }
        }
        .buttonStyle(.plain)
        .offset(
            x: matrixTimeColumnWidth
                + CGFloat(item.dayColumn) * dayColumnWidth
                + CGFloat(item.lane) * laneWidth
                + 2,
            y: matrixY(forRow: item.startRow) + 2
        )
        .accessibilityLabel(matrixCourseAccessibilityLabel(item))
        .accessibilityIdentifier("week-matrix-course-\(item.id)")
    }

    private var dayManifest: some View {
        VStack(alignment: .leading, spacing: 12) {
            TerminalSectionHeader(
                index: String(format: "%02d", selection.selectedDay),
                title: ScheduleDisplayText.weekdayNames[selection.selectedDay - 1],
                detail: selectedDayDetail
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
                .foregroundStyle(QingKeTheme.textSecondary)
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
                .fill(QingKeTheme.divider)
                .frame(width: 1, height: 55)
            VStack(alignment: .leading, spacing: 4) {
                Text(selectedDayPresentation.isNonTeachingDay ? "该日已设为停课" : "该日无课程安排")
                    .font(.headline)
                Text(selectedDayPresentation.isNonTeachingDay
                     ? "可在设置的教学日历中恢复上课。"
                     : "选择其他日期，或使用 ADD 添加课程。")
                    .font(.caption)
                    .foregroundStyle(QingKeTheme.textSecondary)
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
                    .frame(width: 1, height: 55)

                VStack(alignment: .leading, spacing: 5) {
                    if item.isConflicting {
                        TerminalStatusTag(text: "冲突", tint: QingKeTheme.signal)
                    } else {
                        Text(ScheduleDisplayText.periodRange(occurrence.schedule).uppercased())
                            .font(.terminal(9, weight: .black, relativeTo: .caption2))
                            .tracking(0.8)
                            .foregroundStyle(QingKeTheme.textSecondary)
                    }
                    Text(occurrence.course.name)
                        .font(.headline)
                        .lineLimit(2)
                    Text(courseDetails(occurrence))
                        .font(.caption)
                        .foregroundStyle(QingKeTheme.textSecondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(QingKeTheme.textPrimary)
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
        let status = day.isNonTeachingDay ? "，停课" : ""
        return "\(weekday)，\(date.formatted(.dateTime.month().day()))\(status)"
    }

    private func weekdayIndicatorColor(for day: WeekDayPresentation) -> Color {
        if day.isNonTeachingDay { return QingKeTheme.danger }
        return day.dayOfWeek == selection.selectedDay ? QingKeTheme.signal : .clear
    }

    private func weekdayForegroundColor(for day: WeekDayPresentation) -> Color {
        if day.dayOfWeek == selection.selectedDay { return QingKeTheme.textOnInverse }
        return day.isNonTeachingDay ? QingKeTheme.danger : QingKeTheme.textPrimary
    }

    private var selectedDayDetail: String {
        if selectedDayPresentation.isNonTeachingDay { return "OFF DAY" }
        if let source = selectedDayPresentation.scheduleSourceDayOfWeek,
           source != selectedDayPresentation.dayOfWeek {
            return "FOLLOW / \(ScheduleDisplayText.weekdayNames[source - 1])"
        }
        return "\(selectedDayPresentation.items.count) ENTRIES"
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
            ScheduleDisplayText.weekdayNames[item.dayColumn],
            ScheduleDisplayText.periodRange(schedule),
            item.occurrence.course.name,
            item.isConflicting ? "存在冲突" : nil,
        ].compactMap { $0 }
        return parts.joined(separator: "，")
    }

    private var matrixCanvasHeight: CGFloat {
        matrixHeaderHeight
            + matrixRowHeight * CGFloat(matrixPresentation.periods.count)
            + (matrixPresentation.scheduleBreak == nil ? 0 : matrixBreakHeight)
    }

    private func matrixY(forRow row: Int) -> CGFloat {
        let breakOffset: CGFloat
        if let scheduleBreak = matrixPresentation.scheduleBreak,
           row >= scheduleBreak.insertionRow {
            breakOffset = matrixBreakHeight
        } else {
            breakOffset = 0
        }
        return matrixHeaderHeight + CGFloat(row) * matrixRowHeight + breakOffset
    }

    private var matrixTimeColumnWidth: CGFloat { 44 }
    private var matrixHeaderHeight: CGFloat { 38 }
    private var matrixRowHeight: CGFloat { 68 }
    private var matrixBreakHeight: CGFloat { 30 }
}
