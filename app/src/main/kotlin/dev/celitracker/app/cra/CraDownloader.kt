package dev.celitracker.app.cra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val TIMEOUT_MS = 15_000

/** The page is about a hundred kilobytes; beyond that, it is not the right page anymore. */
private const val MAX_SIZE = 1_000_000

/**
 * Downloads the CRA page. No user data leaves the device: this is a GET
 * request on a public address, with no cookie or parameter.
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
            // Read in a loop: a single call to read() returns what has already
            // arrived, not the whole page, and the sentence being searched for is in the middle.
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
