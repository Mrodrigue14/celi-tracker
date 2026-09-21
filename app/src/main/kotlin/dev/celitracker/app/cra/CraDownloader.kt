package dev.celitracker.app.cra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val TIMEOUT_MS = 15_000

/** The real page is about 100 KB; anything far larger is the wrong page. */
private const val MAX_SIZE = 1_000_000

/** Plain GET on a public address: no user data leaves the device. */
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
            // A single read() returns only what has arrived, so loop until the page is complete.
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
