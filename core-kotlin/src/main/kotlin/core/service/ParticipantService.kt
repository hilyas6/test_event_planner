package core.service

import core.model.Participant
import core.repo.ParticipantRepository
import java.time.LocalDate
import java.util.*

class ParticipantService(private val repo: ParticipantRepository) {
    fun all(): List<Participant> = repo.allParticipants()

    fun addParticipant(firstName: String, lastName: String, dob: LocalDate, phone: String, email: String) {
        val participant = Participant(UUID.randomUUID().toString(), firstName, lastName, dob, phone, email)
        repo.saveParticipant(participant)
    }
    fun deleteParticipantById(id: String) {
        val updated = all().filterNot { it.id == id }
        repo.saveAllParticipants(updated)
    }
    fun reload(): List<Participant> {
        val store = repo as? core.repo.file.JsonFileStore
        return store?.reloadParticipants() ?: repo.allParticipants()
    }

}
