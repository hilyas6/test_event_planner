package app.ui

import app.AppContext
import java.time.format.DateTimeFormatter

/**
 * Provides a minimal console fallback so the application is still useful when running in a
 * headless environment (such as CI or remote terminals without an X server).
 */
object HeadlessFallback {

    private val eventService = AppContext.eventService
    private val venueService = AppContext.venueService
    private val participantService = AppContext.participantService
    private val registrationService = AppContext.registrationService
    private val scheduledEventService = AppContext.scheduledEventService

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun run() {
        println("⚠️  Detected a headless environment – launching console summary mode.")
        println()

        printVenues()
        printEvents()
        printSchedule()
        printRegistrations()

        println()
        println("Tip: Run this application from a desktop environment to launch the full Swing UI.")
    }

    private fun printVenues() {
        val venues = venueService.all()
        println("Venues (${venues.size}):")
        if (venues.isEmpty()) {
            println("  (none configured yet)")
        } else {
            venues.forEach { venue ->
                println("  • ${venue.name} — capacity ${venue.capacity} in ${venue.city}")
            }
        }
        println()
    }

    private fun printEvents() {
        val events = eventService.all()
        val venuesById = venueService.all().associateBy { it.id }
        println("Events (${events.size}):")
        if (events.isEmpty()) {
            println("  (none created yet)")
        } else {
            events.forEach { event ->
                val date = dateFormatter.format(event.date)
                val start = timeFormatter.format(event.startTime)
                val end = timeFormatter.format(event.endTime)
                val capacity = registrationService.capacityLimit(event)
                val remaining = registrationService.remainingCapacity(event)
                val venueName = event.venueId?.let { id -> venuesById[id]?.name }
                val venueDetails = venueName?.let { " @ $it" } ?: ""
                println("  • ${event.title}$venueDetails ($date $start–$end) — remaining capacity $remaining/$capacity")
                println("      ${event.description}")
            }
        }
        println()
    }

    private fun printSchedule() {
        val scheduled = scheduledEventService.all()
        val venuesById = venueService.all().associateBy { it.id }
        println("Confirmed schedules (${scheduled.size}):")
        if (scheduled.isEmpty()) {
            println("  (no confirmed schedules)")
        } else {
            val eventsById = eventService.all().associateBy { it.id }
            scheduled.forEach { record ->
                val event = eventsById[record.eventId]
                val title = event?.title ?: "Event ${record.eventId}"
                val date = dateFormatter.format(record.date)
                val start = timeFormatter.format(record.startTime)
                val end = timeFormatter.format(record.endTime)
                val venueName = record.venueId?.let { id -> venuesById[id]?.name }
                val venueDetails = venueName?.let { " @ $it" } ?: ""
                println("  • $title$venueDetails on $date $start–$end (confirmed ${record.confirmedAt})")
            }
        }
        println()
    }

    private fun printRegistrations() {
        val registrations = registrationService.all()
        println("Registrations (${registrations.size}):")
        if (registrations.isEmpty()) {
            println("  (no registrations yet)")
            return
        }

        val eventsById = eventService.all().associateBy { it.id }
        val participantsById = participantService.all().associateBy { it.id }

        registrations.forEach { registration ->
            val eventTitle = eventsById[registration.eventId]?.title ?: registration.eventId
            val participant = participantsById[registration.participantId]
            val participantName = participant?.let { "${it.firstName} ${it.lastName}" } ?: registration.participantId
            println("  • $participantName for $eventTitle (registered ${registration.registeredAt})")
        }
    }
}

