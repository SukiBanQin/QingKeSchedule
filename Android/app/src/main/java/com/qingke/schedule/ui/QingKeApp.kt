package com.qingke.schedule.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            LoadStatus.READY -> if (state.needsOnboarding) form?.let { OnboardingScreen(it, state.isSaving, actions) } ?: LoadingScreen()
            else MainShell(state, selectedTab, currentTime, actions)
        }
        editor?.let { CourseEditorOverlay(it, state.data.semester, state.data.courses, dark, actions) }
        if (editor == null) courseSuccess?.let { CourseSuccessNotice(it, dark, consumeCourseSuccess) }
        state.error?.let { message -> if (state.loadStatus == LoadStatus.READY) ErrorDialog(message, actions.dismissError) }
    }
}

@Composable private fun CourseSuccessNotice(message: String, dark: Boolean, consume: () -> Unit) {
    LaunchedEffect(message) { delay(2_600); consume() }
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 104.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-success-notice")) {
            Text("OK", color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp)); Text(message, color = terminalText(dark), fontWeight = FontWeight.Bold)
        }
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

@Composable private fun OnboardingScreen(form: SemesterFormState, saving: Boolean, actions: QingKeAppActions) = Scaffold(
    topBar = { Row(Modifier.fillMaxWidth().background(InverseSurface).statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("首次设置", color = Color.White, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
        Button(actions.saveSemester, enabled = !saving, shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface), modifier = Modifier.heightIn(min = 48.dp).testTag("semester-save-toolbar")) { Text(if (saving) "保存中" else "保存") }
    } },
) { padding ->
    Column(Modifier.padding(padding).padding(16.dp).navigationBarsPadding().verticalScroll(rememberScrollState()).testTag("onboarding-screen")) {
        Text("建立你的第一个学期", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("onboarding-title"))
        OutlinedTextField(form.name, actions.updateName, Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp).testTag("semester-name"), label = { Text("学期名称") }, singleLine = true, shape = TerminalShape)
        DateControl(form.startDate, actions.updateStartDate); WeekControl(form.totalWeeks, actions.updateTotalWeeks)
        OutlinedButton(actions.togglePeriods, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily-periods-toggle"), shape = TerminalShape) { Text(if (form.periodsExpanded) "收起节次设置（${form.periods.size} 节）" else "展开节次设置（${form.periods.size} 节）") }
        if (form.periodsExpanded) {
            form.periods.forEach { PeriodRow(it, form.periods.size, actions) }
            OutlinedButton(actions.addPeriod, enabled = form.periods.size < 20, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-period"), shape = TerminalShape) { Text("添加节次") }
        }
        form.validationMessage?.let { Text(it, color = Danger, modifier = Modifier.padding(vertical = 8.dp).testTag("semester-validation-error")) }
        Button(actions.saveSemester, enabled = !saving, shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface), modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).heightIn(min = 48.dp).testTag("semester-save")) { Text(if (saving) "正在保存…" else "保存并继续") }
    }
}

@Composable private fun DateControl(date: LocalDate, update: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton({ DatePickerDialog(context, { _, year, month, day -> update(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 48.dp).testTag("semester-start-date"), shape = TerminalShape) { Text("开始日期：${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}") }
}

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

@Composable private fun MainShell(state: ScheduleState, selected: MainTab, currentTime: LocalDateTime, actions: QingKeAppActions) {
    val dark = state.preferences.appearanceMode == AppearanceMode.DARK ||
        (state.preferences.appearanceMode == AppearanceMode.SYSTEM && isSystemInDarkTheme())
    Box(Modifier.fillMaxSize().testTag("main-shell")) {
        TerminalBackdrop(dark)
        when (selected) {
            MainTab.TODAY -> TodayScheduleScreen(state, currentTime, actions.refreshTime, actions.openAddCourse, actions.openCourseAt, dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SCHEDULE -> ShellPlaceholder("课表（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SETTINGS -> ShellPlaceholder("设置（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
        }
        TerminalTabBar(selected, actions.selectTab, dark, Modifier.align(Alignment.BottomCenter))
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

@Composable private fun ShellPlaceholder(text: String, dark: Boolean, modifier: Modifier = Modifier) = Column(
    modifier.fillMaxSize().statusBarsPadding(), Arrangement.Center, Alignment.CenterHorizontally,
) { Text(text, color = terminalText(dark), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TodayScheduleScreen(state: ScheduleState, now: LocalDateTime, refresh: () -> Unit, addCourse: () -> Unit, openCourseAt: (Int) -> Unit, dark: Boolean, modifier: Modifier = Modifier) {
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
        Button(addCourse, Modifier.align(Alignment.BottomEnd).padding(20.dp).size(54.dp).border(1.dp, InverseSurface.copy(alpha = .72f), TerminalShape).drawBehind { drawLine(InverseSurface.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(size.width - 13.dp.toPx(), 0f), androidx.compose.ui.geometry.Offset(size.width, 13.dp.toPx()), 1.dp.toPx()); drawRect(InverseSurface.copy(alpha = .82f), topLeft = androidx.compose.ui.geometry.Offset(size.width - 12.dp.toPx(), 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(11.dp.toPx(), 11.dp.toPx())) }.testTag("today-add-course"), shape = TerminalShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("+", fontSize = 20.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black); Text("ADD", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Black) } }
    }
}

@Composable private fun BrandHeader(dark: Boolean, code: String = "LOCAL / 01", tag: String = "today-brand-header") = Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).border(width = 0.dp, color = Color.Transparent)
        .drawBehind { drawLine(if (dark) Color.White.copy(alpha = .20f) else Color.Black.copy(alpha = .18f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.testTag(tag), verticalAlignment = Alignment.CenterVertically,
) {
    androidx.compose.foundation.Image(painterResource(if (dark) R.drawable.qingke_logo_dark else R.drawable.qingke_logo), "青课 QINGKE ACADEMIC TERMINAL", Modifier.width(154.dp).heightIn(min = 54.dp).testTag("today-brand-logo"), contentScale = ContentScale.Fit)
    Spacer(Modifier.weight(1f)); Text(code, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun TodayHero(now: LocalDateTime, semester: com.qingke.schedule.domain.Semester, presentation: TodaySchedulePresentation, dark: Boolean, modifier: Modifier = Modifier) = Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.width(106.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern("MMM", Locale.US)).uppercase(Locale.US), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("today-month-code"))
        Text(now.format(DateTimeFormatter.ofPattern("dd", Locale.US)), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, style = MaterialTheme.typography.displayLarge, modifier = Modifier.testTag("today-day-number"))
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
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Column(Modifier.width(96.dp)) { Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1); Text("– ${range.getOrNull(1).orEmpty()}", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace) }; Box(Modifier.width(1.dp).heightIn(min = 70.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 15.dp).weight(1f)) { Text(item.occurrence.course.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-featured-details")) } }
    item.timingProgress?.let { progress -> LinearProgressIndicator({ progress.fraction.toFloat() }, Modifier.fillMaxWidth().heightIn(min = 5.dp).testTag("today-featured-progress"), color = QingKeCyan, trackColor = if (dark) Color.White.copy(alpha = .14f) else InverseSurface.copy(alpha = .12f)); Row(Modifier.fillMaxWidth().background(InverseSurface).padding(horizontal = 12.dp, vertical = 9.dp)) { Text("已进行 ${progress.elapsedMinutes} 分钟 · 剩余 ${progress.remainingClockText}", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("◷", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun CourseRow(item: TodayCourseItem, semester: com.qingke.schedule.domain.Semester, index: Int, dark: Boolean, open: () -> Unit) = Row(Modifier.fillMaxWidth().terminalPanel(dark, Color.Transparent).alpha(if (item.status == CourseStatus.FINISHED) .56f else 1f).clickable(onClick = open).padding(12.dp).testTag("today-course-${item.occurrence.key.courseIndex}-${item.occurrence.key.scheduleIndex}"), verticalAlignment = Alignment.CenterVertically) {
    val key = item.occurrence.key; val accent = if (item.status == CourseStatus.ONGOING) SignalYellow else courseColor(item.occurrence.course.color)
    Box(Modifier.width(3.dp).heightIn(min = 68.dp).background(accent).testTag("today-course-color-${key.courseIndex}-${key.scheduleIndex}").semantics { contentDescription = "课程颜色：${courseColorLabel(item.occurrence.course.color)}" }); Spacer(Modifier.width(8.dp)); Text("%02d".format(index + 1), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 9.sp, modifier = Modifier.width(24.dp).graphicsLayer { rotationZ = -90f }.testTag("today-course-index-${key.courseIndex}-${key.scheduleIndex}")); Column(Modifier.width(66.dp)) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1); Text(range.getOrNull(1).orEmpty(), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }; Box(Modifier.width(1.dp).heightIn(min = 52.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 13.dp).weight(1f)) { Text(statusText(item), color = if (item.status == CourseStatus.ONGOING) InverseSurface else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(accent).padding(horizontal = 6.dp, vertical = 2.dp).testTag("today-course-status-${key.courseIndex}-${key.scheduleIndex}")); Text(item.occurrence.course.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-course-details-${key.courseIndex}-${key.scheduleIndex}"), style = MaterialTheme.typography.labelSmall) }; Text("›", color = terminalSecondary(dark), fontSize = 24.sp, modifier = Modifier.padding(start = 8.dp).testTag("today-course-enter-${key.courseIndex}-${key.scheduleIndex}"))
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
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("course-editor")) {
        TerminalBackdrop(dark, "course-editor-backdrop")
        if (editor.mode == CourseEditorMode.CHOOSER) {
            Column(Modifier.fillMaxSize()) {
                EditorHeader("添加课程", "SELECT PROFILE", actions.closeCourseEditor, null, false, dark)
                BrandHeader(dark, "PROFILE / 04", "course-choice-brand-header")
                Column(Modifier.weight(1f).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                TerminalSectionHeader("01", "创建方式", "COURSE DATA", dark, "course-choice-create-section")
                Column(Modifier.fillMaxWidth().padding(top = 10.dp).terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-choice-create-panel")) { Text("创建新的课程资料", color = terminalText(dark), fontWeight = FontWeight.Black); Text("建立名称、教师和颜色，然后添加上课安排。", color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp)); Button(actions.openNewCourse, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("course-create-new"), shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) { Text("+  新建课程", fontWeight = FontWeight.Black) } }
                TerminalSectionHeader("02", "已有课程", "REUSE ${courses.size}", dark, "course-choice-reuse-section")
                courses.withIndex().sortedWith(compareBy<IndexedValue<com.qingke.schedule.domain.Course>> { it.value.name.trim() }.thenBy { it.index }).forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).terminalPanel(dark, courseColor(entry.value.color)).clickable { actions.appendCourseAt(entry.index) }.padding(12.dp).testTag("course-append-${entry.index}").semantics { contentDescription = "${entry.value.name}，追加安排，来源 ${entry.index + 1}" }, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(14.dp).background(courseColor(entry.value.color), androidx.compose.foundation.shape.CircleShape)); Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(entry.value.name.ifBlank { "未命名课程" }, color = terminalText(dark), fontWeight = FontWeight.Bold); Text(entry.value.teacher.ifBlank { "未填写教师" }, color = terminalSecondary(dark), style = MaterialTheme.typography.labelSmall) }; Text("+ 安排", color = SignalYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black) }
                }
                }
            }
        } else {
            val periodMaximum = semester?.periods?.maxOfOrNull { it.number } ?: 1
            val weekMaximum = semester?.totalWeeks ?: 1
            Column(Modifier.fillMaxSize()) {
                EditorHeader(if (editor.isAppend) "添加上课安排" else if (editor.mode == CourseEditorMode.EDIT) "编辑课程" else "添加课程", if (editor.isAppend) "NEW SCHEDULE" else if (editor.mode == CourseEditorMode.EDIT) "COURSE PROFILE" else "NEW COURSE", actions.closeCourseEditor, actions.saveCourse, editor.isInFlight, dark)
                BrandHeader(dark, "${if (editor.isAppend) "APPEND" else if (editor.mode == CourseEditorMode.EDIT) "EDIT" else "CREATE"} / 04", "course-editor-brand-header")
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                TerminalSectionHeader("01", "课程资料", "COURSE PROFILE", dark, "course-info-section")
                if (editor.isAppend) {
                    Column(Modifier.fillMaxWidth().padding(top = 12.dp).terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-append-readonly")) {
                        Text("复用课程资料", color = terminalText(dark), fontWeight = FontWeight.Black)
                        Text(editor.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("course-append-name"))
                        if (editor.teacher.isNotBlank()) Text(editor.teacher, color = terminalSecondary(dark), modifier = Modifier.testTag("course-append-teacher"))
                        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(16.dp).background(courseColor(editor.color), TerminalShape)); Spacer(Modifier.width(8.dp)); Text(editor.color, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace) }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp).terminalPanel(dark, QingKeCyan).padding(horizontal = 12.dp).testTag("course-info-form-section")) { OutlinedTextField(editor.name, actions.updateCourseName, Modifier.fillMaxWidth().testTag("course-name"), label = { Text("课程名称") }, singleLine = true, enabled = !editor.isInFlight, shape = TerminalShape); Box(Modifier.fillMaxWidth().height(1.dp).background(terminalBorder(dark))); OutlinedTextField(editor.teacher, actions.updateCourseTeacher, Modifier.fillMaxWidth().testTag("course-teacher"), label = { Text("教师（可选）") }, singleLine = true, enabled = !editor.isInFlight, shape = TerminalShape) }
                    Text("课程颜色", Modifier.padding(top = 18.dp), color = terminalText(dark), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("#287B74", "#D96952", "#536FAF", "#9A6AAF", "#B87928", "#46835A").forEach { color ->
                            val selected = editor.color == color
                            Box(Modifier.weight(1f).height(38.dp).background(Color.Transparent).clickable(enabled = !editor.isInFlight) { actions.updateCourseColor(color) }.semantics { contentDescription = "预设颜色 $color，${if (selected) "已选中" else "未选中"}" }.testTag("course-color-$color"), contentAlignment = Alignment.Center) { Box(Modifier.size(27.dp).background(courseColor(color), androidx.compose.foundation.shape.CircleShape).border(if (selected) 3.dp else 1.dp, if (selected) SignalYellow else Color.White.copy(alpha = .65f), androidx.compose.foundation.shape.CircleShape).testTag("course-color-swatch-$color"), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) } }
                        }
                    }
                    OutlinedButton(actions.showColorDialog, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).testTag("course-custom-color"), enabled = !editor.isInFlight, shape = TerminalShape) { Text("自定义颜色：${editor.color}") }
                }
                editor.visibleSchedules.forEachIndexed { visibleIndex, schedule ->
                    TerminalSectionHeader("%02d".format(visibleIndex + 2), "上课安排 ${visibleIndex + 1}", "SCHEDULE", dark, "course-schedule-header-${schedule.id}")
                    Column(Modifier.fillMaxWidth().padding(top = 7.dp).terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("course-schedule-${schedule.id}")) {
                        CompactPicker("星期", weekdayName(schedule.dayOfWeek), schedule.dayOfWeek, 1..7, { weekdayName(it) }, { actions.updateCourseDay(schedule.id, it) }, "course-day-${schedule.id}", dark, !editor.isInFlight)
                        CompactPicker("开始节次", periodDescription(semester, schedule.startPeriod, true), schedule.startPeriod, 1..periodMaximum, { periodDescription(semester, it, true) }, { actions.updateCourseStartPeriod(schedule.id, it) }, "course-start-period-${schedule.id}", dark, !editor.isInFlight)
                        CompactPicker("结束节次", periodDescription(semester, schedule.endPeriod, false), schedule.endPeriod, 1..periodMaximum, { periodDescription(semester, it, false) }, { actions.updateCourseEndPeriod(schedule.id, it) }, "course-end-period-${schedule.id}", dark, !editor.isInFlight)
                        EditorStepper("起始周", schedule.startWeek, 1, weekMaximum, { actions.updateCourseStartWeek(schedule.id, it) }, "course-start-week-${schedule.id}", dark, !editor.isInFlight)
                        EditorStepper("结束周", schedule.endWeek, 1, weekMaximum, { actions.updateCourseEndWeek(schedule.id, it) }, "course-end-week-${schedule.id}", dark, !editor.isInFlight)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { RepeatRule.entries.forEach { rule -> OutlinedButton({ actions.updateCourseRepeat(schedule.id, rule) }, Modifier.weight(1f).heightIn(min = 40.dp).testTag("course-repeat-${schedule.id}-${rule.name}"), enabled = !editor.isInFlight, shape = TerminalShape, colors = ButtonDefaults.outlinedButtonColors(containerColor = if (schedule.repeatRule == rule) QingKeCyan.copy(alpha = .3f) else Color.Transparent)) { Text(if (rule == RepeatRule.EVERY) "每周" else if (rule == RepeatRule.ODD) "单周" else "双周") } } }
                        OutlinedTextField(schedule.classroom, { actions.updateCourseClassroom(schedule.id, it) }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("course-classroom-${schedule.id}"), label = { Text("教室（可选）") }, singleLine = true, enabled = !editor.isInFlight, shape = TerminalShape)
                        if (editor.visibleSchedules.size > 1) OutlinedButton({ actions.removeCourseSchedule(schedule.id) }, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 44.dp).testTag("course-remove-schedule-${schedule.id}"), enabled = !editor.isInFlight, shape = TerminalShape) { Text("删除此安排") }
                    }
                }
                Button(actions.addCourseSchedule, Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).testTag("course-add-schedule"), shape = TerminalShape, enabled = !editor.isInFlight, colors = ButtonDefaults.buttonColors(containerColor = SignalYellow, contentColor = InverseSurface)) { Text("+  添加上课安排", fontWeight = FontWeight.Black) }
                editor.validationMessage?.let { Text(it, color = Danger, modifier = Modifier.padding(top = 10.dp).testTag("course-validation")) }
                if (editor.mode == CourseEditorMode.EDIT) Column(Modifier.fillMaxWidth().padding(bottom = 20.dp).terminalPanel(dark, Danger).padding(12.dp).testTag("course-danger-zone")) { Text("99 / 危险操作", color = Danger, fontWeight = FontWeight.Black); OutlinedButton(actions.deleteCourse, Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp).testTag("course-delete"), enabled = !editor.isInFlight, shape = TerminalShape) { Text("删除课程", color = Danger) } }
                }
            }
        }
        when (val confirmation = editor.confirmation) {
            CourseEditorConfirmation.Discard -> EditorDialog("放弃未保存修改？", "返回将丢失当前输入。", "放弃", actions.discardCourseEditor, actions.dismissCourseConfirmation, "course-discard-confirm", dark)
            CourseEditorConfirmation.Delete -> EditorDialog("删除课程？", "课程和全部上课安排将被删除。", "删除", actions.confirmDeleteCourse, actions.dismissCourseConfirmation, "course-delete-confirm", dark)
            is CourseEditorConfirmation.Conflicts -> EditorDialog("发现时间冲突", conflictMessage(confirmation.conflicts), "仍然保存", actions.confirmSaveDespiteConflicts, actions.dismissCourseConfirmation, "course-conflict-confirm", dark)
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

@Composable private fun TerminalSectionHeader(number: String, title: String, subtitle: String, dark: Boolean, tag: String) = Row(Modifier.fillMaxWidth().padding(top = 14.dp).drawBehind { drawLine(terminalBorder(dark), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 7.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) { Text(number, color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black); Spacer(Modifier.width(9.dp)); Text(title, color = terminalText(dark), fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text(subtitle, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 10.sp) }

@Composable private fun EditorStepper(label: String, value: Int, minimum: Int, maximum: Int, update: (Int) -> Unit, tag: String, dark: Boolean, enabled: Boolean) = Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("$label：$value", Modifier.weight(1f), color = terminalText(dark))
    OutlinedButton({ update((value - 1).coerceAtLeast(minimum)) }, Modifier.heightIn(min = 40.dp).testTag("$tag-minus"), enabled = enabled && value > minimum, shape = TerminalShape) { Text("−") }
    OutlinedButton({ update((value + 1).coerceAtMost(maximum)) }, Modifier.padding(start = 6.dp).heightIn(min = 40.dp).testTag("$tag-plus"), enabled = enabled && value < maximum, shape = TerminalShape) { Text("+") }
}

@Composable private fun CompactPicker(label: String, valueLabel: String, value: Int, choices: IntRange, choiceLabel: (Int) -> String, update: (Int) -> Unit, tag: String, dark: Boolean, enabled: Boolean) = Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, color = terminalText(dark), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Box { OutlinedButton({ expanded = true }, Modifier.heightIn(min = 38.dp).testTag(tag), enabled = enabled, shape = TerminalShape) { Text(valueLabel, fontFamily = FontFamily.Monospace, fontSize = 11.sp); Spacer(Modifier.width(7.dp)); Text("⌄", fontWeight = FontWeight.Black) }; DropdownMenu(expanded, { expanded = false }, modifier = Modifier.testTag("$tag-menu"), shape = TerminalShape) { choices.forEach { choice -> DropdownMenuItem({ Text(choiceLabel(choice)) }, { update(choice); expanded = false }, modifier = Modifier.testTag("$tag-option-$choice")) } } }
}

@Composable private fun CourseColorDialog(editor: CourseEditorState, dark: Boolean, actions: QingKeAppActions) {
    val rgb = rgbParts(editor.color); val hsv = hsvParts(editor.color)
    TerminalDialog(code = "COLOR / CUSTOM", title = "自定义颜色", confirm = "完成", onConfirm = actions.dismissColorDialog, onDismiss = actions.dismissColorDialog, tag = "course-color-dialog", dark = dark, messageContent = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(courseColor(editor.color), TerminalShape).border(1.dp, terminalBorder(dark), TerminalShape).testTag("course-color-preview")); Spacer(Modifier.width(12.dp)); Text(editor.color, color = terminalText(dark), fontFamily = FontFamily.Monospace) }
            OutlinedTextField(editor.colorInput, actions.updateCourseColorInput, Modifier.fillMaxWidth().padding(top = 12.dp).testTag("course-custom-color-input"), label = { Text("#RRGGBB") }, singleLine = true, isError = !editor.colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$")), enabled = !editor.isInFlight, shape = TerminalShape)
            if (!editor.colorInput.matches(Regex("^#[0-9A-Fa-f]{6}$"))) Text("请输入严格的 #RRGGBB", color = Danger, style = MaterialTheme.typography.labelSmall)
            Text("RGB", Modifier.padding(top = 8.dp), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace)
            EditorStepper("R", rgb[0], 0, 255, { actions.updateCourseColor(rgbHex(it, rgb[1], rgb[2])) }, "course-r", dark, !editor.isInFlight)
            EditorStepper("G", rgb[1], 0, 255, { actions.updateCourseColor(rgbHex(rgb[0], it, rgb[2])) }, "course-g", dark, !editor.isInFlight)
            EditorStepper("B", rgb[2], 0, 255, { actions.updateCourseColor(rgbHex(rgb[0], rgb[1], it)) }, "course-b", dark, !editor.isInFlight)
            Text("HSV", Modifier.padding(top = 8.dp), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace)
            EditorStepper("H", hsv[0], 0, 360, { actions.updateCourseColor(hsvHex(it, hsv[1], hsv[2])) }, "course-h", dark, !editor.isInFlight)
            EditorStepper("S", hsv[1], 0, 100, { actions.updateCourseColor(hsvHex(hsv[0], it, hsv[2])) }, "course-s", dark, !editor.isInFlight)
            EditorStepper("V", hsv[2], 0, 100, { actions.updateCourseColor(hsvHex(hsv[0], hsv[1], it)) }, "course-v", dark, !editor.isInFlight)
        } })
}

private fun weekdayName(value: Int) = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日").getOrElse(value - 1) { "星期" }
private fun periodDescription(semester: com.qingke.schedule.domain.Semester?, number: Int, start: Boolean): String {
    val period = semester?.periods?.firstOrNull { it.number == number }
    return "第 $number 节${period?.let { "（${if (start) it.startTime else it.endTime}）" } ?: ""}"
}

@Composable private fun EditorDialog(title: String, message: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tag: String, dark: Boolean) = TerminalDialog(
    code = when (tag) { "course-conflict-confirm" -> "WARNING / CONFLICT"; "course-discard-confirm" -> "WARNING / UNSAVED"; else -> "DANGER / DELETE" },
    status = when (tag) { "course-conflict-confirm" -> "SCHEDULE COLLISION"; "course-discard-confirm" -> "DISCARD CHANGES"; else -> "IRREVERSIBLE" },
    dismiss = if (tag == "course-discard-confirm") "继续编辑" else if (tag == "course-delete-confirm") "取消" else "返回修改", title = title, message = message, confirm = confirm, onConfirm = onConfirm, onDismiss = onDismiss, tag = tag, dark = dark,
)

@Composable private fun TerminalDialog(code: String, title: String, message: String = "", confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tag: String, dismissTag: String? = "terminal-dialog-dismiss", confirmTag: String = "$tag-confirm", dismiss: String = "返回修改", status: String = "ACTION REQUIRED", dark: Boolean = isSystemInDarkTheme(), messageContent: (@Composable () -> Unit)? = null) = Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f)).testTag("$tag-backdrop"), contentAlignment = Alignment.Center) {
    Column(Modifier.padding(24.dp).fillMaxWidth().terminalPanel(dark = dark, accent = if (code.startsWith("DANGER")) Danger else SignalYellow, level = TerminalSurfaceLevel.ELEVATED).padding(16.dp).testTag(tag)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(code, color = if (code.startsWith("DANGER")) Danger else SignalYellow, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, fontSize = 10.sp, modifier = Modifier.testTag("$tag-code")); Spacer(Modifier.weight(1f)); Box(Modifier.size(7.dp).background(if (code.startsWith("DANGER")) Danger else SignalYellow, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(6.dp)); Text(status, color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.testTag("$tag-status")) }
        Text(title, color = terminalText(dark), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        if (message.isNotEmpty()) Text(message, color = terminalSecondary(dark), modifier = Modifier.padding(top = 8.dp))
        messageContent?.let { Column(Modifier.padding(top = 8.dp)) { it() } }
        val danger = code.startsWith("DANGER") || status == "DISCARD CHANGES"
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (dismissTag != null) OutlinedButton(onDismiss, Modifier.weight(1f).heightIn(min = 46.dp).testTag(dismissTag), shape = TerminalShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = terminalText(dark))) { Text(dismiss) }; Button(onConfirm, Modifier.weight(1f).heightIn(min = 46.dp).testTag(confirmTag), shape = TerminalShape, colors = ButtonDefaults.buttonColors(containerColor = if (danger) Danger else SignalYellow, contentColor = InverseSurface)) { Text(confirm) } }
    }
}

private fun conflictMessage(conflicts: List<com.qingke.schedule.domain.ScheduleConflict>): String {
    val names = conflicts.map { it.existingCourse.name }.filter { it.isNotBlank() }.distinct().sorted()
    val weeks = conflicts.flatMap { it.weeks }.distinct().sorted()
    val summary = if (weeks.size <= 6) weeks.joinToString("、") { "第${it}周" } else "第${weeks.firstOrNull() ?: 0}周至第${weeks.lastOrNull() ?: 0}周"
    return "与${names.joinToString("、").ifEmpty { "已有课程" }}在${summary}存在时间冲突。"
}

private fun rgbParts(hex: String): IntArray = try {
    val color = AndroidColor.parseColor(hex)
    intArrayOf(AndroidColor.red(color), AndroidColor.green(color), AndroidColor.blue(color))
} catch (_: IllegalArgumentException) { intArrayOf(40, 185, 214) }

private fun rgbHex(red: Int, green: Int, blue: Int) = "#%02X%02X%02X".format(red, green, blue)

private fun hsvParts(hex: String): IntArray {
    val rgb = rgbParts(hex); val values = FloatArray(3)
    AndroidColor.RGBToHSV(rgb[0], rgb[1], rgb[2], values)
    return intArrayOf(values[0].toInt(), (values[1] * 100).toInt(), (values[2] * 100).toInt())
}

private fun hsvHex(hue: Int, saturation: Int, value: Int): String = "#%06X".format(AndroidColor.HSVToColor(floatArrayOf(hue.toFloat(), saturation / 100f, value / 100f)) and 0xFFFFFF)
