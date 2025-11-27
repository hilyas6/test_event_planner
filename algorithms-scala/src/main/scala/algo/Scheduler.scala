package algo

import core.model.{Event, Registration, Venue}
import java.time.{LocalDate, LocalTime}
import scala.collection.immutable.LazyList
import scala.jdk.CollectionConverters._

object Scheduler {

  private val DayStart: LocalTime = LocalTime.of(7, 0)
  private val DayEnd: LocalTime = LocalTime.of(23, 0)
  private val SearchWindowDays: Int = 60

  /** Build an availability-aware schedule that honours preferences and priorities. */
  def buildOptimizedSchedule(events: java.util.List[Event],
                             venues: java.util.List[Venue],
                             registrations: java.util.List[Registration],
                             preferenceScores: java.util.Map[String, java.lang.Double]): java.util.List[ScheduleResult] = {

    val evs = events.asScala.toList
    val vns = venues.asScala.toList
    val regs = registrations.asScala.toList
    val prefs = preferenceScores.asScala.view.mapValues(_.doubleValue()).toMap.withDefaultValue(0.0)

    val eventById = evs.map(e => e.getId -> e).toMap

    val participantsByEvent: Map[String, Set[String]] =
      regs.groupBy(_.getEventId).view.mapValues(_.map(_.getParticipantId).toSet).toMap.withDefaultValue(Set.empty)

    val busyByParticipant: Map[String, List[(LocalDate, LocalTime, LocalTime, String)]] =
      regs.flatMap { reg =>
        eventById.get(reg.getEventId).map { event =>
          (reg.getParticipantId, (event.getDate, event.getStartTime, event.getEndTime, event.getId))
        }
      }.groupBy(_._1).view.mapValues(_.map(_._2)).toMap.withDefaultValue(Nil)

    val sortedEvents = evs.sortWith { (a, b) =>
      val scoreA = prefs(a.getId)
      val scoreB = prefs(b.getId)
      if (scoreA != scoreB) scoreA > scoreB
      else if (a.getDate != b.getDate) a.getDate.isBefore(b.getDate)
      else a.getStartTime.isBefore(b.getStartTime)
    }

    val venueUsage = scala.collection.mutable.Map.empty[(String, LocalDate), List[(LocalTime, LocalTime, Int)]]
    val participantUsage = scala.collection.mutable.Map.empty[String, List[(LocalDate, LocalTime, LocalTime, String)]]
    busyByParticipant.foreach { case (pid, slots) => participantUsage.update(pid, slots) }

    val results = scala.collection.mutable.ListBuffer.empty[ScheduleResult]

    sortedEvents.foreach { event =>
      val interestedParticipants = participantsByEvent(event.getId)
      val durationMinutes = math.max(15L, java.time.Duration.between(event.getStartTime, event.getEndTime).toMinutes)
      val requestedStart = event.getStartTime
      val requestedEnd = requestedStart.plusMinutes(durationMinutes)
      val today = LocalDate.now()
      val baselineDate = if (event.getDate.isAfter(today)) event.getDate else today
      val candidateVenues = preferredVenues(vns, event)

      if (!slotWithinDay(requestedStart, durationMinutes)) {
        results += new ScheduleResult(event.getId, "", event.getDate, requestedStart, requestedEnd, 0.0, "Requested time outside scheduling hours", false)
      } else {
        val searchDates = (0 until SearchWindowDays).map(offset => baselineDate.plusDays(offset.toLong))

        val assignmentOpt = candidateVenues.to(LazyList).flatMap { venue =>
          searchDates.to(LazyList).collectFirst {
            case date if venueAvailable(venueUsage.getOrElse((venue.getId, date), Nil), requestedStart, durationMinutes, event.getExpectedSize, venue.getCapacity) &&
              participantsAvailable(pid => participantUsage.getOrElse(pid, Nil), interestedParticipants, date, requestedStart, durationMinutes) =>

              val conflicts = countConflicts(pid => participantUsage.getOrElse(pid, Nil), interestedParticipants, date, requestedStart, durationMinutes)
              val confidence = computeConfidence(event, venue, interestedParticipants.size, conflicts)
              val note = buildNote(event, date, conflicts)
              (venue, date, requestedStart, requestedEnd, confidence, note)
          }
        }.headOption

        assignmentOpt match {
          case Some((venue, date, start, end, confidence, note)) =>
            results += new ScheduleResult(event.getId, venue.getId, date, start, end, confidence, note, true)
            val dayKey = (venue.getId, date)
            val existing = venueUsage.getOrElse(dayKey, Nil)
            venueUsage.update(dayKey, (start, end, event.getExpectedSize) :: existing)
            interestedParticipants.foreach { pid =>
              val updated = (date, start, end, event.getId) :: participantUsage.getOrElse(pid, Nil)
              participantUsage.update(pid, updated)
            }
          case None =>
            val message =
              if (candidateVenues.isEmpty) "No venue meets the capacity requirement"
              else "No available venue found within the search window"
            results += new ScheduleResult(event.getId, "", event.getDate, requestedStart, requestedEnd, 0.0, message, false)
        }
      }
    }

    results.asJava
  }

  private def preferredVenues(venues: List[Venue], event: Event): List[Venue] = {
    val capacityNeeded = event.getExpectedSize
    val sorted = venues.filter(_.getCapacity >= capacityNeeded)
      .sortBy { v =>
        val capacitySlack = v.getCapacity - capacityNeeded
        val matchesPreferred = Option(event.getVenueId).contains(v.getId)
        (if (matchesPreferred) 0 else 1, capacitySlack)
      }
    sorted
  }

  private def slotWithinDay(start: LocalTime, durationMinutes: Long): Boolean = {
    val end = start.plusMinutes(durationMinutes)
    !start.isBefore(DayStart) && !end.isAfter(DayEnd)
  }

  private def venueAvailable(venueBusy: List[(LocalTime, LocalTime, Int)], start: LocalTime, durationMinutes: Long, expectedSize: Int, venueCapacity: Int): Boolean = {
    val end = start.plusMinutes(durationMinutes)
    val usedCapacity = venueBusy.collect { case (s, e, size) if timesOverlap(s, e, start, end) => size }.sum
    usedCapacity + expectedSize <= venueCapacity
  }

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

  private def computeConfidence(event: Event,
                                venue: Venue,
                                participantCount: Int,
                                conflicts: Int): Double = {
    val capacityScore = math.min(1.0, event.getExpectedSize.toDouble / math.max(1, venue.getCapacity).toDouble)
    val availabilityScore = if (participantCount == 0) 1.0 else 1.0 - conflicts.toDouble / participantCount.toDouble
    (availabilityScore * 0.7 + (1 - capacityScore) * 0.3).max(0.0)
  }

  private def buildNote(event: Event, scheduledDate: LocalDate, conflicts: Int): String = {
    val adjustments = scala.collection.mutable.ArrayBuffer[String]()
    if (scheduledDate != event.getDate) adjustments += s"Moved to $scheduledDate"
    if (conflicts > 0) adjustments += s"$conflicts participant conflict(s) avoided"
    if (adjustments.isEmpty) "Scheduled as requested" else adjustments.mkString("; ")
  }

  private def timesOverlap(aStart: LocalTime, aEnd: LocalTime, bStart: LocalTime, bEnd: LocalTime): Boolean =
    aStart.isBefore(bEnd) && bStart.isBefore(aEnd)

}
