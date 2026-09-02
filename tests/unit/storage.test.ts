import { describe, expect, it } from 'vitest'
import {
  createEmptyData,
  LocalStorageScheduleRepository,
  parseStoredData,
  STORAGE_KEY,
} from '../../src/storage/localStorageRepository'
import { courseFixture, semesterFixture } from '../fixtures'

class MemoryStorage implements Storage {
  private values = new Map<string, string>()
  getRidOfSetItem = false
  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(key) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(key) }
  setItem(key: string, value: string) {
    if (this.getRidOfSetItem) throw new DOMException('Quota exceeded')
    this.values.set(key, value)
  }
}

describe('本地存储', () => {
  it('解析完整的版本化数据', () => {
    const data = {
      ...createEmptyData(),
      semester: semesterFixture,
      courses: [courseFixture()],
    }
    expect(parseStoredData(JSON.stringify(data))).toEqual(data)
  })

  it('拒绝未知版本和不完整数据', () => {
    expect(() => parseStoredData('{"schemaVersion":2}')).toThrow('版本')
    expect(() => parseStoredData('{"schemaVersion":1}')).toThrow('必要字段')
  })

  it('数据损坏时保留原始内容且不覆盖', () => {
    const storage = new MemoryStorage()
    storage.setItem(STORAGE_KEY, '{bad json')
    const repository = new LocalStorageScheduleRepository(storage)
    const result = repository.load()
    expect(result.recoveryRaw).toBe('{bad json')
    expect(result.warning).toContain('原数据尚未被覆盖')
    expect(storage.getItem(STORAGE_KEY)).toBe('{bad json')
  })

  it('保存和清除使用固定键名', () => {
    const storage = new MemoryStorage()
    const repository = new LocalStorageScheduleRepository(storage)
    repository.save(createEmptyData())
    expect(storage.getItem(STORAGE_KEY)).not.toBeNull()
    repository.clear()
    expect(storage.getItem(STORAGE_KEY)).toBeNull()
  })
})
