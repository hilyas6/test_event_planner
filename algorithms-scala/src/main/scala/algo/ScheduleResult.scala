package algo

import scala.beans.BeanProperty

/** Java/Kotlin-friendly result for scheduling */
final class ScheduleResult(
                            @BeanProperty val eventId: String,
                            @BeanProperty val venueId: String,
                            @BeanProperty val assignedDate: java.time.LocalDate,
                            @BeanProperty val startTime: java.time.LocalTime,
                            @BeanProperty val endTime: java.time.LocalTime,
                            @BeanProperty val confidence: Double,
                            @BeanProperty val note: String,
                            @BeanProperty val scheduled: Boolean
                          ) {
  override def toString: String =
    s"ScheduleResult(eventId=$eventId, venueId=$venueId, date=$assignedDate, start=$startTime, end=$endTime, confidence=$confidence, scheduled=$scheduled, note=$note)"
}
