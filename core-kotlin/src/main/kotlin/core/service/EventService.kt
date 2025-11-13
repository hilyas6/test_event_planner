package core.service

import core.model.Event
import core.repo.EventRepository
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

class EventService(private val repo: EventRepository) {
    fun all(): List<Event> = repo.allEvents()

    fun addEvent(title: String, date: LocalDate, startTime: LocalTime, endTime: LocalTime, expectedSize: Int, venueId: String) {
        val event = Event(UUID.randomUUID().toString(), title, date, startTime, endTime, expectedSize, venueId)
        repo.saveEvent(event)
    }

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

