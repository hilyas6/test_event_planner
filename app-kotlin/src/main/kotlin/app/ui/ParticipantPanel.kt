package app.ui

import app.AppContext
import java.awt.*
import java.time.LocalDate
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ParticipantPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

    private val firstNameField = JTextField(15)
    private val lastNameField = JTextField(15)
    private val dobField = JTextField("2000-01-01")
    private val phoneField = JTextField(15)
    private val emailField = JTextField(20)

    private val addButton = JButton("➕ Add")
    private val deleteButton = JButton("🗑 Delete")
    private val refreshButton = JButton("🔄 Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "First", "Last", "DOB", "Phone", "Email"), 0)
    private val table = JTable(tableModel)

    init {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.add(addButton)
        toolbar.add(deleteButton)
        toolbar.add(refreshButton)

        val form = JPanel(GridLayout(0, 2, 5, 5))
        form.border = BorderFactory.createTitledBorder("Add Participant")

        form.add(JLabel("First Name:")); form.add(firstNameField)
        form.add(JLabel("Last Name:")); form.add(lastNameField)
        form.add(JLabel("Date of Birth (YYYY-MM-DD):")); form.add(dobField)
        form.add(JLabel("Phone:")); form.add(phoneField)
        form.add(JLabel("Email:")); form.add(emailField)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createTitledBorder("All Participants")

        add(toolbar, BorderLayout.NORTH)
        add(form, BorderLayout.CENTER)
        add(scrollPane, BorderLayout.SOUTH)

        addButton.addActionListener { addParticipant() }
        deleteButton.addActionListener { deleteParticipant() }
        refreshButton.addActionListener { refreshTable() }

        refreshTable()
    }

    private fun addParticipant() {
        try {
            AppContext.participantService.addParticipant(
                firstNameField.text,
                lastNameField.text,
                LocalDate.parse(dobField.text),
                phoneField.text,
                emailField.text
            )
            JOptionPane.showMessageDialog(this, "✅ Added participant ${firstNameField.text}")
            refreshTable()
            clear()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    private fun deleteParticipant() {
        val row = table.selectedRow
        if (row < 0) return
        val id = tableModel.getValueAt(row, 0) as String
        AppContext.participantService.deleteParticipantById(id)
        refreshTable()
        onDataChanged?.invoke()
    }

    private fun refreshTable() {
        val list = AppContext.participantService.all()
        tableModel.setRowCount(0)
        list.forEach { p ->
            tableModel.addRow(arrayOf(p.id, p.firstName, p.lastName, p.dateOfBirth, p.phone, p.email))
        }
    }

    private fun clear() {
        firstNameField.text = ""
        lastNameField.text = ""
        dobField.text = "2000-01-01"
        phoneField.text = ""
        emailField.text = ""
    }
}
