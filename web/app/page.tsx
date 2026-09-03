"use client";

import type { CSSProperties } from "react";
import { useState } from "react";
import {
  Bell,
  BellRing,
  CalendarDays,
  CalendarRange,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Download,
  Grid2X2,
  MapPin,
  Plus,
  RotateCcw,
  Save,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Trash2,
  Upload,
  X,
} from "lucide-react";

type PrimaryView = "today" | "schedule" | "settings";
type CourseState = "complete" | "current" | "upcoming";
type RepeatRule = "every" | "odd" | "even";

type CourseSchedule = {
  id: string;
  day: number;
  startPeriod: number;
  endPeriod: number;
  startWeek: number;
  endWeek: number;
  repeatRule: RepeatRule;
  room: string;
  start: string;
  end: string;
};

type Course = {
  id: string;
  name: string;
  englishName: string;
  teacher: string;
  color: string;
  state: CourseState;
  stateLabel: string;
  todayOrder?: string;
  schedules: CourseSchedule[];
};

type SemesterSettings = {
  name: string;
  startDate: string;
  totalWeeks: number;
};

type Period = {
  number: number;
  start: string;
  end: string;
};

type EditorRoute = {
  mode: "create" | "edit";
  courseId?: string;
};

const weekdayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"];
const weekdayDates = ["31", "01", "02", "03", "04", "05", "06"];
const repeatLabels: Record<RepeatRule, string> = {
  every: "每周",
  odd: "单周",
  even: "双周",
};

const initialPeriods: Period[] = [
  { number: 1, start: "08:30", end: "10:05" },
  { number: 2, start: "10:25", end: "12:00" },
  { number: 3, start: "14:05", end: "15:40" },
  { number: 4, start: "16:00", end: "17:35" },
  { number: 5, start: "18:30", end: "20:05" },
];

const initialCourses: Course[] = [
  {
    id: "math",
    name: "高等数学",
    englishName: "ADVANCED MATHEMATICS",
    teacher: "林老师",
    color: "#f3cf18",
    state: "complete",
    stateLabel: "COMPLETE",
    todayOrder: "01",
    schedules: [{
      id: "math-fri",
      day: 5,
      startPeriod: 1,
      endPeriod: 1,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "理科楼 302",
      start: "08:30",
      end: "10:05",
    }],
  },
  {
    id: "structure",
    name: "数据结构",
    englishName: "DATA STRUCTURE",
    teacher: "周老师",
    color: "#27b7d7",
    state: "complete",
    stateLabel: "COMPLETE",
    todayOrder: "02",
    schedules: [{
      id: "structure-fri",
      day: 5,
      startPeriod: 2,
      endPeriod: 2,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "实验中心 A204",
      start: "10:25",
      end: "12:00",
    }],
  },
  {
    id: "design",
    name: "交互设计基础",
    englishName: "INTERACTION DESIGN",
    teacher: "顾老师",
    color: "#f3cf18",
    state: "current",
    stateLabel: "CURRENT",
    todayOrder: "03",
    schedules: [{
      id: "design-fri",
      day: 5,
      startPeriod: 3,
      endPeriod: 3,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "设计楼 407",
      start: "14:05",
      end: "15:40",
    }],
  },
  {
    id: "english",
    name: "大学英语",
    englishName: "COLLEGE ENGLISH",
    teacher: "叶老师",
    color: "#27b7d7",
    state: "upcoming",
    stateLabel: "NEXT",
    todayOrder: "04",
    schedules: [{
      id: "english-fri",
      day: 5,
      startPeriod: 5,
      endPeriod: 5,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "综合楼 B112",
      start: "18:30",
      end: "20:05",
    }],
  },
  {
    id: "systems",
    name: "操作系统",
    englishName: "OPERATING SYSTEMS",
    teacher: "许老师",
    color: "#e9eef0",
    state: "upcoming",
    stateLabel: "PLANNED",
    schedules: [{
      id: "systems-mon",
      day: 1,
      startPeriod: 1,
      endPeriod: 2,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "工科楼 C301",
      start: "08:30",
      end: "12:00",
    }],
  },
  {
    id: "visual",
    name: "视觉传达设计",
    englishName: "VISUAL COMMUNICATION",
    teacher: "陈老师",
    color: "#f3cf18",
    state: "upcoming",
    stateLabel: "PLANNED",
    schedules: [{
      id: "visual-tue",
      day: 2,
      startPeriod: 3,
      endPeriod: 4,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "odd",
      room: "设计楼 508",
      start: "14:05",
      end: "17:35",
    }],
  },
  {
    id: "sport",
    name: "大学体育",
    englishName: "PHYSICAL EDUCATION",
    teacher: "何老师",
    color: "#27b7d7",
    state: "upcoming",
    stateLabel: "PLANNED",
    schedules: [{
      id: "sport-wed",
      day: 3,
      startPeriod: 2,
      endPeriod: 2,
      startWeek: 1,
      endWeek: 17,
      repeatRule: "every",
      room: "东区操场",
      start: "10:25",
      end: "12:00",
    }],
  },
  {
    id: "research",
    name: "产品研究方法",
    englishName: "PRODUCT RESEARCH",
    teacher: "沈老师",
    color: "#e9eef0",
    state: "upcoming",
    stateLabel: "PLANNED",
    schedules: [{
      id: "research-thu",
      day: 4,
      startPeriod: 4,
      endPeriod: 5,
      startWeek: 2,
      endWeek: 16,
      repeatRule: "even",
      room: "综合楼 C03",
      start: "16:00",
      end: "20:05",
    }],
  },
];

const coursePalette = ["#f3cf18", "#27b7d7", "#e9eef0", "#ef7c50", "#82968b"];

function DeviceStatus() {
  return (
    <div className="device-status" aria-label="设备状态">
      <span className="device-time">15:03</span>
      <span className="dynamic-island" aria-hidden="true" />
      <div className="device-signals" aria-hidden="true">
        <span className="signal-bars"><i /><i /><i /><i /></span>
        <span className="network">5G</span>
        <span className="battery"><b>87</b><i /></span>
      </div>
    </div>
  );
}

function BrandHeader({
  code,
  onBell,
}: {
  code: string;
  onBell?: () => void;
}) {
  return (
    <header className="command-header">
      <div className="brand-lockup">
        <span className="brand-logo" role="img" aria-label="青课 QINGKE" />
        <span className="visually-hidden">QINGKE 青课 / ACADEMIC TERMINAL</span>
      </div>
      <div className="system-meta">
        <span>{code}</span>
        {onBell && (
          <button type="button" className="square-action" aria-label="查看课程提醒" onClick={onBell}>
            <Bell size={18} strokeWidth={1.7} />
            <i aria-hidden="true" />
          </button>
        )}
      </div>
    </header>
  );
}

function ScreenTitle({
  path,
  title,
  code,
}: {
  path: string;
  title: string;
  code: string;
}) {
  return (
    <header className="module-title">
      <div>
        <span>{path}</span>
        <h1>{title}</h1>
      </div>
      <strong>{code}</strong>
    </header>
  );
}

function CourseRow({ course, selected, onSelect }: {
  course: Course;
  selected: boolean;
  onSelect: () => void;
}) {
  const schedule = course.schedules[0];
  return (
    <button
      type="button"
      className={`course-row course-row--${course.state}${selected ? " is-selected" : ""}`}
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`编辑${course.name}`}
    >
      <span className="course-index">{course.todayOrder ?? "--"}</span>
      <span className="course-time">
        <strong>{schedule.start}</strong>
        <small>{schedule.end}</small>
      </span>
      <span className="course-copy">
        <span className="course-status">{course.stateLabel}</span>
        <strong>{course.name}</strong>
        <small>{schedule.room} / {course.teacher}</small>
      </span>
      <span className="course-open" aria-hidden="true">
        <ChevronRight size={18} strokeWidth={1.7} />
      </span>
    </button>
  );
}

function TodayScreen({ courses, onEdit, notify }: {
  courses: Course[];
  onEdit: (courseId: string) => void;
  notify: (message: string) => void;
}) {
  const [selectedCourse, setSelectedCourse] = useState("design");
  const todayCourses = courses.filter((course) => course.todayOrder);
  const activeCourse = todayCourses.find((course) => course.state === "current") ?? todayCourses[0];
  const activeSchedule = activeCourse.schedules[0];

  return (
    <div className="view-enter" data-view="today">
      <BrandHeader code="LOCAL / 01" onBell={() => notify("REMINDER // 今日课程提醒已开启")} />

      <section className="date-console" aria-labelledby="today-title">
        <div className="date-code">
          <span>SEP</span>
          <strong>04</strong>
          <small>2026 / FRI</small>
        </div>
        <div className="date-heading">
          <span className="terminal-path">SCHEDULE :// TODAY</span>
          <h1 id="today-title">今日</h1>
          <p>第 01 教学周 · 单周</p>
        </div>
        <div className="completion-gauge" aria-label="今日课程进度，正在进行第三节，共四节">
          <span>COURSE</span>
          <strong>03<small>/04</small></strong>
          <div><i /><i /><i /><i /></div>
        </div>
      </section>

      <div className="section-rail" aria-hidden="true">
        <span>当前课程 / CURRENT CLASS</span>
        <span>进度更新于 15:02</span>
      </div>

      <section className="active-course" aria-label="当前课程">
        <div className="active-course__bar">
          <span><i /> CURRENT</span>
          <span>{`${activeCourse.todayOrder} // 04`}</span>
        </div>
        <div className="active-course__body">
          <div className="active-time">
            <strong>{activeSchedule.start}</strong>
            <span>— {activeSchedule.end}</span>
          </div>
          <div className="active-copy">
            <span>{activeCourse.englishName}</span>
            <h2>{activeCourse.name}</h2>
            <p><MapPin size={14} /> {activeSchedule.room} <i /> {activeCourse.teacher}</p>
          </div>
          <button type="button" className="active-open" aria-label={`编辑${activeCourse.name}`} onClick={() => onEdit(activeCourse.id)}>
            <ChevronRight size={21} strokeWidth={1.6} />
          </button>
        </div>
        <div className="active-progress"><span style={{ width: "62%" }} /></div>
        <div className="active-course__footer">
          <span><Clock3 size={13} /> 已进行 58 分钟</span>
          <span>剩余 37 MIN</span>
        </div>
      </section>

      <div className="list-heading">
        <div><span>04</span><h2>课程序列</h2></div>
        <p>MISSION QUEUE / ALL DAY</p>
      </div>

      <section className="course-list" aria-label="今日课程列表">
        {todayCourses.map((course) => (
          <CourseRow
            key={course.id}
            course={course}
            selected={selectedCourse === course.id}
            onSelect={() => {
              setSelectedCourse(course.id);
              onEdit(course.id);
            }}
          />
        ))}
      </section>

      <p className="end-marker">END OF SCHEDULE // 20:05</p>
    </div>
  );
}

function ScheduleScreen({ courses, periods, onEdit }: {
  courses: Course[];
  periods: Period[];
  onEdit: (courseId: string) => void;
}) {
  const [week, setWeek] = useState(1);
  const [selectedDay, setSelectedDay] = useState(5);
  const visibleCourses = courses.filter((course) =>
    course.schedules.some((schedule) => schedule.day === selectedDay),
  );

  const matrixEntries = courses.flatMap((course) =>
    course.schedules
      .filter((schedule) => schedule.day <= 5)
      .map((schedule) => ({ course, schedule })),
  );

  return (
    <div className="view-enter schedule-view" data-view="schedule">
      <BrandHeader code="MATRIX / 02" />
      <ScreenTitle path="SCHEDULE :// WEEK MATRIX" title="课表" code={`W${String(week).padStart(2, "0")}`} />

      <section className="week-controller" aria-label="教学周选择">
        <button type="button" aria-label="上一周" disabled={week === 1} onClick={() => setWeek((value) => Math.max(1, value - 1))}>
          <ChevronLeft size={18} />
        </button>
        <div>
          <small>2026 秋季学期</small>
          <strong>第 {String(week).padStart(2, "0")} 教学周</strong>
        </div>
        <button type="button" aria-label="下一周" disabled={week === 17} onClick={() => setWeek((value) => Math.min(17, value + 1))}>
          <ChevronRight size={18} />
        </button>
      </section>

      <div className="weekday-strip" aria-label="选择星期">
        {weekdayNames.map((name, index) => (
          <button
            type="button"
            key={name}
            className={selectedDay === index + 1 ? "is-active" : ""}
            onClick={() => setSelectedDay(index + 1)}
          >
            <small>{name.slice(1)}</small>
            <strong>{weekdayDates[index]}</strong>
            <i aria-hidden="true" />
          </button>
        ))}
      </div>

      <div className="matrix-meta">
        <span>WEEK OVERVIEW // 05 PERIODS</span>
        <span>{week % 2 === 0 ? "EVEN" : "ODD"} WEEK</span>
      </div>

      <section className="week-matrix" aria-label={`第${week}周课程矩阵`}>
        <div className="matrix-days" aria-hidden="true">
          <span>TIME</span>
          {weekdayNames.slice(0, 5).map((name) => <span key={name}>{name}</span>)}
        </div>
        <div className="matrix-body" style={{ height: `${periods.length * 56}px` }}>
          {periods.map((period) => (
            <div className="matrix-row" key={period.number}>
              <span className="matrix-time"><b>0{period.number}</b><small>{period.start}</small></span>
              {weekdayNames.slice(0, 5).map((name, index) => (
                <span className={`matrix-cell${selectedDay === index + 1 ? " is-selected" : ""}`} key={name} />
              ))}
            </div>
          ))}
          <div className="matrix-courses" style={{ gridTemplateRows: `repeat(${periods.length}, 56px)` }}>
            {matrixEntries.map(({ course, schedule }) => (
              <button
                type="button"
                key={`${course.id}-${schedule.id}`}
                className="matrix-course"
                style={{
                  "--matrix-column": schedule.day + 1,
                  "--matrix-row": schedule.startPeriod,
                  "--matrix-span": schedule.endPeriod - schedule.startPeriod + 1,
                  "--course-color": course.color,
                } as CSSProperties}
                onClick={() => onEdit(course.id)}
                aria-label={`编辑${course.name}`}
              >
                <b>{course.name.slice(0, 4)}</b>
                <small>{schedule.room.replace(/\s/g, "")}</small>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="day-manifest">
        <div className="manifest-heading">
          <div><span>{String(selectedDay).padStart(2, "0")}</span><h2>{weekdayNames[selectedDay - 1]}</h2></div>
          <small>{visibleCourses.length} ENTRIES</small>
        </div>
        {visibleCourses.length ? visibleCourses.map((course) => {
          const schedule = course.schedules.find((item) => item.day === selectedDay) ?? course.schedules[0];
          return (
            <button type="button" className="manifest-row" key={course.id} onClick={() => onEdit(course.id)}>
              <i style={{ background: course.color }} />
              <span><b>{schedule.start}</b><small>{schedule.end}</small></span>
              <span><strong>{course.name}</strong><small>{schedule.room} / {repeatLabels[schedule.repeatRule]}</small></span>
              <ChevronRight size={17} />
            </button>
          );
        }) : (
          <div className="empty-manifest"><span>00</span><p>该日无课程安排<br /><small>NO MISSION ASSIGNED</small></p></div>
        )}
      </section>

    </div>
  );
}

function Toggle({ checked, onChange, label }: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      className={`terminal-toggle${checked ? " is-on" : ""}`}
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
    >
      <span />
      <small>{checked ? "ON" : "OFF"}</small>
    </button>
  );
}

function SettingsScreen({
  semester,
  setSemester,
  periods,
  setPeriods,
  remindersEnabled,
  setRemindersEnabled,
  leadMinutes,
  setLeadMinutes,
  customLeadActive,
  setCustomLeadActive,
  customLeadMinutes,
  setCustomLeadMinutes,
  notify,
  onOnboarding,
}: {
  semester: SemesterSettings;
  setSemester: (settings: SemesterSettings) => void;
  periods: Period[];
  setPeriods: (periods: Period[]) => void;
  remindersEnabled: boolean;
  setRemindersEnabled: (enabled: boolean) => void;
  leadMinutes: number;
  setLeadMinutes: (minutes: number) => void;
  customLeadActive: boolean;
  setCustomLeadActive: (active: boolean) => void;
  customLeadMinutes: number;
  setCustomLeadMinutes: (minutes: number) => void;
  notify: (message: string) => void;
  onOnboarding: () => void;
}) {
  function updatePeriod(index: number, field: "start" | "end", value: string) {
    setPeriods(periods.map((period, periodIndex) =>
      periodIndex === index ? { ...period, [field]: value } : period,
    ));
  }

  function addPeriod() {
    const last = periods.at(-1);
    setPeriods([...periods, {
      number: periods.length + 1,
      start: last?.end ?? "20:15",
      end: "21:45",
    }]);
  }

  function updateCustomLeadMinutes(value: number) {
    const normalized = Math.min(180, Math.max(1, value));
    setCustomLeadMinutes(normalized);
    setLeadMinutes(normalized);
  }

  return (
    <div className="view-enter settings-view" data-view="settings">
      <BrandHeader code="SYSTEM / 03" />
      <ScreenTitle path="CONFIG :// LOCAL SYSTEM" title="设置" code="SYS" />

      <section className="sync-console">
        <div className="sync-icon"><ShieldCheck size={25} strokeWidth={1.5} /></div>
        <div><span>LOCAL DATABASE</span><strong>运行正常</strong><small>最后同步 15:02:46</small></div>
        <i>ONLINE</i>
      </section>

      <section className="settings-group">
        <div className="settings-group__title"><CalendarRange size={16} /><span>学期信息</span><small>SEMESTER</small></div>
        <label className="terminal-field">
          <span>学期名称</span>
          <input value={semester.name} onChange={(event) => setSemester({ ...semester, name: event.target.value })} />
        </label>
        <label className="terminal-field terminal-field--inline">
          <span>开始日期</span>
          <input type="date" value={semester.startDate} onChange={(event) => setSemester({ ...semester, startDate: event.target.value })} />
        </label>
        <div className="stepper-row">
          <span><small>TOTAL WEEKS</small><b>总周数</b></span>
          <div>
            <button type="button" aria-label="减少总周数" onClick={() => setSemester({ ...semester, totalWeeks: Math.max(1, semester.totalWeeks - 1) })}>−</button>
            <strong>{String(semester.totalWeeks).padStart(2, "0")}</strong>
            <button type="button" aria-label="增加总周数" onClick={() => setSemester({ ...semester, totalWeeks: Math.min(52, semester.totalWeeks + 1) })}>＋</button>
          </div>
        </div>
      </section>

      <section className="settings-group">
        <div className="settings-group__title"><SlidersHorizontal size={16} /><span>每日节次</span><small>PERIOD CONFIG</small></div>
        <div className="period-list">
          {periods.map((period, index) => (
            <div className="period-row" key={period.number}>
              <span>0{period.number}</span>
              <label>START<input type="time" value={period.start} onChange={(event) => updatePeriod(index, "start", event.target.value)} /></label>
              <i>—</i>
              <label>END<input type="time" value={period.end} onChange={(event) => updatePeriod(index, "end", event.target.value)} /></label>
            </div>
          ))}
        </div>
        <button type="button" className="outlined-action" onClick={addPeriod}><Plus size={16} /> 添加节次 <small>ADD PERIOD</small></button>
      </section>

      <section className="settings-group">
        <div className="settings-group__title"><BellRing size={16} /><span>上课提醒</span><small>NOTIFICATION</small></div>
        <div className="setting-row">
          <span><b>课程开始前提醒</b><small>仅保存在这台设备</small></span>
          <Toggle checked={remindersEnabled} onChange={setRemindersEnabled} label="上课提醒" />
        </div>
        {remindersEnabled && (
          <div className="lead-selector" aria-label="提醒提前时间">
            {[0, 5, 10, 15, 30].map((minutes) => (
              <button
                type="button"
                className={!customLeadActive && leadMinutes === minutes ? "is-active" : ""}
                key={minutes}
                onClick={() => {
                  setCustomLeadActive(false);
                  setLeadMinutes(minutes);
                }}
              >
                <b>{minutes}</b><small>{minutes === 0 ? "准时" : "MIN"}</small>
              </button>
            ))}
            <button
              type="button"
              className={customLeadActive ? "is-active" : ""}
              onClick={() => {
                setCustomLeadActive(true);
                setLeadMinutes(customLeadMinutes);
              }}
            >
              <b>自定</b><small>CUSTOM</small>
            </button>
          </div>
        )}
        {remindersEnabled && customLeadActive && (
          <div className="custom-lead-control">
            <span><small>CUSTOM LEAD</small><b>自定义提前时间</b></span>
            <div>
              <button type="button" aria-label="减少自定义提醒时间" onClick={() => updateCustomLeadMinutes(customLeadMinutes - 1)}>−</button>
              <label>
                <input
                  type="number"
                  min="1"
                  max="180"
                  inputMode="numeric"
                  value={customLeadMinutes}
                  onChange={(event) => updateCustomLeadMinutes(Number(event.target.value) || 1)}
                  aria-label="自定义提前提醒分钟数"
                />
                <small>MIN</small>
              </label>
              <button type="button" aria-label="增加自定义提醒时间" onClick={() => updateCustomLeadMinutes(customLeadMinutes + 1)}>＋</button>
            </div>
          </div>
        )}
        <p className="setting-note"><i /> 通知权限正常 · {leadMinutes === 0 ? "将在上课时提醒" : `将在上课前 ${leadMinutes} 分钟提醒`}</p>
      </section>

      <section className="settings-group">
        <div className="settings-group__title"><ShieldCheck size={16} /><span>数据备份与迁移</span><small>DATA TRANSFER</small></div>
        <div className="transfer-actions">
          <button type="button" onClick={() => notify("IMPORT // 已载入模拟 JSON，校验通过")}><Download size={18} /><span><b>导入课表</b><small>JSON / VALIDATE</small></span></button>
          <button type="button" onClick={() => notify("EXPORT // 备份文件已生成")}><Upload size={18} /><span><b>导出备份</b><small>JSON / SHARE</small></span></button>
        </div>
        <p className="setting-note"><i /> 导入前会校验，并在确认后替换当前课表</p>
      </section>

      <button type="button" className="primary-terminal-action" onClick={() => notify("SYSTEM // 学期与提醒设置已保存")}>
        <Save size={17} /> 保存设置 <small>COMMIT CONFIG</small>
      </button>
      <button type="button" className="reset-preview" onClick={onOnboarding}>
        <RotateCcw size={14} /> 预览首次设置流程
      </button>
      <p className="build-code">QINGKE BUILD // WEB-CONCEPT 0.2</p>
    </div>
  );
}

function SelectField({ label, value, onChange, children }: {
  label: string;
  value: string | number;
  onChange: (value: string) => void;
  children: React.ReactNode;
}) {
  return (
    <label className="editor-select">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value)}>{children}</select>
      <ChevronDown size={15} aria-hidden="true" />
    </label>
  );
}

function CourseEditor({ course, periods, totalWeeks, onSave, onDelete, onClose }: {
  course?: Course;
  periods: Period[];
  totalWeeks: number;
  onSave: (course: Course) => void;
  onDelete: (courseId: string) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState(course?.name ?? "");
  const [englishName, setEnglishName] = useState(course?.englishName ?? "NEW COURSE");
  const [teacher, setTeacher] = useState(course?.teacher ?? "");
  const [color, setColor] = useState(course?.color ?? coursePalette[0]);
  const [schedules, setSchedules] = useState<CourseSchedule[]>(course?.schedules ?? [{
    id: "draft-1",
    day: 5,
    startPeriod: 3,
    endPeriod: 3,
    startWeek: 1,
    endWeek: totalWeeks,
    repeatRule: "every",
    room: "",
    start: periods[Math.min(2, periods.length - 1)]?.start ?? "14:05",
    end: periods[Math.min(2, periods.length - 1)]?.end ?? "15:40",
  }]);
  const [error, setError] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);

  function updateSchedule(id: string, patch: Partial<CourseSchedule>) {
    setSchedules((items) => items.map((item) => item.id === id ? { ...item, ...patch } : item));
  }

  function updatePeriod(schedule: CourseSchedule, field: "startPeriod" | "endPeriod", value: number) {
    const nextStart = field === "startPeriod" ? value : schedule.startPeriod;
    const nextEnd = field === "endPeriod" ? value : Math.max(schedule.endPeriod, value);
    const normalizedEnd = Math.max(nextStart, nextEnd);
    updateSchedule(schedule.id, {
      startPeriod: nextStart,
      endPeriod: normalizedEnd,
      start: periods[nextStart - 1]?.start ?? schedule.start,
      end: periods[normalizedEnd - 1]?.end ?? schedule.end,
    });
  }

  function adjustWeek(schedule: CourseSchedule, field: "startWeek" | "endWeek", delta: number) {
    if (field === "startWeek") {
      const startWeek = Math.min(schedule.endWeek, Math.max(1, schedule.startWeek + delta));
      updateSchedule(schedule.id, { startWeek });
    } else {
      const endWeek = Math.max(schedule.startWeek, Math.min(totalWeeks, schedule.endWeek + delta));
      updateSchedule(schedule.id, { endWeek });
    }
  }

  function addSchedule() {
    setSchedules((items) => [...items, {
      id: `draft-${items.length + 1}`,
      day: 1,
      startPeriod: 1,
      endPeriod: 1,
      startWeek: 1,
      endWeek: totalWeeks,
      repeatRule: "every",
      room: "",
      start: periods[0]?.start ?? "08:30",
      end: periods[0]?.end ?? "10:05",
    }]);
  }

  function save() {
    if (!name.trim()) {
      setError("课程名称不能为空 / COURSE NAME REQUIRED");
      return;
    }
    onSave({
      id: course?.id ?? `course-${Date.now()}`,
      name: name.trim(),
      englishName: englishName.trim() || "COURSE",
      teacher: teacher.trim(),
      color,
      state: course?.state ?? "upcoming",
      stateLabel: course?.stateLabel ?? "PLANNED",
      todayOrder: course?.todayOrder,
      schedules,
    });
  }

  return (
    <section className="overlay-screen editor-screen" data-overlay="course-editor" aria-label={course ? "编辑课程" : "添加课程"}>
      <DeviceStatus />
      <header className="overlay-header">
        <button type="button" onClick={onClose}><X size={19} /><span>取消</span></button>
        <div><small>COURSE CONFIG</small><strong>{course ? "编辑课程" : "添加课程"}</strong></div>
        <button type="button" className="save-button" onClick={save}><span>保存</span><Check size={19} /></button>
      </header>

      <div className="overlay-scroll">
        <div className="editor-title"><span>{course ? "EDIT" : "NEW"}</span><h1>课程档案</h1><small>COURSE PROFILE // 01</small></div>

        <section className="editor-panel profile-panel">
          <label className="editor-input"><span>课程名称 *</span><input value={name} placeholder="输入课程名称" onChange={(event) => { setName(event.target.value); setError(""); }} /></label>
          <label className="editor-input"><span>英文标识</span><input value={englishName} placeholder="COURSE NAME" onChange={(event) => setEnglishName(event.target.value.toUpperCase())} /></label>
          <label className="editor-input"><span>教师（选填）</span><input value={teacher} placeholder="输入教师姓名" onChange={(event) => setTeacher(event.target.value)} /></label>
          <div className="color-picker"><span>识别色 / SIGNAL COLOR</span><div>{coursePalette.map((option) => (
            <button type="button" key={option} className={color === option ? "is-active" : ""} style={{ "--swatch": option } as CSSProperties} onClick={() => setColor(option)} aria-label={`选择颜色${option}`}>
              {color === option && <Check size={14} />}
            </button>
          ))}</div></div>
          <label className="custom-color-picker">
            <span className="custom-color-swatch" style={{ "--custom-color": color } as CSSProperties}>
              <input type="color" value={color} onChange={(event) => setColor(event.target.value)} aria-label="自定义课程识别色" />
            </span>
            <span><b>自定义颜色</b><small>CUSTOM COLOR</small></span>
            <code>{color.toUpperCase()}</code>
          </label>
        </section>

        {schedules.map((schedule, index) => (
          <section className="editor-panel schedule-panel" key={schedule.id}>
            <div className="editor-panel__title"><span>上课安排 {String(index + 1).padStart(2, "0")}</span><small>SCHEDULE UNIT</small>{schedules.length > 1 && (
              <button type="button" aria-label={`删除上课安排${index + 1}`} onClick={() => setSchedules((items) => items.filter((item) => item.id !== schedule.id))}><Trash2 size={15} /></button>
            )}</div>
            <SelectField label="星期" value={schedule.day} onChange={(value) => updateSchedule(schedule.id, { day: Number(value) })}>
              {weekdayNames.map((weekday, weekdayIndex) => <option value={weekdayIndex + 1} key={weekday}>{weekday}</option>)}
            </SelectField>
            <div className="editor-grid-two">
              <SelectField label="开始节次" value={schedule.startPeriod} onChange={(value) => updatePeriod(schedule, "startPeriod", Number(value))}>
                {periods.map((period) => <option value={period.number} key={period.number}>第 {period.number} 节 · {period.start}</option>)}
              </SelectField>
              <SelectField label="结束节次" value={schedule.endPeriod} onChange={(value) => updatePeriod(schedule, "endPeriod", Number(value))}>
                {periods.map((period) => <option value={period.number} key={period.number}>第 {period.number} 节 · {period.end}</option>)}
              </SelectField>
            </div>
            <div className="week-range">
              <div><span>开始周</span><section><button type="button" onClick={() => adjustWeek(schedule, "startWeek", -1)}>−</button><strong>{String(schedule.startWeek).padStart(2, "0")}</strong><button type="button" onClick={() => adjustWeek(schedule, "startWeek", 1)}>＋</button></section></div>
              <i>—</i>
              <div><span>结束周</span><section><button type="button" onClick={() => adjustWeek(schedule, "endWeek", -1)}>−</button><strong>{String(schedule.endWeek).padStart(2, "0")}</strong><button type="button" onClick={() => adjustWeek(schedule, "endWeek", 1)}>＋</button></section></div>
            </div>
            <div className="repeat-selector" aria-label="重复规则">
              {(Object.keys(repeatLabels) as RepeatRule[]).map((rule) => <button type="button" className={schedule.repeatRule === rule ? "is-active" : ""} key={rule} onClick={() => updateSchedule(schedule.id, { repeatRule: rule })}>{repeatLabels[rule]}<small>{rule.toUpperCase()}</small></button>)}
            </div>
            <label className="editor-input"><span>教室（选填）</span><input value={schedule.room} placeholder="输入教学地点" onChange={(event) => updateSchedule(schedule.id, { room: event.target.value })} /></label>
          </section>
        ))}

        <button type="button" className="outlined-action editor-add-schedule" onClick={addSchedule}><Plus size={16} /> 添加上课安排 <small>ADD SCHEDULE UNIT</small></button>
        {error && <p className="editor-error">{error}</p>}

        {course && (
          <section className="delete-zone">
            <button type="button" className="delete-course-button" onClick={() => setConfirmDelete(true)}><Trash2 size={16} /> 删除课程</button>
            <p>删除后，这门课程的所有上课安排都会一并移除。</p>
          </section>
        )}
      </div>

      {confirmDelete && course && (
        <div className="confirm-layer" role="dialog" aria-modal="true" aria-labelledby="delete-title">
          <section>
            <span>DESTRUCTIVE ACTION</span>
            <h2 id="delete-title">删除这门课程？</h2>
            <p>课程及其所有上课安排都会被删除，这项操作无法撤销。</p>
            <div><button type="button" onClick={() => setConfirmDelete(false)}>取消</button><button type="button" className="is-danger" onClick={() => onDelete(course.id)}>确认删除</button></div>
          </section>
        </div>
      )}
      <span className="home-indicator" aria-hidden="true" />
    </section>
  );
}

function OnboardingScreen({ semester, periods, onComplete, onClose }: {
  semester: SemesterSettings;
  periods: Period[];
  onComplete: () => void;
  onClose: () => void;
}) {
  const [step, setStep] = useState(1);

  return (
    <section className="overlay-screen onboarding-screen" data-overlay="onboarding" aria-label="首次设置预览">
      <DeviceStatus />
      <header className="overlay-header onboarding-header">
        <button type="button" onClick={onClose}><X size={19} /><span>退出</span></button>
        <div><small>INITIAL PROTOCOL</small><strong>首次设置</strong></div>
        <span className="setup-step">0{step}/03</span>
      </header>
      <div className="overlay-scroll onboarding-scroll">
        <div className="onboarding-brand"><span>Q</span><div><small>WELCOME TO</small><strong>青课</strong><p>LOCAL ACADEMIC TERMINAL</p></div></div>

        {step === 1 && (
          <div className="setup-stage view-enter">
            <span className="setup-code">01 // SEMESTER IDENTITY</span>
            <h1>建立你的<br />教学周期</h1>
            <p>先设置当前学期。所有课程周次都会以开始日期所在周的周一为基准。</p>
            <section className="setup-preview-card">
              <small>SEMESTER NAME</small><strong>{semester.name}</strong>
              <div><span>START<br /><b>{semester.startDate}</b></span><span>TOTAL<br /><b>{semester.totalWeeks} WEEKS</b></span></div>
            </section>
          </div>
        )}

        {step === 2 && (
          <div className="setup-stage view-enter">
            <span className="setup-code">02 // PERIOD MATRIX</span>
            <h1>确认每日<br />节次时间</h1>
            <p>课程只需要选择节次，具体时间会统一读取这里的设置。</p>
            <section className="setup-periods">
              {periods.map((period) => <div key={period.number}><span>0{period.number}</span><b>{period.start}</b><i>—</i><b>{period.end}</b></div>)}
            </section>
          </div>
        )}

        {step === 3 && (
          <div className="setup-stage setup-complete view-enter">
            <span className="setup-code">03 // LOCAL READY</span>
            <div className="ready-mark"><Check size={36} /></div>
            <h1>课表终端<br />准备完成</h1>
            <p>设置只保存在本机。进入课表后即可添加第一门课程并开启提醒。</p>
            <section><ShieldCheck size={18} /><span><b>LOCAL DATABASE</b><small>READY / NO CLOUD ACCOUNT</small></span></section>
          </div>
        )}
      </div>
      <div className="onboarding-actions">
        {step > 1 && <button type="button" className="setup-back" onClick={() => setStep((value) => value - 1)}>上一步</button>}
        <button type="button" className="setup-next" onClick={() => step < 3 ? setStep((value) => value + 1) : onComplete()}>
          {step < 3 ? "继续" : "进入青课"}<ChevronRight size={18} /><small>{step < 3 ? "NEXT PROTOCOL" : "OPEN TERMINAL"}</small>
        </button>
      </div>
      <span className="home-indicator" aria-hidden="true" />
    </section>
  );
}

function BottomNavigation({ activeView, onChange }: {
  activeView: PrimaryView;
  onChange: (view: PrimaryView) => void;
}) {
  const items: Array<{ id: PrimaryView; label: string; code: string; icon: typeof Grid2X2 }> = [
    { id: "today", label: "今日", code: "01", icon: Grid2X2 },
    { id: "schedule", label: "课表", code: "02", icon: CalendarDays },
    { id: "settings", label: "设置", code: "03", icon: Settings2 },
  ];

  return (
    <nav className="liquid-nav" aria-label="主要导航">
      <span className="glass-glint" aria-hidden="true" />
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <button
            type="button"
            key={item.id}
            className={activeView === item.id ? "is-active" : ""}
            aria-current={activeView === item.id ? "page" : undefined}
            onClick={() => onChange(item.id)}
          >
            <span><Icon size={18} strokeWidth={1.8} /></span>
            <b>{item.label}</b>
            <small>{item.code}</small>
          </button>
        );
      })}
    </nav>
  );
}

export default function Home() {
  const [activeView, setActiveView] = useState<PrimaryView>("today");
  const [courses, setCourses] = useState(initialCourses);
  const [editorRoute, setEditorRoute] = useState<EditorRoute | null>(null);
  const [onboardingVisible, setOnboardingVisible] = useState(false);
  const [notice, setNotice] = useState("");
  const [semester, setSemester] = useState<SemesterSettings>({
    name: "2026 秋季学期",
    startDate: "2026-08-31",
    totalWeeks: 17,
  });
  const [periods, setPeriods] = useState(initialPeriods);
  const [remindersEnabled, setRemindersEnabled] = useState(true);
  const [leadMinutes, setLeadMinutes] = useState(10);
  const [customLeadActive, setCustomLeadActive] = useState(false);
  const [customLeadMinutes, setCustomLeadMinutes] = useState(20);

  const editingCourse = editorRoute?.courseId
    ? courses.find((course) => course.id === editorRoute.courseId)
    : undefined;

  function notify(message: string) {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
  }

  function saveCourse(course: Course) {
    setCourses((items) => items.some((item) => item.id === course.id)
      ? items.map((item) => item.id === course.id ? course : item)
      : [...items, course]);
    setEditorRoute(null);
    notify(`SAVED // ${course.name} 已写入会话课表`);
  }

  function deleteCourse(courseId: string) {
    const deleted = courses.find((course) => course.id === courseId);
    setCourses((items) => items.filter((course) => course.id !== courseId));
    setEditorRoute(null);
    notify(`DELETED // ${deleted?.name ?? "课程"} 已移除`);
  }

  return (
    <main className="demo-stage">
      <section className="phone-shell" data-screen={activeView} aria-label="青课课程表完整概念稿">
        <div className="ambient-field" aria-hidden="true">
          <span className="ambient-block ambient-block--one" />
          <span className="ambient-block ambient-block--two" />
          <span className="ambient-line" />
        </div>
        <DeviceStatus />

        <div className="screen-scroll" key={activeView}>
          {activeView === "today" && (
            <TodayScreen
              courses={courses}
              onEdit={(courseId) => setEditorRoute({ mode: "edit", courseId })}
              notify={notify}
            />
          )}
          {activeView === "schedule" && (
            <ScheduleScreen
              courses={courses}
              periods={periods}
              onEdit={(courseId) => setEditorRoute({ mode: "edit", courseId })}
            />
          )}
          {activeView === "settings" && (
            <SettingsScreen
              semester={semester}
              setSemester={setSemester}
              periods={periods}
              setPeriods={setPeriods}
              remindersEnabled={remindersEnabled}
              setRemindersEnabled={setRemindersEnabled}
              leadMinutes={leadMinutes}
              setLeadMinutes={setLeadMinutes}
              customLeadActive={customLeadActive}
              setCustomLeadActive={setCustomLeadActive}
              customLeadMinutes={customLeadMinutes}
              setCustomLeadMinutes={setCustomLeadMinutes}
              notify={notify}
              onOnboarding={() => setOnboardingVisible(true)}
            />
          )}
        </div>

        {activeView !== "settings" && (
          <button
            type="button"
            className="floating-add"
            aria-label="添加课程"
            onClick={() => setEditorRoute({ mode: "create" })}
          >
            <Plus size={25} strokeWidth={1.6} />
            <span>ADD</span>
          </button>
        )}

        <BottomNavigation activeView={activeView} onChange={setActiveView} />
        <div className={`system-toast${notice ? " is-visible" : ""}`} role="status" aria-live="polite">{notice}</div>
        <span className="home-indicator" aria-hidden="true" />

        {editorRoute && (
          <CourseEditor
            key={`${editorRoute.mode}-${editorRoute.courseId ?? "new"}`}
            course={editingCourse}
            periods={periods}
            totalWeeks={semester.totalWeeks}
            onSave={saveCourse}
            onDelete={deleteCourse}
            onClose={() => setEditorRoute(null)}
          />
        )}
        {onboardingVisible && (
          <OnboardingScreen
            semester={semester}
            periods={periods}
            onClose={() => setOnboardingVisible(false)}
            onComplete={() => {
              setOnboardingVisible(false);
              notify("INITIALIZED // 首次设置流程完成");
            }}
          />
        )}
      </section>
    </main>
  );
}
