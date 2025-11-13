package core.service

import core.model.Registration
import core.repo.EventRepository
import core.repo.ParticipantRepository
import core.repo.RegistrationRepository
import core.repo.VenueRepository
import java.time.LocalDateTime
import java.util.*

class RegistrationService(
    private val eventRepo: EventRepository,
    private val venueRepo: VenueRepository,
    private val participantRepo: ParticipantRepository,
    private val registrationRepo: RegistrationRepository
) {
    fun all(): List<Registration> = registrationRepo.allRegistrations()

    fun register(eventId: String, participantId: String): Registration {
        val event = eventRepo.eventById(eventId)
            ?: throw IllegalArgumentException("Event not found")
        val participant = participantRepo.participantById(participantId)
            ?: throw IllegalArgumentException("Participant not found")

        val now = LocalDateTime.now()
        val eventStart = LocalDateTime.of(event.date, event.startTime)
        val eventEnd = LocalDateTime.of(event.date, event.endTime)

        require(eventEnd.isAfter(eventStart)) { "Event has invalid time range" }
        if (eventStart.isBefore(now)) {
            throw IllegalStateException("Cannot register for past events")
        }

        val existingForEvent = registrationRepo.registrationsFor(eventId)
        if (existingForEvent.any { it.participantId == participantId }) {
            throw IllegalStateException("Participant is already registered")
        }

        val capacityLimit = capacityLimit(event)
        val capacityRemaining = capacityLimit - existingForEvent.size
        if (capacityRemaining <= 0) {
            throw IllegalStateException("Event is at full capacity")
        }

        val participantRegistrations = registrationRepo.allRegistrations()
            .filter { it.participantId == participantId }
            .mapNotNull { reg -> eventRepo.eventById(reg.eventId)?.let { reg to it } }

        val conflicts = participantRegistrations.filter { (_, otherEvent) ->
            otherEvent.date == event.date && timesOverlap(otherEvent.startTime, otherEvent.endTime, event.startTime, event.endTime)
        }
        if (conflicts.isNotEmpty()) {
            val conflictTitles = conflicts.joinToString { it.second.title }
            throw IllegalStateException("Participant has a conflicting event: $conflictTitles")
        }

        val registration = Registration(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            participantId = participantId,
            registeredAt = now
        )
        registrationRepo.saveRegistration(registration)
        println("✅ Registered ${participant.firstName} ${participant.lastName} for ${event.title}")
        return registration
    }

    private fun timesOverlap(aStart: java.time.LocalTime, aEnd: java.time.LocalTime, bStart: java.time.LocalTime, bEnd: java.time.LocalTime): Boolean {
        return aStart < bEnd && bStart < aEnd
    }

    fun registrationsFor(eventId: String): List<Registration> =
        registrationRepo.registrationsFor(eventId)

    fun deleteRegistrationById(id: String) {
        val updated = all().filterNot { it.id == id }
        registrationRepo.saveAllRegistrations(updated)
    }
    fun occupancyFor(eventId: String): Int = registrationRepo.registrationsFor(eventId).size

    fun remainingCapacity(event: core.model.Event): Int =
        (capacityLimit(event) - occupancyFor(event.id)).coerceAtLeast(0)

    fun capacityLimit(event: core.model.Event): Int {
        val venueCapacity = event.venueId?.let { venueRepo.venueById(it)?.capacity }
        val capacityCandidates = mutableListOf(event.expectedSize)
        if (venueCapacity != null) capacityCandidates += venueCapacity
        return capacityCandidates.minOrNull() ?: event.expectedSize
    }

    fun capacityLimit(eventId: String): Int? =
        eventRepo.eventById(eventId)?.let { capacityLimit(it) }

    fun reload(): List<core.model.Registration> {
        val store = registrationRepo as? core.repo.file.JsonFileStore
        return store?.reloadRegistrations() ?: registrationRepo.allRegistrations()
    }

}
