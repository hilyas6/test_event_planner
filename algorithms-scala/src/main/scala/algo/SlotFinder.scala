package algo

import core.model.{Event, Venue}
import java.time.{LocalDate, LocalTime}
import scala.jdk.CollectionConverters._

object SlotFinder {

  /**
   * Find the first available venue (capacity >= plannedSize) starting at or after earliestDate.
   * Heuristic:
   *  - For each candidate venue with enough capacity:
   *    - Take all events at that venue from earliestDate onward
   *    - Suggest the next start time immediately after the latest same-day end time,
   *      or 09:00 if no events that day; if earliestDate has any event ending after 21:00,
   *      move to the next day 09:00
   *  - Return the venue with the earliest (date, time)
   */
  def findFirstAvailable(events: java.util.List[Event],
                         venues: java.util.List[Venue],
                         plannedSize: Int,
                         earliestDate: LocalDate): java.util.Optional[SlotSuggestion] = {

    val evs = events.asScala.toList
    val vns = venues.asScala.toList

    val dayStart = LocalTime.of(9, 0)
    val dayEnd   = LocalTime.of(21, 0)

    val candidates = vns.filter(v => v.getCapacity >= plannedSize)

    val suggestions = candidates.flatMap { v =>
      val relevant = evs.filter(e =>
        e.getVenueId == v.getId && !e.getDate.isBefore(earliestDate)
      ).sortBy(e => (e.getDate, e.getStartTime))

      // Compute the suggested (date, time)
      val (date, time) =
        if (relevant.isEmpty) (earliestDate, dayStart)
        else {
          val sameDay = relevant.filter(_.getDate == earliestDate)
          if (sameDay.isEmpty) {
            // no events that day -> start at 09:00
            (earliestDate, dayStart)
          } else {
            val latestEnd = sameDay.map(_.getEndTime).max
            val t = latestEnd.plusMinutes(0)
            if (t.isAfter(dayEnd)) (earliestDate.plusDays(1), dayStart)
            else (earliestDate, if (t.isBefore(dayStart)) dayStart else t)
          }
        }

      Some(new SlotSuggestion(v.getId, date, time))
    }

    // Choose the earliest by date then time
    suggestions.sortBy(s => (s.date, s.startTime)).headOption match {
      case Some(s) => java.util.Optional.of(s)
      case None    => java.util.Optional.empty()
    }
  }
}
