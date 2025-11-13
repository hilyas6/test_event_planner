package app.ui

import app.AppContext
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.*

class RegistrationPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

    private data class EventOption(
        val event: core.model.Event,
        val schedule: core.model.ScheduledEvent,
        val venueName: String,
        val remaining: Int,
        val capacity: Int
    ) {
        override fun toString(): String {
            val timeRange = "${schedule.startTime}-${schedule.endTime}"
            val venueDisplay = if (venueName.isBlank()) "Venue TBD" else venueName
            return "${event.title} (${schedule.date} $timeRange @ $venueDisplay, $remaining/$capacity spots left)"
        }
    }

    private data class ParticipantOption(val participant: core.model.Participant) {
        override fun toString(): String = "${participant.firstName} ${participant.lastName}"
    }

    private val eventDropdown = JComboBox<EventOption>()
    private val participantDropdown = JComboBox<ParticipantOption>()

    private val registerButton = JButton("✅ Register")
    private val deleteButton = JButton("🗑 Delete")
    private val refreshButton = JButton("🔄 Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "Participant", "Event", "Registered At"), 0)
    private val table = JTable(tableModel)

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

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createTitledBorder("Registrations")

        add(toolbar, BorderLayout.NORTH)
        add(form, BorderLayout.CENTER)
        add(scrollPane, BorderLayout.SOUTH)

        registerButton.addActionListener { register() }
        deleteButton.addActionListener { deleteReg() }
        refreshButton.addActionListener { refreshAll() }

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

        try {
            AppContext.registrationService.register(eventOption.event.id, participantOption.participant.id)
            JOptionPane.showMessageDialog(this, "✅ Registered ${participantOption.participant.firstName} ${participantOption.participant.lastName} for ${eventOption.event.title}")
            refreshAll()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message ?: "Unable to register")
        }
    }

    private fun deleteReg() {
        val row = table.selectedRow
        if (row < 0) return
        val id = tableModel.getValueAt(row, 0) as String
        AppContext.registrationService.deleteRegistrationById(id)
        refreshAll()
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
                val startDateTime = java.time.LocalDateTime.of(schedule.date, schedule.startTime)
                if (startDateTime.isBefore(now)) return@mapNotNull null
                val remaining = AppContext.registrationService.remainingCapacity(event)
                if (remaining <= 0) return@mapNotNull null
                val capacity = event.expectedSize
                val venueName = schedule.venueId?.let { venueMap[it]?.name } ?: ""
                EventOption(event, schedule, venueName, remaining, capacity)
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
            eventDropdown.toolTipText = "No upcoming scheduled events with available capacity"
        } else {
            eventDropdown.isEnabled = true
            eventDropdown.toolTipText = null
        }

        if (participantModel.size == 0) {
            participantDropdown.isEnabled = false
            participantDropdown.toolTipText = "Add participants before registering"
        } else {
            participantDropdown.isEnabled = true
            participantDropdown.toolTipText = null
        }
    }

    private fun refreshTable() {
        val regs = AppContext.registrationService.reload()
        val participants = AppContext.participantService.reload()
        val events = AppContext.eventService.reload()

        tableModel.rowCount = 0
        regs.forEach { reg ->
            val p = participants.find { it.id == reg.participantId }
            val e = events.find { it.id == reg.eventId }
            if (p != null && e != null) {
                tableModel.addRow(arrayOf(reg.id, "${p.firstName} ${p.lastName}", e.title, reg.registeredAt))
            }
        }
    }
}
