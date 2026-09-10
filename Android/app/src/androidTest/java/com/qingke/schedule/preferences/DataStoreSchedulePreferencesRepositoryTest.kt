package com.qingke.schedule.preferences

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSchedulePreferencesRepositoryTest {
    @Test fun newStoreReadsCompleteDefaults() {
        runBlocking {
            val repository = repository("defaults")
            assertEquals(SchedulePreferences.defaults, repository.load())
            repository.close()
        }
    }

    @Test fun appearanceAndReminderRoundTripSanitizeUnknownAndLegacyFields() {
        runBlocking {
            val repository = repository("appearance-reminder")
            val saved = SchedulePreferences(
                appearanceMode = AppearanceMode.DARK,
                reminder = ReminderPreferences(true, 180, true),
            )
            assertEquals(saved, repository.save(saved))
            assertEquals(saved, repository.load())

            repository.dataStore.edit {
                it[SchedulePreferencesKeys.appearanceMode] = "unknown"
                it[SchedulePreferencesKeys.reminderLeadMinutes] = 181
                it[SchedulePreferencesKeys.usesCustomLeadTime] = true
            }
            assertEquals(AppearanceMode.SYSTEM, repository.load().appearanceMode)
            assertEquals(ReminderPreferences(true, 10, false), repository.load().reminder)

            repository.dataStore.edit {
                it[SchedulePreferencesKeys.reminderLeadMinutes] = 0
                it[SchedulePreferencesKeys.usesCustomLeadTime] = false
            }
            assertEquals(ReminderPreferences(true, 0, false), repository.load().reminder)

            repository.dataStore.edit {
                it[SchedulePreferencesKeys.reminderLeadMinutes] = 37
                it.remove(SchedulePreferencesKeys.usesCustomLeadTime)
            }
            assertEquals(ReminderPreferences(true, 37, true), repository.load().reminder)
            repository.close()
        }
    }

    @Test fun calendarRoundTripNormalizesDatesMakeupAndLunchBreak() {
        runBlocking {
            val file = file("calendar")
            val repository = DataStoreSchedulePreferencesRepository.create(file)
            val saved = repository.save(
                SchedulePreferences(
                    academicCalendar = AcademicCalendarPreferences(
                        weekendsAreNonTeachingDays = true,
                        nonTeachingDates = listOf("2026-10-02", "invalid", "2026-10-01", "2026-10-01"),
                        makeupTeachingDays = listOf(
                            MakeupTeachingDay("2026-10-01", 1),
                            MakeupTeachingDay("2026-10-10", 3),
                            MakeupTeachingDay("2026-10-10", 4),
                            MakeupTeachingDay("2026-10-11", 8),
                        ),
                        lunchBreak = LunchBreakSettings(true, "  ", "14:00", "11:40"),
                    ),
                ),
            )
            assertEquals(
                AcademicCalendarPreferences(
                    weekendsAreNonTeachingDays = true,
                    nonTeachingDates = listOf("2026-10-01", "2026-10-02"),
                    makeupTeachingDays = listOf(MakeupTeachingDay("2026-10-10", 4)),
                    lunchBreak = LunchBreakSettings.defaults,
                ),
                saved.academicCalendar,
            )
            repository.close()

            val reopened = DataStoreSchedulePreferencesRepository.create(file)
            assertEquals(saved, reopened.load())
            reopened.close()
        }
    }

    @Test fun missingNewFieldsUseDefaultsAndValidLunchTitleIsTrimmed() {
        runBlocking {
            val repository = repository("missing")
            repository.dataStore.edit {
                it[SchedulePreferencesKeys.lunchBreakTitle] = "  午间休息  "
                it.remove(SchedulePreferencesKeys.remindersEnabled)
                it.remove(SchedulePreferencesKeys.weekendsAreNonTeachingDays)
                it.remove(SchedulePreferencesKeys.lunchBreakEnabled)
                it.remove(SchedulePreferencesKeys.lunchBreakStartTime)
                it.remove(SchedulePreferencesKeys.lunchBreakEndTime)
            }
            val loaded = repository.load()
            assertEquals(ReminderPreferences.defaults, loaded.reminder)
            assertEquals(false, loaded.academicCalendar.weekendsAreNonTeachingDays)
            assertEquals(true, loaded.academicCalendar.lunchBreak.isEnabled)
            assertEquals("午间休息", loaded.academicCalendar.lunchBreak.title)
            assertEquals("11:40", loaded.academicCalendar.lunchBreak.startTime)
            assertEquals("14:00", loaded.academicCalendar.lunchBreak.endTime)
            repository.close()
        }
    }

    @Test fun corruptFileRecoversDefaultsAndCanBeSavedAndReopened() {
        runBlocking {
            val file = file("corrupt")
            file.writeText("not a preferences protobuf")
            val recovered = DataStoreSchedulePreferencesRepository.create(file)
            assertEquals(SchedulePreferences.defaults, recovered.load())
            val saved = SchedulePreferences(appearanceMode = AppearanceMode.LIGHT)
            recovered.save(saved)
            recovered.close()

            val reopened = DataStoreSchedulePreferencesRepository.create(file)
            assertEquals(saved, reopened.load())
            reopened.close()
        }
    }

    @Test fun writeFailureAndCancellationPropagateWithoutChangingStoredValue() {
        runBlocking {
            val file = file("failure")
            val repository = DataStoreSchedulePreferencesRepository.create(file)
            val stable = SchedulePreferences(appearanceMode = AppearanceMode.DARK)
            repository.save(stable)
            val failing = DataStoreSchedulePreferencesRepository(repository.dataStore, beforeWrite = { error("injected write failure") })
            assertThrows(IllegalStateException::class.java) { runBlocking { failing.save(SchedulePreferences.defaults) } }
            assertEquals(stable, repository.load())

            val cancelledRead = DataStoreSchedulePreferencesRepository(repository.dataStore, beforeRead = { throw CancellationException("read cancelled") })
            assertThrows(CancellationException::class.java) { runBlocking { cancelledRead.load() } }
            val cancelledWrite = DataStoreSchedulePreferencesRepository(repository.dataStore, beforeWrite = { throw CancellationException("write cancelled") })
            assertThrows(CancellationException::class.java) { runBlocking { cancelledWrite.save(SchedulePreferences.defaults) } }
            assertEquals(stable, repository.load())
            repository.close()

            val reopened = DataStoreSchedulePreferencesRepository.create(file)
            assertEquals(stable, reopened.load())
            assertEquals(SchedulePreferences.defaults, reopened.save(SchedulePreferences.defaults))
            reopened.close()
        }
    }

    @Test fun concurrentUpdatesUseLatestCommittedPreferences() {
        runBlocking {
            val repository = repository("concurrent")
            awaitAll(
                async { repository.update { it.copy(appearanceMode = AppearanceMode.DARK) } },
                async { repository.update { it.copy(reminder = it.reminder.copy(remindersEnabled = true)) } },
            )
            assertEquals(
                SchedulePreferences(
                    appearanceMode = AppearanceMode.DARK,
                    reminder = ReminderPreferences(remindersEnabled = true),
                ),
                repository.load(),
            )
            repository.close()
        }
    }

    private fun repository(name: String): DataStoreSchedulePreferencesRepository =
        DataStoreSchedulePreferencesRepository.create(file(name))

    private fun file(name: String): File = File(
        ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir,
        "preferences-$name-${System.nanoTime()}.preferences_pb",
    )
}

private fun <T : Throwable> assertThrows(type: Class<T>, block: () -> Unit) {
    try {
        block()
    } catch (error: Throwable) {
        if (type.isInstance(error)) return
        throw error
    }
    throw AssertionError("Expected ${type.name}")
}
