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

        val form = JPanel(GridLayout(0, 2, 5, 5))
        form.border = BorderFactory.createTitledBorder("Register Participant")
        form.add(JLabel("Event:")); form.add(eventDropdown)
        form.add(JLabel("Participant:")); form.add(participantDropdown)

        val detailsPanel = JPanel(GridLayout(0, 2, 5, 5)).apply {
            border = BorderFactory.createTitledBorder("Event Details")
            add(JLabel("Name:")); add(eventNameValue)
            add(JLabel("Date:")); add(eventDateValue)
            add(JLabel("Time:")); add(eventTimeValue)
            add(JLabel("Venue:")); add(eventVenueValue)
            add(JLabel("Spaces Available:")); add(eventCapacityValue)
            add(JLabel("Description:")); add(JScrollPane(eventDescriptionArea).apply {
                preferredSize = Dimension(0, 70)
                border = BorderFactory.createEmptyBorder()
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            })
        }

        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 5)).apply {
            border = BorderFactory.createTitledBorder("View Registrations")
            add(JLabel("View by:"))
            add(filterModeDropdown)
            add(filterValueLabel)
            add(filterValueDropdown)
        }

        val topPanel = JPanel()
        topPanel.layout = BoxLayout(topPanel, BoxLayout.Y_AXIS)
        topPanel.add(form)
        topPanel.add(Box.createVerticalStrut(8))
        topPanel.add(detailsPanel)
        topPanel.add(Box.createVerticalStrut(8))
        topPanel.add(filterPanel)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createTitledBorder("Registrations")

        val contentPanel = JPanel(BorderLayout(10, 10))
        contentPanel.add(topPanel, BorderLayout.NORTH)
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        add(toolbar, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)

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
            AppContext.registrationService.register(eventOption.event.id, participantOption.participant.id)
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
