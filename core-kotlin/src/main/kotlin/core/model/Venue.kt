package core.model

import kotlinx.serialization.Serializable

@Serializable
data class Venue(
    val id: String,
    val name: String,
    val capacity: Int,
    val city: String
)
