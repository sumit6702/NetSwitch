package com.sumit.netswitch.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sumit.netswitch.ui.theme.NetSwitchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetSwitchTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "NetSwitch",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.showOnboarding(true) }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Setup Guide")
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live System Status Card
            StatusCard(
                shizukuStatus = uiState.shizukuStatus,
                currentMode = uiState.currentNetworkMode,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshAll() }
            )

            // 2. Big Action Button (Switch 4G / Switch 5G)
            BigSwitchButton(
                currentMode = uiState.currentNetworkMode,
                isShizukuReady = uiState.shizukuStatus.isReady,
                isLoading = uiState.isLoading,
                onToggle = { viewModel.toggleNetwork() }
            )

            // 3. SIM Slot Selector
            SimSlotSelector(
                selectedSlot = uiState.selectedSimSlot,
                onSelectSlot = { slot -> viewModel.setSimSlot(slot) }
            )

            // 4. Quick Settings Tile Guide
            TileGuideCard()

            // 5. Diagnostics & Safety Actions
            ActionsCard(
                onTestConnection = { viewModel.testConnection() },
                onRestoreOriginal = { viewModel.restoreOriginalMask() },
                onShowLogs = { viewModel.showLogDialog(true) },
                hasSavedOriginal = !uiState.savedOriginalMask.isNullOrBlank(),
                isLoading = uiState.isLoading
            )

            // 6. Advanced Bitmask Customization
            AdvancedMaskSection(
                custom5gMask = uiState.custom5gMask,
                custom4gMask = uiState.custom4gMask,
                onSaveMasks = { m5g, m4g -> viewModel.saveCustomMasks(m5g, m4g) },
                onResetDefaults = { viewModel.resetMasksToDefault() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Dialogs
    if (uiState.showOnboardingDialog) {
        OnboardingDialog(
            shizukuStatus = uiState.shizukuStatus,
            onComplete = { viewModel.completeOnboarding() },
            onRequestPermission = { viewModel.requestPermission() }
        )
    }

    uiState.activeError?.let { error ->
        ErrorDialog(
            error = error,
            onDismiss = { viewModel.dismissError() },
            onCopyLog = { viewModel.copyLogToClipboard(context) },
            onCopyText = { text -> viewModel.copyTextToClipboard(context, text) }
        )
    }

    if (uiState.showLogDialog) {
        LogDialog(
            logs = uiState.logs,
            onDismiss = { viewModel.showLogDialog(false) },
            onCopyLog = { viewModel.copyLogToClipboard(context) }
        )
    }

    uiState.testConnectionOutput?.let { testOutput ->
        TestOutputDialog(
            output = testOutput,
            onDismiss = { viewModel.clearTestConnectionOutput() },
            onCopy = { viewModel.copyTextToClipboard(context, testOutput, "Connection Test") }
        )
    }
}
