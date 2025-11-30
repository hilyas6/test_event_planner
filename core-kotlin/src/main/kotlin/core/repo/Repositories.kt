package core.repo

import core.model.*

// Repository interfaces define storage operations; implementations can be file-based, in-memory, etc.
interface EventRepository {
    fun allEvents(): List<Event>
    fun eventById(id: String): Event?
    fun saveEvent(event: Event)
    fun saveAllEvents(eventList: List<Event>)
}

interface VenueRepository {
    fun allVenues(): List<Venue>
    fun venueById(id: String): Venue?
    fun saveVenue(venue: Venue)
    fun saveAllVenues(venueList: List<Venue>)
}

interface ParticipantRepository {
    fun allParticipants(): List<Participant>
    fun participantById(id: String): Participant?
    fun saveParticipant(participant: Participant)
    fun saveAllParticipants(participantList: List<Participant>)
}

interface RegistrationRepository {
    fun allRegistrations(): List<Registration>
    fun registrationsFor(eventId: String): List<Registration>
    fun saveRegistration(registration: Registration)
    fun saveAllRegistrations(registrationList: List<Registration>)
}

interface ScheduledEventRepository {
    fun allScheduledEvents(): List<ScheduledEvent>
    fun saveScheduledEvent(scheduledEvent: ScheduledEvent)
    fun deleteScheduledEventByEventId(eventId: String)
    fun saveAllScheduledEvents(scheduledEventList: List<ScheduledEvent>)
}
