package app.ui

import algo.ScheduleResult
import algo.Scheduler
import algo.SlotFinder
import algo.SlotSuggestion
import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class SchedulePanel : JPanel(BorderLayout(10, 10)) {

    private data class EventOption(val event: core.model.Event) {
        override fun toString(): String {
            val dateText = "${event.date} ${event.startTime}-${event.endTime}"
            return "${event.title} ($dateText)"
        }
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val eventDropdown = JComboBox<EventOption>()
    private val plannedSizeSpinner = JSpinner(SpinnerNumberModel(50, 1, 100000, 1))
    private val earliestDateField = DateField(LocalDate.now())
    private val refreshButton = JButton("Refresh")
    private val findSlotButton = JButton("Find Slot")
    private val buildScheduleButton = JButton("Build Schedule")
    private val clearButton = JButton("Clear")
    private val confirmSlotButton = JButton("Confirm Slot")
    private val confirmScheduleButton = JButton("Confirm Selection")
    private val removeConfirmedButton = JButton("Remove Selected")

    private val slotTableModel = object : DefaultTableModel(
        arrayOf("Rank", "Venue", "Date", "Start", "End"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    private val slotTable = JTable(slotTableModel)

    private val scheduleTableModel = object : DefaultTableModel(
        arrayOf("Event", "Date", "Start", "End", "Venue"),
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
        border = EmptyBorder(12, 12, 12, 12)

        configureTables()

        val content = JPanel()
        content.layout = javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS)
        listOf(
            buildControlsCard(),
            createTableCard("Slot Suggestions", slotTable, confirmSlotButton),
            createTableCard("Generated Schedule Preview", scheduleTable, confirmScheduleButton),
            createTableCard("Confirmed Events", confirmedTable, removeConfirmedButton)
        ).forEachIndexed { index, card ->
            content.add(card)
            if (index < 3) {
                content.add(javax.swing.Box.createVerticalStrut(12))
            }
        }

        add(JScrollPane(content), BorderLayout.CENTER)

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
            table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            table.autoCreateRowSorter = true
        }
        val idColumn = confirmedTable.columnModel.getColumn(0)
        idColumn.minWidth = 0
        idColumn.maxWidth = 0
        idColumn.preferredWidth = 0
    }

    private fun buildControlsCard(): JPanel {
        val card = JPanel(GridBagLayout())
        card.border = javax.swing.BorderFactory.createTitledBorder("Scheduling Options")
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        var row = 0
        fun addRow(label: String, component: java.awt.Component) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            card.add(JLabel(label), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            card.add(component, gbc)
            row++
        }

        eventDropdown.preferredSize = Dimension(260, 28)
        plannedSizeSpinner.preferredSize = Dimension(120, 28)
        earliestDateField.spinner.preferredSize = Dimension(150, 28)

        addRow("Event:", eventDropdown)
        addRow("Planned size:", plannedSizeSpinner)
        addRow("Earliest date:", earliestDateField.component)

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(refreshButton)
            add(findSlotButton)
            add(buildScheduleButton)
            add(clearButton)
        }

        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        card.add(buttonRow, gbc)

        return card
    }

    private fun createTableCard(title: String, table: JTable, actionButton: JButton): JPanel {
        val card = JPanel(BorderLayout(6, 6))
        card.border = javax.swing.BorderFactory.createTitledBorder(title)
        val scroll = JScrollPane(table)
        scroll.preferredSize = Dimension(0, 220)
        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(actionButton)
        }
        card.add(scroll, BorderLayout.CENTER)
        card.add(buttonRow, BorderLayout.SOUTH)
        return card
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
        earliestDateField.date = baselineDate
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
            val earliest = earliestDateField.date

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

            val limited = suggestions.take(3)
            currentSuggestions = limited

            if (limited.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No suitable venue found from ${earliest.format(dateFormatter)} for size $planned.")
            } else {
                val venueMap = venuesList.associateBy { it.id }
                limited.forEachIndexed { index, suggestion ->
                    val venueName = suggestion.venueId?.let { venueMap[it]?.name } ?: "(venue TBD)"
                    val rankLabel = if (index == 0) "#1" else "#${index + 1}"
                    slotTableModel.addRow(
                        arrayOf(
                            rankLabel,
                            venueName,
                            suggestion.date.format(dateFormatter),
                            suggestion.startTime.toString(),
                            suggestion.endTime.toString()
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
        val modelRow = confirmedTable.convertRowIndexToModel(row)
        val eventId = confirmedTableModel.getValueAt(modelRow, 0) as String
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

            currentScheduleResults.forEach { r ->
                val venueName = r.venueId.takeIf { it.isNotBlank() }?.let { venueMap[it]?.name }
                    ?: if (r.scheduled) "(venue TBD)" else "(not scheduled)"
                val title = evMap[r.eventId]?.title ?: r.eventId

                scheduleTableModel.addRow(
                    arrayOf(
                        title,
                        r.assignedDate.format(dateFormatter),
                        r.startTime.toString(),
                        r.endTime.toString(),
                        venueName
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
            JOptionPane.showMessageDialog(this, "Schedule confirmed.")
            loadConfirmedSchedules()
            refreshEventDropdown()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Unable to confirm schedule: ${e.message}")
        }
    }

    private fun loadConfirmedSchedules() {
        confirmedTableModel.rowCount = 0
        val events = AppContext.eventService.reload().associateBy { it.id }
        val venues = AppContext.venueService.reload().associateBy { it.id }
        AppContext.scheduledEventService.reload()
            .sortedWith(compareBy({ it.date }, { it.startTime }))
            .forEach { schedule ->
                val event = events[schedule.eventId]
                val venueName = schedule.venueId?.let { venues[it]?.name } ?: "TBC"
                confirmedTableModel.addRow(
                    arrayOf(
                        schedule.eventId,
                        event?.title ?: schedule.eventId,
                        schedule.date.format(dateFormatter),
                        schedule.startTime.toString(),
                        schedule.endTime.toString(),
                        venueName,
                        schedule.confirmedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                )
            }
    }

    private fun clearSlotTable() {
        slotTableModel.rowCount = 0
        currentSuggestions = emptyList()
    }

    private fun clearScheduleTable() {
        scheduleTableModel.rowCount = 0
        currentScheduleResults = emptyList()
    }

    private fun clearTransientTables() {
        clearSlotTable()
        clearScheduleTable()
    }

    fun refreshFromOutside() {
        refreshData()
    }
}
