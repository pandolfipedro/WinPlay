package diplay.desktop

import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

object DiagnosticsDialog {
    fun show(owner:JFrame,sessionActive:()->Boolean) {
        val dialog=JDialog(owner,"WinPlay · Ferramentas",false)
        dialog.defaultCloseOperation=JDialog.DISPOSE_ON_CLOSE
        val root=JPanel(BorderLayout(12,16)).apply { border=EmptyBorder(20,20,20,20) }
        root.add(DesktopTheme.label("Diagnóstico e conexão",22,bold=true),BorderLayout.NORTH)
        val tabs=JTabbedPane()
        val log=JTextArea("Confira o ambiente ou teste o Bluetooth com a sessão CarPlay encerrada.\nOs testes locais não substituem a conexão real com o iPhone.").apply {
            isEditable=false; lineWrap=true; wrapStyleWord=true; margin=Insets(12,12,12,12); font=Font(Font.MONOSPACED,Font.PLAIN,13)
        }
        val phones=JComboBox<WindowsBluetooth.Device>()
        val check=DesktopTheme.button("Verificar ambiente",UiIcon.Kind.REFRESH)
        val probe=DesktopTheme.button("Testar enlace Bluetooth",UiIcon.Kind.PHONE)
        val auth=DesktopTheme.button("Preparar autenticação",UiIcon.Kind.SETTINGS)
        var working=false
        fun task(requiresIdle:Boolean=true,block:()->String) {
            if(working) return
            if(requiresIdle&&sessionActive()) { JOptionPane.showMessageDialog(dialog,"Encerre a sessão CarPlay antes de executar os testes."); return }
            working=true; listOf(check,probe,auth).forEach { it.isEnabled=false }; log.text="Executando…"
            object:SwingWorker<String,Unit>() {
                override fun doInBackground()=block()
                override fun done() {
                    log.text=try { get() } catch(error:Exception) { "Falha: ${error.cause?.message?:error.message}" }
                    log.caretPosition=0; working=false; listOf(check,probe,auth).forEach { it.isEnabled=true }
                }
            }.execute()
        }
        val environment=JPanel(BorderLayout(8,8)).apply { border=EmptyBorder(12,12,12,12); add(JScrollPane(log),BorderLayout.CENTER) }
        val actions=JPanel(FlowLayout(FlowLayout.LEFT)).apply { isOpaque=false; add(check); add(auth) }
        environment.add(actions,BorderLayout.SOUTH); tabs.addTab("Ambiente",environment)
        val bluetooth=JPanel(BorderLayout(12,12)).apply {
            border=EmptyBorder(16,16,16,16)
            add(JLabel("<html>Selecione um dispositivo pareado para testar apenas o enlace iAP2.<br>Este teste não inicia uma sessão CarPlay.</html>"),BorderLayout.NORTH)
            add(phones,BorderLayout.CENTER); add(probe,BorderLayout.SOUTH)
        }; tabs.addTab("Bluetooth",bluetooth)
        root.add(tabs,BorderLayout.CENTER)
        check.addActionListener {
            task { val report=Desktop.report(); val inventory=WindowsBluetooth.inventory()
                SwingUtilities.invokeLater { phones.removeAllItems(); inventory.devices.forEach(phones::addItem) }; report
            }
        }
        auth.addActionListener { task { val messages=mutableListOf<String>(); IdentityProvisioner.ensure { messages.add(it) }; (messages+"Identidade preparada e validada localmente.").joinToString("\n") } }
        probe.addActionListener {
            val phone=phones.selectedItem as? WindowsBluetooth.Device
            if(phone==null) JOptionPane.showMessageDialog(dialog,"Execute Verificar ambiente para atualizar a lista.")
            else { tabs.selectedIndex=0; task { WindowsRfcomm.probe(phone) } }
        }
        val footer=JPanel(FlowLayout(FlowLayout.RIGHT)).apply { isOpaque=false }
        footer.add(DesktopTheme.button("Salvar diagnóstico",UiIcon.Kind.LOGS).apply { addActionListener {
            val chooser=JFileChooser().apply { selectedFile=File("winplay-diagnostico.txt") }
            if(chooser.showSaveDialog(dialog)==JFileChooser.APPROVE_OPTION) {
                val file=chooser.selectedFile
                if(!file.exists()||JOptionPane.showConfirmDialog(dialog,"Substituir ${file.name}?","Salvar",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION) {
                    val report=log.text
                    object:SwingWorker<Unit,Unit>() {
                        override fun doInBackground() { file.writeText(report) }
                        override fun done() { try { get() } catch(error:Exception) { JOptionPane.showMessageDialog(dialog,"Falha ao salvar: ${error.cause?.message?:error.message}") } }
                    }.execute()
                }
            }
        } }); root.add(footer,BorderLayout.SOUTH)
        dialog.contentPane=root; dialog.setSize(900,600); dialog.setLocationRelativeTo(owner); dialog.isVisible=true
    }
}
