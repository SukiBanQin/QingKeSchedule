import type { StoredScheduleData } from '../domain/types'

export interface RepositoryLoadResult {
  data: StoredScheduleData
  recoveryRaw: string | null
  warning: string | null
}

export interface ScheduleRepository {
  load(): RepositoryLoadResult
  save(data: StoredScheduleData): void
  clear(): void
}
