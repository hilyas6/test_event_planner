package app.ui

import app.AppContext
import core.model.Venue
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class VenueFormPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

    private val nameField = JTextField(15)
    private val capacityField = JSpinner(SpinnerNumberModel(100, 1, 10000, 1))
    private val cityField = JTextField(15)

    private val addButton = JButton("➕ Add")
    private val deleteButton = JButton("🗑 Delete")
    private val refreshButton = JButton("🔄 Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "City", "Capacity"), 0)
    private val table = JTable(tableModel)

    init {
        // Top toolbar
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.add(addButton)
        toolbar.add(deleteButton)
        toolbar.add(refreshButton)

        val formPanel = JPanel(GridLayout(0, 2, 5, 5))
        formPanel.border = BorderFactory.createTitledBorder("Add New Venue")
        formPanel.add(JLabel("Venue Name:")); formPanel.add(nameField)
        formPanel.add(JLabel("Capacity:")); formPanel.add(capacityField)
        formPanel.add(JLabel("City:")); formPanel.add(cityField)

        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createTitledBorder("All Venues")

        add(toolbar, BorderLayout.NORTH)
        add(formPanel, BorderLayout.CENTER)
        add(scrollPane, BorderLayout.SOUTH)

        addButton.addActionListener { addVenue() }
        deleteButton.addActionListener { deleteSelectedVenue() }
        refreshButton.addActionListener { refreshTable() }

        refreshTable()
    }

    private fun addVenue() {
        try {
            val name = nameField.text.trim()
            val city = cityField.text.trim()
            val capacity = (capacityField.value as Int)

            if (name.isEmpty() || city.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill all fields")
                return
            }

            AppContext.venueService.addVenue(name, capacity, city)
            JOptionPane.showMessageDialog(this, "✅ Venue '$name' added!")
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
        val id = tableModel.getValueAt(row, 0) as String
        val name = tableModel.getValueAt(row, 1) as String
        val allVenues = AppContext.venueService.all().toMutableList()
        allVenues.removeIf { it.id == id }
        AppContext.venueService.deleteVenueById(id)
        JOptionPane.showMessageDialog(this, "🗑 Venue '$name' removed!")
        refreshTable()
        onDataChanged?.invoke()
    }

    private fun refreshTable() {
        val venues = AppContext.venueService.all()
        tableModel.setRowCount(0)
        venues.forEach {
            tableModel.addRow(arrayOf(it.id, it.name, it.city, it.capacity))
        }
    }

    private fun clearForm() {
        nameField.text = ""
        cityField.text = ""
        capacityField.value = 100
    }
}
