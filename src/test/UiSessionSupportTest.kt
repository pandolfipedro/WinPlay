package diplay.desktop

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiSessionSupportTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun delayedCallbackCannotReachNewConnectionAfterStop() {
        val gate=AttemptGate(); val previous=gate.begin()
        val ready=CountDownLatch(1); val release=CountDownLatch(1)
        val executor=Executors.newSingleThreadExecutor()
        try {
            val callback=executor.submit<Boolean> { ready.countDown(); release.await(2,TimeUnit.SECONDS); gate.accepts(previous) }
            assertTrue(ready.await(2,TimeUnit.SECONDS)); gate.cancel(); val current=gate.begin(); release.countDown()
            assertFalse(callback.get(2,TimeUnit.SECONDS)); assertTrue(gate.accepts(current))
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun logRetainsRecentEntriesInOrderUnderConcurrentLoad() {
        val log=SessionLog(100)
        val executor=Executors.newFixedThreadPool(4)
        try {
            val writes=(1..4).map { worker->executor.submit { repeat(250) { log.append("worker=$worker line=$it") } } }
            writes.forEach { it.get(2,TimeUnit.SECONDS) }
            val (sequence,text)=log.snapshot(); assertEquals(1000L,sequence); assertEquals(100,text.lines().size)
            log.append("last-entry"); assertTrue(log.snapshot().second.endsWith("last-entry"))
            log.clear(); assertEquals("",log.snapshot().second)
        } finally { executor.shutdownNow() }
    }
    @Test fun pointerOutsideLetterboxIsIgnoredButReleaseIsClamped() {
        val area=VideoGeometry.fit(1000,1000,1280,720)
        assertEquals(219,area.y); assertEquals(562,area.height)
        assertNull(VideoGeometry.point(area,20,20))
        assertEquals(0.5,VideoGeometry.point(area,500,500)!!.first,0.001)
        val release=VideoGeometry.point(area,1500,1500,release=true)!!
        assertEquals(1.0,release.first,0.0); assertEquals(1.0,release.second,0.0)
        assertNull(VideoGeometry.point(VideoGeometry.fit(0,0,1280,720),0,0,true))
    }
    @Test fun connectionChoicesRoundTripWithoutCredentialFields() {
        val file=File(temporary.root,"settings/connection.properties")
        val choice=ConnectionPreferences(0x112233445566,"192.168.1.20","Casa Wi-Fi ç")
        choice.save(file); assertEquals(choice,ConnectionPreferences.load(file))
        val properties=java.util.Properties(); file.inputStream().use(properties::load)
        assertEquals(setOf("device","address","ssid"),properties.stringPropertyNames())
        file.writeText("device=invalid")
        assertEquals(ConnectionPreferences(),ConnectionPreferences.load(file))
    }
    @Test fun oversizedLogMessageIsBounded() {
        val log=SessionLog(2); log.append("x".repeat(100000))
        assertTrue(log.snapshot().second.length<4050)
    }
    @Test fun burstsOfLargeMessagesDoNotCreateMegabytesOfUiText() {
        val log=SessionLog(500); repeat(500) { log.append("$it " + "x".repeat(4000)) }
        val text=log.snapshot().second
        assertTrue(text.length<=100000); assertTrue(text.contains("499 ")); assertFalse(text.contains(" 0 "))
    }
}
