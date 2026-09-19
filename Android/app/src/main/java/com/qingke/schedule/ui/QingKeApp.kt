package com.qingke.schedule.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.os.Build
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import com.qingke.schedule.domain.Period
import com.qingke.schedule.domain.RepeatRule
import com.qingke.schedule.domain.ScheduleRules
import com.qingke.schedule.domain.lunchBreakOverlappingPeriods
import com.qingke.schedule.presentation.ScheduleDisplayText
import com.qingke.schedule.presentation.TodayCourseItem
import com.qingke.schedule.presentation.TodaySchedulePresentation
import com.qingke.schedule.preferences.AcademicCalendarPreferences
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
import com.qingke.schedule.viewmodel.LunchBreakConflict
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
    /** API 28+ weight 100 requests the actual condensed ultra-light face; older Android versions use their real thin face safely. */
    val dayNumberTypeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), 100, false) else Typeface.create("sans-serif-thin", Typeface.NORMAL)
    val dayNumber = FontFamily(dayNumberTypeface)
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
    val setWeekendsAreNonTeachingDays: (Boolean) -> Unit = {},
    val addNonTeachingDate: (LocalDate) -> Unit = {},
    val removeNonTeachingDate: (String) -> Unit = {},
    val addMakeupTeachingDay: (LocalDate, Int) -> Unit = { _, _ -> },
    val removeMakeupTeachingDay: (String) -> Unit = {},
    val setLunchBreakEnabled: (Boolean) -> Unit = {},
    val requestLunchBreakTimes: (LocalTime, LocalTime) -> Unit = { _, _ -> },
    val confirmLunchBreakConflict: () -> Unit = {},
    val dismissLunchBreakConflict: () -> Unit = {},
)

@Composable
fun QingKeApp(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val courseSuccess by viewModel.courseSuccess.collectAsStateWithLifecycle()
    val semesterSuccess by viewModel.semesterSuccess.collectAsStateWithLifecycle()
    val lunchBreakConflict by viewModel.lunchBreakConflict.collectAsStateWithLifecycle()
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
            setWeekendsAreNonTeachingDays = viewModel::setWeekendsAreNonTeachingDays,
            addNonTeachingDate = viewModel::addNonTeachingDate, removeNonTeachingDate = viewModel::removeNonTeachingDate,
            addMakeupTeachingDay = viewModel::addMakeupTeachingDay, removeMakeupTeachingDay = viewModel::removeMakeupTeachingDay,
            setLunchBreakEnabled = viewModel::setLunchBreakEnabled, requestLunchBreakTimes = viewModel::requestLunchBreakTimes,
            confirmLunchBreakConflict = viewModel::confirmLunchBreakDespiteConflicts, dismissLunchBreakConflict = viewModel::dismissLunchBreakConfirmation,
        ),
        currentTime, editor, courseSuccess, viewModel::consumeCourseSuccess,
        semesterSuccess, viewModel::consumeSemesterSuccess, lunchBreakConflict,
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
    semesterSuccess: String? = null,
    consumeSemesterSuccess: () -> Unit = {},
    lunchBreakConflict: LunchBreakConflict? = null,
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
            LoadStatus.READY -> if (state.needsOnboarding) form?.let { OnboardingScreen(it, state.preferences.academicCalendar, state.data.semester?.periods.orEmpty(), state.isSaving, actions, dark, lunchBreakConflict) } ?: LoadingScreen()
            else MainShell(
                state, selectedTab, currentTime, actions, form = form,
                courseSuccess = if (editor == null) courseSuccess else null, consumeCourseSuccess = consumeCourseSuccess,
                semesterSuccess = semesterSuccess, consumeSemesterSuccess = consumeSemesterSuccess,
                lunchBreakConflict = lunchBreakConflict,
            )
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

@Composable private fun OnboardingScreen(
    form: SemesterFormState,
    calendar: AcademicCalendarPreferences,
    savedPeriods: List<Period>,
    saving: Boolean,
    actions: QingKeAppActions,
    dark: Boolean,
    lunchBreakConflict: LunchBreakConflict? = null,
) {
    val timePicker = remember { TerminalTimePickerState() }
    val calendarUi = rememberAcademicCalendarUiState(calendar.lunchBreak, form.startDate)
    Scaffold(
        topBar = { TerminalToolbar("首次设置", "INITIAL SETUP", "onboarding-toolbar", saving, actions.saveSemester) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).testTag("onboarding-screen")) {
                BrandHeader(dark, code = "SETUP / 00", tag = "onboarding-brand-header")
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
                    TerminalIntro(dark, onboarding = true)
                    TerminalSemesterForm("onboarding", form, calendar, calendarUi, savedPeriods, dark, actions, timePicker, saving, "创建课表", "INITIALIZE TERMINAL")
                }
            }
            TerminalTimePickerHost(form, dark, actions, timePicker)
            AcademicCalendarTimePickerHost(calendarUi, dark, actions)
            AcademicCalendarConflictHost(lunchBreakConflict, calendar, calendarUi, dark, actions)
        }
    }
}

/** Shared settings/onboarding date control: it lives inside a [TerminalFormSection] panel and keeps a form divider before the inline calendar. */
@Composable private fun TerminalDateControl(date: LocalDate, update: (LocalDate) -> Unit, dark: Boolean, dividerTag: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var shownYear by rememberSaveable { mutableIntStateOf(date.year) }
    var shownMonth by rememberSaveable { mutableIntStateOf(date.monthValue) }
    val month = YearMonth.of(shownYear, shownMonth)
    val chineseDate = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    Column(Modifier.fillMaxWidth().testTag("semester-start-date-container")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded }.testTag("semester-start-date")
                .semantics { contentDescription = "开始日期，$chineseDate，${if (expanded) "收起日历" else "展开日历"}" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("开始日期", color = terminalText(dark), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(chineseDate, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            TerminalChevron(expanded, terminalText(dark), "semester-start-date-chevron")
        }
        if (expanded) {
            TerminalFormDivider(dark, dividerTag)
            InlineMonthCalendar(month, date, dark, { selected -> update(selected) }, { next ->
                val changed = if (next) month.plusMonths(1) else month.minusMonths(1)
                shownYear = changed.year; shownMonth = changed.monthValue
            })
        }
    }
}

/** Settings-page variant of [DateControl]: it sits inside a [TerminalFormSection] panel, and iOS keeps a form divider before the inline calendar. */


@Composable private fun InlineMonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    dark: Boolean,
    select: (LocalDate) -> Unit,
    changeMonth: (Boolean) -> Unit,
    tagPrefix: String = "semester-start-date",
    controlPrefix: String = "semester",
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).testTag("$tagPrefix-calendar")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarMonthButton("‹", "上一个月", "$controlPrefix-calendar-previous") { changeMonth(false) }
            Text(month.format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,)
            CalendarMonthButton("›", "下一个月", "$controlPrefix-calendar-next") { changeMonth(true) }
        }
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { day -> Text(day, Modifier.weight(1f), color = terminalSecondary(dark), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        SemesterMonthGrid.dates(month).chunked(SemesterMonthGrid.columns).forEach { week ->
            Row(Modifier.fillMaxWidth()) { week.forEach { candidate ->
                val isSelected = candidate == selected
                val candidateTag = candidate?.let { "$controlPrefix-calendar-day-$it" } ?: "$controlPrefix-calendar-empty"
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

/** Shared settings/onboarding week control: borderless stepper glyphs keep the single form-panel outline, matching the iOS Stepper. */
@Composable private fun TerminalWeekControl(value: Int, update: (Int) -> Unit, dark: Boolean) = Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("总周数：$value", color = terminalText(dark), modifier = Modifier.weight(1f).testTag("semester-total-weeks"))
    StepperGlyphButton("−", "semester-weeks-minus", value > 1) { update(value - 1) }
    Spacer(Modifier.width(4.dp))
    StepperGlyphButton("+", "semester-weeks-plus", value < 52) { update(value + 1) }
}

/** Settings-page variant of [WeekControl]: borderless stepper glyphs keep the single form-panel outline, matching the iOS Stepper. */


/** Borderless stepper glyph: the iOS Stepper look, so the form panel keeps a single outline. */
@Composable private fun StepperGlyphButton(symbol: String, tag: String, enabled: Boolean, action: () -> Unit) = Box(
    Modifier.size(48.dp).alpha(if (enabled) 1f else .38f).clickable(enabled = enabled, onClick = action).testTag(tag),
    contentAlignment = Alignment.Center,
) { Text(symbol, color = QingKeCyan, fontSize = 22.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif) }

/** Shared settings/onboarding period row: heading, delete affordance and both time cells live inside one [TerminalFormSection] panel. */
@Composable private fun TerminalPeriodRow(
    period: PeriodFormState,
    count: Int,
    dark: Boolean,
    openStart: () -> Unit,
    openEnd: () -> Unit,
    remove: () -> Unit,
) = Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).testTag("period-${period.id}-row")) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("第 ${period.number} 节", color = terminalText(dark), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f).testTag("period-${period.id}-label"))
        if (count > 1) Box(
            Modifier.size(48.dp).clickable(onClick = remove).testTag("period-${period.id}-delete")
                .semantics { contentDescription = "删除第 ${period.number} 节" },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Danger, fontWeight = FontWeight.Black, fontSize = 15.sp) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PeriodTimeCell("开始", period.start, "period-${period.id}-start", dark, Modifier.weight(1f), openStart)
        PeriodTimeCell("结束", period.end, "period-${period.id}-end", dark, Modifier.weight(1f), openEnd)
    }
}

/** Settings-page variant of [PeriodRow]: the row, delete affordance and time cells all live inside one [TerminalFormSection] panel. */


/** iOS compact DatePicker cell: one fill colour instead of a second outline inside the form panel. */
@Composable private fun PeriodTimeCell(label: String, value: LocalTime, tag: String, dark: Boolean, modifier: Modifier, pick: () -> Unit) = Row(
    modifier.heightIn(min = 48.dp)
        .background(if (dark) Color(0xFF202D31) else Color(0xFFDCE4E3), TerminalShape)
        .clickable(onClick = pick).padding(horizontal = 12.dp)
        .testTag(tag).semantics { contentDescription = "${label}时间，$value" },
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    Spacer(Modifier.width(8.dp))
    Text(value.toString(), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable private fun MainShell(
    state: ScheduleState,
    selected: MainTab,
    currentTime: LocalDateTime,
    actions: QingKeAppActions,
    form: SemesterFormState? = null,
    courseSuccess: String? = null,
    consumeCourseSuccess: () -> Unit = {},
    semesterSuccess: String? = null,
    consumeSemesterSuccess: () -> Unit = {},
    lunchBreakConflict: LunchBreakConflict? = null,
) {
    val dark = state.preferences.appearanceMode == AppearanceMode.DARK ||
        (state.preferences.appearanceMode == AppearanceMode.SYSTEM && isSystemInDarkTheme())
    Box(Modifier.fillMaxSize().testTag("main-shell")) {
        TerminalBackdrop(dark)
        when (selected) {
            MainTab.TODAY -> TodayScheduleScreen(state, currentTime, actions.refreshTime, actions.openCourseAt, dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SCHEDULE -> WeekScheduleScreen(state, currentTime, actions, dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SETTINGS -> SemesterSettingsScreen(
                form, state.preferences.academicCalendar, state.data.semester?.periods.orEmpty(), state.isSaving, actions, dark,
                lunchBreakConflict, Modifier.fillMaxSize().padding(bottom = 82.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (selected == MainTab.TODAY || selected == MainTab.SCHEDULE) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    TodayAddButton(actions.openAddCourse, if (selected == MainTab.TODAY) "today-add-course" else "week-add-course")
                }
                Spacer(Modifier.height(8.dp))
            }
            courseSuccess?.let {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { CourseSuccessNotice(it, dark, consumeCourseSuccess) }
            }
            semesterSuccess?.let {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) { SemesterSuccessNotice(it, dark, consumeSemesterSuccess) }
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
    drawRoundRect(color, androidx.compose.ui.geometry.Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke), CornerRadius(3.3.dp.toPx()), style = Stroke(stroke))
    drawLine(color, androidx.compose.ui.geometry.Offset(plusInset, centerY), androidx.compose.ui.geometry.Offset(size.width - plusInset, centerY), stroke)
    drawLine(color, androidx.compose.ui.geometry.Offset(centerX, plusInset), androidx.compose.ui.geometry.Offset(centerX, size.height - plusInset), stroke)
}

/** Real A06 entry: the iOS "学期与节次" page, reusing the onboarding field widgets and save path. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SemesterSettingsScreen(
    form: SemesterFormState?,
    calendar: AcademicCalendarPreferences,
    savedPeriods: List<Period>,
    saving: Boolean,
    actions: QingKeAppActions,
    dark: Boolean,
    lunchBreakConflict: LunchBreakConflict? = null,
    modifier: Modifier = Modifier,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val timePicker = remember { TerminalTimePickerState() }
    val calendarUi = form?.let { rememberAcademicCalendarUiState(calendar.lunchBreak, it.startDate) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        actions.refreshTime()
        scope.launch { delay(450); refreshing = false }
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(refreshing, ::refresh, modifier.fillMaxSize().testTag("settings-refresh-container")) {
            Column(Modifier.statusBarsPadding()) {
                TerminalToolbar("学期与节次", "SYSTEM CONFIG", "settings-toolbar", saving, actions.saveSemester)
                BrandHeader(dark, code = "SYSTEM / 03", tag = "settings-brand-header")
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp).verticalScroll(rememberScrollState()).testTag("settings-screen")) {
                    if (refreshing) Row(Modifier.fillMaxWidth().testTag("settings-refresh-status"), verticalAlignment = Alignment.CenterVertically) {
                        Text("刷新中", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text("SYNC / LOCAL", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    TerminalIntro(dark, onboarding = false)
                    if (form == null) {
                        Text("正在准备学期设置…", color = terminalSecondary(dark), modifier = Modifier.padding(top = 12.dp).testTag("settings-pending"))
                        Spacer(Modifier.height(100.dp).testTag("settings-bottom-spacer"))
                    } else if (calendarUi != null) {
                        TerminalSemesterForm("settings", form, calendar, calendarUi, savedPeriods, dark, actions, timePicker, saving, "保存学期设置", "COMMIT CHANGES")
                    }
                }
            }
        }
        form?.let { TerminalTimePickerHost(it, dark, actions, timePicker) }
        calendarUi?.let { AcademicCalendarTimePickerHost(it, dark, actions) }
        AcademicCalendarConflictHost(lunchBreakConflict, calendar, calendarUi, dark, actions)
    }
}



/** iOS `settingsHeader` save entry: plain signal text on the inverse bar, never a filled yellow button. */
@Composable private fun SettingsToolbarSave(saving: Boolean, save: () -> Unit) = Box(
    Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).clickable(enabled = !saving, onClick = save).testTag("semester-save-toolbar"),
    contentAlignment = Alignment.Center,
) { Text(if (saving) "保存中" else "保存", color = SignalYellow, fontWeight = FontWeight.Black, fontSize = 16.sp) }

/** iOS `TerminalFormSection`: one index header plus a single bordered panel with a cyan rail. */
@Composable private fun TerminalFormSection(
    number: String,
    title: String,
    detail: String,
    dark: Boolean,
    tag: String,
    panelTag: String,
    footer: String? = null,
    footerTag: String? = null,
    content: @Composable () -> Unit,
) = Column(Modifier.fillMaxWidth().padding(top = 5.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
    TerminalSectionHeader(number, title, detail, dark, tag, heavyIndex = true)
    Column(Modifier.fillMaxWidth().terminalFormPanel(dark).testTag(panelTag)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) { content() }
    }
    if (footer != null) Text(
        footer, color = terminalSecondary(dark), fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().testTag(footerTag ?: panelTag + "-footer"),
    )
}

/** iOS `TerminalAcrylicSurface` plus the 1dp edge and 3dp cyan rail; form panels carry no floating card shadow. */
private fun Modifier.terminalFormPanel(dark: Boolean): Modifier {
    val surface = if (dark) Color(0xE61A2527) else Color(0xDDFBFEFD)
    val highlight = if (dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .48f)
    return this
        .background(Brush.linearGradient(listOf(highlight, surface, surface.copy(alpha = .94f))), TerminalShape)
        .border(1.dp, terminalPanelEdge(dark), TerminalShape)
        .drawBehind { drawRect(QingKeCyan, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
}

/** iOS `TerminalFormDivider`: a hairline between the controls of one form panel. */
@Composable private fun TerminalFormDivider(dark: Boolean, tag: String? = null) = Box(
    Modifier.fillMaxWidth().height(1.dp).background(terminalBorder(dark)).then(if (tag != null) Modifier.testTag(tag) else Modifier),
)

/** iOS `chevron.up` / `chevron.down`, drawn so the collapse state stays visible and announced. */
@Composable private fun TerminalChevron(up: Boolean, color: Color, tag: String, description: String? = null) = Canvas(
    Modifier.size(14.dp).testTag(tag).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
) {
    val stroke = 2.dp.toPx()
    val leftX = size.width * 0.08f
    val rightX = size.width * 0.92f
    val midX = size.width / 2f
    val apexY = if (up) size.height * 0.32f else size.height * 0.68f
    val baseY = if (up) size.height * 0.68f else size.height * 0.32f
    drawLine(color, androidx.compose.ui.geometry.Offset(leftX, baseY), androidx.compose.ui.geometry.Offset(midX, apexY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, androidx.compose.ui.geometry.Offset(midX, apexY), androidx.compose.ui.geometry.Offset(rightX, baseY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
}

/** iOS `Image(systemName: "arrow.right")` on the commit card. */
@Composable private fun SaveCardArrow(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val stroke = 2.5.dp.toPx()
    val head = 9.dp.toPx()
    val centerY = size.height / 2f
    val startX = 1.dp.toPx()
    val endX = size.width - 1.dp.toPx()
    drawLine(color, androidx.compose.ui.geometry.Offset(startX, centerY), androidx.compose.ui.geometry.Offset(endX, centerY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, androidx.compose.ui.geometry.Offset(endX - head, centerY - head), androidx.compose.ui.geometry.Offset(endX, centerY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, androidx.compose.ui.geometry.Offset(endX - head, centerY + head), androidx.compose.ui.geometry.Offset(endX, centerY), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
}

/** iOS periods toggle: cyan label on the left, count and chevron on the right, inside the panel. */
@Composable private fun PeriodsToggleRow(expanded: Boolean, count: Int, toggle: () -> Unit, dark: Boolean) = Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = toggle)
        .semantics { contentDescription = "每日节次，$count 节，${if (expanded) "已展开" else "已收起"}" }
        .testTag("daily-periods-toggle"),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(if (expanded) "收起节次设置" else "展开节次设置", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Spacer(Modifier.weight(1f))
    Text("$count 节", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.testTag("daily-periods-count"))
    Spacer(Modifier.width(8.dp))
    TerminalChevron(expanded, QingKeCyan, "daily-periods-chevron", if (expanded) "收起节次箭头" else "展开节次箭头")
}

/** iOS `Label("添加节次", systemImage: "plus") ` inside the same periods panel. */
@Composable private fun AddPeriodRow(count: Int, add: () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = count < 20, onClick = add).testTag("add-period"),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("+", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 16.sp)
    Spacer(Modifier.width(9.dp))
    Text("添加节次", color = QingKeCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

/* Shared terminal form components: the onboarding and the settings page render the same
   panels, rows and time picker so the two screens cannot drift apart. */

internal enum class PeriodTimeField { START, END }

internal data class PeriodTimeTarget(val periodId: String, val field: PeriodTimeField)

/** Shared numeric selection behind [TerminalTimePickerOverlay]; each owner keeps its own instance. */
internal interface TerminalTimeSelection {
    val hour: Int
    val minute: Int

    fun selectHour(value: Int)

    fun selectMinute(value: Int)

    fun selectedTime(): LocalTime = LocalTime.of(hour, minute)
}

/** Shared time-picker state; the target is keyed by period id plus field, so adding or removing periods cannot shift it. */
internal class TerminalTimePickerState : TerminalTimeSelection {
    var target: PeriodTimeTarget? by mutableStateOf(null)
        private set
    override var hour: Int by mutableIntStateOf(0)
        private set
    override var minute: Int by mutableIntStateOf(0)
        private set

    fun open(target: PeriodTimeTarget, value: LocalTime) {
        this.target = target
        hour = value.hour
        minute = value.minute
    }

    fun close() { target = null }

    override fun selectHour(value: Int) { hour = value }

    override fun selectMinute(value: Int) { minute = value }
}

internal enum class CalendarTimeField { START, END }

/** Independent A07 state: the lunch-break picker never shares a target space with the semester period rows. */
internal class CalendarTimePickerState : TerminalTimeSelection {
    var field: CalendarTimeField? by mutableStateOf(null)
        private set
    override var hour: Int by mutableIntStateOf(0)
        private set
    override var minute: Int by mutableIntStateOf(0)
        private set

    fun open(field: CalendarTimeField, value: LocalTime) {
        this.field = field
        hour = value.hour
        minute = value.minute
    }

    fun close() { field = null }

    override fun selectHour(value: Int) { hour = value }

    override fun selectMinute(value: Int) { minute = value }
}

@Composable private fun TerminalToolbar(title: String, subtitle: String, tag: String, saving: Boolean, save: () -> Unit) = Column(
    Modifier.fillMaxWidth().background(InverseSurface).statusBarsPadding().testTag(tag),
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = Color(0xFFF1F5F4), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(subtitle, color = Color(0xB3F1F5F4), fontFamily = FontFamily.Monospace, fontSize = 8.sp, letterSpacing = 1.sp)
        }
        SettingsToolbarSave(saving, save)
    }
    Box(Modifier.fillMaxWidth().height(3.dp).background(SignalYellow))
}

/** Shared plain semester-name field; the iOS form keeps it as a plain text field inside the panel. */
@Composable private fun TerminalNameField(value: String, update: (String) -> Unit, dark: Boolean) = BasicTextField(
    value = value,
    onValueChange = update,
    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("semester-name"),
    singleLine = true,
    textStyle = androidx.compose.ui.text.TextStyle(color = terminalText(dark), fontWeight = FontWeight.Bold, fontSize = 16.sp),
    decorationBox = { innerTextField ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (value.isBlank()) Text("学期名称", color = terminalSecondary(dark), fontSize = 15.sp)
            innerTextField()
        }
    },
)

/** Shared terminal intro block: the iOS terminalIntro differs only by boot tag, copy and index. */
@Composable private fun TerminalIntro(dark: Boolean, onboarding: Boolean) = Row(
    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).testTag(if (onboarding) "onboarding-terminal-header" else "settings-terminal-header"),
    verticalAlignment = Alignment.Bottom,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (onboarding) "FIRST BOOT" else "CONFIGURATION", color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp,
            modifier = Modifier.background(if (onboarding) SignalYellow else QingKeCyan).padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(if (onboarding) "onboarding-status-tag" else "settings-status-tag"),
        )
        Text(
            if (onboarding) "首次设置" else "系统设置", color = terminalText(dark), fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.testTag(if (onboarding) "onboarding-title" else "settings-title"),
        )
        Text(
            if (onboarding) "配置学期与每日节次，完成后即可录入第一门课程。" else "管理学期与每日节次。",
            color = terminalSecondary(dark), fontSize = 13.sp,
        )
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(if (onboarding) "INIT" else "SYS", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(if (onboarding) "00" else "03", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, fontSize = 42.sp, lineHeight = 44.sp)
    }
}

/** Shared commit card: iOS keeps one card shape and only swaps the copy for the first boot. */
@Composable private fun TerminalCommitCard(saving: Boolean, title: String, subtitle: String, save: () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(top = 18.dp).clickable(enabled = !saving, onClick = save).background(InverseSurface).testTag("semester-save"),
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp).testTag("semester-save-body"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (saving) "正在保存…" else title, color = Color(0xFFF1F5F4), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.testTag("semester-save-title"))
            Text(subtitle, color = Color(0x9EF1F5F4), fontFamily = FontFamily.Monospace, fontSize = 8.sp, letterSpacing = 1.sp, modifier = Modifier.testTag("semester-save-subtitle"))
        }
        SaveCardArrow(Color(0xFFF1F5F4), Modifier.size(26.dp).testTag("semester-save-arrow"))
    }
    Box(Modifier.fillMaxWidth().height(4.dp).background(SignalYellow).testTag("semester-save-underline"))
}

/** Shared form body: both screens render the 01／02／03 panels, the period rows and the shared time picker hosts. */
@Composable private fun TerminalSemesterForm(
    prefix: String,
    form: SemesterFormState,
    calendar: AcademicCalendarPreferences,
    calendarUi: AcademicCalendarUiState,
    savedPeriods: List<Period>,
    dark: Boolean,
    actions: QingKeAppActions,
    timePicker: TerminalTimePickerState,
    saving: Boolean,
    commitTitle: String,
    commitSubtitle: String,
) {
    TerminalFormSection("01", "学期信息", "TERM", dark, prefix + "-semester-section", prefix + "-semester-panel") {
        TerminalNameField(form.name, actions.updateName, dark)
        TerminalFormDivider(dark, prefix + "-semester-divider")
        TerminalDateControl(form.startDate, actions.updateStartDate, dark, prefix + "-semester-divider")
        TerminalFormDivider(dark, prefix + "-semester-divider")
        TerminalWeekControl(form.totalWeeks, actions.updateTotalWeeks, dark)
    }
    TerminalFormSection(
        "02", "每日节次", "PERIODS / " + form.periods.size, dark, prefix + "-periods-section", prefix + "-periods-panel",
        footer = "教学周从开始日期所在周的周一算起；课程统一使用这里的节次时间。",
        footerTag = prefix + "-periods-footer",
    ) {
        PeriodsToggleRow(form.periodsExpanded, form.periods.size, actions.togglePeriods, dark)
        if (form.periodsExpanded) {
            TerminalFormDivider(dark, prefix + "-periods-divider")
            form.periods.forEachIndexed { index, period ->
                TerminalPeriodRow(
                    period, form.periods.size, dark,
                    openStart = { timePicker.open(PeriodTimeTarget(period.id, PeriodTimeField.START), period.start) },
                    openEnd = { timePicker.open(PeriodTimeTarget(period.id, PeriodTimeField.END), period.end) },
                    remove = { actions.removePeriod(period.id) },
                )
                if (index < form.periods.lastIndex) TerminalFormDivider(dark, prefix + "-periods-divider")
            }
            TerminalFormDivider(dark, prefix + "-periods-divider")
            AddPeriodRow(form.periods.size, actions.addPeriod)
        }
    }
    AcademicCalendarSection(calendar, calendarUi, savedPeriods, prefix, dark, actions)
    form.validationMessage?.let { ValidationNotice(it, dark = dark, tag = "semester-validation-error") }
    TerminalCommitCard(saving, commitTitle, commitSubtitle, actions.saveSemester)
    Spacer(Modifier.height(100.dp).testTag(prefix + "-bottom-spacer"))
}

/* A07 "03 教学日历": one shared implementation for the first-boot form and the settings form. */

/** iOS `Toggle` inside a terminal form panel: label and switch glyph share one 52dp row target. */
@Composable private fun TerminalToggleRow(label: String, checked: Boolean, tag: String, dark: Boolean, change: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = checked, role = Role.Switch, onValueChange = change)
        .testTag(tag).semantics { contentDescription = label },
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, color = terminalText(dark), fontSize = 14.sp, modifier = Modifier.weight(1f))
    TerminalSwitchGlyph(checked, dark, "$tag-switch")
}

/** Platform equivalent of the iOS switch, drawn so the on／off contrast survives both themes. */
@Composable private fun TerminalSwitchGlyph(checked: Boolean, dark: Boolean, tag: String) = Canvas(Modifier.size(48.dp).testTag(tag)) {
    val trackWidth = 46.dp.toPx()
    val trackHeight = 26.dp.toPx()
    val left = (size.width - trackWidth) / 2f
    val top = (size.height - trackHeight) / 2f
    val radius = trackHeight / 2f
    drawRoundRect(if (checked) QingKeCyan else if (dark) Color(0xFF243134) else Color(0xFFD5DDDC), Offset(left, top), Size(trackWidth, trackHeight), CornerRadius(radius))
    drawRoundRect(if (checked) QingKeCyan else terminalBorder(dark), Offset(left, top), Size(trackWidth, trackHeight), CornerRadius(radius), style = Stroke(1.dp.toPx()))
    drawCircle(
        if (checked) InverseSurface else if (dark) Color(0xFFF1F5F4) else Color(0xFFF7FAFA),
        radius - 3.dp.toPx(),
        Offset(if (checked) left + trackWidth - radius else left + radius, top + radius),
    )
}

/** iOS `ExceptionMode` segmented buttons: inverse fill for the selected mode, plain outline otherwise. */
@Composable private fun androidx.compose.foundation.layout.RowScope.CalendarModeButton(label: String, selected: Boolean, tag: String, dark: Boolean, select: () -> Unit) = Box(
    Modifier.weight(1f).heightIn(min = 48.dp)
        .background(if (selected) InverseSurface else Color.Transparent, TerminalShape)
        .border(1.dp, terminalBorder(dark), TerminalShape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = select)
        .testTag(tag),
    contentAlignment = Alignment.Center,
) { Text(label, color = if (selected) Color(0xFFF1F5F4) else terminalText(dark), fontWeight = FontWeight.Bold, fontSize = 14.sp) }

@Composable private fun CalendarExceptionHeader(title: String, tag: String, dark: Boolean) = Text(
    title, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp,
    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp).testTag(tag),
)

@Composable private fun CalendarExceptionRow(date: String, detail: String, tag: String, dark: Boolean, remove: () -> Unit) {
    val label = calendarExceptionDateLabel(date)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = terminalText(dark), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.testTag("$tag-date"))
            Text(detail, color = terminalSecondary(dark), fontSize = 12.sp, modifier = Modifier.testTag("$tag-detail"))
        }
        Box(
            Modifier.size(48.dp).clickable(onClick = remove).testTag("$tag-delete")
                .semantics { contentDescription = "删除 $label" },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = Danger, fontWeight = FontWeight.Black, fontSize = 15.sp) }
    }
}

private fun calendarExceptionDateLabel(date: String): String {
    val parsed = ScheduleRules.parseLocalDate(date) ?: return date
    return parsed.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.CHINA))
}

/** iOS `AcademicCalendarSettingsSection`: weekend switch, lunch break, exception mode, date and lists. */
@Composable private fun AcademicCalendarSection(
    calendar: AcademicCalendarPreferences,
    ui: AcademicCalendarUiState,
    savedPeriods: List<Period>,
    prefix: String,
    dark: Boolean,
    actions: QingKeAppActions,
) {
    val dateLabel = ui.selectedDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA))
    val lunchConflictPeriods = if (!calendar.lunchBreak.isEnabled) emptyList()
    else lunchBreakOverlappingPeriods(calendar.lunchBreak.startTime, calendar.lunchBreak.endTime, savedPeriods).map { it.number }
    TerminalFormSection(
        "03", "教学日历", "CALENDAR", dark, "$prefix-calendar-section", "$prefix-calendar-panel",
        footer = "停课日优先级最高；调课日可指定按某个星期的课表上课，适用于节假日调休。",
        footerTag = "$prefix-calendar-footer",
    ) {
        TerminalToggleRow("周末默认不上课", calendar.weekendsAreNonTeachingDays, "$prefix-calendar-weekends-toggle", dark, actions.setWeekendsAreNonTeachingDays)
        TerminalFormDivider(dark, "$prefix-calendar-divider")
        TerminalToggleRow("在周课表显示午休", calendar.lunchBreak.isEnabled, "$prefix-calendar-lunch-toggle", dark, actions.setLunchBreakEnabled)
        if (calendar.lunchBreak.isEnabled) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodTimeCell("开始", ui.lunchStart, "$prefix-calendar-lunch-start", dark, Modifier.weight(1f)) { ui.lunchPicker.open(CalendarTimeField.START, ui.lunchStart) }
                PeriodTimeCell("结束", ui.lunchEnd, "$prefix-calendar-lunch-end", dark, Modifier.weight(1f)) { ui.lunchPicker.open(CalendarTimeField.END, ui.lunchEnd) }
            }
            if (!ui.lunchBreakRangeIsValid) Text(
                "午休开始时间必须早于结束时间。", color = Danger, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("$prefix-calendar-lunch-error"),
            )
            if (lunchConflictPeriods.isNotEmpty()) Text(
                "午休与第 " + lunchConflictPeriods.joinToString("、") + " 节重叠，节次优先：周课表不会显示午休条。",
                color = Danger, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).testTag("$prefix-calendar-lunch-conflict-note"),
            )
        }
        TerminalFormDivider(dark, "$prefix-calendar-divider")
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarModeButton(CalendarExceptionMode.NON_TEACHING.label, ui.mode == CalendarExceptionMode.NON_TEACHING, "$prefix-calendar-mode-non-teaching", dark) { ui.mode = CalendarExceptionMode.NON_TEACHING }
            CalendarModeButton(CalendarExceptionMode.MAKEUP.label, ui.mode == CalendarExceptionMode.MAKEUP, "$prefix-calendar-mode-makeup", dark) { ui.mode = CalendarExceptionMode.MAKEUP }
        }
        TerminalFormDivider(dark, "$prefix-calendar-divider")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { ui.datePickerExpanded = !ui.datePickerExpanded }
                .testTag("$prefix-calendar-exception-date")
                .semantics { contentDescription = "日期，" + dateLabel + "，" + (if (ui.datePickerExpanded) "收起日历" else "展开日历") },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("日期", color = terminalText(dark), fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(dateLabel, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.testTag("$prefix-calendar-date-value"))
            Spacer(Modifier.width(8.dp))
            TerminalChevron(ui.datePickerExpanded, terminalText(dark), "$prefix-calendar-date-chevron")
        }
        if (ui.datePickerExpanded) {
            TerminalFormDivider(dark, "$prefix-calendar-divider")
            InlineMonthCalendar(
                ui.shownMonthValue, ui.selectedDate, dark, { ui.selectedDate = it },
                { next ->
                    val changed = if (next) ui.shownMonthValue.plusMonths(1) else ui.shownMonthValue.minusMonths(1)
                    ui.shownYear = changed.year
                    ui.shownMonth = changed.monthValue
                },
                tagPrefix = "$prefix-calendar-exception", controlPrefix = "$prefix-calendar-exception",
            )
        }
        if (ui.mode == CalendarExceptionMode.MAKEUP) {
            TerminalFormDivider(dark, "$prefix-calendar-divider")
            CompactPicker(
                label = "按课表上课", valueLabel = ScheduleDisplayText.weekdayName(ui.followsDayOfWeek),
                value = ui.followsDayOfWeek, choices = 1..7, choiceLabel = ScheduleDisplayText::weekdayName,
                update = { ui.followsDayOfWeek = it }, tag = "$prefix-calendar-makeup-weekday",
                dark = dark, enabled = true, visibleLabel = "按课表上课",
            )
        }
        TerminalFormDivider(dark, "$prefix-calendar-divider")
        Box(
            Modifier.fillMaxWidth().padding(vertical = 10.dp).heightIn(min = 50.dp).background(SignalYellow, TerminalShape)
                .clickable {
                    when (ui.mode) {
                        CalendarExceptionMode.NON_TEACHING -> actions.addNonTeachingDate(ui.selectedDate)
                        CalendarExceptionMode.MAKEUP -> actions.addMakeupTeachingDay(ui.selectedDate, ui.followsDayOfWeek)
                    }
                    ui.datePickerExpanded = false
                }.testTag("$prefix-calendar-add-exception"),
            contentAlignment = Alignment.Center,
        ) { Text(if (ui.mode == CalendarExceptionMode.NON_TEACHING) "添加停课日" else "添加调课日", color = InverseSurface, fontWeight = FontWeight.Black, fontSize = 15.sp) }
        if (calendar.nonTeachingDates.isNotEmpty()) {
            TerminalFormDivider(dark, "$prefix-calendar-divider")
            CalendarExceptionHeader("停课日 / OFF", "$prefix-calendar-non-teaching-header", dark)
            calendar.nonTeachingDates.forEach { date ->
                CalendarExceptionRow(date, "不显示课程", "$prefix-calendar-non-teaching-" + date, dark) { actions.removeNonTeachingDate(date) }
            }
        }
        if (calendar.makeupTeachingDays.isNotEmpty()) {
            TerminalFormDivider(dark, "$prefix-calendar-divider")
            CalendarExceptionHeader("调课日 / MAKEUP", "$prefix-calendar-makeup-header", dark)
            calendar.makeupTeachingDays.forEach { day ->
                CalendarExceptionRow(day.date, "按" + ScheduleDisplayText.weekdayName(day.followsDayOfWeek) + "课表", "$prefix-calendar-makeup-" + day.date, dark) { actions.removeMakeupTeachingDay(day.date) }
            }
        }
    }
}

/** Screen-level host for the lunch-break picker: it stays outside the scrolling form, like the period picker. */
@Composable private fun AcademicCalendarTimePickerHost(ui: AcademicCalendarUiState, dark: Boolean, actions: QingKeAppActions) {
    val field = ui.lunchPicker.field ?: return
    TerminalTimePickerOverlay(
        title = if (field == CalendarTimeField.START) "午休开始时间" else "午休结束时间",
        state = ui.lunchPicker,
        dark = dark,
        onCancel = { ui.lunchPicker.close() },
        onConfirm = {
            val value = ui.lunchPicker.selectedTime()
            if (field == CalendarTimeField.START) ui.lunchStart = value else ui.lunchEnd = value
            if (ui.lunchBreakRangeIsValid) actions.requestLunchBreakTimes(ui.lunchStart, ui.lunchEnd)
            ui.lunchPicker.close()
        },
        tagPrefix = "terminal-lunch-time-picker",
    )
}

/**
 * P3-07-R1 one-off warning: a valid lunch range that overlaps a persisted period waits here for an
 * explicit decision. "返回修改" restores the stored range; "仍然保存" keeps the range and the week
 * matrix keeps hiding the lunch break row.
 */
@Composable private fun AcademicCalendarConflictHost(
    conflict: LunchBreakConflict?,
    calendar: AcademicCalendarPreferences,
    ui: AcademicCalendarUiState?,
    dark: Boolean,
    actions: QingKeAppActions,
) {
    val value = conflict ?: return
    TerminalDialog(
        code = "WARNING / CONFLICT",
        status = "PERIOD OVERLAP",
        title = "午休与节次重叠",
        message = lunchBreakConflictMessage(value),
        confirm = "仍然保存",
        dismiss = "返回修改",
        onDismiss = {
            ui?.let { state ->
                state.lunchStart = ScheduleRules.parseLocalTime(calendar.lunchBreak.startTime) ?: state.lunchStart
                state.lunchEnd = ScheduleRules.parseLocalTime(calendar.lunchBreak.endTime) ?: state.lunchEnd
            }
            actions.dismissLunchBreakConflict()
        },
        onConfirm = actions.confirmLunchBreakConflict,
        tag = "calendar-lunch-conflict",
        danger = true,
        dark = dark,
    )
}

/**
 * P3-07-R1 message: the saved semester and the form can disagree while period times are unsaved, so the
 * wording states which periods overlap and whether the week matrix will hide the lunch row afterwards.
 */
private fun lunchBreakConflictMessage(conflict: LunchBreakConflict): String {
    val saved = conflict.persistedPeriodNumbers
    val draft = conflict.draftPeriodNumbers
    val overlap = when {
        saved.isEmpty() ->
            "与当前节次设置的第 " + draft.joinToString("、") + " 节时间重叠，这些节次时间尚未保存到学期设置。"
        draft.isEmpty() || draft == saved -> "与第 " + saved.joinToString("、") + " 节时间重叠。"
        else ->
            "与已保存的第 " + saved.joinToString("、") + " 节以及当前节次设置的第 " + draft.joinToString("、") + " 节时间重叠。"
    }
    val consequence = if (conflict.hidesWeekMatrixRow) {
        "节次优先：仍然保存后周课表不会显示该午休条。"
    } else {
        "节次优先：周课表按已保存的节次时间判断，保存学期设置后才会隐藏该午休条。"
    }
    return overlap + consequence
}

/** Shared time-picker host: it renders only while the target period still exists, so add／remove can never address the wrong row. */
@Composable private fun TerminalTimePickerHost(form: SemesterFormState, dark: Boolean, actions: QingKeAppActions, timePicker: TerminalTimePickerState) {
    val target = timePicker.target ?: return
    val period = form.periods.firstOrNull { it.id == target.periodId }
    if (period == null) {
        LaunchedEffect(target) { timePicker.close() }
        return
    }
    TerminalTimePickerOverlay(
        title = "第 " + period.number + " 节" + if (target.field == PeriodTimeField.START) " 开始时间" else " 结束时间",
        state = timePicker,
        dark = dark,
        onCancel = { timePicker.close() },
        onConfirm = {
            val value = timePicker.selectedTime()
            if (target.field == PeriodTimeField.START) actions.updatePeriodStart(target.periodId, value) else actions.updatePeriodEnd(target.periodId, value)
            timePicker.close()
        },
    )
}

/** Shared 24-hour terminal time picker: two numeric columns, a live HH:MM readout and cancel／confirm actions. */
@Composable internal fun TerminalTimePickerOverlay(
    title: String,
    state: TerminalTimeSelection,
    dark: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    tagPrefix: String = "terminal-time-picker",
) {
    BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)).testTag(tagPrefix + "-backdrop"), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).widthIn(max = 420.dp).terminalModalSurface(dark, QingKeCyan).testTag(tagPrefix)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("TIME SELECT", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.testTag(tagPrefix + "-code"))
                Spacer(Modifier.weight(1f))
                Text(title, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.testTag(tagPrefix + "-title"))
            }
            Text(
                "%02d:%02d".format(state.hour, state.minute), color = terminalText(dark),
                fontFamily = TerminalTypography.indexFont, fontWeight = FontWeight.Black, fontSize = 34.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = 2.dp, bottom = 8.dp).testTag(tagPrefix + "-value"),
            )
            TerminalFormDivider(dark)
            Row(Modifier.fillMaxWidth().height(230.dp).testTag(tagPrefix + "-columns")) {
                TerminalNumberColumn("小时", 0..23, state.hour, tagPrefix + "-hour", dark, Modifier.weight(1f)) { state.selectHour(it) }
                Box(Modifier.width(1.dp).fillMaxHeight().background(terminalBorder(dark)))
                TerminalNumberColumn("分钟", 0..59, state.minute, tagPrefix + "-minute", dark, Modifier.weight(1f)) { state.selectMinute(it) }
            }
            TerminalFormDivider(dark)
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TerminalPickerButton("取消", tagPrefix + "-cancel", false, dark, onCancel)
                TerminalPickerButton("确认", tagPrefix + "-confirm", true, dark, onConfirm)
            }
        }
    }
}

@Composable private fun TerminalNumberColumn(label: String, range: IntRange, selected: Int, tagPrefix: String, dark: Boolean, modifier: Modifier, select: (Int) -> Unit) {
    val itemHeight = 48.dp
    val scroll = rememberScrollState()
    val itemPx = with(androidx.compose.ui.platform.LocalDensity.current) { itemHeight.toPx() }
    LaunchedEffect(Unit) { runCatching { scroll.scrollTo((selected * itemPx).toInt()) } }
    Column(modifier) {
        Text(label, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 14.dp, top = 8.dp))
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).testTag(tagPrefix + "-list")) {
            range.forEach { value ->
                val active = value == selected
                Row(
                    Modifier.fillMaxWidth().height(itemHeight).background(if (active) QingKeCyan else Color.Transparent)
                        .clickable { select(value) }.testTag("%s-%02d".format(tagPrefix, value))
                        .semantics { contentDescription = "%s %02d".format(label, value) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("%02d".format(value), color = if (active) InverseSurface else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable private fun androidx.compose.foundation.layout.RowScope.TerminalPickerButton(label: String, tag: String, primary: Boolean, dark: Boolean, action: () -> Unit) = Box(
    Modifier.weight(1f).heightIn(min = 48.dp)
        .background(if (primary) SignalYellow else if (dark) Color(0xFF1F2C2F) else Color(0xFFE3E9E8), TerminalShape)
        .border(1.dp, terminalBorder(dark), TerminalShape)
        .clickable(onClick = action).testTag(tag),
    contentAlignment = Alignment.Center,
) { Text(label, color = if (primary) InverseSurface else terminalText(dark), fontWeight = FontWeight.Black, fontSize = 15.sp) }




@Composable private fun SemesterSuccessNotice(message: String, dark: Boolean, consume: () -> Unit) {
    LaunchedEffect(message) { delay(2_600); consume() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).terminalPanel(dark, SignalYellow, TerminalSurfaceLevel.ELEVATED)
            .padding(horizontal = 14.dp, vertical = 8.dp).testTag("semester-save-success"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(SignalYellow, androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(message, color = terminalText(dark), fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).testTag("semester-save-success-message"))
        Text("✓", color = SignalYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

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

@Composable private fun TodayAddButton(addCourse: () -> Unit, tag: String = "today-add-course") = Button(addCourse, Modifier.size(64.dp).testTag(tag).semantics { contentDescription = "添加课程" }, shape = TerminalShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) {
    Box(Modifier.fillMaxSize().drawBehind {
        val fold = 13.dp.toPx(); val shadowOffset = 1.dp.toPx(); val frameInset = 3.dp.toPx(); val frameStroke = 1.dp.toPx()
        val shadow = androidx.compose.ui.graphics.Path().apply { moveTo(size.width - fold - shadowOffset, shadowOffset); lineTo(size.width - shadowOffset, shadowOffset); lineTo(size.width - shadowOffset, fold + shadowOffset); close() }
        val foldPath = androidx.compose.ui.graphics.Path().apply { moveTo(size.width - fold, 0f); lineTo(size.width, 0f); lineTo(size.width, fold); close() }
        drawPath(shadow, InverseSurface.copy(alpha = .16f))
        drawPath(foldPath, Color.White.copy(alpha = .75f))
        drawRect(Color.White.copy(alpha = .75f), topLeft = Offset(frameInset + frameStroke / 2f, frameInset + frameStroke / 2f), size = Size(size.width - frameInset * 2f - frameStroke, size.height - frameInset * 2f - frameStroke), style = Stroke(frameStroke))
    }.testTag("today-add-visual"), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) { TodayAddPlus(InverseSurface); Text("ADD", fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.testTag("today-add-label")) } }
}

/** A 25dp light-weight plus keeps the ADD control independent of platform font glyph shapes. */
@Composable private fun TodayAddPlus(color: Color) = Canvas(Modifier.size(25.dp).testTag("today-add-plus")) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val inset = 4.dp.toPx()
    val stroke = 1.6.dp.toPx()
    drawLine(color, Offset(inset, center.y), Offset(size.width - inset, center.y), stroke, StrokeCap.Round)
    drawLine(color, Offset(center.x, inset), Offset(center.x, size.height - inset), stroke, StrokeCap.Round)
}

@Composable internal fun BrandHeader(dark: Boolean, code: String = "LOCAL / 01", tag: String = "today-brand-header") = Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).border(width = 0.dp, color = Color.Transparent)
        .drawBehind { drawLine(if (dark) Color.White.copy(alpha = .20f) else Color.Black.copy(alpha = .18f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.testTag(tag), verticalAlignment = Alignment.CenterVertically,
) {
    androidx.compose.foundation.Image(painterResource(if (dark) R.drawable.qingke_logo_dark else R.drawable.qingke_logo), "青课 QINGKE ACADEMIC TERMINAL", Modifier.width(154.dp).heightIn(min = 54.dp).testTag("today-brand-logo"), contentScale = ContentScale.Fit)
    Spacer(Modifier.weight(1f)); Text(code, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun TodayHero(now: LocalDateTime, semester: com.qingke.schedule.domain.Semester, presentation: TodaySchedulePresentation, dark: Boolean, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.width(TodayVisualSpec.dayColumnWidth)) {
        Text(now.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(Locale.US), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("today-month-code"))
        Text(now.format(DateTimeFormatter.ofPattern("dd", Locale.US)), color = terminalText(dark), fontFamily = TodayVisualSpec.dayNumber, fontWeight = FontWeight.Normal, fontSize = TodayVisualSpec.dayNumberSize, lineHeight = TodayVisualSpec.dayNumberSize, maxLines = 1, modifier = Modifier.testTag("today-day-number"))
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
                            Text("新建一门课程", color = terminalText(dark), fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin, modifier = Modifier.padding(start = 10.dp))
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

/** iOS uses Avenir Next Condensed Black for the section index; the platform mono face has no heavy cut, so pair the bold face with a black weight request. */
private object TerminalTypography {
    val indexFont = FontFamily(Typeface.create("monospace", Typeface.BOLD))
}

@Composable private fun TerminalSectionHeader(number: String, title: String, subtitle: String, dark: Boolean, tag: String, heavyIndex: Boolean = false) = Row(Modifier.fillMaxWidth().padding(top = 14.dp).drawBehind { drawLine(terminalBorder(dark), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 7.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
    Text(
        number, color = QingKeCyan,
        fontFamily = if (heavyIndex) TerminalTypography.indexFont else FontFamily.Monospace,
        fontWeight = FontWeight.Black,
        fontSize = if (heavyIndex) 15.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        lineHeight = if (heavyIndex) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        letterSpacing = if (heavyIndex) 0.6.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
        modifier = Modifier.testTag("$tag-number"),
    )
    Spacer(Modifier.width(9.dp)); Text(title, color = terminalText(dark), fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(subtitle, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
}

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

@Composable private fun CompactPicker(label: String, valueLabel: String, value: Int, choices: IntRange, choiceLabel: (Int) -> String, update: (Int) -> Unit, tag: String, dark: Boolean, enabled: Boolean, visibleLabel: String? = null) = Box(Modifier.fillMaxWidth()) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { expanded = true }.testTag(tag).semantics { contentDescription = "$label：$valueLabel" }, verticalAlignment = Alignment.CenterVertically) {
        if (visibleLabel != null) Text(visibleLabel, color = terminalText(dark), modifier = Modifier.weight(1f))
        Text(valueLabel, color = QingKeCyan, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = (if (visibleLabel == null) Modifier.weight(1f) else Modifier).testTag("$tag-value"))
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
        Box(Modifier.weight(1f).height(42.dp).padding(vertical = 3.dp).clickable(enabled = enabled) { update(option) }.semantics { contentDescription = "网格颜色 $option" }.testTag("course-color-grid-${option.removePrefix("#")}"), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(36.dp).background(courseColor(option), TerminalShape).border(if (selected) 3.dp else 1.dp, if (selected) SignalYellow else terminalBorder(dark), TerminalShape), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = if (option == "#FFFFFF") InverseSurface else Color.White, fontWeight = FontWeight.Black) }
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
        Text(label, color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(18.dp).testTag("$tag-label"))
        Text(value.toString().padStart(3, '0'), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false, modifier = Modifier.width(46.dp).testTag("$tag-value"))
        Spacer(Modifier.width(4.dp))
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

@Composable private fun TerminalDialog(code: String, title: String, message: String = "", confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tag: String, dismissTag: String? = "terminal-dialog-dismiss", confirmTag: String = "$tag-confirm", dismiss: String = "返回修改", status: String = "ACTION REQUIRED", dark: Boolean = isSystemInDarkTheme(), messageContent: (@Composable () -> Unit)? = null, danger: Boolean = code.startsWith("DANGER") || status == "DISCARD CHANGES") = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .72f)).testTag("$tag-backdrop"), contentAlignment = Alignment.Center) {
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
