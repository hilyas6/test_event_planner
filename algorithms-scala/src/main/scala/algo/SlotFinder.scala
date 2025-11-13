package algo

import core.model.{Event, Participant, Registration, Venue}
import java.time.{Duration, LocalDate, LocalTime}
import scala.jdk.CollectionConverters._

object SlotFinder {

  private val SearchDays = 21
  private val DayStart = LocalTime.of(8, 0)
  private val DayEnd = LocalTime.of(21, 0)
  private val DefaultDurationMinutes = 60L
  private val StepMinutes = 30L

  /**
    * Propose the best slots for a meeting considering venue availability and participant calendars.
    */
  def proposeSlots(events: java.util.List[Event],
                   venues: java.util.List[Venue],
                   registrations: java.util.List[Registration],
                   participants: java.util.List[Participant],
                   plannedSize: Int,
                   earliestDate: LocalDate): java.util.List[SlotSuggestion] = {

    val evs = events.asScala.toList
    val vns = venues.asScala.toList.filter(_.getCapacity >= plannedSize)
    val regs = registrations.asScala.toList
    val parts = participants.asScala.toList

    if (vns.isEmpty) return java.util.Collections.emptyList()

    val averageDuration = averageEventDuration(evs).getOrElse(Duration.ofMinutes(DefaultDurationMinutes))
    val durationMinutes = averageDuration.toMinutes

    val eventsByVenueDate: Map[(String, LocalDate), List[(LocalTime, LocalTime)]] =
      evs.flatMap { event =>
        Option(event.getVenueId).map { vid =>
          ((vid, event.getDate), (event.getStartTime, event.getEndTime))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val busyByParticipant: Map[String, List[(LocalDate, LocalTime, LocalTime)]] =
      regs.flatMap { reg =>
        evs.find(_.getId == reg.getEventId).map { event =>
          (reg.getParticipantId, (event.getDate, event.getStartTime, event.getEndTime))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val participantIds = parts.map(_.getId).toSet

    val suggestions = scala.collection.mutable.ListBuffer.empty[SlotSuggestion]

    for {
      dayOffset <- 0 until SearchDays
      date = earliestDate.plusDays(dayOffset.toLong)
      venue <- vns
    } {
      val venueBusy = eventsByVenueDate((venue.getId, date))
      val slots = dailySlots(durationMinutes, venueBusy)
      slots.foreach { start =>
        val end = start.plusMinutes(durationMinutes)
        val unavailable = participantIds.count { pid =>
          busyByParticipant(pid).exists { case (d, s, e) =>
            d == date && overlaps(s, e, start, end)
          }
        }
        val available = participantIds.size - unavailable
        val confidence = if (participantIds.isEmpty) 1.0 else available.toDouble / participantIds.size.toDouble
        val capacityFit = math.min(1.0, plannedSize.toDouble / math.max(1, venue.getCapacity).toDouble)
        val compositeScore = confidence * 0.7 + (1 - capacityFit) * 0.3
        val note = if (unavailable == 0) {
          s"All ${participantIds.size} participants are free"
        } else {
          s"$available of ${participantIds.size} participants free"
        }
        suggestions += new SlotSuggestion(venue.getId, date, start, end, compositeScore, note)
      }
    }

    suggestions.toList
      .sortBy(s => (-s.getConfidence(), s.getDate(), s.getStartTime()))
      .take(10)
      .asJava
  }

  private def averageEventDuration(events: List[Event]): Option[Duration] = {
    val durations = events.map { e =>
      Duration.between(e.getStartTime, e.getEndTime).toMinutes
    }.filter(_ > 0)
    if (durations.isEmpty) None
    else Some(Duration.ofMinutes((durations.sum / durations.size.toDouble).round))
  }

  private def dailySlots(durationMinutes: Long,
                         venueBusy: List[(LocalTime, LocalTime)]): List[LocalTime] = {
    val slots = scala.collection.mutable.ListBuffer.empty[LocalTime]
    var current = DayStart
    while (!current.plusMinutes(durationMinutes).isAfter(DayEnd)) {
      val end = current.plusMinutes(durationMinutes)
      val clash = venueBusy.exists { case (s, e) => overlaps(s, e, current, end) }
      if (!clash) slots += current
      current = current.plusMinutes(StepMinutes)
    }
    if (slots.isEmpty) List(DayEnd.minusMinutes(durationMinutes)) else slots.toList
  }

  private def overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
}
