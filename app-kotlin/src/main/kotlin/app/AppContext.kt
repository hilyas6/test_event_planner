package app

import core.repo.file.JsonFileStore
import core.service.EventService
import core.service.RegistrationService
import core.service.VenueService
import core.service.ParticipantService

/**
 * Global application context for shared services and data store.
 */
object AppContext {
    private val store = JsonFileStore()

    val eventService = EventService(store)
    val venueService = VenueService(store)
    val participantService = ParticipantService(store)
    val registrationService = RegistrationService(store, store, store, store)
}
