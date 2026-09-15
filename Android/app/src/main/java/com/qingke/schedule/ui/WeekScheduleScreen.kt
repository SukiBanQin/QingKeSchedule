package com.qingke.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingke.schedule.presentation.ScheduleDisplayText
import com.qingke.schedule.presentation.WeekMatrixItem
import com.qingke.schedule.presentation.WeekMatrixPresentation
import com.qingke.schedule.presentation.WeekSchedulePresentation
import com.qingke.schedule.state.ScheduleState
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WeekSignal = Color(0xFFFFD400)
private val WeekCyan = Color(0xFF28B9D6)
private val WeekInverse = Color(0xFF091113)
private fun weekForeground(dark: Boolean) = if (dark) Color(0xFFF1F5F4) else WeekInverse
private fun weekSecondary(dark: Boolean) = weekForeground(dark).copy(alpha = .68f)

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

    PullToRefreshBox(refreshing, ::refresh, modifier.fillMaxSize().testTag("week-refresh-container")) {
        Column(Modifier.statusBarsPadding().testTag("week-schedule")) {
            BrandHeader(dark, code = "MATRIX / 02", tag = "week-brand-header")
            Column(Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            WeekHeader(schedule, semester.name, selectedWeek, semester.totalWeeks, dark,
                previous = { selectedWeek--; followsCurrentWeek = false },
                next = { selectedWeek++; followsCurrentWeek = false },
                current = {
                    schedule.currentWeek?.let { selectedWeek = it; selectedDay = now.dayOfWeek.value; followsCurrentWeek = true; followsCurrentDay = true }
                },
            )
            WeekDayStrip(schedule, selectedDay, dark) { selectedDay = it; followsCurrentDay = false }
            Text("01 / WEEK MATRIX", color = WeekCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp).testTag("week-matrix-header"))
            WeekMatrix(matrix, dark, actions.openCourseAt)
            if (refreshing) Row(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("week-refresh-status")) {
                Text("刷新中", color = weekForeground(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); Text("SYNC / LOCAL", color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            WeekManifest(schedule, semester, selectedDay, dark, actions.openCourseAt)
            Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable private fun WeekHeader(schedule: WeekSchedulePresentation, semesterName: String, week: Int, totalWeeks: Int, dark: Boolean, previous: () -> Unit, next: () -> Unit, current: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SCHEDULE :// WEEK MATRIX", color = WeekCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.testTag("week-brand"))
            Text("%02d".format(week), color = weekForeground(dark), fontSize = 38.sp, fontWeight = FontWeight.Black)
            Text(semesterName, color = weekSecondary(dark), fontSize = 11.sp, modifier = Modifier.testTag("week-semester-name"))
        }
        Text("‹", color = if (week > 1) WeekSignal else weekSecondary(dark), fontSize = 28.sp, modifier = Modifier.width(48.dp).height(48.dp).then(if (week > 1) Modifier.clickable(onClick = previous) else Modifier).testTag("week-previous").semantics { contentDescription = if (week > 1) "上一周" else "已到第一周"; if (week <= 1) disabled() })
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("第 $week 周", color = weekForeground(dark), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("week-title"))
            Text(if (week % 2 == 0) "双周" else "单周", color = WeekCyan, fontSize = 10.sp)
        }
        Text("›", color = if (week < totalWeeks) WeekSignal else weekSecondary(dark), fontSize = 28.sp, modifier = Modifier.width(48.dp).height(48.dp).then(if (week < totalWeeks) Modifier.clickable(onClick = next) else Modifier).testTag("week-next").semantics { contentDescription = if (week < totalWeeks) "下一周" else "已到最后一周"; if (week >= totalWeeks) disabled() })
    }
    val canReturn = schedule.currentWeek != null
    Text(if (canReturn) "返回当前周" else "当前日期在学期外", color = if (canReturn) WeekCyan else weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp).then(if (canReturn) Modifier.clickable(onClick = current) else Modifier).testTag("week-current").semantics { contentDescription = if (canReturn) "返回当前周" else "当前日期在学期外"; if (!canReturn) disabled() })
}

@Composable private fun WeekDayStrip(schedule: WeekSchedulePresentation, selectedDay: Int, dark: Boolean, select: (Int) -> Unit) = Row(Modifier.fillMaxWidth().testTag("week-date-strip")) {
    schedule.days.forEach { day ->
        val selected = day.dayOfWeek == selectedDay
        Column(Modifier.weight(1f).padding(horizontal = 1.dp).background(if (selected) WeekInverse else Color.Transparent).clickable { select(day.dayOfWeek) }.testTag("week-day-${day.dayOfWeek}").semantics { contentDescription = "${ScheduleDisplayText.weekdayName(day.dayOfWeek)} ${day.date}${if (day.isNonTeachingDay) "，停课" else ""}" }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ScheduleDisplayText.weekdayName(day.dayOfWeek).removePrefix("周"), color = if (selected) WeekSignal else weekSecondary(dark), fontSize = 10.sp)
            Text(day.date.dayOfMonth.toString(), color = if (selected) Color.White else weekForeground(dark), fontWeight = FontWeight.Bold)
            Text(if (day.isNonTeachingDay) "休" else "·", color = if (day.isNonTeachingDay) WeekSignal else weekSecondary(dark), fontSize = 9.sp)
        }
    }
}

@Composable private fun WeekMatrix(matrix: WeekMatrixPresentation, dark: Boolean, open: (Int) -> Unit) = BoxWithConstraints(Modifier.fillMaxWidth().background(if (dark) Color(0xDD1A2527) else Color(0xDDFBFEFD)).border(1.dp, weekSecondary(dark).copy(alpha = .35f)).testTag("week-matrix")) {
    val timeWidth = 44.dp
    val columnWidth = (maxWidth - timeWidth) / 7
    val rowHeight = 48.dp
    val breakHeight = 22.dp
    val insertion = matrix.scheduleBreak?.insertionRow
    fun rowY(index: Int) = rowHeight * index + if (insertion != null && index >= insertion) breakHeight else 0.dp
    fun itemHeight(item: WeekMatrixItem) = rowHeight * item.rowSpan + if (insertion != null && item.startRow < insertion && item.startRow + item.rowSpan > insertion) breakHeight else 0.dp
    val contentHeight = rowHeight * matrix.periods.size + if (insertion == null) 0.dp else breakHeight
    Box(Modifier.fillMaxWidth().height(contentHeight + 30.dp)) {
        Text("TIME", color = WeekCyan, fontFamily = FontFamily.Monospace, fontSize = 8.sp, modifier = Modifier.width(timeWidth).padding(4.dp).testTag("week-time-header"))
        ScheduleDisplayText.weekdayNames.forEachIndexed { index, name -> Text(name, color = weekForeground(dark), fontSize = 9.sp, modifier = Modifier.width(columnWidth).offset(x = timeWidth + columnWidth * index).padding(4.dp).testTag("week-column-header-${index + 1}")) }
        matrix.periods.forEachIndexed { index, period ->
            Text("%02d\n%s".format(period.number, period.startTime), color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 8.sp, modifier = Modifier.width(timeWidth).height(rowHeight).offset(y = 30.dp + rowY(index)).padding(3.dp).testTag("week-period-${period.number}"))
            Box(Modifier.fillMaxWidth().height(1.dp).offset(y = 30.dp + rowY(index)).background(weekSecondary(dark).copy(alpha = .22f)))
        }
        if (insertion != null) Text(matrix.scheduleBreak!!.title, color = WeekCyan, fontSize = 8.sp, modifier = Modifier.fillMaxWidth().height(breakHeight).offset(y = 30.dp + rowY(insertion) - breakHeight).background(WeekInverse.copy(alpha = .78f)).padding(start = timeWidth).testTag("week-lunch-break"))
        matrix.items.forEach { item ->
            val key = item.occurrence.key
            val laneWidth = columnWidth / item.laneCount
            val x = timeWidth + columnWidth * item.dayColumn + laneWidth * item.lane
            Box(Modifier.width(laneWidth).height(itemHeight(item)).offset(x = x, y = 30.dp + rowY(item.startRow)).padding(2.dp).background(WeekInverse).border(2.dp, if (item.isConflicting) Color(0xFFDF695F) else courseColor(item.occurrence.course.color)).clickable { open(key.courseIndex) }.testTag("week-item-${item.id}").semantics { contentDescription = "${item.occurrence.course.name}，${ScheduleDisplayText.periodRange(item.occurrence.schedule)}，lane ${item.lane + 1}/${item.laneCount}${if (item.isConflicting) "，冲突" else ""}" }) {
                Column(Modifier.padding(4.dp)) { Text(item.occurrence.course.name, color = Color.White, fontSize = 10.sp, maxLines = 2); Text(if (item.isConflicting) "CONFLICT" else ScheduleDisplayText.periodRange(item.occurrence.schedule), color = if (item.isConflicting) Color(0xFFFF8A80) else courseColor(item.occurrence.course.color), fontFamily = FontFamily.Monospace, fontSize = 7.sp) }
            }
        }
    }
}

@Composable private fun WeekManifest(schedule: WeekSchedulePresentation, semester: com.qingke.schedule.domain.Semester, selectedDay: Int, dark: Boolean, open: (Int) -> Unit) {
    val day = schedule.days.first { it.dayOfWeek == selectedDay }
    Text("02 / DAY MANIFEST", color = WeekCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp).testTag("week-manifest-header"))
    Text("${ScheduleDisplayText.weekdayName(selectedDay)} · ${day.date}", color = weekForeground(dark), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("selected-day-title"))
    if (day.isNonTeachingDay || day.items.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 7.dp).background(if (dark) Color(0xDD1A2527) else Color(0xDDFBFEFD)).border(1.dp, WeekCyan).padding(14.dp).testTag("week-empty")) { Text("STANDBY", color = WeekCyan, fontFamily = FontFamily.Monospace); Text(if (day.isNonTeachingDay) "停课日" else "本日无课", color = weekForeground(dark), fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (day.isNonTeachingDay) "该日不显示课程安排。" else "享受空闲时间吧。", color = weekSecondary(dark)); Text("END OF DAY", color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp)) }
    } else day.items.forEach { item ->
        val key = item.occurrence.key
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).background(if (dark) Color(0xDD1A2527) else Color(0xDDFBFEFD)).border(1.dp, weekSecondary(dark).copy(alpha = .35f)).clickable { open(key.courseIndex) }.testTag("week-list-${key.courseIndex}-${key.scheduleIndex}").semantics { contentDescription = "${item.occurrence.course.name}，${ScheduleDisplayText.periodRange(item.occurrence.schedule)}${if (item.isConflicting) "，冲突" else ""}" }) { Box(Modifier.width(4.dp).height(60.dp).background(if (item.isConflicting) Color(0xFFDF695F) else courseColor(item.occurrence.course.color))); Column(Modifier.padding(10.dp).weight(1f)) { Text(item.occurrence.course.name, color = weekForeground(dark), fontWeight = FontWeight.Bold); Text("${ScheduleDisplayText.periodRange(item.occurrence.schedule)} · ${ScheduleDisplayText.compactCourseDetails(item.occurrence.course, item.occurrence.schedule)}", color = weekSecondary(dark), fontSize = 11.sp); if (item.isConflicting) Text("CONFLICT", color = Color(0xFFDF695F), fontFamily = FontFamily.Monospace, fontSize = 9.sp) } }
    }
    if (day.items.isNotEmpty()) Text("END OF MANIFEST // ${ScheduleDisplayText.timeRange(day.items.last().occurrence.schedule, semester).substringAfter('–')}", color = weekSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("week-end-marker"))
}
