package com.qingke.schedule.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
)

@Composable
fun QingKeApp(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
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
        ),
        currentTime,
    )
}

@Composable
fun QingKeAppContent(
    state: ScheduleState,
    form: SemesterFormState?,
    selectedTab: MainTab,
    actions: QingKeAppActions,
    currentTime: LocalDateTime = LocalDateTime.now(),
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
        state.error?.let { message -> if (state.loadStatus == LoadStatus.READY) ErrorDialog(message, actions.dismissError) }
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

@Composable private fun ErrorDialog(message: String, dismiss: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss, title = { Text("操作未完成") }, text = { Text(message) },
    confirmButton = { Button(dismiss, Modifier.heightIn(min = 48.dp).testTag("app-error-dismiss"), shape = TerminalShape) { Text("知道了") } }, modifier = Modifier.testTag("app-error-dialog"),
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
            MainTab.TODAY -> TodayScheduleScreen(state, currentTime, actions.refreshTime, dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SCHEDULE -> ShellPlaceholder("课表（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
            MainTab.SETTINGS -> ShellPlaceholder("设置（壳层）", dark, Modifier.fillMaxSize().padding(bottom = 82.dp))
        }
        TerminalTabBar(selected, actions.selectTab, dark, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun TerminalBackdrop(dark: Boolean) = Canvas(
    Modifier.fillMaxSize().clipToBounds().testTag("terminal-backdrop"),
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
            .terminalPanel(dark, Color.Transparent).padding(6.dp),
    ) {
        listOf(Triple(MainTab.TODAY, "今日", "01"), Triple(MainTab.SCHEDULE, "课表", "02"), Triple(MainTab.SETTINGS, "设置", "03")).forEach { (tab, title, number) ->
            val active = selected == tab
            Column(Modifier.weight(1f).height(62.dp).background(if (active) InverseSurface else Color.Transparent, TerminalShape)
                .selectable(active, onClick = { onSelect(tab) }, role = Role.Tab).testTag("${tab.name.lowercase()}-tab").semantics { contentDescription = title }) {
                Row(Modifier.weight(1f).padding(horizontal = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    TerminalTabIcon(tab, if (active) Color(0xFFF1F5F4) else terminalText(dark), "${tab.name.lowercase()}-tab-icon")
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, color = if (active) Color(0xFFF1F5F4) else terminalText(dark), fontWeight = FontWeight.Bold)
                        Text(number, color = if (active) SignalYellow else terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
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
@Composable private fun TodayScheduleScreen(state: ScheduleState, now: LocalDateTime, refresh: () -> Unit, dark: Boolean, modifier: Modifier = Modifier) {
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
    Column(modifier.statusBarsPadding().testTag("today-screen")) {
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
                        item(key = "featured-${featured.occurrence.key.courseIndex}-${featured.occurrence.key.scheduleIndex}") { FeaturedCourse(featured, semester, presentation.items.indexOf(featured), presentation.items.size, dark) }
                    }
                    item { SequenceHeader(presentation.items.size, dark) }
                    items(presentation.items, key = { "${it.occurrence.key.courseIndex}-${it.occurrence.key.scheduleIndex}" }) { item -> CourseRow(item, semester, presentation.items.indexOf(item), dark) }
                    item { Text("END OF SCHEDULE // ${presentation.items.lastOrNull()?.let { ScheduleDisplayText.timeRange(it.occurrence.schedule, semester).substringAfter('–') } ?: "--:--"}", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().testTag("today-end-marker"), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable private fun BrandHeader(dark: Boolean) = Row(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp).border(width = 0.dp, color = Color.Transparent)
        .drawBehind { drawLine(if (dark) Color.White.copy(alpha = .20f) else Color.Black.copy(alpha = .18f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.testTag("today-brand-header"), verticalAlignment = Alignment.CenterVertically,
) {
    androidx.compose.foundation.Image(painterResource(if (dark) R.drawable.qingke_logo_dark else R.drawable.qingke_logo), "青课 QINGKE ACADEMIC TERMINAL", Modifier.width(154.dp).heightIn(min = 54.dp).testTag("today-brand-logo"), contentScale = ContentScale.Fit)
    Spacer(Modifier.weight(1f)); Text("LOCAL / 01", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
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

@Composable private fun SequenceHeader(count: Int, dark: Boolean) = Row(Modifier.fillMaxWidth().drawBehind { drawLine(terminalBorder(dark), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = 8.dp).testTag("today-course-sequence"), verticalAlignment = Alignment.CenterVertically) { Text("%02d".format(count), color = QingKeCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black); Spacer(Modifier.width(9.dp)); Text("课程序列", color = terminalText(dark), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.weight(1f)); Text("QUEUE / ALL DAY", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }

@Composable private fun RefreshFeedback(dark: Boolean) = Row(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(12.dp).testTag("today-refresh-status")) { Text("刷新中", color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("SYNC / LOCAL", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }

@Composable private fun TodayEmpty(message: String, dark: Boolean) = Column(Modifier.testTag("today-empty")) { SequenceHeader(0, dark); Column(Modifier.fillMaxWidth().terminalPanel(dark, QingKeCyan).padding(16.dp)) { Text("STANDBY", color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.background(QingKeCyan).padding(horizontal = 6.dp, vertical = 3.dp)); Spacer(Modifier.heightIn(min = 9.dp)); Text("今天没有课程", color = terminalText(dark), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(message, color = terminalSecondary(dark)) } }

@Composable private fun FeaturedCourse(item: TodayCourseItem, semester: com.qingke.schedule.domain.Semester, index: Int, total: Int, dark: Boolean) = Column(Modifier.fillMaxWidth().terminalPanel(dark, if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan).testTag("today-featured-course-${item.occurrence.key.courseIndex}-${item.occurrence.key.scheduleIndex}")) {
    val accent = if (item.status == CourseStatus.ONGOING) SignalYellow else QingKeCyan
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).background(accent).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(InverseSurface, androidx.compose.foundation.shape.CircleShape)); Spacer(Modifier.width(8.dp)); Text(if (item.status == CourseStatus.ONGOING) "CURRENT" else "NEXT", color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.testTag("today-featured-status")); Spacer(Modifier.weight(1f)); Text("%02d // %02d".format(index + 1, total), color = InverseSurface, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall) }
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Column(Modifier.width(96.dp)) { Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 28.sp, maxLines = 1); Text("– ${range.getOrNull(1).orEmpty()}", color = terminalSecondary(dark), fontFamily = FontFamily.Monospace) }; Box(Modifier.width(1.dp).heightIn(min = 70.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 15.dp).weight(1f)) { Text(item.occurrence.course.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-featured-details")) } }
    item.timingProgress?.let { progress -> LinearProgressIndicator({ progress.fraction.toFloat() }, Modifier.fillMaxWidth().heightIn(min = 5.dp).testTag("today-featured-progress"), color = QingKeCyan, trackColor = if (dark) Color.White.copy(alpha = .14f) else InverseSurface.copy(alpha = .12f)); Row(Modifier.fillMaxWidth().background(InverseSurface).padding(horizontal = 12.dp, vertical = 9.dp)) { Text("已进行 ${progress.elapsedMinutes} 分钟 · 剩余 ${progress.remainingClockText}", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text("◷", color = Color(0xFFF1F5F4), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) } }
}

@Composable private fun CourseRow(item: TodayCourseItem, semester: com.qingke.schedule.domain.Semester, index: Int, dark: Boolean) = Row(Modifier.fillMaxWidth().terminalPanel(dark, courseColor(item.occurrence.course.color)).alpha(if (item.status == CourseStatus.FINISHED) .56f else 1f).padding(12.dp).testTag("today-course-${item.occurrence.key.courseIndex}-${item.occurrence.key.scheduleIndex}"), verticalAlignment = Alignment.CenterVertically) {
    val key = item.occurrence.key; val accent = if (item.status == CourseStatus.ONGOING) SignalYellow else courseColor(item.occurrence.course.color)
    Text("%02d".format(index + 1), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, modifier = Modifier.width(24.dp).graphicsLayer { rotationZ = -90f }.testTag("today-course-index-${key.courseIndex}-${key.scheduleIndex}")); Column(Modifier.width(66.dp)) { val range = ScheduleDisplayText.timeRange(item.occurrence.schedule, semester).split("–"); Text(range.firstOrNull().orEmpty(), color = terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text(range.getOrNull(1).orEmpty(), color = terminalSecondary(dark), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall) }; Box(Modifier.width(1.dp).heightIn(min = 52.dp).background(terminalBorder(dark))); Column(Modifier.padding(start = 13.dp).weight(1f)) { Text(statusText(item), color = if (item.status == CourseStatus.ONGOING) InverseSurface else terminalText(dark), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, modifier = Modifier.background(accent).padding(horizontal = 6.dp, vertical = 2.dp).testTag("today-course-status-${key.courseIndex}-${key.scheduleIndex}")); Text(item.occurrence.course.name, color = terminalText(dark), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(courseDetails(item.occurrence), color = terminalSecondary(dark), modifier = Modifier.testTag("today-course-details-${key.courseIndex}-${key.scheduleIndex}"), style = MaterialTheme.typography.labelSmall) }; Box(Modifier.width(3.dp).heightIn(min = 68.dp).background(accent).testTag("today-course-color-${key.courseIndex}-${key.scheduleIndex}").semantics { contentDescription = "课程颜色：${courseColorLabel(item.occurrence.course.color)}" })
}
private fun terminalText(dark: Boolean) = if (dark) Color(0xFFF1F5F4) else Color(0xFF091113)
private fun terminalSecondary(dark: Boolean) = if (dark) Color(0xB3F1F5F4) else Color(0xB3091113)
private fun terminalBorder(dark: Boolean) = if (dark) Color.White.copy(alpha = .28f) else Color.White.copy(alpha = .82f)
private fun Modifier.terminalPanel(dark: Boolean, accent: Color): Modifier {
    val surface = if (dark) Color(0xE61A2527) else Color(0xDDFBFEFD)
    val highlight = if (dark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .48f)
    return this
        .shadow(if (dark) 7.dp else 4.dp, TerminalShape, ambientColor = Color.Black.copy(alpha = if (dark) .42f else .22f), spotColor = Color.Black.copy(alpha = if (dark) .34f else .16f))
        .background(Brush.linearGradient(listOf(highlight, surface, surface.copy(alpha = .94f))), TerminalShape)
        .border(1.dp, terminalBorder(dark), TerminalShape)
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
