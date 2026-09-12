package com.qingke.schedule.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qingke.schedule.preferences.AppearanceMode
import com.qingke.schedule.state.LoadStatus
import com.qingke.schedule.state.ScheduleState
import com.qingke.schedule.viewmodel.MainTab
import com.qingke.schedule.viewmodel.PeriodFormState
import com.qingke.schedule.viewmodel.ScheduleViewModel
import com.qingke.schedule.viewmodel.SemesterFormState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val SignalYellow = Color(0xFFFFD400)
private val QingKeCyan = Color(0xFF28B9D6)
private val InverseSurface = Color(0xFF091113)
private val DarkSurface = Color(0xFF182427)
private val LightSurface = Color(0xFFF1F5F4)
private val Danger = Color(0xFFE65A4F)
private val TerminalShape = RoundedCornerShape(0.dp)

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
)

@Composable
fun QingKeApp(viewModel: ScheduleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    QingKeAppContent(
        state, form, selectedTab,
        QingKeAppActions(
            retryLoad = viewModel::retryLoad, dismissError = viewModel::dismissError,
            selectTab = viewModel::selectTab, updateName = viewModel::updateName,
            updateStartDate = viewModel::updateStartDate, updateTotalWeeks = viewModel::updateTotalWeeks,
            updatePeriodStart = viewModel::updatePeriodStart, updatePeriodEnd = viewModel::updatePeriodEnd,
            addPeriod = viewModel::addPeriod, removePeriod = viewModel::removePeriod,
            togglePeriods = viewModel::togglePeriods, saveSemester = viewModel::saveSemester,
        ),
    )
}

@Composable
fun QingKeAppContent(
    state: ScheduleState,
    form: SemesterFormState?,
    selectedTab: MainTab,
    actions: QingKeAppActions,
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
            else MainShell(selectedTab, actions.selectTab)
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

@Composable private fun MainShell(selected: MainTab, select: (MainTab) -> Unit) = Scaffold(
    bottomBar = { NavigationBar(containerColor = InverseSurface, modifier = Modifier.navigationBarsPadding()) {
        listOf(MainTab.TODAY to "今日", MainTab.SCHEDULE to "课表", MainTab.SETTINGS to "设置").forEach { (tab, label) ->
            NavigationBarItem(selected == tab, { select(tab) }, icon = {}, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = InverseSurface, selectedTextColor = SignalYellow, indicatorColor = SignalYellow, unselectedIconColor = Color.White, unselectedTextColor = Color.White), modifier = Modifier.heightIn(min = 48.dp).testTag("${tab.name.lowercase()}-tab").semantics { contentDescription = label })
        }
    } }, modifier = Modifier.testTag("main-shell"),
) { padding -> Column(Modifier.fillMaxSize().statusBarsPadding().padding(padding), Arrangement.Center, Alignment.CenterHorizontally) { Text(when (selected) { MainTab.TODAY -> "今日（壳层）"; MainTab.SCHEDULE -> "课表（壳层）"; MainTab.SETTINGS -> "设置（壳层）" }, style = MaterialTheme.typography.headlineMedium) } }
