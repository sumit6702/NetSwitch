package com.sumit.netswitch.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sumit.netswitch.controller.NetworkController
import com.sumit.netswitch.data.CommandLogRepository
import com.sumit.netswitch.data.PreferencesRepository
import com.sumit.netswitch.model.LogEntry
import com.sumit.netswitch.model.NetworkMode
import com.sumit.netswitch.model.ShizukuStatus
import com.sumit.netswitch.shizuku.ShizukuManager
import com.sumit.netswitch.ui.model.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus(
        isInstalled = false,
        isRunning = false,
        isPermissionGranted = false,
        isServiceConnected = false
    ),
    val currentNetworkMode: NetworkMode = NetworkMode.UNKNOWN,
    val selectedSimSlot: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val testConnectionOutput: String? = null,
    val custom5gMask: String = PreferencesRepository.DEFAULT_5G_MASK,
    val custom4gMask: String = PreferencesRepository.DEFAULT_4G_MASK,
    val savedOriginalMask: String? = null,
    val isOnboardingCompleted: Boolean = false,
    val showOnboardingDialog: Boolean = false,
    val showLogDialog: Boolean = false,
    val activeError: AppError? = null,
    val logs: List<LogEntry> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesRepository(application)
    private val _uiState = MutableStateFlow(
        UiState(
            selectedSimSlot = prefs.selectedSimSlot,
            custom5gMask = prefs.custom5gMask,
            custom4gMask = prefs.custom4gMask,
            savedOriginalMask = prefs.savedOriginalMask,
            isOnboardingCompleted = prefs.isOnboardingCompleted,
            showOnboardingDialog = !prefs.isOnboardingCompleted
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        ShizukuManager.init(application)

        // Observe Shizuku status updates
        viewModelScope.launch {
            ShizukuManager.statusFlow.collect { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
                if (status.isReady) {
                    refreshNetworkState()
                } else {
                    _uiState.update { it.copy(currentNetworkMode = NetworkMode.UNKNOWN) }
                }
            }
        }

        // Observe command logs
        viewModelScope.launch {
            CommandLogRepository.logsFlow.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }

        refreshAll()
    }

    fun refreshAll() {
        val app = getApplication<Application>()
        ShizukuManager.updateStatus(app)
        val status = ShizukuManager.statusFlow.value

        if (!status.isInstalled) {
            _uiState.update { it.copy(activeError = AppError.shizukuNotInstalled()) }
            return
        } else if (!status.isRunning) {
            _uiState.update { it.copy(activeError = AppError.shizukuNotRunning()) }
            return
        } else if (!status.isPermissionGranted) {
            _uiState.update { it.copy(activeError = AppError.permissionNotGranted()) }
            return
        }

        refreshNetworkState()
    }

    fun refreshNetworkState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, statusMessage = null) }
            val app = getApplication<Application>()
            val slot = _uiState.value.selectedSimSlot
            val result = NetworkController.getState(app, slot)

            _uiState.update { state ->
                state.copy(
                    isRefreshing = false,
                    currentNetworkMode = result.mode,
                    savedOriginalMask = prefs.savedOriginalMask
                )
            }

            if (!result.isSuccess) {
                val err = result.errorMessage.orEmpty()
                if (err.contains("slot", ignoreCase = true) || err.contains("phone process died", ignoreCase = true)) {
                    _uiState.update { it.copy(activeError = AppError.wrongOrEmptySimSlot(slot) { newSlot -> setSimSlot(newSlot) }) }
                } else if (result.rawOutput.isNotBlank() && result.mode == NetworkMode.UNKNOWN) {
                    _uiState.update { it.copy(activeError = AppError.outputCannotBeParsed(result.rawOutput)) }
                }
            }
        }
    }

    fun toggleNetwork() {
        val currentState = _uiState.value
        val app = getApplication<Application>()

        if (!currentState.shizukuStatus.isInstalled) {
            _uiState.update { it.copy(activeError = AppError.shizukuNotInstalled()) }
            return
        }
        if (!currentState.shizukuStatus.isRunning) {
            _uiState.update { it.copy(activeError = AppError.shizukuNotRunning()) }
            return
        }
        if (!currentState.shizukuStatus.isPermissionGranted) {
            _uiState.update { it.copy(activeError = AppError.permissionNotGranted()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Switching network mode...") }
            val slot = currentState.selectedSimSlot
            val toggleResult = NetworkController.toggle(
                context = app,
                slot = slot,
                custom5gMask = currentState.custom5gMask,
                custom4gMask = currentState.custom4gMask
            )

            _uiState.update {
                it.copy(
                    isLoading = false,
                    currentNetworkMode = toggleResult.newMode,
                    statusMessage = toggleResult.message,
                    savedOriginalMask = prefs.savedOriginalMask
                )
            }

            if (!toggleResult.success) {
                if (toggleResult.stateDidNotUpdateWarning) {
                    _uiState.update {
                        it.copy(
                            activeError = AppError.stateDidNotUpdateAfter5Seconds(
                                currentMode = toggleResult.previousMode.displayName,
                                onRetry = { toggleNetwork() }
                            )
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            activeError = AppError.commandRefused(
                                reason = toggleResult.message,
                                onRetry = { toggleNetwork() }
                            )
                        )
                    }
                }
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, testConnectionOutput = null) }
            val app = getApplication<Application>()
            val slot = _uiState.value.selectedSimSlot
            val result = NetworkController.getState(app, slot)

            val displayText = buildString {
                append("Slot: ").append(slot).append("\n")
                append("Success: ").append(result.isSuccess).append("\n")
                append("Detected Mode: ").append(result.mode.displayName).append("\n")
                if (result.errorMessage != null) {
                    append("Error: ").append(result.errorMessage).append("\n")
                }
                append("\n--- Raw Modem Output ---\n")
                append(result.rawOutput.ifBlank { "(empty output)" })
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    testConnectionOutput = displayText,
                    currentNetworkMode = result.mode
                )
            }
        }
    }

    fun clearTestConnectionOutput() {
        _uiState.update { it.copy(testConnectionOutput = null) }
    }

    fun setSimSlot(slot: Int) {
        prefs.selectedSimSlot = slot
        _uiState.update { it.copy(selectedSimSlot = slot) }
        refreshNetworkState()
    }

    fun saveCustomMasks(mask5g: String, mask4g: String) {
        val clean5g = mask5g.trim()
        val clean4g = mask4g.trim()
        prefs.custom5gMask = clean5g
        prefs.custom4gMask = clean4g
        _uiState.update {
            it.copy(
                custom5gMask = clean5g,
                custom4gMask = clean4g,
                statusMessage = "Custom bitmasks saved."
            )
        }
    }

    fun resetMasksToDefault() {
        prefs.resetMasksToDefault()
        _uiState.update {
            it.copy(
                custom5gMask = PreferencesRepository.DEFAULT_5G_MASK,
                custom4gMask = PreferencesRepository.DEFAULT_4G_MASK,
                statusMessage = "Bitmasks reset to Motorola edge 70 defaults."
            )
        }
    }

    fun restoreOriginalMask() {
        val original = prefs.savedOriginalMask
        val app = getApplication<Application>()
        if (original.isNullOrBlank()) {
            Toast.makeText(app, "No original mask recorded yet.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Restoring original mask: $original") }
            val res = NetworkController.restoreMask(app, _uiState.value.selectedSimSlot, original)
            _uiState.update { it.copy(isLoading = false) }

            if (res.isSuccess) {
                Toast.makeText(app, "Original mask restored successfully.", Toast.LENGTH_LONG).show()
                refreshNetworkState()
            } else {
                _uiState.update {
                    it.copy(
                        activeError = AppError.commandRefused("Failed to restore: ${res.stderr}") {
                            restoreOriginalMask()
                        }
                    )
                }
            }
        }
    }

    fun requestPermission() {
        ShizukuManager.requestPermission()
    }

    fun dismissError() {
        _uiState.update { it.copy(activeError = null) }
    }

    fun showOnboarding(show: Boolean) {
        _uiState.update { it.copy(showOnboardingDialog = show) }
    }

    fun completeOnboarding() {
        prefs.isOnboardingCompleted = true
        _uiState.update { it.copy(isOnboardingCompleted = true, showOnboardingDialog = false) }
    }

    fun showLogDialog(show: Boolean) {
        _uiState.update { it.copy(showLogDialog = show) }
    }

    fun copyLogToClipboard(context: Context) {
        val logText = CommandLogRepository.getFormattedLogsString()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NetSwitch Log", logText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Log copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    fun copyTextToClipboard(context: Context, text: String, label: String = "NetSwitch") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
