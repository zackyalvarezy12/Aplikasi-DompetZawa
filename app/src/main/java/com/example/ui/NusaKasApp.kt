package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.PayrollCalculator
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.AppThemePreset
import com.example.ui.theme.LocalActiveScreenGradient
import com.example.ui.theme.NusaKasTheme
import com.example.ui.theme.TimeOfDayPastelPhase

enum class NusaKasTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Beranda", Icons.Filled.Home, Icons.Outlined.Home),
    LEDGER("Catatan", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    WEALTH("Dompet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    GROUP_DEBT("Patungan", Icons.Filled.Groups, Icons.Outlined.Groups),
    ADVISOR("Laporan", Icons.Filled.Insights, Icons.Outlined.Insights)
}

@Composable
fun NusaKasApp(viewModel: NusaKasViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPinUnlocked by viewModel.isPinUnlocked.collectAsStateWithLifecycle()
    val hasEnteredSession by viewModel.hasEnteredSession.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activePreset = remember(state.settings.themePreset) {
        AppThemePreset.fromId(state.settings.themePreset)
    }

    var currentTab by rememberSaveable { mutableStateOf(NusaKasTab.DASHBOARD) }
    val stateHolder = rememberSaveableStateHolder()
    var showWorkspaceModal by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var showSyncSecurityModal by remember { mutableStateOf(false) }
    var showNewTransactionModal by remember { mutableStateOf(false) }
    var showAutoSalarySetupModal by remember { mutableStateOf(false) }
    var showPayrollPopupManual by remember { mutableStateOf(false) }
    var dismissedPayrollInSession by rememberSaveable { mutableStateOf(false) }
    var autoTimePreviewPhase by rememberSaveable { mutableStateOf<TimeOfDayPastelPhase?>(null) }

    val isPaydayAutoDue = remember(state.settings, state.canEdit) {
        state.canEdit && PayrollCalculator.isPaydayDueThisMonth(
            isEnabled = state.settings.isAutoSalaryEnabled,
            monthlyBaseSalary = state.settings.monthlyBaseSalary,
            paydayDay = state.settings.salaryPaydayDay,
            lastHandledCycleKey = state.settings.lastSalaryCycleHandled
        )
    }

    val isPinRequired = state.settings.isPinEnabled && state.settings.pinHash.isNotBlank()
    val isLockedByPin = isPinRequired && !isPinUnlocked
    val showLoginScreen = !hasEnteredSession || isLockedByPin

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    NusaKasTheme(
        preset = activePreset,
        autoTimeOverridePhase = autoTimePreviewPhase
    ) {
        if (!state.isReady) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            return@NusaKasTheme
        }

        if (showLoginScreen) {
            PinLockScreen(
                isPinRequired = isPinRequired,
                userName = state.activeMember?.name ?: state.currentUser?.name ?: "Sahabat",
                workspaceName = state.activeWorkspace?.name ?: "Keuangan Pribadi",
                onVerifyPin = viewModel::unlockWithPin,
                onEnterWithoutPin = viewModel::enterSession,
                onOpenThemePicker = { showThemeModal = true }
            )
            if (showThemeModal) {
                ThemePickerDialog(
                    currentPresetId = state.settings.themePreset,
                    autoTimePreviewPhase = autoTimePreviewPhase,
                    onSelectAutoTimePhase = { autoTimePreviewPhase = it },
                    onSelectPreset = viewModel::setThemePreset,
                    onDismiss = { showThemeModal = false }
                )
            }
            return@NusaKasTheme
        }

        BackHandler(enabled = currentTab != NusaKasTab.DASHBOARD) {
            currentTab = NusaKasTab.DASHBOARD
        }

        val activeScreenColors = LocalActiveScreenGradient.current
        val screenBgBrush = remember(activeScreenColors) {
            Brush.verticalGradient(activeScreenColors)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBgBrush)
        ) {
            val useRail = maxWidth >= 700.dp

            Scaffold(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    NusaKasTopBar(
                        state = state,
                        onOpenWorkspaceModal = { showWorkspaceModal = true },
                        onOpenThemeModal = { showThemeModal = true },
                        onOpenSyncSecurityModal = { showSyncSecurityModal = true },
                        onToggleStealth = viewModel::toggleStealthMode
                    )
                },
                floatingActionButton = {
                    if (state.canEdit && (currentTab == NusaKasTab.DASHBOARD || currentTab == NusaKasTab.LEDGER)) {
                        FloatingActionButton(
                            onClick = { showNewTransactionModal = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_transaction")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Catat Transaksi Baru")
                        }
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 4.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NusaKasTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail {
                            NusaKasTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentModifier = Modifier.widthIn(max = 960.dp)
                        stateHolder.SaveableStateProvider(currentTab.name) {
                            when (currentTab) {
                                NusaKasTab.DASHBOARD -> DashboardScreen(
                                    state = state,
                                    onQuickInputSubmit = viewModel::submitQuickInput,
                                    onOpenNewTransaction = { showNewTransactionModal = true },
                                    onOpenThemePicker = { showThemeModal = true },
                                    onDismissGuide = viewModel::dismissBeginnerGuide,
                                    onNavigateToLedger = { currentTab = NusaKasTab.LEDGER },
                                    onNavigateToWealth = { currentTab = NusaKasTab.WEALTH },
                                    onNavigateToGroup = { currentTab = NusaKasTab.GROUP_DEBT },
                                    onPayRecurring = viewModel::payRecurringNow,
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onOpenAutoSalarySetup = { showAutoSalarySetupModal = true },
                                    onOpenPayrollPopup = { showPayrollPopupManual = true },
                                    onNavigateToAdvisor = { currentTab = NusaKasTab.ADVISOR },
                                    modifier = contentModifier
                                )

                                NusaKasTab.LEDGER -> LedgerScreen(
                                    state = state,
                                    onQuickInputSubmit = viewModel::submitQuickInput,
                                    onOpenNewTransaction = { showNewTransactionModal = true },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onLoadMore = viewModel::loadMoreTransactions,
                                    modifier = contentModifier
                                )

                                NusaKasTab.WEALTH -> WealthBudgetScreen(
                                    state = state,
                                    onSaveAccount = viewModel::saveAccount,
                                    onReconcileAccount = viewModel::reconcileAccount,
                                    onSaveAsset = viewModel::saveAsset,
                                    onSaveBudget = viewModel::saveBudget,
                                    onSaveRecurring = viewModel::saveRecurringRule,
                                    onPayRecurringNow = viewModel::payRecurringNow,
                                    modifier = contentModifier
                                )

                                NusaKasTab.GROUP_DEBT -> GroupDebtScreen(
                                    state = state,
                                    onSaveDebtLoan = viewModel::saveDebtLoan,
                                    onPayDebtInstallment = viewModel::payDebtInstallment,
                                    onSaveGoal = viewModel::saveGoal,
                                    onContributeGoal = viewModel::contributeToGoal,
                                    onCreateSplitBill = viewModel::createEqualSplitBill,
                                    onSettleTransfer = viewModel::settleGroupTransfer,
                                    modifier = contentModifier
                                )

                                NusaKasTab.ADVISOR -> AdvisorReportsScreen(
                                    state = state,
                                    onOpenSyncSecurity = { showSyncSecurityModal = true },
                                    onNotify = viewModel::showMessage,
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showWorkspaceModal) {
            WorkspaceCollaborationDialog(
                state = state,
                onDismiss = { showWorkspaceModal = false },
                onSwitchWorkspace = viewModel::switchWorkspace,
                onCreateWorkspace = viewModel::createWorkspace,
                onJoinWorkspace = viewModel::joinWorkspace,
                onInviteMember = viewModel::inviteMember,
                onChangeRole = viewModel::changeMemberRole,
                onSwitchActiveMemberDemo = viewModel::switchActiveMemberRoleDemo,
                onNotify = viewModel::showMessage
            )
        }

        if (showThemeModal) {
            ThemePickerDialog(
                currentPresetId = state.settings.themePreset,
                autoTimePreviewPhase = autoTimePreviewPhase,
                onSelectAutoTimePhase = { autoTimePreviewPhase = it },
                onSelectPreset = viewModel::setThemePreset,
                onDismiss = { showThemeModal = false }
            )
        }

        if (showSyncSecurityModal) {
            CloudSyncAndSecurityDialog(
                state = state,
                onDismiss = { showSyncSecurityModal = false },
                onToggleOfflineSim = viewModel::toggleSimulatedOffline,
                onSaveCloudConfig = viewModel::saveCloudSettings,
                onTriggerSync = viewModel::triggerManualSync,
                onEnablePin = viewModel::enablePin,
                onDisablePin = viewModel::disablePin,
                onLockNow = viewModel::lockAppNow
            )
        }

        if (showNewTransactionModal) {
            TransactionEntryDialog(
                accounts = state.accounts,
                categories = state.categories,
                onDismiss = { showNewTransactionModal = false },
                onSave = viewModel::saveTransaction
            )
        }

        if (showAutoSalarySetupModal) {
            AutoSalarySetupDialog(
                settings = state.settings,
                accounts = state.accounts,
                stealthMode = state.settings.isStealthMode,
                onDismiss = { showAutoSalarySetupModal = false },
                onSave = { enabled, baseSalary, paydayDay, workingDays, targetAccId ->
                    viewModel.saveAutoSalaryConfig(enabled, baseSalary, paydayDay, workingDays, targetAccId)
                    showAutoSalarySetupModal = false
                },
                onSaveAndOpenPayrollNow = { enabled, baseSalary, paydayDay, workingDays, targetAccId ->
                    viewModel.saveAutoSalaryConfig(enabled, baseSalary, paydayDay, workingDays, targetAccId)
                    showAutoSalarySetupModal = false
                    showPayrollPopupManual = true
                }
            )
        }

        val showPayrollDialog = showPayrollPopupManual || (isPaydayAutoDue && !dismissedPayrollInSession && !showAutoSalarySetupModal)
        if (showPayrollDialog) {
            MonthlyPayrollPopupDialog(
                settings = state.settings,
                accounts = state.accounts,
                stealthMode = state.settings.isStealthMode,
                isAutoCyclePrompt = isPaydayAutoDue && !showPayrollPopupManual,
                onDismissSession = {
                    showPayrollPopupManual = false
                    dismissedPayrollInSession = true
                },
                onSkipCycle = { cycleKey ->
                    viewModel.skipSalaryCycle(cycleKey)
                    showPayrollPopupManual = false
                    dismissedPayrollInSession = true
                },
                onConfirmPayroll = { input, accId, cycleKey ->
                    viewModel.submitMonthlyPayroll(input, accId, cycleKey)
                    showPayrollPopupManual = false
                    dismissedPayrollInSession = true
                }
            )
        }
    }
}
