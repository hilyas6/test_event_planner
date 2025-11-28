package app

import javax.swing.JScrollPane
import javax.swing.JTextArea
import kotlin.jvm.functions.Function0
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class MainTest {
    @BeforeTest
    fun setHeadlessMode() {
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun `safePanel wraps factory failures in scrollable error area`() {
        val method = Class.forName("app.MainKt").getDeclaredMethod(
            "safePanel",
            String::class.java,
            Function0::class.java
        ).apply { isAccessible = true }

        val component = method.invoke(null, "Test Panel", Function0 {
            throw IllegalStateException("boom")
        })

        val scrollPane = assertIs<JScrollPane>(component)
        val textArea = assertIs<JTextArea>(scrollPane.viewport.view)

        assertContains(textArea.text, "Test Panel")
        assertContains(textArea.text, "IllegalStateException")
    }
}
