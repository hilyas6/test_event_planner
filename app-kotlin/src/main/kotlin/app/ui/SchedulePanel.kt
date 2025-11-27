package app.ui

import algo.ScheduleResult
import algo.Scheduler
import algo.SlotFinder
import algo.SlotSuggestion
import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableModel

class SchedulePanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout(15, 15)) {

    private data class EventOption(val event: core.model.Event) {
        override fun toString(): String {
            val dateText = "${event.date.format(dateFormatter)} ${event.startTime}-${event.endTime}"
            return "${event.title} ($dateText)"
        }
    }

    private companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    private val eventDropdown = JComboBox<EventOption>()
    private val plannedSizeSpinner = JSpinner(SpinnerNumberModel(50, 1, 100000, 1))
    private val refreshButton = JButton("Refresh")
    private val findSlotButton = JButton("Find Slot")
    private val buildScheduleButton = JButton("Build Schedule")
    private val clearButton = JButton("Clear")
    private val confirmSlotButton = JButton("Confirm Slot")
    private val confirmScheduleButton = JButton("Confirm Selection")
    private val removeConfirmedButton = JButton("Remove Selected")

    private val slotTableModel = object : DefaultTableModel(
        arrayOf("Rank", "Venue", "Date", "Start", "End", "Remaining Venue Capacity"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val slotTable = JTable(slotTableModel)

    private val scheduleTableModel = object : DefaultTableModel(
        arrayOf("Event", "Date", "Start", "End", "Venue", "Remaining Venue Capacity"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val scheduleTable = JTable(scheduleTableModel)

    private val confirmedTableModel = object : DefaultTableModel(
        arrayOf("Event ID", "Event", "Date", "Start", "End", "Venue", "Confirmed At"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val confirmedTable = JTable(confirmedTableModel)

    private var currentSuggestions: List<SlotSuggestion> = emptyList()
    private var currentScheduleResults: List<ScheduleResult> = emptyList()

    init {
        background = UiTheme.backgroundColor
        border = javax.swing.border.EmptyBorder(20, 20, 20, 20)

        configureTables()
        configureButtons()

        val controlsCard = buildControlsCard()
        val slotCard = createTableCard("Slot Suggestions", slotTable, confirmSlotButton)
        val scheduleCard = createTableCard("Generated Schedule Preview", scheduleTable, confirmScheduleButton)
        val confirmedCard = createTableCard("Confirmed Events", confirmedTable, removeConfirmedButton)

        val content = JPanel()
        content.background = UiTheme.backgroundColor
        content.layout = javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS)
        listOf(controlsCard, slotCard, scheduleCard, confirmedCard).forEachIndexed { index, card ->
            content.add(card)
            if (index != 3) {
                content.add(javax.swing.Box.createVerticalStrut(16))
            }
        }

        add(UiTheme.wrapWithScroll(content), BorderLayout.CENTER)

        eventDropdown.addActionListener { onEventSelected() }
        refreshButton.addActionListener { refreshData() }
        clearButton.addActionListener { clearTransientTables() }
        findSlotButton.addActionListener { onFindSlot() }
        buildScheduleButton.addActionListener { onBuildSchedule() }
        confirmSlotButton.addActionListener { confirmSelectedSlot() }
        confirmScheduleButton.addActionListener { confirmSelectedSchedule() }
        removeConfirmedButton.addActionListener { removeSelectedConfirmed() }

        refreshData()
    }

    private fun configureTables() {
        listOf(slotTable, scheduleTable, confirmedTable).forEach { table ->
            UiTheme.styleTable(table)
            table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        }
        val idColumn = confirmedTable.columnModel.getColumn(0)
        idColumn.minWidth = 0
        idColumn.maxWidth = 0
        idColumn.preferredWidth = 0
        idColumn.width = 0
    }

    private fun configureButtons() {
        listOf(
            refreshButton,
            findSlotButton,
            buildScheduleButton,
            clearButton,
            confirmSlotButton,
            confirmScheduleButton,
            removeConfirmedButton
        ).forEach(UiTheme::stylePrimaryButton)
    }

    private fun buildControlsCard(): JComponent {
        val card = UiTheme.createCard(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Scheduling Options"),
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

        eventDropdown.preferredSize = Dimension(260, 30)
        plannedSizeSpinner.preferredSize = Dimension(120, 30)

        addRow("Event:", eventDropdown)
        addRow("Planned size:", plannedSizeSpinner)

        val buttonRow = UiTheme.createButtonRow(refreshButton, findSlotButton, buildScheduleButton, clearButton)
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        card.add(buttonRow, gbc)

        return card
    }

    private fun createTableCard(title: String, table: JTable, actionButton: JButton): JComponent {
        val card = UiTheme.createCard(BorderLayout(10, 10)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), title),
                javax.swing.border.EmptyBorder(12, 12, 12, 12)
            )
        }
        val scroll = javax.swing.JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(0, 220)
            background = UiTheme.cardColor
            viewport.background = java.awt.Color.WHITE
        }
        card.add(scroll, BorderLayout.CENTER)
        card.add(UiTheme.createButtonRow(actionButton), BorderLayout.SOUTH)
        return card
    }

    private fun refreshData() {
        refreshEventDropdown()
        loadConfirmedSchedules()
        clearTransientTables()
    }

    private fun refreshEventDropdown() {
        val previousId = (eventDropdown.selectedItem as? EventOption)?.event?.id
        val scheduledEventIds = AppContext.scheduledEventService.reload().map { it.eventId }.toSet()
        val now = LocalDateTime.now()
        val events = AppContext.eventService.reload()
            .filter { !LocalDateTime.of(it.date, it.startTime).isBefore(now) }
            .filterNot { scheduledEventIds.contains(it.id) }
            .sortedWith(compareBy({ it.date }, { it.startTime }, { it.title }))
        val model = javax.swing.DefaultComboBoxModel<EventOption>()
        events.forEach { model.addElement(EventOption(it)) }
        eventDropdown.model = model
        if (model.size > 0) {
            val targetIndex = (0 until model.size).firstOrNull { model.getElementAt(it).event.id == previousId } ?: 0
            eventDropdown.selectedIndex = targetIndex
            onEventSelected()
        } else {
            plannedSizeSpinner.value = 50
        }
    }

    private fun onEventSelected() {
        val option = eventDropdown.selectedItem as? EventOption ?: return
        plannedSizeSpinner.value = option.event.expectedSize
    }

    private fun onFindSlot() {
        try {
            val option = eventDropdown.selectedItem as? EventOption
            if (option == null) {
                JOptionPane.showMessageDialog(this, "Add an event before searching for slots.")
                return
            }

            clearSlotTable()

            val selectedEvent = option.event

            val planned = plannedSizeSpinner.value as Int
            val now = LocalDateTime.now()
            val today = LocalDate.now()
            val earliest = if (selectedEvent.date.isBefore(today)) today else selectedEvent.date

            val eventsList = AppContext.eventService.reload()
                .filter { !LocalDateTime.of(it.date, it.startTime).isBefore(now) }
            val eventsForSuggestions = eventsList.filter { it.id != selectedEvent.id }
            val venuesList = AppContext.venueService.reload()
            val registrationsList = AppContext.registrationService.reload()
                .filter { reg -> eventsList.any { it.id == reg.eventId } }
            val participantsList = AppContext.participantService.reload()

            val suggestions = SlotFinder.proposeSlots(
                java.util.ArrayList(eventsForSuggestions),
                java.util.ArrayList(venuesList),
                java.util.ArrayList(registrationsList),
                java.util.ArrayList(participantsList),
                planned,
                earliest,
                selectedEvent.startTime,
                selectedEvent.endTime
            ).toList()

            val earliestStart = suggestions.minByOrNull { LocalDateTime.of(it.date, it.startTime) }
            val earliestSuggestions = earliestStart?.let { first ->
                val target = LocalDateTime.of(first.date, first.startTime)
                suggestions.filter { LocalDateTime.of(it.date, it.startTime) == target }
            } ?: emptyList()

            val limited = earliestSuggestions.take(3)
            currentSuggestions = limited

            if (limited.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No suitable venue found from ${earliest.format(dateFormatter)} for size $planned.")
            } else {
                val venueMap = venuesList.associateBy { it.id }
                val otherEventsByVenueDate = eventsForSuggestions
                    .filter { it.venueId != null }
                    .groupBy { it.venueId!! to it.date }
                limited.forEachIndexed { index, suggestion ->
                    val venueName = suggestion.venueId?.let { venueMap[it]?.name } ?: "(venue TBD)"
                    val capacityLeft = suggestion.venueId?.let { venueId ->
                        val venueCapacity = venueMap[venueId]?.capacity ?: return@let null
                        val overlapping = otherEventsByVenueDate.getOrDefault(venueId to suggestion.date, emptyList())
                            .filter { event ->
                                timesOverlap(event.startTime, event.endTime, suggestion.startTime, suggestion.endTime)
                            }
                        val used = overlapping.sumOf { it.expectedSize }
                        (venueCapacity - used - planned).coerceAtLeast(0)
                    }
                    val rankLabel = if (index == 0) "⭐ #1" else "#${index + 1}"
                    slotTableModel.addRow(
                        arrayOf(
                            rankLabel,
                            venueName,
                            suggestion.date.format(dateFormatter),
                            suggestion.startTime.toString(),
                            suggestion.endTime.toString(),
                            capacityLeft?.toString() ?: "-"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    private fun confirmSelectedSlot() {
        val option = eventDropdown.selectedItem as? EventOption
        if (option == null) {
            JOptionPane.showMessageDialog(this, "Select an event to confirm a slot for.")
            return
        }

        val selectedRow = slotTable.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select one of the suggested slots first.")
            return
        }

        val suggestion = currentSuggestions.getOrNull(selectedRow)
        if (suggestion == null) {
            JOptionPane.showMessageDialog(this, "No suggestion data available for the selected row.")
            return
        }

        try {
            AppContext.scheduledEventService.confirmSchedule(
                option.event.id,
                suggestion.date,
                suggestion.startTime,
                suggestion.endTime,
                suggestion.venueId
            )
            JOptionPane.showMessageDialog(this, "Confirmed schedule for '${option.event.title}'.")
            loadConfirmedSchedules()
            refreshEventDropdown()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Unable to confirm schedule: ${e.message}")
        }
    }

    private fun removeSelectedConfirmed() {
        val row = confirmedTable.selectedRow
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a confirmed schedule to remove.")
            return
        }
        val modelRow = confirmedTable.convertRowIndexToModel(row)
        val eventId = confirmedTableModel.getValueAt(modelRow, 0) as String
        AppContext.scheduledEventService.removeSchedule(eventId)
        loadConfirmedSchedules()
        refreshEventDropdown()
        onDataChanged?.invoke()
    }

    private fun onBuildSchedule() {
        try {
            clearScheduleTable()

            val now = LocalDateTime.now()
            val scheduledEventIds = AppContext.scheduledEventService.reload().map { it.eventId }.toSet()
            val eventsList = AppContext.eventService.reload()
                .filter { !LocalDateTime.of(it.date, it.startTime).isBefore(now) }
            val unscheduledEvents = eventsList.filterNot { scheduledEventIds.contains(it.id) }
            val venuesList = AppContext.venueService.reload()
            val registrationsList = AppContext.registrationService.reload()
                .filter { reg -> unscheduledEvents.any { it.id == reg.eventId } }

            val preferenceScores = java.util.HashMap<String, Double>()
            val registrationsByEvent = registrationsList.groupBy { it.eventId }

            unscheduledEvents.forEach { event ->
                val registeredCount = registrationsByEvent[event.id]?.size ?: 0
                val demandRatio = if (event.expectedSize > 0) registeredCount.toDouble() / event.expectedSize else 0.0
                val score = event.expectedSize * 0.25 + registeredCount * 1.5 + demandRatio
                preferenceScores[event.id] = score
            }

            val result = Scheduler.buildOptimizedSchedule(
                java.util.ArrayList(unscheduledEvents),
                java.util.ArrayList(venuesList),
                java.util.ArrayList(registrationsList),
                preferenceScores
            )

            currentScheduleResults = result.toList()

            val evMap = eventsList.associateBy { it.id }
            val venueMap = venuesList.associateBy { it.id }

            val eventsByVenueDate = eventsList
                .filter { it.venueId != null }
                .groupBy { it.venueId!! to it.date }

            currentScheduleResults.forEach { r ->
                val venueName = r.venueId.takeIf { it.isNotBlank() }?.let { venueMap[it]?.name }
                    ?: if (r.scheduled) "(venue TBD)" else "(not scheduled)"
                val title = evMap[r.eventId]?.title ?: r.eventId
                val remainingCapacity = r.venueId.takeIf { it.isNotBlank() }?.let { venueId ->
                    val venueCapacity = venueMap[venueId]?.capacity ?: return@let null
                    val overlapping = eventsByVenueDate.getOrDefault(venueId to r.assignedDate, emptyList())
                        .filter { event ->
                            timesOverlap(event.startTime, event.endTime, r.startTime, r.endTime)
                        }
                    val used = overlapping.sumOf { it.expectedSize }
                    val plannedSize = evMap[r.eventId]?.expectedSize ?: 0
                    (venueCapacity - used - plannedSize).coerceAtLeast(0)
                }

                scheduleTableModel.addRow(
                    arrayOf(
                        title,
                        r.assignedDate.format(dateFormatter),
                        r.startTime.toString(),
                        r.endTime.toString(),
                        venueName,
                        remainingCapacity?.toString() ?: "-"
                    )
                )
            }

            if (currentScheduleResults.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No schedule could be generated with current data.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    private fun confirmSelectedSchedule() {
        val selectedRow = scheduleTable.selectedRow
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a generated schedule entry first.")
            return
        }

        val schedule = currentScheduleResults.getOrNull(selectedRow)
        if (schedule == null) {
            JOptionPane.showMessageDialog(this, "No schedule data available for the selected row.")
            return
        }

        if (!schedule.scheduled) {
            JOptionPane.showMessageDialog(this, "The selected entry does not have an available schedule to confirm.")
            return
        }

        try {
            AppContext.scheduledEventService.confirmSchedule(
                schedule.eventId,
                schedule.assignedDate,
                schedule.startTime,
                schedule.endTime,
                schedule.venueId.takeIf { it.isNotBlank() }
            )
            loadConfirmedSchedules()
            refreshEventDropdown()
            val title = scheduleTableModel.getValueAt(selectedRow, 0) as? String ?: schedule.eventId
            JOptionPane.showMessageDialog(this, "Confirmed schedule for '$title'.")
            onDataChanged?.invoke()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Unable to confirm schedule: ${e.message}")
        }
    }

    private fun loadConfirmedSchedules() {
        val scheduledRecords = AppContext.scheduledEventService.reload()
        val events = AppContext.eventService.reload()
        val venues = AppContext.venueService.reload()
        val eventMap = events.associateBy { it.id }
        val venueMap = venues.associateBy { it.id }

        confirmedTableModel.rowCount = 0
        scheduledRecords
            .sortedWith(compareBy({ it.date }, { it.startTime }, { eventMap[it.eventId]?.title ?: it.eventId }))
            .forEach { record ->
                val event = eventMap[record.eventId]
                val venueName = record.venueId?.let { venueMap[it]?.name } ?: "(venue TBD)"
                confirmedTableModel.addRow(
                    arrayOf(
                        record.eventId,
                        event?.title ?: record.eventId,
                        record.date.format(dateFormatter),
                        record.startTime.toString(),
                        record.endTime.toString(),
                        venueName,
                        record.confirmedAt
                    )
                )
            }
    }

    private fun clearTransientTables() {
        clearSlotTable()
        clearScheduleTable()
    }

    private fun clearSlotTable() {
        currentSuggestions = emptyList()
        slotTableModel.rowCount = 0
    }

    private fun clearScheduleTable() {
        currentScheduleResults = emptyList()
        scheduleTableModel.rowCount = 0
    }

    private fun timesOverlap(aStart: java.time.LocalTime, aEnd: java.time.LocalTime, bStart: java.time.LocalTime, bEnd: java.time.LocalTime): Boolean =
        aStart < bEnd && bStart < aEnd
}
