package com.example.corepostemergencybutton

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.corepostemergencybutton.data.CorePostApi
import com.example.corepostemergencybutton.data.CorePostApiException
import com.example.corepostemergencybutton.data.CorePostConfig
import com.example.corepostemergencybutton.data.DeviceState
import com.example.corepostemergencybutton.data.MobileStatus
import com.example.corepostemergencybutton.data.PrimaryActionKind
import com.example.corepostemergencybutton.data.SecureConfigStore
import com.example.corepostemergencybutton.data.toDashboardModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

data class MainUiState(
    val baseUrlInput: String = "",
    val emergencyIdInput: String = "",
    val panicSecretInput: String = "",
    val savedConfig: CorePostConfig? = null,
    val isEditingSettings: Boolean = true,
    val mobileStatus: MobileStatus? = null,
    val isSavingSettings: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPerformingAction: Boolean = false,
    val pendingLockDeadlineMs: Long? = null,
    val lastSuccessfulSyncMs: Long? = null,
    val inlineError: String? = null,
    val snackbarMessage: String? = null,
) {
    val hasSavedConfig: Boolean
        get() = savedConfig?.isComplete == true
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = SecureConfigStore(application)
    private val api = CorePostApi()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    fun onBaseUrlChanged(value: String) {
        _uiState.update { it.copy(baseUrlInput = value, inlineError = null) }
    }

    fun onEmergencyIdChanged(value: String) {
        _uiState.update { it.copy(emergencyIdInput = value, inlineError = null) }
    }

    fun onPanicSecretChanged(value: String) {
        _uiState.update { it.copy(panicSecretInput = value, inlineError = null) }
    }

    fun openSettings() {
        _uiState.update { it.copy(isEditingSettings = true, inlineError = null) }
    }

    fun cancelSettings() {
        _uiState.update { state ->
            if (state.hasSavedConfig) {
                state.copy(isEditingSettings = false, inlineError = null)
            } else {
                state
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearSettings() {
        viewModelScope.launch {
            runCatching { configStore.clear() }
                .onSuccess {
                    _uiState.value = MainUiState(
                        snackbarMessage = "Сохраненные параметры удалены",
                    )
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            inlineError = "Не удалось удалить настройки: ${error.localizedMessage ?: "неизвестная ошибка"}",
                        )
                    }
                }
        }
    }

    fun saveSettings() {
        val normalized = normalizeConfig(
            baseUrl = _uiState.value.baseUrlInput,
            emergencyId = _uiState.value.emergencyIdInput,
            panicSecret = _uiState.value.panicSecretInput,
        ) ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingSettings = true,
                    inlineError = null,
                    snackbarMessage = null,
                )
            }
            runCatching {
                configStore.save(normalized)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        savedConfig = normalized,
                        baseUrlInput = normalized.normalizedBaseUrl,
                        emergencyIdInput = normalized.emergencyId,
                        panicSecretInput = normalized.panicSecret,
                        isSavingSettings = false,
                        isEditingSettings = false,
                        snackbarMessage = "Настройки сохранены в защищенном хранилище",
                    )
                }
                refreshStatus(showSpinner = true)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSavingSettings = false,
                        inlineError = "Не удалось сохранить настройки: ${error.localizedMessage ?: "неизвестная ошибка"}",
                    )
                }
            }
        }
    }

    fun refreshStatus(showSpinner: Boolean = true) {
        val config = _uiState.value.savedConfig ?: return
        viewModelScope.launch {
            if (showSpinner) {
                _uiState.update { it.copy(isRefreshing = true, inlineError = null) }
            }
            runCatching {
                api.checkStatus(config)
            }.onSuccess { status ->
                _uiState.update { state ->
                    state.copy(
                        mobileStatus = status,
                        isRefreshing = false,
                        inlineError = null,
                        lastSuccessfulSyncMs = System.currentTimeMillis(),
                        pendingLockDeadlineMs = status.defaultPendingDeadline(existingDeadline = state.pendingLockDeadlineMs),
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        inlineError = error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun triggerPrimaryAction() {
        val status = _uiState.value.mobileStatus ?: return
        val isAwaitingSecondTap = _uiState.value.pendingLockDeadlineMs?.let { it > System.currentTimeMillis() } == true
        when (status.toDashboardModel(isAwaitingSecondTap).actionKind) {
            PrimaryActionKind.LOCK,
            PrimaryActionKind.CONFIRM_LOCK,
            -> performLock()

            PrimaryActionKind.UNLOCK -> performUnlock()
            PrimaryActionKind.NONE -> Unit
        }
    }

    fun onPendingWindowExpired() {
        val shouldExpire = _uiState.value.pendingLockDeadlineMs != null
        if (!shouldExpire) {
            return
        }
        _uiState.update { it.copy(pendingLockDeadlineMs = null) }
        refreshStatus(showSpinner = false)
    }

    private fun loadInitialState() {
        val loadedConfig = runCatching { configStore.load() }
            .getOrElse {
                _uiState.update {
                    it.copy(
                        inlineError = "Защищенное хранилище недоступно: ${it.inlineError ?: "проверьте Keystore"}",
                    )
                }
                CorePostConfig()
            }

        _uiState.update {
            it.copy(
                baseUrlInput = loadedConfig.normalizedBaseUrl,
                emergencyIdInput = loadedConfig.emergencyId,
                panicSecretInput = loadedConfig.panicSecret,
                savedConfig = loadedConfig.takeIf(CorePostConfig::isComplete),
                isEditingSettings = !loadedConfig.isComplete,
            )
        }

        if (loadedConfig.isComplete) {
            refreshStatus(showSpinner = true)
        }
    }

    private fun performLock() {
        val config = _uiState.value.savedConfig ?: return
        val currentApprovalTime = _uiState.value.mobileStatus?.lockApprovalTimeSecond ?: 30
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingAction = true, inlineError = null) }
            runCatching { api.lock(config) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            isPerformingAction = false,
                            snackbarMessage = result.detail,
                            lastSuccessfulSyncMs = System.currentTimeMillis(),
                            pendingLockDeadlineMs = if (result.currentState == DeviceState.PENDING_LOCK) {
                                System.currentTimeMillis() + currentApprovalTime * 1000L
                            } else {
                                null
                            },
                            mobileStatus = state.mobileStatus?.let { currentStatus ->
                                currentStatus.copy(
                                    currentState = result.currentState ?: currentStatus.currentState,
                                )
                            },
                        )
                    }
                    refreshStatus(showSpinner = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            inlineError = error.toUserMessage(),
                        )
                    }
                }
        }
    }

    private fun performUnlock() {
        val config = _uiState.value.savedConfig ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformingAction = true, inlineError = null) }
            runCatching { api.unlock(config) }
                .onSuccess { result ->
                    _uiState.update { state ->
                        state.copy(
                            isPerformingAction = false,
                            snackbarMessage = result.detail,
                            lastSuccessfulSyncMs = System.currentTimeMillis(),
                            pendingLockDeadlineMs = null,
                            mobileStatus = state.mobileStatus?.let { currentStatus ->
                                currentStatus.copy(
                                    currentState = result.currentState ?: currentStatus.currentState,
                                )
                            },
                        )
                    }
                    refreshStatus(showSpinner = false)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isPerformingAction = false,
                            inlineError = error.toUserMessage(),
                        )
                    }
                }
        }
    }

    private fun normalizeConfig(
        baseUrl: String,
        emergencyId: String,
        panicSecret: String,
    ): CorePostConfig? {
        val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
        val trimmedEmergencyId = emergencyId.trim()
        val trimmedPanicSecret = panicSecret.trim()

        if (trimmedBaseUrl.isBlank() || trimmedEmergencyId.isBlank() || trimmedPanicSecret.isBlank()) {
            _uiState.update {
                it.copy(inlineError = "Заполните base URL, emergencyId и panicSecret")
            }
            return null
        }

        val uri = runCatching { URI(trimmedBaseUrl) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            _uiState.update {
                it.copy(inlineError = "Base URL должен быть полным HTTP(S)-адресом со схемой и хостом")
            }
            return null
        }

        return CorePostConfig(
            baseUrl = trimmedBaseUrl,
            emergencyId = trimmedEmergencyId,
            panicSecret = trimmedPanicSecret,
        )
    }
}

private fun Throwable.toUserMessage(): String = when (this) {
    is CorePostApiException -> message
    else -> localizedMessage ?: "Неизвестная ошибка"
}

private fun MobileStatus.defaultPendingDeadline(existingDeadline: Long?): Long? {
    return if (currentState == DeviceState.PENDING_LOCK) {
        val now = System.currentTimeMillis()
        when {
            existingDeadline != null && existingDeadline > now -> existingDeadline
            else -> now + lockApprovalTimeSecond * 1000L
        }
    } else {
        null
    }
}
