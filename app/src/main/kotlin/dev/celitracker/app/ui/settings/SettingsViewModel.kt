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
import dev.celitracker.engine.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [downloadPage] est injecte plutot qu'appele en dur: la regle de lecture
 * de l'ARC se teste ainsi sans reseau.
 */
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

    /**
     * [replaceInputs] a false, le chargement ne remplit que les champs
     * encore vides: la lecture de la database est asynchrone et ecraserait sinon ce
     * que l'utilisateur vient de taper. Un import, lui, remplace whole le
     * content, donc les champs affiches aussi.
     */
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
                    urlPageArc = it.urlPageArc.ifBlank { settings.urlPageArc },
                    lastCraCheck = settings.lastCheckDate,
                )
            }
        }
    }

    fun updateBirthYear(value: String) = _uiState.update { it.copy(birthYear = value) }
    fun updateFhsaOpeningDate(value: String) = _uiState.update { it.copy(fhsaOpeningDate = value) }
    fun updateNewLimitYear(value: String) = _uiState.update { it.copy(newLimitYear = value) }
    fun updateNewLimitAmount(value: String) = _uiState.update { it.copy(newLimitAmount = value) }

    fun updateCraPageUrl(value: String) = _uiState.update { it.copy(urlPageArc = value) }

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
            // confirmed = true : input manuelle directe, labelStep une lecture ARC en
            // attente de validation.
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

    /**
     * Va read le limit annonce par l'ARC. Une demande explicite ignore la
     * limite d'une lecture par month; l'opening de l'ecran, non.
     */
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

    /**
     * L'address est essayee before d'etre enregistree. Rejected, le champ revient
     * a l'address en place, la latest qui a fonctionne.
     */
    fun saveCraPageUrl() = changeAddress(_uiState.value.urlPageArc.trim())

    /** L'address d'origine passe par le meme essai: elle aussi peut avoir change. */
    fun restoreCraPageUrl() = changeAddress(DEFAULT_CRA_PAGE_URL)

    private fun changeAddress(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(checkInProgress = true) }
            val result = repository.changeCraPageUrl(input, downloadPage)
            val inPlace = repository.settings().urlPageArc
            _uiState.update {
                it.copy(
                    urlPageArc = inPlace,
                    checkInProgress = false,
                    message = when (result) {
                        CraAddressResult.Saved -> uiText(R.string.message_address_saved)
                        is CraAddressResult.Rejected -> uiText(R.string.message_address_rejected, uiText(result.reason.textRes()))
                    },
                )
            }
        }
    }

    /**
     * L'ecran fournit l'ecriture et la lecture du file: les API Android de
     * stockage restent hors du ViewModel.
     */
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

    /** L'import remplace whole le content; l'ecran confirmed before d'appeler. */
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

    private suspend fun tfsaLimits(): List<AnnualLimit> = repository.limits().filter { it.account == Account.TFSA }.sortedBy { it.year }
}
