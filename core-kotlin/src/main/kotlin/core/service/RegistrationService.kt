package core.service

import core.model.Registration
import core.repo.EventRepository
import core.repo.ParticipantRepository
import core.repo.RegistrationRepository
import core.repo.ScheduledEventRepository
import core.repo.VenueRepository
import java.time.LocalDateTime
import java.util.*

class RegistrationService(
    private val eventRepo: EventRepository,
    private val venueRepo: VenueRepository,
    private val participantRepo: ParticipantRepository,
    private val registrationRepo: RegistrationRepository,
    private val scheduledEventRepo: ScheduledEventRepository
) {
    fun all(): List<Registration> = registrationRepo.allRegistrations()

    fun register(eventId: String, participantId: String): Registration {
        val schedule = scheduledEventRepo.allScheduledEvents().find { it.eventId == eventId }
            ?: throw IllegalStateException("Event does not have a confirmed schedule")
        return registerForScheduledEvent(schedule, participantId)
    }

    fun registerForScheduledEvent(
        schedule: core.model.ScheduledEvent,
        participantId: String
    ): Registration {
        val confirmedSchedule = scheduledEventRepo.allScheduledEvents()
            .find { it.eventId == schedule.eventId }
            ?: throw IllegalStateException("Event does not have a confirmed schedule")

        val event = eventRepo.eventById(confirmedSchedule.eventId)
            ?: throw IllegalArgumentException("Event not found")
        val participant = participantRepo.participantById(participantId)
            ?: throw IllegalArgumentException("Participant not found")

        val now = LocalDateTime.now()
        val scheduleStart = LocalDateTime.of(confirmedSchedule.date, confirmedSchedule.startTime)
        val scheduleEnd = LocalDateTime.of(confirmedSchedule.date, confirmedSchedule.endTime)

        require(scheduleEnd.isAfter(scheduleStart)) { "Schedule has invalid time range" }
        if (scheduleStart.isBefore(now)) {
            throw IllegalStateException("Cannot register for past schedules")
        }

        val existingForEvent = registrationRepo.registrationsFor(confirmedSchedule.eventId)
        if (existingForEvent.any { it.participantId == participantId }) {
            throw IllegalStateException("Participant is already registered")
        }

        val capacityLimit = capacityLimit(event)
        val capacityRemaining = capacityLimit - existingForEvent.size
        if (capacityRemaining <= 0) {
            throw IllegalStateException("Event is at full capacity")
        }

        val scheduledByEvent = scheduledEventRepo.allScheduledEvents().associateBy { it.eventId }
        val participantRegistrations = registrationRepo.allRegistrations()
            .filter { it.participantId == participantId }

        val conflicts = participantRegistrations.mapNotNull { reg ->
            val otherEvent = eventRepo.eventById(reg.eventId)
            val otherSchedule = scheduledByEvent[reg.eventId]

            val otherStart = when {
                otherSchedule != null -> LocalDateTime.of(otherSchedule.date, otherSchedule.startTime)
                otherEvent != null -> LocalDateTime.of(otherEvent.date, otherEvent.startTime)
                else -> null
            }
            val otherEnd = when {
                otherSchedule != null -> LocalDateTime.of(otherSchedule.date, otherSchedule.endTime)
                otherEvent != null -> LocalDateTime.of(otherEvent.date, otherEvent.endTime)
                else -> null
            }

            if (otherStart != null && otherEnd != null && otherStart.toLocalDate() == confirmedSchedule.date) {
                val overlap = timesOverlap(
                    otherStart.toLocalTime(),
                    otherEnd.toLocalTime(),
                    confirmedSchedule.startTime,
                    confirmedSchedule.endTime
                )
                if (overlap) {
                    val title = otherEvent?.title ?: "Event ${reg.eventId}"
                    title
                } else {
                    null
                }
            } else {
                null
            }
        }

        if (conflicts.isNotEmpty()) {
            val conflictTitles = conflicts.joinToString()
            throw IllegalStateException("Participant has a conflicting event: $conflictTitles")
        }

        val registration = Registration(
            id = UUID.randomUUID().toString(),
            eventId = confirmedSchedule.eventId,
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
