package diplay.desktop

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.Native
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2LinkEngine
import java.io.IOException
import java.util.UUID

/** AF_BTH/RFCOMM Windows x64 adapter. Peer negotiation needs hardware acceptance. */
class WindowsRfcomm private constructor(private val api: Api, private val socket: Long) : BlockingDuplexByteStream {
    interface Api : StdCallLibrary {
        fun WSAStartup(version: Short, data: Pointer): Int
        fun WSACleanup(): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun ioctlsocket(socket: Long, command: Int, value: IntByReference): Int
        fun connect(socket: Long, address: Pointer, length: Int): Int
        fun closesocket(socket: Long): Int
        fun send(socket: Long, bytes: ByteArray, length: Int, flags: Int): Int
        fun recv(socket: Long, bytes: ByteArray, length: Int, flags: Int): Int
        fun select(ignored: Int, read: Pointer?, write: Pointer?, except: Pointer?, timeout: Pointer): Int
        fun getsockopt(socket: Long, level: Int, option: Int, value: IntByReference, size: IntByReference): Int
    }
    @Volatile private var closed = false
    private fun waitReady(read: Boolean, millis: Long): Boolean {
        check(!closed) { "Canal encerrado" }
        val set = Memory(16).apply { clear(); setInt(0, 1); setLong(8, socket) }
        val timeout = Memory(8).apply { setInt(0, (millis / 1000).toInt()); setInt(4, ((millis % 1000) * 1000).toInt()) }
        val result = api.select(0, if (read) set else null, if (read) null else set, null, timeout)
        if (result < 0) throw failure("select RFCOMM", Native.getLastError())
        return result > 0
    }
    override fun send(data: ByteArray) {
        var offset = 0
        val deadline = System.nanoTime() + 5_000_000_000L
        while (offset < data.size) {
            if (System.nanoTime() >= deadline) throw IOException("Tempo esgotado enviando RFCOMM")
            if (!waitReady(false, 100)) continue
            val bytes = data.copyOfRange(offset, data.size)
            val count = api.send(socket, bytes, bytes.size, 0)
            if (count <= 0) {
                val error = if (count < 0) Native.getLastError() else 0
                if (count < 0 && error == 10035) continue
                if (count == 0) throw IOException("send RFCOMM: o canal não aceitou mais dados")
                throw failure("send RFCOMM", error)
            }
            offset += count
        }
    }
    override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? {
        require(maxBytes in 1..1_048_576 && timeoutMillis in 0..60_000)
        if (!waitReady(true, timeoutMillis)) return null
        val bytes = ByteArray(maxBytes)
        val count = api.recv(socket, bytes, bytes.size, 0)
        if (count < 0) {
            val error = Native.getLastError()
            if (error == 10035) return null
            throw failure("recv RFCOMM", error)
        }
        return bytes.copyOf(count)
    }
    @Synchronized override fun close() {
        if (!closed) { closed = true; api.closesocket(socket); api.WSACleanup() }
    }
    companion object {
        // JNA snapshots GetLastError as the native function returns. Calling WSAGetLastError
        // through another JNA invocation can reset the Windows thread error to zero.
        // Read Native.getLastError immediately after a failed call, before cleanup or other I/O.
        internal fun failure(operation: String, code: Int): IOException {
            val reason = when (code) {
                0 -> "a operação falhou, mas o código nativo não foi disponibilizado"
                10013 -> "acesso ao socket negado (WSAEACCES)"
                10022 -> "parâmetro ou estado da operação inválido (WSAEINVAL)"
                10038 -> "handle de socket inválido (WSAENOTSOCK)"
                10047 -> "família de endereços não suportada (WSAEAFNOSUPPORT)"
                10050 -> "rede ou adaptador indisponível (WSAENETDOWN)"
                10051 -> "dispositivo/rede sem rota disponível (WSAENETUNREACH)"
                10054 -> "o dispositivo encerrou a conexão (WSAECONNRESET)"
                10060 -> "tempo de conexão esgotado (WSAETIMEDOUT)"
                10061 -> "conexão recusada pelo destino (WSAECONNREFUSED)"
                10064 -> "destino indisponível (WSAEHOSTDOWN)"
                10065 -> "destino inacessível (WSAEHOSTUNREACH)"
                10108 -> "serviço solicitado não encontrado (WSASERVICE_NOT_FOUND)"
                10109 -> "tipo de serviço solicitado não encontrado (WSATYPE_NOT_FOUND)"
                11001 -> "destino/serviço não localizado (WSAHOST_NOT_FOUND)"
                else -> "falha nativa de socket"
            }
            return IOException("$operation: ${if (code == 0) "código indisponível" else "Windows $code"} — $reason")
        }
        internal fun isConnectPending(error: Int): Boolean = error == 10035 || error == 10036
        /** Shared by RFCOMM and native loopback regression tests; socket is already nonblocking. */
        internal fun finishConnection(api: Api, socket: Long, target: Memory, timeoutMillis: Long) {
            if (api.connect(socket, target, target.size().toInt()) == 0) return
            val error = Native.getLastError()
            if (!isConnectPending(error)) throw failure("connect RFCOMM (serviço iAP2)", error)
            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            // Connect can complete through writefds or fail through exceptfds.
            while (System.nanoTime() < deadline) {
                val write = Memory(16).apply { clear(); setInt(0, 1); setLong(8, socket) }
                val except = Memory(16).apply { clear(); setInt(0, 1); setLong(8, socket) }
                val timeout = Memory(8).apply { clear(); setInt(4, 100_000) }
                val selected = api.select(0, null, write, except, timeout)
                if (selected < 0) throw failure("connect select", Native.getLastError())
                if (selected == 0) continue
                val result = IntByReference()
                if (api.getsockopt(socket, 0xffff, 0x1007, result, IntByReference(4)) != 0)
                    throw failure("SO_ERROR", Native.getLastError())
                if (result.value != 0) throw failure("Conclusão da conexão RFCOMM", result.value)
                return
            }
            throw IOException("Tempo esgotado conectando RFCOMM (${timeoutMillis} ms)")
        }
        const val IPHONE_SERVICE = "00000000-deca-fade-deca-deafdecacafe"
        fun addressBytes(address: Long): ByteArray {
            require(address in 1..0xffffffffffffL)
            // ws2bth.h uses pack(1): USHORT, ULONG64, GUID, ULONG = 30 bytes.
            val memory = Memory(30).apply { clear(); setShort(0, 32); setLong(2, address) }
            val uuid = UUID.fromString(IPHONE_SERVICE)
            memory.setInt(10, (uuid.mostSignificantBits ushr 32).toInt())
            memory.setShort(14, (uuid.mostSignificantBits ushr 16).toShort())
            memory.setShort(16, uuid.mostSignificantBits.toShort())
            repeat(8) { memory.setByte((18 + it).toLong(), (uuid.leastSignificantBits ushr (56 - 8 * it)).toByte()) }
            return memory.getByteArray(0, 30)
        }
        fun connect(address: Long, timeoutMillis: Long = 12_000): WindowsRfcomm {
            require(timeoutMillis in 1..30_000)
            val encoded = addressBytes(address)
            val api = Native.load("Ws2_32", Api::class.java)
            val startup = api.WSAStartup(0x202.toShort(), Memory(512))
            if (startup != 0) throw failure("WSAStartup", startup)
            val socket = api.socket(32, 1, 3)
            if (socket == -1L) {
                val error = Native.getLastError(); api.WSACleanup()
                throw failure("socket RFCOMM", error)
            }
            val stream = WindowsRfcomm(api, socket)
            try {
                if (api.ioctlsocket(socket, 0x8004667e.toInt(), IntByReference(1)) != 0)
                    throw failure("ioctlsocket", Native.getLastError())
                val target = Memory(30).apply { write(0, encoded, 0, encoded.size) }
                finishConnection(api, socket, target, timeoutMillis)
                return stream
            } catch (ex: Throwable) { stream.close(); throw ex }
        }
        fun probe(device: WindowsBluetooth.Device): String {
            require(device.paired) { "Pareie o iPhone nas configurações do Windows antes deste teste" }
            return connect(device.address).use { stream ->
                val engine = Iap2LinkEngine()
                val start = System.nanoTime()
                fun elapsed() = (System.nanoTime() - start) / 1_000_000
                engine.start(wiredInitiator = false, nowMillis = 0)
                while (elapsed() < 10_000) {
                    engine.advanceTime(elapsed())
                    engine.takeOutput().let { if (it.isNotEmpty()) stream.send(it) }
                    val data = stream.recv(16_384, 100)
                    if (data != null) {
                        if (data.isEmpty()) throw IOException("O telefone encerrou o canal RFCOMM")
                        engine.feed(data, elapsed())
                    }
                    if (engine.writable()) return@use "RFCOMM aberto e enlace iAP2 negociado com o dispositivo.\nAutenticação e sessão CarPlay: ainda não iniciadas."
                    if (engine.state() == Iap2LinkEngine.State.DEAD) throw IOException("O enlace iAP2 foi encerrado")
                }
                "RFCOMM abriu, mas a negociação iAP2 não completou em 10 segundos.\nIsso não comprova CarPlay."
            }
        }
    }
}
