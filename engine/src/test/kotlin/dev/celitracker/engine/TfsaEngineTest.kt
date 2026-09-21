package dev.celitracker.engine

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Raccourci: whole litteral monetaire d'un test s'ecrit a 2 decimales. */
private fun toMoney(value: String): BigDecimal = BigDecimal(value).toMoney()

private fun tfsaLimits(vararg pairs: Pair<Int, String>): List<AnnualLimit> = pairs.map { (year, amount) -> AnnualLimit(Account.TFSA, year, toMoney(amount)) }

private fun repository(date: String, amount: String) = Transaction(Account.TFSA, LocalDate.parse(date), TransactionType.DEPOSIT, toMoney(amount))

private fun withdrawal(date: String, amount: String) = Transaction(Account.TFSA, LocalDate.parse(date), TransactionType.WITHDRAWAL, toMoney(amount))

class TfsaEngineTest {

    /**
     * scenario_2019_eligible_three_deposits
     *
     * Personne devenue admissible au TFSA en 2019, aucun withdrawal, trois deposits.
     * Scenario synthetique, derive des limits annuels publies par l'ARC.
     * Controle: cumul des limits 51 500 - deposits 9 700 = 41 800.
     */
    @Test
    fun `scenario 2019 eligible three deposits`() {
        val profile = Profile(
            birthYear = 2001,
            fhsaOpeningDate = null,
        )
        val limits = tfsaLimits(
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
            repository("2021-03-10", "5000.00"),
            repository("2023-06-15", "3500.00"),
            repository("2024-11-02", "1200.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2026)
            .associateBy { it.year }

        assertEquals(toMoney("6000.00"), room.getValue(2019).startRoom)
        assertEquals(toMoney("6000.00"), room.getValue(2019).endRoom)

        assertEquals(toMoney("12000.00"), room.getValue(2020).startRoom)
        assertEquals(toMoney("12000.00"), room.getValue(2020).endRoom)

        assertEquals(toMoney("18000.00"), room.getValue(2021).startRoom)
        assertEquals(toMoney("5000.00"), room.getValue(2021).deposits)
        assertEquals(toMoney("13000.00"), room.getValue(2021).endRoom)

        assertEquals(toMoney("19000.00"), room.getValue(2022).startRoom)
        assertEquals(toMoney("19000.00"), room.getValue(2022).endRoom)

        assertEquals(toMoney("25500.00"), room.getValue(2023).startRoom)
        assertEquals(toMoney("3500.00"), room.getValue(2023).deposits)
        assertEquals(toMoney("22000.00"), room.getValue(2023).endRoom)

        assertEquals(toMoney("29000.00"), room.getValue(2024).startRoom)
        assertEquals(toMoney("1200.00"), room.getValue(2024).deposits)
        assertEquals(toMoney("27800.00"), room.getValue(2024).endRoom)

        assertEquals(toMoney("34800.00"), room.getValue(2025).startRoom)
        assertEquals(toMoney("34800.00"), room.getValue(2025).endRoom)

        assertEquals(toMoney("41800.00"), room.getValue(2026).startRoom)
        assertEquals(toMoney("41800.00"), room.getValue(2026).endRoom)
    }

    @Test
    fun `les annees vont de l'admissibilite a l'annee demandee`() {
        val profile = Profile(2001, null)
        val room = TfsaEngine.roomByYear(
            profile,
            tfsaLimits(2019 to "6000.00", 2020 to "6000.00"),
            emptyList(),
            upTo = 2020,
        )

        assertEquals(listOf(2019, 2020), room.map { it.year })
    }

    @Test
    fun `les transactions CELIAPP sont ignorees par le moteur CELI`() {
        val profile = Profile(2001, LocalDate.of(2023, 4, 1))
        val transactions = listOf(
            Transaction(Account.FHSA, LocalDate.of(2019, 5, 1), TransactionType.DEPOSIT, toMoney("5000.00")),
        )

        val room = TfsaEngine.roomByYear(
            profile,
            tfsaLimits(2019 to "6000.00"),
            transactions,
            upTo = 2019,
        )

        assertEquals(toMoney("0.00"), room.single().deposits)
        assertEquals(toMoney("6000.00"), room.single().endRoom)
    }

    @Test
    fun `un retrait ne redonne pas de droits dans l'annee du retrait`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(
            repository("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 (end 2019) + 6000 (limit 2020) + 0 (withdrawals 2019) = 12000.
        // Le withdrawal de 2020 n'ajoute RIEN aux room de 2020.
        assertEquals(toMoney("12000.00"), room.getValue(2020).startRoom)
        assertEquals(toMoney("6000.00"), room.getValue(2020).withdrawals)
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `un retrait redonne des droits le 1er janvier suivant`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(
            2019 to "6000.00",
            2020 to "6000.00",
            2021 to "6000.00",
        )
        val transactions = listOf(
            repository("2020-03-01", "6000.00"),
            withdrawal("2020-08-01", "6000.00"),
        )

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2021)
            .associateBy { it.year }

        // 6000 (end 2020) + 6000 (limit 2021) + 6000 (withdrawals 2020) = 18000.
        assertEquals(toMoney("18000.00"), room.getValue(2021).startRoom)
        assertEquals(toMoney("18000.00"), room.getValue(2021).endRoom)
    }

    @Test
    fun `une sur-cotisation se propage a l'annee suivante sans etre effacee`() {
        val profile = Profile(2001, null)
        val limits = tfsaLimits(2019 to "6000.00", 2020 to "6000.00")
        val transactions = listOf(repository("2019-05-01", "10000.00"))

        val room = TfsaEngine.roomByYear(profile, limits, transactions, upTo = 2020)
            .associateBy { it.year }

        // 6000 - 10000 = -4000. Un MAX(..., 0) ici donnerait 0 et masquerait
        // la sur-cotisation, exactement le bug du classeur remplace.
        assertEquals(toMoney("-4000.00"), room.getValue(2019).endRoom)
        // -4000 + 6000 = 2000: l'excess est absorbed par le limit suivant.
        assertEquals(toMoney("2000.00"), room.getValue(2020).startRoom)
    }

    @Test
    fun `une annee sans plafond est signalee et ne cree aucun droit`() {
        val profile = Profile(2001, null)
        // 2020 absent de la table.
        val limits = tfsaLimits(2019 to "6000.00")

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(false, room.getValue(2019).limitMissing)
        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(toMoney("0.00"), room.getValue(2020).limit)
        // Les room stagnent: le moteur n'invente labelStep de limit.
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }

    @Test
    fun `un plafond non confirme est traite comme absent`() {
        val profile = Profile(2001, null)
        val limits = listOf(
            AnnualLimit(Account.TFSA, 2019, toMoney("6000.00")),
            // Proposed par la lecture automatique du site de l'ARC, labelStep encore
            // valid par l'utilisateur: il ne doit labelStep entrer dans le calcul.
            AnnualLimit(Account.TFSA, 2020, toMoney("6000.00"), confirmed = false),
        )

        val room = TfsaEngine.roomByYear(profile, limits, emptyList(), upTo = 2020)
            .associateBy { it.year }

        assertEquals(true, room.getValue(2020).limitMissing)
        assertEquals(toMoney("0.00"), room.getValue(2020).limit)
        assertEquals(toMoney("6000.00"), room.getValue(2020).endRoom)
    }
}
