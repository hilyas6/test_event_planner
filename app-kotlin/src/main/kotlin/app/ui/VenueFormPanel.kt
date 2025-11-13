package app.ui

import app.AppContext
import java.awt.*
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import kotlin.math.ceil
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel

class VenueFormPanel(private val onDataChanged: (() -> Unit)? = null) : JPanel(BorderLayout()) {

    private val backgroundColor = Color(0xF5, 0xF5, 0xF5)
    private val textColor = Color(0x33, 0x33, 0x33)
    private val cardColor = Color(0xFF, 0xFF, 0xFF)
    private val buttonColor = Color(0x5B, 0x7C, 0x99)
    private val highlightColor = Color(0xA8, 0xB9, 0xA2)

    private val nameField = JTextField(12)
    private val capacityField = JSpinner(SpinnerNumberModel(10, 1, 10000, 1))
    private val cityField = JTextField(12)

    private val addButton = JButton("Add")
    private val deleteButton = JButton("Delete")
    private val refreshButton = JButton("Refresh")

    private val tableModel = DefaultTableModel(arrayOf("ID", "Name", "Location", "Capacity"), 0)
    private val table = JTable(tableModel)

    init {
        layout = BorderLayout(15, 15)
        background = backgroundColor
        border = EmptyBorder(20, 20, 20, 20)

        val formCard = JPanel(GridBagLayout()).apply {
            background = cardColor
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(highlightColor, 1, true),
                EmptyBorder(15, 20, 15, 20)
            )
        }

        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        fun JLabel.style(): JLabel = apply {
            foreground = textColor
            font = font.deriveFont(Font.BOLD)
        }

        fun JComponent.compact(): JComponent = apply {
            preferredSize = Dimension(180, 28)
        }

        var row = 0
        fun addRow(label: String, component: JComponent) {
            gbc.gridx = 0
            gbc.gridy = row
            gbc.weightx = 0.0
            formCard.add(JLabel(label).style(), gbc)

            gbc.gridx = 1
            gbc.weightx = 1.0
            formCard.add(component.compact(), gbc)
            row++
        }

        addRow("Venue Name:", nameField)
        addRow("Capacity:", capacityField)
        addRow("Location:", cityField)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 0)).apply {
            background = buttonColor
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, buttonColor.darker()),
                EmptyBorder(12, 0, 12, 0)
            )
            isOpaque = true
        }

        val buttons = mapOf(
            addButton to "➕",
            deleteButton to "🗑",
            refreshButton to "🔄"
        )
        buttons.forEach { (button, glyph) ->
            button.background = buttonColor
            button.foreground = Color.WHITE
            button.isOpaque = true
            button.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(buttonColor.darker()),
                EmptyBorder(8, 20, 8, 20)
            )
            button.font = button.font.deriveFont(Font.BOLD, 14f)
            button.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            button.icon = createGlyphIcon(glyph, highlightColor)
            button.horizontalTextPosition = SwingConstants.RIGHT
            button.iconTextGap = 10
            buttonPanel.add(button)
        }

        val formWrapper = JPanel(BorderLayout()).apply {
            background = backgroundColor
            add(formCard, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        val scrollPane = JScrollPane(table).apply {
            border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(highlightColor), "Saved Venues")
            preferredSize = Dimension(0, 220)
        }
        table.fillsViewportHeight = true
        table.autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        table.foreground = textColor
        table.background = Color.WHITE
        table.gridColor = highlightColor
        table.font = table.font.deriveFont(13f)
        table.selectionBackground = highlightColor.darker()
        table.selectionForeground = Color.WHITE
        table.tableHeader.apply {
            background = highlightColor
            foreground = textColor
            font = font.deriveFont(Font.BOLD)
        }

        add(formWrapper, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        addButton.addActionListener { addVenue() }
        deleteButton.addActionListener { deleteSelectedVenue() }
        refreshButton.addActionListener { refreshTable() }

        refreshTable()
    }

    private fun createGlyphIcon(symbol: String, color: Color): Icon {
        val font = Font("Dialog", Font.BOLD, 18)
        val frc = FontRenderContext(AffineTransform(), true, true)
        val glyphVector = font.createGlyphVector(frc, symbol)
        val bounds = glyphVector.visualBounds
        val width = ceil(bounds.width + 8).toInt().coerceAtLeast(1)
        val height = ceil(bounds.height + 8).toInt().coerceAtLeast(1)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.font = font
        g2.color = color
        val x = (4 - bounds.x).toFloat()
        val y = (4 - bounds.y).toFloat()
        g2.drawString(symbol, x, y)
        g2.dispose()

        return ImageIcon(image)
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
        capacityField.value = 10
    }
}
