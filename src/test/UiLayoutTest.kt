package diplay.desktop

import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import org.junit.Assert.*
import org.junit.Test

class UiLayoutTest {
    private fun descendants(component:Component):List<Component> = listOf(component)+(if(component is Container) component.components.flatMap { descendants(it) } else emptyList())
    private fun layout(container:Container) { container.doLayout(); container.components.filterIsInstance<Container>().forEach(::layout) }
    private fun visible(component:Component):Boolean=component.isVisible&&(component.parent?.let(::visible)?:true)
    @Test fun connectionActionRemainsAccessibleAndSidebarCanBeToggled() {
        SwingUtilities.invokeAndWait {
            for((width,height,large) in listOf(Triple(1280,820,false),Triple(940,700,true))) {
                DesktopTheme.install(large)
                val panel=ReceiverPanel()
                try {
                    panel.setSize(width,height); repeat(3) { layout(panel) }
                    val buttons=descendants(panel).filterIsInstance<JButton>()
                    val connect=buttons.single { it.text=="Conectar CarPlay" }
                    val toggle=buttons.single { it.text=="Conexão" }
                    val bounds=SwingUtilities.convertRectangle(connect.parent,connect.bounds,panel)
                    assertTrue("Primary action must fit in the window",Rectangle(0,0,width,height).contains(bounds))
                    assertTrue(connect.isEnabled); assertTrue(visible(connect))
                    toggle.doClick(); assertFalse(visible(connect))
                    toggle.doClick(); assertTrue(visible(connect))
                } finally { panel.close() }
            }
        }
    }
    @Test fun actualVideoStateEnablesNavigationAndLateBootstrapDoesNotDowngradeIt() {
        SwingUtilities.invokeAndWait {
            DesktopTheme.install()
            val panel=ReceiverPanel()
            try {
                val buttons=descendants(panel).filterIsInstance<JButton>()
                val home=buttons.single { it.text=="Início" }
                assertFalse(home.isEnabled)
                val status=ReceiverPanel::class.java.getDeclaredMethod("applyStatus",String::class.java).apply { isAccessible=true }
                status.invoke(panel,"CarPlay — recebendo vídeo")
                assertTrue(home.isEnabled)
                assertTrue(buttons.single { it.text=="Encerrar" }.isVisible)
                assertFalse(visible(buttons.single { it.text=="Conectar CarPlay" }))
                status.invoke(panel,"Identificando e autenticando…")
                assertTrue(descendants(panel).filterIsInstance<JLabel>().any { it.text=="Você está conectado" })
                assertTrue(home.isEnabled)
            } finally { panel.close() }
        }
    }
}
