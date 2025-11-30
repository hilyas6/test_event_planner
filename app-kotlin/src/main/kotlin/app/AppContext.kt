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
    private val dataStore = JsonFileStore()

    val eventService = EventService(dataStore)
    val venueService = VenueService(dataStore)
    val participantService = ParticipantService(dataStore)
    val registrationService = RegistrationService(dataStore, dataStore, dataStore, dataStore, dataStore)
    val scheduledEventService = ScheduledEventService(dataStore, eventService, venueService)
}
