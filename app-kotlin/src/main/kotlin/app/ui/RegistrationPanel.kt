package app.ui

import app.AppContext
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.*
import java.time.format.DateTimeFormatter

class RegistrationPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

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

    private data class ParticipantOption(val participant: core.model.Participant) {
        override fun toString(): String = "${participant.firstName} ${participant.lastName}"
    }

    private data class RegistrationRow(
        val registration: core.model.Registration,
        val participantName: String,
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
    private val participantDropdown = JComboBox<ParticipantOption>()

    private val eventNameValue = JLabel("-")
    private val eventDateValue = JLabel("-")
    private val eventTimeValue = JLabel("-")
    private val eventVenueValue = JLabel("-")
    private val eventCapacityValue = JLabel("-")
    private val eventDescriptionArea = JTextArea(3, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        background = UIManager.getColor("Panel.background")
    }

    private val registerButton = JButton("✅ Register")
    private val deleteButton = JButton("🗑 Delete")
    private val refreshButton = JButton("🔄 Refresh")

    private val filterModeDropdown = JComboBox(FilterMode.values())
    private val filterValueDropdown = JComboBox<String>()
    private val filterValueLabel = JLabel("Value:")

    private val tableModel = object : DefaultTableModel(
        arrayOf("ID", "Participant", "Event", "Date", "Start", "End", "Venue", "Registered At"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val table = JTable(tableModel)

    private var registrationRows: List<RegistrationRow> = emptyList()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.add(registerButton)
        toolbar.add(deleteButton)
        toolbar.add(refreshButton)

        val form = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Register Participant")
            val gbc = GridBagConstraints().apply {
                insets = Insets(4, 4, 4, 4)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 0.0
            }

            gbc.gridx = 0
            gbc.gridy = 0
            add(JLabel("Event:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            add(eventDropdown, gbc)

            gbc.gridx = 0
            gbc.gridy = 1
            gbc.weightx = 0.0
            add(JLabel("Participant:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            add(participantDropdown, gbc)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val detailsPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("Event Details")
            val gbc = GridBagConstraints().apply {
                insets = Insets(3, 4, 3, 4)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
            }

            gbc.gridx = 0
            gbc.gridy = 0
            gbc.weightx = 0.0
            add(JLabel("Name:"), gbc)

            gbc.gridx = 1
            gbc.gridwidth = 3
            gbc.weightx = 1.0
            add(eventNameValue, gbc)

            gbc.gridy = 1
            gbc.gridx = 0
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            add(JLabel("Date:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 0.5
            add(eventDateValue, gbc)

            gbc.gridx = 2
            gbc.weightx = 0.0
            add(JLabel("Time:"), gbc)

            gbc.gridx = 3
            gbc.weightx = 0.5
            add(eventTimeValue, gbc)

            gbc.gridy = 2
            gbc.gridx = 0
            gbc.weightx = 0.0
            add(JLabel("Venue:"), gbc)

            gbc.gridx = 1
            gbc.weightx = 0.5
            add(eventVenueValue, gbc)

            gbc.gridx = 2
            gbc.weightx = 0.0
            add(JLabel("Spaces:"), gbc)

            gbc.gridx = 3
            gbc.weightx = 0.5
            add(eventCapacityValue, gbc)

            val descriptionScroll = JScrollPane(eventDescriptionArea).apply {
                preferredSize = Dimension(0, 60)
                border = BorderFactory.createEmptyBorder()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            }

            gbc.gridy = 3
            gbc.gridx = 0
            gbc.gridwidth = 1
            gbc.weightx = 0.0
            gbc.weighty = 0.0
            add(JLabel("Description:"), gbc)

            gbc.gridx = 1
            gbc.gridwidth = 3
            gbc.weightx = 1.0
            gbc.fill = GridBagConstraints.BOTH
            add(descriptionScroll, gbc)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 5)).apply {
            border = BorderFactory.createTitledBorder("View Registrations")
            add(JLabel("View by:"))
            add(filterModeDropdown)
            add(filterValueLabel)
            add(filterValueDropdown)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
            add(form)
            add(Box.createVerticalStrut(8))
            add(detailsPanel)
            add(Box.createVerticalStrut(8))
            add(filterPanel)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val tableScrollPane = JScrollPane(table).apply {
            border = BorderFactory.createTitledBorder("Registrations")
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(0, 260)
        }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(topPanel)
            add(Box.createVerticalStrut(12))
            add(tableScrollPane)
        }

        val outerScroll = JScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
        }

        add(toolbar, BorderLayout.NORTH)
        add(outerScroll, BorderLayout.CENTER)

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

    private fun register() {
        val eventOption = eventDropdown.selectedItem as? EventOption ?: run {
            JOptionPane.showMessageDialog(this, "Select an event with capacity remaining.")
            return
        }
        val participantOption = participantDropdown.selectedItem as? ParticipantOption ?: run {
            JOptionPane.showMessageDialog(this, "Select a participant to register.")
            return
        }

        val remaining = AppContext.registrationService.remainingCapacity(eventOption.event)
        if (remaining <= 0) {
            JOptionPane.showMessageDialog(this, "This event is currently full. Choose another event or try again later.")
            refreshAll()
            return
        }

        if (eventOption.hasStarted) {
            JOptionPane.showMessageDialog(this, "This event has already started. You cannot register for it anymore.")
            refreshAll()
            return
        }

        try {
            AppContext.registrationService.registerForScheduledEvent(
                eventOption.schedule,
                participantOption.participant.id
            )
            JOptionPane.showMessageDialog(
                this,
                "✅ Registered ${participantOption.participant.firstName} ${participantOption.participant.lastName} for ${eventOption.event.title}"
            )
            refreshAll()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message ?: "Unable to register")
        }
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

    private fun refreshAll() {
        refreshDropdowns()
        refreshTable()
    }

    private fun refreshDropdowns() {
        val eventModel = DefaultComboBoxModel<EventOption>()
        val participantModel = DefaultComboBoxModel<ParticipantOption>()

        val events = AppContext.eventService.reload()
        val schedules = AppContext.scheduledEventService.reload()
        val participants = AppContext.participantService.reload()
        val venues = AppContext.venueService.reload()
        val venueMap = venues.associateBy { it.id }
        val eventMap = events.associateBy { it.id }
        val now = java.time.LocalDateTime.now()

        schedules
            .mapNotNull { schedule ->
                val event = eventMap[schedule.eventId] ?: return@mapNotNull null
                val capacityLimit = AppContext.registrationService.capacityLimit(event)
                val remaining = AppContext.registrationService.remainingCapacity(event)
                val venue = schedule.venueId?.let { venueMap[it] }
                val hasStarted = java.time.LocalDateTime.of(schedule.date, schedule.startTime).isBefore(now)
                EventOption(event, schedule, venue, remaining, capacityLimit, hasStarted)
            }
            .sortedWith(compareBy({ it.schedule.date }, { it.schedule.startTime }, { it.event.title }))
            .forEach { eventModel.addElement(it) }

        participants
            .sortedWith(compareBy({ it.firstName }, { it.lastName }))
            .forEach { participantModel.addElement(ParticipantOption(it)) }

        eventDropdown.model = eventModel
        participantDropdown.model = participantModel

        if (eventModel.size == 0) {
            eventDropdown.isEnabled = false
            eventDropdown.toolTipText = "No confirmed schedules available"
            registerButton.isEnabled = false
            registerButton.toolTipText = "Add confirmed schedules to register participants"
        } else {
            eventDropdown.isEnabled = true
            eventDropdown.toolTipText = null
            eventDropdown.selectedIndex = 0
            registerButton.isEnabled = true
            registerButton.toolTipText = null
        }

        if (participantModel.size == 0) {
            participantDropdown.isEnabled = false
            participantDropdown.toolTipText = "Add participants before registering"
        } else {
            participantDropdown.isEnabled = true
            participantDropdown.toolTipText = null
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
            RegistrationRow(
                registration = reg,
                participantName = "${participant.firstName} ${participant.lastName}",
                event = event,
                schedule = schedule,
                venue = venue
            )
        }.sortedWith(compareBy({ it.event.date }, { it.event.startTime }, { it.participantName }))

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
                arrayOf(
                    row.registration.id,
                    row.participantName,
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
