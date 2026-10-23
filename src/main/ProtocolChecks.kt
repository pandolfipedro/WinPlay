package diplay.desktop

import com.shilapi.xcertplay.iap2.wire.*
import com.shilapi.xcertplay.transport.Iap2LinkEngine

object ProtocolChecks {
    fun run(): List<String> {
        val results = mutableListOf<String>()
        val expected = Iap2Frame(0x5703, Iap2ParameterList.of(
            Iap2Parameter(1, byteArrayOf(1, 2, 3)), Iap2Parameter(1, byteArrayOf(4))
        ).encode())
        val encoded = expected.encodedFrame()
        for (split in 0..encoded.size) {
            val decoder = Iap2CsmFramer()
            val frames = decoder.offer(encoded.copyOfRange(0, split)) + decoder.offer(encoded.copyOfRange(split, encoded.size))
            check(frames == listOf(expected)) { "Falha no framing em $split" }
        }
        results += "CSM: fragmentação em todas as ${encoded.size + 1} posições preserva a mensagem"
        check(Iap2CsmFramer().offer(byteArrayOf(0, 1, 0x40, 0x40, 0, 2) + encoded + encoded) == listOf(expected, expected))
        results += "CSM: recupera cabeçalho inválido e separa mensagens concatenadas"
        val max = Iap2Frame(0xffff, ByteArray(Iap2CsmFramer.MAX_BODY_BYTES) { (it % 251).toByte() })
        val decoder = Iap2CsmFramer()
        check(Iap2CsmFramer.splitForLink(max, 257).flatMap { decoder.offer(it) } == listOf(max))
        results += "CSM: frame máximo de 65.535 bytes reconstituído em fragmentos"
        check(expected.body().all(1).size == 2)
        check(runCatching { Iap2ParameterList.parse(byteArrayOf(0, 8, 0, 1, 2)) }.isFailure)
        check(runCatching { Iap2Frame(1, ByteArray(65_530)) }.isFailure)
        results += "Parâmetros: repetições preservadas; dados truncados e excesso de tamanho rejeitados"
        val a = Iap2LinkEngine()
        val b = Iap2LinkEngine()
        a.start(wiredInitiator = true, nowMillis = 0)
        b.start(wiredInitiator = false, nowMillis = 0)
        repeat(20) { round ->
            a.takeOutput().let { if (it.isNotEmpty()) b.feed(it, (round + 1).toLong()) }
            b.takeOutput().let { if (it.isNotEmpty()) a.feed(it, (round + 1).toLong()) }
        }
        check(a.writable() && b.writable()) { "Negociação entre dois motores iAP2 não completou" }
        results += "iAP2: dois motores negociaram um enlace local (sem iPhone)"
        return results
    }
}
