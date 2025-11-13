package app.ui

import algo.ScheduleResult
import algo.Scheduler
import algo.SlotFinder
import algo.SlotSuggestion
import app.AppContext
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class SchedulePanel : JPanel(BorderLayout()) {

    private data class EventOption(val event: core.model.Event) {
        override fun toString(): String {
            val dateText = "${event.date} ${event.startTime}-${event.endTime}"
            return "${event.title} ($dateText)"
        }
    }

    private val zoneId: ZoneId = ZoneId.systemDefault()

    private val eventDropdown = JComboBox<EventOption>()
    private val plannedSizeSpinner = JSpinner(SpinnerNumberModel(50, 1, 100000, 1))
    private val earliestDateSpinner = JSpinner(SpinnerDateModel(Date(), null, null, Calendar.DAY_OF_MONTH))
    private val refreshButton = JButton("↻ Refresh")
    private val findSlotButton = JButton("🔍 Find Slot")
    private val buildScheduleButton = JButton("🧩 Build Schedule")
    private val clearButton = JButton("🗑 Clear")
    private val confirmSlotButton = JButton("✅ Confirm Selected Slot")
    private val removeConfirmedButton = JButton("🗑 Remove Selected")

    private val slotTableModel = object : DefaultTableModel(
        arrayOf("Rank", "Venue", "Date", "Start", "End", "Confidence", "Notes"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val slotTable = JTable(slotTableModel)

    private val scheduleTableModel = object : DefaultTableModel(
        arrayOf("Event", "Date", "Start", "End", "Venue", "Confidence", "Notes"),
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
        val dateEditor = JSpinner.DateEditor(earliestDateSpinner, "yyyy-MM-dd")
        earliestDateSpinner.editor = dateEditor

        slotTable.fillsViewportHeight = true
        slotTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        scheduleTable.fillsViewportHeight = true
        scheduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        confirmedTable.fillsViewportHeight = true
        confirmedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        configureHiddenIdColumn()

        val controls = buildControls()
        val center = buildCenterPanels()

        add(controls, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)

        eventDropdown.addActionListener { onEventSelected() }
        refreshButton.addActionListener { refreshData() }
        clearButton.addActionListener { clearTransientTables() }
        findSlotButton.addActionListener { onFindSlot() }
        buildScheduleButton.addActionListener { onBuildSchedule() }
        confirmSlotButton.addActionListener { confirmSelectedSlot() }
        removeConfirmedButton.addActionListener { removeSelectedConfirmed() }

        refreshData()
    }

    private fun buildControls(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        gbc.gridy = 0
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(JLabel("Event:"), gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        panel.add(eventDropdown, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.0
        panel.add(JLabel("Planned size:"), gbc)
        gbc.gridx = 3
        panel.add(plannedSizeSpinner, gbc)

        gbc.gridx = 4
        panel.add(JLabel("Earliest date:"), gbc)
        gbc.gridx = 5
        panel.add(earliestDateSpinner, gbc)

        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.0
        panel.add(refreshButton, gbc)
        gbc.gridx = 1
        panel.add(findSlotButton, gbc)
        gbc.gridx = 2
        panel.add(buildScheduleButton, gbc)
        gbc.gridx = 3
        panel.add(clearButton, gbc)

        return panel
    }

    private fun buildCenterPanels(): JComponent {
        val container = JPanel()
        container.layout = BoxLayout(container, BoxLayout.Y_AXIS)

        val slotPanel = JPanel(BorderLayout(6, 6))
        slotPanel.border = BorderFactory.createTitledBorder("Slot suggestions")
        slotPanel.add(JScrollPane(slotTable), BorderLayout.CENTER)
        slotPanel.add(confirmSlotButton, BorderLayout.SOUTH)

        val schedulePanel = JPanel(BorderLayout(6, 6))
        schedulePanel.border = BorderFactory.createTitledBorder("Generated schedule preview")
        schedulePanel.add(JScrollPane(scheduleTable), BorderLayout.CENTER)

        val confirmedPanel = JPanel(BorderLayout(6, 6))
        confirmedPanel.border = BorderFactory.createTitledBorder("Confirmed schedules")
        confirmedPanel.add(JScrollPane(confirmedTable), BorderLayout.CENTER)
        confirmedPanel.add(removeConfirmedButton, BorderLayout.SOUTH)

        container.add(slotPanel)
        container.add(Box.createVerticalStrut(10))
        container.add(schedulePanel)
        container.add(Box.createVerticalStrut(10))
        container.add(confirmedPanel)

        return container
    }

    private fun configureHiddenIdColumn() {
        val idColumn = confirmedTable.columnModel.getColumn(0)
        idColumn.minWidth = 0
        idColumn.maxWidth = 0
        idColumn.preferredWidth = 0
        idColumn.width = 0
    }

    private fun refreshData() {
        refreshEventDropdown()
        loadConfirmedSchedules()
        clearTransientTables()
    }

    private fun refreshEventDropdown() {
        val previousId = (eventDropdown.selectedItem as? EventOption)?.event?.id
        val events = AppContext.eventService.reload()
            .sortedWith(compareBy({ it.date }, { it.startTime }, { it.title }))
        val model = DefaultComboBoxModel<EventOption>()
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
        val today = LocalDate.now()
        val baselineDate = if (option.event.date.isAfter(today)) option.event.date else today
        earliestDateSpinner.value = Date.from(baselineDate.atStartOfDay(zoneId).toInstant())
    }

    private fun onFindSlot() {
        try {
            val option = eventDropdown.selectedItem as? EventOption
            if (option == null) {
                JOptionPane.showMessageDialog(this, "Add an event before searching for slots.")
                return
            }

            clearSlotTable()

            val planned = plannedSizeSpinner.value as Int
            val earliest = (earliestDateSpinner.value as Date)
                .toInstant().atZone(zoneId).toLocalDate()

            val eventsList = AppContext.eventService.reload()
            val venuesList = AppContext.venueService.reload()
            val registrationsList = AppContext.registrationService.reload()
            val participantsList = AppContext.participantService.reload()

            val suggestions = SlotFinder.proposeSlots(
                java.util.ArrayList(eventsList),
                java.util.ArrayList(venuesList),
                java.util.ArrayList(registrationsList),
                java.util.ArrayList(participantsList),
                planned,
                earliest
            ).toList()

            currentSuggestions = suggestions

            if (suggestions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No suitable venue found from $earliest for size $planned.")
            } else {
                val venueMap = venuesList.associateBy { it.id }
                suggestions.forEachIndexed { index, suggestion ->
                    val venueName = suggestion.venueId?.let { venueMap[it]?.name } ?: "(venue TBD)"
                    val rankLabel = if (index == 0) "⭐ #1" else "#${index + 1}"
                    slotTableModel.addRow(
                        arrayOf(
                            rankLabel,
                            venueName,
                            suggestion.date.toString(),
                            suggestion.startTime.toString(),
                            suggestion.endTime.toString(),
                            "${"%.0f".format(suggestion.confidence * 100)}%",
                            suggestion.note
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
        val eventId = confirmedTableModel.getValueAt(row, 0) as String
        AppContext.scheduledEventService.removeSchedule(eventId)
        loadConfirmedSchedules()
        refreshEventDropdown()
    }

    private fun onBuildSchedule() {
        try {
            clearScheduleTable()

            val eventsList = AppContext.eventService.reload()
            val venuesList = AppContext.venueService.reload()
            val registrationsList = AppContext.registrationService.reload()

            val preferenceScores = java.util.HashMap<String, Double>()
            val registrationsByEvent = registrationsList.groupBy { it.eventId }

            eventsList.forEach { event ->
                val registeredCount = registrationsByEvent[event.id]?.size ?: 0
                val demandRatio = if (event.expectedSize > 0) registeredCount.toDouble() / event.expectedSize else 0.0
                val score = event.expectedSize * 0.25 + registeredCount * 1.5 + demandRatio
                preferenceScores[event.id] = score
            }

            val result = Scheduler.buildOptimizedSchedule(
                java.util.ArrayList(eventsList),
                java.util.ArrayList(venuesList),
                java.util.ArrayList(registrationsList),
                preferenceScores
            )

            currentScheduleResults = result.toList()

            val evMap = eventsList.associateBy { it.id }
            val venueMap = venuesList.associateBy { it.id }
            var confirmedCount = 0
            var clearedCount = 0

            currentScheduleResults.forEach { r ->
                val venueName = r.venueId.takeIf { it.isNotBlank() }?.let { venueMap[it]?.name }
                    ?: if (r.scheduled) "(venue TBD)" else "(not scheduled)"
                val title = evMap[r.eventId]?.title ?: r.eventId

                scheduleTableModel.addRow(
                    arrayOf(
                        title,
                        r.assignedDate.toString(),
                        r.startTime.toString(),
                        r.endTime.toString(),
                        venueName,
                        "${"%.0f".format(r.confidence * 100)}%",
                        r.note
                    )
                )

                if (r.scheduled) {
                    try {
                        AppContext.scheduledEventService.confirmSchedule(
                            r.eventId,
                            r.assignedDate,
                            r.startTime,
                            r.endTime,
                            r.venueId.takeIf { it.isNotBlank() }
                        )
                        confirmedCount++
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        JOptionPane.showMessageDialog(this, "Unable to confirm schedule for $title: ${ex.message}")
                    }
                } else {
                    AppContext.scheduledEventService.removeSchedule(r.eventId)
                    clearedCount++
                }
            }

            loadConfirmedSchedules()
            refreshEventDropdown()

            if (currentScheduleResults.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No schedule could be generated with current data.")
            } else if (confirmedCount > 0 || clearedCount > 0) {
                val parts = mutableListOf<String>()
                if (confirmedCount > 0) parts += "confirmed $confirmedCount event(s)"
                if (clearedCount > 0) parts += "cleared $clearedCount event(s)"
                if (parts.isNotEmpty()) {
                    JOptionPane.showMessageDialog(this, "Schedule builder ${parts.joinToString(" and ")}.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
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
                        record.date.toString(),
                        record.startTime.toString(),
                        record.endTime.toString(),
                        venueName,
                        record.confirmedAt.toString()
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
}
