package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.TypeTx
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Export/import JSON de la base, verse par [Depot]. Aucune valeur calculee
 * n'est exportee : profil, plafonds, transactions, snapshots ARC et reglages,
 * tels que persistes, rien de plus.
 */

private const val VERSION_EXPORT = 1

@Serializable
private data class ProfilJson(
    val anneeAdmissibiliteCeli: Int,
    val anneeNaissance: Int,
    val dateOuvertureCeliapp: String?,
)

@Serializable
private data class PlafondJson(
    val compte: String,
    val annee: Int,
    val montant: String,
    val confirme: Boolean,
)

@Serializable
private data class TransactionJson(
    val id: Long,
    val compte: String,
    val date: String,
    val type: String,
    val montant: String,
)

@Serializable
private data class SnapshotArcJson(
    val id: Long,
    val compte: String,
    val dateReference: String,
    val droitsDeclares: String,
)

@Serializable
private data class ReglagesJson(
    val urlPageArc: String,
    val dateDerniereVerification: String?,
)

@Serializable
private data class ExportDonnees(
    val version: Int,
    val profil: ProfilJson?,
    val plafonds: List<PlafondJson>,
    val transactions: List<TransactionJson>,
    val snapshotsArc: List<SnapshotArcJson>,
    val reglages: ReglagesJson,
)

/**
 * Serialise la base en JSON, `version` = 1. Les montants sont des chaines,
 * jamais des nombres JSON : un nombre JSON transite par un `double` chez la
 * plupart des lecteurs, ce qui detruirait l'exactitude de [BigDecimal]. Les
 * collections sont triees par une cle stable pour qu'a contenu egal, deux
 * exports successifs produisent la meme chaine.
 */
suspend fun Depot.exporterJson(): String {
    val donnees = ExportDonnees(
        version = VERSION_EXPORT,
        profil = profil()?.let {
            ProfilJson(
                anneeAdmissibiliteCeli = it.anneeAdmissibiliteCeli,
                anneeNaissance = it.anneeNaissance,
                dateOuvertureCeliapp = it.dateOuvertureCeliapp?.toString(),
            )
        },
        plafonds = plafonds()
            .sortedWith(compareBy({ it.compte.name }, { it.annee }))
            .map { PlafondJson(it.compte.name, it.annee, it.montant.toPlainString(), it.confirme) },
        transactions = transactions()
            .sortedBy { it.id }
            .map { TransactionJson(it.id, it.compte.name, it.date.toString(), it.type.name, it.montant.toPlainString()) },
        snapshotsArc = snapshotsArc()
            .sortedBy { it.id }
            .map { SnapshotArcJson(it.id, it.compte.name, it.dateReference.toString(), it.droitsDeclares.toPlainString()) },
        reglages = reglages().let { ReglagesJson(it.urlPageArc, it.dateDerniereVerification?.toString()) },
    )
    return Json.encodeToString(ExportDonnees.serializer(), donnees)
}

/**
 * Remplace tout le contenu de la base par celui du JSON, dans une seule
 * transaction : ce n'est pas une fusion, les tables sont videes puis
 * remplies. Un import qui echoue - version inconnue, JSON malforme - ne
 * modifie jamais la base existante.
 */
suspend fun Depot.importerJson(json: String) {
    val donnees = try {
        val version = Json.parseToJsonElement(json).jsonObject["version"]?.jsonPrimitive?.intOrNull
        require(version == VERSION_EXPORT) {
            "Version d'export non prise en charge : $version. Seule la version $VERSION_EXPORT est acceptee."
        }
        Json.decodeFromString(ExportDonnees.serializer(), json)
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("JSON malforme : ${e.message}", e)
    }

    val profil = donnees.profil?.let {
        ProfilEntity(
            anneeAdmissibiliteCeli = it.anneeAdmissibiliteCeli,
            anneeNaissance = it.anneeNaissance,
            dateOuvertureCeliapp = it.dateOuvertureCeliapp?.let(LocalDate::parse),
        )
    }
    val plafonds = donnees.plafonds.map {
        PlafondEntity(
            compte = Compte.valueOf(it.compte),
            annee = it.annee,
            montant = BigDecimal(it.montant),
            confirme = it.confirme,
        )
    }
    val transactions = donnees.transactions.map {
        TransactionEntity(
            id = it.id,
            compte = Compte.valueOf(it.compte),
            date = LocalDate.parse(it.date),
            type = TypeTx.valueOf(it.type),
            montant = BigDecimal(it.montant),
        )
    }
    val snapshots = donnees.snapshotsArc.map {
        SnapshotArcEntity(
            id = it.id,
            compte = Compte.valueOf(it.compte),
            dateReference = LocalDate.parse(it.dateReference),
            droitsDeclares = BigDecimal(it.droitsDeclares),
        )
    }
    val reglages = ReglagesEntity(
        urlPageArc = donnees.reglages.urlPageArc,
        dateDerniereVerification = donnees.reglages.dateDerniereVerification?.let(Instant::parse),
    )

    remplacerTout(profil, plafonds, transactions, snapshots, reglages)
}
