package algo

import java.time.{LocalDate, LocalTime}
import scala.beans.BeanProperty

/** Java/Kotlin-friendly result for slot finding */
final class SlotSuggestion(
                            @BeanProperty val venueId: String,
                            @BeanProperty val date: LocalDate,
                            @BeanProperty val startTime: LocalTime
                          ) {
  override def toString: String = s"SlotSuggestion(venueId=$venueId, date=$date, startTime=$startTime)"
}
