package dev.celitracker.app.cra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val TIMEOUT_MS = 15_000

/** La page fait une centaine de kilooctets; au-dela, ce n'est plus elle. */
private const val MAX_SIZE = 1_000_000

/**
 * Telecharge la page de l'ARC. Aucune donnee de l'utilisateur ne quitte
 * l'appareil: c'est une requete GET sur une address publique, sans cookie ni
 * parametre.
 */
suspend fun downloadCraPage(url: String): String = withContext(Dispatchers.IO) {
    val address = URI(url).toURL()
    require(address.protocol == "https") { "https required" }

    val connection = (address.openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("Accept-Language", "fr-CA")
    }
    try {
        val code = connection.responseCode
        require(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
        connection.inputStream.bufferedReader().use { reader ->
            // Lecture en boucle: un seul appel a read() rend ce qui est deja
            // arrive, labelStep la page entiere, et la phrase cherchee est au milieu.
            val page = StringBuilder()
            val buffer = CharArray(8 * 1024)
            while (page.length < MAX_SIZE) {
                val charsRead = reader.read(buffer)
                if (charsRead < 0) break
                page.appendRange(buffer, 0, charsRead)
            }
            page.toString()
        }
    } finally {
        connection.disconnect()
    }
}
