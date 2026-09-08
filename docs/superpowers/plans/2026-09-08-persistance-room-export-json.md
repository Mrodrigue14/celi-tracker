# Persistance Room et export JSON — plan d'implémentation

**Goal:** Livrer le module `:data` — persistance Room des saisies et
export/import JSON — sans introduire de dépendance Android.

**Architecture:** `:data` est un module **Kotlin/JVM pur** qui dépend de
`:engine`. Room y tourne via son artefact JVM et le pilote SQLite embarqué,
donc les tests s'exécutent sur la JVM, sans SDK Android ni émulateur, et la CI
reste inchangée. `:engine` ne dépend jamais de `:data`.

**Tech Stack:** Room 2.8.4 (`room-runtime`, `room-compiler` via KSP 2.3.11,
plugin Gradle `androidx.room` pour l'export du schéma), `androidx.sqlite:sqlite-bundled`
2.7.0, `kotlinx-coroutines-core` 1.10.2, `kotlinx-serialization-json` pour
l'export. Kotlin 2.4.10, Gradle 9.7.1, JDK 21.

**Spec:** `docs/superpowers/specs/2026-09-07-suivi-celi-celiapp-design.md`

## Contraintes découvertes par un spike, à ne pas redécouvrir

Un spike jetable a validé le montage. Quatre points l'ont fait échouer avant de
passer :

1. **Pas de `@ConstructedBy` ni d'`expect object`.** C'est le patron
   multiplateforme ; dans un module `kotlin("jvm")` simple il produit
   `'expect' and 'actual' declarations can be used only in multiplatform projects`.
   Sur JVM, Room génère directement l'implémentation.
2. **`pluginManagement.repositories` doit inclure `google()`** dans
   `settings.gradle.kts` : le plugin Gradle `androidx.room` n'est pas publié
   sur le portail Gradle.
3. **Le plugin `androidx.room` avec `room { schemaDirectory(...) }` est
   obligatoire.** Sans lui, Room émet un avertissement sur l'export du schéma —
   et la CI passe `-PwarningsAsErrors=true`, donc le build échouerait.
4. **Construction de la base :**
   `Room.databaseBuilder<T>(name = <chemin absolu>).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()`.

## Global Constraints

- **`:engine` ne dépend jamais de `:data`.** La dépendance va dans un seul sens.
- **Aucune valeur calculée n'est persistée.** La base contient le profil, la
  table des plafonds, le journal des transactions, les snapshots ARC et les
  réglages. **Aucune table de droits**, aucun solde mémoïsé.
- **Les montants sont stockés en `TEXT`**, jamais en `REAL`. SQLite n'a pas de
  type décimal exact ; un `REAL` réintroduit la dérive de virgule flottante que
  `BigDecimal` élimine. Conversion via `BigDecimal.toPlainString()`.
- **Les dates sont stockées en `TEXT` ISO-8601** (`LocalDate.toString()`).
- Aucune dépendance Android, aucun SDK Android, aucun Robolectric.
- Package racine : `dev.celitracker.data`.
- Aucune donnée financière nominative : les fixtures restent synthétiques.
- Le seuil de couverture Kover s'applique aussi à `:data`.

## Structure des fichiers

| Fichier | Responsabilité |
|---|---|
| `settings.gradle.kts` | ajoute `include(":data")` et `google()` dans `pluginManagement` |
| `build.gradle.kts` (racine) | déclare les plugins KSP et `androidx.room`, sans les appliquer |
| `data/build.gradle.kts` | applique kotlin/ksp/room/kover, dépendances, `schemaDirectory` |
| `data/src/main/kotlin/.../Entites.kt` | les 5 entités Room + les convertisseurs de type |
| `data/src/main/kotlin/.../CeliTrackerDao.kt` | requêtes |
| `data/src/main/kotlin/.../CeliTrackerBase.kt` | `@Database`, et la fabrique d'ouverture |
| `data/src/main/kotlin/.../Depot.kt` | expose les types de `:engine`, jamais les entités ; valide les saisies |
| `data/src/main/kotlin/.../ExportJson.kt` | sérialisation, versionnée |

---

### Task 1 : Module `:data` et persistance des saisies

**Files:** `settings.gradle.kts`, `build.gradle.kts`, `data/build.gradle.kts`,
`data/src/main/kotlin/dev/celitracker/data/{Entites,CeliTrackerDao,CeliTrackerBase,Depot}.kt`,
`data/src/test/kotlin/dev/celitracker/data/DepotTest.kt`

**Interfaces produites :**

```kotlin
class Depot(private val base: CeliTrackerBase) {
    suspend fun profil(): Profil?
    suspend fun enregistrerProfil(profil: Profil)
    suspend fun plafonds(): List<PlafondAnnuel>
    suspend fun enregistrerPlafond(plafond: PlafondAnnuel)
    suspend fun transactions(): List<Transaction>
    suspend fun ajouterTransaction(transaction: Transaction)
    suspend fun supprimerTransaction(id: Long)
    suspend fun snapshotsArc(): List<SnapshotArc>
    suspend fun enregistrerSnapshotArc(snapshot: SnapshotArc)
    suspend fun reglages(): Reglages
    suspend fun enregistrerReglages(reglages: Reglages)
}

fun ouvrirBase(chemin: String): CeliTrackerBase
```

`SnapshotArc` et `Reglages` n'existent pas encore dans `:engine` : ajoute-les à
`Modele.kt` (`SnapshotArc(id: Long, compte: Compte, dateReference: LocalDate,
droitsDeclares: BigDecimal)` et `Reglages(urlPageArc: String,
dateDerniereVerification: Instant?)`), avec la même discipline que les types
existants. `Transaction` gagne un `id: Long = 0` pour permettre la suppression.

**Validation à la saisie.** C'est ici, et pas dans le moteur, que les saisies
incohérentes sont rejetées — un constat de revue avait montré qu'une
transaction antérieure à l'année d'admissibilité produisait deux résultats
différents selon le moteur consulté. `ajouterTransaction` lève
`IllegalArgumentException` si :

- le montant est nul ou négatif (le signe est porté par `type`) ;
- la transaction est CELI et antérieure au 1er janvier de
  `profil.anneeAdmissibiliteCeli` ;
- la transaction est CELIAPP et antérieure à `profil.dateOuvertureCeliapp`, ou
  qu'aucune date d'ouverture n'est enregistrée.

**Tests obligatoires :** aller-retour de chaque type sur une base réelle en
fichier temporaire ; un montant à deux décimales relu exactement
(`1234.56` reste `1234.56`, jamais `1234.5600000000002`) ; la colonne de
montant est bien de type `TEXT` ; chacune des règles de validation ci-dessus ;
suppression d'une transaction.

---

### Task 2 : Export et import JSON

**Files:** `data/src/main/kotlin/dev/celitracker/data/ExportJson.kt`,
`data/src/test/kotlin/dev/celitracker/data/ExportJsonTest.kt`

**Interfaces produites :**

```kotlin
suspend fun Depot.exporterJson(): String
suspend fun Depot.importerJson(json: String)
```

**Règles :**

- L'export porte un champ `version` entier, valant 1. `importerJson` refuse une
  version inconnue avec un message explicite plutôt que d'interpréter au mieux.
- Les montants sont sérialisés en **chaînes**, jamais en nombres JSON : un
  nombre JSON passe par un `double` chez la plupart des lecteurs et perdrait
  l'exactitude qui est toute la raison d'être de `BigDecimal`.
- **L'import remplace tout, de façon atomique** : dans une seule transaction de
  base, toutes les tables sont vidées puis remplies. Un import qui échoue à
  mi-course ne doit jamais laisser une base à moitié écrasée. Documente ce
  choix : ce n'est pas une fusion.
- L'export est **déterministe** : à contenu égal, deux exports successifs
  produisent une chaîne identique (ordonne les collections par clé stable).

**Tests obligatoires :** aller-retour sans perte (exporter → importer dans une
base vierge → réexporter donne une chaîne identique) ; un montant à deux
décimales survit ; une version inconnue est refusée ; un JSON malformé est
refusé sans écraser la base existante ; l'export est déterministe.

---

## Hors périmètre

Interface Compose, lecture des plafonds sur le site de l'ARC, sauvegarde
automatique Android, module applicatif Android et APK. Ils viendront quand le
SDK Android sera installé — ce plan n'en a pas besoin.
