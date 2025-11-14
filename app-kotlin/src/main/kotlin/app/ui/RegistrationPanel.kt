package app.ui

import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class RegistrationPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout(10, 10)) {

    private data class EventOption(
        val event: core.model.Event,
        val schedule: core.model.ScheduledEvent,
        val venue: core.model.Venue?,
        val remaining: Int,
        val capacity: Int,
        val hasStarted: Boolean
    ) {
        override fun toString(): String {
            val timeRange = "${schedule.startTime}-${schedule.endTime}"
            val venueDisplay = venue?.name?.takeIf { it.isNotBlank() } ?: "Venue TBD"
            val statusSuffix = when {
                remaining <= 0 -> " • Full"
                hasStarted -> " • Started"
                else -> ""
            }
            return "${event.title} (${schedule.date} $timeRange @ $venueDisplay, $remaining/$capacity spots left)$statusSuffix"
        }
    }

    private data class RegistrationRow(
        val registration: core.model.Registration,
        val participant: core.model.Participant,
        val participantFullName: String,
        val event: core.model.Event,
        val schedule: core.model.ScheduledEvent?,
        val venue: core.model.Venue?
    )

    private enum class FilterMode(val displayName: String) {
        ALL("All Registrations"),
        BY_DATE("Event Date"),
        BY_EVENT("Event"),
        BY_VENUE("Venue");

        override fun toString(): String = displayName
    }

    private val eventDropdown = JComboBox<EventOption>()

    private val firstNameField = JTextField(15)
    private val lastNameField = JTextField(15)
    private val dobField = DateField(LocalDate.now().minusYears(18))
    private val phoneField = JTextField(15)
    private val emailField = JTextField(25)

    private val registerButton = JButton("Register Participant")
    private val deleteButton = JButton("Delete Registration")
    private val refreshButton = JButton("Refresh Data")

    private val filterModeDropdown = JComboBox(FilterMode.values())
    private val filterValueDropdown = JComboBox<String>()
    private val filterValueLabel = JLabel("Value:")

    private val tableModel = object : DefaultTableModel(
        arrayOf(
            "ID",
            "First Name",
            "Last Name",
            "Date of Birth",
            "Phone",
            "Email",
            "Event",
            "Date",
            "Start",
            "End",
            "Venue",
            "Registered At"
        ),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val table = JTable(tableModel)

    private var registrationRows: List<RegistrationRow> = emptyList()

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^[0-9]{11}$")

    private val eventNameValue = JLabel("-")
    private val eventDateValue = JLabel("-")
    private val eventTimeValue = JLabel("-")
    private val eventVenueValue = JLabel("-")
    private val eventCapacityValue = JLabel("-")
    private val eventDescriptionArea = JTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder()
        isOpaque = false
    }

    init {
        border = EmptyBorder(12, 12, 12, 12)

        val content = JPanel()
        content.layout = javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS)
        listOf(buildFormCard(), buildEventDetailsCard(), buildFilterCard(), buildTableCard()).forEachIndexed { index, card ->
            content.add(card)
            if (index < 3) {
                content.add(javax.swing.Box.createVerticalStrut(12))
            }
        }

        add(JScrollPane(content), BorderLayout.CENTER)

        registerButton.addActionListener { register() }
        deleteButton.addActionListener { deleteReg() }
        refreshButton.addActionListener { refreshAll() }
        eventDropdown.addActionListener { updateEventDetails() }
        filterModeDropdown.addActionListener { updateFilterValues() }
        filterValueDropdown.addActionListener { applyFilterAndPopulateTable() }

        table.autoCreateRowSorter = true

        filterValueDropdown.isEnabled = false
        filterValueLabel.isEnabled = false

        refreshAll()
    }

    private fun buildFormCard(): JComponent {
        val card = JPanel(GridBagLayout())
        card.border = BorderFactory.createTitledBorder("Register Participant")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0
        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            card.add(JLabel(label), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            card.add(component, gbc)
            row++
        }

        eventDropdown.preferredSize = Dimension(280, 28)
        dobField.spinner.preferredSize = Dimension(160, 28)

        addRow("Event:", eventDropdown)
        addRow("First Name:", firstNameField)
        addRow("Last Name:", lastNameField)
        addRow("Date of Birth:", dobField.component)
        addRow("Phone:", phoneField)
        addRow("Email:", emailField)

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(registerButton)
            add(deleteButton)
            add(refreshButton)
        }

        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        card.add(buttonRow, gbc)

        return card
    }

    private fun buildEventDetailsCard(): JComponent {
        val card = JPanel(GridBagLayout())
        card.border = BorderFactory.createTitledBorder("Selected Event Details")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        var row = 0
        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            card.add(JLabel(label), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            card.add(component, gbc)
            row++
        }

        addRow("Name:", eventNameValue)
        addRow("Date:", eventDateValue)
        addRow("Time:", eventTimeValue)
        addRow("Venue:", eventVenueValue)
        addRow("Capacity:", eventCapacityValue)
        addRow("Description:", JScrollPane(eventDescriptionArea).apply {
            preferredSize = Dimension(0, 70)
            border = BorderFactory.createEmptyBorder()
            viewport.border = BorderFactory.createEmptyBorder()
        })

        return card
    }

    private fun buildFilterCard(): JComponent {
        val card = JPanel(GridBagLayout())
        card.border = BorderFactory.createTitledBorder("Filter Registrations")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        gbc.gridx = 0
        gbc.gridy = 0
        card.add(JLabel("Filter by:"), gbc)

        gbc.gridx = 1
        card.add(filterModeDropdown, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        card.add(filterValueLabel, gbc)

        gbc.gridx = 1
        card.add(filterValueDropdown, gbc)

        return card
    }

    private fun buildTableCard(): JComponent {
        val card = JPanel(BorderLayout(6, 6))
        card.border = BorderFactory.createTitledBorder("Registrations")
        val scroll = JScrollPane(table)
        card.add(scroll, BorderLayout.CENTER)
        return card
    }

    private fun register() {
        val eventOption = eventDropdown.selectedItem as? EventOption ?: run {
            JOptionPane.showMessageDialog(this, "No event selected")
            return
        }
        val first = firstNameField.text.trim()
        val last = lastNameField.text.trim()
        val phone = phoneField.text.trim()
        val email = emailField.text.trim()
        val dob = dobField.date

        if (first.isEmpty() || last.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name")
            return
        }
        if (!phoneRegex.matches(phone)) {
            JOptionPane.showMessageDialog(this, "Phone must be 11 digits")
            return
        }
        if (!emailRegex.matches(email)) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email")
            return
        }
        if (eventOption.remaining <= 0) {
            JOptionPane.showMessageDialog(this, "That event is already full")
            return
        }

        try {
            val participant = AppContext.participantService.addParticipant(first, last, dob, phone, email)
            AppContext.registrationService.registerForScheduledEvent(eventOption.schedule, participant.id)
            JOptionPane.showMessageDialog(this, "Participant registered")
            clearForm()
            refreshAll()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    private fun deleteReg() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a registration to delete")
            return
        }
        val modelRow = table.convertRowIndexToModel(selectedRow)
        val id = tableModel.getValueAt(modelRow, 0) as String
        AppContext.registrationService.deleteRegistrationById(id)
        refreshAll()
        onDataChanged?.invoke()
    }

    private fun refreshAll() {
        refreshEventDropdown()
        loadRegistrations()
        updateEventDetails()
        updateFilterValues()
    }

    private fun refreshEventDropdown() {
        val scheduledEvents = AppContext.scheduledEventService.reload()
        val venues = AppContext.venueService.all().associateBy { it.id }
        val events = AppContext.eventService.reload().associateBy { it.id }

        val options = scheduledEvents.mapNotNull { schedule ->
            val event = events[schedule.eventId] ?: return@mapNotNull null
            val venue = schedule.venueId?.let { venues[it] }
            val capacity = AppContext.registrationService.capacityLimit(event)
            val registered = AppContext.registrationService.occupancyFor(event.id)
            val remaining = (capacity - registered).coerceAtLeast(0)
            val hasStarted = LocalDateTime.of(schedule.date, schedule.startTime).isBefore(LocalDateTime.now())
            EventOption(event, schedule, venue, remaining, capacity, hasStarted)
        }

        eventDropdown.model = DefaultComboBoxModel(options.toTypedArray())
        if (options.isEmpty()) {
            clearEventDetails()
        } else {
            eventDropdown.selectedIndex = 0
        }
    }

    private fun updateEventDetails() {
        val option = eventDropdown.selectedItem as? EventOption ?: run {
            clearEventDetails()
            return
        }
        eventNameValue.text = option.event.title
        eventDateValue.text = option.schedule.date.format(dateFormatter)
        eventTimeValue.text = "${option.schedule.startTime} - ${option.schedule.endTime}"
        eventVenueValue.text = option.venue?.name ?: "TBC"
        eventCapacityValue.text = "${option.remaining} of ${option.capacity} seats left"
        eventDescriptionArea.text = option.event.description
    }

    private fun clearEventDetails() {
        eventNameValue.text = "-"
        eventDateValue.text = "-"
        eventTimeValue.text = "-"
        eventVenueValue.text = "-"
        eventCapacityValue.text = "-"
        eventDescriptionArea.text = ""
    }

    private fun loadRegistrations() {
        val registrations = AppContext.registrationService.reload()
        val participants = AppContext.participantService.all().associateBy { it.id }
        val events = AppContext.eventService.reload().associateBy { it.id }
        val schedules = AppContext.scheduledEventService.reload().associateBy { it.eventId }
        val venues = AppContext.venueService.all().associateBy { it.id }

        registrationRows = registrations.mapNotNull { reg ->
            val participant = participants[reg.participantId] ?: return@mapNotNull null
            val event = events[reg.eventId] ?: return@mapNotNull null
            val schedule = schedules[reg.eventId]
            val venue = schedule?.venueId?.let { venues[it] }
            RegistrationRow(
                registration = reg,
                participant = participant,
                participantFullName = "${participant.firstName} ${participant.lastName}",
                event = event,
                schedule = schedule,
                venue = venue
            )
        }

        applyFilterAndPopulateTable()
    }

    private fun updateFilterValues() {
        val mode = filterModeDropdown.selectedItem as? FilterMode ?: FilterMode.ALL
        val values = when (mode) {
            FilterMode.ALL -> emptyList()
            FilterMode.BY_DATE -> registrationRows.mapNotNull { it.schedule?.date?.format(dateFormatter) }.distinct().sorted()
            FilterMode.BY_EVENT -> registrationRows.map { it.event.title }.distinct().sorted()
            FilterMode.BY_VENUE -> registrationRows.mapNotNull { it.venue?.name }.distinct().sorted()
        }

        filterValueDropdown.model = DefaultComboBoxModel(values.toTypedArray())
        val enableValue = values.isNotEmpty()
        filterValueDropdown.isEnabled = enableValue
        filterValueLabel.isEnabled = enableValue
        if (enableValue) {
            filterValueDropdown.selectedIndex = 0
        }
        applyFilterAndPopulateTable()
    }

    private fun applyFilterAndPopulateTable() {
        val mode = filterModeDropdown.selectedItem as? FilterMode ?: FilterMode.ALL
        val selectedValue = filterValueDropdown.selectedItem as? String

        val filtered = registrationRows.filter { row ->
            when (mode) {
                FilterMode.ALL -> true
                FilterMode.BY_DATE -> row.schedule?.date?.format(dateFormatter) == selectedValue
                FilterMode.BY_EVENT -> row.event.title == selectedValue
                FilterMode.BY_VENUE -> row.venue?.name == selectedValue
            }
        }

        tableModel.rowCount = 0
        filtered.forEach { row ->
            tableModel.addRow(
                arrayOf(
                    row.registration.id,
                    row.participant.firstName,
                    row.participant.lastName,
                    row.participant.dateOfBirth.format(dateFormatter),
                    row.participant.phone,
                    row.participant.email,
                    row.event.title,
                    row.schedule?.date?.format(dateFormatter) ?: "-",
                    row.schedule?.startTime?.toString() ?: "-",
                    row.schedule?.endTime?.toString() ?: "-",
                    row.venue?.name ?: "-",
                    row.registration.registeredAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )
            )
        }
    }

    private fun clearForm() {
        firstNameField.text = ""
       lastNameField.text = ""
       phoneField.text = ""
       emailField.text = ""
       dobField.date = LocalDate.now().minusYears(18)
    }

    fun refreshFromOutside() {
        refreshAll()
    }
}
