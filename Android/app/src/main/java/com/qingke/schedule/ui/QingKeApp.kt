package com.qingke.schedule.ui

import android.app.Activity
import android.app.TimePickerDialog
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.qingke.schedule.domain.CourseOccurrence
import com.qingke.schedule.domain.CourseStatus
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.presentation.ScheduleDisplayText
import com.qingke.schedule.presentation.TodayCourseItem
import com.qingke.schedule.presentation.TodaySchedulePresentation
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.ScheduleViewModel
import com.qingke.schedule.viewmodel.SemesterFormState
import com.qingke.schedule.viewmodel.CourseEditorState
import com.qingke.schedule.viewmodel.CourseEditorMode
import com.qingke.schedule.viewmodel.CourseEditorConfirmation
import com.qingke.schedule.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val SignalYellow = Color(0xFFFFD400)
private val QingKeCyan = Color(0xFF28B9D6)
private val InverseSurface = Color(0xFF091113)
private val DarkSurface = Color(0xFF182427)
private val LightSurface = Color(0xFFF1F5F4)
private val Danger = Color(0xFFE65A4F)
private val TerminalShape = RoundedCornerShape(0.dp)
private enum class TerminalSurfaceLevel { STANDARD, ELEVATED }

/** Android's system condensed face is the platform equivalent of iOS's Avenir Next Condensed; missing glyphs use system fallback. */
internal object TodayVisualSpec {
    val condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    val dayColumnWidth = 106.dp
    val dayNumberSize = 74.sp
    val featuredTimeColumnWidth = 82.dp
    val featuredStartTimeSize = 32.sp
    val featuredEndTimeSize = 13.sp
    val sequenceTimeColumnWidth = 66.dp
    val sequenceStartTimeSize = 24.sp
    val sequenceEndTimeSize = 11.sp
    val featuredCourseNameSize = 20.sp
    val sequenceCourseNameSize = 17.sp
}

/** Accept only the persisted six-digit RGB format; malformed legacy data gets the brand fallback. */
internal fun courseColor(raw: String): Color {
    if (!raw.matches(Regex("^#[0-9A-Fa-f]{6}$"))) return QingKeCyan
    return Color(0xFF000000L or raw.substring(1).toLong(16))
}

internal fun courseColorLabel(raw: String): String =
    if (raw.matches(Regex("^#[0-9A-Fa-f]{6}$"))) raw.uppercase() else "#28B9D6"

/** The testable root has callbacks only; production still owns all lifecycle-aware collection. */
data class QingKeAppActions(
    val retryLoad: () -> Unit = {},
    val dismissError: () -> Unit = {},
    val selectTab: (MainTab) -> Unit = {},
    val updateName: (String) -> Unit = {},
    val updateStartDate: (LocalDate) -> Unit = {},
    val updateTotalWeeks: (Int) -> Unit = {},
    val updatePeriodStart: (String, LocalTime) -> Unit = { _, _ -> },
    val updatePeriodEnd: (String, LocalTime) -> Unit = { _, _ -> },
    val addPeriod: () -> Unit = {},
    val removePeriod: (String) -> Unit = {},
    val togglePeriods: () -> Unit = {},
    val saveSemester: () -> Unit = {},
    val refreshTime: () -> Unit = {},
    val openAddCourse: () -> Unit = {}, val openNewCourse: () -> Unit = {},
    val openCourseAt: (Int) -> Unit = {}, val appendCourseAt: (Int) -> Unit = {},
    val closeCourseEditor: () -> Unit = {}, val discardCourseEditor: () -> Unit = {},
    val editorBack: () -> Unit = {},
    val dismissCourseConfirmation: () -> Unit = {}, val deleteCourse: () -> Unit = {},
    val confirmDeleteCourse: () -> Unit = {}, val saveCourse: () -> Unit = {},
    val confirmSaveDespiteConflicts: () -> Unit = {},
    val updateCourseName: (String) -> Unit = {}, val updateCourseTeacher: (String) -> Unit = {},
    val updateCourseColor: (String) -> Unit = {}, val updateCourseClassroom: (String, String) -> Unit = { _, _ -> },
    val updateCourseColorInput: (String) -> Unit = {}, val showColorDialog: () -> Unit = {}, val dismissColorDialog: () -> Unit = {},
    val updateCourseDay: (String, Int) -> Unit = { _, _ -> }, val updateCourseStartPeriod: (String, Int) -> Unit = { _, _ -> },
    val updateCourseEndPeriod: (String, Int) -> Unit = { _, _ -> }, val updateCourseStartWeek: (String, Int) -> Unit = { _, _ -> },
    val updateCourseEndWeek: (String, Int) -> Unit = { _, _ -> }, val updateCourseRepeat: (String, RepeatRule) -> Unit = { _, _ -> },
    val addCourseSchedule: () -> Unit = {}, val removeCourseSchedule: (String) -> Unit = {},
)

@Composable
fun QingKeApp(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val courseSuccess by viewModel.courseSuccess.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.refreshCurrentTime()
            while (isActive) {
                delay(1_000)
                viewModel.refreshCurrentTime()
            }
        }
    }
    QingKeAppContent(
        state, form, selectedTab,
        QingKeAppActions(
            retryLoad = viewModel::retryLoad, dismissError = viewModel::dismissError,
            selectTab = viewModel::selectTab, updateName = viewModel::updateName,
            updateStartDate = viewModel::updateStartDate, updateTotalWeeks = viewModel::updateTotalWeeks,
            updatePeriodStart = viewModel::updatePeriodStart, updatePeriodEnd = viewModel::updatePeriodEnd,
            addPeriod = viewModel::addPeriod, removePeriod = viewModel::removePeriod,
            togglePeriods = viewModel::togglePeriods, saveSemester = viewModel::saveSemester,
            refreshTime = viewModel::refreshCurrentTime,
            openAddCourse = viewModel::openAddCourse, openNewCourse = viewModel::openNewCourse,
            openCourseAt = viewModel::openCourseAt, appendCourseAt = viewModel::appendCourseAt,
            closeCourseEditor = viewModel::requestCloseEditor, discardCourseEditor = viewModel::confirmDiscardEditor,
            editorBack = viewModel::onEditorBack,
            dismissCourseConfirmation = viewModel::dismissEditorConfirmation, deleteCourse = viewModel::requestDeleteCourse,
            confirmDeleteCourse = viewModel::confirmDeleteCourse, saveCourse = viewModel::saveCourse,
            confirmSaveDespiteConflicts = viewModel::confirmSaveDespiteConflicts,
            updateCourseName = viewModel::updateCourseName, updateCourseTeacher = viewModel::updateCourseTeacher,
            updateCourseColor = viewModel::updateCourseColor, updateCourseClassroom = viewModel::updateCourseScheduleClassroom,
            updateCourseColorInput = viewModel::updateCourseColorInput, showColorDialog = viewModel::showColorDialog, dismissColorDialog = viewModel::dismissColorDialog,
            updateCourseDay = viewModel::updateCourseScheduleDay, updateCourseStartPeriod = viewModel::updateCourseScheduleStartPeriod,
            updateCourseEndPeriod = viewModel::updateCourseScheduleEndPeriod, updateCourseStartWeek = viewModel::updateCourseScheduleStartWeek,
            updateCourseEndWeek = viewModel::updateCourseScheduleEndWeek, updateCourseRepeat = viewModel::updateCourseScheduleRepeat,
            addCourseSchedule = viewModel::addCourseSchedule, removeCourseSchedule = viewModel::removeCourseSchedule,
        ),
        currentTime, editor, courseSuccess, viewModel::consumeCourseSuccess,
    )
}

@Composable
fun QingKeAppContent(
    state: ScheduleState,
    form: SemesterFormState?,
    selectedTab: MainTab,
    actions: QingKeAppActions,
    currentTime: LocalDateTime = LocalDateTime.now(),
    editor: CourseEditorState? = null,
    courseSuccess: String? = null,
    consumeCourseSuccess: () -> Unit = {},
) {
    val dark = when (state.preferences.appearanceMode) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) darkColorScheme(surface = DarkSurface, primary = SignalYellow, secondary = QingKeCyan, error = Danger)
    else lightColorScheme(surface = LightSurface, primary = InverseSurface, secondary = QingKeCyan, error = Danger)
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp), medium = RoundedCornerShape(0.dp), large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp)),
    ) {
        SystemBars(darkTop = dark || state.needsOnboarding || state.loadStatus == LoadStatus.NOT_LOADED || state.loadStatus == LoadStatus.LOADING)
        when (state.loadStatus) {
            LoadStatus.NOT_LOADED, LoadStatus.LOADING -> LoadingScreen()
            LoadStatus.FAILED -> LoadErrorScreen(state.error.orEmpty(), actions.retryLoad)
            LoadStatus.READY -> if (state.needsOnboarding) form?.let { OnboardingScreen(it, state.isSaving, actions, dark) } ?: LoadingScreen()
            else MainShell(state, selectedTab, currentTime, actions, courseSuccess = if (editor == null) courseSuccess else null, consumeCourseSuccess = consumeCourseSuccess)
        }
        editor?.let { CourseEditorOverlay(it, state.data.semester, state.data.courses, dark, actions) }
        state.error?.let { message -> if (state.loadStatus == LoadStatus.READY) ErrorDialog(message, actions.dismissError) }
    }
}

@Composable private fun CourseSuccessNotice(message: String, dark: Boolean, consume: () -> Unit) {
    LaunchedEffect(message) { delay(2_600); consume() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).terminalPanel(dark, SignalYellow, TerminalSurfaceLevel.ELEVATED)
            .padding(horizontal = 14.dp, vertical = 8.dp).testTag("course-success-notice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(SignalYellow, androidx.compose.foundation.shape.CircleShape).testTag("course-success-dot"))
        Spacer(Modifier.width(10.dp))
        Text(message, color = terminalText(dark), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("course-success-message"))
        Text("✓", color = SignalYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("course-success-check"))
    }
}

@Composable private fun SystemBars(darkTop: Boolean) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        activity.window.apply {
            statusBarColor = (if (darkTop) InverseSurface else LightSurface).toArgb()
            navigationBarColor = InverseSurface.toArgb()
        }
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTop
            isAppearanceLightNavigationBars = false
        }
    }
}

@Composable private fun LoadingScreen() = Column(
    Modifier.fillMaxSize().background(InverseSurface).statusBarsPadding().navigationBarsPadding().testTag("app-loading"),
    Arrangement.Center, Alignment.CenterHorizontally,
) { Text("QINGKE", color = SignalYellow, fontWeight = FontWeight.Black); Text("正在读取课表…", color = Color.White) }

@Composable private fun LoadErrorScreen(message: String, retry: () -> Unit) = Column(
    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp).testTag("app-load-error"), Arrangement.Center,
) { Text("读取失败", style = MaterialTheme.typography.headlineMedium); Text(message); Button(retry, Modifier.padding(top = 16.dp).heightIn(min = 48.dp).testTag("app-load-retry"), shape = TerminalShape) { Text("重试") } }

@Composable private fun ErrorDialog(message: String, dismiss: () -> Unit) = TerminalDialog(
    code = "WARNING / ERROR", title = "操作未完成", message = message, confirm = "知道了",
    onConfirm = dismiss, onDismiss = dismiss, tag = "app-error-dialog", dismissTag = null, confirmTag = "app-error-dismiss",
)

@Composable private fun OnboardingScreen(form: SemesterFormState, saving: Boolean, actions: QingKeAppActions, dark: Boolean) = Scaffold(
    topBar = { Row(Modifier.fillMaxWidth().background(InverseSurface).statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("首次设置", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
        Button(actions.saveSemester, enabled = !saving, shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface), modifier = Modifier.heightIn(min = 48.dp).testTag("semester-save-toolbar")) { Text(if (saving) "保存中" else "保存") }
    } },
) { padding ->
    Column(Modifier.padding(padding).padding(16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).testTag("onboarding-screen")) {
        Text("建立你的第一个学期", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("onboarding-title"))
        OutlinedTextField(form.name, actions.updateName, Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp).testTag("semester-name"), label = { Text("学期名称") }, singleLine = true, shape = TerminalShape)
        DateControl(form.startDate, actions.updateStartDate, dark); WeekControl(form.totalWeeks, actions.updateTotalWeeks)
        OutlinedButton(actions.togglePeriods, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily-periods-toggle"), shape = TerminalShape) { Text(if (form.periodsExpanded) "收起节次设置（${form.periods.size} 节）" else "展开节次设置（${form.periods.size} 节）") }
        if (form.periodsExpanded) {
            form.periods.forEach { PeriodRow(it, form.periods.size, actions) }
            OutlinedButton(actions.addPeriod, enabled = form.periods.size < 20, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-period"), shape = TerminalShape) { Text("添加节次") }
        }
        form.validationMessage?.let { ValidationNotice(it, dark = dark, tag = "semester-validation-error") }
        Button(actions.saveSemester, enabled = !saving, shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface), modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).heightIn(min = 48.dp).testTag("semester-save")) { Text(if (saving) "正在保存…" else "保存并继续") }
    }
}

@Composable private fun DateControl(date: LocalDate, update: (LocalDate) -> Unit, dark: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var shownYear by rememberSaveable { mutableIntStateOf(date.year) }
    var shownMonth by rememberSaveable { mutableIntStateOf(date.monthValue) }
    val month = YearMonth.of(shownYear, shownMonth)
    val chineseDate = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).terminalPanel(dark, QingKeCyan).testTag("semester-start-date-container")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }
                .padding(horizontal = 14.dp).testTag("semester-start-date")
                .semantics { contentDescription = "开始日期，$chineseDate，${if (expanded) "收起日历" else "展开日历"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("开始日期", color = terminalText(dark), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(chineseDate, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "⌃" else "⌄", color = terminalText(dark), fontWeight = FontWeight.Black, modifier = Modifier.testTag("semester-start-date-chevron"))
        }
        if (expanded) InlineMonthCalendar(month, date, dark, { selected -> update(selected) }, { next ->
            val changed = if (next) month.plusMonths(1) else month.minusMonths(1)
            shownYear = changed.year; shownMonth = changed.monthValue
        })
    }
}

@Composable private fun InlineMonthCalendar(month: YearMonth, selected: LocalDate, dark: Boolean, select: (LocalDate) -> Unit, changeMonth: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).testTag("semester-start-date-calendar")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarMonthButton("‹", "上一个月", "semester-calendar-previous") { changeMonth(false) }
            Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,)
            CalendarMonthButton("›", "下一个月", "semester-calendar-next") { changeMonth(true) }
        }
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { day -> Text(day, Modifier.weight(1f), color = terminalSecondary(dark), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        SemesterMonthGrid.dates(month).chunked(SemesterMonthGrid.columns).forEach { week ->
            Row(Modifier.fillMaxWidth()) { week.forEach { candidate ->
                val isSelected = candidate == selected
                val candidateTag = candidate?.let { "semester-calendar-day-$it" } ?: "semester-calendar-empty"
                val candidateDescription = candidate?.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
                Box(
                    Modifier.weight(1f).height(40.dp).padding(2.dp)
                        .background(if (isSelected) SignalYellow else Color.Transparent, TerminalShape)
                        .clickable(enabled = candidate != null) { candidate?.let { select(it) } }
                        .testTag(candidateTag)
                        .semantics { candidateDescription?.let { contentDescription = "选择 $it" } },
                    contentAlignment = Alignment.Center,
                ) {
                    candidate?.let { Text(it.dayOfMonth.toString(), color = if (isSelected) InverseSurface else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal) }
                }
            }
            }
        }
    }
}

@Composable private fun CalendarMonthButton(symbol: String, label: String, tag: String, action: () -> Unit) = Box(
    Modifier.size(48.dp).clickable(onClick = action).testTag(tag).semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
) { Text(symbol, color = SignalYellow, fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif) }

@Composable private fun WeekControl(value: Int, update: (Int) -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("总周数：$value", Modifier.weight(1f).testTag("semester-total-weeks"))
    OutlinedButton({ update(value - 1) }, enabled = value > 1, modifier = Modifier.heightIn(min = 48.dp).testTag("semester-weeks-minus"), shape = TerminalShape) { Text("−") }
    Spacer(Modifier.width(8.dp)); OutlinedButton({ update(value + 1) }, enabled = value < 52, modifier = Modifier.heightIn(min = 48.dp).testTag("semester-weeks-plus"), shape = TerminalShape) { Text("+") }
}

@Composable private fun PeriodRow(period: PeriodFormState, count: Int, actions: QingKeAppActions) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("period-${period.id}-row")) {
        Text("第 ${period.number} 节", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ TimePickerDialog(context, { _, hour, minute -> actions.updatePeriodStart(period.id, LocalTime.of(hour, minute)) }, period.start.hour, period.start.minute, true).show() }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("period-${period.id}-start"), shape = TerminalShape) { Text(period.start.toString()) }
            OutlinedButton({ TimePickerDialog(context, { _, hour, minute -> actions.updatePeriodEnd(period.id, LocalTime.of(hour, minute)) }, period.end.hour, period.end.minute, true).show() }, Modifier.weight(1f).heightIn(min = 48.dp).testTag("period-${period.id}-end"), shape = TerminalShape) { Text(period.end.toString()) }
        }
        if (count > 1) OutlinedButton({ actions.removePeriod(period.id) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("period-${period.id}-delete").semantics { contentDescription = "删除第 ${period.number} 节" }, shape = TerminalShape) { Text("删除第 ${period.number} 节") }
    }
}

@Composable private fun MainShell(state: ScheduleState, selected: MainTab, currentTime: LocalDateTime, actions: QingKeAppActions, courseSuccess: String? = null, consumeCourseSuccess: () -> Unit = {}) {
    val dark = state.preferences.appearanceMode == AppearanceMode.DARK ||
        (state.preferences.appearanceMode == AppearanceMode.SYSTEM && isSystemInDarkTheme())
    Box(Modifier.fillMaxSize().testTag("main-shell")) {
        TerminalBackdrop(dark)
        when (selected) {
            MainTab.TODAY -> TodayScheduleScreen(state, currentTime, actions.refreshTime, actions.openCourseAt, dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SCHEDULE -> ShellPlaceholder("课表（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SETTINGS -> ShellPlaceholder("设置（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (selected == MainTab.TODAY) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    TodayAddButton(actions.openAddCourse)
                }
                Spacer(Modifier.height(8.dp))
            }
            courseSuccess?.let {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CourseSuccessNotice(it, dark, consumeCourseSuccess) }
            }
            Box(Modifier.fillMaxWidth().testTag("terminal-tab-bar")) { TerminalTabBar(selected, actions.selectTab, dark) }
        }
    }
}

@Composable private fun TerminalBackdrop(dark: Boolean, tag: String = "terminal-backdrop") = Canvas(
    Modifier.fillMaxSize().clipToBounds().testTag(tag),
) {
    val canvas = if (dark) Color(0xFF081113) else Color(0xFFE3EBEB)
    val grid = if (dark) Color(0xFFF1F5F4).copy(alpha = .055f) else Color(0xFF091113).copy(alpha = .07f)
    drawRect(canvas)
    var x = 0f
    while (x <= size.width) { drawLine(grid, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), .5.dp.toPx()); x += 24.dp.toPx() }
    var y = 0f
    while (y <= size.height) { drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), .5.dp.toPx()); y += 24.dp.toPx() }
    val diameter = maxOf(size.width * .88f, 320.dp.toPx())
    val center = androidx.compose.ui.geometry.Offset(size.width * .56f + diameter / 2, size.height * .27f + diameter / 2)
    listOf(0.dp, 42.dp, 90.dp).forEach { inset -> drawCircle(grid.copy(alpha = grid.alpha * 1.5f), diameter / 2 + inset.toPx(), center, style = Stroke(1.dp.toPx())) }
    drawRect(Brush.linearGradient(listOf(Color.Transparent, if (dark) QingKeCyan.copy(alpha = .06f) else Color.White.copy(alpha = .48f))))
}

@Composable private fun TerminalTabBar(selected: MainTab, onSelect: (MainTab) -> Unit, dark: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp)
            .terminalPanel(dark, Color.Transparent, TerminalSurfaceLevel.ELEVATED).padding(6.dp),
    ) {
        listOf(Triple(MainTab.TODAY, "今日", "01"), Triple(MainTab.SCHEDULE, "课表", "02"), Triple(MainTab.SETTINGS, "设置", "03")).forEach { (tab, title, number) ->
            val active = selected == tab
            Column(Modifier.weight(1f).height(62.dp).background(if (active) InverseSurface else Color.Transparent, TerminalShape)
                .selectable(active, onClick = { onSelect(tab) }, role = Role.Tab).testTag("${tab.name.lowercase()}-tab").semantics { contentDescription = title }) {
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.testTag("${tab.name.lowercase()}-tab-content"), verticalAlignment = Alignment.CenterVertically) {
                        TerminalTabIcon(tab, if (active) Color(0xFFF1F5F4) else terminalText(dark), "${tab.name.lowercase()}-tab-icon")
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(title, color = if (active) Color(0xFFF1F5F4) else terminalText(dark), fontWeight = FontWeight.Bold)
                            Text(number, color = if (active) SignalYellow else terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Box(Modifier.align(Alignment.CenterHorizontally).width(30.dp).heightIn(min = 4.dp).background(if (active) SignalYellow else Color.Transparent))
            }
        }
    }
}

@Composable private fun TerminalTabIcon(tab: MainTab, color: Color, tag: String) = Canvas(Modifier.size(18.dp).testTag(tag)) {
    val stroke = Stroke(1.7.dp.toPx())
    when (tab) {
        MainTab.TODAY -> listOf(.5f, 10.5f).forEach { x -> listOf(.5f, 10.5f).forEach { y -> drawRect(color, androidx.compose.ui.geometry.Offset(x.dp.toPx(), y.dp.toPx()), androidx.compose.ui.geometry.Size(7.dp.toPx(), 7.dp.toPx()), style = stroke) } }
        MainTab.SCHEDULE -> { drawRect(color, androidx.compose.ui.geometry.Offset(1.dp.toPx(), 3.dp.toPx()), androidx.compose.ui.geometry.Size(16.dp.toPx(), 14.dp.toPx()), style = stroke); drawLine(color, androidx.compose.ui.geometry.Offset(1.dp.toPx(), 7.dp.toPx()), androidx.compose.ui.geometry.Offset(17.dp.toPx(), 7.dp.toPx()), 1.7.dp.toPx()) }
        MainTab.SETTINGS -> listOf(4.dp, 9.dp, 14.dp).forEachIndexed { i, y -> drawLine(color, androidx.compose.ui.geometry.Offset(1.dp.toPx(), y.toPx()), androidx.compose.ui.geometry.Offset(17.dp.toPx(), y.toPx()), 1.4.dp.toPx()); drawCircle(color, 2.dp.toPx(), androidx.compose.ui.geometry.Offset((if (i == 1) 12 else 6).dp.toPx(), y.toPx())) }
    }
}

/** Platform-native geometry equivalent to plus.square; all strokes share one canvas center. */
@Composable private fun CreateCourseIcon(color: Color) = Canvas(Modifier.size(22.dp).testTag("course-create-new-icon")) {
    val stroke = 1.5.dp.toPx()
    val inset = stroke / 2f
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val plusInset = 5.dp.toPx()
    drawRect(color, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
    drawLine(color, androidx.compose.ui.geometry.Offset(plusInset, centerY), androidx.compose.ui.geometry.Offset(size.width - plusInset, centerY), stroke)
    drawLine(color, androidx.compose.ui.geometry.Offset(centerX, plusInset), androidx.compose.ui.geometry.Offset(centerX, size.height - plusInset), stroke)
}

@Composable private fun ShellPlaceholder(text: String, dark: Boolean, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().statusBarsPadding(), Arrangement.Center, Alignment.CenterHorizontally,
) { Text(text, color = terminalText(dark), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TodayScheduleScreen(state: ScheduleState, now: LocalDateTime, refresh: () -> Unit, openCourseAt: (Int) -> Unit, dark: Boolean, modifier: Modifier = Modifier) {
    val semester = state.data.semester ?: return
    val presentation = TodaySchedulePresentation.create(semester, state.data.courses, now, state.preferences.academicCalendar)
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val triggerRefresh = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                refresh()
                delay(350)
                refreshing = false
            }
        }
    }
    Box(modifier.statusBarsPadding().testTag("today-screen")) {
        Column(Modifier.fillMaxSize()) {
            BrandHeader(dark)
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = triggerRefresh, modifier = Modifier.weight(1f).fillMaxWidth().testTag("today-refresh-container")) {
            LazyColumn(Modifier.fillMaxSize().testTag("today-scroll-content"), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { TodayHero(now, semester, presentation, dark, Modifier.testTag("today-date-hero")) }
                if (refreshing) item { RefreshFeedback(dark) }
                if (presentation.items.isEmpty()) item { TodayEmpty(presentation.emptyMessage, dark) }
                else {
                    val featured = presentation.items.firstOrNull { it.status == CourseStatus.ONGOING } ?: presentation.items.firstOrNull { it.isNext }
                    if (featured != null) {
                        item { ActivityRail(featured, now, dark) }
                        item(key = "featured-${featured.occurrence.key.courseIndex}-${featured.occurrence.key.scheduleIndex}") { FeaturedCourse(featured, semester, presentation.items.indexOf(featured), presentation.items.size, dark, { openCourseAt(featured.occurrence.key.courseIndex) }) }
                    }
                    item { SequenceHeader(presentation.items.size, dark) }
                    items(presentation.items, key = { "${it.occurrence.key.courseIndex}-${it.occurrence.key.scheduleIndex}" }) { item -> CourseRow(item, semester, presentation.items.indexOf(item), dark, { openCourseAt(item.occurrence.key.courseIndex) }) }
                    item { Text("END OF SCHEDULE // ${presentation.items.lastOrNull()?.let { ScheduleDisplayText.timeRange(it.occurrence.schedule, semester).substringAfter('–') } ?: "--:--"}", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().testTag("today-end-marker"), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
            }
            }
        }
    }
}

@Composable private fun TodayAddButton(addCourse: () -> Unit) = Button(addCourse, Modifier.size(64.dp).testTag("today-add-course").semantics { contentDescription = "添加课程" }, shape = TerminalShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) {
    Box(Modifier.fillMaxSize().drawBehind {
        val inset = 3.dp.toPx(); val fold = 13.dp.toPx()
        drawRect(InverseSurface.copy(alpha = .82f), topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1.dp.toPx()))
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width - inset - fold, inset); lineTo(size.width - inset, inset); lineTo(size.width - inset, inset + fold); close() }
        drawPath(path, InverseSurface.copy(alpha = .88f)); drawLine(InverseSurface.copy(alpha = .82f), androidx.compose.ui.geometry.Offset(size.width - inset - fold, inset), androidx.compose.ui.geometry.Offset(size.width - inset, inset + fold), 1.dp.toPx())
    }.testTag("today-add-visual"), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("+", fontSize = 25.sp, lineHeight = 22.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif, modifier = Modifier.testTag("today-add-plus")); Text("ADD", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("today-add-label")) } }
}

@Composable private fun BrandHeader(dark: Boolean, code: String = "LOCAL / 01", tag: String = "today-brand-header") = Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).border(width = 0.dp, color = Color.Transparent)
        .drawBehind { drawLine(if (dark) Color.White.copy(alpha = .20f) else Color.Black.copy(alpha = .18f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.testTag(tag), verticalAlignment = Alignment.CenterVertically,
) {
    androidx.compose.foundation.Image(painterResource(if (dark) R.drawable.qingke_logo_dark else R.drawable.qingke_logo), "青课 QINGKE ACADEMIC TERMINAL", Modifier.width(154.dp).heightIn(min = 54.dp).testTag("today-brand-logo"), contentScale = ContentScale.Fit)
    Spacer(Modifier.weight(1f)); Text(code, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun TodayHero(now: LocalDateTime, semester: com.qingke.schedule.domain.Semester, presentation: TodaySchedulePresentation, dark: Boolean, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.width(TodayVisualSpec.dayColumnWidth)) {
        Text(now.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(Locale.US), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("today-month-code"))
        Text(now.format(DateTimeFormatter.ofPattern("dd", Locale.US)), color = terminalText(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Thin, fontSize = TodayVisualSpec.dayNumberSize, lineHeight = TodayVisualSpec.dayNumberSize, maxLines = 1, modifier = Modifier.testTag("today-day-number"))
        Text(now.format(DateTimeFormatter.ofPattern("yyyy / EEE", Locale.US)).uppercase(Locale.US), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
    Box(Modifier.width(1.dp).heightIn(min = 116.dp).background(terminalBorder(dark)))
    Column(Modifier.weight(1f).padding(start = 16.dp)) {
        Text("SCHEDULE :// TODAY", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(InverseSurface).padding(horizontal = 8.dp, vertical = 5.dp).testTag("today-hero-code"))
        Text("今日", color = terminalText(dark), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        val week = presentation.teachingWeek
        Text(if (week != null && week in 1..semester.totalWeeks) "第 ${"%02d".format(week)} 教学周 · ${if (week % 2 == 0) "双周" else "单周"}" else "学期外 · ${semester.name}", color = terminalSecondary(dark), modifier = Modifier.testTag("today-teaching-week"), style = MaterialTheme.typography.labelMedium)
    }
    Column(horizontalAlignment = Alignment.End) { Text("COURSE", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall); Text("%02d".format(presentation.items.size), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("today-course-count")); Text("/ DAY", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }
}

@Composable private fun ActivityRail(item: TodayCourseItem, now: LocalDateTime, dark: Boolean) = Row(Modifier.fillMaxWidth().heightIn(min = 34.dp).background(InverseSurface).drawBehind { drawRect(if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan, size = androidx.compose.ui.geometry.Size(size.width, 4.dp.toPx())) }.padding(horizontal = 12.dp).testTag("today-activity-rail"), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp)); Text(if (item.status == CourseStatus.ONGOING) "当前课程" else "下一门课程", color = Color(0xFFF1F5F4), fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("进度更新于 ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}", color = Color(0xB3F1F5F4), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }

@Composable private fun SequenceHeader(count: Int, dark: Boolean, detail: String = "QUEUE / ALL DAY") = Row(Modifier.fillMaxWidth().drawBehind { drawLine(terminalBorder(dark), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 8.dp).testTag("today-course-sequence"), verticalAlignment = Alignment.CenterVertically) { Text("%02d".format(count), color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black); Spacer(Modifier.width(9.dp)); Text("课程序列", color = terminalText(dark), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Text(detail, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }

@Composable private fun RefreshFeedback(dark: Boolean) = Row(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("today-refresh-status")) { Text("刷新中", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("SYNC / LOCAL", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }

@Composable private fun TodayEmpty(message: String, dark: Boolean) = Column(Modifier.testTag("today-empty")) { SequenceHeader(0, dark, "QUEUE EMPTY"); Column(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(16.dp)) { Text("STANDBY", color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.background(QingKeCyan).padding(horizontal = 6.dp, vertical = 3.dp)); Spacer(Modifier.heightIn(min = 9.dp)); Text("今天没有课程", color = terminalText(dark), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(message, color = terminalSecondary(dark)); Text("使用右下角 ADD 录入一门新课程。", color = terminalSecondary(dark), modifier = Modifier.padding(top = 12.dp)) } }

@Composable private fun FeaturedCourse(item: TodayCourseItem, semester: com.qingke.schedule.domain.Semester, index: Int, total: Int, dark: Boolean, open: () -> Unit) = Column(Modifier.fillMaxWidth().terminalPanel(dark, if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan).clickable(onClick = open).testTag("today-featured-course-${item.occurrence.key.courseIndex}-${item.occurrence.key.scheduleIndex}")) {
    val accent = if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).background(accent).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(InverseSurface, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp)); Text(if (item.status == CourseStatus.ONGOING) "CURRENT" else "NEXT", color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.testTag("today-featured-status")); Spacer(Modifier.weight(1f)); Text("%02d // %02d".format(index + 1, total), color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall) }
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Column(Modifier.width(TodayVisualSpec.featuredTimeColumnWidth)) { Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Bold, fontSize = TodayVisualSpec.featuredStartTimeSize, lineHeight = TodayVisualSpec.featuredStartTimeSize, maxLines = 1, modifier = Modifier.testTag("today-featured-start-time")); Text("– ${range.getOrNull(1).orEmpty()}", color = terminalSecondary(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Normal, fontSize = TodayVisualSpec.featuredEndTimeSize, lineHeight = TodayVisualSpec.featuredEndTimeSize, maxLines = 1, modifier = Modifier.testTag("today-featured-end-time")) }; Box(Modifier.width(1.dp).heightIn(min = 70.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 15.dp).weight(1f)) { Text(item.occurrence.course.name, color = terminalText(dark), fontSize = TodayVisualSpec.featuredCourseNameSize, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.testTag("today-featured-name")); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-featured-details")) } }
    item.timingProgress?.let { progress -> LinearProgressIndicator({ progress.fraction.toFloat() }, Modifier.fillMaxWidth().heightIn(min = 5.dp).testTag("today-featured-progress"), color = QingKeCyan, trackColor = if (dark) Color.White.copy(alpha = .14f) else InverseSurface.copy(alpha = .12f)); Row(Modifier.fillMaxWidth().background(InverseSurface).padding(horizontal = 12.dp, vertical = 9.dp)) { Text("已进行 ${progress.elapsedMinutes} 分钟 · 剩余 ${progress.remainingClockText}", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("◷", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun CourseRow(item: TodayCourseItem, semester: com.qingke.schedule.domain.Semester, index: Int, dark: Boolean, open: () -> Unit) = Row(Modifier.fillMaxWidth().terminalPanel(dark, Color.Transparent).alpha(if (item.status == CourseStatus.FINISHED) .56f else 1f).clickable(onClick = open).padding(12.dp).testTag("today-course-${item.occurrence.key.courseIndex}-${item.occurrence.key.scheduleIndex}"), verticalAlignment = Alignment.CenterVertically) {
    val key = item.occurrence.key; val accent = if (item.status == CourseStatus.ONGOING) SignalYellow else courseColor(item.occurrence.course.color)
    Box(Modifier.width(3.dp).heightIn(min = 68.dp).background(accent).testTag("today-course-color-${key.courseIndex}-${key.scheduleIndex}").semantics { contentDescription = "课程颜色：${courseColorLabel(item.occurrence.course.color)}" }); Spacer(Modifier.width(8.dp)); Text("%02d".format(index + 1), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.width(24.dp).graphicsLayer { rotationZ = -90f }.testTag("today-course-index-${key.courseIndex}-${key.scheduleIndex}")); Column(Modifier.width(TodayVisualSpec.sequenceTimeColumnWidth)) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Bold, fontSize = TodayVisualSpec.sequenceStartTimeSize, lineHeight = TodayVisualSpec.sequenceStartTimeSize, maxLines = 1, modifier = Modifier.testTag("today-course-start-time-${key.courseIndex}-${key.scheduleIndex}")); Text(range.getOrNull(1).orEmpty(), color = terminalSecondary(dark), fontFamily = TodayVisualSpec.condensed, fontWeight = FontWeight.Normal, fontSize = TodayVisualSpec.sequenceEndTimeSize, lineHeight = TodayVisualSpec.sequenceEndTimeSize, maxLines = 1, modifier = Modifier.testTag("today-course-end-time-${key.courseIndex}-${key.scheduleIndex}")) }; Box(Modifier.width(1.dp).heightIn(min = 52.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 13.dp).weight(1f)) { Text(statusText(item), color = if (item.status == CourseStatus.ONGOING) InverseSurface else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(accent).padding(horizontal = 6.dp, vertical = 2.dp).testTag("today-course-status-${key.courseIndex}-${key.scheduleIndex}")); Text(item.occurrence.course.name, color = terminalText(dark), fontSize = TodayVisualSpec.sequenceCourseNameSize, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.testTag("today-course-name-${key.courseIndex}-${key.scheduleIndex}")); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-course-details-${key.courseIndex}-${key.scheduleIndex}"), style = MaterialTheme.typography.labelSmall) }; Text("›", color = terminalSecondary(dark), fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp).testTag("today-course-enter-${key.courseIndex}-${key.scheduleIndex}"))
}
private fun terminalText(dark: Boolean) = if (dark) Color(0xFFF1F5F4) else Color(0xFF091113)
private fun terminalSecondary(dark: Boolean) = if (dark) Color(0xB3F1F5F4) else Color(0xB3091113)
private fun terminalBorder(dark: Boolean) = if (dark) Color(0x59F1F5F4) else Color(0x57091113)
private fun terminalPanelEdge(dark: Boolean) = if (dark) Color.White.copy(alpha = .30f) else Color.White.copy(alpha = .76f)
private fun Modifier.terminalPanel(dark: Boolean, accent: Color, level: TerminalSurfaceLevel = TerminalSurfaceLevel.STANDARD): Modifier {
    val elevated = level == TerminalSurfaceLevel.ELEVATED
    val surface = if (dark) (if (elevated) Color(0xF01A2527) else Color(0xE61A2527)) else (if (elevated) Color(0xEAFBFEFD) else Color(0xDDFBFEFD))
    val highlight = if (dark) Color.White.copy(alpha = if (elevated) .14f else .10f) else Color.White.copy(alpha = if (elevated) .62f else .48f)
    return this
        .shadow(if (elevated) 8.dp else 4.dp, TerminalShape, ambientColor = Color.Black.copy(alpha = if (dark) (if (elevated) .52f else .42f) else (if (elevated) .28f else .22f)), spotColor = Color.Black.copy(alpha = if (dark) (if (elevated) .44f else .34f) else (if (elevated) .22f else .16f)))
        .background(Brush.linearGradient(listOf(highlight, surface, surface.copy(alpha = .94f))), TerminalShape)
        .border(1.dp, terminalPanelEdge(dark), TerminalShape)
        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
}

/** A modal-only opaque surface: its highlight is color-blended, never alpha-blended with the screen beneath. */
internal fun Modifier.terminalModalSurface(dark: Boolean, accent: Color): Modifier {
    val surface = if (dark) Color(0xFF142124) else Color(0xFFF7FBFA)
    val highlight = if (dark) Color(0xFF29383B) else Color(0xFFFFFFFF)
    return this
        .shadow(18.dp, TerminalShape, ambientColor = Color.Black.copy(alpha = .62f), spotColor = Color.Black.copy(alpha = .52f))
        .background(Brush.linearGradient(listOf(highlight, surface, surface)), TerminalShape)
        .border(1.dp, terminalPanelEdge(dark), TerminalShape)
        .drawBehind { drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
}

@Composable private fun ValidationNotice(message: String, dark: Boolean, tag: String) = Row(
    Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 52.dp).terminalPanel(dark, Danger, TerminalSurfaceLevel.ELEVATED)
        .padding(horizontal = 14.dp, vertical = 9.dp).testTag(tag),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("▲", color = Danger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.testTag("$tag-icon"))
    Spacer(Modifier.width(10.dp))
    Text(message, color = Danger, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("$tag-message"))
}

private fun statusText(item: TodayCourseItem): String = when (item.status) {
    CourseStatus.FINISHED -> "COMPLETE"
    CourseStatus.ONGOING -> "CURRENT"
    CourseStatus.UPCOMING -> if (item.isNext) "NEXT" else "UPCOMING"
}

private fun courseDetails(occurrence: CourseOccurrence): String = listOf(
    occurrence.schedule.classroom.trim(), occurrence.course.teacher.trim(),
).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { ScheduleDisplayText.periodRange(occurrence.schedule) }

@Composable private fun CourseEditorOverlay(
    editor: CourseEditorState,
    semester: com.qingke.schedule.domain.Semester?,
    courses: List<com.qingke.schedule.domain.Course>,
    dark: Boolean,
    actions: QingKeAppActions,
) {
    BackHandler(onBack = actions.editorBack)
    Box(Modifier.fillMaxSize().testTag("course-editor")) {
        TerminalBackdrop(dark, "course-editor-backdrop")
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (editor.mode == CourseEditorMode.CHOOSER) {
            Column(Modifier.fillMaxSize()) {
                EditorHeader("添加课程", "SELECT PROFILE", actions.closeCourseEditor, null, false, dark)
                BrandHeader(dark, "PROFILE / 04", "course-choice-brand-header")
                Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                TerminalSectionHeader("01", "创建方式", "COURSE DATA", dark, "course-choice-create-section")
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Box(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).clickable { actions.openNewCourse() }.testTag("course-create-new").semantics { contentDescription = "新建一门课程" }) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp).testTag("course-choice-create-panel"), verticalAlignment = Alignment.CenterVertically) {
                            CreateCourseIcon(terminalText(dark))
                            Text("新建一门课程", color = terminalText(dark), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                    Text("已有课程会复用名称、教师和识别色，只新增一条上课安排。", color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 3.dp, top = 8.dp).testTag("course-choice-footer"))
                }
                TerminalSectionHeader("02", "已有课程", "REUSE ${courses.size}", dark, "course-choice-reuse-section")
                courses.withIndex().sortedWith(compareBy<IndexedValue<com.qingke.schedule.domain.Course>> { it.value.name.trim() }.thenBy { it.index }).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp).heightIn(min = 48.dp).terminalPanel(dark, QingKeCyan).clickable { actions.appendCourseAt(entry.index) }.padding(horizontal = 12.dp, vertical = 7.dp).testTag("course-append-${entry.index}").semantics { contentDescription = "为 ${entry.value.name} 添加上课安排，来源 ${entry.index + 1}" }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(6.dp).height(42.dp).background(courseColor(entry.value.color), TerminalShape).testTag("course-append-rail-${entry.index}"))
                        Box(Modifier.padding(start = 8.dp).size(10.dp).background(courseColor(entry.value.color), androidx.compose.foundation.shape.CircleShape))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) { Text(entry.value.name.ifBlank { "未命名课程" }, color = terminalText(dark), fontWeight = FontWeight.Bold); Text(entry.value.teacher.ifBlank { "未填写教师" }, color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall) }
                        Text("添加安排", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(14.dp))
                }
            }
        } else {
            val periodMaximum = semester?.periods?.maxOfOrNull { it.number } ?: 1
            val weekMaximum = semester?.totalWeeks ?: 1
            Column(Modifier.fillMaxSize()) {
                EditorHeader(if (editor.isAppend) "添加上课安排" else if (editor.mode == CourseEditorMode.EDIT) "编辑课程" else "添加课程", if (editor.isAppend) "NEW SCHEDULE" else if (editor.mode == CourseEditorMode.EDIT) "COURSE PROFILE" else "NEW COURSE", actions.closeCourseEditor, actions.saveCourse, editor.isInFlight, dark)
                BrandHeader(dark, "${if (editor.isAppend) "APPEND" else if (editor.mode == CourseEditorMode.EDIT) "EDIT" else "CREATE"} / 04", "course-editor-brand-header")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                TerminalSectionHeader("01", if (editor.isAppend) "沿用课程资料" else "课程信息", if (editor.isAppend) "REUSED PROFILE" else "COURSE PROFILE", dark, "course-info-section")
                if (editor.isAppend) {
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(horizontal = 12.dp, vertical = 8.dp).testTag("course-append-readonly"), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(6.dp).height(42.dp).background(courseColor(editor.color), TerminalShape))
                            Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(editor.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("course-append-name")); Text(editor.teacher.ifBlank { "未填写教师" }, color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("course-append-teacher")) }
                        }
                        Text("课程名称、教师和识别色沿用已有课程；这里只新增上课安排。", color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 3.dp, top = 8.dp).testTag("course-append-footer"))
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp).terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-info-form-section")) {
                    PlainEditorTextField("课程名称", editor.name, actions.updateCourseName, "course-name", !editor.isInFlight, dark); Box(Modifier.fillMaxWidth().height(1.dp).background(terminalBorder(dark))); PlainEditorTextField("教师（选填）", editor.teacher, actions.updateCourseTeacher, "course-teacher", !editor.isInFlight, dark); Box(Modifier.fillMaxWidth().height(1.dp).background(terminalBorder(dark)))
                    Text("课程颜色", Modifier.padding(top = 12.dp), color = terminalText(dark), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("#287B74", "#D96952", "#536FAF", "#9A6AAF", "#B87928", "#46835A").forEach { color ->
                            val selected = editor.color == color
                            Box(Modifier.weight(1f).height(38.dp).background(Color.Transparent).clickable(enabled = !editor.isInFlight) { actions.updateCourseColor(color) }.semantics { contentDescription = "预设颜色 $color，${if (selected) "已选中" else "未选中"}" }.testTag("course-color-$color"), contentAlignment = Alignment.Center) { Box(Modifier.size(27.dp).background(courseColor(color), androidx.compose.foundation.shape.CircleShape).border(if (selected) 3.dp else 1.dp, if (selected) SignalYellow else Color.White.copy(alpha = .65f), androidx.compose.foundation.shape.CircleShape).testTag("course-color-swatch-$color"), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !editor.isInFlight, onClick = actions.showColorDialog).testTag("course-custom-color").semantics { contentDescription = "自定义颜色，当前 ${editor.color}" }, verticalAlignment = Alignment.CenterVertically) { Text("自定义颜色", color = terminalText(dark), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(Modifier.size(22.dp).background(courseColor(editor.color), androidx.compose.foundation.shape.CircleShape).border(1.dp, terminalBorder(dark), androidx.compose.foundation.shape.CircleShape).testTag("course-custom-color-swatch")); Text("›", color = QingKeCyan, fontSize = 20.sp, modifier = Modifier.padding(start = 9.dp)) }
                    Text("当前色值  ${editor.color.uppercase()}", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp).testTag("course-current-color"))
                    }
                }
                editor.visibleSchedules.forEachIndexed { visibleIndex, schedule ->
                    val scheduleNumber = editor.originalScheduleCount + visibleIndex + 1
                    TerminalSectionHeader("%02d".format(scheduleNumber + 1), "上课安排 $scheduleNumber", "SCHEDULE", dark, "course-schedule-header-${schedule.id}")
                    Column(Modifier.fillMaxWidth().padding(top = 7.dp).terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-schedule-${schedule.id}")) {
                        CompactPicker("星期", weekdayName(schedule.dayOfWeek), schedule.dayOfWeek, 1..7, { weekdayName(it) }, { actions.updateCourseDay(schedule.id, it) }, "course-day-${schedule.id}", dark, !editor.isInFlight)
                        CompactPicker("开始节次", periodDescription(semester, schedule.startPeriod, true), schedule.startPeriod, 1..periodMaximum, { periodDescription(semester, it, true) }, { actions.updateCourseStartPeriod(schedule.id, it) }, "course-start-period-${schedule.id}", dark, !editor.isInFlight)
                        CompactPicker("结束节次", periodDescription(semester, schedule.endPeriod, false), schedule.endPeriod, 1..periodMaximum, { periodDescription(semester, it, false) }, { actions.updateCourseEndPeriod(schedule.id, it) }, "course-end-period-${schedule.id}", dark, !editor.isInFlight)
                        EditorStepper("起始周", schedule.startWeek, 1, weekMaximum, { actions.updateCourseStartWeek(schedule.id, it) }, "course-start-week-${schedule.id}", dark, !editor.isInFlight)
                        EditorStepper("结束周", schedule.endWeek, 1, weekMaximum, { actions.updateCourseEndWeek(schedule.id, it) }, "course-end-week-${schedule.id}", dark, !editor.isInFlight)
                        TerminalRepeatSelector(schedule.repeatRule, { actions.updateCourseRepeat(schedule.id, it) }, "course-repeat-${schedule.id}", dark, !editor.isInFlight)
                        OutlinedTextField(schedule.classroom, { actions.updateCourseClassroom(schedule.id, it) }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("course-classroom-${schedule.id}"), label = { Text("教室（可选）") }, singleLine = true, enabled = !editor.isInFlight, shape = TerminalShape)
                        if (editor.schedules.size > 1) OutlinedButton({ actions.removeCourseSchedule(schedule.id) }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).testTag("course-remove-schedule-${schedule.id}"), enabled = !editor.isInFlight, shape = TerminalShape) { Text("删除这个安排", color = Danger) }
                    }
                }
                Button(actions.addCourseSchedule, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("course-add-schedule"), shape = TerminalShape, enabled = !editor.isInFlight, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) { Text("+  添加上课安排", fontWeight = FontWeight.Black) }
                editor.validationMessage?.let { ValidationNotice(it, dark, "course-validation") }
                if (editor.mode == CourseEditorMode.EDIT) { TerminalSectionHeader("99", "危险操作", "DANGER", dark, "course-danger-header"); Column(Modifier.fillMaxWidth().terminalPanel(dark, Danger).padding(12.dp).testTag("course-danger-zone")) { OutlinedButton(actions.deleteCourse, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("course-delete"), enabled = !editor.isInFlight, shape = TerminalShape) { Text("删除课程", color = Danger) } }; Text("删除后，这门课程的所有上课安排都会一并移除。", color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(start = 3.dp, top = 8.dp, bottom = 20.dp).testTag("course-danger-footer")) }
                }
            }
        }
        }
        when (val confirmation = editor.confirmation) {
            CourseEditorConfirmation.Discard -> EditorDialog("放弃未保存的修改？", "当前编辑内容尚未保存。放弃后，本次修改不会保留。", "放弃修改", actions.discardCourseEditor, actions.dismissCourseConfirmation, "course-discard-confirm", dark)
            CourseEditorConfirmation.Delete -> EditorDialog("删除这门课程？", "课程及其所有上课安排都会被删除，这项操作无法撤销。", "确认删除", actions.confirmDeleteCourse, actions.dismissCourseConfirmation, "course-delete-confirm", dark)
            is CourseEditorConfirmation.Conflicts -> EditorDialog("检测到课程冲突", "${conflictMessage(confirmation.conflicts)} 冲突会被标记，但仍可保存。", "仍然保存", actions.confirmSaveDespiteConflicts, actions.dismissCourseConfirmation, "course-conflict-confirm", dark)
            null -> Unit
        }
        if (editor.isColorDialogOpen) CourseColorDialog(editor, dark, actions)
    }
}

@Composable private fun EditorHeader(title: String, subtitle: String, close: () -> Unit, save: (() -> Unit)?, saving: Boolean, dark: Boolean) = Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).background(InverseSurface).drawBehind { drawRect(SignalYellow, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }.padding(horizontal = 14.dp, vertical = 7.dp).testTag("course-editor-toolbar"), verticalAlignment = Alignment.CenterVertically) {
    Text("取消", color = Color(0xFFF1F5F4), fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !saving, onClick = close).padding(vertical = 12.dp).testTag("course-editor-close"))
    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = Color(0xFFF1F5F4), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = Color(0xB3F1F5F4), fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    if (save != null) Text(if (saving) "保存中" else "保存", color = SignalYellow, fontWeight = FontWeight.Black, modifier = Modifier.clickable(enabled = !saving, onClick = save).padding(vertical = 12.dp).testTag("course-save-toolbar")) else Spacer(Modifier.width(28.dp))
}

@Composable private fun TerminalSectionHeader(number: String, title: String, subtitle: String, dark: Boolean, tag: String) = Row(Modifier.fillMaxWidth().padding(top = 14.dp).drawBehind { drawLine(terminalBorder(dark), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 7.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) { Text(number, color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.testTag("$tag-number")); Spacer(Modifier.width(9.dp)); Text(title, color = terminalText(dark), fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(subtitle, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }

@Composable private fun PlainEditorTextField(label: String, value: String, update: (String) -> Unit, tag: String, enabled: Boolean, dark: Boolean) = BasicTextField(
    value = value,
    onValueChange = update,
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
    enabled = enabled,
    singleLine = true,
    textStyle = androidx.compose.ui.text.TextStyle(color = terminalText(dark), fontWeight = FontWeight.Bold),
    decorationBox = { innerTextField ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) Text(label, color = terminalSecondary(dark), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("$tag-placeholder"))
            innerTextField()
        }
    },
)

@Composable private fun EditorStepper(label: String, value: Int, minimum: Int, maximum: Int, update: (Int) -> Unit, tag: String, dark: Boolean, enabled: Boolean) = Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("$label：$value", Modifier.weight(1f), color = terminalText(dark))
    Text("−", color = QingKeCyan, fontSize = 21.sp, fontWeight = FontWeight.Black, modifier = Modifier.size(48.dp).clickable(enabled = enabled && value > minimum) { update((value - 1).coerceAtLeast(minimum)) }.testTag("$tag-minus").wrapContentSize(Alignment.Center))
    Text("+", color = QingKeCyan, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.size(48.dp).clickable(enabled = enabled && value < maximum) { update((value + 1).coerceAtMost(maximum)) }.testTag("$tag-plus").wrapContentSize(Alignment.Center))
}

@Composable private fun CompactPicker(label: String, valueLabel: String, value: Int, choices: IntRange, choiceLabel: (Int) -> String, update: (Int) -> Unit, tag: String, dark: Boolean, enabled: Boolean) = Box(Modifier.fillMaxWidth()) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { expanded = true }.testTag(tag).semantics { contentDescription = "$label：$valueLabel" }, verticalAlignment = Alignment.CenterVertically) {
        Text(valueLabel, color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("$tag-value"))
        Text("⌄", color = QingKeCyan, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 7.dp))
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(terminalBorder(dark)))
    TerminalDropdownMenu(expanded, { expanded = false }, choices, value, choiceLabel, { update(it); expanded = false }, tag, dark)
}

@Composable private fun TerminalDropdownMenu(expanded: Boolean, dismiss: () -> Unit, choices: IntRange, selected: Int, choiceLabel: (Int) -> String, update: (Int) -> Unit, tag: String, dark: Boolean) = DropdownMenu(
    expanded = expanded, onDismissRequest = dismiss, modifier = Modifier.widthIn(min = 236.dp).heightIn(max = 280.dp).border(1.dp, terminalBorder(dark), TerminalShape).testTag("$tag-menu"),
    shape = TerminalShape, containerColor = if (dark) DarkSurface else LightSurface, tonalElevation = 0.dp, shadowElevation = 10.dp,
) {
    choices.forEach { choice ->
        val active = choice == selected
        DropdownMenuItem(
            text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(choiceLabel(choice), color = if (active) Color.White else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); if (active) Text("✓", color = SignalYellow, fontWeight = FontWeight.Black) } },
            onClick = { update(choice) },
            modifier = Modifier.heightIn(min = 48.dp).background(if (active) InverseSurface else Color.Transparent).drawBehind { drawRect(QingKeCyan, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx())) }.testTag("$tag-option-$choice"),
        )
    }
}

@Composable private fun TerminalRepeatSelector(value: RepeatRule, update: (RepeatRule) -> Unit, tag: String, dark: Boolean, enabled: Boolean) = Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    RepeatRule.entries.forEach { rule ->
        val selected = rule == value
        val label = if (rule == RepeatRule.EVERY) "每周" else if (rule == RepeatRule.ODD) "单周" else "双周"
        Box(Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, if (selected) SignalYellow else terminalBorder(dark), TerminalShape).background(if (selected) InverseSurface else Color.Transparent).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { update(rule) }).testTag("$tag-${rule.name}"), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) { if (selected) Box(Modifier.size(7.dp).background(SignalYellow, androidx.compose.foundation.shape.CircleShape)); if (selected) Spacer(Modifier.width(5.dp)); Text(label, color = if (selected) Color.White else terminalText(dark), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
}

@Composable private fun CourseColorDialog(editor: CourseEditorState, dark: Boolean, actions: QingKeAppActions) {
    var mode by rememberSaveable { mutableStateOf(ColorPickerMode.GRID) }
    TerminalDialog(code = "COLOR / CUSTOM", title = "自定义颜色", confirm = "完成", onConfirm = actions.dismissColorDialog, onDismiss = actions.dismissColorDialog, tag = "course-color-dialog", dark = dark, messageContent = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(courseColor(editor.color), TerminalShape).border(1.dp, terminalBorder(dark), TerminalShape).testTag("course-color-preview")); Spacer(Modifier.width(12.dp)); Text(editor.color, color = terminalText(dark), fontFamily = FontFamily.Monospace) }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup()) { ColorPickerMode.entries.forEach { item -> ColorModeTab(item, mode == item, { mode = item }, dark) } }
            when (mode) {
                ColorPickerMode.GRID -> ColorGridPicker(editor.color, { actions.updateCourseColor(it) }, dark, !editor.isInFlight)
                ColorPickerMode.SPECTRUM -> SpectrumPicker(editor.color, { actions.updateCourseColor(it) }, dark, !editor.isInFlight)
                ColorPickerMode.SLIDERS -> RgbSliderPicker(editor, actions, dark)
            }
        } })
}

internal enum class ColorPickerMode(val label: String) { GRID("网格"), SPECTRUM("光谱"), SLIDERS("滑块") }

/** Deterministic visual contract shared by the grid and its JVM tests. */
internal object CourseColorVisualSpec {
    val grid = listOf(
        "#FFFFFF", "#D1D5DB", "#6B7280", "#111827", "#000000", "#FEE2E2",
        "#FECACA", "#FB7185", "#E11D48", "#991B1B", "#FED7AA", "#FDBA74",
        "#F97316", "#C2410C", "#7C2D12", "#FEF3C7", "#FCD34D", "#EAB308",
        "#A16207", "#713F12", "#DCFCE7", "#86EFAC", "#22C55E", "#15803D",
        "#14532D", "#DBEAFE", "#93C5FD", "#3B82F6", "#1D4ED8", "#172554",
        "#EDE9FE", "#C4B5FD", "#8B5CF6", "#6D28D9", "#3B0764", "#FCE7F3",
    )
}

@Composable private fun androidx.compose.foundation.layout.RowScope.ColorModeTab(item: ColorPickerMode, selected: Boolean, click: () -> Unit, dark: Boolean) = Box(
    Modifier.weight(1f).heightIn(min = 48.dp).border(1.dp, if (selected) SignalYellow else terminalBorder(dark), TerminalShape).background(if (selected) InverseSurface else Color.Transparent).selectable(selected, role = Role.Tab, onClick = click).testTag("course-color-mode-${item.name}"), contentAlignment = Alignment.Center,
) { Text(item.label, color = if (selected) Color.White else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }

@Composable private fun ColorGridPicker(color: String, update: (String) -> Unit, dark: Boolean, enabled: Boolean) = Column(Modifier.padding(top = 10.dp).testTag("course-color-grid")) {
    Text("COLOR MATRIX", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
    CourseColorVisualSpec.grid.chunked(6).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { option ->
        val selected = option.equals(color, true)
        Box(Modifier.weight(1f).heightIn(min = 36.dp).padding(vertical = 3.dp).clickable(enabled = enabled) { update(option) }.semantics { contentDescription = "网格颜色 $option" }.testTag("course-color-grid-${option.removePrefix("#")}"), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().background(courseColor(option), TerminalShape).border(if (selected) 3.dp else 1.dp, if (selected) SignalYellow else terminalBorder(dark), TerminalShape), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = if (option == "#FFFFFF") InverseSurface else Color.White, fontWeight = FontWeight.Black) }
        }
    } } }
}

@Composable private fun SpectrumPicker(color: String, update: (String) -> Unit, dark: Boolean, enabled: Boolean) {
    val hsv = hsvParts(color); var areaSize by remember { mutableStateOf(IntSize.Zero) }; var hueSize by remember { mutableStateOf(IntSize.Zero) }
    fun updateSpectrum(point: Offset) { if (enabled && areaSize.width > 0 && areaSize.height > 0) update(spectrumHexAt(point.x / areaSize.width, point.y / areaSize.height, hsv[0])) }
    fun updateHue(point: Offset) { if (enabled && hueSize.width > 0) update(hsvHex((point.x / hueSize.width * 360f).toInt(), hsv[1], hsv[2])) }
    Column(Modifier.padding(top = 10.dp).testTag("course-color-spectrum")) {
        Text("HSV SPECTRUM", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 7.dp).onSizeChanged { areaSize = it }.pointerInput(hsv[0], enabled) { detectTapGestures { updateSpectrum(it) } }.pointerInput(hsv[0], enabled, "drag") { detectDragGestures(onDragStart = { updateSpectrum(it) }, onDrag = { change, _ -> updateSpectrum(change.position) }) }.testTag("course-color-spectrum-area")) {
            val columns = 24; val rows = 14
            repeat(columns) { x -> repeat(rows) { y -> drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(hsv[0].toFloat(), x.toFloat() / (columns - 1), 1f - y.toFloat() / (rows - 1)))), Offset(x * size.width / columns, y * size.height / rows), Size(size.width / columns + 1f, size.height / rows + 1f)) } }
            drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(hsv[1] / 100f * size.width, (1f - hsv[2] / 100f) * size.height), style = Stroke(2.dp.toPx()))
        }
        Text("HUE  ${hsv[0]}°", Modifier.padding(top = 8.dp), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Canvas(Modifier.fillMaxWidth().height(32.dp).onSizeChanged { hueSize = it }.pointerInput(enabled, hsv[1], hsv[2]) { detectTapGestures { updateHue(it) } }.pointerInput(enabled, hsv[1], hsv[2], "hue-drag") { detectDragGestures(onDragStart = { updateHue(it) }, onDrag = { change, _ -> updateHue(change.position) }) }.testTag("course-color-spectrum-hue")) {
            repeat(36) { i -> drawRect(Color(AndroidColor.HSVToColor(floatArrayOf(i * 10f, 1f, 1f))), Offset(i * size.width / 36f, 0f), Size(size.width / 36f + 1f, size.height)) }
            drawLine(Color.White, Offset(hsv[0] / 360f * size.width, 0f), Offset(hsv[0] / 360f * size.width, size.height), 2.dp.toPx())
        }
    }
}

@Composable private fun RgbSliderPicker(editor: CourseEditorState, actions: QingKeAppActions, dark: Boolean) {
    val rgb = rgbParts(editor.color)
    Column(Modifier.padding(top = 10.dp).testTag("course-color-sliders")) {
        Text("RGB SLIDERS", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black)
        RgbChannelSlider("R", rgb[0], Color.Red, { actions.updateCourseColor(rgbHex(it, rgb[1], rgb[2])) }, dark, !editor.isInFlight, "course-r-slider")
        RgbChannelSlider("G", rgb[1], Color.Green, { actions.updateCourseColor(rgbHex(rgb[0], it, rgb[2])) }, dark, !editor.isInFlight, "course-g-slider")
        RgbChannelSlider("B", rgb[2], Color.Blue, { actions.updateCourseColor(rgbHex(rgb[0], rgb[1], it)) }, dark, !editor.isInFlight, "course-b-slider")
        OutlinedTextField(editor.colorInput, actions.updateCourseColorInput, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("course-custom-color-input"), label = { Text("HEX / ADVANCED") }, singleLine = true, isError = !editor.colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$")), enabled = !editor.isInFlight, shape = TerminalShape)
        if (!editor.colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$"))) Text("请输入严格的 #RRGGBB", color = Danger, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun RgbChannelSlider(label: String, value: Int, tint: Color, update: (Int) -> Unit, dark: Boolean, enabled: Boolean, tag: String) {
    var trackSize by remember { mutableStateOf(IntSize.Zero) }
    fun updateAt(point: Offset) { if (enabled && trackSize.width > 0) update((point.x / trackSize.width * 255f).toInt().coerceIn(0, 255)) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label  ${value.toString().padStart(3, '0')}", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
        Canvas(Modifier.weight(1f).height(32.dp).onSizeChanged { trackSize = it }.pointerInput(value, enabled) { detectTapGestures { updateAt(it) } }.pointerInput(value, enabled, "rgb-drag") { detectDragGestures(onDragStart = { updateAt(it) }, onDrag = { change, _ -> updateAt(change.position) }) }.testTag(tag)) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black, tint)), size = size)
            drawLine(Color.White, Offset(value / 255f * size.width, 0f), Offset(value / 255f * size.width, size.height), 2.dp.toPx())
        }
    }
}

private fun weekdayName(value: Int) = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日").getOrElse(value - 1) { "星期" }
private fun periodDescription(semester: com.qingke.schedule.domain.Semester?, number: Int, start: Boolean): String {
    val period = semester?.periods?.firstOrNull { it.number == number }
    return "第 $number 节${period?.let { " · ${if (start) it.startTime else it.endTime}" } ?: ""}"
}

@Composable private fun EditorDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tag: String, dark: Boolean) = TerminalDialog(
    code = when (tag) { "course-conflict-confirm" -> "WARNING / CONFLICT"; "course-discard-confirm" -> "WARNING / UNSAVED"; else -> "DANGER / DELETE" },
    status = when (tag) { "course-conflict-confirm" -> "SCHEDULE COLLISION"; "course-discard-confirm" -> "DISCARD CHANGES"; else -> "IRREVERSIBLE" },
    dismiss = if (tag == "course-discard-confirm") "继续编辑" else if (tag == "course-delete-confirm") "取消" else "返回修改", title = title, message = message, confirm = confirm, onConfirm = onConfirm, onDismiss = onDismiss, tag = tag, dark = dark,
)

@Composable private fun TerminalDialog(code: String, title: String, message: String = "", confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tag: String, dismissTag: String? = "terminal-dialog-dismiss", confirmTag: String = "$tag-confirm", dismiss: String = "返回修改", status: String = "ACTION REQUIRED", dark: Boolean = isSystemInDarkTheme(), messageContent: (@Composable () -> Unit)? = null) = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)).testTag("$tag-backdrop"), contentAlignment = Alignment.Center) {
    val danger = code.startsWith("DANGER") || status == "DISCARD CHANGES"
    val tone = if (danger) Danger else SignalYellow
    Column(Modifier.padding(24.dp).fillMaxWidth().terminalModalSurface(dark = dark, accent = tone).padding(16.dp).testTag(tag)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(code, color = tone, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.testTag("$tag-code")); Spacer(Modifier.weight(1f)); Box(Modifier.size(7.dp).background(tone, androidx.compose.foundation.shape.CircleShape).testTag("$tag-status-dot")) }
        Text(status, color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp).background(tone).padding(horizontal = 6.dp, vertical = 3.dp).testTag("$tag-status"))
        Text(title, color = terminalText(dark), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        if (message.isNotEmpty()) Text(message, color = terminalSecondary(dark), modifier = Modifier.padding(top = 8.dp))
        messageContent?.let { Column(Modifier.padding(top = 8.dp)) { it() } }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (dismissTag != null) OutlinedButton(onDismiss, Modifier.weight(1f).heightIn(min = 46.dp).testTag(dismissTag), shape = TerminalShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = terminalText(dark))) { Text(dismiss) }; Button(onConfirm, Modifier.weight(1f).heightIn(min = 46.dp).testTag(confirmTag), shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = if (danger) Danger else SignalYellow, contentColor = InverseSurface)) { Text(confirm) } }
    }
}

private fun conflictMessage(conflicts: List<com.qingke.schedule.domain.ScheduleConflict>): String {
    val names = conflicts.map { it.existingCourse.name }.filter { it.isNotBlank() }.distinct().sorted()
    val weeks = conflicts.flatMap { it.weeks }.distinct().sorted()
    val summary = if (weeks.size <= 6) weeks.joinToString("、") { "第${it}周" } else "第${weeks.firstOrNull() ?: 0}周至第${weeks.lastOrNull() ?: 0}周"
    return "与${names.joinToString("、").ifEmpty { "已有课程" }}在${summary}存在时间冲突。"
}

internal fun rgbParts(hex: String): IntArray = if (hex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) intArrayOf(hex.substring(1, 3).toInt(16), hex.substring(3, 5).toInt(16), hex.substring(5, 7).toInt(16)) else intArrayOf(40, 185, 214)

internal fun rgbHex(red: Int, green: Int, blue: Int) = "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

internal fun hsvParts(hex: String): IntArray {
    val rgb = rgbParts(hex); val red = rgb[0] / 255f; val green = rgb[1] / 255f; val blue = rgb[2] / 255f
    val maximum = maxOf(red, green, blue); val minimum = minOf(red, green, blue); val delta = maximum - minimum
    val hue = if (delta == 0f) 0f else when (maximum) { red -> 60f * (((green - blue) / delta) % 6f); green -> 60f * ((blue - red) / delta + 2f); else -> 60f * ((red - green) / delta + 4f) }
    return intArrayOf(((hue + 360f) % 360f).toInt(), if (maximum == 0f) 0 else (delta / maximum * 100).toInt(), (maximum * 100).toInt())
}

internal fun hsvHex(hue: Int, saturation: Int, value: Int): String {
    val normalizedHue = ((hue % 360) + 360) % 360; val saturationFraction = saturation.coerceIn(0, 100) / 100f; val valueFraction = value.coerceIn(0, 100) / 100f
    val chroma = valueFraction * saturationFraction; val secondary = chroma * (1f - kotlin.math.abs((normalizedHue / 60f) % 2f - 1f)); val match = valueFraction - chroma
    val (red, green, blue) = when (normalizedHue) { in 0..59 -> Triple(chroma, secondary, 0f); in 60..119 -> Triple(secondary, chroma, 0f); in 120..179 -> Triple(0f, chroma, secondary); in 180..239 -> Triple(0f, secondary, chroma); in 240..299 -> Triple(secondary, 0f, chroma); else -> Triple(chroma, 0f, secondary) }
    return rgbHex(((red + match) * 255).toInt(), ((green + match) * 255).toInt(), ((blue + match) * 255).toInt())
}

/** HSV saturation/value plane: left/right map to 0/100 saturation, top/bottom to 100/0 value. */
internal fun spectrumHexAt(xFraction: Float, yFraction: Float, hue: Int): String = hsvHex(hue, (xFraction.coerceIn(0f, 1f) * 100).toInt(), ((1f - yFraction.coerceIn(0f, 1f)) * 100).toInt())
