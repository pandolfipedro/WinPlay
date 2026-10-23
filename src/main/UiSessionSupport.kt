package diplay.desktop

import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong

/** Invalidates queued UI and input callbacks when a connection is replaced or stopped. */
class AttemptGate {
    private val generation=AtomicLong()
    fun begin()=generation.incrementAndGet()
    fun cancel() { generation.incrementAndGet() }
    fun accepts(token:Long)=generation.get()==token
}

class SessionLog(private val limit:Int=500) {
    init { require(limit>0) }
    private val entries=ArrayDeque<String>()
    private var characters=0
    private var sequence=0L
    @Synchronized fun append(message:String) {
        val time=LocalTime.now(ZoneId.of("America/Sao_Paulo")).format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val entry="$time  ${message.take(4000)}"
        entries.addLast(entry); characters+=entry.length+1
        while(entries.size>limit||characters>100_000) characters-=entries.removeFirst().length+1
        sequence++
    }
    @Synchronized fun snapshot()=sequence to entries.joinToString("\n")
    @Synchronized fun clear() { entries.clear(); characters=0; sequence++ }
}

object VideoGeometry {
    fun fit(containerWidth:Int,containerHeight:Int,imageWidth:Int,imageHeight:Int):Rectangle {
        if(containerWidth<=0||containerHeight<=0||imageWidth<=0||imageHeight<=0) return Rectangle()
        val scale=minOf(containerWidth.toDouble()/imageWidth,containerHeight.toDouble()/imageHeight)
        val width=maxOf(1,(imageWidth*scale).toInt()); val height=maxOf(1,(imageHeight*scale).toInt())
        return Rectangle((containerWidth-width)/2,(containerHeight-height)/2,width,height)
    }
    fun point(area:Rectangle,x:Int,y:Int,release:Boolean=false):Pair<Double,Double>? {
        if(area.width<=0||area.height<=0||(!release&&!area.contains(x,y))) return null
        return ((x-area.x).toDouble()/area.width).coerceIn(0.0,1.0) to ((y-area.y).toDouble()/area.height).coerceIn(0.0,1.0)
    }
}

data class ConnectionPreferences(val device:Long=0,val address:String="",val ssid:String="") {
    fun save(file:File=File(IdentityProvisioner.dataDirectory(),"connection-ui.properties")) {
        file.parentFile.mkdirs()
        val properties=Properties().apply { setProperty("device",device.toString()); setProperty("address",address); setProperty("ssid",ssid) }
        val temp=Files.createTempFile(file.parentFile.toPath(),"ui-preferences-",".tmp")
        try { Files.newOutputStream(temp).use { properties.store(it,"Connection choices; no Wi-Fi password") }; Files.move(temp,file.toPath(),StandardCopyOption.REPLACE_EXISTING) }
        finally { Files.deleteIfExists(temp) }
    }
    companion object {
        fun load(file:File=File(IdentityProvisioner.dataDirectory(),"connection-ui.properties")):ConnectionPreferences = runCatching {
            val properties=Properties(); file.inputStream().use(properties::load)
            ConnectionPreferences(properties.getProperty("device","0").toLong(),properties.getProperty("address",""),properties.getProperty("ssid",""))
        }.getOrDefault(ConnectionPreferences())
    }
}
