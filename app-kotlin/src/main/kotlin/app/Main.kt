package app

import app.ui.EventFormPanel
import app.ui.RegistrationPanel
import app.ui.SchedulePanel
import app.ui.VenueFormPanel
import app.ui.UiTheme
import java.awt.BorderLayout
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.*

fun main() {
    println("Launching Event Planner…")
    println("Headless? " + java.awt.GraphicsEnvironment.isHeadless())

    // Log any uncaught exceptions so we can see what's wrong
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        System.err.println("Uncaught on " + t.name)
        e.printStackTrace()
        JOptionPane.showMessageDialog(null, e.toString(), "Uncaught exception", JOptionPane.ERROR_MESSAGE)
    }

    SwingUtilities.invokeLater {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) {}

        val frame = JFrame("Event Planner")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(1200, 720)
        frame.setLocationRelativeTo(null)

        val registrationPanel = RegistrationPanel()
        val tabs = JTabbedPane().apply {
            background = UiTheme.backgroundColor
            foreground = UiTheme.textColor
            tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        }
        tabs.add("Venues", safePanel("Venues") { VenueFormPanel() })
        tabs.add("Events", safePanel("Events") { EventFormPanel() })
        tabs.add("Scheduling", safePanel("Scheduling") { SchedulePanel(registrationPanel::refreshAll) })
        tabs.add("Registration", safePanel("Registration") { registrationPanel })

        UiTheme.applyToolbarTheme(tabs)

        frame.contentPane.add(tabs, BorderLayout.CENTER)
        frame.isVisible = true
        println("Window should now be visible.")
    }
}

/** Wrap panel creation; if it throws, show the stack trace instead of crashing. */
private fun safePanel(name: String, factory: () -> JComponent): JComponent {
    return try {
        factory()
    } catch (e: Throwable) {
        System.err.println("Failed to build panel '$name': ${e.message}")
        e.printStackTrace()
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        JTextArea("Failed to load '$name' panel:\n${e}\n\n${sw}\n").apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
        }.let { JScrollPane(it) }
    }
}
