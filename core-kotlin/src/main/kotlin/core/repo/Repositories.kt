package core.repo

import core.model.*

interface EventRepository {
    fun allEvents(): List<Event>
    fun eventById(id: String): Event?
    fun saveEvent(event: Event)
    fun saveAllEvents(events: List<Event>)
}

interface VenueRepository {
    fun allVenues(): List<Venue>
    fun venueById(id: String): Venue?
    fun saveVenue(venue: Venue)
    fun saveAllVenues(venues: List<Venue>)
}

interface ParticipantRepository {
    fun allParticipants(): List<Participant>
    fun participantById(id: String): Participant?
    fun saveParticipant(participant: Participant)
    fun saveAllParticipants(participants: List<Participant>)
}

interface RegistrationRepository {
    fun allRegistrations(): List<Registration>
    fun registrationsFor(eventId: String): List<Registration>
    fun saveRegistration(registration: Registration)
    fun saveAllRegistrations(registrations: List<Registration>)
}
