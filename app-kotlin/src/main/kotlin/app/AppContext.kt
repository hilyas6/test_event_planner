package app

import core.repo.file.JsonFileStore
import core.service.EventService
import core.service.ParticipantService
import core.service.RegistrationService
import core.service.ScheduledEventService
import core.service.VenueService

/**
 * Global application context for shared services and data store.
 */
object AppContext {
    private val store = JsonFileStore()

    val eventService = EventService(store)
    val venueService = VenueService(store)
    val participantService = ParticipantService(store)
    val registrationService = RegistrationService(store, store, store, store, store)
    val scheduledEventService = ScheduledEventService(store, eventService, venueService)
}
