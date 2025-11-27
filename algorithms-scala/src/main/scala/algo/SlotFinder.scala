package algo

import core.model.{Event, Participant, Registration, Venue}
import java.time.{Duration, LocalDate, LocalTime}
import scala.jdk.CollectionConverters._

object SlotFinder {

  private val SearchDays = 21
  private val DayStart = LocalTime.of(7, 0)
  private val DayEnd = LocalTime.of(23, 0)
  private val DefaultDurationMinutes = 60L

  /**
    * Propose the best slots for a meeting considering venue availability and participant calendars.
    */
  def proposeSlots(events: java.util.List[Event],
                   venues: java.util.List[Venue],
                   registrations: java.util.List[Registration],
                   participants: java.util.List[Participant],
                   plannedSize: Int,
                   earliestDate: LocalDate,
                   preferredStart: LocalTime,
                   preferredEnd: LocalTime): java.util.List[SlotSuggestion] = {

    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate
    val startDate = if (earliestDate.isBefore(today)) today else earliestDate

    val evs = events.asScala.toList.filter(e => !e.getDate.isBefore(startDate))
    val vns = venues.asScala.toList.filter(_.getCapacity >= plannedSize)
    val regs = registrations.asScala.toList
    val parts = participants.asScala.toList

    if (vns.isEmpty) return java.util.Collections.emptyList()

    val requestedDuration = Duration.between(preferredStart, preferredEnd)
    val durationMinutes = if (requestedDuration.isPositive) requestedDuration.toMinutes else DefaultDurationMinutes

    val eventsByVenueDate: Map[(String, LocalDate), List[(LocalTime, LocalTime, Int)]] =
      evs.flatMap { event =>
        Option(event.getVenueId).map { vid =>
          ((vid, event.getDate), (event.getStartTime, event.getEndTime, event.getExpectedSize))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val capacityByVenue: Map[String, Int] = vns.map(v => v.getId -> v.getCapacity).toMap

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
      date = startDate.plusDays(dayOffset.toLong)
      venue <- vns
    } {
      val venueBusy = eventsByVenueDate((venue.getId, date))

      val baselineStart = if (preferredStart.isBefore(DayStart)) DayStart else preferredStart
      val presentAdjustedStart =
        if (date.isEqual(today) && baselineStart.isBefore(now.toLocalTime)) {
          if (baselineStart.isAfter(now.toLocalTime)) baselineStart else now.toLocalTime
        } else baselineStart

      val end = presentAdjustedStart.plusMinutes(durationMinutes)

      if (!presentAdjustedStart.isBefore(DayStart) && !end.isAfter(DayEnd)) {
        val withinCapacity = {
          val overlapping = venueBusy.filter { case (s, e, _) => overlaps(s, e, presentAdjustedStart, end) }
          val usedCapacity = overlapping.map(_._3).sum
          val capacity = capacityByVenue.getOrElse(venue.getId, 0)
          usedCapacity + plannedSize <= capacity
        }

        if (withinCapacity) {
          val unavailable = participantIds.count { pid =>
            busyByParticipant(pid).exists { case (d, s, e) =>
              d == date && overlaps(s, e, presentAdjustedStart, end)
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
          suggestions += new SlotSuggestion(venue.getId, date, presentAdjustedStart, end, compositeScore, note)
        }
      }
    }

    val sorted = suggestions.toList
      .sortBy(s => (s.getDate(), s.getStartTime(), Option(s.getVenueId()).getOrElse("")))

    val earliestByVenue = scala.collection.mutable.LinkedHashMap.empty[String, SlotSuggestion]
    sorted.foreach { s =>
      val venueKey = Option(s.getVenueId()).getOrElse("")
      if (!earliestByVenue.contains(venueKey)) {
        earliestByVenue.put(venueKey, s)
      }
    }

    earliestByVenue.values.take(3).toList.asJava
  }

  private def overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
}
