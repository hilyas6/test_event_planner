package core.service

import core.model.Event
import core.repo.EventRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class EventService(private val repo: EventRepository) {

    fun all(): List<Event> = repo.allEvents()

    fun addEvent(
        title: String,
        description: String,
        category: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        expectedSize: Int,
        organiserName: String,
        organiserEmail: String,
        priority: Int
    ) {
        require(title.isNotBlank()) { "Title is required" }
        require(endTime.isAfter(startTime)) { "End time must be after start time" }
        require(expectedSize > 0) { "Capacity must be positive" }
        require(priority > 0) { "Priority must be positive" }

        val normalizedPriority = priority.coerceIn(1, 10)

        val event = Event(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            category = category.trim(),
            date = date,
            startTime = startTime,
            endTime = endTime,
            expectedSize = expectedSize,
            organiserName = organiserName.trim(),
            organiserEmail = organiserEmail.trim(),
            priority = normalizedPriority,
            venueId = null
        )

        repo.saveEvent(event)
    }

    fun eventById(id: String): Event? = repo.eventById(id)

    fun deleteEventById(id: String) {
        val updated = repo.allEvents().filterNot { it.id == id }
        repo.saveAllEvents(updated)
    }

    fun replaceAll(events: List<Event>) {
        repo.saveAllEvents(events)
    }

    fun rescheduleEvent(
        eventId: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        venueId: String?
    ) {
        require(endTime.isAfter(startTime)) { "End time must be after start time" }

        val current = repo.eventById(eventId) ?: return

        val updated = current.copy(
            date = date,
            startTime = startTime,
            endTime = endTime,
            venueId = venueId ?: current.venueId
        )

        repo.saveEvent(updated)
    }

    fun reload(): List<Event> {
        val store = repo as? core.repo.file.JsonFileStore
        return store?.reloadEvents() ?: repo.allEvents()
    }
}
