package dev.celitracker.app.arc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

private const val DELAI_MS = 15_000

/** La page fait une centaine de kilooctets; au-dela, ce n'est plus elle. */
private const val TAILLE_MAX = 1_000_000

/**
 * Telecharge la page de l'ARC. Aucune donnee de l'utilisateur ne quitte
 * l'appareil: c'est une requete GET sur une adresse publique, sans cookie ni
 * parametre.
 */
suspend fun telechargerPageArc(url: String): String = withContext(Dispatchers.IO) {
    val adresse = URI(url).toURL()
    require(adresse.protocol == "https") { "https required" }

    val connexion = (adresse.openConnection() as HttpURLConnection).apply {
        connectTimeout = DELAI_MS
        readTimeout = DELAI_MS
        setRequestProperty("Accept-Language", "fr-CA")
    }
    try {
        val code = connexion.responseCode
        require(code == HttpURLConnection.HTTP_OK) { "HTTP $code" }
        connexion.inputStream.bufferedReader().use { lecteur ->
            // Lecture en boucle: un seul appel a read() rend ce qui est deja
            // arrive, pas la page entiere, et la phrase cherchee est au milieu.
            val page = StringBuilder()
            val tampon = CharArray(8 * 1024)
            while (page.length < TAILLE_MAX) {
                val lus = lecteur.read(tampon)
                if (lus < 0) break
                page.appendRange(tampon, 0, lus)
            }
            page.toString()
        }
    } finally {
        connexion.disconnect()
    }
}
