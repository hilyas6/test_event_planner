package app.ui

import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ScrollPaneConstants
import javax.swing.table.DefaultTableModel

class RegistrationPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout(15, 15)) {

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
            val dateText = schedule.date.format(dateFormatter)
            return "${event.title} ($dateText $timeRange @ $venueDisplay, $remaining/$capacity spots left)$statusSuffix"
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
    private val dobField = DatePickerField(LocalDate.now().minusYears(18))
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

    private companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
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
        background = UiTheme.cardColor
    }

    init {
        background = UiTheme.backgroundColor
        border = javax.swing.border.EmptyBorder(20, 20, 20, 20)

        UiTheme.stylePrimaryButton(registerButton)
        UiTheme.stylePrimaryButton(deleteButton)
        UiTheme.stylePrimaryButton(refreshButton)

        val formCard = buildFormCard()
        val eventDetailsCard = buildEventDetailsCard()
        val filterCard = buildFilterCard()
        val tableCard = buildTableCard()

        val content = JPanel()
        content.background = UiTheme.backgroundColor
        content.layout = javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS)
        listOf(formCard, eventDetailsCard, filterCard, tableCard).forEachIndexed { index, card ->
            content.add(card)
            if (index != 3) {
                content.add(javax.swing.Box.createVerticalStrut(16))
            }
        }

        add(UiTheme.wrapWithScroll(content), BorderLayout.CENTER)

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
        val card = UiTheme.createCard(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Register Participant"),
                javax.swing.border.EmptyBorder(16, 16, 16, 16)
            )
        }
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0
        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            card.add(UiTheme.styleLabel(JLabel(label), bold = true), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            card.add(component, gbc)
            row++
        }

        eventDropdown.preferredSize = Dimension(280, 30)
        firstNameField.preferredSize = Dimension(220, 30)
        lastNameField.preferredSize = Dimension(220, 30)
        dobField.spinner.preferredSize = Dimension(160, 30)
        phoneField.preferredSize = Dimension(220, 30)
        emailField.preferredSize = Dimension(260, 30)

        addRow("Event:", eventDropdown)
        addRow("First Name:", firstNameField)
        addRow("Last Name:", lastNameField)
        addRow("Date of Birth:", dobField.component)
        addRow("Phone (11 digits):", phoneField)
        addRow("Email:", emailField)

        val buttonRow = UiTheme.createButtonRow(registerButton, deleteButton, refreshButton)
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        card.add(buttonRow, gbc)

        return card
    }

    private fun buildEventDetailsCard(): JComponent {
        val card = UiTheme.createCard(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Event Details"),
                javax.swing.border.EmptyBorder(14, 14, 14, 14)
            )
        }
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0
        fun addRow(label: String, component: JComponent, fill: Int = GridBagConstraints.HORIZONTAL) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            gbc.fill = GridBagConstraints.HORIZONTAL
            card.add(UiTheme.styleLabel(JLabel(label), bold = true), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.fill = fill
            card.add(component, gbc)
            row++
        }

        listOf(eventNameValue, eventDateValue, eventTimeValue, eventVenueValue, eventCapacityValue).forEach {
            it.foreground = UiTheme.textColor
        }

        addRow("Name:", eventNameValue)
        addRow("Date:", eventDateValue)
        addRow("Time:", eventTimeValue)
        addRow("Venue:", eventVenueValue)
        addRow("Capacity:", eventCapacityValue)

        val descriptionScroll = javax.swing.JScrollPane(eventDescriptionArea).apply {
            preferredSize = Dimension(0, 80)
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        addRow("Description:", descriptionScroll, GridBagConstraints.BOTH)

        return card
    }

    private fun buildFilterCard(): JComponent {
        val card = UiTheme.createCard(FlowLayout(FlowLayout.LEFT, 12, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "View Registrations"),
                javax.swing.border.EmptyBorder(12, 12, 12, 12)
            )
        }
        filterModeDropdown.preferredSize = Dimension(200, 30)
        filterValueDropdown.preferredSize = Dimension(200, 30)
        card.add(UiTheme.styleLabel(JLabel("View by:"), bold = true))
        UiTheme.styleLabel(filterValueLabel, bold = true)
        card.add(filterModeDropdown)
        card.add(filterValueLabel)
        card.add(filterValueDropdown)
        return card
    }

    private fun buildTableCard(): JComponent {
        UiTheme.styleTable(table)
        val card = UiTheme.createCard(BorderLayout(10, 10)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Registrations"),
                javax.swing.border.EmptyBorder(12, 12, 12, 12)
            )
        }
        val tableScrollPane = javax.swing.JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(0, 280)
            background = UiTheme.cardColor
            viewport.background = java.awt.Color.WHITE
        }
        card.add(tableScrollPane, BorderLayout.CENTER)
        return card
    }

    private fun register() {
        val eventOption = eventDropdown.selectedItem as? EventOption ?: run {
            showMessage("Select an event with capacity remaining.")
            return
        }

        val firstName = firstNameField.text.trim()
        val lastName = lastNameField.text.trim()
        val phone = phoneField.text.trim()
        val email = emailField.text.trim()
        val dob = dobField.date

        when {
            firstName.isBlank() || lastName.isBlank() -> {
                showMessage("Please provide the participant's first and last name.")
                return
            }
            dob.isAfter(LocalDate.now()) -> {
                showMessage("Date of birth cannot be in the future.")
                return
            }
            !phoneRegex.matches(phone) -> {
                JOptionPane.showMessageDialog(this, "Phone number must contain exactly 11 digits.", "Invalid Phone", JOptionPane.ERROR_MESSAGE)
                return
            }
            !emailRegex.matches(email) -> {
                JOptionPane.showMessageDialog(this, "Please enter a valid email address.", "Invalid Email", JOptionPane.ERROR_MESSAGE)
                return
            }
        }

        val remaining = AppContext.registrationService.remainingCapacity(eventOption.event)
        if (remaining <= 0) {
            showMessage("This event is currently full. Choose another event or try again later.")
            refreshAll()
            return
        }
        if (eventOption.hasStarted) {
            showMessage("This event has already started. You cannot register for it anymore.")
            refreshAll()
            return
        }

        try {
            val participant = AppContext.participantService.upsertParticipant(
                firstName,
                lastName,
                dob,
                phone,
                email
            )
            AppContext.registrationService.registerForScheduledEvent(
                eventOption.schedule,
                participant.id
            )
            JOptionPane.showMessageDialog(
                this,
                "✅ Registered ${participant.firstName} ${participant.lastName} for ${eventOption.event.title}"
            )
            clearParticipantForm()
            refreshAll()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                refreshAll()
            }
            JOptionPane.showMessageDialog(this, e.message ?: "Unable to register")
        }
    }

    private fun clearParticipantForm() {
        firstNameField.text = ""
        lastNameField.text = ""
        dobField.date = LocalDate.now().minusYears(18)
        phoneField.text = ""
        emailField.text = ""
    }

    private fun deleteReg() {
        val row = table.selectedRow
        if (row < 0) return
        val modelRow = table.convertRowIndexToModel(row)
        val id = tableModel.getValueAt(modelRow, 0) as String
        AppContext.registrationService.deleteRegistrationById(id)
        refreshAll()
        onDataChanged?.invoke()
    }

    private fun showMessage(message: String) {
        if (GraphicsEnvironment.isHeadless()) {
            println(message)
        } else {
            JOptionPane.showMessageDialog(this, message)
        }
    }

    private fun refreshAll() {
        refreshDropdowns()
        refreshTable()
    }

    private fun refreshDropdowns() {
        val eventModel = DefaultComboBoxModel<EventOption>()

        val events = AppContext.eventService.reload()
        val schedules = AppContext.scheduledEventService.reload()
        val venues = AppContext.venueService.reload()
        val venueMap = venues.associateBy { it.id }
        val eventMap = events.associateBy { it.id }
        val today = LocalDate.now()
        val now = LocalTime.now()

        schedules
            .mapNotNull { schedule ->
                val event = eventMap[schedule.eventId] ?: return@mapNotNull null
                val capacityLimit = AppContext.registrationService.capacityLimit(event)
                val remaining = AppContext.registrationService.remainingCapacity(event)
                val venue = schedule.venueId?.let { venueMap[it] }
                val isInFuture = schedule.date.isAfter(today)
                val startsLaterToday = schedule.date.isEqual(today) && schedule.startTime.isAfter(now)
                val hasStarted = !(isInFuture || startsLaterToday)
                EventOption(event, schedule, venue, remaining, capacityLimit, hasStarted)
            }
            .sortedWith(compareBy({ it.schedule.date }, { it.schedule.startTime }, { it.event.title }))
            .forEach { eventModel.addElement(it) }

        eventDropdown.model = eventModel

        if (eventModel.size == 0) {
            eventDropdown.isEnabled = false
            eventDropdown.toolTipText = "No confirmed schedules available"
            registerButton.isEnabled = false
            registerButton.toolTipText = "No upcoming events with available capacity"
        } else {
            eventDropdown.isEnabled = true
            eventDropdown.toolTipText = null
            eventDropdown.selectedIndex = 0
            registerButton.isEnabled = true
            registerButton.toolTipText = null
        }

        updateEventDetails()
    }

    private fun refreshTable() {
        val regs = AppContext.registrationService.reload()
        val participants = AppContext.participantService.reload().associateBy { it.id }
        val events = AppContext.eventService.reload().associateBy { it.id }
        val venues = AppContext.venueService.reload().associateBy { it.id }
        val schedules = AppContext.scheduledEventService.reload().associateBy { it.eventId }

        registrationRows = regs.mapNotNull { reg ->
            val participant = participants[reg.participantId] ?: return@mapNotNull null
            val event = events[reg.eventId] ?: return@mapNotNull null
            val schedule = schedules[reg.eventId]
            val venue = event.venueId?.let { venues[it] }
            val fullName = listOf(participant.firstName, participant.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            RegistrationRow(
                registration = reg,
                participant = participant,
                participantFullName = fullName.ifBlank { participant.firstName.ifBlank { participant.lastName } },
                event = event,
                schedule = schedule,
                venue = venue
            )
        }.sortedWith(compareBy({ it.event.date }, { it.event.startTime }, { it.participantFullName }))

        updateFilterValues()
    }

    private fun updateEventDetails() {
        val option = eventDropdown.selectedItem as? EventOption
        if (option == null) {
            eventNameValue.text = "-"
            eventDateValue.text = "-"
            eventTimeValue.text = "-"
            eventVenueValue.text = "-"
            eventCapacityValue.text = "-"
            eventDescriptionArea.text = ""
            eventDescriptionArea.toolTipText = null
            registerButton.isEnabled = false
            registerButton.toolTipText = "Select an event to register participants"
            return
        }

        val event = option.event
        val schedule = option.schedule
        val venue = option.venue
        val remaining = AppContext.registrationService.remainingCapacity(event)
        val capacity = AppContext.registrationService.capacityLimit(event)
        val hasStarted = option.hasStarted

        eventNameValue.text = event.title
        eventDateValue.text = schedule.date.format(dateFormatter)
        eventTimeValue.text = "${schedule.startTime} - ${schedule.endTime}"
        eventVenueValue.text = venue?.let { "${it.name} (${it.city})" } ?: "Venue TBD"
        eventCapacityValue.text = "$remaining / $capacity"
        val description = event.description.ifBlank { "No additional details" }
        eventDescriptionArea.text = description
        eventDescriptionArea.caretPosition = 0
        eventDescriptionArea.toolTipText = if (description.length > 120) description else null

        val canRegister = remaining > 0 && !hasStarted
        registerButton.isEnabled = canRegister
        registerButton.toolTipText = when {
            !canRegister && remaining <= 0 -> "This event has reached full capacity"
            !canRegister && hasStarted -> "This event has already started"
            else -> null
        }
    }

    private fun updateFilterValues() {
        val mode = filterModeDropdown.selectedItem as? FilterMode ?: FilterMode.ALL
        val values = when (mode) {
            FilterMode.ALL -> emptyList()
            FilterMode.BY_DATE -> registrationRows.mapNotNull { row ->
                row.schedule?.date?.format(dateFormatter) ?: row.event.date.format(dateFormatter)
            }.distinct().sorted()
            FilterMode.BY_EVENT -> registrationRows.map { it.event.title }.distinct().sorted()
            FilterMode.BY_VENUE -> registrationRows.map {
                it.venue?.name ?: "Venue TBD"
            }.distinct().sorted()
        }

        val model = DefaultComboBoxModel(values.toTypedArray())
        filterValueDropdown.model = model
        val hasValues = mode != FilterMode.ALL && values.isNotEmpty()
        filterValueDropdown.isEnabled = hasValues
        filterValueLabel.isEnabled = hasValues

        if (hasValues) {
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
                FilterMode.BY_DATE -> {
                    val dateValue = row.schedule?.date?.format(dateFormatter)
                        ?: row.event.date.format(dateFormatter)
                    selectedValue == null || dateValue == selectedValue
                }
                FilterMode.BY_EVENT -> selectedValue == null || row.event.title == selectedValue
                FilterMode.BY_VENUE -> {
                    val venueName = row.venue?.name ?: "Venue TBD"
                    selectedValue == null || venueName == selectedValue
                }
            }
        }

        tableModel.rowCount = 0
        filtered.forEach { row ->
            val schedule = row.schedule
            val dateText = schedule?.date?.format(dateFormatter) ?: row.event.date.format(dateFormatter)
            val startText = schedule?.startTime ?: row.event.startTime
            val endText = schedule?.endTime ?: row.event.endTime
            val venueName = row.venue?.let { "${it.name} (${it.city})" } ?: "Venue TBD"
            tableModel.addRow(
                arrayOf<Any?>(
                    row.registration.id,
                    row.participant.firstName,
                    row.participant.lastName,
                    row.participant.dateOfBirth.format(dateFormatter),
                    row.participant.phone,
                    row.participant.email,
                    row.event.title,
                    dateText,
                    startText,
                    endText,
                    venueName,
                    row.registration.registeredAt
                )
            )
        }
    }
}
