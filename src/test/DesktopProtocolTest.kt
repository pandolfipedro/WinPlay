package diplay.desktop

import com.shilapi.xcertplay.iap2.wire.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import java.net.InetAddress
import java.net.ServerSocket

class DesktopProtocolTest {
    @Test fun pendingConnectionCodesAndUnknownErrorAreNotMisreported() {
        assertTrue(WindowsRfcomm.isConnectPending(10035))
        assertTrue(WindowsRfcomm.isConnectPending(10036))
        assertFalse(WindowsRfcomm.isConnectPending(0))
        assertFalse(WindowsRfcomm.isConnectPending(10061))
        assertTrue(WindowsRfcomm.failure("connect", 10061).message!!.contains("WSAECONNREFUSED"))
        assertFalse(WindowsRfcomm.failure("connect", 0).message!!.contains("Windows 0"))
    }

    @Test fun nativeWinsockFailureRetainsErrorAcrossJnaBoundary() {
        val api = Native.load("Ws2_32", WindowsRfcomm.Api::class.java)
        assertEquals(0, api.WSAStartup(0x202.toShort(), Memory(512)))
        try {
            val error = runCatching { WindowsRfcomm.finishConnection(api, -1L, Memory(30).apply { clear() }, 100) }.exceptionOrNull()
            assertTrue(error is java.io.IOException)
            assertTrue(error!!.message, error.message!!.contains("Windows 10038"))
            assertTrue(error.message!!.contains("WSAENOTSOCK"))
        } finally { api.WSACleanup() }
    }

    @Test fun nativeNonblockingConnectionCompletesAgainstLocalListener() {
        val api = Native.load("Ws2_32", WindowsRfcomm.Api::class.java)
        assertEquals(0, api.WSAStartup(0x202.toShort(), Memory(512)))
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { listener ->
                listener.soTimeout = 2_000
                val socket = api.socket(2, 1, 6)
                assertNotEquals(-1L, socket)
                try {
                    assertEquals(0, api.ioctlsocket(socket, 0x8004667e.toInt(), IntByReference(1)))
                    val target = Memory(16).apply {
                        clear(); setShort(0, 2)
                        setByte(2, (listener.localPort ushr 8).toByte()); setByte(3, listener.localPort.toByte())
                        setByte(4, 127); setByte(7, 1)
                    }
                    WindowsRfcomm.finishConnection(api, socket, target, 2_000)
                    listener.accept().use { assertTrue(it.isConnected) }
                } finally { api.closesocket(socket) }
            }
        } finally { api.WSACleanup() }
    }

    @Test fun windowsBluetoothAddressUsesPackedSdkLayout() {
        val bytes = WindowsRfcomm.addressBytes(0x112233445566L)
        assertEquals(30, bytes.size)
        val expected = "20 00 66 55 44 33 22 11 00 00 00 00 00 00 ca de de fa de ca de af de ca ca fe 00 00 00 00".split(" ").map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, bytes)
        assertTrue(runCatching { WindowsRfcomm.addressBytes(0) }.isFailure)
        assertTrue(runCatching { WindowsRfcomm.addressBytes(0x1000000000000L) }.isFailure)
    }
    @Test fun localNegotiationAndFramingChecks() { assertEquals(5, ProtocolChecks.run().size) }

    @Test fun deterministicRandomTransportChunkingPreservesOrder() {
        val random = Random(721)
        val expected = List(80) { index -> Iap2Frame(index, random.nextBytes(random.nextInt(0, 2048))) }
        val wire = expected.fold(byteArrayOf()) { all, frame -> all + frame.encodedFrame() }
        val decoder = Iap2CsmFramer()
        val actual = mutableListOf<Iap2Frame>()
        var offset = 0
        while (offset < wire.size) {
            val end = minOf(wire.size, offset + random.nextInt(1, 4096))
            actual += decoder.offer(wire.copyOfRange(offset, end)); offset = end
        }
        assertEquals(expected, actual)
    }

    @Test fun frameAndParameterPayloadsCannotBeMutatedByCaller() {
        val original = byteArrayOf(1, 2, 3)
        val frame = Iap2Frame(3, original)
        val parameter = Iap2Parameter(4, original)
        original[0] = 7
        frame.payload[1] = 8
        parameter.payload[2] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), frame.payload)
        assertArrayEquals(byteArrayOf(1, 2, 3), parameter.payload)
    }
}
