package diplay.desktop

import com.shilapi.xcertplay.airplay.*
import java.awt.image.BufferedImage
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

class WindowsMediaSink(private val onFrame: (BufferedImage) -> Unit, private val log: (String) -> Unit) : MediaSink, Closeable {
    private val videoLock = Any()
    private var video: NativeCodecs.Video? = null
    private val audio = ConcurrentHashMap<AudioStreamId, AudioPlayer>()
    private val microphones = ConcurrentHashMap<AudioStreamId, Microphone>()
    private var recovery: (() -> Unit)? = null
    private var videoDiagnostic: ((String) -> Unit)? = null
    private var renderedFirstFrame = false
    override fun onVideoCodec(type: Int, codec: VideoCodec) { require(codec == VideoCodec.H264) { "Esta versão anuncia vídeo H.264" } }
    override fun onVideoConfig(type: Int, codecData: ByteArray) {
        if (type != 110) return
        synchronized(videoLock) { video?.close(); video = NativeCodecs.Video(avcToAnnexB(codecData)); renderedFirstFrame=false }
        log("Decodificador H.264 preparado")
    }
    override fun setVideoRecoveryHandler(type: Int, handler: () -> Unit) { if (type == 110) recovery = handler }
    override fun setVideoDiagnosticHandler(type: Int, handler: (String) -> Unit) { if(type==110) videoDiagnostic=handler }
    override fun onVideoFrame(type: Int, naluBytes: ByteArray) {
        if (type != 110) return
        try { synchronized(videoLock) { video?.decode(naluBytes) { image ->
            if(!renderedFirstFrame) { renderedFirstFrame=true; videoDiagnostic?.invoke("first frame rendered") }
            onFrame(image)
        } } }
        catch (error: Exception) { log("Vídeo: ${error.message}"); recovery?.invoke() }
    }
    override fun onAudioStarted(id: AudioStreamId, format: AudioFormat, firstSample: Int) {
        audio.remove(id)?.close(); audio[id] = AudioPlayer(format, log)
        log("Áudio ${id.audioType}: ${format.codec}, ${format.sampleRate} Hz, ${format.channels} canais")
    }
    override fun onAudioRtp(id: AudioStreamId, format: AudioFormat, rtp: ByteArray, sample: Int) { audio[id]?.offer(rtp) }
    override fun onAudioStopped(id: AudioStreamId) { audio.remove(id)?.close() }
    override fun onMicrophoneStarted(id: AudioStreamId, config: MicrophoneConfig) {
        microphones.remove(id)?.close(); microphones[id] = Microphone(config, log)
    }
    override fun onMicrophoneStopped(id: AudioStreamId) { microphones.remove(id)?.close() }
    override fun close() {
        microphones.values.forEach { it.close() }; microphones.clear()
        audio.values.forEach { it.close() }; audio.clear()
        synchronized(videoLock) { video?.close(); video=null }
    }
    private class AudioPlayer(private val format: AudioFormat, val log: (String) -> Unit) : Closeable {
        private val closed = AtomicBoolean(false)
        private val queue = ArrayBlockingQueue<ByteArray>(48)
        @Volatile private var line: SourceDataLine? = null
        private val thread = Thread({
            var decoder: NativeCodecs.Audio? = null
            try {
                val rate = if (format.codec == AudioCodecKind.OPUS) 48000 else format.sampleRate
                val pcm = javax.sound.sampled.AudioFormat(rate.toFloat(), 16, format.channels, true, false)
                val output = AudioSystem.getSourceDataLine(pcm); line=output
                output.open(pcm, rate * format.channels * 2 / 5); output.start()
                if (format.codec != AudioCodecKind.LPCM) decoder=NativeCodecs.Audio(format.codec==AudioCodecKind.OPUS, rate, format.channels)
                while (!closed.get()) {
                    val packet=queue.take(); if (packet.size <= 12) continue
                    val body=packet.copyOfRange(12, packet.size)
                    if (format.codec==AudioCodecKind.LPCM) {
                        var i=0; while (i+1<body.size) { val a=body[i]; body[i]=body[i+1]; body[i+1]=a; i+=2 }
                        output.write(body,0,body.size)
                    } else decoder?.decode(body) { bytes -> output.write(bytes,0,bytes.size) }
                }
            } catch (error: Exception) { if (!closed.get()) log("Saída de áudio falhou: ${error.message}") }
            finally { decoder?.close(); line?.close() }
        }, "windows-audio-${format.audioType}").apply { isDaemon=true; start() }
        fun offer(bytes: ByteArray) { if (!closed.get() && !queue.offer(bytes.copyOf())) { queue.poll(); queue.offer(bytes.copyOf()) } }
        override fun close() { closed.set(true); line?.stop(); line?.close(); thread.interrupt() }
    }
    private class Microphone(private val config: MicrophoneConfig, private val log: (String) -> Unit) : Closeable {
        private val closed=AtomicBoolean(false)
        @Volatile private var line: TargetDataLine?=null
        @Volatile private var socket: DatagramSocket?=null
        private val thread=Thread({
            var encoder: NativeCodecs.OpusEncoder?=null
            try {
                val opus=config.codec==AudioCodecKind.OPUS
                val rate=if(opus) 48000 else config.sampleRate
                val pcm=javax.sound.sampled.AudioFormat(rate.toFloat(),16,config.channels,true,false)
                val input=AudioSystem.getTargetDataLine(pcm); line=input; input.open(pcm); input.start()
                val udp=DatagramSocket(); socket=udp
                val counters=MicrophoneCounters()
                if(opus) encoder=NativeCodecs.OpusEncoder(config.channels)
                val bytes=ByteArray(if(opus) 960*config.channels*2 else config.frameBytes)
                fun send(payload: ByteArray) {
                    val packet=MicrophonePacketizer.sealPacket(config.key,config.payloadType,counters,payload,config.rtpSamplesPerPacket)
                    udp.send(DatagramPacket(packet,packet.size,config.host,config.port))
                }
                log("Microfone ativo: ${config.audioType}")
                while(!closed.get()) {
                    var offset=0
                    while(offset<bytes.size && !closed.get()) {
                        val count=input.read(bytes,offset,bytes.size-offset); if(count<=0) break; offset+=count
                    }
                    if(offset!=bytes.size || closed.get()) continue
                    if(opus) encoder?.encode(bytes,::send) else send(MicrophonePacketizer.toWirePcm(bytes))
                }
            } catch(error: Exception) { if(!closed.get()) log("Microfone indisponível: ${error.message}") }
            finally { encoder?.close(); line?.close(); socket?.close(); log("Microfone encerrado") }
        },"windows-microphone").apply { isDaemon=true; start() }
        override fun close() { closed.set(true); line?.stop(); line?.close(); socket?.close(); thread.interrupt() }
    }
    companion object {
        internal fun avcToAnnexB(config: ByteArray): ByteArray {
            require(config.size>=7 && config[0]==1.toByte()) { "Configuração avcC inválida" }
            val output=java.io.ByteArrayOutputStream(); var offset=6
            fun parameterSets(count:Int) { repeat(count) {
                require(offset+2<=config.size)
                val size=((config[offset].toInt() and 255) shl 8) or (config[offset+1].toInt() and 255); offset+=2
                require(size>0 && offset+size<=config.size)
                output.write(byteArrayOf(0,0,0,1)); output.write(config,offset,size); offset+=size
            } }
            parameterSets(config[5].toInt() and 31)
            require(offset<config.size); parameterSets(config[offset++].toInt() and 255)
            return output.toByteArray()
        }
    }
}
