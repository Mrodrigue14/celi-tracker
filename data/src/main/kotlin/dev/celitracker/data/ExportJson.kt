package dev.celitracker.data

import dev.celitracker.engine.Compte
import dev.celitracker.engine.TypeTx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Export/import JSON de la base, verse par [Depot]. Aucune valeur calculee
 * n'est exportee : profil, plafonds, transactions, snapshots ARC et reglages,
 * tels que persistes, rien de plus.
 */

private const val VERSION_EXPORT = 2

/**
 * La version 1 portait une annee d'admissibilite CELI saisie a la main, qui se
 * calcule maintenant depuis l'annee de naissance. Un export de version 1 reste
 * lisible : le champ en trop est ignore plutot que de rendre une ancienne
 * sauvegarde inutilisable, ce qui est tout l'interet de l'export.
 */
private val VERSIONS_ACCEPTEES = setOf(1, VERSION_EXPORT)

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class ProfilJson(
    @SerialName("anneeNaissance") val anneeNaissance: Int,
    @SerialName("dateOuvertureCeliapp") val dateOuvertureCeliapp: String?,
)

@Serializable
private data class PlafondJson(
    @SerialName("compte") val compte: String,
    @SerialName("annee") val annee: Int,
    @SerialName("montant") val montant: String,
    @SerialName("confirme") val confirme: Boolean,
)

@Serializable
private data class TransactionJson(
    val id: Long,
    @SerialName("compte") val compte: String,
    val date: String,
    val type: String,
    @SerialName("montant") val montant: String,
)

@Serializable
private data class SnapshotArcJson(
    val id: Long,
    @SerialName("compte") val compte: String,
    @SerialName("dateReference") val dateReference: String,
    @SerialName("droitsDeclares") val droitsDeclares: String,
)

@Serializable
private data class ReglagesJson(
    @SerialName("urlPageArc") val urlPageArc: String,
    @SerialName("dateDerniereVerification") val dateDerniereVerification: String?,
)

@Serializable
private data class ExportDonnees(
    val version: Int,
    @SerialName("profil") val profil: ProfilJson?,
    @SerialName("plafonds") val plafonds: List<PlafondJson>,
    val transactions: List<TransactionJson>,
    @SerialName("snapshotsArc") val snapshotsArc: List<SnapshotArcJson>,
    @SerialName("reglages") val reglages: ReglagesJson,
)

/**
 * Serialise la base en JSON. Les montants sont des chaines,
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
                anneeNaissance = it.anneeNaissance,
                dateOuvertureCeliapp = it.dateOuvertureCeliapp?.toString(),
            )
        },
        plafonds = plafonds()
            .sortedWith(compareBy({ it.compte.storedValue() }, { it.annee }))
            .map { PlafondJson(it.compte.storedValue(), it.annee, it.montant.toPlainString(), it.confirme) },
        transactions = transactions()
            .sortedBy { it.id }
            .map { TransactionJson(it.id, it.compte.storedValue(), it.date.toString(), it.type.storedValue(), it.montant.toPlainString()) },
        snapshotsArc = snapshotsArc()
            .sortedBy { it.id }
            .map { SnapshotArcJson(it.id, it.compte.storedValue(), it.dateReference.toString(), it.droitsDeclares.toPlainString()) },
        reglages = reglages().let { ReglagesJson(it.urlPageArc, it.dateDerniereVerification?.toString()) },
    )
    return json.encodeToString(ExportDonnees.serializer(), donnees)
}

/**
 * Remplace tout le contenu de la base par celui du JSON, dans une seule
 * transaction : ce n'est pas une fusion, les tables sont videes puis
 * remplies. Un import qui echoue - version inconnue, JSON malforme - ne
 * modifie jamais la base existante.
 */
suspend fun Depot.importerJson(contenu: String) {
    val donnees = try {
        val version = json.parseToJsonElement(contenu).jsonObject["version"]?.jsonPrimitive?.intOrNull
        if (version !in VERSIONS_ACCEPTEES) throw ImportInvalide(RaisonImport.VERSION_INCONNUE)
        json.decodeFromString(ExportDonnees.serializer(), contenu)
    } catch (e: ImportInvalide) {
        throw e
    } catch (e: Exception) {
        throw ImportInvalide(RaisonImport.JSON_MALFORME, e)
    }

    val profil = donnees.profil?.let {
        ProfilEntity(
            anneeNaissance = it.anneeNaissance,
            dateOuvertureCeliapp = it.dateOuvertureCeliapp?.let(LocalDate::parse),
        )
    }
    val plafonds = donnees.plafonds.map {
        PlafondEntity(
            compte = storedAccount(it.compte),
            annee = it.annee,
            montant = BigDecimal(it.montant),
            confirme = it.confirme,
        )
    }
    val transactions = donnees.transactions.map {
        TransactionEntity(
            id = it.id,
            compte = storedAccount(it.compte),
            date = LocalDate.parse(it.date),
            type = storedTransactionType(it.type),
            montant = BigDecimal(it.montant),
        )
    }
    val snapshots = donnees.snapshotsArc.map {
        SnapshotArcEntity(
            id = it.id,
            compte = storedAccount(it.compte),
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
