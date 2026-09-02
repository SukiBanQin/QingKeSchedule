import { describe, expect, it, vi } from 'vitest'
import { createScheduleStore } from '../../src/composables/useSchedule'
import type { ScheduleRepository } from '../../src/repositories/ScheduleRepository'
import { createEmptyData } from '../../src/storage/localStorageRepository'
import { courseFixture, semesterFixture } from '../fixtures'

describe('课表应用状态', () => {
  it('保存后立即同步跨页面共享的数据', () => {
    const repository: ScheduleRepository = {
      load: () => ({ data: createEmptyData(), recoveryRaw: null, warning: null }),
      save: vi.fn(),
      clear: vi.fn(),
    }
    const store = createScheduleStore(repository)
    store.initialize()
    expect(store.saveSemester(semesterFixture)).toBe(true)
    expect(store.saveCourse(courseFixture())).toBe(true)
    expect(store.semester.value?.name).toBe('测试学期')
    expect(store.courses.value.map((course) => course.name)).toEqual(['数据结构'])
    expect(repository.save).toHaveBeenCalledTimes(2)
  })

  it('存储写入失败时保留原内存状态并给出错误', () => {
    const repository: ScheduleRepository = {
      load: () => ({ data: createEmptyData(), recoveryRaw: null, warning: null }),
      save: () => { throw new Error('Quota exceeded') },
      clear: vi.fn(),
    }
    const store = createScheduleStore(repository)
    store.initialize()
    expect(store.saveSemester(semesterFixture)).toBe(false)
    expect(store.semester.value).toBeNull()
    expect(store.state.error).toContain('Quota exceeded')
  })
})
