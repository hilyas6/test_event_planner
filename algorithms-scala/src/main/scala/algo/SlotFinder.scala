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

    val relevantEvents = events.asScala.toList.filter(event => !event.getDate.isBefore(startDate))
    val suitableVenues = venues.asScala.toList.filter(_.getCapacity >= plannedSize)
    val registrationList = registrations.asScala.toList
    val participantList = participants.asScala.toList

    if (suitableVenues.isEmpty) return java.util.Collections.emptyList()

    val requestedDuration = Duration.between(preferredStart, preferredEnd)
    val durationMinutes = if (requestedDuration.isPositive) requestedDuration.toMinutes else DefaultDurationMinutes

    val eventsByVenueDate: Map[(String, LocalDate), List[(LocalTime, LocalTime, Int)]] =
      relevantEvents.flatMap { event =>
        Option(event.getVenueId).map { venueId =>
          ((venueId, event.getDate), (event.getStartTime, event.getEndTime, event.getExpectedSize))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val capacityByVenue: Map[String, Int] = suitableVenues.map(venue => venue.getId -> venue.getCapacity).toMap

    val busyByParticipant: Map[String, List[(LocalDate, LocalTime, LocalTime)]] =
      registrationList.flatMap { registration =>
        relevantEvents.find(_.getId == registration.getEventId).map { event =>
          (registration.getParticipantId, (event.getDate, event.getStartTime, event.getEndTime))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val participantIds = participantList.map(_.getId).toSet

    val suggestions = scala.collection.mutable.ListBuffer.empty[SlotSuggestion]

    for {
      dayOffset <- 0 until SearchDays
      date = startDate.plusDays(dayOffset.toLong)
      venue <- suitableVenues
    } {
      val venueBusySlots = eventsByVenueDate((venue.getId, date))

      val baselineStart = if (preferredStart.isBefore(DayStart)) DayStart else preferredStart
      val presentAdjustedStart =
        if (date.isEqual(today) && baselineStart.isBefore(now.toLocalTime)) {
          if (baselineStart.isAfter(now.toLocalTime)) baselineStart else now.toLocalTime
        } else baselineStart

      val proposedEndTime = presentAdjustedStart.plusMinutes(durationMinutes)

      if (!presentAdjustedStart.isBefore(DayStart) && !proposedEndTime.isAfter(DayEnd)) {
        val fitsCapacity = {
          val overlapping = venueBusySlots.filter { case (start, end, _) => overlaps(start, end, presentAdjustedStart, proposedEndTime) }
          val usedCapacity = overlapping.map(_._3).sum
          val capacity = capacityByVenue.getOrElse(venue.getId, 0)
          usedCapacity + plannedSize <= capacity
        }

        if (fitsCapacity) {
          val unavailableCount = participantIds.count { participantId =>
            busyByParticipant(participantId).exists { case (dateBusy, start, end) =>
              dateBusy == date && overlaps(start, end, presentAdjustedStart, proposedEndTime)
            }
          }
          val availableCount = participantIds.size - unavailableCount
          val confidence = if (participantIds.isEmpty) 1.0 else availableCount.toDouble / participantIds.size.toDouble
          val capacityFit = math.min(1.0, plannedSize.toDouble / math.max(1, venue.getCapacity).toDouble)
          val compositeScore = confidence * 0.7 + (1 - capacityFit) * 0.3
          val note = if (unavailableCount == 0) {
            s"All ${participantIds.size} participants are free"
          } else {
            s"$availableCount of ${participantIds.size} participants free"
          }
          suggestions += new SlotSuggestion(venue.getId, date, presentAdjustedStart, proposedEndTime, compositeScore, note)
        }
      }
    }

    val sortedSuggestions = suggestions.toList
      .sortBy(suggestion => (suggestion.getDate(), suggestion.getStartTime(), Option(suggestion.getVenueId()).getOrElse("")))

    val earliestByVenue = scala.collection.mutable.LinkedHashMap.empty[String, SlotSuggestion]
    sortedSuggestions.foreach { suggestion =>
      val venueKey = Option(suggestion.getVenueId()).getOrElse("")
      if (!earliestByVenue.contains(venueKey)) {
        earliestByVenue.put(venueKey, suggestion)
      }
    }

    earliestByVenue.values.take(3).toList.asJava
  }

  private def overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
}
