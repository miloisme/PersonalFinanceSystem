package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.CurrencyConverter
import com.example.sync.GoogleDriveSync
import com.example.ui.MainViewModel
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

enum class AppTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.BarChart),
    BUDGET("Budget", Icons.Default.PieChart),
    ACCOUNTS("Accounts", Icons.Default.AccountBalanceWallet),
    BALANCE("Balance", Icons.Default.CalendarViewMonth),
    TRANSACTIONS("Records", Icons.Default.ReceiptLong),
    DEBTS("Debt", Icons.Default.CreditCard),
    NOTES("Notes", Icons.Default.Notes),
    SQL("SQL", Icons.Default.Terminal),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PersonalFinanceApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalFinanceApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }
    val symbol = CurrencyConverter.symbol(uiState.baseCurrency)

    var showGoogleAccountDialog by remember { mutableStateOf(false) }
    var showDownloadConfirmDialog by remember { mutableStateOf(false) }
    var showDownloadPasswordDialog by remember { mutableStateOf(false) }
    var downloadPasswordInput by remember { mutableStateOf("") }
    var downloadPasswordError by remember { mutableStateOf<String?>(null) }
    var downloadPasswordVisible by remember { mutableStateOf(false) }
    var syncNoticeMessage by remember { mutableStateOf<String?>(null) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(GoogleDriveSync.SCOPE_DRIVE_FILE),
                Scope(GoogleDriveSync.SCOPE_DRIVE_APPDATA)
            )
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email = account.email ?: ""
            viewModel.handleGoogleSignInSuccess(account.account, email) { success, msg ->
                syncNoticeMessage = msg
            }
        } catch (e: ApiException) {
            syncNoticeMessage = "Google Sign-In failed (code ${e.statusCode}): ${e.localizedMessage ?: "Please verify Google Cloud credentials"}\n\nPlease ensure you have added an Android OAuth client ID in Google Cloud Console -> Credentials with the correct Package name and SHA-1 fingerprint."
        } catch (e: Exception) {
            syncNoticeMessage = "Login error: ${e.localizedMessage}"
        }
    }

    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        viewModel.dismissStatusMessage()
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissStatusMessage()
        }
    }

    if (!uiState.isInitialized) {
        Box(modifier = Modifier.fillMaxSize().background(GrayBackground), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    // Login / Lock Screen if encrypted and not unlocked
    if (uiState.isEncrypted && !uiState.isUnlocked) {
        LoginScreen(
            isEncrypted = true,
            onUnlock = { password, callback ->
                viewModel.unlockDatabase(password, callback)
            },
            onDirectOpen = { viewModel.loadAllData() }
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Personal Finance",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = uiState.baseCurrency,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavySidebar,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // 1. Google Drive Account / Login Button
                    IconButton(onClick = { showGoogleAccountDialog = true }) {
                        Icon(
                            imageVector = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank()) Icons.Default.AccountCircle else Icons.Default.VpnKey,
                            contentDescription = "Google Drive Account",
                            tint = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank()) GreenIncome else Color.White
                        )
                    }

                    // 2. Upload to Google Drive
                    IconButton(
                        onClick = {
                            viewModel.uploadToGoogleDrive { success, msg ->
                                syncNoticeMessage = msg
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload to Google Drive",
                            tint = Color.White
                        )
                    }

                    // 3. Download from Google Drive (with overwrite warning prompt)
                    IconButton(
                        onClick = {
                            showDownloadConfirmDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download from Google Drive",
                            tint = Color.White
                        )
                    }

                    // Lock action if encrypted
                    if (uiState.isEncrypted) {
                        IconButton(onClick = { viewModel.lockDatabase() }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Database",
                                tint = OrangeDark
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            val mainTabs = remember {
                listOf(
                    AppTab.DASHBOARD,
                    AppTab.BUDGET,
                    AppTab.ACCOUNTS,
                    AppTab.BALANCE,
                    AppTab.TRANSACTIONS,
                    AppTab.DEBTS,
                    AppTab.NOTES,
                    AppTab.SQL,
                    AppTab.SETTINGS
                )
            }
            Surface(
                color = Color.White,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    mainTabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent,
                            modifier = Modifier
                                .clickable { selectedTab = tab }
                                .testTag("tab_${tab.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(19.dp),
                                    tint = if (isSelected) PrimaryBlue else GrayTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) PrimaryBlue else GrayTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GrayBackground)
        ) {
            when (selectedTab) {
                AppTab.DASHBOARD -> DashboardScreen(
                    baseCurrency = uiState.baseCurrency,
                    accounts = uiState.accounts,
                    debts = uiState.debts,
                    transactions = uiState.transactions,
                    netWorthHistory = uiState.netWorthHistory,
                    startDate = uiState.dashboardStartDate,
                    endDate = uiState.dashboardEndDate,
                    period = uiState.dashboardPeriod,
                    onRangeChanged = { s, e, p -> viewModel.setDashboardRange(s, e, p) },
                    onUpdateNetWorth = { d, a, l -> viewModel.updateNetWorthManual(d, a, l) }
                )

                AppTab.ACCOUNTS -> AccountsScreen(
                    baseCurrency = uiState.baseCurrency,
                    accounts = uiState.accounts,
                    debts = uiState.debts,
                    currencies = uiState.currencies,
                    netWorthHistory = uiState.netWorthHistory,
                    onAddAccount = { acc, bTx -> viewModel.addAccount(acc, bTx) },
                    onUpdateAccount = { acc -> viewModel.updateAccount(acc) },
                    onDeleteAccount = { id -> viewModel.deleteAccount(id) },
                    onTransfer = { f, t, a, cb -> viewModel.transferBetweenAccounts(f, t, a, cb) }
                )

                AppTab.BALANCE -> BalanceScreen(
                    baseCurrency = uiState.baseCurrency,
                    selectedYear = uiState.balanceReviewYear,
                    transactions = uiState.transactions,
                    categories = uiState.categories,
                    onSelectYear = { y -> viewModel.setBalanceReviewYear(y) }
                )

                AppTab.TRANSACTIONS -> TransactionsScreen(
                    baseCurrency = uiState.baseCurrency,
                    transactions = uiState.transactions,
                    accounts = uiState.accounts,
                    categories = uiState.categories,
                    currencies = uiState.currencies,
                    filterConditions = uiState.filterConditions,
                    onAddCondition = { c -> viewModel.addFilterCondition(c) },
                    onRemoveCondition = { idx -> viewModel.removeFilterCondition(idx) },
                    onClearConditions = { viewModel.clearFilterConditions() },
                    onAddTransaction = { tx -> viewModel.addTransaction(tx) },
                    onUpdateTransaction = { tx -> viewModel.updateTransaction(tx) },
                    onDeleteTransaction = { id -> viewModel.deleteTransaction(id) }
                )

                AppTab.BUDGET -> BudgetScreen(
                    baseCurrency = uiState.baseCurrency,
                    accounts = uiState.accounts,
                    debts = uiState.debts,
                    funds = uiState.funds,
                    categories = uiState.categories,
                    transactions = uiState.transactions,
                    budgetAccountIds = uiState.budgetAccountIds,
                    forecast = uiState.forecast,
                    history = uiState.netWorthHistory,
                    onToggleAccount = { id, checked -> viewModel.toggleBudgetAccountId(id, checked) },
                    onAddFund = { name, amt, cats -> viewModel.addFund(name, amt, cats) },
                    onUpdateFund = { id, name, amt, cats -> viewModel.updateFund(id, name, amt, cats) },
                    onDeleteFund = { id -> viewModel.deleteFund(id) },
                    onSetForecastCell = { m, f, v -> viewModel.setForecastCell(m, f, v) }
                )

                AppTab.DEBTS -> DebtsScreen(
                    baseCurrency = uiState.baseCurrency,
                    debts = uiState.debts,
                    currencies = uiState.currencies,
                    onAddDebt = { d -> viewModel.addDebt(d) },
                    onUpdateDebt = { d -> viewModel.updateDebt(d) },
                    onDeleteDebt = { id -> viewModel.deleteDebt(id) }
                )

                AppTab.NOTES -> NotesScreen(
                    notes = uiState.notes,
                    onAddNote = { c -> viewModel.addNote(c) },
                    onUpdateNote = { id, c -> viewModel.updateNote(id, c) },
                    onDeleteNote = { id -> viewModel.deleteNote(id) }
                )

                AppTab.SQL -> SqlScreen(
                    sqlResult = uiState.sqlResult,
                    onExecuteSql = { q -> viewModel.executeSql(q) }
                )

                AppTab.SETTINGS -> SettingsScreen(
                    baseCurrency = uiState.baseCurrency,
                    currencies = uiState.currencies,
                    categories = uiState.categories,
                    isEncrypted = uiState.isEncrypted,
                    onSetBaseCurrency = { c -> viewModel.setBaseCurrency(c) },
                    onAddCurrency = { c -> viewModel.addCurrency(c) },
                    onRemoveCurrency = { c -> viewModel.removeCurrency(c) },
                    onRefreshRates = { viewModel.refreshRates() },
                    onAddCategory = { c -> viewModel.addCategory(c) },
                    onUpdateCategory = { c -> viewModel.updateCategory(c) },
                    onDeleteCategory = { id -> viewModel.deleteCategory(id) },
                    onApplyEncryption = { en, pwd, cb -> viewModel.applyEncryption(en, pwd, cb) },
                    onClearAllData = { viewModel.clearAllData() }
                )
            }

            // Global Loading Indicator overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = PrimaryBlue)
                            Text(
                                text = if (uiState.syncStatus.isNotBlank()) uiState.syncStatus else "Processing...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = NavySidebar
                            )
                        }
                    }
                }
            }
        }
    }

    // Google Drive Download Overwrite Confirmation Dialog
    if (showDownloadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirmDialog = false },
            title = { Text("Download Database from Google Drive") },
            text = { Text("Downloading will overwrite your local database (if present). If the cloud database is encrypted, you will need to enter the master password after download.\n\nAre you sure you want to download and overwrite?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadConfirmDialog = false
                        viewModel.downloadFromGoogleDrive(
                            onRequiresPassword = { salt, verifier ->
                                downloadPasswordInput = ""
                                downloadPasswordError = null
                                showDownloadPasswordDialog = true
                            },
                            onResult = { success, msg ->
                                syncNoticeMessage = msg
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedExpense)
                ) {
                    Text("Overwrite & Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Encrypted Download Password Decryption Dialog
    if (showDownloadPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showDownloadPasswordDialog = false
                downloadPasswordInput = ""
                downloadPasswordError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = OrangeDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloud Database Encrypted (AES-256)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "The database downloaded from Google Drive is password protected. Enter the master password to decrypt and cache locally.",
                        fontSize = 13.sp,
                        color = GrayTextSecondary
                    )

                    OutlinedTextField(
                        value = downloadPasswordInput,
                        onValueChange = {
                            downloadPasswordInput = it
                            downloadPasswordError = null
                        },
                        label = { Text("Master Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (downloadPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { downloadPasswordVisible = !downloadPasswordVisible }) {
                                Icon(
                                    imageVector = if (downloadPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        isError = downloadPasswordError != null
                    )

                    if (downloadPasswordError != null) {
                        Text(
                            text = downloadPasswordError ?: "",
                            color = RedExpense,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (downloadPasswordInput.isBlank()) {
                            downloadPasswordError = "Please enter master password"
                            return@Button
                        }
                        viewModel.decryptAndApplyDownloadedDb(downloadPasswordInput.trim()) { success, msg ->
                            if (success) {
                                showDownloadPasswordDialog = false
                                downloadPasswordInput = ""
                                downloadPasswordError = null
                                syncNoticeMessage = msg
                            } else {
                                downloadPasswordError = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Decrypt & Cache")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadPasswordDialog = false
                    downloadPasswordInput = ""
                    downloadPasswordError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Google Drive Login / Account Setup Dialog
    if (showGoogleAccountDialog) {
        var manualToken by remember { mutableStateOf(uiState.googleOAuthToken) }
        var showManualInput by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGoogleAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google Drive Cloud Sync", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Account Status Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank())
                                GreenIncome.copy(alpha = 0.08f)
                            else GrayBackground
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank())
                                        Icons.Default.CheckCircle
                                    else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank())
                                        GreenIncome
                                    else GrayTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank())
                                        "Connected Google Account"
                                    else "No Google Account Connected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = NavySidebar
                                )
                            }

                            if (uiState.googleAccountEmail.isNotBlank()) {
                                Text(
                                    text = "Account: ${uiState.googleAccountEmail}",
                                    fontSize = 13.sp,
                                    color = NavySidebar
                                )
                            }
                            if (uiState.lastSyncedAt.isNotBlank()) {
                                Text(
                                    text = "Last Synced: ${uiState.lastSyncedAt}",
                                    fontSize = 11.sp,
                                    color = GrayTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action button: Google Sign-In or Change
                            Button(
                                onClick = {
                                    signInLauncher.launch(googleSignInClient.signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.googleAccountEmail.isNotBlank()) "Switch / Re-authorize Google Account" else "Select Google Account")
                            }

                            if (uiState.googleAccountEmail.isNotBlank() || uiState.googleOAuthToken.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.signOutGoogleAccount()
                                        googleSignInClient.signOut()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Sign Out Current Account", color = RedExpense)
                                }
                            }
                        }
                    }

                    // Google Cloud Console Configuration Guide
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Google Cloud Console Credentials Info",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryBlue
                            )
                            Text(
                                text = "In Google Cloud Console -> APIs & Services -> Credentials, create an OAuth 2.0 Client ID (Android) with the following info and enable Google Drive API:",
                                fontSize = 11.sp,
                                color = GrayTextSecondary,
                                lineHeight = 16.sp
                            )

                            // Package Name
                            Column {
                                Text("Package Name:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = uiState.packageName.ifBlank { context.packageName },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(uiState.packageName.ifBlank { context.packageName }, "Package Name")
                                            syncNoticeMessage = "Package Name copied to clipboard"
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // SHA-1 Fingerprint
                            Column {
                                Text("SHA-1 Fingerprint:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = uiState.sha1Fingerprint.ifBlank { GoogleDriveSync.getCertificateSha1(context) },
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            copyToClipboard(uiState.sha1Fingerprint.ifBlank { GoogleDriveSync.getCertificateSha1(context) }, "SHA-1 Fingerprint")
                                            syncNoticeMessage = "SHA-1 Fingerprint copied to clipboard"
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Collapsible Manual OAuth Token
                    TextButton(
                        onClick = { showManualInput = !showManualInput },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(if (showManualInput) "Hide Manual Token Input" else "Advanced: Manual OAuth Token", fontSize = 12.sp)
                    }

                    if (showManualInput) {
                        OutlinedTextField(
                            value = manualToken,
                            onValueChange = { manualToken = it },
                            label = { Text("Manual OAuth Access Token") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                            maxLines = 4
                        )
                        Button(
                            onClick = {
                                viewModel.setGoogleOAuthToken(manualToken.trim())
                                showGoogleAccountDialog = false
                                syncNoticeMessage = "OAuth Token saved manually"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Manual Token")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showGoogleAccountDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Sync Notification Dialog
    if (syncNoticeMessage != null) {
        AlertDialog(
            onDismissRequest = { syncNoticeMessage = null },
            title = { Text("Google Drive Sync Notice") },
            text = { Text(syncNoticeMessage ?: "") },
            confirmButton = {
                Button(onClick = { syncNoticeMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}
