package app.ui

import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * UI for adding, deleting, and viewing venues that events can be booked into.
 */
class VenueFormPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout(15, 15)) {

    private val nameField = JTextField(12)
    private val capacityField = JSpinner(SpinnerNumberModel(10, 1, 10000, 1))
    private val cityField = JTextField(12)

    private val addButton = JButton("Add")
    private val deleteButton = JButton("Delete")
    private val refreshButton = JButton("Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "Location", "Capacity"), 0)
    private val table = JTable(tableModel)

    init {
        background = UiTheme.backgroundColor
        border = EmptyBorder(20, 20, 20, 20)

        val formCard = UiTheme.createCard(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Venue Details"),
                EmptyBorder(15, 20, 15, 20)
            )
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        fun JComponent.compact(): JComponent = apply {
            preferredSize = Dimension(180, 28)
        }

        fun JComponent.tight(): JComponent = apply {
            preferredSize = Dimension(120, 28)
        }

        var row = 0
        fun addRow(label: String, component: JComponent, tight: Boolean = false) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            formCard.add(UiTheme.styleLabel(JLabel(label), bold = true), gbc)

            gbc.gridx = 1
            gbc.weightx = if (tight) 0.0 else 1.0
            gbc.fill = if (tight) GridBagConstraints.NONE else GridBagConstraints.HORIZONTAL
            formCard.add(if (tight) component.tight() else component.compact(), gbc)
            gbc.fill = GridBagConstraints.HORIZONTAL
            row++
        }

        addRow("Venue Name:", nameField)
        addRow("Capacity:", capacityField, tight = true)
        addRow("Location:", cityField)

        val buttonPanel = UiTheme.createButtonRow(addButton, deleteButton, refreshButton)

        val formWrapper = JPanel(BorderLayout()).apply {
            background = UiTheme.backgroundColor
            add(formCard, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        UiTheme.styleTable(table)
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

        val scrollPane = JScrollPane(table).apply {
            border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Saved Venues")
            preferredSize = Dimension(0, 220)
            background = UiTheme.cardColor
            viewport.background = java.awt.Color.WHITE
        }

        add(formWrapper, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        addButton.addActionListener { addVenue() }
        deleteButton.addActionListener { deleteSelectedVenue() }
        refreshButton.addActionListener { refreshTable() }

        refreshTable()
    }

    /**
     * Validate and persist a new venue, then refresh the table so users get immediate feedback.
     */
    private fun addVenue() {
        try {
            val name = nameField.text.trim()
            val city = cityField.text.trim()
            val capacity = (capacityField.value as Number).toInt()

            if (name.isEmpty() || city.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields")
                return
            }

            AppContext.venueService.addVenue(name, capacity, city)
            JOptionPane.showMessageDialog(this, "Venue '$name' added.")
            clearForm()
            refreshTable()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    /** Remove the highlightted venue row after a simple confirmation step. */
    private fun deleteSelectedVenue() {
        val row = table.selectedRow
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a venue to delete")
            return
        }
        val id = tableModel.getValueAt(row, 0) as String
        val name = tableModel.getValueAt(row, 1) as String
        AppContext.venueService.deleteVenueById(id)
        JOptionPane.showMessageDialog(this, "Venue '$name' removed.")
        refreshTable()
        onDataChanged?.invoke()
    }

    /** Reload venue data from storage into the table. */
    private fun refreshTable() {
        val venues = AppContext.venueService.all()
        tableModel.setRowCount(0)
        venues.forEach {
            tableModel.addRow(arrayOf<Any?>(it.id, it.name, it.city, it.capacity))
        }
    }

    /** Return input controls to their default state. */
    private fun clearForm() {
        nameField.text = ""
        cityField.text = ""
        capacityField.value = 10
    }
}
