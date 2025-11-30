package core.repo.file

import core.model.*
import core.repo.*
import java.nio.file.*
import java.nio.file.StandardOpenOption.*
import java.time.*

import kotlinx.serialization.KSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule


/**
 * Lightweight JSON-backed repository implementation.
 * Data is persisted to the nearest `data/` directory relative to the working directory
 * so both the CLI tools and UI share the same files.
 */
class JsonFileStore :
    EventRepository,
    VenueRepository,
    ParticipantRepository,
    RegistrationRepository,
    ScheduledEventRepository {

    private val dataDirectory = determineDataDirectory()

    /**
     * Walks up the current working directory to find a sibling `data` folder.
     * If none exists, it will create one alongside the current working directory
     * so the app always has a predictable place to read/write JSON files.
     */
    private fun determineDataDirectory(): Path {
        val workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val dataDirectoryCandidate = generateSequence(workingDirectory) { it.parent }
            .map { it.resolve("data") }
            .firstOrNull { Files.isDirectory(it) }

        return (dataDirectoryCandidate ?: workingDirectory.resolve("data")).normalize()
    }

    // Serializer for Java time types so they can be written as ISO-8601 strings.
    private val serializersModule = SerializersModule {
        contextual(LocalDate::class, LocalDateSerializer)
        contextual(LocalTime::class, LocalTimeSerializer)
        contextual(LocalDateTime::class, LocalDateTimeSerializer)
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        serializersModule = this@JsonFileStore.serializersModule
    }

    init {
        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory)
        }
    }

    /**
     * Reads a JSON array from the requested file into a mutable list.
     * Missing files are treated as empty collections to keep the UI responsive
     * even before any data is created.
     */
    private fun <T> load(fileName: String, deserializer: DeserializationStrategy<List<T>>): MutableList<T> {
        val path = dataDirectory.resolve(fileName)
        if (!Files.exists(path)) return mutableListOf()
        val text = Files.readString(path)
        return json.decodeFromString(deserializer, text).toMutableList()
    }

    /**
     * Writes the given list back to disk, replacing the existing file content.
     */
    private fun <T> save(fileName: String, serializer: SerializationStrategy<List<T>>, data: List<T>) {
        val path = dataDirectory.resolve(fileName)
        val text = json.encodeToString(serializer, data)
        Files.writeString(path, text, CREATE, TRUNCATE_EXISTING, WRITE)
    }

    // Events
    private val events = load("events.json", ListSerializer(Event.serializer()))

    override fun allEvents(): List<Event> = events
    override fun eventById(id: String): Event? = events.find { it.id == id }
    override fun saveEvent(event: Event) {
        events.removeIf { it.id == event.id }
        events.add(event)
        save("events.json", ListSerializer(Event.serializer()), events)
    }
    override fun saveAllEvents(eventList: List<Event>) {
        events.clear()
        events.addAll(eventList)
        save("events.json", ListSerializer(Event.serializer()), events)
    }

    // Venues
    private val venues = load("venues.json", ListSerializer(Venue.serializer()))

    override fun allVenues(): List<Venue> = venues
    override fun venueById(id: String): Venue? = venues.find { it.id == id }
    override fun saveVenue(venue: Venue) {
        venues.removeIf { it.id == venue.id }
        venues.add(venue)
        save("venues.json", ListSerializer(Venue.serializer()), venues)
    }
    override fun saveAllVenues(venueList: List<Venue>) {
        venues.clear()
        venues.addAll(venueList)
        save("venues.json", ListSerializer(Venue.serializer()), venues)
    }

    // Participants
    private val participants = load("participants.json", ListSerializer(Participant.serializer()))

    override fun allParticipants(): List<Participant> = participants
    override fun participantById(id: String): Participant? = participants.find { it.id == id }
    override fun saveParticipant(participant: Participant) {
        participants.removeIf { it.id == participant.id }
        participants.add(participant)
        save("participants.json", ListSerializer(Participant.serializer()), participants)
    }
    override fun saveAllParticipants(participantList: List<Participant>) {
        participants.clear()
        participants.addAll(participantList)
        save("participants.json", ListSerializer(Participant.serializer()), participants)
    }

    // Registrations
    private val registrations = load("registrations.json", ListSerializer(Registration.serializer()))

    override fun allRegistrations(): List<Registration> = registrations
    override fun registrationsFor(eventId: String): List<Registration> = registrations.filter { it.eventId == eventId }
    override fun saveRegistration(registration: Registration) {
        registrations.removeIf { it.id == registration.id }
        registrations.add(registration)
        save("registrations.json", ListSerializer(Registration.serializer()), registrations)
    }
    override fun saveAllRegistrations(registrationList: List<Registration>) {
        registrations.clear()
        registrations.addAll(registrationList)
        save("registrations.json", ListSerializer(Registration.serializer()), registrations)
    }

    // Scheduled events
    private val scheduledEvents = load("scheduled-events.json", ListSerializer(ScheduledEvent.serializer()))

    override fun allScheduledEvents(): List<ScheduledEvent> = scheduledEvents

    override fun saveScheduledEvent(scheduledEvent: ScheduledEvent) {
        scheduledEvents.removeIf { it.eventId == scheduledEvent.eventId }
        scheduledEvents.add(scheduledEvent)
        save("scheduled-events.json", ListSerializer(ScheduledEvent.serializer()), scheduledEvents)
    }

    override fun deleteScheduledEventByEventId(eventId: String) {
        val changed = scheduledEvents.removeIf { it.eventId == eventId }
        if (changed) {
            save("scheduled-events.json", ListSerializer(ScheduledEvent.serializer()), scheduledEvents)
        }
    }

    override fun saveAllScheduledEvents(scheduledEventList: List<ScheduledEvent>) {
        scheduledEvents.clear()
        scheduledEvents.addAll(scheduledEventList)
        save("scheduled-events.json", ListSerializer(ScheduledEvent.serializer()), scheduledEvents)
    }
    // --- Reload helpers ---

    fun reloadEvents(): List<Event> {
        val fresh = load("events.json", kotlinx.serialization.builtins.ListSerializer(Event.serializer()))
        events.clear()
        events.addAll(fresh)
        return events
    }

    fun reloadVenues(): List<Venue> {
        val fresh = load("venues.json", kotlinx.serialization.builtins.ListSerializer(Venue.serializer()))
        venues.clear()
        venues.addAll(fresh)
        return venues
    }
    fun reloadParticipants(): List<core.model.Participant> {
        val fresh = load("participants.json", kotlinx.serialization.builtins.ListSerializer(core.model.Participant.serializer()))
        participants.clear(); participants.addAll(fresh)
        return participants
    }

    fun reloadRegistrations(): List<core.model.Registration> {
        val fresh = load("registrations.json", kotlinx.serialization.builtins.ListSerializer(core.model.Registration.serializer()))
        registrations.clear(); registrations.addAll(fresh)
        return registrations
    }

    fun reloadScheduledEvents(): List<ScheduledEvent> {
        val fresh = load("scheduled-events.json", kotlinx.serialization.builtins.ListSerializer(ScheduledEvent.serializer()))
        scheduledEvents.clear(); scheduledEvents.addAll(fresh)
        return scheduledEvents
    }

}

/** Serializers for Java Time classes (ISO-8601) */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
