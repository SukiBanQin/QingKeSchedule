<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useSchedule } from '../composables/useSchedule'
import {
  getOccurrenceStatus,
  getOccurrenceTimeLabel,
  getOccurrencesForDate,
  getTeachingWeek,
  isTeachingWeekInSemester,
} from '../domain/rules'
import { DAY_NAMES, type CourseStatus } from '../domain/types'

const store = useSchedule()
const semester = computed(() => store.semester.value!)
const now = ref(new Date())
let timer: number | undefined

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 60_000)
})
onBeforeUnmount(() => window.clearInterval(timer))

const teachingWeek = computed(() => getTeachingWeek(semester.value, now.value))
const inSemester = computed(() => isTeachingWeekInSemester(semester.value, teachingWeek.value))
const occurrences = computed(() => getOccurrencesForDate(semester.value, store.courses.value, now.value))
const dayIndex = computed(() => (now.value.getDay() === 0 ? 6 : now.value.getDay() - 1))
const dateLabel = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(now.value),
)
const nextOccurrence = computed(() =>
  occurrences.value.find(
    (occurrence) => getOccurrenceStatus(occurrence, semester.value, now.value) !== 'finished',
  ),
)
const statusLabels: Record<CourseStatus, string> = {
  finished: '已结束',
  ongoing: '进行中',
  upcoming: '未开始',
}
</script>

<template>
  <section class="today-page page-wrap">
    <header class="workspace-heading today-heading">
      <div>
        <span class="eyebrow">{{ semester.name }} · {{ inSemester ? `第 ${teachingWeek} 周` : '学期外' }}</span>
        <h1>今天，{{ DAY_NAMES[dayIndex] }}</h1>
        <p>{{ dateLabel }}</p>
      </div>
      <RouterLink
        class="primary-button"
        to="/courses/new"
      >
        <span aria-hidden="true">＋</span> 添加课程
      </RouterLink>
    </header>

    <div
      v-if="!inSemester"
      class="empty-state surface-card"
    >
      <span
        class="empty-symbol"
        aria-hidden="true"
      >○</span>
      <h2>当前在学期外</h2>
      <p>你仍然可以在周课表中手动浏览本学期的课程。</p>
      <RouterLink
        class="secondary-button"
        to="/"
      >
        查看周课表
      </RouterLink>
    </div>

    <div
      v-else-if="occurrences.length === 0"
      class="empty-state surface-card"
    >
      <span
        class="empty-symbol is-rest"
        aria-hidden="true"
      >☕</span>
      <h2>今天没有课</h2>
      <p>这是一个空白日。去周课表看看接下来的安排吧。</p>
      <RouterLink
        class="secondary-button"
        to="/"
      >
        查看本周课程
      </RouterLink>
    </div>

    <div
      v-else
      class="today-layout"
    >
      <aside class="today-summary surface-card">
        <span class="section-kicker">今日概览</span>
        <strong>{{ occurrences.length }}</strong>
        <p>门课程</p>
        <div class="summary-rule" />
        <template v-if="nextOccurrence">
          <small>接下来</small>
          <b>{{ nextOccurrence.course.name }}</b>
          <span>{{ getOccurrenceTimeLabel(nextOccurrence, semester) }}</span>
        </template>
        <template v-else>
          <small>今日课程</small>
          <b>已经全部结束</b>
          <span>辛苦啦</span>
        </template>
      </aside>

      <ol
        class="today-list"
        aria-label="今日课程列表"
      >
        <li
          v-for="occurrence in occurrences"
          :key="occurrence.schedule.id"
          class="today-course-row"
        >
          <div class="timeline-time">
            <strong>{{ semester.periods.find((period) => period.number === occurrence.schedule.startPeriod)?.startTime }}</strong>
            <span>{{ semester.periods.find((period) => period.number === occurrence.schedule.endPeriod)?.endTime }}</span>
          </div>
          <span
            class="timeline-dot"
            :style="{ backgroundColor: occurrence.course.color }"
            aria-hidden="true"
          />
          <RouterLink
            class="today-course-card surface-card"
            :class="`is-${getOccurrenceStatus(occurrence, semester, now)}`"
            :to="`/courses/${occurrence.course.id}/edit`"
          >
            <div class="course-card-main">
              <span class="status-label">{{ statusLabels[getOccurrenceStatus(occurrence, semester, now)] }}</span>
              <h2>{{ occurrence.course.name }}</h2>
              <p>第 {{ occurrence.schedule.startPeriod }}–{{ occurrence.schedule.endPeriod }} 节</p>
            </div>
            <dl>
              <div><dt>教师</dt><dd>{{ occurrence.course.teacher || '未填写' }}</dd></div>
              <div><dt>教室</dt><dd>{{ occurrence.schedule.classroom || '待定' }}</dd></div>
            </dl>
            <span
              class="row-arrow"
              aria-hidden="true"
            >→</span>
          </RouterLink>
        </li>
      </ol>
    </div>
  </section>
</template>
