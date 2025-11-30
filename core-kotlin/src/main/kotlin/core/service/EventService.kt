package core.service

import core.model.Event
import core.repo.EventRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class EventService(private val eventRepository: EventRepository) {

    /**
     * Returns every event, regardless of whether it is scheduled yet.
     */
    fun all(): List<Event> = eventRepository.allEvents()

    fun addEvent(
        title: String,
        description: String,
        category: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        expectedSize: Int,
        organiserName: String,
        organiserEmail: String
    ) {
        // Basic validation keeps garbage data out of the repository and avoids
        // surprising results elsewhere (e.g. scheduling or registration).
        require(title.isNotBlank()) { "Title is required" }
        require(endTime.isAfter(startTime)) { "End time must be after start time" }
        require(expectedSize > 0) { "Capacity must be positive" }

        val today = LocalDate.now()
        require(!date.isBefore(today)) { "Event date cannot be in the past" }

        if (date.isEqual(today)) {
            val currentTime = LocalTime.now()
            require(startTime.isAfter(currentTime)) { "Start time must be later than the current time" }
        }

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
            venueId = null
        )

        // Persist the new record; repositories decide how/where it is stored.
        eventRepository.saveEvent(event)
    }

    fun eventById(id: String): Event? = eventRepository.eventById(id)

    fun deleteEventById(id: String) {
        // Repository does not expose deletion directly, so we replace the collection
        // without the matching entry.
        val remainingEvents = eventRepository.allEvents().filterNot { it.id == id }
        eventRepository.saveAllEvents(remainingEvents)
    }

    fun rescheduleEvent(
        eventId: String,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        venueId: String?
    ) {
        // Avoid nonsensical time ranges before touching stored data.
        require(endTime.isAfter(startTime)) { "End time must be after start time" }

        val currentEvent = eventRepository.eventById(eventId) ?: return

        val updatedEvent = currentEvent.copy(
            date = date,
            startTime = startTime,
            endTime = endTime,
            venueId = venueId ?: currentEvent.venueId
        )

        eventRepository.saveEvent(updatedEvent)
    }

    fun reload(): List<Event> {
        val jsonStore = eventRepository as? core.repo.file.JsonFileStore
        return jsonStore?.reloadEvents() ?: eventRepository.allEvents()
    }
}
