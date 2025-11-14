package app.ui

import app.AppContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
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

class VenueFormPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout(10, 10)) {

    private val nameField = JTextField(16)
    private val capacityField = JSpinner(SpinnerNumberModel(10, 1, 10000, 1))
    private val cityField = JTextField(16)

    private val addButton = JButton("Add")
    private val deleteButton = JButton("Delete")
    private val refreshButton = JButton("Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "Location", "Capacity"), 0)
    private val table = JTable(tableModel)

    init {
        border = EmptyBorder(12, 12, 12, 12)

        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        var row = 0
        fun addRow(label: String, component: java.awt.Component) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            formPanel.add(JLabel(label), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            formPanel.add(component, gbc)
            row++
        }

        addRow("Venue Name:", nameField)
        addRow("Capacity:", capacityField)
        addRow("Location:", cityField)

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(addButton)
            add(deleteButton)
            add(refreshButton)
        }

        val top = JPanel(BorderLayout(6, 6)).apply {
            add(formPanel, BorderLayout.CENTER)
            add(buttonRow, BorderLayout.SOUTH)
        }

        val scrollPane = JScrollPane(table)
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

        add(top, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        addButton.addActionListener { addVenue() }
        deleteButton.addActionListener { deleteSelectedVenue() }
        refreshButton.addActionListener { refreshTable() }

        refreshTable()
    }

    private fun addVenue() {
        val name = nameField.text.trim()
        val city = cityField.text.trim()
        val capacity = (capacityField.value as Number).toInt()

        if (name.isEmpty() || city.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields")
            return
        }

        try {
            AppContext.venueService.addVenue(name, capacity, city)
            JOptionPane.showMessageDialog(this, "Venue '$name' added")
            clearForm()
            refreshTable()
            onDataChanged?.invoke()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    private fun deleteSelectedVenue() {
        val row = table.selectedRow
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a venue to delete")
            return
        }
        val modelRow = table.convertRowIndexToModel(row)
        val id = tableModel.getValueAt(modelRow, 0) as String
        val name = tableModel.getValueAt(modelRow, 1) as String
        AppContext.venueService.deleteVenueById(id)
        JOptionPane.showMessageDialog(this, "Venue '$name' removed")
        refreshTable()
        onDataChanged?.invoke()
    }

    private fun refreshTable() {
        val venues = AppContext.venueService.all()
        tableModel.setRowCount(0)
        venues.forEach {
            tableModel.addRow(arrayOf<Any?>(it.id, it.name, it.city, it.capacity))
        }
    }

    private fun clearForm() {
        nameField.text = ""
        cityField.text = ""
        capacityField.value = 10
    }
}
