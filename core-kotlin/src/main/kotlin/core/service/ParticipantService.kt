package core.service

import core.model.Participant
import core.repo.ParticipantRepository
import java.time.LocalDate
import java.util.*

class ParticipantService(private val repo: ParticipantRepository) {

    fun upsertParticipant(
        firstName: String,
        lastName: String,
        dob: LocalDate,
        phone: String,
        email: String
    ): Participant {
        val trimmedEmail = email.trim()
        val current = repo.allParticipants()
        val existing = current.firstOrNull { it.email.equals(trimmedEmail, ignoreCase = true) }
        val participant = if (existing != null) {
            existing.copy(
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dob,
                phone = phone,
                email = trimmedEmail
            )
        } else {
            Participant(UUID.randomUUID().toString(), firstName, lastName, dob, phone, trimmedEmail)
        }
        val updated = current.filterNot { it.id == participant.id } + participant
        repo.saveAllParticipants(updated)
        return participant
    }
    fun reload(): List<Participant> {
        val store = repo as? core.repo.file.JsonFileStore
        return store?.reloadParticipants() ?: repo.allParticipants()
    }

}
