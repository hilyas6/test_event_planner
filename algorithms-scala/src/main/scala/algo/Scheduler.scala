package algo

import core.model.{Event, Venue}
import java.time.LocalTime
import scala.jdk.CollectionConverters._

object Scheduler {

  /**
   * Build a conflict-free schedule (greedy):
   *  - Sort events by (date, startTime)
   *  - For each event, assign the first venue that:
   *      * has capacity >= event.expectedSize
   *      * isn’t already occupied by a previously scheduled event overlapping in time
   * The result is a list of (eventId -> venueId).
   */
  def buildConflictFreeSchedule(events: java.util.List[Event],
                                venues: java.util.List[Venue]): java.util.List[ScheduleResult] = {

    val evs = events.asScala.toList.sortBy(e => (e.getDate, e.getStartTime))
    val vns = venues.asScala.toList

    // Map[venueId -> List[(start, end)] of already assigned intervals that day]
    var assigned: Map[String, List[(java.time.LocalDate, LocalTime, LocalTime)]] = Map.empty
    val results = scala.collection.mutable.ListBuffer.empty[ScheduleResult]

    def overlaps(d: java.time.LocalDate, s1: LocalTime, e1: LocalTime,
                 s2: LocalTime, e2: LocalTime): Boolean =
      !(e1.compareTo(s2) <= 0 || e2.compareTo(s1) <= 0)

    evs.foreach { e =>
      val needed = e.getExpectedSize
      val maybeVenue = vns.find { v =>
        v.getCapacity >= needed && {
          val slots = assigned.getOrElse(v.getId, Nil)
          val ok = slots.forall { case (d, s, en) =>
            if (d != e.getDate) true
            else !overlaps(d, s, en, e.getStartTime, e.getEndTime)
          }
          ok
        }
      }
      maybeVenue.foreach { v =>
        results += new ScheduleResult(e.getId, v.getId)
        val slots = assigned.getOrElse(v.getId, Nil)
        assigned = assigned.updated(v.getId, (e.getDate, e.getStartTime, e.getEndTime) :: slots)
      }
    }

    results.asJava
  }
}
