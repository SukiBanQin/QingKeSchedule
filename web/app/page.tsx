"use client";

import { useState } from "react";
import {
  Bell,
  CalendarDays,
  ChevronRight,
  Clock3,
  Grid2X2,
  MapPin,
  Plus,
  Settings2,
} from "lucide-react";

type CourseState = "complete" | "current" | "upcoming";

type Course = {
  id: string;
  order: string;
  start: string;
  end: string;
  name: string;
  room: string;
  teacher: string;
  state: CourseState;
  stateLabel: string;
};

const courses: Course[] = [
  {
    id: "math",
    order: "01",
    start: "08:30",
    end: "10:05",
    name: "高等数学",
    room: "理科楼 302",
    teacher: "林老师",
    state: "complete",
    stateLabel: "COMPLETE",
  },
  {
    id: "structure",
    order: "02",
    start: "10:25",
    end: "12:00",
    name: "数据结构",
    room: "实验中心 A204",
    teacher: "周老师",
    state: "complete",
    stateLabel: "COMPLETE",
  },
  {
    id: "design",
    order: "03",
    start: "14:05",
    end: "15:40",
    name: "交互设计基础",
    room: "设计楼 407",
    teacher: "顾老师",
    state: "current",
    stateLabel: "CURRENT",
  },
  {
    id: "english",
    order: "04",
    start: "18:30",
    end: "20:05",
    name: "大学英语",
    room: "综合楼 B112",
    teacher: "叶老师",
    state: "upcoming",
    stateLabel: "NEXT",
  },
];

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

function CourseRow({
  course,
  selected,
  onSelect,
}: {
  course: Course;
  selected: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      className={`course-row course-row--${course.state}${selected ? " is-selected" : ""}`}
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`查看${course.name}课程信息`}
    >
      <span className="course-index">{course.order}</span>
      <span className="course-time">
        <strong>{course.start}</strong>
        <small>{course.end}</small>
      </span>
      <span className="course-copy">
        <span className="course-status">{course.stateLabel}</span>
        <strong>{course.name}</strong>
        <small>{course.room} / {course.teacher}</small>
      </span>
      <span className="course-open" aria-hidden="true">
        <ChevronRight size={18} strokeWidth={1.7} />
      </span>
    </button>
  );
}

export default function Home() {
  const [selectedCourse, setSelectedCourse] = useState("design");
  const [notice, setNotice] = useState("");

  function showNotice(message: string) {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
  }

  return (
    <main className="demo-stage">
      <section className="phone-shell" data-screen="today" aria-label="青课今日页面概念稿">
        <div className="ambient-field" aria-hidden="true">
          <span className="ambient-block ambient-block--one" />
          <span className="ambient-block ambient-block--two" />
          <span className="ambient-line" />
        </div>

        <DeviceStatus />

        <div className="screen-scroll">
          <header className="command-header">
            <div className="brand-lockup">
              <span className="brand-symbol" aria-hidden="true">Q</span>
              <span>
                <b>QINGKE</b>
                <small>ACADEMIC TERMINAL</small>
              </span>
            </div>
            <div className="system-meta">
              <span>LOCAL / 01</span>
              <button
                type="button"
                className="square-action"
                aria-label="查看课程提醒"
                onClick={() => showNotice("REMINDER // 今日课程提醒已开启")}
              >
                <Bell size={18} strokeWidth={1.7} />
                <i aria-hidden="true" />
              </button>
            </div>
          </header>

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
            <span>ACTIVE MISSION</span>
              <span>SYNC 15:02:46</span>
          </div>

          <section className="active-course" aria-label="当前课程">
            <div className="active-course__bar">
              <span><i /> CURRENT</span>
              <span>03 // 04</span>
            </div>
            <div className="active-course__body">
              <div className="active-time">
                <strong>14:05</strong>
                <span>— 15:40</span>
              </div>
              <div className="active-copy">
                <span>INTERACTION DESIGN</span>
                <h2>交互设计基础</h2>
                <p><MapPin size={14} /> 设计楼 407 <i /> 顾老师</p>
              </div>
              <button
                type="button"
                className="active-open"
                aria-label="查看交互设计基础"
                onClick={() => setSelectedCourse("design")}
              >
                <ChevronRight size={21} strokeWidth={1.6} />
              </button>
            </div>
            <div className="active-progress">
              <span style={{ width: "62%" }} />
            </div>
            <div className="active-course__footer">
              <span><Clock3 size={13} /> 已进行 58 分钟</span>
              <span>剩余 37 MIN</span>
            </div>
          </section>

          <div className="list-heading">
            <div>
              <span>04</span>
              <h2>课程序列</h2>
            </div>
            <p>MISSION QUEUE / ALL DAY</p>
          </div>

          <section className="course-list" aria-label="今日课程列表">
            {courses.map((course) => (
              <CourseRow
                key={course.id}
                course={course}
                selected={selectedCourse === course.id}
                onSelect={() => setSelectedCourse(course.id)}
              />
            ))}
          </section>

          <p className="end-marker">END OF SCHEDULE // 20:05</p>
        </div>

        <button
          type="button"
          className="floating-add"
          aria-label="添加课程"
          onClick={() => showNotice("NEW ENTRY // 课程编辑器将在下一步接入")}
        >
          <Plus size={25} strokeWidth={1.6} />
          <span>ADD</span>
        </button>

        <nav className="liquid-nav" aria-label="主要导航">
          <span className="glass-glint" aria-hidden="true" />
          <button type="button" className="is-active" aria-current="page">
            <span><Grid2X2 size={18} strokeWidth={1.8} /></span>
            <b>今日</b>
            <small>01</small>
          </button>
          <button type="button" onClick={() => showNotice("WEEK MATRIX // 本轮仅展示今日页面") }>
            <span><CalendarDays size={18} strokeWidth={1.8} /></span>
            <b>课表</b>
            <small>02</small>
          </button>
          <button type="button" onClick={() => showNotice("SYSTEM // 本轮仅展示今日页面") }>
            <span><Settings2 size={18} strokeWidth={1.8} /></span>
            <b>设置</b>
            <small>03</small>
          </button>
        </nav>

        <div className={`system-toast${notice ? " is-visible" : ""}`} role="status" aria-live="polite">
          {notice}
        </div>
        <span className="home-indicator" aria-hidden="true" />
      </section>
    </main>
  );
}
