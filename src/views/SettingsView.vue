<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { createId, getWeekRangeLabel, toLocalDateString } from '../domain/rules'
import type { Period, Semester, StoredScheduleData } from '../domain/types'
import { validateSemester, type ValidationIssue } from '../domain/validation'
import { useSchedule } from '../composables/useSchedule'
import { downloadScheduleExport } from '../transfer/scheduleDataExport'
import { cloneData } from '../utils/clone'

const router = useRouter()
const store = useSchedule()

const defaultPeriods: Period[] = [
  { number: 1, startTime: '08:00', endTime: '08:45' },
  { number: 2, startTime: '08:55', endTime: '09:40' },
  { number: 3, startTime: '10:00', endTime: '10:45' },
  { number: 4, startTime: '10:55', endTime: '11:40' },
  { number: 5, startTime: '14:00', endTime: '14:45' },
  { number: 6, startTime: '14:55', endTime: '15:40' },
  { number: 7, startTime: '16:00', endTime: '16:45' },
  { number: 8, startTime: '16:55', endTime: '17:40' },
  { number: 9, startTime: '19:00', endTime: '19:45' },
  { number: 10, startTime: '19:55', endTime: '20:40' },
]

const now = new Date()
const semesterName = `${now.getFullYear()} ${now.getMonth() >= 6 ? '秋季' : '春季'}学期`
const initialSemester: Semester = store.semester.value
  ? cloneData(store.semester.value)
  : {
      id: createId(),
      name: semesterName,
      startDate: toLocalDateString(now),
      totalWeeks: 18,
      periods: cloneData(defaultPeriods),
    }

const form = reactive(initialSemester)
const issues = ref<ValidationIssue[]>([])
const saved = ref(false)
const clearDialogOpen = ref(false)
const exportMessage = ref('')
const isOnboarding = computed(() => !store.semester.value)
const weekOneLabel = computed(() => {
  const preview = { ...form, startDate: form.startDate || toLocalDateString(now) }
  return getWeekRangeLabel(preview, 1)
})

const errorFor = (path: string) => issues.value.find((issue) => issue.path === path)?.message

const addMinutes = (time: string, minutes: number) => {
  const [hours, currentMinutes] = time.split(':').map(Number)
  const total = Math.min(hours * 60 + currentMinutes + minutes, 23 * 60 + 59)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

const addPeriod = () => {
  const previous = form.periods.at(-1)
  const startTime = previous ? addMinutes(previous.endTime, 10) : '08:00'
  form.periods.push({
    number: form.periods.length + 1,
    startTime,
    endTime: addMinutes(startTime, 45),
  })
}

const removePeriod = (index: number) => {
  if (form.periods.length <= 1) return
  form.periods.splice(index, 1)
  form.periods.forEach((period, position) => {
    period.number = position + 1
  })
}

const save = async () => {
  issues.value = validateSemester(form)
  saved.value = false
  if (issues.value.length) return
  const success = store.saveSemester(cloneData(form))
  if (!success) return
  saved.value = true
  if (store.courses.value.length === 0) await router.push('/courses/new?welcome=1')
  else await router.push('/')
}

const clearAll = async () => {
  clearDialogOpen.value = false
  if (!store.clearAll()) return
  await router.replace('/settings')
}

const exportData = () => {
  try {
    const data = cloneData(store.state.data) as StoredScheduleData
    const file = downloadScheduleExport(data)
    exportMessage.value = `已导出 ${file.fileName}`
  } catch (error) {
    const detail = error instanceof Error ? error.message : '浏览器未能创建下载文件'
    exportMessage.value = `导出失败：${detail}`
  }
}
</script>

<template>
  <section class="settings-page page-wrap">
    <header class="page-heading settings-heading">
      <div>
        <span class="eyebrow">{{ isOnboarding ? '第一次使用' : '偏好设置' }}</span>
        <h1>{{ isOnboarding ? '先把这个学期安顿好' : '学期与节次' }}</h1>
        <p>教学周从开始日期所在周计算，课程时间统一使用这里设置的节次。</p>
      </div>
      <div
        v-if="isOnboarding"
        class="steps"
        aria-label="设置进度"
      >
        <span class="step is-active"><i>1</i><b>学期信息</b></span>
        <span class="step is-active"><i>2</i><b>每日节次</b></span>
        <span class="step"><i>3</i><b>添加课程</b></span>
      </div>
    </header>

    <form
      class="settings-layout"
      novalidate
      @submit.prevent="save"
    >
      <section class="surface-card form-section semester-section">
        <div class="section-title-row">
          <div>
            <span class="section-kicker">学期信息</span>
            <h2>从哪一周开始？</h2>
          </div>
          <span class="soft-badge">每周一为一周起点</span>
        </div>
        <div class="form-grid">
          <label
            class="field span-2"
            :class="{ 'has-error': errorFor('name') }"
          >
            <span>学期名称</span>
            <input
              v-model="form.name"
              name="semesterName"
              autocomplete="off"
            >
            <small
              v-if="errorFor('name')"
              class="field-error"
            >{{ errorFor('name') }}</small>
          </label>
          <label
            class="field"
            :class="{ 'has-error': errorFor('startDate') }"
          >
            <span>开始日期</span>
            <input
              v-model="form.startDate"
              name="startDate"
              type="date"
            >
            <small
              v-if="errorFor('startDate')"
              class="field-error"
            >{{ errorFor('startDate') }}</small>
            <small v-else>所在周计为第 1 教学周</small>
          </label>
          <label
            class="field"
            :class="{ 'has-error': errorFor('totalWeeks') }"
          >
            <span>总周数</span>
            <div class="input-suffix"><input
              v-model.number="form.totalWeeks"
              name="totalWeeks"
              type="number"
              min="1"
              max="52"
            ><span>周</span></div>
            <small
              v-if="errorFor('totalWeeks')"
              class="field-error"
            >{{ errorFor('totalWeeks') }}</small>
          </label>
        </div>
        <div class="preview-note">
          <span class="calendar-tile"><b>01</b><small>WEEK</small></span>
          <p><strong>第 1 教学周</strong><br>{{ weekOneLabel }}</p>
        </div>
      </section>

      <section class="surface-card form-section period-section">
        <div class="section-title-row">
          <div>
            <span class="section-kicker">每日节次</span>
            <h2>一天怎样安排？</h2>
            <p>课程时间会自动取对应节次的开始和结束时间。</p>
          </div>
          <span class="period-count">{{ form.periods.length }} 节</span>
        </div>
        <div class="period-list">
          <div
            v-for="(period, index) in form.periods"
            :key="index"
            class="period-row"
            :class="{ 'has-error': errorFor(`periods.${index}`) }"
          >
            <b>第 {{ period.number }} 节</b>
            <label>
              <span class="sr-only">第 {{ period.number }} 节开始时间</span>
              <input
                v-model="period.startTime"
                :name="`periodStart-${index}`"
                type="time"
              >
            </label>
            <span aria-hidden="true">—</span>
            <label>
              <span class="sr-only">第 {{ period.number }} 节结束时间</span>
              <input
                v-model="period.endTime"
                :name="`periodEnd-${index}`"
                type="time"
              >
            </label>
            <button
              class="icon-button"
              type="button"
              :disabled="form.periods.length === 1"
              :aria-label="`删除第 ${period.number} 节`"
              @click="removePeriod(index)"
            >
              ×
            </button>
          </div>
        </div>
        <button
          class="dashed-button"
          type="button"
          :disabled="form.periods.length >= 20"
          @click="addPeriod"
        >
          ＋ 添加节次
        </button>
        <p
          v-if="issues.some((issue) => issue.path.startsWith('periods'))"
          class="section-error"
          role="alert"
        >
          {{ issues.find((issue) => issue.path.startsWith('periods'))?.message }}
        </p>
      </section>

      <section
        v-if="!isOnboarding"
        class="surface-card form-section data-section"
      >
        <div class="section-title-row data-title-row">
          <div>
            <span class="section-kicker">数据备份</span>
            <h2>保存一份课表副本</h2>
            <p>导出标准 JSON 文件，可用于轻课课表 iOS 版或以后恢复数据。</p>
          </div>
          <button
            class="secondary-button"
            type="button"
            @click="exportData"
          >
            导出课表 JSON
          </button>
        </div>
        <p
          v-if="exportMessage"
          class="export-message"
          role="status"
        >
          {{ exportMessage }}
        </p>
      </section>

      <div class="settings-actions">
        <div class="storage-reminder">
          <span aria-hidden="true">i</span>
          <p><strong>数据保存在当前浏览器</strong><br>清除浏览器数据后课表可能无法恢复。</p>
        </div>
        <div class="button-row">
          <button
            v-if="!isOnboarding"
            class="text-button danger-text"
            type="button"
            @click="clearDialogOpen = true"
          >
            清除全部数据
          </button>
          <RouterLink
            v-if="!isOnboarding"
            class="secondary-button"
            to="/"
          >
            取消
          </RouterLink>
          <button
            class="primary-button"
            type="submit"
          >
            {{ isOnboarding ? '保存并添加课程 →' : '保存设置' }}
          </button>
        </div>
      </div>
      <p
        v-if="saved"
        class="success-message"
        role="status"
      >
        设置已保存
      </p>
    </form>

    <ConfirmDialog
      :open="clearDialogOpen"
      title="清除全部课表数据？"
      description="这会删除当前学期和所有课程，且无法撤销。"
      confirm-label="确认清除"
      tone="danger"
      @cancel="clearDialogOpen = false"
      @confirm="clearAll"
    />
  </section>
</template>
