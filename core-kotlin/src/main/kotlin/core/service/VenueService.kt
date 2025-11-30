package core.service

import core.model.Venue
import core.repo.VenueRepository
import java.util.*

class VenueService(private val venueRepository: VenueRepository) {

    fun all(): List<Venue> = venueRepository.allVenues()

    fun addVenue(name: String, capacity: Int, city: String) {
        val venue = Venue(UUID.randomUUID().toString(), name, capacity, city)
        venueRepository.saveVenue(venue)
    }

    // delete venue safely
    fun deleteVenueById(id: String) {
        val remainingVenues = all().filterNot { it.id == id }
        venueRepository.saveAllVenues(remainingVenues)
    }
    fun reload(): List<Venue> {
        val jsonStore = venueRepository as? core.repo.file.JsonFileStore
        return jsonStore?.reloadVenues() ?: venueRepository.allVenues()
    }

}
