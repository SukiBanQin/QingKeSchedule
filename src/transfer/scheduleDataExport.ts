import type { StoredScheduleData } from '../domain/types'

export const SCHEDULE_EXPORT_MIME_TYPE = 'application/json'

export interface ScheduleExportFile {
  fileName: string
  contents: string
}

function localDateStamp(date: Date): string {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-')
}

export function serializeScheduleData(data: StoredScheduleData): string {
  return `${JSON.stringify(data, null, 2)}\n`
}

export function createScheduleExport(
  data: StoredScheduleData,
  exportedAt = new Date(),
): ScheduleExportFile {
  return {
    fileName: `qingke-schedule-${localDateStamp(exportedAt)}.json`,
    contents: serializeScheduleData(data),
  }
}

export function downloadScheduleExport(data: StoredScheduleData): ScheduleExportFile {
  const file = createScheduleExport(data)
  const blob = new Blob([file.contents], { type: SCHEDULE_EXPORT_MIME_TYPE })
  const objectURL = URL.createObjectURL(blob)
  const anchor = document.createElement('a')

  try {
    anchor.href = objectURL
    anchor.download = file.fileName
    anchor.hidden = true
    document.body.append(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    URL.revokeObjectURL(objectURL)
  }

  return file
}
