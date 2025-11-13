package app.ui

import app.AppContext
import java.awt.*
import java.time.LocalDate
import java.time.LocalTime
import javax.swing.*
import javax.swing.table.DefaultTableModel

class EventFormPanel : JPanel(BorderLayout()) {

    private val titleField = JTextField(30)
    private val descriptionField = JTextField(30)
    private val categoryField = JTextField(20)
    private val datePicker = JSpinner(SpinnerDateModel())
    private val startTimeBox = JComboBox((6..22).map { "%02d:00".format(it) }.toTypedArray())
    private val endTimeBox = JComboBox((7..23).map { "%02d:00".format(it) }.toTypedArray())
    private val expectedSizeField = JSpinner(SpinnerNumberModel(50, 1, 10000, 1))
    private val organiserNameField = JTextField(25)
    private val organiserEmailField = JTextField(25)

    private val addButton = JButton("➕ Add Event")
    private val deleteButton = JButton("🗑️ Delete Selected")
    private val refreshButton = JButton("↻ Refresh List")

    private val tableModel = DefaultTableModel(
        arrayOf("Title", "Date", "Start", "End", "Expected Size", "Venue"),
        0
    )
    private val eventTable = JTable(tableModel)

    init {
        layout = BorderLayout(10, 10)

        val formPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 5)
            weightx = 1.0
        }

        var row = 0
        fun addRow(label: String, comp: JComponent) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            formPanel.add(JLabel(label), gbc)
            gbc.gridx = 1
            gbc.weightx = 1.0
            formPanel.add(comp, gbc)
            row++
        }

        val dateEditor = JSpinner.DateEditor(datePicker, "yyyy-MM-dd")
        datePicker.editor = dateEditor

        addRow("Title:", titleField)
        addRow("Description:", descriptionField)
        addRow("Category:", categoryField)
        addRow("Date:", datePicker)
        addRow("Start Time:", startTimeBox)
        addRow("End Time:", endTimeBox)
        addRow("Expected Size:", expectedSizeField)
        addRow("Organiser Name:", organiserNameField)
        addRow("Organiser Email:", organiserEmailField)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(addButton)
        buttonPanel.add(deleteButton)
        buttonPanel.add(refreshButton)

        val tableScroll = JScrollPane(eventTable)
        eventTable.fillsViewportHeight = true
        eventTable.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS

        add(formPanel, BorderLayout.NORTH)
        add(tableScroll, BorderLayout.CENTER)
        add(buttonPanel, BorderLayout.SOUTH)

        loadEvents()

        addButton.addActionListener { onAddEvent() }
        deleteButton.addActionListener { onDeleteEvent() }
        refreshButton.addActionListener { loadEvents() }
    }

    private fun loadEvents() {
        tableModel.rowCount = 0
        val events = AppContext.eventService.all()
        events.forEach {
            val venueName = "Unassigned"
            tableModel.addRow(
                arrayOf(it.title, it.date, it.startTime, it.endTime, it.expectedSize, venueName)
            )
        }
    }

    private fun onAddEvent() {
        try {
            val date = (datePicker.value as java.util.Date)
                .toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val start = LocalTime.parse(startTimeBox.selectedItem as String)
            val end = LocalTime.parse(endTimeBox.selectedItem as String)

            // No venue selected now, pass blank string
            AppContext.eventService.addEvent(
                titleField.text,
                date,
                start,
                end,
                (expectedSizeField.value as Int),
                "" // venueId left empty for now
            )

            JOptionPane.showMessageDialog(this, "✅ Event added successfully!")
            clearForm()
            loadEvents()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "⚠️ Error: ${e.message}")
        }
    }

    private fun onDeleteEvent() {
        val selectedRow = eventTable.selectedRow
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select an event to delete.")
            return
        }
        val title = tableModel.getValueAt(selectedRow, 0) as String
        val events = AppContext.eventService.all().toMutableList()
        val toDelete = events.find { it.title == title }
        if (toDelete != null) {
            events.remove(toDelete)
            AppContext.eventService.replaceAll(events)
            loadEvents()
        }
    }

    private fun clearForm() {
        titleField.text = ""
        descriptionField.text = ""
        categoryField.text = ""
        expectedSizeField.value = 50
        organiserNameField.text = ""
        organiserEmailField.text = ""
    }
}
