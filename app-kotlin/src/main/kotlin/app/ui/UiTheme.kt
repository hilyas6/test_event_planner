package app.ui

import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Shared colours and helpers so every panel keeps a consistent look-and-feel.
 */
object UiTheme {
    val backgroundColor: Color = Color(0xF5, 0xF5, 0xF5)
    val textColor: Color = Color(0x33, 0x33, 0x33)
    val cardColor: Color = Color(0xFF, 0xFF, 0xFF)
    val buttonColor: Color = Color(0x5B, 0x7C, 0x99)
    val highlightColor: Color = Color(0xA8, 0xB9, 0xA2)

    fun createCard(layout: LayoutManager = GridBagLayout()): JPanel = JPanel(layout).apply {
        background = cardColor
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(highlightColor, 1, true),
            EmptyBorder(18, 22, 18, 22)
        )
    }

    fun styleLabel(label: JLabel, bold: Boolean = false): JLabel = label.apply {
        foreground = textColor
        font = if (bold) font.deriveFont(Font.BOLD) else font
    }

    fun stylePrimaryButton(button: JButton) {
        button.background = buttonColor
        button.foreground = Color.WHITE
        button.isOpaque = true
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(buttonColor.darker()),
            EmptyBorder(10, 26, 10, 26)
        )
        button.font = button.font.deriveFont(Font.BOLD, 14f)
        button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        button.isFocusPainted = false
    }

    fun createButtonRow(vararg buttons: JButton): JPanel = JPanel(FlowLayout(FlowLayout.CENTER, 18, 0)).apply {
        background = cardColor
        buttons.forEach { stylePrimaryButton(it); add(it) }
        border = EmptyBorder(12, 0, 0, 0)
    }

    fun styleTable(table: JTable) {
        table.foreground = textColor
        table.background = Color.WHITE
        table.gridColor = highlightColor
        table.font = table.font.deriveFont(13f)
        table.selectionBackground = highlightColor.darker()
        table.selectionForeground = Color.WHITE
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        table.tableHeader.apply {
            background = highlightColor
            foreground = textColor
            font = font.deriveFont(Font.BOLD)
        }
    }

    fun wrapWithScroll(component: JComponent): JScrollPane = JScrollPane(component).apply {
        border = BorderFactory.createEmptyBorder()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        viewport.background = backgroundColor
    }
}
