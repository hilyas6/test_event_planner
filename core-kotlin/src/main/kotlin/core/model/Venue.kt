package core.model

import kotlinx.serialization.Serializable

/** A physical location with a capacity limit for hosting events. */
@Serializable
data class Venue(
    val id: String,
    val name: String,
    val capacity: Int,
    val city: String
)
