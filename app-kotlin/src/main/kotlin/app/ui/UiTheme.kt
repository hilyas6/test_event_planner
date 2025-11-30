package app.ui

import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Insets
import java.awt.GridBagLayout
import java.awt.LayoutManager
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JToggleButton
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.EmptyBorder
import javax.swing.plaf.basic.BasicTabbedPaneUI

/**
 * Shared colours and helpers so every panel keeps a consistent looks.
 */
object UiTheme {
    val backgroundColor: Color = Color(0xF0, 0xF3, 0xF8)
    val cardColor: Color = Color(0xFF, 0xFF, 0xFF)
    val textColor: Color = Color(0x1F, 0x29, 0x33)
    val mutedTextColor: Color = Color(0x55, 0x65, 0x7A)
    val borderColor: Color = Color(0xD7, 0xDE, 0xEA)
    val buttonColor: Color = Color(0x36, 0x70, 0xF4)
    val highlightColor: Color = Color(0xB9, 0xC6, 0xD9)

    private val baseFont: Font = Font("Segoe UI", Font.PLAIN, 14).let { candidate ->
        // Fall back to the current look-and-feel's default font when Segoe UI isn't available so
        // Windows and macOS render the same spacing and sizing.
        if (candidate.family.equals("dialog", ignoreCase = true)) {
            UIManager.getFont("defaultFont") ?: candidate
        } else candidate
    }

    fun applyGlobalDefaults() {
        val fontKeys = listOf(
            "Label.font",
            "Button.font",
            "TextField.font",
            "FormattedTextField.font",
            "PasswordField.font",
            "ComboBox.font",
            "Table.font",
            "TableHeader.font",
            "TabbedPane.font",
            "TextArea.font",
            "Spinner.font",
            "List.font",
            "OptionPane.messageFont",
            "OptionPane.buttonFont"
        )
        fontKeys.forEach { UIManager.put(it, baseFont) }

        UIManager.put("Panel.background", backgroundColor)
        UIManager.put("ScrollPane.background", backgroundColor)
        UIManager.put("TabbedPane.background", backgroundColor)
        UIManager.put("TabbedPane.selectedForeground", textColor)
        UIManager.put("Table.gridColor", borderColor)
        UIManager.put("Table.selectionBackground", highlightColor.darker())
        UIManager.put("Table.selectionForeground", Color.WHITE)
        UIManager.put("OptionPane.background", backgroundColor)
        UIManager.put("OptionPane.messageForeground", textColor)
        UIManager.put("Button.background", cardColor)
        UIManager.put("Button.foreground", textColor)
        UIManager.put("Button.font", baseFont.deriveFont(Font.BOLD, 13f))
    }

    fun createCard(layout: LayoutManager = GridBagLayout()): JPanel = JPanel(layout).apply {
        background = cardColor
        isOpaque = true
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(18, 22, 18, 22)
        )
    }

    fun styleLabel(label: JLabel, bold: Boolean = false, muted: Boolean = false): JLabel = label.apply {
        foreground = if (muted) mutedTextColor else textColor
        font = baseFont.deriveFont(if (bold) Font.BOLD else Font.PLAIN, baseFont.size2D)
    }

    fun stylePrimaryButton(button: javax.swing.JButton) {
        button.background = buttonColor
        button.foreground = Color.WHITE
        button.isOpaque = true
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(buttonColor.darker(), 1, true),
            EmptyBorder(10, 24, 10, 24)
        )
        button.font = baseFont.deriveFont(Font.BOLD, 14f)
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.isFocusPainted = false
        button.margin = Insets(6, 10, 6, 10)
    }

    fun styleSoftButton(button: javax.swing.JButton) {
        button.background = Color(0xE7, 0xEC, 0xF5)
        button.foreground = textColor
        button.isOpaque = true
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1, true),
            EmptyBorder(8, 14, 8, 14)
        )
        button.font = baseFont.deriveFont(Font.BOLD, 13f)
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.isFocusPainted = false
    }

    fun createButtonRow(vararg buttons: javax.swing.JButton): JPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 18, 0)).apply {
        background = cardColor
        buttons.forEach { stylePrimaryButton(it); add(it) }
        border = EmptyBorder(12, 0, 0, 0)
    }

    fun applyToolbarTheme(tabbedPane: JTabbedPane): JPanel {
        tabbedPane.background = backgroundColor
        tabbedPane.isOpaque = false
        tabbedPane.border = EmptyBorder(0, 0, 0, 0)
        tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
        tabbedPane.setUI(object : BasicTabbedPaneUI() {
            override fun calculateTabAreaHeight(tabPlacement: Int, runCount: Int, maxTabHeight: Int): Int = 0
            override fun paintTabArea(g: Graphics?, tabPlacement: Int, selectedIndex: Int) {}
        })

        val buttons = mutableListOf<JToggleButton>()

        repeat(tabbedPane.tabCount) { index ->
            val title = tabbedPane.getTitleAt(index)
            val button = JToggleButton(title).apply {
                isFocusPainted = false
                isContentAreaFilled = true
                isOpaque = true
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                font = baseFont.deriveFont(Font.BOLD, 14f)
                margin = Insets(0, 0, 0, 0)
                horizontalAlignment = SwingConstants.CENTER
                preferredSize = Dimension(150, 38)
                border = EmptyBorder(0, 0, 0, 0)
                addActionListener { tabbedPane.selectedIndex = index }
            }
            buttons += button
        }

        fun refreshSelection() {
            buttons.forEachIndexed { idx, button ->
                val selected = tabbedPane.selectedIndex == idx
                val background = if (selected) buttonColor else cardColor
                val foreground = if (selected) Color.WHITE else textColor
                val borderCol = if (selected) buttonColor.darker() else borderColor
                button.background = background
                button.foreground = foreground
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderCol, 1, true),
                    EmptyBorder(8, 22, 8, 22)
                )
                button.isSelected = selected
            }
        }

        tabbedPane.addChangeListener { refreshSelection() }
        refreshSelection()

        return JPanel(java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 18, 0)).apply {
            background = backgroundColor
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor),
                EmptyBorder(12, 18, 12, 18)
            )
            buttons.forEach { add(it) }
        }
    }

    fun styleTable(table: JTable) {
        table.foreground = textColor
        table.background = Color.WHITE
        table.gridColor = borderColor
        table.font = baseFont.deriveFont(13f)
        table.rowHeight = 26
        table.selectionBackground = highlightColor.darker()
        table.selectionForeground = Color.WHITE
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        table.tableHeader.apply {
            background = Color(0xEC, 0xF0, 0xF7)
            foreground = textColor
            font = baseFont.deriveFont(Font.BOLD, 13f)
            reorderingAllowed = false
        }
    }

    fun wrapWithScroll(component: JComponent): JScrollPane = JScrollPane(component).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        viewport.background = backgroundColor
        background = backgroundColor
    }
}
