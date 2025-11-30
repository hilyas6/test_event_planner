package algo

import core.model.{Event, Participant, Registration, Venue}
import java.time.{Duration, LocalDate, LocalTime}
import scala.jdk.CollectionConverters._

object SlotFinder {

  // Number of days in the future to scan when proposing options.
  private val SearchDays = 21
  // Earliest and latest hours within a day when meetings may be scheduled.
  private val DayStart = LocalTime.of(7, 0)
  private val DayEnd = LocalTime.of(23, 0)
  // Default to a 60-minute slot when no preferred duration can be derived.
  private val DefaultDurationMinutes = 60L

  /**
    * Propose the best slots for a meeting considering venue availability and participant calendars.
    *
    * The mechanics mirror the previous implementation: convert incoming Java collections to Scala,
    * pre-filter venues that can hold the expected audience, and iterate over a fixed search window
    * to build high-confidence slot suggestions. Additional comments clarify each step without
    * altering behaviour.
    */
  def proposeSlots(events: java.util.List[Event],
                   venues: java.util.List[Venue],
                   registrations: java.util.List[Registration],
                   participants: java.util.List[Participant],
                   plannedSize: Int,
                   earliestDate: LocalDate,
                   preferredStart: LocalTime,
                   preferredEnd: LocalTime): java.util.List[SlotSuggestion] = {

    // Work relative to “now” so we avoid recommending times that already passed today.
    val now = java.time.LocalDateTime.now()
    val today = now.toLocalDate
    val startDate = if (earliestDate.isBefore(today)) today else earliestDate

    // Only keep events that fall on or after our starting date to speed up later lookups.
    val relevantEvents = events.asScala.toList.filter(event => !event.getDate.isBefore(startDate))
    // Only venues with enough capacity remain candidates.
    val suitableVenues = venues.asScala.toList.filter(_.getCapacity >= plannedSize)
    val registrationList = registrations.asScala.toList
    val participantList = participants.asScala.toList

    // If nothing can host the attendees, return early with an empty list.
    if (suitableVenues.isEmpty) return java.util.Collections.emptyList()

    // Derive requested duration; fall back to an hour when the preferred window is invalid.
    val requestedDuration = Duration.between(preferredStart, preferredEnd)
    val durationMinutes = if (requestedDuration.isPositive) requestedDuration.toMinutes else DefaultDurationMinutes

    // Map each (venue, date) pair to the busy intervals already booked there.
    val eventsByVenueDate: Map[(String, LocalDate), List[(LocalTime, LocalTime, Int)]] =
      relevantEvents.flatMap { event =>
        Option(event.getVenueId).map { venueId =>
          ((venueId, event.getDate), (event.getStartTime, event.getEndTime, event.getExpectedSize))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    // Quick lookup for venue capacities while iterating over suggestions.
    val capacityByVenue: Map[String, Int] = suitableVenues.map(venue => venue.getId -> venue.getCapacity).toMap

    // Track when participants are busy so we can exclude conflicting slots.
    val busyByParticipant: Map[String, List[(LocalDate, LocalTime, LocalTime)]] =
      registrationList.flatMap { registration =>
        relevantEvents.find(_.getId == registration.getEventId).map { event =>
          (registration.getParticipantId, (event.getDate, event.getStartTime, event.getEndTime))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    // Simple set of IDs makes later availability checks faster and cleaner.
    val participantIds = participantList.map(_.getId).toSet

    // Collect results as we explore each date and venue combination.
    val suggestions = scala.collection.mutable.ListBuffer.empty[SlotSuggestion]

    // Iterate through every day and every suitable venue in the search window.
    for {
      dayOffset <- 0 until SearchDays
      date = startDate.plusDays(dayOffset.toLong)
      venue <- suitableVenues
    } {
      // Quickly fetch existing bookings for this venue/day.
      val venueBusySlots = eventsByVenueDate((venue.getId, date))

      // Clamp preferred start to the allowed daily window.
      val baselineStart = if (preferredStart.isBefore(DayStart)) DayStart else preferredStart
      // When searching for today, skip times that have already elapsed.
      val presentAdjustedStart =
        if (date.isEqual(today) && baselineStart.isBefore(now.toLocalTime)) {
          now.toLocalTime
        } else baselineStart

      val proposedEndTime = presentAdjustedStart.plusMinutes(durationMinutes)

      // Only consider times that stay inside the permissible day boundary.
      if (!presentAdjustedStart.isBefore(DayStart) && !proposedEndTime.isAfter(DayEnd)) {
        val fitsCapacity = {
          // Sum capacity already consumed by overlapping events in this venue.
          val overlapping = venueBusySlots.filter { case (start, end, _) => overlaps(start, end, presentAdjustedStart, proposedEndTime) }
          val usedCapacity = overlapping.map(_._3).sum
          val capacity = capacityByVenue.getOrElse(venue.getId, 0)
          usedCapacity + plannedSize <= capacity
        }

        if (fitsCapacity) {
          // Count participants whose calendars conflict with this candidate slot.
          val unavailableCount = participantIds.count { participantId =>
            busyByParticipant(participantId).exists { case (dateBusy, start, end) =>
              dateBusy == date && overlaps(start, end, presentAdjustedStart, proposedEndTime)
            }
          }
          val availableCount = participantIds.size - unavailableCount
          // Confidence prioritises slots where most participants are available.
          val confidence = if (participantIds.isEmpty) 1.0 else availableCount.toDouble / participantIds.size.toDouble
          // Capacity fit discourages using venues that are almost full.
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

    // Deterministically order suggestions by date/time and then per venue.
    val sortedSuggestions = suggestions.toList
      .sortBy(suggestion => (suggestion.getDate(), suggestion.getStartTime(), Option(suggestion.getVenueId()).getOrElse("")))

    // Retain only the first (earliest) suggestion per venue, then return the top three overall.
    val earliestByVenue = scala.collection.mutable.LinkedHashMap.empty[String, SlotSuggestion]
    sortedSuggestions.foreach { suggestion =>
      val venueKey = Option(suggestion.getVenueId()).getOrElse("")
      if (!earliestByVenue.contains(venueKey)) {
        earliestByVenue.put(venueKey, suggestion)
      }
    }

    earliestByVenue.values.take(3).toList.asJava
  }

  // Utility to detect overlapping time intervals.
  private def overlaps(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)
}
