package app.ui

import app.AppContext
import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.*

class RegistrationPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

    private val eventDropdown = JComboBox<String>()
    private val participantDropdown = JComboBox<String>()

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
        val eventTitle = eventDropdown.selectedItem as? String ?: return
        val participantName = participantDropdown.selectedItem as? String ?: return

        val event = AppContext.eventService.all().find { it.title == eventTitle }
        val participant = AppContext.participantService.all().find {
            "${it.firstName} ${it.lastName}" == participantName
        }
        if (event == null || participant == null) {
            JOptionPane.showMessageDialog(this, "Invalid selection")
            return
        }

        AppContext.registrationService.register(event.id, participant.id)
        JOptionPane.showMessageDialog(this, "✅ Registered $participantName for $eventTitle")
        refreshAll()
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
        eventDropdown.removeAllItems()
        participantDropdown.removeAllItems()

        val events = AppContext.eventService.reload()
        val participants = AppContext.participantService.reload()

        if (events.isEmpty()) eventDropdown.addItem("No events available")
        else events.forEach { eventDropdown.addItem(it.title) }

        if (participants.isEmpty()) participantDropdown.addItem("No participants available")
        else participants.forEach { participantDropdown.addItem("${it.firstName} ${it.lastName}") }
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
