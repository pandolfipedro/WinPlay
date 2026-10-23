package diplay.desktop

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import com.shilapi.xcertplay.transport.*
import java.awt.image.BufferedImage
import java.io.Closeable
import java.io.File
import java.net.*
import java.util.Properties
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.*

data class ReceiverSettings(val device: WindowsBluetooth.Device, val address: Inet4Address, val ssid: String, val password: String) {
    init {
        require(device.paired) { "O iPhone precisa estar pareado no Bluetooth" }
        require(ssid.isNotBlank() && '\u0000' !in ssid && '\r' !in ssid && '\n' !in ssid) { "Informe o nome exato da rede Wi-Fi" }
        require(password.isEmpty() || password.length in 8..63 || password.matches(Regex("[0-9a-fA-F]{64}"))) { "Senha WPA2 deve ter 8 a 63 caracteres ou 64 dígitos hexadecimais" }
    }
}

class DesktopReceiver(private val settings: ReceiverSettings, private val status: (String) -> Unit, private val log: (String) -> Unit,
                      private val frame: (BufferedImage) -> Unit) : Closeable {
    private val closed=AtomicBoolean(false)
    private val resources=CopyOnWriteArrayList<AutoCloseable>()
    private val sessions=CopyOnWriteArrayList<AirPlaySession>()
    private val executor=Executors.newCachedThreadPool { runnable -> Thread(runnable,"diplay-receiver").apply { isDaemon=true } }
    private val firstFrame=AtomicBoolean(false)
    @Volatile private var active: AirPlaySession?=null
    private fun <T:AutoCloseable> own(value:T):T {
        synchronized(resources) {
            if(closed.get()) { value.close(); throw java.io.IOException("Conexão cancelada") }
            resources.add(value); return value
        }
    }
    fun start() { executor.execute { try { run() } catch(error:Throwable) {
        if(!closed.get()) { log("Falha na conexão: ${error.message ?: error.javaClass.simpleName}"); status("Falha: ${error.message ?: "erro de conexão"}"); close() }
    } } }
    private fun run() {
        status("Preparando autenticação…")
        val directory=IdentityProvisioner.ensure(log)
        if(closed.get()) return
        val mfi=LocalMfiAuthenticationClient.load(directory)
        val inventory=WindowsBluetooth.inventory()
        val localMac=inventory.localAddresses.singleOrNull() ?: error("Requer um único rádio Bluetooth ativo")
        val identity=loadIdentity()
        val listenerSocket=ServerSocket().apply { reuseAddress=true }
        val socket=try { listenerSocket.bind(InetSocketAddress(settings.address,7000)); own(listenerSocket) }
        catch(error:BindException) { listenerSocket.close(); own(ServerSocket().apply { reuseAddress=true; bind(InetSocketAddress(settings.address,0)) }) }
        val config=AirPlayConfig("WinPlay",localMac,localMac,"950.7.1",AirPlayDisplayConfig(1280,720,fps=30),
            port=socket.localPort,microphone=true,microphoneOpus=true,manufacturer="WinPlay",model="WinPlay",oemLabel="WinPlay",hevc=false,mainBufferedAudio=false)
        val wireless=Iap2WirelessIdentification(localMac,settings.ssid)
        val identification=Iap2IdentificationConfig("WinPlay","WinPlay","WinPlay","diplay-windows", "0.2.0","1",wireless)
        val endpoint=Iap2WirelessCarPlayEndpoint(settings.ssid,settings.password,0,
            if(settings.password.isEmpty()) Iap2WirelessSecurity.NONE else Iap2WirelessSecurity.WPA_WPA2,
            listOf(settings.address.hostAddress),socket.localPort,localMac.replace(":",""),identity.publicKeyHex,config.sourceVersion)
        val sink=own(WindowsMediaSink({ image ->
            if(firstFrame.compareAndSet(false,true)) status("CarPlay — recebendo vídeo")
            frame(image)
        },log))
        val media=CarPlayMediaEngine(sink,microphoneEnabled=true)
        val pairings=loadPairings()
        val listener=object:AirPlaySessionListener {
            override fun onSessionActive(session:AirPlaySession) { active=session; status("Sessão de rede ativa — aguardando vídeo"); log("Sessão AirPlay/CarPlay ativa") }
            override fun onSessionEnded(session:AirPlaySession) {
                sessions.remove(session)
                if(active===session) { active=null; firstFrame.set(false); if(!closed.get()) status("Sessão encerrada — desconecte e conecte novamente") }
            }
            override fun onDebugLog(message:String) { log(message) }
            override fun onTransportError(message:String) { log("Transporte: $message") }
        }
        media.setIapTunnelHandler { underlying ->
            if(closed.get()) false else {
                executor.execute {
                    try {
                        val channel=own(Iap2Session.openTunnel(underlying))
                        log("Túnel iAP2 pela rede aceito")
                        Iap2WirelessControlClient(channel,Iap2MfiAuthenticationClient(mfi)).run(identification,endpoint,
                            timeoutMillis=Iap2WirelessControlClient.NO_TIMEOUT_MILLIS,
                            onReady={ log("Túnel iAP2 autenticado") },onProgress=log)
                    } catch(error:Exception) { if(!closed.get()) log("Túnel iAP2: ${error.message}") }
                }; true
            }
        }
        executor.execute {
            while(!closed.get()) {
                try {
                    val peer=socket.accept()
                    peer.tcpNoDelay=true; peer.keepAlive=true
                    val session=AirPlaySession(peer,config,identity,pairings,mfi,listener,media)
                    if(closed.get()) { session.close(); break }
                    sessions.add(session); log("Conexão de controle recebida pela rede"); session.start()
                } catch(error:Exception) { if(!closed.get()) log("Listener de rede: ${error.message}"); break }
            }
        }
        val dns=own(JmDNS.create(settings.address,"diplay-${localMac.replace(":","")}"))
        val feature=AirPlayInfoPlist.features(config)
        val features="0x${(feature and 0xffffffffL).toString(16)},0x${(feature ushr 32).toString(16)}"
        dns.registerService(ServiceInfo.create("_airplay._tcp.local.",config.deviceName,config.port,0,0,
            mapOf("deviceid" to config.deviceId,"features" to features,"flags" to "0x4","model" to config.model,"srcvers" to config.sourceVersion,
                "protovers" to "1.1","pi" to identity.pairingId,"pk" to identity.publicKeyHex)))
        val probing=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        dns.addServiceListener("_carplay-ctrl._tcp.local.",object:ServiceListener {
            override fun serviceAdded(event:ServiceEvent) { event.dns.requestServiceInfo(event.type,event.name,true) }
            override fun serviceRemoved(event:ServiceEvent) { probing.remove(event.name) }
            override fun serviceResolved(event:ServiceEvent) {
                val info=event.info
                // Do not probe another phone: match the selected Bluetooth transport identity.
                val bt=info.getPropertyString("id") ?: info.getPropertyString("btaddr") ?: info.getPropertyString("bluetoothAddress")
                val selected=(5 downTo 0).joinToString("") { "%02x".format((settings.device.address ushr (it*8)) and 255) }
                if(bt!=null && bt.replace(":","").replace("-","").lowercase()!=selected) return
                val remote=info.inet4Addresses.firstOrNull() ?: return
                if(info.port !in 1..65535 || !probing.add(event.name)) return
                executor.execute {
                    try { probePhone(remote,info.port,config) }
                    catch(error:Exception) { if(!closed.get()) log("Descoberta CarPlay: ${error.message}"); probing.remove(event.name) }
                }
            }
        })
        log("Serviço CarPlay publicado na interface selecionada; controle na porta ${socket.localPort}")
        status("Conectando ao iPhone por Bluetooth…")
        val stream=own(WindowsRfcomm.connect(settings.device.address))
        val channel=own(Iap2Session.openWireless(stream))
        status("Identificando e autenticando…")
        val result=Iap2WirelessControlClient(channel,Iap2MfiAuthenticationClient(mfi)).run(
            identification,endpoint,timeoutMillis=120_000,
            keepAliveAfterDeadline={ !closed.get() && active!=null },
            onStartSessionSent={ status("Aguardando sessão CarPlay pela rede…") },
            onProgress={ message -> log(message); if(message=="iap2 authentication accepted") status("Autenticado — preparando rede…") })
        log("Bootstrap Bluetooth terminou: ${result.stage}, ${result.terminal}")
        // RFCOMM may end normally while the phone hands control over to the Wi-Fi tunnel.
        if(active==null && !closed.get()) {
            Thread.sleep(10_000)
            if(active==null && !closed.get()) {
                status("Sem sessão de rede. Confira Wi-Fi, senha e permissão no firewall.")
                log("Não foi recebida sessão CarPlay. O enlace Bluetooth sozinho não comprova a conexão completa.")
            }
        }
    }
    private fun probePhone(address:InetAddress,port:Int,config:AirPlayConfig) {
        Socket().use { socket ->
            socket.bind(InetSocketAddress(settings.address,0)); socket.connect(InetSocketAddress(address,port),3_000); socket.soTimeout=3_000
            val request="GET /ctrl-int/1/connect HTTP/1.1\r\nHost: ${address.hostAddress}:$port\r\nUser-Agent: AirPlay/${config.sourceVersion}\r\nAirPlay-Receiver-Device-ID: ${config.deviceId.replace(":","")}\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            val response=socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()?.take(120)
            log("Serviço de controle do iPhone respondeu: ${response ?: "sem resposta"}")
        }
    }
    fun touch(x:Double,y:Double,down:Boolean) { active?.sendTouch(listOf(AirPlayContact(0,x.coerceIn(0.0,1.0),y.coerceIn(0.0,1.0),down))) }
    fun home() { active?.sendKnob(AirPlayKnobState(home=true)) }
    fun back() { active?.sendKnob(AirPlayKnobState(back=true)) }
    fun siri() { active?.invokeSiri() }
    override fun close() {
        if(!closed.compareAndSet(false,true)) return
        sessions.forEach { runCatching { it.close() } }; sessions.clear(); active=null
        synchronized(resources) { resources.reversed().forEach { runCatching { it.close() } }; resources.clear() }
        executor.shutdownNow()
    }
    private fun loadIdentity():AirPlayIdentity {
        val file=File(IdentityProvisioner.dataDirectory(),"airplay-identity.properties")
        val properties=Properties()
        if(file.isFile) {
            file.inputStream().use(properties::load)
            return AirPlayIdentity(java.util.Base64.getDecoder().decode(properties.getProperty("private")),
                java.util.Base64.getDecoder().decode(properties.getProperty("public")),properties.getProperty("id"))
        }
        val identity=AirPlayIdentity.generate()
        properties.setProperty("private",java.util.Base64.getEncoder().encodeToString(identity.privateKey))
        properties.setProperty("public",java.util.Base64.getEncoder().encodeToString(identity.publicKey)); properties.setProperty("id",identity.pairingId)
        file.outputStream().use { properties.store(it,"Local AirPlay receiver identity; keep private") }
        return identity
    }
    private fun loadPairings():PairingStore {
        val file=File(IdentityProvisioner.dataDirectory(),"paired-controllers.properties")
        val properties=Properties()
        if(file.isFile) file.inputStream().use(properties::load)
        val store=PairingStore { id,key -> synchronized(properties) {
            properties.setProperty(id,java.util.Base64.getEncoder().encodeToString(key))
            file.outputStream().use { properties.store(it,"Paired controller public keys") }
        } }
        properties.stringPropertyNames().toList().forEach { id -> store.save(id,java.util.Base64.getDecoder().decode(properties.getProperty(id))) }
        return store
    }
}
