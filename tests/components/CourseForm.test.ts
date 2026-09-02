import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CourseForm from '../../src/components/CourseForm.vue'
import { courseFixture, semesterFixture } from '../fixtures'

describe('CourseForm', () => {
  it('支持动态增加和删除上课安排', async () => {
    const wrapper = mount(CourseForm, {
      props: {
        course: courseFixture(),
        semester: semesterFixture,
        issues: [],
        submitLabel: '保存课程',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    expect(wrapper.findAll('.schedule-block')).toHaveLength(1)
    await wrapper.get('.dashed-button').trigger('click')
    expect(wrapper.findAll('.schedule-block')).toHaveLength(2)
    await wrapper.get('[aria-label="删除安排 2"]').trigger('click')
    expect(wrapper.findAll('.schedule-block')).toHaveLength(1)
  })

  it('提交修剪后的课程文本', async () => {
    const wrapper = mount(CourseForm, {
      props: {
        course: courseFixture({ name: '  数据结构  ', teacher: ' 陈老师 ' }),
        semester: semesterFixture,
        issues: [],
        submitLabel: '保存课程',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    await wrapper.get('form').trigger('submit')
    const submitted = wrapper.emitted('submit')?.[0]?.[0]
    expect(submitted).toMatchObject({ name: '数据结构', teacher: '陈老师' })
  })

  it('呈现校验摘要', () => {
    const wrapper = mount(CourseForm, {
      props: {
        course: courseFixture(),
        semester: semesterFixture,
        issues: [{ path: 'name', message: '请填写课程名称' }],
        submitLabel: '保存课程',
      },
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } },
    })
    expect(wrapper.get('[role="alert"]').text()).toContain('请填写课程名称')
  })
})
