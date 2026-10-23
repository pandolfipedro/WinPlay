package diplay.desktop

import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationClient
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2WirelessIdentification
import java.io.File
import java.io.IOException

object AuthenticationProbe {
    fun run(device: WindowsBluetooth.Device, localMac: String, ssid: String, directory: File): String {
        require(device.paired)
        val messages = mutableListOf("Verificação da identidade local…")
        try {
            val identity = LocalMfiAuthenticationClient.load(directory)
            val config = Iap2IdentificationConfig(
                name = "WinPlay", modelIdentifier = "Windows Desktop",
                manufacturer = "Experimental", serialNumber = "diplay-windows-preflight",
                firmwareVersion = "0.1.0", hardwareVersion = "1",
                wireless = Iap2WirelessIdentification(localMac, ssid),
            )
            messages += "Identidade local válida. Conectando RFCOMM…"
            WindowsRfcomm.connect(device.address).use { stream ->
                messages += "RFCOMM conectado. Negociando enlace iAP2…"
                Iap2Session.openWireless(stream).use { session ->
                    if (!session.awaitReady(10_000)) throw IOException("Enlace iAP2 não ficou pronto")
                    messages += "Enlace pronto. Enviando identificação…"
                    Iap2IdentificationClient(session).identify(config, 15_000) { messages += it }
                    messages += "Identificação aceita. Autenticando…"
                    Iap2MfiAuthenticationClient(identity).run(session, 15_000) { messages += it }
                    messages += "AUTENTICAÇÃO ACEITA PELO DISPOSITIVO."
                }
            }
            messages += "Teste concluído; canal encerrado. Sessão CarPlay de rede, vídeo e áudio ainda não implementados."
        } catch (ex: Exception) {
            messages += "FALHA: ${ex.message}"
            messages += "CarPlay não iniciado. A etapa acima indica onde investigar."
        }
        return messages.joinToString("\n")
    }
}
