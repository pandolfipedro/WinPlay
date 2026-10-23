package diplay.desktop

import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import java.awt.*
import java.io.File
import java.net.NetworkInterface
import java.time.Instant
import java.util.Collections
import javax.swing.*
import kotlin.system.exitProcess

object Desktop {
    const val VERSION = "0.3.1"
    private const val STATUS = "CarPlay sem fio para Windows"
    fun networkReport(): String = Collections.list(NetworkInterface.getNetworkInterfaces()).filter { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }.joinToString("\n") {
        "${it.displayName}: " + Collections.list(it.inetAddresses).filter { address -> !address.isLoopbackAddress }.joinToString { address -> address.hostAddress }
    }.ifBlank { "Nenhuma interface ativa" }

    fun report(): String = buildString {
        appendLine("WinPlay $VERSION")
        appendLine(STATUS)
        appendLine("UTC: ${Instant.now()}")
        appendLine("Sistema: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("Java: ${System.getProperty("java.version")}")
        appendLine("\nPROTOCOLO LOCAL")
        ProtocolChecks.run().forEach { appendLine("PASS: $it") }
        appendLine("\nCODECS NATIVOS (sem gravação do microfone)")
        try { MediaChecks.run().forEach { appendLine("PASS: $it") } }
        catch(error:Exception) { appendLine("Falha de codec: ${error.message}") }
        catch(error:LinkageError) { appendLine("Falha carregando bibliotecas nativas: ${error.message}") }
        appendLine("\nREDE (endereços locais; revise antes de compartilhar)")
        appendLine(networkReport())
        appendLine("\nBLUETOOTH")
        try {
            val inventory = WindowsBluetooth.inventory()
            appendLine("Rádios: ${inventory.radios}")
            appendLine("Dispositivos lembrados/pareados: ${inventory.devices.size}")
            inventory.devices.forEachIndexed { i, device -> appendLine("Dispositivo ${i + 1}: pareado=${device.paired}, conectado=${device.connected} (nome e endereço omitidos)") }
        } catch (ex: Exception) { appendLine("Falha ao consultar Bluetooth: ${ex.message}") }
        appendLine("\nFuncionamento da versão 0.2.0 confirmado pelo usuário em iPhone 15 Pro Max / iOS 27. Interface WinPlay 0.3.1 validada localmente. USB não implementado.")
        appendLine("Os testes locais não autenticam um iPhone.")
    }

    @JvmStatic fun main(args: Array<String>) {
        try {
            System.setProperty("org.bytedeco.javacpp.cachedir",File(IdentityProvisioner.dataDirectory(),"native-cache").absolutePath)
            if(args.firstOrNull()=="--write-icon") { require(args.size==2); BrandAssets.writeIcon(File(args[1])); return }
            if(args.firstOrNull()=="--prepare-auth") {
                require(args.size==2)
                val messages=mutableListOf("WinPlay $VERSION")
                try {
                    IdentityProvisioner.ensure { messages.add(it) }
                    messages.add("PASS: identidade oficial do preview preparada e validada localmente; aceite do iPhone não testado")
                } catch(error:Exception) {
                    messages.add("FAIL: ${error.message}"); File(args[1]).writeText(messages.joinToString("\n")); throw error
                }
                File(args[1]).writeText(messages.joinToString("\n")); return
            }
            if (args.firstOrNull() == "--self-test") {
                val results = ProtocolChecks.run()
                results.forEach { println("PASS: $it") }
                println("PASS: ${results.size} grupos; sem conexão física")
                return
            }
            if (args.firstOrNull() == "--render-preview") {
                require(args.size == 2 || args.size == 4 || args.size == 5)
                SwingUtilities.invokeLater { show(File(args[1]), args.getOrNull(2)?.toInt() ?: 1280, args.getOrNull(3)?.toInt() ?: 820, args.getOrNull(4) == "large") }
                return
            }
            if (args.firstOrNull() == "--diagnostics") {
                require(args.size == 2) { "Uso: --diagnostics arquivo.txt" }
                File(args[1]).writeText(report(), Charsets.UTF_8)
                return
            }
            require(args.isEmpty()) { "Argumento desconhecido" }
            SwingUtilities.invokeLater { show() }
        } catch (ex: Exception) { System.err.println("Falha: ${ex.message}"); exitProcess(1) }
    }

    fun show(preview: File? = null, width: Int = 1280, height: Int = 820, largeFont: Boolean = false) {
        DesktopTheme.install(largeFont)
        val frame = JFrame("WinPlay · CarPlay para Windows")
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.minimumSize = Dimension(940, 680)
        lateinit var panel: ReceiverPanel
        panel = ReceiverPanel { DiagnosticsDialog.show(frame) { panel.hasSession() } }
        frame.contentPane = panel
        frame.iconImage = BrandAssets.image(64)
        frame.addWindowListener(object: java.awt.event.WindowAdapter() {
            override fun windowClosing(event: java.awt.event.WindowEvent) {
                frame.isVisible = false
                panel.shutdown { frame.dispose(); exitProcess(0) }
            }
        })
        frame.setSize(width, height); frame.setLocationRelativeTo(null)
        if(preview==null) frame.isVisible=true else { frame.addNotify(); frame.validate() }
        if (preview == null) SwingUtilities.invokeLater { panel.refresh() }
        else Timer(800) {
            val image = java.awt.image.BufferedImage(panel.width, panel.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics(); panel.printAll(g); g.dispose()
            javax.imageio.ImageIO.write(image, "png", preview)
            panel.shutdown { frame.dispose(); exitProcess(0) }
        }.apply { isRepeats = false; start() }
    }
}
