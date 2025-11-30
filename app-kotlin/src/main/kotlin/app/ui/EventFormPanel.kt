package app.ui

import app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

/**
 * Form for creating and managing base event records before they are scheduled.
 */
class EventFormPanel : JPanel(BorderLayout(15, 15)) {

    private val titleField = JTextField(30)
    private val descriptionField = JTextField(30)
    private val categoryBox = JComboBox(arrayOf("Workshops", "Festivals", "Conferences", "Entertainment", "Sports", "Other"))
    private val dateField = DatePickerField(LocalDate.now())
    private val timeOptions: List<String> = generateTimeOptions()
    private val startTimeBox = JComboBox(timeOptions.toTypedArray())
    private val endTimeBox = JComboBox(timeOptions.toTypedArray())
    private val expectedSizeField = JSpinner(SpinnerNumberModel(1, 1, 10000, 1))
    private val organiserNameField = JTextField(25)
    private val organiserEmailField = JTextField(25)

    private val addButton = JButton("Add Event")
    private val deleteButton = JButton("Delete Selected")
    private val refreshButton = JButton("Refresh List")

    private val tableModel = object : DefaultTableModel(
        arrayOf("ID", "Title", "Category", "Date", "Start", "End", "Expected Size"),
        0
    ) {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val eventTable = JTable(tableModel)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    init {
        background = UiTheme.backgroundColor
        border = EmptyBorder(20, 20, 20, 20)

        val formCard = UiTheme.createCard(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
        }

        fun prepare(component: JComponent, compact: Boolean = false): JComponent = component.apply {
            preferredSize = Dimension(if (compact) 160 else 220, 28)
        }

        var row = 0
        fun addRow(label: String, component: JComponent, compact: Boolean = false) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            formCard.add(UiTheme.styleLabel(JLabel(label), bold = true), gbc)

            gbc.gridx = 1
            gbc.weightx = if (compact) 0.0 else 1.0
            gbc.fill = if (compact) GridBagConstraints.NONE else GridBagConstraints.HORIZONTAL
            formCard.add(prepare(component, compact), gbc)
            gbc.fill = GridBagConstraints.HORIZONTAL
            row++
        }

        addRow("Title:", titleField)
        addRow("Description:", descriptionField)
        addRow("Category:", categoryBox)
        addRow("Date:", dateField.component)
        addRow("Start Time:", startTimeBox)
        addRow("End Time:", endTimeBox)
        addRow("Expected Size:", expectedSizeField, compact = true)
        addRow("Organiser Name:", organiserNameField)
        addRow("Organiser Email:", organiserEmailField)

        startTimeBox.selectedItem = "09:00"
        endTimeBox.selectedItem = "10:00"

        val formWrapper = JPanel(BorderLayout()).apply {
            background = UiTheme.backgroundColor
            add(formCard, BorderLayout.CENTER)
            add(UiTheme.createButtonRow(addButton, deleteButton, refreshButton), BorderLayout.SOUTH)
        }

        UiTheme.styleTable(eventTable)
        val tableScroll = javax.swing.JScrollPane(eventTable).apply {
            border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(UiTheme.highlightColor), "Saved Events")
            preferredSize = Dimension(0, 260)
            background = UiTheme.cardColor
            viewport.background = java.awt.Color.WHITE
        }

        eventTable.columnModel.getColumn(0).apply {
            minWidth = 0
            maxWidth = 0
            width = 0
            preferredWidth = 0
        }

        val content = JPanel().apply {
            background = UiTheme.backgroundColor
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(formWrapper)
            add(javax.swing.Box.createVerticalStrut(14))
            add(tableScroll)
        }

        add(UiTheme.wrapWithScroll(content), BorderLayout.CENTER)

        loadEvents()

        addButton.addActionListener { onAddEvent() }
        deleteButton.addActionListener { onDeleteEvent() }
        refreshButton.addActionListener { loadEvents() }
    }

    private fun generateTimeOptions(): List<String> {
        // Generate 15-minute increments between 07:00 and 23:00 for dropdowns.
        val start = LocalTime.of(7, 0)
        val end = LocalTime.of(23, 0)
        val times = mutableListOf<String>()
        var current = start
        while (!current.isAfter(end)) {
            times += String.format("%02d:%02d", current.hour, current.minute)
            current = current.plusMinutes(15)
        }
        return times
    }

    /**
     * Reloads events from storage and repopulates the table sorted by date/time.
     */
    private fun loadEvents() {
        tableModel.rowCount = 0
        val events = AppContext.eventService.reload()
        events.sortedWith(compareBy({ it.date }, { it.startTime }, { it.title }))
            .forEach {
                tableModel.addRow(
                    arrayOf(
                        it.id,
                        it.title,
                        it.category,
                        it.date.format(dateFormatter),
                        it.startTime.toString(),
                        it.endTime.toString(),
                        it.expectedSize
                    )
                )
            }
    }

    /**
     * Validates the form and sends the event to the service layer.
     * User friendlly error messages keep the Swing UI from crashing on invalid input.
     */
    private fun onAddEvent() {
        val title = titleField.text.trim()
        val description = descriptionField.text.trim()
        val organiserEmail = organiserEmailField.text.trim()
        val organiserName = organiserNameField.text.trim()
        val category = (categoryBox.selectedItem as? String)?.trim().orEmpty()
        val start = LocalTime.parse(startTimeBox.selectedItem as String)
        val end = LocalTime.parse(endTimeBox.selectedItem as String)
        val date = dateField.date
        val today = LocalDate.now()
        val currentTime = LocalTime.now()

        if (title.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please provide a title for the event.")
            return
        }
        if (organiserName.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please provide the organiser name.")
            return
        }
        if (!emailRegex.matches(organiserEmail)) {
            JOptionPane.showMessageDialog(this, "Please enter a valid organiser email.", "Invalid Email", JOptionPane.ERROR_MESSAGE)
            return
        }
        if (!end.isAfter(start)) {
            JOptionPane.showMessageDialog(this, "End time must be after the start time.")
            return
        }
        if (date.isBefore(today)) {
            JOptionPane.showMessageDialog(this, "Event date cannot be in the past.")
            return
        }
        if (date.isEqual(today) && !start.isAfter(currentTime)) {
            JOptionPane.showMessageDialog(this, "Event start time must be later than now.")
            return
        }

        try {
            val expectedSize = expectedSizeField.value as Int
            AppContext.eventService.addEvent(
                title = title,
                description = description,
                category = category,
                date = date,
                startTime = start,
                endTime = end,
                expectedSize = expectedSize,
                organiserName = organiserName,
                organiserEmail = organiserEmail
            )
            JOptionPane.showMessageDialog(this, "Event added successfully.")
            clearForm()
            loadEvents()
        } catch (e: Exception) {
            e.printStackTrace()
            JOptionPane.showMessageDialog(this, "Error: ${e.message}")
        }
    }

    /** Removes the currently selected event from storage. */
    private fun onDeleteEvent() {
        val selectedRow = eventTable.selectedRow
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select an event to delete.")
            return
        }
        val modelRow = eventTable.convertRowIndexToModel(selectedRow)
        val eventId = tableModel.getValueAt(modelRow, 0) as String
        AppContext.eventService.deleteEventById(eventId)
        loadEvents()
    }

    /** Reset all inputs back to defaults values selected*/
    private fun clearForm() {
        titleField.text = ""
        descriptionField.text = ""
        categoryBox.selectedIndex = 0
        dateField.date = LocalDate.now()
        startTimeBox.selectedItem = "09:00"
        endTimeBox.selectedItem = "10:00"
        expectedSizeField.value = 1
        organiserNameField.text = ""
        organiserEmailField.text = ""
    }
}
