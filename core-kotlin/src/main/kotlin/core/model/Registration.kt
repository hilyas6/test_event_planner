package core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDateTime

@Serializable
data class Registration(
    val id: String,
    val eventId: String,
    val participantId: String,
    @Contextual val registeredAt: LocalDateTime
)
