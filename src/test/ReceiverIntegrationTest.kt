package diplay.desktop

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.junit.rules.TemporaryFolder
import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import java.io.File
import java.net.InetAddress
import org.bytedeco.ffmpeg.avcodec.*
import org.bytedeco.ffmpeg.avutil.*
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.ffmpeg.global.avutil.*
import org.bytedeco.javacpp.BytePointer

class ReceiverIntegrationTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun alteredApkIsRejectedBeforeIdentityImport() {
        val apk=temporary.newFile("altered.apk"); apk.writeText("not official")
        assertTrue(runCatching { IdentityProvisioner.importVerified(apk,File(temporary.root,"out")) }.isFailure)
        assertFalse(File(temporary.root,"out/identity.pk8").exists())
    }
    @Test fun pinnedPublicApkLoadsWithoutUserSuppliedCredentials() {
        val path=System.getenv("DIPLAY_TEST_PUBLIC_APK")
        assumeTrue("Optional local public APK fixture",path!=null && File(path).isFile)
        val directory=IdentityProvisioner.importVerified(File(path!!),File(temporary.root,"verified"))
        val client=LocalMfiAuthenticationClient.load(directory)
        assertEquals(64,client.signChallenge(ByteArray(32) { it.toByte() }).size)
    }
    @Test fun opusMicrophoneEncodingAndPlaybackDecodingRoundTrip() {
        val packets=mutableListOf<ByteArray>()
        NativeCodecs.OpusEncoder(1).use { encoder -> repeat(3) { encoder.encode(ByteArray(1920)) { packets+=it } } }
        assertTrue(packets.isNotEmpty())
        var decoded=0
        NativeCodecs.Audio(true,48000,1).use { decoder -> packets.forEach { decoder.decode(it) { pcm -> decoded+=pcm.size } } }
        assertTrue(decoded>=1920)
    }
    @Test fun aacMusicEncodingAndDecodingProducesStereoPcm() {
        val codec=avcodec_find_encoder(AV_CODEC_ID_AAC)
        val context=avcodec_alloc_context3(codec); val frame=av_frame_alloc(); val packet=av_packet_alloc()
        try {
            context.sample_rate(48000).sample_fmt(AV_SAMPLE_FMT_FLTP).bit_rate(128000)
            context.time_base().num(1).den(48000); av_channel_layout_default(context.ch_layout(),2)
            assertEquals(0,avcodec_open2(context,codec,null as AVDictionary?))
            frame.format(AV_SAMPLE_FMT_FLTP).sample_rate(48000).nb_samples(1024)
            av_channel_layout_copy(frame.ch_layout(),context.ch_layout()); assertEquals(0,av_frame_get_buffer(frame,0))
            var decoded=0
            NativeCodecs.Audio(false,48000,2).use { decoder ->
                fun drain() { while(avcodec_receive_packet(context,packet)==0) {
                    val bytes=ByteArray(packet.size()); packet.data().get(bytes)
                    decoder.decode(bytes) { decoded+=it.size }; av_packet_unref(packet)
                } }
                repeat(3) { index ->
                    assertEquals(0,av_frame_make_writable(frame))
                    repeat(2) { channel -> frame.data(channel).position(0).put(*ByteArray(4096)) }
                    frame.pts(index*1024L); assertEquals(0,avcodec_send_frame(context,frame)); drain()
                }
                assertEquals(0,avcodec_send_frame(context,null as AVFrame?)); drain()
            }
            assertTrue(decoded>=4096)
        } finally { av_packet_free(packet); av_frame_free(frame); avcodec_free_context(context) }
    }
    @Test fun h264EncodeDecodeProducesExpectedImage() {
        val codec=avcodec_find_encoder_by_name("libopenh264")
        assertNotNull(codec); assertFalse(codec.isNull)
        val context=avcodec_alloc_context3(codec)
        val frame=av_frame_alloc(); val packet=av_packet_alloc()
        try {
            context.width(64).height(48).pix_fmt(AV_PIX_FMT_YUV420P).max_b_frames(0).gop_size(1).thread_count(1).bit_rate(200000)
            context.time_base().num(1).den(30)
            assertEquals(0,avcodec_open2(context,codec,null as AVDictionary?))
            frame.format(AV_PIX_FMT_YUV420P).width(64).height(48); assertEquals(0,av_frame_get_buffer(frame,32))
            repeat(3) { plane ->
                val height=if(plane==0) 48 else 24
                val size=frame.linesize(plane)*height
                frame.data(plane).put(*ByteArray(size) { if(plane==0) 96.toByte() else 128.toByte() })
            }
            frame.pts(0); assertEquals(0,avcodec_send_frame(context,frame))
            assertEquals(0,avcodec_receive_packet(context,packet))
            val bytes=ByteArray(packet.size()); packet.data().get(bytes)
            var frames=0
            NativeCodecs.Video(ByteArray(0)).use { decoder -> decoder.decode(bytes) { image ->
                assertEquals(64,image.width); assertEquals(48,image.height); frames++
            } }
            assertEquals(1,frames)
        } finally { av_packet_free(packet); av_frame_free(frame); avcodec_free_context(context) }
    }
    @Test fun malformedAvcConfigurationIsRejected() {
        assertTrue(runCatching { WindowsMediaSink.avcToAnnexB(ByteArray(7)) }.isFailure)
        val config=byteArrayOf(1,66,0,30,-1,-31,0,2,0x67,1,1,0,2,0x68,2)
        assertArrayEquals(byteArrayOf(0,0,0,1,0x67,1,0,0,0,1,0x68,2),WindowsMediaSink.avcToAnnexB(config))
    }
    @Test fun networkSettingsValidateBeforeConnection() {
        val phone=WindowsBluetooth.Device("Test",1,true,false)
        val ip=InetAddress.getByName("127.0.0.1") as java.net.Inet4Address
        assertTrue(runCatching { ReceiverSettings(phone,ip,"","") }.isFailure)
        assertTrue(runCatching { ReceiverSettings(phone,ip,"WiFi","short") }.isFailure)
        assertEquals("WiFi",ReceiverSettings(phone,ip,"WiFi","longpassword").ssid)
    }
    @Test fun airplayInfoRequestRunsThroughRealTcpSession() {
        val loopback=InetAddress.getByName("127.0.0.1")
        java.net.ServerSocket(0,1,loopback).use { server ->
            java.net.Socket(loopback,server.localPort).use { client ->
                client.soTimeout=3000
                val config=com.shilapi.xcertplay.airplay.AirPlayConfig("Test","02:00:00:00:00:02","02:00:00:00:00:02","950.7.1",com.shilapi.xcertplay.airplay.AirPlayDisplayConfig(1280,720))
                val session=com.shilapi.xcertplay.airplay.AirPlaySession(server.accept(),config,
                    com.shilapi.xcertplay.airplay.AirPlayIdentity.generate(),com.shilapi.xcertplay.airplay.PairingStore(),null,
                    object:com.shilapi.xcertplay.airplay.AirPlaySessionListener {},object:com.shilapi.xcertplay.airplay.AirPlayMediaHandler {})
                session.use {
                    session.start()
                    client.getOutputStream().write("GET /info RTSP/1.0\r\nCSeq: 1\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    val input=client.getInputStream().buffered()
                    fun line():String { val bytes=java.io.ByteArrayOutputStream(); while(true) { val b=input.read(); check(b>=0); if(b==10) return bytes.toString("US-ASCII").trimEnd('\r'); bytes.write(b) } }
                    assertTrue(line().contains("200"))
                    var length=0
                    while(true) { val header=line(); if(header.isEmpty()) break; if(header.startsWith("Content-Length:",true)) length=header.substringAfter(':').trim().toInt() }
                    assertTrue(length>8)
                    val body=input.readNBytes(length)
                    assertEquals(length,body.size)
                    assertEquals("bplist00",body.copyOfRange(0,8).toString(Charsets.US_ASCII))
                }
            }
        }
    }
}
