import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { StoredScheduleData } from '../../src/domain/types'
import {
  createScheduleExport,
  SCHEDULE_EXPORT_MIME_TYPE,
  serializeScheduleData,
} from '../../src/transfer/scheduleDataExport'

function readFixture(fileName: string): StoredScheduleData {
  return JSON.parse(
    readFileSync(resolve(process.cwd(), 'shared/fixtures/valid', fileName), 'utf8'),
  ) as StoredScheduleData
}

describe('课表 JSON 导出', () => {
  it('输出可读且不改变 schemaVersion 1 内容', () => {
    const source = readFixture('complete-schedule.json')
    const serialized = serializeScheduleData(source)

    expect(serialized.endsWith('\n')).toBe(true)
    expect(JSON.parse(serialized)).toEqual(source)
    expect(serialized).toBe(serializeScheduleData(readFixture('web-export.json')))
  })

  it('使用稳定的扩展名、日期文件名和 JSON MIME 类型', () => {
    const source = readFixture('empty-schedule.json')
    const file = createScheduleExport(source, new Date(2026, 8, 3, 15, 30))

    expect(file.fileName).toBe('qingke-schedule-2026-09-03.json')
    expect(file.contents).toBe(serializeScheduleData(source))
    expect(SCHEDULE_EXPORT_MIME_TYPE).toBe('application/json')
  })
})
