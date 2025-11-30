package algo

import core.model.{Event, Registration, Venue}
import java.time.{LocalDate, LocalTime}
import scala.jdk.CollectionConverters._

object Scheduler {

  // Allowed scheduling window for a single day.
  private val DayStart: LocalTime = LocalTime.of(7, 0)
  private val DayEnd: LocalTime = LocalTime.of(23, 0)
  // Number of days to look ahead when trying to place events.
  private val SearchWindowDays: Int = 60

  /** Build an availability-aware schedule that honours preferences and priorities. */
  def buildOptimizedSchedule(events: java.util.List[Event],
                             venues: java.util.List[Venue],
                             registrations: java.util.List[Registration],
                             preferenceScores: java.util.Map[String, java.lang.Double]): java.util.List[ScheduleResult] = {

    // Convert Java collections into Scala collections for easier manipulation.
    val eventList = events.asScala.toList
    val venueList = venues.asScala.toList
    val registrationList = registrations.asScala.toList
    // Preference scores are optional; default missing entries to zero.
    val preferenceScoreByEvent = preferenceScores.asScala.view.mapValues(_.doubleValue()).toMap.withDefaultValue(0.0)

    // Pre-compute lookup tables to cut down on repeated iteration.
    val eventById = eventList.map(event => event.getId -> event).toMap

    val participantsByEvent: Map[String, Set[String]] =
      registrationList.groupBy(_.getEventId).view.mapValues(_.map(_.getParticipantId).toSet).toMap.withDefaultValue(Set.empty)

    val busySlotsByParticipant: Map[String, List[(LocalDate, LocalTime, LocalTime, String)]] =
      registrationList.flatMap { registration =>
        eventById.get(registration.getEventId).map { event =>
          (registration.getParticipantId, (event.getDate, event.getStartTime, event.getEndTime, event.getId))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    // Highest-priority events are scheduled first; tie-break by date then time.
    val prioritizedEvents = eventList.sortWith { (left, right) =>
      val leftScore = preferenceScoreByEvent(left.getId)
      val rightScore = preferenceScoreByEvent(right.getId)
      if (leftScore != rightScore) leftScore > rightScore
      else if (left.getDate != right.getDate) left.getDate.isBefore(right.getDate)
      else left.getStartTime.isBefore(right.getStartTime)
    }

    // Mutable trackers maintain which venues and participants are already booked as we go.
    val venueUsageByDay = scala.collection.mutable.Map.empty[(String, LocalDate), List[(LocalTime, LocalTime, Int)]]
    val participantUsage = scala.collection.mutable.Map.empty[String, List[(LocalDate, LocalTime, LocalTime, String)]]
    busySlotsByParticipant.foreach { case (participantId, slots) => participantUsage.update(participantId, slots) }

    val scheduleResults = scala.collection.mutable.ListBuffer.empty[ScheduleResult]

    // Try to place each event, respecting its length and prioritisation.
    prioritizedEvents.foreach { event =>
      val interestedParticipants = participantsByEvent(event.getId)
      val durationMinutes = math.max(15L, java.time.Duration.between(event.getStartTime, event.getEndTime).toMinutes)
      val requestedStart = event.getStartTime
      val requestedEnd = requestedStart.plusMinutes(durationMinutes)
      val today = LocalDate.now()
      val baselineDate = if (event.getDate.isAfter(today)) event.getDate else today
      val candidateVenues = preferredVenues(venueList, event)

      if (!slotWithinDay(requestedStart, durationMinutes)) {
        scheduleResults += new ScheduleResult(event.getId, "", event.getDate, requestedStart, requestedEnd, 0.0, "Requested time outside scheduling hours", false)
      } else {
        // Explore each day in the search window until we find a feasible placement.
        val searchDates = (0 until SearchWindowDays).map(offset => baselineDate.plusDays(offset.toLong))

        // Collect the first viable assignment across venues and dates.
        val assignmentOpt = candidateVenues.to(LazyList).flatMap { venue =>
          searchDates.to(LazyList).collectFirst {
            case date if venueAvailable(venueUsageByDay.getOrElse((venue.getId, date), Nil), requestedStart, durationMinutes, event.getExpectedSize, venue.getCapacity) &&
              participantsAvailable(pid => participantUsage.getOrElse(pid, Nil), interestedParticipants, date, requestedStart, durationMinutes) =>

              val conflicts = countConflicts(pid => participantUsage.getOrElse(pid, Nil), interestedParticipants, date, requestedStart, durationMinutes)
              val confidence = computeConfidence(event, venue, interestedParticipants.size, conflicts)
              val note = buildNote(event, date, conflicts)
              (venue, date, requestedStart, requestedEnd, confidence, note)
          }
        }.headOption

        assignmentOpt match {
          case Some((venue, date, start, end, confidence, note)) =>
            // Reserve the venue and participant calendars now that we have a placement.
            scheduleResults += new ScheduleResult(event.getId, venue.getId, date, start, end, confidence, note, true)
            val dayKey = (venue.getId, date)
            val existingUsage = venueUsageByDay.getOrElse(dayKey, Nil)
            venueUsageByDay.update(dayKey, (start, end, event.getExpectedSize) :: existingUsage)
            interestedParticipants.foreach { participantId =>
              val updated = (date, start, end, event.getId) :: participantUsage.getOrElse(participantId, Nil)
              participantUsage.update(participantId, updated)
            }
          case None =>
            // Provide a clear explanation when no slots can be found.
            val message =
              if (candidateVenues.isEmpty) "No venue meets the capacity requirement"
              else "No available venue found within the search window"
            scheduleResults += new ScheduleResult(event.getId, "", event.getDate, requestedStart, requestedEnd, 0.0, message, false)
        }
      }
    }

    scheduleResults.asJava
  }

  /**
    * Rank venues by how closely they match the event request (preferred first, then least wasted space).
    */
  private def preferredVenues(venues: List[Venue], event: Event): List[Venue] = {
    val capacityNeeded = event.getExpectedSize
    venues.filter(_.getCapacity >= capacityNeeded)
      .sortBy { v =>
        val capacitySlack = v.getCapacity - capacityNeeded
        val matchesPreferred = Option(event.getVenueId).contains(v.getId)
        (if (matchesPreferred) 0 else 1, capacitySlack)
      }
  }

  // Ensure the requested slot stays within the configured daily boundaries.
  private def slotWithinDay(start: LocalTime, durationMinutes: Long): Boolean = {
    val end = start.plusMinutes(durationMinutes)
    !start.isBefore(DayStart) && !end.isAfter(DayEnd)
  }

  // Confirm a venue has enough spare capacity during the proposed interval.
  private def venueAvailable(venueBusy: List[(LocalTime, LocalTime, Int)], start: LocalTime, durationMinutes: Long, expectedSize: Int, venueCapacity: Int): Boolean = {
    val end = start.plusMinutes(durationMinutes)
    val usedCapacity = venueBusy.collect { case (s, e, size) if timesOverlap(s, e, start, end) => size }.sum
    usedCapacity + expectedSize <= venueCapacity
  }

  // Check whether all participants can attend the proposed slot.
  private def participantsAvailable(participantUsage: String => List[(LocalDate, LocalTime, LocalTime, String)],
                                    participants: Set[String],
                                    date: LocalDate,
                                    start: LocalTime,
                                    durationMinutes: Long): Boolean = {
    val end = start.plusMinutes(durationMinutes)
    participants.forall { pid =>
      participantUsage(pid).forall { case (d, s, e, _) =>
        d != date || !timesOverlap(s, e, start, end)
      }
    }
  }

  // Count the number of participants whose calendars would be disrupted by this slot.
  private def countConflicts(participantUsage: String => List[(LocalDate, LocalTime, LocalTime, String)],
                             participants: Set[String],
                             date: LocalDate,
                             start: LocalTime,
                             durationMinutes: Long): Int = {
    val end = start.plusMinutes(durationMinutes)
    participants.count { pid =>
      participantUsage(pid).exists { case (d, s, e, _) =>
        d == date && timesOverlap(s, e, start, end)
      }
    }
  }

  // Combine availability and capacity signals into a single confidence score.
  private def computeConfidence(event: Event,
                                venue: Venue,
                                participantCount: Int,
                                conflicts: Int): Double = {
    val capacityScore = math.min(1.0, event.getExpectedSize.toDouble / math.max(1, venue.getCapacity).toDouble)
    val availabilityScore = if (participantCount == 0) 1.0 else 1.0 - conflicts.toDouble / participantCount.toDouble
    (availabilityScore * 0.7 + (1 - capacityScore) * 0.3).max(0.0)
  }

  // Produce a short note describing any adjustments made to the requested time.
  private def buildNote(event: Event, scheduledDate: LocalDate, conflicts: Int): String = {
    val adjustments = scala.collection.mutable.ArrayBuffer[String]()
    if (scheduledDate != event.getDate) adjustments += s"Moved to $scheduledDate"
    if (conflicts > 0) adjustments += s"$conflicts participant conflict(s) avoided"
    if (adjustments.isEmpty) "Scheduled as requested" else adjustments.mkString("; ")
  }

  // Shared overlap helper reused by availability checks.
  private def timesOverlap(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)

}
