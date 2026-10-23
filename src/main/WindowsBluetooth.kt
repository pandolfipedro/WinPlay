package diplay.desktop

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException

/** Windows x64 BLUETOOTH_DEVICE_INFO and SEARCH_PARAMS, Windows SDK layouts. */
object WindowsBluetooth {
    interface Api : StdCallLibrary {
        fun BluetoothFindFirstRadio(params: Pointer, radio: PointerByReference): Pointer?
        fun BluetoothFindNextRadio(find: Pointer, radio: PointerByReference): Int
        fun BluetoothFindRadioClose(find: Pointer): Int
        fun BluetoothGetRadioInfo(radio: Pointer, info: Pointer): Int
        fun BluetoothFindFirstDevice(params: Pointer, info: Pointer): Pointer?
        fun BluetoothFindNextDevice(find: Pointer, info: Pointer): Int
        fun BluetoothFindDeviceClose(find: Pointer): Int
    }
    interface Kernel : StdCallLibrary { fun CloseHandle(handle: Pointer): Int }
    data class Device(val name: String, val address: Long, val paired: Boolean, val connected: Boolean) {
        override fun toString() = "$name — ${if (paired) "pareado" else "lembrado"}${if (connected) ", conectado" else ""}"
    }
    data class Inventory(val radios: Int, val devices: List<Device>, val localAddresses: List<String> = emptyList())
    private val api by lazy {
        val path = java.io.File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/bthprops.cpl")
        try { Native.load(path.absolutePath, Api::class.java) }
        catch (ex: UnsatisfiedLinkError) { throw IOException("API Bluetooth do Windows indisponível", ex) }
    }
    private val kernel by lazy { Native.load("kernel32", Kernel::class.java) }

    fun inventory(): Inventory {
        check(Native.POINTER_SIZE == 8) { "Esta versão requer Windows x64" }
        val radioParams = Memory(4).apply { clear(); setInt(0, 4) }
        val radio = PointerByReference()
        var radios = 0
        val addresses = mutableListOf<String>()
        val search = api.BluetoothFindFirstRadio(radioParams, radio)
        if (search != null) {
            try {
                do {
                    radios++
                    try {
                        val info = Memory(520).apply { clear(); setInt(0, 520) }
                        if (api.BluetoothGetRadioInfo(radio.value, info) == 0) {
                            val address = info.getLong(8)
                            addresses += (5 downTo 0).joinToString(":") { "%02X".format((address ushr (it * 8)) and 255) }
                        }
                    } finally { kernel.CloseHandle(radio.value) }
                }
                while (api.BluetoothFindNextRadio(search, radio) != 0)
                val error = Native.getLastError()
                if (error != 259) throw IOException("BluetoothFindNextRadio: Windows $error")
            } finally { api.BluetoothFindRadioClose(search) }
        } else {
            val error = Native.getLastError()
            if (error !in listOf(259, 1168)) throw IOException("BluetoothFindFirstRadio: Windows $error")
        }
        if (radios == 0) return Inventory(0, emptyList())
        // Cached devices only: does not pair devices or start a radio inquiry.
        val params = Memory(40).apply {
            clear(); setInt(0, 40); setInt(4, 1); setInt(8, 1); setInt(16, 1)
        }
        val info = Memory(560).apply { clear(); setInt(0, 560) }
        val devices = mutableListOf<Device>()
        val find = api.BluetoothFindFirstDevice(params, info)
        if (find == null) {
            val error = Native.getLastError()
            if (error !in listOf(259, 1168)) throw IOException("BluetoothFindFirstDevice: Windows $error")
            return Inventory(radios, emptyList(), addresses)
        }
        try {
            do {
                val name = info.getByteArray(64, 496).toString(Charsets.UTF_16LE).substringBefore('\u0000')
                devices += Device(name.ifBlank { "Dispositivo sem nome" }, info.getLong(8), info.getInt(24) != 0, info.getInt(20) != 0)
                info.clear(); info.setInt(0, 560)
            } while (api.BluetoothFindNextDevice(find, info) != 0)
            val error = Native.getLastError()
            if (error != 259) throw IOException("BluetoothFindNextDevice: Windows $error")
        } finally { api.BluetoothFindDeviceClose(find) }
        return Inventory(radios, devices.distinctBy { it.address }, addresses)
    }
}
