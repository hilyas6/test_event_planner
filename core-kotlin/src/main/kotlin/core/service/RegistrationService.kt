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

    fun register(eventId: String, participantId: String) {
        val event = eventRepo.eventById(eventId)
        val participant = participantRepo.participantById(participantId)
        val venue = event?.venueId?.let { venueRepo.venueById(it) }

        if (event == null || participant == null || venue == null) {
            println("⚠️ Registration failed: missing data for event=$eventId, participant=$participantId")
            return
        }

        val registration = Registration(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            participantId = participantId,
            registeredAt = LocalDateTime.now()
        )
        registrationRepo.saveRegistration(registration)
        println("✅ Registered ${participant.firstName} ${participant.lastName} for ${event.title}")
    }

    fun registrationsFor(eventId: String): List<Registration> =
        registrationRepo.registrationsFor(eventId)

    fun deleteRegistrationById(id: String) {
        val updated = all().filterNot { it.id == id }
        registrationRepo.saveAllRegistrations(updated)
    }
    fun reload(): List<core.model.Registration> {
        val store = registrationRepo as? core.repo.file.JsonFileStore
        return store?.reloadRegistrations() ?: registrationRepo.allRegistrations()
    }

}
