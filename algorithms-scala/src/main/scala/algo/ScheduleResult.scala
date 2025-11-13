package algo

import scala.beans.BeanProperty

/** Java/Kotlin-friendly result for scheduling */
final class ScheduleResult(
                            @BeanProperty val eventId: String,
                            @BeanProperty val venueId: String
                          ) {
  override def toString: String = s"ScheduleResult(eventId=$eventId, venueId=$venueId)"
}
