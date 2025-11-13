package core.service

import core.model.ScheduledEvent
import core.repo.ScheduledEventRepository
import core.repo.file.JsonFileStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduledEventService(
    private val repo: ScheduledEventRepository,
    private val eventService: EventService
) {

    fun all(): List<ScheduledEvent> = repo.allScheduledEvents()

    fun confirmSchedule(
        eventId: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        venueId: String?
    ): ScheduledEvent {
        val normalizedVenue = venueId?.takeIf { it.isNotBlank() }
        require(endTime.isAfter(startTime)) { "End time must be after start time" }

        val existing = eventService.eventById(eventId)
            ?: throw IllegalArgumentException("Event not found")

        eventService.rescheduleEvent(eventId, date, startTime, endTime, normalizedVenue)

        val record = ScheduledEvent(
            eventId = eventId,
            venueId = normalizedVenue,
            date = date,
            startTime = startTime,
            endTime = endTime,
            confirmedAt = LocalDateTime.now()
        )
        repo.saveScheduledEvent(record)
        return record
    }

    fun removeSchedule(eventId: String) {
        repo.deleteScheduledEventByEventId(eventId)
    }

    fun reload(): List<ScheduledEvent> {
        val store = repo as? JsonFileStore
        return store?.reloadScheduledEvents() ?: repo.allScheduledEvents()
    }
}
