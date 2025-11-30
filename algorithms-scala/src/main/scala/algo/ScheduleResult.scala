package algo

import scala.beans.BeanProperty

/**
  * Java/Kotlin-friendly result for scheduling.
  *
  * Each property is exposed via standard getters so the Kotlin UI can easily
  * render scheduled outcomes produced by the Scala algorithms.
  */
final class ScheduleResult(
                            // Identifier of the original event request.
                            @BeanProperty val eventId: String,
                            // Venue chosen for the scheduled event (may be blank when unscheduled).
                            @BeanProperty val venueId: String,
                            // Date assigned to the event (may differ from the request after optimisation).
                            @BeanProperty val assignedDate: java.time.LocalDate,
                            // Scheduled start time.
                            @BeanProperty val startTime: java.time.LocalTime,
                            // Scheduled end time.
                            @BeanProperty val endTime: java.time.LocalTime,
                            // Confidence score summarising feasibility.
                            @BeanProperty val confidence: Double,
                            // Notes that explain why a placement was chosen or why it failed.
                            @BeanProperty val note: String,
                            // Flag indicating whether the event was successfully scheduled.
                            @BeanProperty val scheduled: Boolean
                          ) {
  override def toString: String =
    s"ScheduleResult(eventId=$eventId, venueId=$venueId, date=$assignedDate, start=$startTime, end=$endTime, confidence=$confidence, scheduled=$scheduled, note=$note)"
}
