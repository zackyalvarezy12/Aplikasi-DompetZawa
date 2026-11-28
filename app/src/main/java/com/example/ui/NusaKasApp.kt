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
import com.example.data.local.TransactionEntity
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
    CALENDAR("Kalender", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    WEALTH("Dompet", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    GROUP_DEBT("Patungan", Icons.Filled.Groups, Icons.Outlined.Groups),
    ADVISOR("Laporan", Icons.Filled.Insights, Icons.Outlined.Insights)
}

@Composable
fun NusaKasApp(
    viewModel: NusaKasViewModel = viewModel(),
    deepLinkTab: String? = null,
    deepLinkSubTab: Int? = null,
    deepLinkNonce: Int = 0
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isPinUnlocked by viewModel.isPinUnlocked.collectAsStateWithLifecycle()
    val hasEnteredSession by viewModel.hasEnteredSession.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val activePreset = remember(state.settings.themePreset) {
        AppThemePreset.fromId(state.settings.themePreset)
    }

    var currentTab by rememberSaveable { mutableStateOf(NusaKasTab.DASHBOARD) }
    var wealthRequestedSubTab by rememberSaveable { mutableStateOf<Int?>(null) }
    var wealthSubTabNonce by rememberSaveable { mutableIntStateOf(0) }
    val stateHolder = rememberSaveableStateHolder()
    var showWorkspaceModal by remember { mutableStateOf(false) }
    var showThemeModal by remember { mutableStateOf(false) }
    var showProfileSecurityModal by remember { mutableStateOf(false) }
    var showNewTransactionModal by remember { mutableStateOf(false) }
    var prefillTransactionDateMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showAutoSalarySetupModal by remember { mutableStateOf(false) }
    var showPayrollPopupManual by remember { mutableStateOf(false) }
    var showZawaAiModal by rememberSaveable { mutableStateOf(false) }
    var dismissedPayrollInSession by rememberSaveable { mutableStateOf(false) }
    var autoTimePreviewPhase by rememberSaveable { mutableStateOf<TimeOfDayPastelPhase?>(null) }

    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val aiAnalysisState by viewModel.aiAnalysisState.collectAsStateWithLifecycle()
    val aiStreamingAnalysis by viewModel.aiStreamingAnalysis.collectAsStateWithLifecycle()
    val aiNavigationRequest by viewModel.aiNavigationRequest.collectAsStateWithLifecycle()

    LaunchedEffect(aiNavigationRequest) {
        aiNavigationRequest?.let { (tabKey, _) ->
            currentTab = when (tabKey) {
                "LEDGER" -> NusaKasTab.LEDGER
                "WEALTH" -> NusaKasTab.WEALTH
                "GROUP_DEBT" -> NusaKasTab.GROUP_DEBT
                "ADVISOR" -> NusaKasTab.ADVISOR
                else -> NusaKasTab.DASHBOARD
            }
            showZawaAiModal = false
            viewModel.consumeAiNavigationRequest()
        }
    }

    LaunchedEffect(deepLinkNonce) {
        if (deepLinkNonce > 0 && deepLinkTab == "WEALTH") {
            currentTab = NusaKasTab.WEALTH
            wealthRequestedSubTab = deepLinkSubTab
            wealthSubTabNonce++
        }
    }

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

        val needsOnboarding = !state.settings.hasCompletedOnboarding || state.currentUser == null || state.workspaces.isEmpty()

        if (needsOnboarding) {
            OnboardingScreen(
                tutorialOnlyMode = false,
                initialName = state.currentUser?.name ?: state.authUserDisplayName,
                isGoogleLinked = state.isGoogleLinked,
                googleEmail = state.authUserEmail,
                isAuthBusy = state.isAuthBusy,
                onSignInWithGoogle = { ctx, cb -> viewModel.signInWithGoogle(ctx, cb) },
                onRegisterWithEmailPassword = { ctx, email, pass, name, cb -> viewModel.registerWithEmailPassword(ctx, email, pass, name, cb) },
                onSignInWithEmailPassword = { ctx, email, pass, cb -> viewModel.signInWithEmailPassword(ctx, email, pass, cb) },
                onCompleteOnboarding = { displayName, colorHex, pin6, bio, photoUri ->
                    viewModel.completeOnboarding(
                        displayName = displayName,
                        avatarColorHex = colorHex,
                        pin6Digit = pin6,
                        enableBiometric = bio,
                        profilePhotoUri = photoUri,
                        markTutorialDone = false
                    )
                },
                onCompleteTutorialOnly = viewModel::markTutorialCompleted
            )
            return@NusaKasTheme
        }

        if (showLoginScreen) {
            PinLockScreen(
                isPinRequired = isPinRequired,
                isBiometricEnabled = state.settings.isBiometricEnabled,
                userName = state.activeMember?.name ?: state.currentUser?.name ?: "Sahabat",
                workspaceName = state.activeWorkspace?.name ?: "Keuangan Pribadi",
                profilePhotoPath = state.settings.profilePhotoPath,
                avatarColorHex = state.currentUser?.avatarColorHex ?: 0xFF065F46L,
                onVerifyPin = viewModel::unlockWithPin,
                onBiometricSuccess = viewModel::unlockWithBiometric,
                onEnterWithoutPin = viewModel::enterSession,
                onOpenThemePicker = { showThemeModal = true },
                onSwitchOrLogoutAccount = if (!isPinRequired) viewModel::logoutAndSwitchAccount else null
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
                        onOpenSyncSecurityModal = { showProfileSecurityModal = true },
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
                                    onEditTransaction = { tx -> editingTransaction = tx },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onOpenAutoSalarySetup = { showAutoSalarySetupModal = true },
                                    onOpenPayrollPopup = { showPayrollPopupManual = true },
                                    onNavigateToAdvisor = { currentTab = NusaKasTab.ADVISOR },
                                    onOpenZawaAi = { showZawaAiModal = true },
                                    modifier = contentModifier
                                )

                                NusaKasTab.LEDGER -> LedgerScreen(
                                    state = state,
                                    onQuickInputSubmit = viewModel::submitQuickInput,
                                    onOpenNewTransaction = { showNewTransactionModal = true },
                                    onEditTransaction = { tx -> editingTransaction = tx },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onLoadMore = viewModel::loadMoreTransactions,
                                    modifier = contentModifier
                                )

                                NusaKasTab.CALENDAR -> CalendarScreen(
                                    accounts = state.accounts,
                                    categories = state.categories,
                                    transactions = state.transactions,
                                    recurringRules = state.recurringRules,
                                    userSettings = state.settings,
                                    categoryMap = state.categoryMap,
                                    accountMap = state.accountMap,
                                    splitItemsByTxId = state.splitItemsByTxId,
                                    canEdit = state.canEdit,
                                    onOpenAddTransactionForDate = { dateMillis ->
                                        prefillTransactionDateMillis = dateMillis
                                        showNewTransactionModal = true
                                    },
                                    onEditTransaction = { tx -> editingTransaction = tx },
                                    onDeleteTransaction = viewModel::deleteTransaction,
                                    onPayRecurring = viewModel::payRecurringNow
                                )

                                NusaKasTab.WEALTH -> WealthBudgetScreen(
                                    state = state,
                                    requestedSubTab = wealthRequestedSubTab,
                                    requestedSubTabNonce = wealthSubTabNonce,
                                    onSaveAccount = viewModel::saveAccount,
                                    onReconcileAccount = viewModel::reconcileAccount,
                                    onDeleteAccount = viewModel::deleteAccount,
                                    onSaveAsset = viewModel::saveAsset,
                                    onDeleteAsset = viewModel::deleteAsset,
                                    onSaveBudget = { existingId, catId, lim, roll, rollEn ->
                                        viewModel.saveBudget(
                                            existingId = existingId,
                                            categoryId = catId,
                                            limitAmount = lim,
                                            rolloverAmount = roll,
                                            rolloverEnabled = rollEn
                                        )
                                    },
                                    onDeleteBudget = viewModel::deleteBudget,
                                    onSaveRecurring = { existingId, title, amt, accId, catId, freq, dueMillis, reminderEn ->
                                        viewModel.saveRecurringRule(
                                            existingId = existingId,
                                            title = title,
                                            amount = amt,
                                            accountId = accId,
                                            categoryId = catId,
                                            frequency = freq,
                                            nextDueDateMillis = dueMillis,
                                            reminderEnabled = reminderEn
                                        )
                                    },
                                    onDeleteRecurring = viewModel::deleteRecurringRule,
                                    onPayRecurringNow = viewModel::payRecurringNow,
                                    modifier = contentModifier
                                )

                                NusaKasTab.GROUP_DEBT -> GroupDebtScreen(
                                    state = state,
                                    onSaveDebtLoan = { existingId, kind, title, party, principal, remaining, apr, minPay ->
                                        viewModel.saveDebtLoan(
                                            existingId = existingId,
                                            kind = kind,
                                            title = title,
                                            counterparty = party,
                                            principal = principal,
                                            remainingAmount = remaining,
                                            interestRateAnnual = apr,
                                            minMonthlyPayment = minPay
                                        )
                                    },
                                    onDeleteDebtLoan = viewModel::deleteDebtLoan,
                                    onPayDebtInstallment = viewModel::payDebtInstallment,
                                    onSaveGoal = { existingId, title, target, current, monthly, monthsAhead ->
                                        viewModel.saveGoal(
                                            existingId = existingId,
                                            title = title,
                                            targetAmount = target,
                                            currentAmount = current,
                                            monthlyContribution = monthly,
                                            monthsAhead = monthsAhead
                                        )
                                    },
                                    onDeleteGoal = viewModel::deleteGoal,
                                    onContributeGoal = viewModel::contributeToGoal,
                                    onCreateSplitBill = viewModel::createEqualSplitBill,
                                    onDeleteSplitBill = viewModel::deleteSplitBill,
                                    onSettleTransfer = viewModel::settleGroupTransfer,
                                    onOpenWorkspaceInvite = { showWorkspaceModal = true },
                                    modifier = contentModifier
                                )

                                NusaKasTab.ADVISOR -> AdvisorReportsScreen(
                                    state = state,
                                    onOpenSyncSecurity = { showProfileSecurityModal = true },
                                    onNotify = viewModel::showMessage,
                                    aiAnalysisState = aiAnalysisState,
                                    aiStreamingText = aiStreamingAnalysis,
                                    onRunDeepAiAnalysis = { viewModel.runDeepAiAnalysisStream() },
                                    onOpenZawaAiChat = { showZawaAiModal = true },
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

        if (showProfileSecurityModal) {
            ProfileAndSecurityDialog(
                state = state,
                onDismiss = { showProfileSecurityModal = false },
                onUpdateProfile = viewModel::updateUserProfile,
                onUpdateProfilePhoto = viewModel::updateProfilePhoto,
                onRemoveProfilePhoto = viewModel::removeProfilePhoto,
                onSignInWithGoogle = { ctx -> viewModel.signInWithGoogle(ctx) },
                onSignOutGoogle = viewModel::signOutGoogle,
                onToggleOfflineSim = viewModel::toggleSimulatedOffline,
                onTriggerSync = viewModel::triggerManualSync,
                onEnablePin = viewModel::enablePin,
                onDisablePin = viewModel::disablePin,
                onSetBiometricEnabled = viewModel::setBiometricEnabled,
                onLockNow = viewModel::lockAppNow,
                onSetBillReminderEnabled = viewModel::setBillReminderEnabled,
                onTestBillNotification = viewModel::testBillReminderNotificationNow,
                onReplayTutorial = viewModel::replayTutorial,
                onResetCleanAccount = viewModel::resetCleanAccount,
                onLogoutKeepData = viewModel::logoutSessionKeepData,
                onLogoutSwitchAccount = viewModel::logoutAndSwitchAccount
            )
        }

        val activeTxForModal = editingTransaction
        if (showNewTransactionModal || activeTxForModal != null) {
            TransactionEntryDialog(
                accounts = state.accounts,
                categories = state.categories,
                existingTx = activeTxForModal,
                existingSplitItems = activeTxForModal?.let { state.splitItemsByTxId[it.id] }.orEmpty(),
                initialDateMillis = prefillTransactionDateMillis,
                onDismiss = {
                    showNewTransactionModal = false
                    prefillTransactionDateMillis = null
                    editingTransaction = null
                },
                onSave = viewModel::saveTransaction,
                onDelete = viewModel::deleteTransaction,
                onSaveCategory = { name, type ->
                    viewModel.saveCategory(
                        existingId = null,
                        name = name,
                        type = type,
                        iconKey = "category",
                        colorHex = 0xFF059669L
                    )
                }
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

        if (showZawaAiModal) {
            ZawaAiAssistantDialog(
                uiState = aiUiState,
                messages = aiMessages,
                onSendMessage = viewModel::sendMessageToZawaAi,
                onRetryLastMessage = viewModel::retryLastZawaAiMessage,
                onClearChat = viewModel::clearAiChat,
                onDismissError = viewModel::dismissAiError,
                onDismiss = { showZawaAiModal = false }
            )
        }

        if (!state.settings.hasCompletedTutorial) {
            AppTutorialOverlay(
                userName = state.currentUser?.name ?: state.activeMember?.name ?: "",
                onSelectTabKey = { tabKey ->
                    currentTab = when (tabKey) {
                        "LEDGER" -> NusaKasTab.LEDGER
                        "WEALTH" -> NusaKasTab.WEALTH
                        "GROUP_DEBT" -> NusaKasTab.GROUP_DEBT
                        "ADVISOR" -> NusaKasTab.ADVISOR
                        else -> NusaKasTab.DASHBOARD
                    }
                },
                onFinishTutorial = viewModel::markTutorialCompleted
            )
        }
    }
}
