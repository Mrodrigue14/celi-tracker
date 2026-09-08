# Moteur de calcul CELI / CELIAPP — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Livrer le moteur de calcul des droits de cotisation CELI et CELIAPP comme module Kotlin/JVM pur, entièrement testé, et faire passer la CI du rouge au vert.

**Architecture:** Un seul module Gradle `:engine`, sans dépendance Android. Les droits sont des fonctions pures `(profil, plafonds, transactions) → droits par année`, jamais persistées. Deux moteurs distincts, `CeliMoteur` et `CeliappMoteur`, plus un calculateur de sur-cotisation. Aucune dépendance externe hors `kotlin("test")`.

**Tech Stack:** Kotlin 2.2.0 (JVM), Gradle 9.7.1, JDK 21 (toolchain), `kotlin("test")` sur JUnit Platform, `java.math.BigDecimal`, `java.time`.

**Spec:** `docs/superpowers/specs/2026-09-07-suivi-celi-celiapp-design.md`

## Global Constraints

- **Aucune valeur calculée n'est stockée.** Le moteur ne persiste rien. Pas de champ mémoïsé, pas de cache, pas de structure `droits_par_annee` conservée entre deux appels.
- **`BigDecimal` partout, jamais `Double` ni `Float`** pour un montant.
- **Échelle normalisée à 2 décimales sur toute sortie du moteur.** `BigDecimal.equals` compare aussi l'échelle : `BigDecimal("6000") != BigDecimal("6000.00")`. Toute valeur monétaire retournée passe par `.argent()`. Tout littéral monétaire d'un test s'écrit avec deux décimales.
- **`CeliMoteur` et `CeliappMoteur` restent deux objets séparés.** Interdit de les fusionner derrière un paramètre `Compte`.
- **`droitsFin` n'est jamais ramené à zéro.** Un solde négatif *est* la sur-cotisation et doit se propager à l'année suivante. Le classeur d'origine utilisait `MAX(J36-K36, 0)`, ce qui effaçait silencieusement toute sur-cotisation — c'est précisément le bug à ne pas reproduire.
- **Package racine :** `dev.celitracker.engine`.
- **Aucune donnée financière nominative dans le dépôt.** Les fixtures sont des scénarios anonymes : pas de nom d'institution, pas de formulation à la première personne.
- **Toutes les versions sont épinglées.** Dependabot propose les montées mineures et correctives ; les majeures sont bloquées par `.github/dependabot.yml`.

## Structure des fichiers

| Fichier | Responsabilité |
|---|---|
| `settings.gradle.kts` | Déclare le module `:engine` |
| `build.gradle.kts` | Racine — déclare le plugin Kotlin sans l'appliquer |
| `engine/build.gradle.kts` | Toolchain 21, `kotlin("test")`, JUnit Platform |
| `engine/src/main/kotlin/dev/celitracker/engine/Modele.kt` | Types de saisie : `Compte`, `TypeTx`, `Transaction`, `PlafondAnnuel`, `Profil`, extension `argent()` |
| `engine/src/main/kotlin/dev/celitracker/engine/CeliMoteur.kt` | `DroitsAnnee` + droits CELI année par année |
| `engine/src/main/kotlin/dev/celitracker/engine/CeliappMoteur.kt` | `DroitsAnneeCeliapp` + droits CELIAPP, report non cumulatif, fin de période de participation |
| `engine/src/main/kotlin/dev/celitracker/engine/SurCotisation.kt` | `ExcedentMensuel` + excédent maximal par mois et pénalité |
| `engine/src/test/kotlin/dev/celitracker/engine/ModeleTest.kt` | Précision décimale |
| `engine/src/test/kotlin/dev/celitracker/engine/CeliMoteurTest.kt` | Fixture d'acceptation, retraits, plafonds manquants |
| `engine/src/test/kotlin/dev/celitracker/engine/CeliappMoteurTest.kt` | Fixture d'acceptation, report plafonné, échéance |
| `engine/src/test/kotlin/dev/celitracker/engine/SurCotisationTest.kt` | Excédent mensuel, piège de la re-cotisation |

---

### Task 1 : Chaîne d'outils Gradle + modèle de données

Fait passer la CI de rouge (`chmod: cannot access 'gradlew'`) à vert, et pose les types de saisie sur lesquels tout le reste s'appuie.

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `engine/build.gradle.kts`
- Create: `engine/src/main/kotlin/dev/celitracker/engine/Modele.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/ModeleTest.kt`
- Generated: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: rien.
- Produces: `enum class Compte { CELI, CELIAPP }` · `enum class TypeTx { DEPOT, RETRAIT }` · `data class Transaction(compte: Compte, date: LocalDate, type: TypeTx, montant: BigDecimal)` · `data class PlafondAnnuel(compte: Compte, annee: Int, montant: BigDecimal, confirme: Boolean = true)` · `data class Profil(anneeAdmissibiliteCeli: Int, anneeNaissance: Int, dateOuvertureCeliapp: LocalDate?)` · `fun BigDecimal.argent(): BigDecimal`

- [ ] **Step 1 : Générer le wrapper Gradle**

Gradle n'est pas installé sur la machine. On télécharge la distribution une fois, on l'utilise pour générer le wrapper, puis on la jette — c'est le wrapper qui est versionné, pas la distribution.

```bash
cd /c/Dev/celi-tracker
SCRATCH="$HOME/AppData/Local/Temp/celi-gradle"
mkdir -p "$SCRATCH"
curl -L -o "$SCRATCH/gradle.zip" https://services.gradle.org/distributions/gradle-9.7.1-bin.zip
unzip -q -o "$SCRATCH/gradle.zip" -d "$SCRATCH"
"$SCRATCH/gradle-9.7.1/bin/gradle" wrapper --gradle-version 9.7.1
```

Vérifier : `cat gradle/wrapper/gradle-wrapper.properties` doit contenir `gradle-9.7.1-bin.zip`.

- [ ] **Step 2 : Écrire les fichiers de build**

`settings.gradle.kts` :

```kotlin
rootProject.name = "celi-tracker"

include(":engine")
```

`build.gradle.kts` (racine) :

```kotlin
// Le plugin Kotlin est declare ici pour epingler sa version en un seul endroit,
// mais applique uniquement dans les modules qui en ont besoin.
plugins {
    kotlin("jvm") version "2.2.0" apply false
}
```

`engine/build.gradle.kts` :

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    // Indispensable: sans depot declare, Gradle ne resout ni kotlin-stdlib ni
    // kotlin-test, et le build echoue sur "no repositories are defined".
    mavenCentral()
}

dependencies {
    // Seule dependance du moteur. Il ne connait ni Android, ni Room, ni reseau.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3 : Écrire le test qui échoue**

`engine/src/test/kotlin/dev/celitracker/engine/ModeleTest.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ModeleTest {

    @Test
    fun `un montant conserve sa precision decimale exacte`() {
        val tx = Transaction(
            compte = Compte.CELI,
            date = LocalDate.of(2026, 4, 6),
            type = TypeTx.DEPOT,
            montant = BigDecimal("1234.56"),
        )

        // Si quelqu'un remplace BigDecimal par Double, cette egalite de chaine
        // casse (1234.5600000000001) et le test devient rouge.
        assertEquals("1234.56", tx.montant.toPlainString())
    }

    @Test
    fun `argent normalise l'echelle a deux decimales`() {
        // BigDecimal.equals compare l'echelle: sans normalisation,
        // BigDecimal("6000") != BigDecimal("6000.00").
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000").argent())
        assertEquals(BigDecimal("6000.00"), BigDecimal("6000.000").argent())
    }

    @Test
    fun `un plafond est confirme par defaut`() {
        val plafond = PlafondAnnuel(Compte.CELI, 2019, BigDecimal("6000.00"))
        assertEquals(true, plafond.confirme)
    }

    @Test
    fun `un profil accepte l'absence de compte CELIAPP`() {
        val profil = Profil(
            anneeAdmissibiliteCeli = 2019,
            anneeNaissance = 2001,
            dateOuvertureCeliapp = null,
        )
        assertEquals(null, profil.dateOuvertureCeliapp)
    }
}
```

- [ ] **Step 4 : Lancer le test pour vérifier qu'il échoue**

Run : `./gradlew :engine:test`
Expected : ÉCHEC à la compilation — `Unresolved reference: Transaction`, `Unresolved reference: argent`.

- [ ] **Step 5 : Écrire l'implémentation minimale**

`engine/src/main/kotlin/dev/celitracker/engine/Modele.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Types de SAISIE du moteur. Rien ici n'est calcule: le profil, la table des
 * plafonds et le journal des transactions sont les trois seules entrees dont
 * les droits de cotisation sont derives.
 */

enum class Compte { CELI, CELIAPP }

enum class TypeTx { DEPOT, RETRAIT }

/** Le montant est TOUJOURS positif; c'est [type] qui porte le sens. */
data class Transaction(
    val compte: Compte,
    val date: LocalDate,
    val type: TypeTx,
    val montant: BigDecimal,
)

/**
 * [confirme] a false = plafond propose par la lecture automatique du site de
 * l'ARC, pas encore valide par l'utilisateur. Un plafond non confirme n'entre
 * jamais dans le calcul des droits.
 */
data class PlafondAnnuel(
    val compte: Compte,
    val annee: Int,
    val montant: BigDecimal,
    val confirme: Boolean = true,
)

data class Profil(
    /** Annee des 18 ans ET de la residence canadienne. */
    val anneeAdmissibiliteCeli: Int,
    /** Pour la branche des 71 ans de la periode de participation CELIAPP. */
    val anneeNaissance: Int,
    /** Demarre l'accumulation des droits CELIAPP ET l'horloge des 15 ans. */
    val dateOuvertureCeliapp: LocalDate?,
)

/**
 * Normalise un montant a 2 decimales.
 *
 * BigDecimal.equals compare la valeur ET l'echelle, donc BigDecimal("6000")
 * n'est pas egal a BigDecimal("6000.00"). Toute valeur monetaire produite par
 * le moteur passe par cette fonction, sans quoi les assertions des tests
 * echouent sur des montants pourtant identiques.
 */
fun BigDecimal.argent(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
```

- [ ] **Step 6 : Lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :engine:test`
Expected : PASS, 4 tests.

- [ ] **Step 7 : Commit**

```bash
cd /c/Dev/celi-tracker
git add settings.gradle.kts build.gradle.kts engine gradle gradlew gradlew.bat
git commit -m "$(cat <<'EOF'
Ajoute le wrapper Gradle, le module engine et le modele de donnees

Le moteur est un module Kotlin/JVM pur: pas de SDK Android requis pour le
construire ni pour le tester, ce qui fait verdir la CI sans dependance lourde.

Modele.kt ne contient que des types de SAISIE. La fonction argent() normalise
l'echelle a 2 decimales parce que BigDecimal.equals compare aussi l'echelle.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

- [ ] **Step 8 : Vérifier que la CI est verte**

Run : `gh run list --limit 1`
Expected : le workflow `Build` est en `success`. C'était le premier objectif du plan.

---

### Task 2 : Moteur CELI — fixture d'acceptation

Le cœur du projet. La fixture est un scénario synthétique dérivé des plafonds annuels publiés par l'ARC : cumul des plafonds 51 500 $ − dépôts 9 700 $ = 41 800 $, sur ses dix valeurs.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/CeliMoteur.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/CeliMoteurTest.kt`

**Interfaces:**
- Consumes: `Compte`, `TypeTx`, `Transaction`, `PlafondAnnuel`, `Profil`, `BigDecimal.argent()` (Task 1).
- Produces: `data class DroitsAnnee(annee: Int, plafond: BigDecimal, droitsDebut: BigDecimal, depots: BigDecimal, retraits: BigDecimal, droitsFin: BigDecimal, plafondManquant: Boolean)` · `object CeliMoteur { fun droitsParAnnee(profil: Profil, plafonds: List<PlafondAnnuel>, transactions: List<Transaction>, jusqua: Int): List<DroitsAnnee> }`

- [ ] **Step 1 : Écrire le test qui échoue**

`engine/src/test/kotlin/dev/celitracker/engine/CeliMoteurTest.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Raccourci: tout litteral monetaire d'un test s'ecrit a 2 decimales. */
private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

private fun plafondsCeli(vararg paires: Pair<Int, String>): List<PlafondAnnuel> =
    paires.map { (annee, montant) -> PlafondAnnuel(Compte.CELI, annee, argent(montant)) }

private fun depot(date: String, montant: String) =
    Transaction(Compte.CELI, LocalDate.parse(date), TypeTx.DEPOT, argent(montant))

class CeliMoteurTest {

    /**
     * scenario_2019_eligible_three_deposits
     *
     * Personne devenue admissible au CELI en 2019, aucun retrait, trois depots.
     * Scenario synthetique, derive des plafonds annuels publies par l'ARC.
     * Controle: cumul des plafonds 51 500 - depots 9 700 = 41 800.
     */
    @Test
    fun `scenario 2019 eligible three deposits`() {
        val profil = Profil(
            anneeAdmissibiliteCeli = 2019,
            anneeNaissance = 2001,
            dateOuvertureCeliapp = null,
        )
        val plafonds = plafondsCeli(
            2019 to "6000.00",
            2020 to "6000.00",
            2021 to "6000.00",
            2022 to "6000.00",
            2023 to "6500.00",
            2024 to "7000.00",
            2025 to "7000.00",
            2026 to "7000.00",
        )
        val transactions = listOf(
            depot("2021-03-10", "5000.00"),
            depot("2023-06-15", "3500.00"),
            depot("2024-11-02", "1200.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2026)
            .associateBy { it.annee }

        assertEquals(argent("6000.00"), droits.getValue(2019).droitsDebut)
        assertEquals(argent("6000.00"), droits.getValue(2019).droitsFin)

        assertEquals(argent("12000.00"), droits.getValue(2020).droitsDebut)
        assertEquals(argent("12000.00"), droits.getValue(2020).droitsFin)

        assertEquals(argent("18000.00"), droits.getValue(2021).droitsDebut)
        assertEquals(argent("5000.00"), droits.getValue(2021).depots)
        assertEquals(argent("13000.00"), droits.getValue(2021).droitsFin)

        assertEquals(argent("19000.00"), droits.getValue(2022).droitsDebut)
        assertEquals(argent("19000.00"), droits.getValue(2022).droitsFin)

        assertEquals(argent("25500.00"), droits.getValue(2023).droitsDebut)
        assertEquals(argent("3500.00"), droits.getValue(2023).depots)
        assertEquals(argent("22000.00"), droits.getValue(2023).droitsFin)

        assertEquals(argent("29000.00"), droits.getValue(2024).droitsDebut)
        assertEquals(argent("1200.00"), droits.getValue(2024).depots)
        assertEquals(argent("27800.00"), droits.getValue(2024).droitsFin)

        assertEquals(argent("34800.00"), droits.getValue(2025).droitsDebut)
        assertEquals(argent("34800.00"), droits.getValue(2025).droitsFin)

        assertEquals(argent("41800.00"), droits.getValue(2026).droitsDebut)
        assertEquals(argent("41800.00"), droits.getValue(2026).droitsFin)
    }

    @Test
    fun `les annees vont de l'admissibilite a l'annee demandee`() {
        val profil = Profil(2019, 2001, null)
        val droits = CeliMoteur.droitsParAnnee(
            profil,
            plafondsCeli(2019 to "6000.00", 2020 to "6000.00"),
            emptyList(),
            jusqua = 2020,
        )

        assertEquals(listOf(2019, 2020), droits.map { it.annee })
    }

    @Test
    fun `les transactions CELIAPP sont ignorees par le moteur CELI`() {
        val profil = Profil(2019, 2001, LocalDate.of(2023, 4, 1))
        val transactions = listOf(
            Transaction(Compte.CELIAPP, LocalDate.of(2019, 5, 1), TypeTx.DEPOT, argent("5000.00")),
        )

        val droits = CeliMoteur.droitsParAnnee(
            profil, plafondsCeli(2019 to "6000.00"), transactions, jusqua = 2019,
        )

        assertEquals(argent("0.00"), droits.single().depots)
        assertEquals(argent("6000.00"), droits.single().droitsFin)
    }
}
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :engine:test --tests "dev.celitracker.engine.CeliMoteurTest"`
Expected : ÉCHEC à la compilation — `Unresolved reference: CeliMoteur`.

- [ ] **Step 3 : Écrire l'implémentation minimale**

`engine/src/main/kotlin/dev/celitracker/engine/CeliMoteur.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal

/**
 * Droits de cotisation CELI pour une annee.
 *
 * [plafondManquant] signale une annee dont le plafond est absent de la table ou
 * pas encore confirme. Son plafond vaut alors zero: le moteur n'invente aucun
 * droit, et l'erreur penche du cote prudent (droits sous-estimes plutot que
 * sur-estimes, donc jamais d'incitation a sur-cotiser).
 */
data class DroitsAnnee(
    val annee: Int,
    val plafond: BigDecimal,
    val droitsDebut: BigDecimal,
    val depots: BigDecimal,
    val retraits: BigDecimal,
    val droitsFin: BigDecimal,
    val plafondManquant: Boolean,
)

/**
 * Moteur CELI. Fonction pure: memes entrees, memes sorties, aucun etat conserve
 * entre deux appels.
 *
 * NE PAS fusionner avec [CeliappMoteur]. Les deux regimes divergent sur chaque
 * axe, a commencer par le fait qu'un retrait CELI redonne des droits alors
 * qu'un retrait CELIAPP n'en redonne jamais.
 */
object CeliMoteur {

    fun droitsParAnnee(
        profil: Profil,
        plafonds: List<PlafondAnnuel>,
        transactions: List<Transaction>,
        jusqua: Int,
    ): List<DroitsAnnee> {
        val parAnnee = plafonds
            .filter { it.compte == Compte.CELI && it.confirme }
            .associateBy { it.annee }
        val txCeli = transactions.filter { it.compte == Compte.CELI }

        val resultat = mutableListOf<DroitsAnnee>()
        var droitsFinPrecedent = BigDecimal.ZERO
        var retraitsPrecedent = BigDecimal.ZERO

        for (annee in profil.anneeAdmissibiliteCeli..jusqua) {
            val plafondAnnee = parAnnee[annee]
            val plafond = plafondAnnee?.montant ?: BigDecimal.ZERO
            val depots = somme(txCeli, annee, TypeTx.DEPOT)
            val retraits = somme(txCeli, annee, TypeTx.RETRAIT)

            // Les retraits de l'annee PRECEDENTE reviennent le 1er janvier;
            // ceux de l'annee courante ne comptent pas encore.
            val droitsDebut = droitsFinPrecedent + plafond + retraitsPrecedent

            // Pas de coerceAtLeast(ZERO) ici: un solde negatif EST la
            // sur-cotisation et doit se propager a l'annee suivante. Le
            // classeur d'origine utilisait MAX(..., 0), ce qui l'effacait.
            val droitsFin = droitsDebut - depots

            resultat += DroitsAnnee(
                annee = annee,
                plafond = plafond.argent(),
                droitsDebut = droitsDebut.argent(),
                depots = depots.argent(),
                retraits = retraits.argent(),
                droitsFin = droitsFin.argent(),
                plafondManquant = plafondAnnee == null,
            )

            droitsFinPrecedent = droitsFin
            retraitsPrecedent = retraits
        }
        return resultat
    }

    private fun somme(transactions: List<Transaction>, annee: Int, type: TypeTx): BigDecimal =
        transactions
            .filter { it.date.year == annee && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.montant }
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :engine:test`
Expected : PASS, 7 tests.

- [ ] **Step 5 : Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Ajoute le moteur CELI et sa fixture d'acceptation

droitsDebut(A) = droitsFin(A-1) + plafond(A) + retraits(A-1)
droitsFin(A)   = droitsDebut(A) - depots(A)

droitsFin n'est deliberement pas ramene a zero: un solde negatif est la
sur-cotisation et doit se propager. Le classeur remplace utilisait MAX(..., 0),
ce qui effacait silencieusement toute sur-cotisation.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3 : Retraits CELI — restitution au 1er janvier suivant

Le scénario d'acceptation ne contient **aucun retrait** : ce chemin n'est couvert par aucune de ses fixtures et doit être testé explicitement.

**Files:**
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/CeliMoteurTest.kt` (ajout de tests)

**Interfaces:**
- Consumes: `CeliMoteur.droitsParAnnee`, `DroitsAnnee` (Task 2).
- Produces: rien de nouveau. Cette tâche valide un comportement déjà implémenté ; si un test échoue, c'est `CeliMoteur` qui est corrigé.

- [ ] **Step 1 : Écrire les tests qui doivent passer**

Ajouter dans `CeliMoteurTest.kt`, avec ce helper près des autres en haut du fichier :

```kotlin
private fun retrait(date: String, montant: String) =
    Transaction(Compte.CELI, LocalDate.parse(date), TypeTx.RETRAIT, argent(montant))
```

Puis, dans la classe :

```kotlin
    @Test
    fun `un retrait ne redonne pas de droits dans l'annee du retrait`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(
            depot("2020-03-01", "6000.00"),
            retrait("2020-08-01", "6000.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2020)
            .associateBy { it.annee }

        // 6000 (fin 2019) + 6000 (plafond 2020) + 0 (retraits 2019) = 12000.
        // Le retrait de 2020 n'ajoute RIEN aux droits de 2020.
        assertEquals(argent("12000.00"), droits.getValue(2020).droitsDebut)
        assertEquals(argent("6000.00"), droits.getValue(2020).retraits)
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }

    @Test
    fun `un retrait redonne des droits le 1er janvier suivant`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(
            2019 to "6000.00", 2020 to "6000.00", 2021 to "6000.00",
        )
        val transactions = listOf(
            depot("2020-03-01", "6000.00"),
            retrait("2020-08-01", "6000.00"),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2021)
            .associateBy { it.annee }

        // 6000 (fin 2020) + 6000 (plafond 2021) + 6000 (retraits 2020) = 18000.
        assertEquals(argent("18000.00"), droits.getValue(2021).droitsDebut)
        assertEquals(argent("18000.00"), droits.getValue(2021).droitsFin)
    }

    @Test
    fun `une sur-cotisation se propage a l'annee suivante sans etre effacee`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = plafondsCeli(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(depot("2019-05-01", "10000.00"))

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, transactions, jusqua = 2020)
            .associateBy { it.annee }

        // 6000 - 10000 = -4000. Un MAX(..., 0) ici donnerait 0 et masquerait
        // la sur-cotisation, exactement le bug du classeur remplace.
        assertEquals(argent("-4000.00"), droits.getValue(2019).droitsFin)
        // -4000 + 6000 = 2000: l'excedent est absorbe par le plafond suivant.
        assertEquals(argent("2000.00"), droits.getValue(2020).droitsDebut)
    }
```

- [ ] **Step 2 : Lancer les tests**

Run : `./gradlew :engine:test --tests "dev.celitracker.engine.CeliMoteurTest"`
Expected : PASS, 10 tests. Si l'un échoue, corriger `CeliMoteur.kt` — pas le test.

- [ ] **Step 3 : Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Couvre la restitution des droits CELI apres un retrait

Le classeur remplace ne contenait aucun retrait: ce chemin n'avait aucune
validation. Trois cas: pas de restitution dans l'annee du retrait, restitution
au 1er janvier suivant, et propagation d'une sur-cotisation sans effacement.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4 : Moteur CELIAPP — report non cumulatif

Le piège du régime : le report est plafonné à 8 000 $ **par année d'arrivée** et ne s'accumule pas. Écrire `report += reste` au lieu de `report = min(reste, 8000)` produit un chiffre faux et parfaitement plausible.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/CeliappMoteur.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/CeliappMoteurTest.kt`

**Interfaces:**
- Consumes: `Compte`, `TypeTx`, `Transaction`, `Profil`, `BigDecimal.argent()` (Task 1).
- Produces: `data class DroitsAnneeCeliapp(annee: Int, reportEntrant: BigDecimal, droitsAnnee: BigDecimal, depots: BigDecimal, retraits: BigDecimal, reportSortant: BigDecimal, plafondVieRestant: BigDecimal)` · `object CeliappMoteur { val PLAFOND_ANNUEL; val REPORT_MAX; val PLAFOND_VIE; fun droitsParAnnee(profil: Profil, transactions: List<Transaction>, jusqua: Int): List<DroitsAnneeCeliapp> }`

- [ ] **Step 1 : Écrire le test qui échoue**

`engine/src/test/kotlin/dev/celitracker/engine/CeliappMoteurTest.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

private fun depotFhsa(date: String, montant: String) =
    Transaction(Compte.CELIAPP, LocalDate.parse(date), TypeTx.DEPOT, argent(montant))

private fun retraitFhsa(date: String, montant: String) =
    Transaction(Compte.CELIAPP, LocalDate.parse(date), TypeTx.RETRAIT, argent(montant))

class CeliappMoteurTest {

    private val profilOuvert2023 = Profil(
        anneeAdmissibiliteCeli = 2019,
        anneeNaissance = 2001,
        dateOuvertureCeliapp = LocalDate.of(2023, 4, 1),
    )

    /**
     * scenario_fhsa_opened_2023_no_contributions
     *
     * Garde-fou contre l'erreur la plus tentante du regime: croire que trois
     * annees sans cotiser accumulent 32000$. Le report est plafonne a 8000$
     * PAR ANNEE D'ARRIVEE, donc le plafond annuel se stabilise a 16000$.
     */
    @Test
    fun `scenario fhsa opened 2023 no contributions`() {
        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, emptyList(), jusqua = 2026)
            .associateBy { it.annee }

        assertEquals(argent("0.00"), droits.getValue(2023).reportEntrant)
        assertEquals(argent("8000.00"), droits.getValue(2023).droitsAnnee)
        assertEquals(argent("8000.00"), droits.getValue(2023).reportSortant)

        assertEquals(argent("8000.00"), droits.getValue(2024).reportEntrant)
        assertEquals(argent("16000.00"), droits.getValue(2024).droitsAnnee)
        // Le point critique: 8000, PAS 16000. Le report ne se cumule pas.
        assertEquals(argent("8000.00"), droits.getValue(2024).reportSortant)

        assertEquals(argent("16000.00"), droits.getValue(2025).droitsAnnee)
        assertEquals(argent("8000.00"), droits.getValue(2025).reportSortant)

        assertEquals(argent("16000.00"), droits.getValue(2026).droitsAnnee)
        assertEquals(argent("40000.00"), droits.getValue(2026).plafondVieRestant)
    }

    @Test
    fun `l'accumulation demarre a l'annee d'ouverture du compte`() {
        val droits = CeliappMoteur.droitsParAnnee(profilOuvert2023, emptyList(), jusqua = 2026)

        assertEquals(2023, droits.first().annee)
    }

    @Test
    fun `aucun droit sans compte ouvert`() {
        val profilSansCompte = Profil(2019, 2001, dateOuvertureCeliapp = null)

        assertTrue(CeliappMoteur.droitsParAnnee(profilSansCompte, emptyList(), 2026).isEmpty())
    }

    @Test
    fun `un retrait ne redonne jamais de droits`() {
        val transactions = listOf(
            depotFhsa("2023-10-01", "8000.00"),
            retraitFhsa("2023-11-01", "8000.00"),
        )

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2024)
            .associateBy { it.annee }

        // Le retrait est enregistre...
        assertEquals(argent("8000.00"), droits.getValue(2023).retraits)
        // ...mais les 8000 cotises restent consommes a vie: 40000 - 8000.
        assertEquals(argent("32000.00"), droits.getValue(2023).plafondVieRestant)
        // 2023 entierement utilise -> aucun report vers 2024.
        assertEquals(argent("0.00"), droits.getValue(2024).reportEntrant)
        assertEquals(argent("8000.00"), droits.getValue(2024).droitsAnnee)
    }

    @Test
    fun `une cotisation partielle reporte le solde inutilise`() {
        val transactions = listOf(depotFhsa("2023-10-01", "3000.00"))

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2024)
            .associateBy { it.annee }

        // 8000 - 3000 = 5000 inutilises, sous le plafond de report.
        assertEquals(argent("5000.00"), droits.getValue(2023).reportSortant)
        assertEquals(argent("13000.00"), droits.getValue(2024).droitsAnnee)
    }

    @Test
    fun `le plafond a vie de 40000 borne les droits annuels`() {
        val transactions = listOf(
            depotFhsa("2023-10-01", "8000.00"),
            depotFhsa("2024-10-01", "16000.00"),
            depotFhsa("2025-10-01", "16000.00"),
        )

        val droits = CeliappMoteur
            .droitsParAnnee(profilOuvert2023, transactions, jusqua = 2026)
            .associateBy { it.annee }

        // 8000 + 16000 + 16000 = 40000 cotises: le plafond a vie est atteint.
        assertEquals(argent("0.00"), droits.getValue(2025).plafondVieRestant)
        // Meme si le report autoriserait davantage, il ne reste rien a vie.
        assertEquals(argent("0.00"), droits.getValue(2026).droitsAnnee)
    }
}
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :engine:test --tests "dev.celitracker.engine.CeliappMoteurTest"`
Expected : ÉCHEC à la compilation — `Unresolved reference: CeliappMoteur`.

- [ ] **Step 3 : Écrire l'implémentation minimale**

`engine/src/main/kotlin/dev/celitracker/engine/CeliappMoteur.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal

data class DroitsAnneeCeliapp(
    val annee: Int,
    val reportEntrant: BigDecimal,
    val droitsAnnee: BigDecimal,
    val depots: BigDecimal,
    val retraits: BigDecimal,
    val reportSortant: BigDecimal,
    val plafondVieRestant: BigDecimal,
)

/**
 * Moteur CELIAPP. Deliberement separe de [CeliMoteur]: les deux regimes
 * divergent sur chaque axe, et reutiliser le chemin de restitution du CELI
 * pour le CELIAPP est le bug de correctness le plus probable de ce projet.
 *
 * Les trois plafonds sont fixes par la loi et ne sont PAS indexes: contrairement
 * au CELI, il n'y a rien a recuperer sur le site de l'ARC.
 */
object CeliappMoteur {

    val PLAFOND_ANNUEL: BigDecimal = BigDecimal("8000").argent()

    /**
     * Plafond du report, PAR ANNEE D'ARRIVEE. Le report ne se cumule pas:
     * une personne qui ne cotise jamais voit son plafond annuel se stabiliser
     * a 16000 (8000 + 8000), pas croitre de 8000 chaque annee.
     */
    val REPORT_MAX: BigDecimal = BigDecimal("8000").argent()

    val PLAFOND_VIE: BigDecimal = BigDecimal("40000").argent()

    fun droitsParAnnee(
        profil: Profil,
        transactions: List<Transaction>,
        jusqua: Int,
    ): List<DroitsAnneeCeliapp> {
        // L'accumulation demarre a l'OUVERTURE du compte, pas aux 18 ans.
        val ouverture = profil.dateOuvertureCeliapp ?: return emptyList()
        val txFhsa = transactions.filter { it.compte == Compte.CELIAPP }

        val resultat = mutableListOf<DroitsAnneeCeliapp>()
        var reportEntrant = BigDecimal.ZERO
        var cotisationsCumulees = BigDecimal.ZERO

        for (annee in ouverture.year..jusqua) {
            val depots = somme(txFhsa, annee, TypeTx.DEPOT)
            val retraits = somme(txFhsa, annee, TypeTx.RETRAIT)

            val vieRestantAvant = (PLAFOND_VIE - cotisationsCumulees)
                .coerceAtLeast(BigDecimal.ZERO)
            val droitsAnnee = minOf(PLAFOND_ANNUEL + reportEntrant, vieRestantAvant)

            // min(..., REPORT_MAX) et NON une accumulation: c'est toute la
            // difference avec le CELI et le REER.
            val reportSortant = minOf(
                (droitsAnnee - depots).coerceAtLeast(BigDecimal.ZERO),
                REPORT_MAX,
            )

            cotisationsCumulees += depots

            resultat += DroitsAnneeCeliapp(
                annee = annee,
                reportEntrant = reportEntrant.argent(),
                droitsAnnee = droitsAnnee.argent(),
                depots = depots.argent(),
                // Enregistre pour l'affichage du solde, mais n'entre dans AUCUN
                // calcul de droits: un retrait CELIAPP ne redonne jamais rien.
                retraits = retraits.argent(),
                reportSortant = reportSortant.argent(),
                plafondVieRestant = (PLAFOND_VIE - cotisationsCumulees)
                    .coerceAtLeast(BigDecimal.ZERO).argent(),
            )

            reportEntrant = reportSortant
        }
        return resultat
    }

    private fun somme(transactions: List<Transaction>, annee: Int, type: TypeTx): BigDecimal =
        transactions
            .filter { it.date.year == annee && it.type == type }
            .fold(BigDecimal.ZERO) { total, tx -> total + tx.montant }
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :engine:test`
Expected : PASS, 16 tests.

- [ ] **Step 5 : Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Ajoute le moteur CELIAPP avec report non cumulatif

Le report est plafonne a 8000$ par annee d'arrivee et ne s'accumule pas: un
compte ouvert sans cotisation se stabilise a 16000$ de plafond annuel, pas
8000$ de plus chaque annee. Un retrait n'y redonne jamais de droits.

Moteur volontairement separe de CeliMoteur.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5 : Fin de la période de participation CELIAPP

**Files:**
- Modify: `engine/src/main/kotlin/dev/celitracker/engine/CeliappMoteur.kt` (ajout d'une fonction)
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/CeliappMoteurTest.kt` (ajout de tests)

**Interfaces:**
- Consumes: `Profil` (Task 1), `CeliappMoteur` (Task 4).
- Produces: `fun CeliappMoteur.finPeriodeParticipation(profil: Profil): LocalDate?`

- [ ] **Step 1 : Écrire les tests qui échouent**

Ajouter dans `CeliappMoteurTest.kt` (et l'import `java.time.LocalDate` est déjà présent) :

```kotlin
    @Test
    fun `l'echeance est le 31 decembre de l'annee du 15e anniversaire`() {
        // Ouvert en avril 2023 -> 15e anniversaire en avril 2038.
        // La periode se termine le 31 decembre de CETTE annee-la.
        assertEquals(
            LocalDate.of(2038, 12, 31),
            CeliappMoteur.finPeriodeParticipation(profilOuvert2023),
        )
    }

    @Test
    fun `la branche des 71 ans l'emporte quand elle est plus rapprochee`() {
        val profilAge = Profil(
            anneeAdmissibiliteCeli = 1975,
            anneeNaissance = 1960, // 71 ans en 2031
            dateOuvertureCeliapp = LocalDate.of(2023, 4, 1), // 15 ans -> 2038
        )

        assertEquals(
            LocalDate.of(2031, 12, 31),
            CeliappMoteur.finPeriodeParticipation(profilAge),
        )
    }

    @Test
    fun `aucune echeance sans compte ouvert`() {
        val profilSansCompte = Profil(2019, 2001, dateOuvertureCeliapp = null)

        assertEquals(null, CeliappMoteur.finPeriodeParticipation(profilSansCompte))
    }
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :engine:test --tests "dev.celitracker.engine.CeliappMoteurTest"`
Expected : ÉCHEC à la compilation — `Unresolved reference: finPeriodeParticipation`.

- [ ] **Step 3 : Écrire l'implémentation minimale**

Ajouter dans `object CeliappMoteur`, après `droitsParAnnee`, et ajouter `import java.time.LocalDate` en haut du fichier :

```kotlin
    /**
     * Fin de la periode de participation maximale: le 31 decembre de l'annee ou
     * survient le PREMIER des trois evenements suivants.
     *
     *   1. le 15e anniversaire de l'ouverture du premier CELIAPP
     *   2. les 71 ans du titulaire
     *   3. l'annee suivant le premier retrait admissible
     *
     * La branche 3 n'est PAS implementee: elle exige de distinguer un retrait
     * admissible (achat d'une premiere propriete) d'un retrait ordinaire, ce que
     * le modele ne suit pas. L'echeance reelle peut donc etre plus rapprochee
     * que celle retournee ici. Exclusion assumee, documentee dans la spec.
     */
    fun finPeriodeParticipation(profil: Profil): LocalDate? {
        val ouverture = profil.dateOuvertureCeliapp ?: return null
        val anneeQuinzeAns = ouverture.year + 15
        val anneeSoixanteEtOnzeAns = profil.anneeNaissance + 71
        return LocalDate.of(minOf(anneeQuinzeAns, anneeSoixanteEtOnzeAns), 12, 31)
    }
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :engine:test`
Expected : PASS, 19 tests.

- [ ] **Step 5 : Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Calcule la fin de periode de participation CELIAPP

31 decembre de l'annee du premier evenement entre le 15e anniversaire de
l'ouverture et les 71 ans. La troisieme branche (annee suivant le premier
retrait admissible) reste non implementee, faute de classification des
retraits: l'echeance reelle peut etre plus rapprochee que celle affichee.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6 : Sur-cotisation CELI — excédent mensuel et pénalité

Ce que le classeur ne pouvait pas faire. La pénalité de l'ARC est de 1 % par mois appliqué à l'excédent **le plus élevé du mois**, ce qui exige un solde rejouable dans l'ordre chronologique.

**Files:**
- Create: `engine/src/main/kotlin/dev/celitracker/engine/SurCotisation.kt`
- Test: `engine/src/test/kotlin/dev/celitracker/engine/SurCotisationTest.kt`

**Interfaces:**
- Consumes: `Compte`, `TypeTx`, `Transaction`, `PlafondAnnuel`, `Profil`, `argent()` (Task 1) ; `CeliMoteur.droitsParAnnee`, `DroitsAnnee` (Task 2).
- Produces: `data class ExcedentMensuel(annee: Int, mois: Int, excedentMax: BigDecimal, penalite: BigDecimal)` · `object SurCotisation { fun excedentsCeli(profil: Profil, plafonds: List<PlafondAnnuel>, transactions: List<Transaction>, jusqua: YearMonth): List<ExcedentMensuel> }`

- [ ] **Step 1 : Écrire le test qui échoue**

`engine/src/test/kotlin/dev/celitracker/engine/SurCotisationTest.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun argent(valeur: String): BigDecimal = BigDecimal(valeur).argent()

class SurCotisationTest {

    private val profil = Profil(2019, 2001, null)
    private val plafonds = listOf(
        PlafondAnnuel(Compte.CELI, 2019, argent("6000.00")),
        PlafondAnnuel(Compte.CELI, 2020, argent("6000.00")),
    )

    private fun tx(date: String, type: TypeTx, montant: String) =
        Transaction(Compte.CELI, LocalDate.parse(date), type, argent(montant))

    @Test
    fun `aucun excedent quand les cotisations respectent les droits`() {
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "6000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        )

        assertTrue(excedents.isEmpty())
    }

    @Test
    fun `un excedent persiste chaque mois jusqu'a la fin de l'annee`() {
        // Droits 2019 = 6000, depot de 10000 -> excedent de 4000.
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "10000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        )

        // Mars a decembre inclus = 10 mois.
        assertEquals(10, excedents.size)
        assertEquals(3, excedents.first().mois)
        assertEquals(argent("4000.00"), excedents.first().excedentMax)
        assertEquals(argent("40.00"), excedents.first().penalite)
        assertEquals(12, excedents.last().mois)
        assertEquals(argent("4000.00"), excedents.last().excedentMax)
    }

    @Test
    fun `re-cotiser un montant retire la meme annee recree l'excedent`() {
        // LE piege du regime. Un retrait annule l'excedent existant, mais ne
        // redonne AUCUN droit avant le 1er janvier suivant. Re-cotiser le meme
        // montant la meme annee cree donc un excedent plein.
        val transactions = listOf(
            tx("2019-02-01", TypeTx.DEPOT, "6000.00"),   // droits epuises, 0 excedent
            tx("2019-04-01", TypeTx.RETRAIT, "6000.00"), // aucun droit restitue
            tx("2019-06-01", TypeTx.DEPOT, "6000.00"),   // re-cotisation -> excedent
        )

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        ).associateBy { it.mois }

        // Fevrier a mai: les droits couvrent les cotisations, aucun excedent.
        assertTrue(excedents[2] == null)
        assertTrue(excedents[5] == null)
        // Juin: les droits etaient deja epuises, le retrait n'en a pas rendu.
        assertEquals(argent("6000.00"), excedents.getValue(6).excedentMax)
        assertEquals(argent("60.00"), excedents.getValue(6).penalite)
        // L'excedent persiste jusqu'a la fin de l'annee: juin a decembre.
        assertEquals(7, excedents.size)
        assertEquals(argent("6000.00"), excedents.getValue(12).excedentMax)
    }

    @Test
    fun `un retrait annule l'excedent mais le mois reste facture`() {
        val transactions = listOf(
            tx("2019-02-01", TypeTx.DEPOT, "6000.00"),
            tx("2019-03-01", TypeTx.DEPOT, "1000.00"),   // depassement de 1000
            tx("2019-04-15", TypeTx.RETRAIT, "1000.00"), // corrige en avril
        )

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2019, 12),
        ).associateBy { it.mois }

        assertEquals(argent("1000.00"), excedents.getValue(3).excedentMax)
        assertEquals(argent("10.00"), excedents.getValue(3).penalite)
        // Avril reste facture: la penalite porte sur l'excedent le PLUS ELEVE
        // du mois, et il valait 1000 jusqu'au 15.
        assertEquals(argent("1000.00"), excedents.getValue(4).excedentMax)
        // Mai est propre: l'excedent a ete annule par le retrait.
        assertTrue(excedents[5] == null)
    }

    @Test
    fun `l'excedent est absorbe par les droits de l'annee suivante`() {
        // Droits 2019 = 6000, depot de 10000 -> excedent de 4000 jusqu'en
        // decembre. Au 1er janvier 2020, le plafond de 6000 absorbe l'excedent
        // (droitsDebut 2020 = -4000 + 6000 = 2000 > 0).
        val transactions = listOf(tx("2019-03-15", TypeTx.DEPOT, "10000.00"))

        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, transactions, jusqua = YearMonth.of(2020, 12),
        )

        assertEquals(2019, excedents.last().annee)
        assertEquals(12, excedents.last().mois)
        assertTrue(excedents.none { it.annee == 2020 })
    }

    @Test
    fun `aucun excedent sans transaction`() {
        val excedents = SurCotisation.excedentsCeli(
            profil, plafonds, emptyList(), jusqua = YearMonth.of(2019, 12),
        )

        assertTrue(excedents.isEmpty())
    }
}
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run : `./gradlew :engine:test --tests "dev.celitracker.engine.SurCotisationTest"`
Expected : ÉCHEC à la compilation — `Unresolved reference: SurCotisation`.

- [ ] **Step 3 : Écrire l'implémentation minimale**

`engine/src/main/kotlin/dev/celitracker/engine/SurCotisation.kt` :

```kotlin
package dev.celitracker.engine

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Excedent d'un mois et penalite correspondante.
 *
 * [excedentMax] est l'excedent le PLUS ELEVE atteint pendant le mois, pas celui
 * de la fin du mois: c'est sur cette base que l'ARC calcule la penalite.
 */
data class ExcedentMensuel(
    val annee: Int,
    val mois: Int,
    val excedentMax: BigDecimal,
    val penalite: BigDecimal,
)

object SurCotisation {

    /** 1 % par mois de l'excedent le plus eleve du mois. */
    private val TAUX_PENALITE_MENSUELLE = BigDecimal("0.01")

    fun excedentsCeli(
        profil: Profil,
        plafonds: List<PlafondAnnuel>,
        transactions: List<Transaction>,
        jusqua: YearMonth,
    ): List<ExcedentMensuel> {
        val txCeli = transactions
            .filter { it.compte == Compte.CELI }
            .sortedBy { it.date }
        val premier = txCeli.firstOrNull() ?: return emptyList()

        val droitsDebutParAnnee = CeliMoteur
            .droitsParAnnee(profil, plafonds, transactions, jusqua.year)
            .associate { it.annee to it.droitsDebut }

        val resultat = mutableListOf<ExcedentMensuel>()
        var mois = YearMonth.from(premier.date)

        // DEUX variables, et non un cumul net. Un cumul net rendrait une
        // re-cotisation gratuite, alors que c'est exactement le piege du regime:
        //   - un DEPOT consomme d'abord les droits restants, le reste devient
        //     de l'excedent;
        //   - un RETRAIT annule l'excedent existant, mais ne restitue AUCUN
        //     droit -- ceux-ci ne reviennent que le 1er janvier suivant, via
        //     droitsDebut de l'annee suivante.
        var anneeCourante = Int.MIN_VALUE
        var droitsRestants = BigDecimal.ZERO
        var excedent = BigDecimal.ZERO

        while (!mois.isAfter(jusqua)) {
            if (mois.year != anneeCourante) {
                anneeCourante = mois.year
                // droitsDebut inclut deja le report, le plafond de l'annee et
                // les retraits de l'annee precedente. S'il est negatif, la
                // sur-cotisation n'a pas ete absorbee et se poursuit.
                val debut = droitsDebutParAnnee[anneeCourante] ?: BigDecimal.ZERO
                if (debut.signum() < 0) {
                    excedent = debut.negate()
                    droitsRestants = BigDecimal.ZERO
                } else {
                    excedent = BigDecimal.ZERO
                    droitsRestants = debut
                }
            }

            // L'excedent reporte du mois precedent est deja facturable.
            var excedentMax = excedent

            for (tx in txCeli.filter { YearMonth.from(it.date) == mois }) {
                if (tx.type == TypeTx.DEPOT) {
                    val absorbe = minOf(droitsRestants, tx.montant)
                    droitsRestants -= absorbe
                    excedent += tx.montant - absorbe
                } else {
                    excedent = (excedent - tx.montant).coerceAtLeast(BigDecimal.ZERO)
                    // droitsRestants est volontairement inchange.
                }
                excedentMax = maxOf(excedentMax, excedent)
            }

            if (excedentMax.signum() > 0) {
                resultat += ExcedentMensuel(
                    annee = mois.year,
                    mois = mois.monthValue,
                    excedentMax = excedentMax.argent(),
                    penalite = (excedentMax * TAUX_PENALITE_MENSUELLE).argent(),
                )
            }
            mois = mois.plusMonths(1)
        }
        return resultat
    }
}
```

- [ ] **Step 4 : Lancer les tests pour vérifier qu'ils passent**

Run : `./gradlew :engine:test`
Expected : PASS, 25 tests.

- [ ] **Step 5 : Commit et push**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Detecte la sur-cotisation CELI et estime la penalite

La penalite de l'ARC est de 1% par mois sur l'excedent le plus eleve du mois,
ce qui exige un solde rejouable dans l'ordre chronologique -- possible
uniquement parce qu'aucune valeur calculee n'est stockee.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push origin main
```

---

### Task 7 : Plafonds manquants ou non confirmés

Un plafond absent ou proposé par la lecture automatique sans confirmation ne doit **jamais** créer de droits. L'erreur penche du côté prudent.

**Files:**
- Modify: `engine/src/test/kotlin/dev/celitracker/engine/CeliMoteurTest.kt` (ajout de tests)

**Interfaces:**
- Consumes: `CeliMoteur.droitsParAnnee`, `DroitsAnnee` (Task 2), `PlafondAnnuel.confirme` (Task 1).
- Produces: rien de nouveau. Valide un comportement déjà implémenté.

- [ ] **Step 1 : Écrire les tests qui doivent passer**

Ajouter dans `CeliMoteurTest.kt` :

```kotlin
    @Test
    fun `une annee sans plafond est signalee et ne cree aucun droit`() {
        val profil = Profil(2019, 2001, null)
        // 2020 absent de la table.
        val plafonds = plafondsCeli(2019 to "6000.00")

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, emptyList(), jusqua = 2020)
            .associateBy { it.annee }

        assertEquals(false, droits.getValue(2019).plafondManquant)
        assertEquals(true, droits.getValue(2020).plafondManquant)
        assertEquals(argent("0.00"), droits.getValue(2020).plafond)
        // Les droits stagnent: le moteur n'invente pas de plafond.
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }

    @Test
    fun `un plafond non confirme est traite comme absent`() {
        val profil = Profil(2019, 2001, null)
        val plafonds = listOf(
            PlafondAnnuel(Compte.CELI, 2019, argent("6000.00")),
            // Propose par la lecture automatique du site de l'ARC, pas encore
            // valide par l'utilisateur: il ne doit pas entrer dans le calcul.
            PlafondAnnuel(Compte.CELI, 2020, argent("6000.00"), confirme = false),
        )

        val droits = CeliMoteur.droitsParAnnee(profil, plafonds, emptyList(), jusqua = 2020)
            .associateBy { it.annee }

        assertEquals(true, droits.getValue(2020).plafondManquant)
        assertEquals(argent("6000.00"), droits.getValue(2020).droitsFin)
    }
```

- [ ] **Step 2 : Lancer les tests**

Run : `./gradlew :engine:test`
Expected : PASS, 27 tests. Si l'un échoue, corriger `CeliMoteur.kt` — pas le test.

- [ ] **Step 3 : Commit**

```bash
cd /c/Dev/celi-tracker
git add engine
git commit -m "$(cat <<'EOF'
Verrouille le traitement des plafonds absents ou non confirmes

Un plafond absent de la table, ou propose par la lecture automatique sans
confirmation de l'utilisateur, ne cree aucun droit. L'erreur penche du cote
prudent: droits sous-estimes, jamais d'incitation a sur-cotiser.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8 : Protection de branche et auto-merge Dependabot

La barrière CI reportée à l'amorçage du dépôt. Elle ne pouvait pas être posée avant : un check requis qui ne peut pas passer rend `main` impossible à merger. Maintenant que `Build & test` est vert, elle a du sens.

**Files:**
- Create: `.github/workflows/dependabot-auto-merge.yml`
- Modify: `CLAUDE.md` (section Git)

**Interfaces:**
- Consumes: le check `Build & test` produit par `.github/workflows/build.yml`.
- Produces: rien en code.

- [ ] **Step 1 : Activer la protection de `main`**

```bash
cd /c/Dev/celi-tracker
gh api -X PUT repos/Mrodrigue14/celi-tracker/branches/main/protection --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["Build & test"] },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON
```

Vérifier : `gh api repos/Mrodrigue14/celi-tracker/branches/main/protection --jq '.required_status_checks.contexts'`
Expected : `["Build & test"]`

- [ ] **Step 2 : Écrire le workflow d'auto-merge**

`.github/workflows/dependabot-auto-merge.yml` :

```yaml
name: Dependabot auto-merge

# Auto-merge des PRs Dependabot une fois la CI verte.
#
# SÉCURITÉ — ce workflow suit le patron officiel GitHub :
#
#   1. Déclencheur `pull_request` (JAMAIS `pull_request_target`) : le job n'a
#      pas accès aux secrets du dépôt.
#   2. AUCUN `checkout` ni exécution du code de la PR.
#   3. Vérification de l'auteur via `github.event.pull_request.user.login` (le
#      champ non falsifiable) et NON `github.actor` (falsifiable sur
#      synchronize via `@dependabot recreate`).
#   4. Aucune interpolation de donnée contrôlable dans un `run:` — seul
#      `html_url`, généré par GitHub, passe par une variable d'environnement.
#   5. Permissions minimales.
#
# Le merge est délégué à l'auto-merge natif de GitHub (`--auto`), qui n'aboutit
# QUE si les checks requis par la protection de branche passent. La protection
# de `main` exigeant « Build & test » est donc la barrière réelle : sans elle,
# ce workflow fusionnerait sans garde-fou.
#
# PORTÉE : patch et mineur uniquement, tous écosystèmes. Aucune montée majeure
# n'est auto-mergée. Pas de liste de workflows « non couverts » à maintenir —
# une telle liste devient périmée en silence et finit par laisser passer ce
# qu'elle devait retenir.
on: pull_request

permissions:
  contents: write
  pull-requests: write

jobs:
  auto-merge:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    if: ${{ github.event.pull_request.user.login == 'dependabot[bot]' }}
    steps:
      - name: Récupérer les métadonnées Dependabot
        id: meta
        uses: dependabot/fetch-metadata@v3
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}

      - name: Activer l'auto-merge
        if: >
          steps.meta.outputs.update-type == 'version-update:semver-patch' ||
          steps.meta.outputs.update-type == 'version-update:semver-minor'
        run: gh pr merge --auto --merge "$PR_URL"
        env:
          PR_URL: ${{ github.event.pull_request.html_url }}
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

- [ ] **Step 3 : Mettre `CLAUDE.md` à jour**

Remplacer la section `## Git` par :

```markdown
## Git

`main` est protégée : le check « Build & test » doit passer, et la branche doit
être à jour avec `main` avant le merge. Travailler en branche, ouvrir une PR,
laisser la CI verte avant de merger.

Les PRs Dependabot patch et mineures sont auto-mergées une fois la CI verte.
Les montées majeures sont bloquées par `.github/dependabot.yml` et restent en
revue manuelle.
```

- [ ] **Step 4 : Commit et push via une PR**

`main` est désormais protégée : le push direct est refusé. C'est le premier exercice réel de la barrière.

```bash
cd /c/Dev/celi-tracker
git checkout -b ci/auto-merge-dependabot
git add .github/workflows/dependabot-auto-merge.yml CLAUDE.md
git commit -m "$(cat <<'EOF'
Active l'auto-merge Dependabot derriere la protection de branche

La protection de main exigeant « Build & test » est la barriere reelle: --auto
n'aboutit que si les checks requis passent. Portee limitee a patch et mineur,
sans liste de workflows « non couverts » a maintenir -- une telle liste devient
perimee en silence.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push -u origin ci/auto-merge-dependabot
gh pr create --title "Active l'auto-merge Dependabot" --body "$(cat <<'EOF'
Pose la barriere CI reportee a l'amorcage du depot: la protection de `main`
exige desormais le check « Build & test ».

L'auto-merge Dependabot est limite aux montees patch et mineures. Les majeures
restent en revue manuelle, et `.github/dependabot.yml` bloque deja les majeures
Gradle.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5 : Vérifier que la barrière fonctionne, puis merger**

Run : `gh pr checks --watch`
Expected : `Build & test` en `pass`.

```bash
gh pr merge --merge --delete-branch
git checkout main && git pull
```

---

## Auto-revue

**1. Couverture de la spec.**

| Exigence de la spec | Tâche |
|---|---|
| Modèle de données (types de saisie) | 1 |
| `BigDecimal` et non `Double` | 1 (test dédié) |
| Formule CELI + fixture 41 800,00 $ | 2 |
| Retraits restitués au 1er janvier suivant | 3 |
| Sur-cotisation propagée sans effacement | 3 |
| Formule CELIAPP + report non cumulatif + fixture 16 000 $ | 4 |
| Retrait CELIAPP ne redonne aucun droit | 4 |
| Plafond à vie 40 000 $ | 4 |
| Période de participation (branches 15 ans / 71 ans) | 5 |
| Excédent mensuel + pénalité 1 %/mois | 6 |
| Plafond absent ou non confirmé | 7 |
| Moteurs CELI et CELIAPP séparés | 2 et 4, contrainte globale |

**Hors de ce plan, à couvrir par les plans suivants :** persistance Room, export/import JSON, écrans Compose, lecture des plafonds sur le site de l'ARC (validation multiple de 500, URL éditable, échec visible), sauvegarde automatique Android, snapshot ARC, installation du SDK Android, APK.

**2. Placeholders.** Aucun « TBD », aucun « similaire à la tâche N », aucune étape sans code.

**3. Cohérence des types.** `argent()` est définie une fois (Task 1) et utilisée partout. `DroitsAnnee` (Task 2) est consommée telle quelle par les tâches 3, 6 et 7. `DroitsAnneeCeliapp` et `CeliappMoteur` (Task 4) sont étendus par la tâche 5. `SurCotisation.excedentsCeli` consomme `CeliMoteur.droitsParAnnee` avec la signature exacte de la tâche 2. Le helper privé `argent(String)` est redéfini dans chaque fichier de test — c'est volontaire : les fichiers de test restent indépendants et lisibles isolément.

**Note d'exécution.** Les compteurs de tests cumulés (4, 7, 10, 16, 19, 25, 27) supposent l'exécution des tâches dans l'ordre.
