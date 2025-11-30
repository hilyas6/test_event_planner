package core.service

import core.model.Participant
import core.repo.ParticipantRepository
import java.time.LocalDate
import java.util.*

class ParticipantService(private val participantRepository: ParticipantRepository) {

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
            matchingParticipant.copy(
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                phone = phone,
                email = trimmedEmail
            )
        } else {
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
