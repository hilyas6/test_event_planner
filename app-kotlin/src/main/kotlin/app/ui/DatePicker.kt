package app.ui

import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Window
import java.awt.Dialog
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

class DatePickerField(
    initialDate: LocalDate = LocalDate.now(),
    private val minDate: LocalDate? = null
) {
    private val zoneId = ZoneId.systemDefault()
    private val spinnerModel = object : SpinnerDateModel(
        java.util.Date.from(initialDate.atStartOfDay(zoneId).toInstant()),
        minDate?.let { java.util.Date.from(it.atStartOfDay(zoneId).toInstant()) },
        null,
        java.util.Calendar.DAY_OF_MONTH
    ) {
        override fun setValue(value: Any?) {
            if (value == null) return
            val dateValue = value as? java.util.Date ?: return
            val min = start as? java.util.Date
            if (min != null && dateValue.before(min)) {
                super.setValue(min)
            } else {
                super.setValue(value)
            }
        }
    }

    val spinner = JSpinner(spinnerModel).apply {
        editor = JSpinner.DateEditor(this, "dd/MM/yyyy")
        preferredSize = Dimension(148, preferredSize.height)
        font = font.deriveFont(Font.PLAIN, 14f)
    }

    private val calendarButton = JButton("📅").apply {
        margin = java.awt.Insets(4, 10, 4, 10)
        toolTipText = "Open calendar"
        font = font.deriveFont(Font.BOLD, 18f)
        UiTheme.styleSoftButton(this)
    }

    val component: JPanel = JPanel(BorderLayout(6, 0)).apply {
        background = UiTheme.cardColor
        add(spinner, BorderLayout.CENTER)
        add(calendarButton, BorderLayout.EAST)
    }

    init {
        calendarButton.addActionListener {
            val selected = showCalendarDialog(component, date, minDate)
            if (selected != null) {
                date = selected
            }
        }
    }

    var date: LocalDate
        get() {
            val value = spinner.value as java.util.Date
            return value.toInstant().atZone(zoneId).toLocalDate()
        }
        set(value) {
            val clamped = minDate?.let { if (value.isBefore(it)) it else value } ?: value
            spinner.value = java.util.Date.from(clamped.atStartOfDay(zoneId).toInstant())
        }

    fun setEnabled(enabled: Boolean) {
        spinner.isEnabled = enabled
        calendarButton.isEnabled = enabled
    }

    companion object {
        private fun showCalendarDialog(parent: Component, initialDate: LocalDate, minDate: LocalDate?): LocalDate? {
            val owner = SwingUtilities.getWindowAncestor(parent)
            val dialog = CalendarDialog(owner, initialDate, minDate)
            dialog.isVisible = true
            return dialog.selectedDate
        }
    }
}

private class CalendarDialog(
    owner: Window?,
    initialDate: LocalDate,
    private val minDate: LocalDate?
) : JDialog(owner, "Select Date", Dialog.ModalityType.APPLICATION_MODAL) {

    private val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val monthLabel = JLabel("", JLabel.CENTER)
    private val daysPanel = JPanel(java.awt.GridLayout(0, 7, 4, 4))
    private var displayMonth: LocalDate = initialDate.withDayOfMonth(1)

    var selectedDate: LocalDate? = null
        private set

    init {
        contentPane.background = UiTheme.cardColor
        layout = BorderLayout(10, 10)
        (contentPane as? JPanel)?.border = EmptyBorder(14, 14, 14, 14)

        val header = JPanel(BorderLayout()).apply {
            background = UiTheme.cardColor
            val prev = JButton("◀").apply {
                addActionListener { shiftMonth(-1) }
            }
            val next = JButton("▶").apply {
                addActionListener { shiftMonth(1) }
            }
            listOf(prev, next).forEach { button ->
                UiTheme.stylePrimaryButton(button)
                button.preferredSize = Dimension(42, 30)
            }
            monthLabel.horizontalAlignment = JLabel.CENTER
            monthLabel.font = monthLabel.font.deriveFont(java.awt.Font.BOLD, 16f)
            monthLabel.foreground = UiTheme.textColor
            add(prev, BorderLayout.WEST)
            add(monthLabel, BorderLayout.CENTER)
            add(next, BorderLayout.EAST)
        }

        val namesPanel = JPanel(java.awt.GridLayout(1, 7, 4, 4)).apply {
            background = UiTheme.cardColor
            dayNames.forEach { day ->
                add(JLabel(day, JLabel.CENTER).apply {
                    foreground = UiTheme.textColor
                    font = font.deriveFont(java.awt.Font.BOLD)
                })
            }
        }

        val daysWrapper = JPanel(BorderLayout()).apply {
            background = UiTheme.cardColor
            add(namesPanel, BorderLayout.NORTH)
            add(daysPanel, BorderLayout.CENTER)
        }

        val actionPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            background = UiTheme.cardColor
            val cancel = JButton("Close").apply {
                UiTheme.styleSoftButton(this)
                addActionListener { dispose() }
            }
            add(cancel)
        }

        add(header, BorderLayout.NORTH)
        add(daysWrapper, BorderLayout.CENTER)
        add(actionPanel, BorderLayout.SOUTH)

        pack()
        setSize(360, 320)
        setLocationRelativeTo(owner)

        refreshDays()
    }

    private fun shiftMonth(offset: Long) {
        val candidate = displayMonth.plusMonths(offset)
        if (offset < 0 && minDate != null) {
            val lastOfCandidate = candidate.withDayOfMonth(candidate.lengthOfMonth())
            if (lastOfCandidate.isBefore(minDate)) {
                return
            }
        }
        displayMonth = candidate
        refreshDays()
    }

    private fun refreshDays() {
        monthLabel.text = formatter.format(displayMonth)
        daysPanel.removeAll()

        val firstOfMonth = displayMonth.withDayOfMonth(1)
        val firstDayIndex = ((firstOfMonth.dayOfWeek.value + 6) % 7) // Monday = 0
        repeat(firstDayIndex) {
            daysPanel.add(Box.createVerticalStrut(0))
        }

        val daysInMonth = displayMonth.lengthOfMonth()
        for (day in 1..daysInMonth) {
            val currentDate = displayMonth.withDayOfMonth(day)
            val button = JButton(day.toString()).apply {
                background = UiTheme.cardColor
                foreground = UiTheme.textColor
                border = BorderFactory.createLineBorder(UiTheme.highlightColor)
                isOpaque = true
                isFocusPainted = false
                cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                preferredSize = Dimension(44, 34)
                addActionListener {
                    selectedDate = currentDate
                    dispose()
                }
            }
            val enabled = minDate?.let { !currentDate.isBefore(it) } ?: true
            button.isEnabled = enabled
            if (!enabled) {
                button.foreground = UiTheme.textColor.darker()
                button.cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.DEFAULT_CURSOR)
            }
            daysPanel.add(button)
        }

        val totalCells = firstDayIndex + daysInMonth
        val remainder = totalCells % 7
        if (remainder != 0) {
            repeat(7 - remainder) {
                daysPanel.add(Box.createVerticalStrut(0))
            }
        }

        daysPanel.revalidate()
        daysPanel.repaint()
    }
}
