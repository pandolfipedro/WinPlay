package diplay.desktop

object MediaChecks {
    fun run():List<String> {
        NativeCodecs.Video(ByteArray(0)).use { }
        val packets=mutableListOf<ByteArray>()
        NativeCodecs.OpusEncoder(1).use { encoder -> repeat(3) { encoder.encode(ByteArray(1920)) { packets.add(it) } } }
        check(packets.isNotEmpty()) { "O encoder Opus não produziu pacotes" }
        var decoded=0
        NativeCodecs.Audio(true,48000,1).use { decoder -> packets.forEach { decoder.decode(it) { pcm -> decoded+=pcm.size } } }
        check(decoded>=1920) { "O decoder Opus não produziu PCM" }
        NativeCodecs.Audio(false,48000,2).use { }
        return listOf("Decodificador H.264 inicializado", "Microfone Opus → pacotes → PCM: roundtrip local", "Decodificador AAC inicializado")
    }
}
