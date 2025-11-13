package core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDate

@Serializable
data class Participant(
    val id: String,
    val firstName: String,
    val lastName: String,
    @Contextual val dateOfBirth: LocalDate,
    val phone: String,
    val email: String
)
