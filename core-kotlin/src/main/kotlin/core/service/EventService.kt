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
        organiserEmail: String
    ) {
        require(title.isNotBlank()) { "Title is required" }
        require(endTime.isAfter(startTime)) { "End time must be after start time" }
        require(expectedSize > 0) { "Capacity must be positive" }

        val priority = expectedSize.coerceAtLeast(1)
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
            priority = priority
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
    fun reload(): List<Event> {
        val store = repo as? core.repo.file.JsonFileStore
        return store?.reloadEvents() ?: repo.allEvents()
    }

}

