package app

import app.ui.EventFormPanel
import app.ui.RegistrationPanel
import app.ui.SchedulePanel
import app.ui.VenueFormPanel
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main() {
    println("Launching Event Planner…")
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (_: Exception) {
            // keep defaults if the platform look and feel cannot be applied
        }

        val frame = JFrame("Event Planner")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1100, 720)
        frame.setLocationRelativeTo(null)

        val schedulePanel = SchedulePanel()
        val registrationPanel = RegistrationPanel { schedulePanel.refreshFromOutside() }
        val venuePanel = VenueFormPanel {
            schedulePanel.refreshFromOutside()
            registrationPanel.refreshFromOutside()
        }
        val eventPanel = EventFormPanel {
            schedulePanel.refreshFromOutside()
            registrationPanel.refreshFromOutside()
        }

        val tabs = JTabbedPane()
        tabs.addTab("Venues", venuePanel)
        tabs.addTab("Events", eventPanel)
        tabs.addTab("Scheduling", schedulePanel)
        tabs.addTab("Registration", registrationPanel)

        frame.contentPane.add(tabs, BorderLayout.CENTER)
        frame.isVisible = true
    }
}
