import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it } from 'vitest'
import WeekScheduleGrid from '../../src/components/WeekScheduleGrid.vue'
import { courseFixture, scheduleFixture, semesterFixture } from '../fixtures'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/courses/:courseId/edit', component: { template: '<div />' } },
  ],
})

describe('WeekScheduleGrid', () => {
  it('显示当前周生效的课程卡片', () => {
    const wrapper = mount(WeekScheduleGrid, {
      props: {
        semester: semesterFixture,
        courses: [courseFixture()],
        week: 1,
        today: new Date(2026, 7, 31),
      },
      global: { plugins: [router] },
    })
    expect(wrapper.get('.course-card').text()).toContain('数据结构')
    expect(wrapper.text()).toContain('A101')
    expect(wrapper.get('.day-heading.is-today').text()).toContain('今天')
  })

  it('在双周隐藏单周课程', () => {
    const wrapper = mount(WeekScheduleGrid, {
      props: {
        semester: semesterFixture,
        courses: [courseFixture({ schedules: [scheduleFixture({ repeat: 'odd' })] })],
        week: 2,
        today: new Date(2026, 8, 7),
      },
      global: { plugins: [router] },
    })
    expect(wrapper.find('.course-card').exists()).toBe(false)
  })

  it('为同一时间的课程添加冲突标识并错位展示', () => {
    const courses = [
      courseFixture(),
      courseFixture({ id: 'course-2', name: '操作系统', schedules: [scheduleFixture({ id: 'schedule-2' })] }),
    ]
    const wrapper = mount(WeekScheduleGrid, {
      props: { semester: semesterFixture, courses, week: 1, today: new Date(2026, 7, 31) },
      global: { plugins: [router] },
    })
    const cards = wrapper.findAll('.course-card.is-conflicting')
    expect(cards).toHaveLength(2)
    expect(cards[0].attributes('style')).toContain('50%')
  })
})
