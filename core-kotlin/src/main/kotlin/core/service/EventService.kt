package core.service

import core.model.Event
import core.repo.EventRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class EventService(private val eventRepository: EventRepository) {

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
        require(title.isNotBlank()) { "Title is required" }
        require(endTime.isAfter(startTime)) { "End time must be after start time" }
        require(expectedSize > 0) { "Capacity must be positive" }

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

        eventRepository.saveEvent(event)
    }

    fun eventById(id: String): Event? = eventRepository.eventById(id)

    fun deleteEventById(id: String) {
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
