package algo

import java.time.{LocalDate, LocalTime}
import scala.beans.BeanProperty

/**
  * Java/Kotlin-friendly result for slot finding.
  *
  * This class intentionally stays lightweight so it can be consumed across
  * language boundaries without additional adapters. The BeanProperty
  * annotations expose standard getters for Kotlin and Java callers.
  */
final class SlotSuggestion(
                            // Identifier of the venue the suggestion belongs to.
                            @BeanProperty val venueId: String,
                            // Calendar day for the proposed meeting.
                            @BeanProperty val date: LocalDate,
                            // Proposed start time within the day.
                            @BeanProperty val startTime: LocalTime,
                            // Proposed end time computed from the desired duration.
                            @BeanProperty val endTime: LocalTime,
                            // Composite score combining availability and capacity.
                            @BeanProperty val confidence: Double,
                            // Human-readable explanation of why this slot is recommended.
                            @BeanProperty val note: String
                          ) {
  override def toString: String =
    s"SlotSuggestion(venueId=$venueId, date=$date, startTime=$startTime, endTime=$endTime, confidence=$confidence, note=$note)"
}
