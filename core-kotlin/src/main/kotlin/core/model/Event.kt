package core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDate
import java.time.LocalTime

@Serializable
data class Event(
    val id: String,
    val title: String,
    @Contextual val date: LocalDate,
    @Contextual val startTime: LocalTime,
    @Contextual val endTime: LocalTime,
    val expectedSize: Int,
    val venueId: String
)
