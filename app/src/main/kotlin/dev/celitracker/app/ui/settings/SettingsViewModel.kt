package dev.celitracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.celitracker.app.R
import dev.celitracker.app.ui.text.textRes
import dev.celitracker.app.ui.text.uiText
import dev.celitracker.data.CraAddressResult
import dev.celitracker.data.CraCheckResult
import dev.celitracker.data.DEFAULT_CRA_PAGE_URL
import dev.celitracker.data.InvalidImport
import dev.celitracker.data.Repository
import dev.celitracker.data.changeCraPageUrl
import dev.celitracker.data.checkCraLimits
import dev.celitracker.data.exportJson
import dev.celitracker.data.importJson
import dev.celitracker.engine.Account
import dev.celitracker.engine.AnnualLimit
import dev.celitracker.engine.CraSnapshot
import dev.celitracker.engine.Profile
import dev.celitracker.engine.UNSAVED_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(
    private val repository: Repository,
    private val downloadPage: suspend (String) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
        checkCra(explicitRequest = false)
    }

    /** Only empty fields are filled unless [replaceInputs]: the async read would overwrite typing. */
    fun load(replaceInputs: Boolean = false) {
        viewModelScope.launch {
            val profile = repository.profile()
            val limits = tfsaLimits()
            val settings = repository.settings()
            val birth = profile?.birthYear?.toString() ?: ""
            val opening = profile?.fhsaOpeningDate?.toString() ?: ""
            _uiState.update {
                it.copy(
                    birthYear = if (replaceInputs) birth else it.birthYear.ifBlank { birth },
                    fhsaOpeningDate = if (replaceInputs) opening else it.fhsaOpeningDate.ifBlank { opening },
                    limits = limits,
                    snapshots = craSnapshots(),
                    craPageUrl = it.craPageUrl.ifBlank { settings.craPageUrl },
                    lastCraCheck = settings.lastCheckDate,
                )
            }
        }
    }

    fun updateBirthYear(value: String) = _uiState.update { it.copy(birthYear = value) }
    fun updateFhsaOpeningDate(value: String) = _uiState.update { it.copy(fhsaOpeningDate = value) }
    fun updateNewLimitYear(value: String) = _uiState.update { it.copy(newLimitYear = value) }
    fun updateNewLimitAmount(value: String) = _uiState.update { it.copy(newLimitAmount = value) }

    fun updateSnapshotAccount(value: Account) = _uiState.update { it.copy(snapshotAccount = value) }
    fun updateSnapshotDate(value: String) = _uiState.update { it.copy(snapshotDate = value) }
    fun updateSnapshotAmount(value: String) = _uiState.update { it.copy(snapshotAmount = value) }

    fun updateCraPageUrl(value: String) = _uiState.update { it.copy(craPageUrl = value) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun saveProfile() {
        val state = _uiState.value
        val birth = state.validBirthYear ?: return
        if (state.invalidOpeningDate) return
        viewModelScope.launch {
            repository.saveProfile(
                Profile(birthYear = birth, fhsaOpeningDate = state.validOpeningDate),
            )
            _uiState.update { it.copy(message = uiText(R.string.message_profile_saved)) }
        }
    }

    fun addLimit() {
        val state = _uiState.value
        val year = state.validNewLimitYear ?: return
        val amount = state.validNewLimitAmount ?: return
        viewModelScope.launch {
            // Manual entry is confirmed, unlike a CRA reading.
            repository.saveLimit(AnnualLimit(account = Account.TFSA, year = year, amount = amount, confirmed = true))
            val limits = tfsaLimits()
            _uiState.update {
                it.copy(
                    limits = limits,
                    newLimitYear = "",
                    newLimitAmount = "",
                    message = uiText(R.string.message_limit_saved),
                )
            }
        }
    }

    fun saveSnapshot() {
        val state = _uiState.value
        val date = state.validSnapshotDate ?: return
        val amount = state.validSnapshotAmount ?: return
        viewModelScope.launch {
            repository.saveCraSnapshot(CraSnapshot(UNSAVED_ID, state.snapshotAccount, date, amount))
            _uiState.update {
                it.copy(
                    snapshots = craSnapshots(),
                    snapshotDate = "",
                    snapshotAmount = "",
                    message = uiText(R.string.message_cra_snapshot_saved),
                )
            }
        }
    }

    fun deleteSnapshot(snapshot: CraSnapshot) {
        viewModelScope.launch {
            repository.deleteCraSnapshot(snapshot.id)
            _uiState.update { it.copy(snapshots = craSnapshots(), message = uiText(R.string.message_cra_snapshot_deleted)) }
        }
    }

    /** Only an explicit request ignores the once-a-month limit. */
    fun checkCra(explicitRequest: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(checkInProgress = true, craError = null) }
            val result = repository.checkCraLimits(
                download = downloadPage,
                today = LocalDate.now(),
                ignoreFrequency = explicitRequest,
            )
            val limits = tfsaLimits()
            val settings = repository.settings()
            _uiState.update {
                it.copy(
                    limits = limits,
                    lastCraCheck = settings.lastCheckDate,
                    checkInProgress = false,
                    craError = (result as? CraCheckResult.Failed)?.let { failure -> uiText(failure.reason.textRes()) },
                    message = when {
                        result is CraCheckResult.Proposed ->
                            uiText(R.string.message_limit_proposed, result.limit.year)

                        explicitRequest && result is CraCheckResult.NotNeeded ->
                            uiText(R.string.message_cra_nothing_new)

                        else -> it.message
                    },
                )
            }
        }
    }

    fun confirmProposal(limit: AnnualLimit) {
        viewModelScope.launch {
            repository.saveLimit(limit.copy(confirmed = true))
            _uiState.update { it.copy(limits = tfsaLimits(), message = uiText(R.string.message_limit_confirmed, limit.year)) }
        }
    }

    fun rejectProposal(limit: AnnualLimit) {
        viewModelScope.launch {
            repository.deleteLimit(limit.account, limit.year)
            _uiState.update { it.copy(limits = tfsaLimits(), message = uiText(R.string.message_proposal_rejected, limit.year)) }
        }
    }

    /** A rejected address reverts to the last one that worked. */
    fun saveCraPageUrl() = changeAddress(_uiState.value.craPageUrl.trim())

    /** The default address is checked too: it may have changed. */
    fun restoreCraPageUrl() = changeAddress(DEFAULT_CRA_PAGE_URL)

    private fun changeAddress(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(checkInProgress = true) }
            val result = repository.changeCraPageUrl(input, downloadPage)
            val inPlace = repository.settings().craPageUrl
            _uiState.update {
                it.copy(
                    craPageUrl = inPlace,
                    checkInProgress = false,
                    message = when (result) {
                        CraAddressResult.Saved -> uiText(R.string.message_address_saved)
                        is CraAddressResult.Rejected -> uiText(R.string.message_address_rejected, uiText(result.reason.textRes()))
                    },
                )
            }
        }
    }

    /** The screen supplies the file access, keeping Android storage APIs out of the ViewModel. */
    fun exportData(write: suspend (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                write(repository.exportJson())
                uiText(R.string.message_data_exported)
            } catch (e: Exception) {
                uiText(R.string.message_export_failed)
            }
            _uiState.update { it.copy(message = message) }
        }
    }

    /** Replaces all content: the screen confirms first. */
    fun importData(read: suspend () -> String) {
        viewModelScope.launch {
            val message = try {
                repository.importJson(read())
                uiText(R.string.message_data_imported)
            } catch (e: InvalidImport) {
                uiText(R.string.message_import_rejected, uiText(e.reason.textRes()))
            } catch (e: Exception) {
                uiText(R.string.message_import_rejected, uiText(R.string.import_unreadable_file))
            }
            load(replaceInputs = true)
            _uiState.update { it.copy(message = message) }
        }
    }

    private suspend fun craSnapshots(): List<CraSnapshot> = repository.craSnapshots()
        .sortedWith(compareByDescending<CraSnapshot> { it.referenceDate }.thenByDescending { it.id })

    private suspend fun tfsaLimits(): List<AnnualLimit> = repository.limits().filter { it.account == Account.TFSA }.sortedBy { it.year }
}
