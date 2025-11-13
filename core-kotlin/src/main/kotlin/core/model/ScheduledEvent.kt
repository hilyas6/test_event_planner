package core.model
@kotlinx.serialization.Serializable
data class ScheduledEvent(
    val eventId: String,
    val venueId: String
)