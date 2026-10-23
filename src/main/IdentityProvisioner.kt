package diplay.desktop

import com.shilapi.xcertplay.mfi.LocalMfiAuthenticationClient
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Imports only the experimental identity explicitly distributed in the pinned public preview. */
object IdentityProvisioner {
    const val APK_URL = "https://github.com/shihabal3amri/DiPlay/releases/download/v0.2.21/DiPlay-0.2.21.apk"
    const val APK_SHA256 = "916c0708beb1022d4ea9678ff6a46dfb6d234f707837a2cbd80d615e8e2bb2e3"
    fun dataDirectory(): File = System.getProperty("diplay.data.dir")?.let(::File)
        ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "DiPlayWindows")
    fun ensure(onProgress: (String) -> Unit): File {
        val directory = File(dataDirectory(), "authentication-0.2.21")
        if (runCatching { LocalMfiAuthenticationClient.load(directory) }.isSuccess) return directory
        onProgress("Preparando autenticação experimental a partir do APK público oficial DiPlay 0.2.21…")
        dataDirectory().mkdirs()
        val temp = Files.createTempFile(dataDirectory().toPath(), "diplay-public-", ".apk").toFile()
        try {
            val connection = URI(APK_URL).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000; connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "DiPlayWindows/0.2")
            try {
                require(connection.responseCode == 200) { "Download da autenticação: HTTP ${connection.responseCode}" }
                connection.inputStream.use { input -> temp.outputStream().use { output ->
                    val buffer = ByteArray(65_536); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 32_000_000) { "APK excede o tamanho esperado" }; output.write(buffer, 0, count)
                    }
                } }
            } finally { connection.disconnect() }
            return importVerified(temp, directory).also { onProgress("Autenticação local preparada. A aceitação ainda depende do iPhone.") }
        } finally { temp.delete() }
    }
    internal fun importVerified(apk: File, destination: File): File {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val bytes = ByteArray(65_536); while (true) {
            val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count)
        } }
        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == APK_SHA256) { "O APK não corresponde ao hash oficial fixado; importação cancelada" }
        val staging = Files.createTempDirectory(destination.parentFile.toPath(), "identity-verify-").toFile()
        try {
            ZipFile(apk).use { zip -> listOf("identity.pk8", "certificate.p7b").forEach { name ->
                val entry = requireNotNull(zip.getEntry("assets/offline-mfi/$name")) { "Arquivo de autenticação ausente" }
                val bytes = zip.getInputStream(entry).use { it.readNBytes(16_385) }
                require(bytes.size in 1..16_384) { "Arquivo de autenticação inválido" }
                File(staging, name).writeBytes(bytes); bytes.fill(0)
            } }
            LocalMfiAuthenticationClient.load(staging)
            destination.mkdirs()
            listOf("identity.pk8", "certificate.p7b").forEach { File(staging, it).copyTo(File(destination, it), overwrite = true) }
            File(destination, "ORIGIN.txt").writeText("Experimental identity imported from $APK_URL\nSHA256=$APK_SHA256\nNot an Apple-provisioned identity for this Windows application.\n")
            return destination
        } finally {
            // Delete only the two known staging files and the temporary directory created here.
            listOf("identity.pk8", "certificate.p7b").forEach { File(staging, it).delete() }; staging.delete()
        }
    }
}
