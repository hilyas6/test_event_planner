package core.service

import core.model.Venue
import core.repo.VenueRepository
import java.util.*

class VenueService(private val repo: VenueRepository) {

    fun all(): List<Venue> = repo.allVenues()

    fun addVenue(name: String, capacity: Int, city: String) {
        val venue = Venue(UUID.randomUUID().toString(), name, capacity, city)
        repo.saveVenue(venue)
    }

    // ✅ NEW: delete venue safely
    fun deleteVenueById(id: String) {
        val updated = all().filterNot { it.id == id }
        repo.saveAllVenues(updated)
    }
    fun reload(): List<Venue> {
        val store = repo as? core.repo.file.JsonFileStore
        return store?.reloadVenues() ?: repo.allVenues()
    }

}
