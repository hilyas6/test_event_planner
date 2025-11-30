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
    private val eventRepository: EventRepository,
    private val venueRepository: VenueRepository,
    private val participantRepository: ParticipantRepository,
    private val registrationRepository: RegistrationRepository,
    private val scheduledEventRepository: ScheduledEventRepository
) {
    fun all(): List<Registration> = registrationRepository.allRegistrations()


    fun registerForScheduledEvent(
        schedule: core.model.ScheduledEvent,
        participantId: String
    ): Registration {
        val event = eventRepository.eventById(schedule.eventId)
            ?: throw IllegalArgumentException("Event not found")
        val participant = participantRepository.participantById(participantId)
            ?: throw IllegalArgumentException("Participant not found")

        val now = LocalDateTime.now()
        val scheduleStart = LocalDateTime.of(schedule.date, schedule.startTime)
        val scheduleEnd = LocalDateTime.of(schedule.date, schedule.endTime)

        require(scheduleEnd.isAfter(scheduleStart)) { "Schedule has invalid time range" }
        if (scheduleStart.isBefore(now)) {
            throw IllegalStateException("Cannot register for past schedules")
        }

        val existingForEvent = registrationRepository.registrationsFor(schedule.eventId)
        if (existingForEvent.any { it.participantId == participantId }) {
            throw IllegalStateException("Participant is already registered")
        }

        val capacityLimit = capacityLimit(event)
        val capacityRemaining = capacityLimit - existingForEvent.size
        if (capacityRemaining <= 0) {
            throw IllegalStateException("Event is at full capacity")
        }

        val scheduledByEvent = scheduledEventRepository.allScheduledEvents().associateBy { it.eventId }
        val participantRegistrations = registrationRepository.allRegistrations()
            .filter { it.participantId == participantId }

        val conflicts = participantRegistrations.mapNotNull { reg ->
            val otherEvent = eventRepository.eventById(reg.eventId)
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

            if (otherStart != null && otherEnd != null && otherStart.toLocalDate() == schedule.date) {
                val overlap = timesOverlap(
                    otherStart.toLocalTime(),
                    otherEnd.toLocalTime(),
                    schedule.startTime,
                    schedule.endTime
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
            eventId = schedule.eventId,
            participantId = participantId,
            registeredAt = now
        )
        registrationRepository.saveRegistration(registration)
        println(" Registered ${participant.firstName} ${participant.lastName} for ${event.title}")
        return registration
    }

    private fun timesOverlap(aStart: java.time.LocalTime, aEnd: java.time.LocalTime, bStart: java.time.LocalTime, bEnd: java.time.LocalTime): Boolean {
        return aStart < bEnd && bStart < aEnd
    }

    fun deleteRegistrationById(id: String) {
        val remainingRegistrations = all().filterNot { it.id == id }
        registrationRepository.saveAllRegistrations(remainingRegistrations)
    }
    fun occupancyFor(eventId: String): Int = registrationRepository.registrationsFor(eventId).size

    fun remainingCapacity(event: core.model.Event): Int =
        (capacityLimit(event) - occupancyFor(event.id)).coerceAtLeast(0)

    fun capacityLimit(event: core.model.Event): Int {
        val venueCapacity = event.venueId?.let { venueRepository.venueById(it)?.capacity }
        val capacityCandidates = mutableListOf(event.expectedSize)
        if (venueCapacity != null) capacityCandidates += venueCapacity
        return capacityCandidates.minOrNull() ?: event.expectedSize
    }
    fun reload(): List<core.model.Registration> {
        val jsonStore = registrationRepository as? core.repo.file.JsonFileStore
        return jsonStore?.reloadRegistrations() ?: registrationRepository.allRegistrations()
    }

}
