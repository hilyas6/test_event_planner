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

    fun applyToolbarTheme(tabbedPane: JTabbedPane) {
        tabbedPane.background = backgroundColor
        tabbedPane.isOpaque = false
        tabbedPane.border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, highlightColor),
            EmptyBorder(16, 20, 8, 20)
        )

        val buttons = mutableListOf<JToggleButton>()

        repeat(tabbedPane.tabCount) { index ->
            val title = tabbedPane.getTitleAt(index)
            val button = JToggleButton(title).apply {
                isFocusPainted = false
                isContentAreaFilled = true
                isOpaque = true
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                font = font.deriveFont(Font.BOLD, 14f)
                margin = Insets(0, 0, 0, 0)
                horizontalAlignment = SwingConstants.CENTER
                border = EmptyBorder(0, 0, 0, 0)
                addActionListener {
                    val currentIndex = tabbedPane.indexOfTabComponent(this)
                    if (currentIndex >= 0) {
                        tabbedPane.selectedIndex = currentIndex
                    }
                }
            }
            tabbedPane.setTabComponentAt(index, button)
            buttons += button
        }

        fun refreshSelection() {
            buttons.forEach { button ->
                val idx = tabbedPane.indexOfTabComponent(button)
                if (idx < 0) return@forEach
                val selected = tabbedPane.selectedIndex == idx
                val background = if (selected) buttonColor else cardColor
                val foreground = if (selected) Color.WHITE else textColor
                val borderColor = if (selected) buttonColor.darker() else highlightColor
                button.background = background
                button.foreground = foreground
                button.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor, 1, true),
                    EmptyBorder(8, 26, 8, 26)
                )
                button.isSelected = selected
            }
        }

        tabbedPane.addChangeListener { refreshSelection() }
        refreshSelection()
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
