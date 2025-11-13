package app.ui

import algo.Scheduler
import algo.SlotFinder
import app.AppContext
import java.awt.*
import java.time.LocalDate
import javax.swing.*
import javax.swing.table.DefaultTableModel

class SchedulePanel : JPanel(BorderLayout()) {

    // Inputs
    private val plannedSizeSpinner = JSpinner(SpinnerNumberModel(50, 1, 100000, 1))
    private val earliestDateSpinner = JSpinner(SpinnerDateModel(java.util.Date(), null, null, java.util.Calendar.DAY_OF_MONTH))
    private val refreshButton = JButton("↻ Refresh")
    private val findSlotButton = JButton("🔍 Find Slot")
    private val buildScheduleButton = JButton("🧩 Build Schedule")
    private val clearButton = JButton("🗑 Clear")

    // Tables
    private val slotTableModel = DefaultTableModel(arrayOf("Venue", "Date", "Start", "End", "Confidence", "Notes"), 0)
    private val slotTable = JTable(slotTableModel)

    private val scheduleTableModel = DefaultTableModel(arrayOf("Event", "Date", "Start", "End", "Venue", "Confidence", "Notes"), 0)
    private val scheduleTable = JTable(scheduleTableModel)

    init {
        // Top controls
        val top = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
        }
        // Date editor (calendar-like spinner)
        val dateEditor = JSpinner.DateEditor(earliestDateSpinner, "yyyy-MM-dd")
        earliestDateSpinner.editor = dateEditor

        var col = 0
        fun addTop(label: String, comp: JComponent) {
            gbc.gridx = col; gbc.gridy = 0
            top.add(JLabel(label), gbc)
            gbc.gridx = col + 1
            top.add(comp, gbc)
            col += 2
        }

        addTop("Planned size:", plannedSizeSpinner)
        addTop("Earliest date:", earliestDateSpinner)
        gbc.gridx = col; gbc.gridy = 0; top.add(refreshButton, gbc); col++
        gbc.gridx = col; top.add(findSlotButton, gbc); col++
        gbc.gridx = col; top.add(buildScheduleButton, gbc); col++
        gbc.gridx = col; top.add(clearButton, gbc)

        // Center: two tables stacked
        val center = JPanel(GridLayout(2, 1, 8, 8))
        val slotPanel = JPanel(BorderLayout())
        slotPanel.add(JLabel("Suggested Slot"), BorderLayout.NORTH)
        slotPanel.add(JScrollPane(slotTable), BorderLayout.CENTER)

        val schedulePanel = JPanel(BorderLayout())
        schedulePanel.add(JLabel("Conflict-free Schedule"), BorderLayout.NORTH)
        schedulePanel.add(JScrollPane(scheduleTable), BorderLayout.CENTER)

        center.add(slotPanel)
        center.add(schedulePanel)

        add(top, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)

        // Actions
        refreshButton.addActionListener { refreshTables() }
        clearButton.addActionListener { clearTables() }
        findSlotButton.addActionListener { onFindSlot() }
        buildScheduleButton.addActionListener { onBuildSchedule() }

        refreshTables()
    }

    private fun onFindSlot() {
        try {
            clearSlotTable()

            val planned = (plannedSizeSpinner.value as Int)
            val earliest = (earliestDateSpinner.value as java.util.Date)
                .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()

            // Pull current data from services
            val events = java.util.ArrayList(AppContext.eventService.all())
            val venues = java.util.ArrayList(AppContext.venueService.all())
            val registrations = java.util.ArrayList(AppContext.registrationService.all())
            val participants = java.util.ArrayList(AppContext.participantService.all())

            val suggestions = SlotFinder.proposeSlots(events, venues, registrations, participants, planned, earliest)

            if (suggestions.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No suitable venue found from $earliest for size $planned.")
            } else {
                val venueMap = AppContext.venueService.all().associateBy { it.id }
                suggestions.forEach { suggestion ->
                    val venueName = venueMap[suggestion.venueId]?.name ?: "(unassigned venue)"
                    slotTableModel.addRow(
                        arrayOf(
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

    private fun onBuildSchedule() {
        try {
            clearScheduleTable()

            val events = java.util.ArrayList(AppContext.eventService.all())
            val venues = java.util.ArrayList(AppContext.venueService.all())
            val registrations = java.util.ArrayList(AppContext.registrationService.all())

            val preferenceScores = mutableMapOf<String, Double>()
            val registrationsByEvent = registrations.groupBy { it.eventId }

            AppContext.eventService.all().forEach { event ->
                val registeredCount = registrationsByEvent[event.id]?.size ?: 0
                val basePriority = if (event.priority > 0) event.priority else event.expectedSize
                val interestBoost = registeredCount * 2.0
                val demandRatio = if (event.expectedSize > 0) registeredCount.toDouble() / event.expectedSize else 0.0
                val score = basePriority + interestBoost + demandRatio
                preferenceScores[event.id] = score
            }

            val result = Scheduler.buildOptimizedSchedule(events, venues, registrations, preferenceScores)

            val evMap = AppContext.eventService.all().associateBy { it.id }
            val vnMap = AppContext.venueService.all().associateBy { it.id }

            result.forEach { r ->
                val event = evMap[r.eventId]
                val venueName = vnMap[r.venueId]?.name ?: if (r.scheduled) "(venue TBD)" else "(not scheduled)"
                val title = event?.title ?: r.eventId
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
            }

            if (result.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No schedule could be generated with current data.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    /** Reload data-driven tables (useful after adding/deleting items elsewhere) */
    private fun refreshTables() {
        // We don’t persist schedules: just clear them;
        // users can click Build again, or Find Slot again
        clearTables()
    }

    private fun clearTables() {
        clearSlotTable()
        clearScheduleTable()
    }
    private fun clearSlotTable() = run { slotTableModel.rowCount = 0 }
    private fun clearScheduleTable() = run { scheduleTableModel.rowCount = 0 }
}
