<script setup lang="ts">
import { computed, ref } from 'vue'
import WeekScheduleGrid from '../components/WeekScheduleGrid.vue'
import { useSchedule } from '../composables/useSchedule'
import { getTeachingWeek, getWeekRangeLabel, isTeachingWeekInSemester } from '../domain/rules'

const store = useSchedule()
const semester = computed(() => store.semester.value!)
const today = new Date()
const actualWeek = computed(() => getTeachingWeek(semester.value, today))
const initialWeek = computed(() =>
  isTeachingWeekInSemester(semester.value, actualWeek.value) ? actualWeek.value : 1,
)
const selectedWeek = ref(initialWeek.value)
const rangeLabel = computed(() => getWeekRangeLabel(semester.value, selectedWeek.value))
const isActualWeekOutside = computed(() => !isTeachingWeekInSemester(semester.value, actualWeek.value))
const occurrenceCount = computed(() =>
  store.courses.value.reduce(
    (count, course) =>
      count +
      course.schedules.filter(
        (schedule) =>
          selectedWeek.value >= schedule.startWeek &&
          selectedWeek.value <= schedule.endWeek &&
          (schedule.repeat === 'every' ||
            (schedule.repeat === 'odd' && selectedWeek.value % 2 === 1) ||
            (schedule.repeat === 'even' && selectedWeek.value % 2 === 0)),
      ).length,
    0,
  ),
)

const goToCurrentWeek = () => {
  selectedWeek.value = isActualWeekOutside.value ? 1 : actualWeek.value
}
</script>

<template>
  <section class="week-page page-wrap">
    <header class="workspace-heading">
      <div>
        <div class="heading-meta">
          <span class="eyebrow">{{ semester.name }}</span>
          <span
            v-if="isActualWeekOutside"
            class="status-chip is-outside"
          >当前在学期外</span>
          <span
            v-else-if="selectedWeek === actualWeek"
            class="status-chip"
          >本周</span>
        </div>
        <h1>第 {{ selectedWeek }} 周</h1>
        <p>{{ rangeLabel }} · 共 {{ occurrenceCount }} 个上课安排</p>
      </div>
      <div
        class="week-toolbar"
        aria-label="切换教学周"
      >
        <button
          class="icon-button large"
          type="button"
          :disabled="selectedWeek <= 1"
          aria-label="上一周"
          @click="selectedWeek -= 1"
        >
          ←
        </button>
        <button
          class="secondary-button"
          type="button"
          @click="goToCurrentWeek"
        >
          回到本周
        </button>
        <button
          class="icon-button large"
          type="button"
          :disabled="selectedWeek >= semester.totalWeeks"
          aria-label="下一周"
          @click="selectedWeek += 1"
        >
          →
        </button>
        <RouterLink
          class="primary-button add-course-button"
          to="/courses/new"
        >
          <span aria-hidden="true">＋</span> 添加课程
        </RouterLink>
      </div>
    </header>

    <div
      v-if="store.courses.value.length === 0"
      class="empty-banner"
    >
      <div><strong>课表还是空的</strong><span>添加第一门课程后，这里会按星期与节次自动排列。</span></div>
      <RouterLink
        class="primary-button"
        to="/courses/new"
      >
        添加第一门课程
      </RouterLink>
    </div>

    <WeekScheduleGrid
      :semester="semester"
      :courses="store.courses.value"
      :week="selectedWeek"
      :today="today"
    />
    <p class="grid-footnote">
      点击课程卡片可查看和编辑 · 条纹边框表示同一时间有课程冲突
    </p>
  </section>
</template>
