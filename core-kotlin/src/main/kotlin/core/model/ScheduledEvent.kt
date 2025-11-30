package core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Record that an event has been given a concrete date, time, and optional venue.
 * Used by both scheduling and registration flows to coordinate availability.
 */
@Serializable
data class ScheduledEvent(
    val eventId: String,
    val venueId: String?,
    @Contextual val date: LocalDate,
    @Contextual val startTime: LocalTime,
    @Contextual val endTime: LocalTime,
    @Contextual val confirmedAt: LocalDateTime
)
