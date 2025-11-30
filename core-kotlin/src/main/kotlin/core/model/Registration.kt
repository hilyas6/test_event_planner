package core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDateTime

/** Links a participant to a scheduled event at the time they registered. */
@Serializable
data class Registration(
    val id: String,
    val eventId: String,
    val participantId: String,
    @Contextual val registeredAt: LocalDateTime
)
