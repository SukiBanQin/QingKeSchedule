package com.qingke.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingke.schedule.domain.Semester
import com.qingke.schedule.presentation.ScheduleDisplayText
import com.qingke.schedule.presentation.WeekCourseItem
import com.qingke.schedule.presentation.WeekDayPresentation
import com.qingke.schedule.presentation.WeekMatrixItem
import com.qingke.schedule.presentation.WeekMatrixPresentation
import com.qingke.schedule.presentation.WeekSchedulePresentation
import com.qingke.schedule.state.ScheduleState
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WeekSignal = Color(0xFFFFD400)
private val WeekArrowWidth = 44.dp
private val WeekOnInverse = Color(0xFFF1F5F4)
private val WeekOnAccent = Color(0xFF071013)

private fun weekForeground(dark: Boolean) = if (dark) Color(0xFFF1F5F4) else Color(0xFF091113)
private fun weekSecondary(dark: Boolean) = weekForeground(dark).copy(alpha = .68f)
private fun weekBorder(dark: Boolean) = weekForeground(dark).copy(alpha = if (dark) .22f else .20f)
private fun weekBorderStrong(dark: Boolean) = weekForeground(dark).copy(alpha = if (dark) .32f else .34f)
private fun weekPanelEdge(dark: Boolean) = if (dark) WeekOnInverse.copy(alpha = .22f) else Color.White.copy(alpha = .68f)
private fun weekCyan(dark: Boolean) = if (dark) Color(0xFF35C8E5) else Color(0xFF28B9D6)
private fun weekDanger(dark: Boolean) = if (dark) Color(0xFFFF665C) else Color(0xFFE65A4F)
private fun weekSurface(dark: Boolean) = if (dark) Color(0xFF111B1E) else Color(0xFFF1F5F4)
private fun weekInverse(dark: Boolean) = if (dark) Color(0xFF182427) else Color(0xFF091113)

/** Renders P3-01's presentation only; scheduling and lane rules deliberately stay out of Compose. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScheduleScreen(
    state: ScheduleState,
    now: LocalDateTime,
    actions: QingKeAppActions,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val semester = state.data.semester ?: return
    var selectedWeek by rememberSaveable { mutableIntStateOf(WeekSchedulePresentation.initialWeek(semester, now)) }
    var selectedDay by rememberSaveable { mutableIntStateOf(now.dayOfWeek.value) }
    var followsCurrentWeek by rememberSaveable { mutableStateOf(true) }
    var followsCurrentDay by rememberSaveable { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentWeek = WeekSchedulePresentation.initialWeek(semester, now)

    LaunchedEffect(currentWeek, now.toLocalDate(), followsCurrentWeek, followsCurrentDay) {
        if (followsCurrentWeek) selectedWeek = currentWeek
        if (followsCurrentDay) selectedDay = now.dayOfWeek.value
    }
    val schedule = remember(semester, state.data.courses, state.preferences.academicCalendar, selectedWeek, now) {
        WeekSchedulePresentation.create(selectedWeek, semester, state.data.courses, now, state.preferences.academicCalendar)
    }
    val matrix = remember(schedule, state.preferences.academicCalendar) {
        WeekMatrixPresentation.create(semester, schedule.days, state.preferences.academicCalendar)
    }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        actions.refreshTime()
        scope.launch { delay(450); refreshing = false }
    }
    val selectedDayPresentation = schedule.days.first { it.dayOfWeek == selectedDay }

    PullToRefreshBox(refreshing, ::refresh, modifier.fillMaxSize().testTag("week-refresh-container")) {
        Column(Modifier.statusBarsPadding().testTag("week-schedule")) {
            BrandHeader(dark, code = "MATRIX / 02", tag = "week-brand-header")
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
                WeekScreenTitle(selectedWeek, dark)
                Spacer(Modifier.height(14.dp))
                WeekControls(
                    semesterName = semester.name,
                    week = selectedWeek,
                    totalWeeks = semester.totalWeeks,
                    currentWeek = schedule.currentWeek,
                    followsCurrentWeek = followsCurrentWeek,
                    dark = dark,
                    previous = { if (selectedWeek > 1) { selectedWeek--; followsCurrentWeek = false } },
                    next = { if (selectedWeek < semester.totalWeeks) { selectedWeek++; followsCurrentWeek = false } },
                    current = {
                        schedule.currentWeek?.let {
                            selectedWeek = it
                            selectedDay = now.dayOfWeek.value
                            followsCurrentWeek = true
                            followsCurrentDay = true
                        }
                    },
                )
                Spacer(Modifier.height(14.dp))
                WeekDayStrip(schedule, selectedDay, dark) { selectedDay = it; followsCurrentDay = false }
                WeekSectionHeader(
                    index = "05",
                    title = "周视图",
                    detail = ScheduleDisplayText.weekMatrixSummary(matrix.periods.size),
                    dark = dark,
                    tag = "week-matrix-header",
                    indexTag = "week-matrix-header-index",
                    titleTag = "week-view-title",
                    detailTag = "week-matrix-summary",
                )
                Spacer(Modifier.height(10.dp))
                WeekMatrix(matrix, dark, actions.openCourseAt)
                if (refreshing) Row(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("week-refresh-status")) {
                    Text("刷新中", color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f)); Text("SYNC / LOCAL", color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
                WeekSectionHeader(
                    index = "%02d".format(selectedDay),
                    title = ScheduleDisplayText.weekdayName(selectedDay),
                    detail = dayManifestDetail(selectedDayPresentation),
                    dark = dark,
                    tag = "week-manifest-header",
                    titleTag = "selected-day-title",
                    detailTag = "week-manifest-detail",
                )
                WeekManifest(selectedDayPresentation, semester, dark, actions.openCourseAt)
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

private fun dayManifestDetail(day: WeekDayPresentation): String = when {
    day.isNonTeachingDay -> "OFF DAY"
    day.scheduleSourceDayOfWeek != null && day.scheduleSourceDayOfWeek != day.dayOfWeek ->
        "FOLLOW / " + ScheduleDisplayText.weekdayName(day.scheduleSourceDayOfWeek)
    else -> day.items.size.toString() + " ENTRIES"
}

@Composable private fun WeekScreenTitle(week: Int, dark: Boolean) = Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Bottom,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            "SCHEDULE :// WEEK MATRIX",
            color = WeekOnInverse,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
            modifier = Modifier.background(weekInverse(dark)).padding(horizontal = 8.dp, vertical = 5.dp).testTag("week-brand"),
        )
        Text("课表", color = weekForeground(dark), fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text("WEEK", color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.1.sp)
        Text(
            "%02d".format(week),
            color = weekForeground(dark),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            fontSize = 42.sp,
            lineHeight = 44.sp,
            modifier = Modifier.testTag("week-title").semantics { contentDescription = "第 " + "%02d".format(week) + " 教学周" },
        )
    }
}

@Composable private fun WeekControls(
    semesterName: String,
    week: Int,
    totalWeeks: Int,
    currentWeek: Int?,
    followsCurrentWeek: Boolean,
    dark: Boolean,
    previous: () -> Unit,
    next: () -> Unit,
    current: () -> Unit,
) {
    val canReturn = currentWeek != null && (currentWeek != week || !followsCurrentWeek)
    Box(
        Modifier.fillMaxWidth()
            .background(weekSurface(dark))
            .drawBehind {
                val stroke = 1.dp.toPx()
                val edge = WeekArrowWidth.toPx()
                drawRect(weekBorder(dark), topLeft = Offset(edge, 0f), size = Size(stroke, size.height))
                drawRect(weekBorder(dark), topLeft = Offset(size.width - edge, 0f), size = Size(stroke, size.height))
            }
            .border(1.dp, weekBorder(dark))
            .testTag("week-controls"),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeekArrow("‹", "上一周", "week-previous", week > 1, dark, previous)
            Column(
                Modifier.weight(1f).clickable(enabled = canReturn, onClick = current).testTag("week-current")
                    .semantics { contentDescription = if (canReturn) "第 " + week + " 周，点按返回本周" else "第 " + week + " 周"; if (!canReturn) disabled() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    semesterName,
                    color = weekSecondary(dark),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("week-semester-name"),
                )
                Text(
                    "第 " + "%02d".format(week) + " 教学周",
                    color = weekForeground(dark),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    maxLines = 1,
                    modifier = Modifier.testTag("week-teaching-week"),
                )
                Text(
                    if (week % 2 == 0) "EVEN WEEK" else "ODD WEEK",
                    color = weekCyan(dark),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.testTag("week-parity"),
                )
            }
            WeekArrow("›", "下一周", "week-next", week < totalWeeks, dark, next)
        }
    }
}

@Composable private fun WeekArrow(symbol: String, label: String, tag: String, enabled: Boolean, dark: Boolean, click: () -> Unit) = Box(
    Modifier.width(WeekArrowWidth).heightIn(min = 64.dp).clickable(enabled = enabled, onClick = click).testTag(tag)
        .semantics { contentDescription = label; if (!enabled) disabled() },
    contentAlignment = Alignment.Center,
) { Text(symbol, color = if (enabled) weekCyan(dark) else weekSecondary(dark), fontSize = 26.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black) }

@Composable private fun WeekDayStrip(schedule: WeekSchedulePresentation, selectedDay: Int, dark: Boolean, select: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth().testTag("week-date-strip"),
) {
    schedule.days.forEach { day ->
        val selected = day.dayOfWeek == selectedDay
        val indicator = when {
            day.isNonTeachingDay -> weekDanger(dark)
            selected -> WeekSignal
            else -> Color.Transparent
        }
        Column(
            Modifier.weight(1f).height(64.dp)
                .background(if (selected) weekInverse(dark) else Color.Transparent)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawRect(weekBorderStrong(dark), size = Size(size.width, stroke))
                    drawRect(weekBorderStrong(dark), topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
                    if (day.dayOfWeek > 1) drawRect(weekBorder(dark), size = Size(stroke, size.height))
                }
                .clickable { select(day.dayOfWeek) }
                .testTag("week-day-" + day.dayOfWeek)
                .semantics {
                    contentDescription = buildString {
                        append(ScheduleDisplayText.weekdayName(day.dayOfWeek))
                        append("，")
                        append(day.date.monthValue.toString() + "月" + day.date.dayOfMonth + "日")
                        if (day.isNonTeachingDay) append("，停课")
                        append(if (selected) "，已选择" else "，未选择")
                    }
                }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                ScheduleDisplayText.weekdayName(day.dayOfWeek).removePrefix("周"),
                color = if (selected) WeekOnInverse else if (day.isNonTeachingDay) weekDanger(dark) else weekForeground(dark),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
            )
            Text(
                "%02d".format(day.date.dayOfMonth),
                color = if (selected) WeekOnInverse else weekForeground(dark),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                lineHeight = 19.sp,
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(3.dp).background(indicator))
        }
    }
}

@Composable private fun WeekSectionHeader(
    index: String,
    title: String,
    detail: String,
    dark: Boolean,
    tag: String,
    indexTag: String? = null,
    titleTag: String? = null,
    detailTag: String? = null,
) = Row(
    Modifier.fillMaxWidth().padding(top = 14.dp)
        .drawBehind { drawRect(weekBorderStrong(dark), topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }
        .padding(bottom = 7.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(
        index,
        color = weekCyan(dark),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = 13.sp,
        modifier = if (indexTag == null) Modifier else Modifier.testTag(indexTag),
    )
    Spacer(Modifier.width(9.dp))
    Text(
        title,
        color = weekForeground(dark),
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = if (titleTag == null) Modifier else Modifier.testTag(titleTag),
    )
    Spacer(Modifier.weight(1f))
    Text(
        detail.uppercase(),
        color = weekSecondary(dark),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        modifier = if (detailTag == null) Modifier else Modifier.testTag(detailTag),
    )
}

@Composable private fun WeekMatrix(matrix: WeekMatrixPresentation, dark: Boolean, open: (Int) -> Unit) = BoxWithConstraints(
    Modifier.fillMaxWidth().background(weekSurface(dark)).border(1.dp, weekPanelEdge(dark)).testTag("week-matrix"),
) {
    val timeWidth = 44.dp
    val headerHeight = 38.dp
    val rowHeight = 68.dp
    val breakHeight = 30.dp
    val columnWidth = (maxWidth - timeWidth) / 7
    val insertion = matrix.scheduleBreak?.insertionRow
    fun rowY(index: Int) = headerHeight + rowHeight * index + if (insertion != null && index >= insertion) breakHeight else 0.dp
    fun breakY() = headerHeight + rowHeight * (insertion ?: 0)
    fun itemHeight(item: WeekMatrixItem) = rowHeight * item.rowSpan + if (insertion != null && item.startRow < insertion && item.startRow + item.rowSpan > insertion) breakHeight else 0.dp
    val contentHeight = rowY(matrix.periods.size)
    Box(Modifier.fillMaxWidth().height(contentHeight).testTag("week-matrix-canvas")) {
        repeat(matrix.periods.size + 1) { row ->
            Box(
                Modifier.fillMaxWidth().height(1.dp).offset(y = rowY(row))
                    .background(if (row == 0) weekBorderStrong(dark) else weekBorder(dark)),
            )
        }
        repeat(8) { column ->
            Box(
                Modifier.width(1.dp).height(contentHeight).offset(x = timeWidth + columnWidth * column)
                    .background(if (column == 0) weekBorderStrong(dark) else weekBorder(dark)),
            )
        }
        Box(Modifier.width(timeWidth).height(headerHeight), contentAlignment = Alignment.Center) {
            Text("TIME", color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.8.sp, modifier = Modifier.testTag("week-time-header"))
        }
        ScheduleDisplayText.weekdayNames.forEachIndexed { index, name ->
            Box(Modifier.width(columnWidth).height(headerHeight).offset(x = timeWidth + columnWidth * index), contentAlignment = Alignment.Center) {
                Text(name, color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, maxLines = 1, modifier = Modifier.testTag("week-column-header-" + (index + 1)))
            }
        }
        matrix.periods.forEachIndexed { index, period ->
            Column(
                Modifier.width(timeWidth).height(rowHeight).offset(y = rowY(index)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("%02d".format(period.number), color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.testTag("week-period-" + period.number))
                Text(period.startTime, color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 7.sp, modifier = Modifier.testTag("week-period-" + period.number + "-start"))
            }
        }
        matrix.scheduleBreak?.let { scheduleBreak ->
            Row(
                Modifier.fillMaxWidth().height(breakHeight).offset(y = breakY())
                    .background(weekCyan(dark).copy(alpha = .92f))
                    .drawBehind {
                        val stroke = .8.dp.toPx()
                        drawRect(WeekOnInverse.copy(alpha = .7f), size = Size(size.width, stroke))
                        drawRect(WeekOnInverse.copy(alpha = .7f), topLeft = Offset(0f, size.height - stroke), size = Size(size.width, stroke))
                    }
                    .padding(horizontal = 8.dp)
                    .testTag("week-lunch-break")
                    .semantics { contentDescription = scheduleBreak.title + "，" + scheduleBreak.startTime + "到" + scheduleBreak.endTime },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(scheduleBreak.title, color = WeekOnAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.testTag("week-lunch-break-title"))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).height(1.dp).background(WeekOnAccent.copy(alpha = .4f)))
                Spacer(Modifier.width(8.dp))
                Text(scheduleBreak.startTime + "–" + scheduleBreak.endTime, color = WeekOnAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
        }
        matrix.items.forEach { item ->
            val key = item.occurrence.key
            val laneWidth = columnWidth / item.laneCount
            val accent = if (item.isConflicting) WeekSignal else courseColor(item.occurrence.course.color)
            val details = ScheduleDisplayText.compactCourseDetails(item.occurrence.course, item.occurrence.schedule)
            Box(
                Modifier.width(laneWidth).height(itemHeight(item))
                    .offset(x = timeWidth + columnWidth * item.dayColumn + laneWidth * item.lane, y = rowY(item.startRow))
                    .padding(2.dp)
                    .background(weekInverse(dark))
                    .drawBehind { drawRect(accent, size = Size(4.dp.toPx(), size.height)) }
                    .border(1.dp, weekBorder(dark))
                    .clickable { open(key.courseIndex) }
                    .testTag("week-item-" + item.id)
                    .semantics {
                        contentDescription = buildString {
                            append(item.occurrence.course.name)
                            append("，")
                            append(ScheduleDisplayText.periodRange(item.occurrence.schedule))
                            append("，lane ")
                            append((item.lane + 1).toString())
                            append("/")
                            append(item.laneCount.toString())
                            if (item.isConflicting) append("，冲突")
                        }
                    },
            ) {
                Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    if (item.isConflicting) Text("CONFLICT", color = WeekSignal, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 7.sp)
                    Text(
                        item.occurrence.course.name,
                        color = WeekOnInverse,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = if (item.rowSpan > 1) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (details.isNotEmpty()) Text(
                        details,
                        color = WeekOnInverse.copy(alpha = .62f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        maxLines = if (item.rowSpan > 1) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable private fun WeekManifest(day: WeekDayPresentation, semester: Semester, dark: Boolean, open: (Int) -> Unit) {
    if (day.isNonTeachingDay || day.items.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().background(weekSurface(dark)).border(1.dp, weekCyan(dark)).padding(14.dp).testTag("week-empty"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("00", color = weekCyan(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 42.sp, lineHeight = 44.sp)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(55.dp).background(weekBorder(dark)))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (day.isNonTeachingDay) "该日已设为停课" else "该日无课程安排", color = weekForeground(dark), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (day.isNonTeachingDay) "可在设置的教学日历中恢复上课。" else "选择其他日期，或使用 ADD 添加课程。",
                    color = weekSecondary(dark),
                    fontSize = 11.sp,
                )
            }
        }
    } else day.items.forEachIndexed { index, item -> WeekManifestCard(item, index, semester, dark, open) }
    if (day.items.isNotEmpty()) Text(
        "END OF MANIFEST // " + ScheduleDisplayText.timeRange(day.items.last().occurrence.schedule, semester).substringAfter('–'),
        color = weekSecondary(dark),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("week-end-marker"),
    )
}

@Composable private fun WeekManifestCard(item: WeekCourseItem, index: Int, semester: Semester, dark: Boolean, open: (Int) -> Unit) {
    val occurrence = item.occurrence
    val key = occurrence.key
    val accent = if (item.isConflicting) WeekSignal else courseColor(occurrence.course.color)
    val range = ScheduleDisplayText.timeRange(occurrence.schedule, semester).split("–")
    val details = ScheduleDisplayText.compactCourseDetails(occurrence.course, occurrence.schedule)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 94.dp)
            .background(weekSurface(dark))
            .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
            .border(1.dp, weekBorder(dark))
            .clickable { open(key.courseIndex) }
            .padding(horizontal = 13.dp, vertical = 12.dp)
            .testTag("week-list-" + key.courseIndex + "-" + key.scheduleIndex)
            .semantics {
                contentDescription = buildString {
                    append(occurrence.course.name)
                    append("，")
                    append(ScheduleDisplayText.periodRange(occurrence.schedule))
                    if (item.isConflicting) append("，冲突")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(index + 1),
            color = weekSecondary(dark),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            modifier = Modifier.width(24.dp).graphicsLayer { rotationZ = -90f },
        )
        Column(Modifier.width(66.dp)) {
            Text(range.firstOrNull().orEmpty(), color = weekForeground(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp, maxLines = 1)
            Text(range.getOrNull(1).orEmpty(), color = weekSecondary(dark), fontFamily = TodayVisualSpec.condensed, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(13.dp))
        Box(Modifier.width(1.dp).height(55.dp).background(weekBorder(dark)))
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            if (item.isConflicting) {
                Text("冲突", color = WeekOnAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.background(WeekSignal).padding(horizontal = 8.dp, vertical = 4.dp))
            } else {
                Text(ScheduleDisplayText.periodRange(occurrence.schedule).uppercase(), color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 0.8.sp)
            }
            Text(occurrence.course.name, color = weekForeground(dark), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Text(details.ifEmpty { ScheduleDisplayText.periodRange(occurrence.schedule) }, color = weekSecondary(dark), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        }
        Text("›", color = weekSecondary(dark), fontSize = 22.sp)
    }
}
