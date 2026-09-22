package dev.celitracker.engine

import java.math.BigDecimal

data class SnapshotComparison(val snapshot: CraSnapshot, val calculatedRoom: BigDecimal) {
    /** Positive when the CRA shows more room than the calculation. */
    val difference: BigDecimal get() = (snapshot.declaredRoom - calculatedRoom).toMoney()
}

/**
 * Compared with the room on January 1 of the reference year: the CRA figure leaves out that year's deposits,
 * so remaining room would show a difference equal to them.
 */
fun tfsaSnapshotComparison(snapshots: List<CraSnapshot>, rows: List<TfsaYear>): SnapshotComparison? {
    val snapshot = latestSnapshot(snapshots, Account.TFSA) ?: return null
    val year = snapshot.referenceDate.year
    if (rows.any { it.year <= year && it.limitMissing }) return null
    val row = rows.find { it.year == year } ?: return null
    return SnapshotComparison(snapshot, row.startRoom)
}

fun fhsaSnapshotComparison(snapshots: List<CraSnapshot>, rows: List<FhsaYear>): SnapshotComparison? {
    val snapshot = latestSnapshot(snapshots, Account.FHSA) ?: return null
    val row = rows.find { it.year == snapshot.referenceDate.year } ?: return null
    return SnapshotComparison(snapshot, row.yearRoom)
}

/** The same order decides which figure the comparison uses and how the settings list them. */
fun List<CraSnapshot>.newestFirst(): List<CraSnapshot> = sortedWith(compareByDescending<CraSnapshot> { it.referenceDate }.thenByDescending { it.id })

private fun latestSnapshot(snapshots: List<CraSnapshot>, account: Account): CraSnapshot? = snapshots
    .filter { it.account == account }
    .newestFirst()
    .firstOrNull()
