package app.ui

import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel

/**
 * Simple wrapper around a JSpinner so forms can read and write LocalDate values easily.
 */
class DateField(initialDate: LocalDate = LocalDate.now()) {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val model = SpinnerDateModel(
        Date.from(initialDate.atStartOfDay(zoneId).toInstant()),
        null,
        null,
        Calendar.DAY_OF_MONTH
    )

    val spinner: JSpinner = JSpinner(model).apply {
        editor = JSpinner.DateEditor(this, "yyyy-MM-dd")
    }

    var date: LocalDate
        get() = (spinner.value as Date).toInstant().atZone(zoneId).toLocalDate()
        set(value) {
            spinner.value = Date.from(value.atStartOfDay(zoneId).toInstant())
        }

    val component: JComponent
        get() = spinner

    fun setEnabled(enabled: Boolean) {
        spinner.isEnabled = enabled
    }
}
