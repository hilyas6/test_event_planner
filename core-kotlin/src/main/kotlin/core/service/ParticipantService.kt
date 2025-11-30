package core.service

import core.model.Participant
import core.repo.ParticipantRepository
import java.time.LocalDate
import java.util.*

class ParticipantService(private val participantRepository: ParticipantRepository) {

    /**
     * Creates or updates a participant using their email as the stable identifier.
     * Returning the saved entity allows callers to grab the generated ID immediately.
     */
    fun upsertParticipant(
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate,
        phone: String,
        email: String
    ): Participant {
        val trimmedEmail = email.trim()
        val existingParticipants = participantRepository.allParticipants()
        val matchingParticipant = existingParticipants.firstOrNull { it.email.equals(trimmedEmail, ignoreCase = true) }
        val participant = if (matchingParticipant != null) {
            // Preserve the same ID but refresh all personal details.
            matchingParticipant.copy(
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                phone = phone,
                email = trimmedEmail
            )
        } else {
            // No match? Generate a new participant record.
            Participant(UUID.randomUUID().toString(), firstName, lastName, dateOfBirth, phone, trimmedEmail)
        }
        val mergedParticipants = existingParticipants.filterNot { it.id == participant.id } + participant
        participantRepository.saveAllParticipants(mergedParticipants)
        return participant
    }
    fun reload(): List<Participant> {
        val jsonStore = participantRepository as? core.repo.file.JsonFileStore
        return jsonStore?.reloadParticipants() ?: participantRepository.allParticipants()
    }

}
