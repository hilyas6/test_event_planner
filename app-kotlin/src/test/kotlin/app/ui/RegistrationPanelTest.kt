package app.ui

import app.AppContext
import core.model.Registration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegistrationPanelTest {
    private lateinit var tempDir: Path
    private lateinit var originalUserDir: String
    private var originalHeadless: String? = null

    @BeforeAll
    fun setUp() {
        originalUserDir = System.getProperty("user.dir")
        tempDir = Files.createTempDirectory("event-planner-test")
        copyDirectory(Path.of(originalUserDir, "data"), tempDir.resolve("data"))
        System.setProperty("user.dir", tempDir.toString())
        originalHeadless = System.getProperty("java.awt.headless")
        System.setProperty("java.awt.headless", "true")
    }

    @AfterAll
    fun tearDown() {
        System.setProperty("user.dir", originalUserDir)
        originalHeadless?.let { System.setProperty("java.awt.headless", it) }
            ?: System.clearProperty("java.awt.headless")
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `register button registers participant for selected schedule`() {
        val panel = buildPanel()
        val eventDropdown = getPrivateField(panel, "eventDropdown") as JComboBox<*>
        val participantDropdown = getPrivateField(panel, "participantDropdown") as JComboBox<*>
        val registerButton = getPrivateField(panel, "registerButton") as JButton

        // Ensure there is at least one event available
        assertTrue(eventDropdown.itemCount > 0, "Expected confirmed events in dropdown")

        // Pick the first event with capacity remaining and choose an unregistered participant
        val eventIndex = findEventIndexWithCapacity(eventDropdown)
        assertTrue(eventIndex >= 0, "No event with capacity remaining found")

        val eventOption = eventDropdown.getItemAt(eventIndex)
        val eventId = extractEventId(eventOption)
        val registeredParticipantIds = AppContext.registrationService.registrationsFor(eventId)
            .map(Registration::participantId)
            .toSet()

        val participantIndex = findAvailableParticipant(participantDropdown, registeredParticipantIds)
        assertTrue(participantIndex >= 0, "No available participant found for event $eventId")

        val participantId = extractParticipantId(participantDropdown.getItemAt(participantIndex))

        SwingUtilities.invokeAndWait {
            eventDropdown.selectedIndex = eventIndex
            participantDropdown.selectedIndex = participantIndex
        }

        SwingUtilities.invokeAndWait {
            assertTrue(registerButton.isEnabled, "Register button should be enabled before clicking")
            registerButton.doClick()
        }

        val updatedRegistrations = AppContext.registrationService.reload()
        val registered = updatedRegistrations.any { it.eventId == eventId && it.participantId == participantId }
        assertTrue(registered, "Participant $participantId should be registered for event $eventId")
    }

    private fun buildPanel(): RegistrationPanel {
        var panel: RegistrationPanel? = null
        SwingUtilities.invokeAndWait {
            panel = RegistrationPanel()
        }
        return requireNotNull(panel)
    }

    private fun findEventIndexWithCapacity(eventDropdown: JComboBox<*>): Int {
        for (i in 0 until eventDropdown.itemCount) {
            val option = eventDropdown.getItemAt(i)
            val remaining = extractIntField(option, "remaining")
            val hasStarted = extractBooleanField(option, "hasStarted")
            if (remaining > 0 && !hasStarted) {
                return i
            }
        }
        return -1
    }

    private fun findAvailableParticipant(dropdown: JComboBox<*>, takenIds: Set<String>): Int {
        for (i in 0 until dropdown.itemCount) {
            val participantId = extractParticipantId(dropdown.getItemAt(i))
            if (!takenIds.contains(participantId)) {
                return i
            }
        }
        return -1
    }

    private fun extractEventId(option: Any): String {
        val eventField = option.javaClass.getDeclaredField("event").apply { isAccessible = true }
        val event = eventField.get(option)
        val idField = event.javaClass.getDeclaredField("id").apply { isAccessible = true }
        return idField.get(event) as String
    }

    private fun extractParticipantId(option: Any): String {
        val participantField = option.javaClass.getDeclaredField("participant").apply { isAccessible = true }
        val participant = participantField.get(option)
        val idField = participant.javaClass.getDeclaredField("id").apply { isAccessible = true }
        return idField.get(participant) as String
    }

    private fun extractIntField(option: Any, fieldName: String): Int {
        val field = option.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(option) as Int
    }

    private fun extractBooleanField(option: Any, fieldName: String): Boolean {
        val field = option.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
        return field.get(option) as Boolean
    }

    private fun getPrivateField(instance: Any, name: String): Any {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(instance)
    }

    private fun copyDirectory(source: Path, target: Path) {
        require(source.exists() && source.isDirectory()) { "Source directory $source must exist" }
        Files.walk(source).use { stream ->
            stream.forEach { path ->
                val relative = source.relativize(path)
                val destination = target.resolve(relative.toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
