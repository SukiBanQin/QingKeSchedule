"use client";

import type { CSSProperties } from "react";
import { useState } from "react";
import {
  Bell,
  BookOpen,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Download,
  Grid2X2,
  MapPin,
  Plus,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Trash2,
  Upload,
  UserRound,
  X,
} from "lucide-react";

type PrimaryView = "today" | "schedule" | "settings";

type Course = {
  time: string;
  endTime: string;
  name: string;
  room: string;
  teacher: string;
  status: string;
  tone: "muted" | "active" | "next";
};

const todayCourses: Course[] = [
  {
    time: "08:30",
    endTime: "10:05",
    name: "高等数学",
    room: "理科楼 · 302",
    teacher: "林老师",
    status: "已结束",
    tone: "muted",
  },
  {
    time: "10:25",
    endTime: "12:00",
    name: "数据结构",
    room: "实验中心 · A204",
    teacher: "周老师",
    status: "已结束",
    tone: "muted",
  },
  {
    time: "14:05",
    endTime: "15:40",
    name: "交互设计基础",
    room: "设计楼 · 407",
    teacher: "顾老师",
    status: "进行中",
    tone: "active",
  },
  {
    time: "18:30",
    endTime: "20:05",
    name: "大学英语",
    room: "综合楼 · B112",
    teacher: "叶老师",
    status: "下一节",
    tone: "next",
  },
];

const weekDays = [
  { short: "一", date: "31" },
  { short: "二", date: "01" },
  { short: "三", date: "02" },
  { short: "四", date: "03" },
  { short: "五", date: "04" },
];

const weekCourses = [
  { day: 0, slot: 0, span: 2, name: "高数", room: "302", color: "#f8fcff" },
  { day: 1, slot: 2, span: 2, name: "英语", room: "B112", color: "#63d4ff" },
  { day: 2, slot: 1, span: 2, name: "数据结构", room: "A204", color: "#1188ff" },
  { day: 3, slot: 3, span: 2, name: "交互设计", room: "407", color: "#f8fcff" },
  { day: 4, slot: 0, span: 2, name: "体育", room: "操场", color: "#63d4ff" },
  { day: 4, slot: 4, span: 1, name: "研讨", room: "C03", color: "#1188ff" },
];

const editorColors = ["#fbfdff", "#69dcff", "#0c8cff", "#4d6cff", "#845cff"];

function DeviceStatus() {
  return (
    <header className="status-bar">
      <span>18:13</span>
      <div className="status-icons" aria-label="设备状态">
        <span className="signal">▮▮▮</span>
        <span>5G</span>
        <span className="battery">87</span>
      </div>
    </header>
  );
}

function TodayScreen({ onEdit }: { onEdit: (course?: Course) => void }) {
  return (
    <div className="screen view-enter" data-screen="today">
      <header className="today-hero">
        <div>
          <p className="eyebrow">QINGKE / DAILY LOG</p>
          <h1>今日</h1>
          <p className="date-line">2026.09 / THU</p>
        </div>
        <div className="date-mark" aria-label="9 月 3 日">
          <span>SEP</span>
          <strong>03</strong>
        </div>
      </header>

      <div className="briefing-panel">
        <div className="briefing-index">01</div>
        <div>
          <p>第 1 教学周</p>
          <strong>还有 1 节课程</strong>
        </div>
        <div className="briefing-meta">
          <Clock3 size={15} strokeWidth={2.4} />
          <span>17 MIN</span>
        </div>
      </div>

      <div className="section-heading">
        <div>
          <span className="section-number">04</span>
          <h2>课程序列</h2>
        </div>
        <button type="button" className="icon-button" aria-label="提醒设置">
          <Bell size={19} />
        </button>
      </div>

      <div className="course-list">
        {todayCourses.map((course, index) => (
          <article className={`course-row ${course.tone}`} key={course.time}>
            <div className="timeline-column">
              <span>{course.time}</span>
              <i aria-hidden="true" />
            </div>
            <button
              type="button"
              className="course-card"
              onClick={() => onEdit(course)}
              aria-label={`编辑${course.name}`}
            >
              <div className="course-card-topline">
                <span>{course.status}</span>
                <small>0{index + 1}</small>
              </div>
              <h3>{course.name}</h3>
              <p>{course.room}</p>
              <div className="course-card-footer">
                <span>{course.time}—{course.endTime}</span>
                <span>{course.teacher}</span>
                <ChevronRight size={17} />
              </div>
            </button>
          </article>
        ))}
      </div>
    </div>
  );
}

function ScheduleScreen({
  selectedDay,
  onSelectDay,
  onEdit,
}: {
  selectedDay: number;
  onSelectDay: (day: number) => void;
  onEdit: () => void;
}) {
  return (
    <div className="screen view-enter" data-screen="schedule">
      <header className="screen-title">
        <div>
          <p className="eyebrow">WEEK / MATRIX</p>
          <h1>课表</h1>
        </div>
        <div className="title-code">W01</div>
      </header>

      <div className="week-switcher">
        <button type="button" aria-label="上一周"><ChevronLeft size={18} /></button>
        <div>
          <small>2026 秋季学期</small>
          <strong>第 1 教学周</strong>
        </div>
        <button type="button" aria-label="下一周"><ChevronRight size={18} /></button>
      </div>

      <div className="weekday-strip" aria-label="选择星期">
        {weekDays.map((day, index) => (
          <button
            type="button"
            className={selectedDay === index ? "active" : ""}
            onClick={() => onSelectDay(index)}
            key={day.short}
          >
            <small>周{day.short}</small>
            <strong>{day.date}</strong>
          </button>
        ))}
      </div>

      <div className="matrix-label">
        <span>TIME AXIS</span>
        <span>05 PERIODS</span>
      </div>

      <div className="week-matrix">
        <div className="period-axis">
          {["08:30", "10:25", "14:05", "16:00", "18:30"].map((time, index) => (
            <div key={time}><b>0{index + 1}</b><span>{time}</span></div>
          ))}
        </div>
        {weekDays.map((day, dayIndex) => (
          <div className={`day-lane ${selectedDay === dayIndex ? "selected" : ""}`} key={day.short}>
            {weekCourses.filter((course) => course.day === dayIndex).map((course) => (
              <button
                type="button"
                className="matrix-course"
                onClick={onEdit}
                key={`${course.name}-${course.slot}`}
                style={{
                  "--block-color": course.color,
                  top: `calc(${course.slot} * 86px + 5px)`,
                  height: `calc(${course.span} * 86px - 10px)`,
                } as CSSProperties}
              >
                <strong>{course.name}</strong>
                <span>{course.room}</span>
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

function SettingsScreen({
  reminders,
  onToggleReminders,
  leadMinutes,
  onLeadMinutes,
}: {
  reminders: boolean;
  onToggleReminders: () => void;
  leadMinutes: number;
  onLeadMinutes: (minutes: number) => void;
}) {
  return (
    <div className="screen view-enter settings-screen" data-screen="settings">
      <header className="screen-title settings-title">
        <div>
          <p className="eyebrow">SYSTEM / CONFIG</p>
          <h1>设置</h1>
        </div>
        <SlidersHorizontal size={28} />
      </header>

      <section className="sync-card">
        <div className="sync-icon"><ShieldCheck size={23} /></div>
        <div><small>LOCAL STORAGE</small><strong>数据已安全保存</strong></div>
        <span>ONLINE</span>
      </section>

      <section className="setting-group">
        <div className="setting-group-title"><span>01</span><h2>学期参数</h2></div>
        <button type="button" className="setting-row">
          <div><small>当前学期</small><strong>2026 秋季学期</strong></div>
          <ChevronRight size={18} />
        </button>
        <div className="setting-stats">
          <div><small>开始日期</small><strong>08 / 31</strong></div>
          <div><small>总周数</small><strong>17 W</strong></div>
          <div><small>每日节次</small><strong>10</strong></div>
        </div>
      </section>

      <section className="setting-group">
        <div className="setting-group-title"><span>02</span><h2>上课提醒</h2></div>
        <div className="setting-row notification-row">
          <div><small>NOTIFICATION</small><strong>{reminders ? "提醒已开启" : "提醒已关闭"}</strong></div>
          <button
            type="button"
            role="switch"
            aria-checked={reminders}
            className={`switch ${reminders ? "on" : ""}`}
            onClick={onToggleReminders}
            aria-label="切换上课提醒"
          ><span /></button>
        </div>
        {reminders && (
          <div className="lead-options">
            {[0, 5, 10, 15].map((minutes) => (
              <button
                type="button"
                className={leadMinutes === minutes ? "active" : ""}
                onClick={() => onLeadMinutes(minutes)}
                key={minutes}
              >{minutes === 0 ? "准时" : `${minutes} MIN`}</button>
            ))}
          </div>
        )}
      </section>

      <section className="setting-group">
        <div className="setting-group-title"><span>03</span><h2>数据交换</h2></div>
        <div className="transfer-grid">
          <button type="button"><Upload size={20} /><span>导入课表</span><small>JSON / V1</small></button>
          <button type="button"><Download size={20} /><span>导出备份</span><small>LOCAL FILE</small></button>
        </div>
      </section>

      <p className="build-mark">QINGKE / CONCEPT UI 01 · BUILD 2026.09</p>
    </div>
  );
}

function CourseEditor({
  course,
  onClose,
  onSave,
}: {
  course?: Course;
  onClose: () => void;
  onSave: (name: string) => void;
}) {
  const [name, setName] = useState(course?.name ?? "");
  const [teacher, setTeacher] = useState(course?.teacher ?? "");
  const [room, setRoom] = useState(course?.room.replace(" · ", " ") ?? "");
  const [selectedColor, setSelectedColor] = useState(course ? 2 : 1);
  const [repeat, setRepeat] = useState("每周");
  const [confirmDelete, setConfirmDelete] = useState(false);

  return (
    <div className="screen editor-screen view-enter" data-screen="editor">
      <header className="editor-topbar">
        <button type="button" onClick={onClose}><X size={21} /><span>取消</span></button>
        <div><small>COURSE / EDIT</small><strong>{course ? "编辑课程" : "新建课程"}</strong></div>
        <button type="button" className="save-action" onClick={() => onSave(name || "未命名课程")}>
          <Check size={21} /><span>保存</span>
        </button>
      </header>

      <div className="editor-heading">
        <span>01</span>
        <div><p>COURSE PROFILE</p><h1>{course ? "调整课程" : "建立课程"}</h1></div>
      </div>

      <section className="editor-card course-profile">
        <label>
          <span><BookOpen size={16} />课程名称</span>
          <input value={name} onChange={(event) => setName(event.target.value)} placeholder="输入课程名称" />
        </label>
        <label>
          <span><UserRound size={16} />任课教师</span>
          <input value={teacher} onChange={(event) => setTeacher(event.target.value)} placeholder="选填" />
        </label>
        <div className="color-field">
          <span>课程识别色</span>
          <div>
            {editorColors.map((color, index) => (
              <button
                type="button"
                className={selectedColor === index ? "active" : ""}
                style={{ "--swatch": color } as CSSProperties}
                onClick={() => setSelectedColor(index)}
                aria-label={`选择颜色 ${index + 1}`}
                key={color}
              >{selectedColor === index && <Check size={14} />}</button>
            ))}
          </div>
        </div>
      </section>

      <div className="editor-heading compact"><span>02</span><div><p>SCHEDULE UNIT</p><h2>上课安排 1</h2></div></div>
      <section className="editor-card schedule-editor">
        <div className="weekday-picker">
          {["一", "二", "三", "四", "五", "六", "日"].map((day) => (
            <button type="button" className={day === "四" ? "active" : ""} key={day}>{day}</button>
          ))}
        </div>
        <label className="select-row"><span>开始节次</span><select defaultValue="3"><option value="3">第 3 节 · 14:05</option><option value="4">第 4 节 · 16:00</option></select><ChevronDown size={16} /></label>
        <label className="select-row"><span>结束节次</span><select defaultValue="4"><option value="4">第 4 节 · 15:40</option><option value="5">第 5 节 · 17:35</option></select><ChevronDown size={16} /></label>
        <div className="week-range"><div><small>开始周</small><strong>01</strong></div><i /><div><small>结束周</small><strong>17</strong></div></div>
        <div className="repeat-picker">
          {["每周", "单周", "双周"].map((item) => (
            <button type="button" className={repeat === item ? "active" : ""} onClick={() => setRepeat(item)} key={item}>{item}</button>
          ))}
        </div>
        <label className="room-field"><span><MapPin size={16} />教室</span><input value={room} onChange={(event) => setRoom(event.target.value)} placeholder="选填" /></label>
      </section>

      <button type="button" className="add-schedule-action"><Plus size={19} />添加上课安排</button>

      {course && (
        <button type="button" className="delete-action" onClick={() => setConfirmDelete(true)}>
          <Trash2 size={19} />删除课程
        </button>
      )}

      {confirmDelete && (
        <div className="confirm-layer" role="dialog" aria-modal="true" aria-label="确认删除课程">
          <div>
            <span>WARNING / DELETE</span>
            <h2>删除这门课程？</h2>
            <p>课程及其所有上课安排都会一并移除。</p>
            <div><button type="button" onClick={() => setConfirmDelete(false)}>返回</button><button type="button" className="danger" onClick={onClose}>确认删除</button></div>
          </div>
        </div>
      )}
    </div>
  );
}

function BottomNavigation({ view, onChange }: { view: PrimaryView; onChange: (view: PrimaryView) => void }) {
  const items = [
    { id: "today" as const, label: "今日", icon: CalendarDays },
    { id: "schedule" as const, label: "课表", icon: Grid2X2 },
    { id: "settings" as const, label: "设置", icon: Settings },
  ];

  return (
    <nav className="bottom-nav" aria-label="主要页面">
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <button type="button" className={view === item.id ? "active" : ""} onClick={() => onChange(item.id)} key={item.id}>
            <Icon size={20} /><span>{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

export default function Home() {
  const [view, setView] = useState<PrimaryView>("today");
  const [editorCourse, setEditorCourse] = useState<Course | undefined>();
  const [editorOpen, setEditorOpen] = useState(false);
  const [selectedDay, setSelectedDay] = useState(3);
  const [reminders, setReminders] = useState(true);
  const [leadMinutes, setLeadMinutes] = useState(10);
  const [notice, setNotice] = useState("");

  const openEditor = (course?: Course) => {
    setEditorCourse(course);
    setEditorOpen(true);
  };

  const saveCourse = (name: string) => {
    setEditorOpen(false);
    setView("today");
    setNotice(`${name} 已保存`);
    window.setTimeout(() => setNotice(""), 2200);
  };

  return (
    <main className="demo-stage">
      <section className="phone-shell" aria-label="青课 iPhone UI 概念稿">
        <div className="ambient-grid" aria-hidden="true" />
        <DeviceStatus />

        <div className={`app-content ${editorOpen ? "editor-content" : ""}`}>
          {editorOpen ? (
            <CourseEditor
              key={editorCourse?.name ?? "new-course"}
              course={editorCourse}
              onClose={() => setEditorOpen(false)}
              onSave={saveCourse}
            />
          ) : (
            <>
              {view === "today" && <TodayScreen onEdit={openEditor} />}
              {view === "schedule" && (
                <ScheduleScreen selectedDay={selectedDay} onSelectDay={setSelectedDay} onEdit={() => openEditor(todayCourses[2])} />
              )}
              {view === "settings" && (
                <SettingsScreen
                  reminders={reminders}
                  onToggleReminders={() => setReminders((current) => !current)}
                  leadMinutes={leadMinutes}
                  onLeadMinutes={setLeadMinutes}
                />
              )}
            </>
          )}
        </div>

        {!editorOpen && view !== "settings" && (
          <button type="button" className="floating-add" aria-label="添加课程" onClick={() => openEditor()}>
            <Plus size={25} strokeWidth={2.4} />
          </button>
        )}
        {!editorOpen && <BottomNavigation view={view} onChange={setView} />}

        {notice && <div className="save-notice"><Check size={17} />{notice}</div>}
        <div className="home-indicator" aria-hidden="true" />
      </section>
    </main>
  );
}
