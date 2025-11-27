package core.service

import core.model.ScheduledEvent
import core.repo.ScheduledEventRepository
import core.repo.file.JsonFileStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduledEventService(
    private val repo: ScheduledEventRepository,
    private val eventService: EventService,
    private val venueService: VenueService
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

        val alreadyScheduledSameDay = repo.allScheduledEvents()
            .firstOrNull { it.eventId == eventId && it.date == date }

        if (alreadyScheduledSameDay != null) {
            throw IllegalStateException("Event is already scheduled on $date. Remove it before booking another slot that day.")
        }

        ensureVenueCapacity(eventId, normalizedVenue, date, startTime, endTime)

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

    private fun ensureVenueCapacity(
        eventId: String,
        venueId: String?,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime
    ) {
        if (venueId == null) return

        val venue = venueService.all().firstOrNull { it.id == venueId }
            ?: throw IllegalArgumentException("Venue not found")
        val allEvents = eventService.all().associateBy { it.id }
        val scheduledForVenue = repo.allScheduledEvents()
            .filter { it.venueId == venueId && it.date == date && it.eventId != eventId }

        val overlappingUsage = scheduledForVenue.filter { existing ->
            timesOverlap(existing.startTime, existing.endTime, startTime, endTime)
        }

        val usedCapacity = overlappingUsage.sumOf { scheduled ->
            allEvents[scheduled.eventId]?.expectedSize ?: 0
        }

        val requestedEventSize = allEvents[eventId]?.expectedSize ?: 0
        val neededCapacity = usedCapacity + requestedEventSize

        if (neededCapacity > venue.capacity) {
            throw IllegalStateException("Venue capacity exceeded for that slot: requires $neededCapacity of ${venue.capacity}.")
        }
    }

    private fun timesOverlap(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
        aStart < bEnd && bStart < aEnd
}
