package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoManager
import com.example.data.db.AppDatabase
import com.example.data.db.SqlResult
import com.example.data.model.*
import com.example.sync.CurrencyConverter
import com.example.sync.GoogleDriveSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class MainUiState(
    val isInitialized: Boolean = false,
    val isEncrypted: Boolean = false,
    val isUnlocked: Boolean = false,
    val baseCurrency: String = "HKD",
    val currencies: List<String> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val debts: List<DebtItem> = emptyList(),
    val funds: List<EarmarkedFund> = emptyList(),
    val budgetAccountIds: Set<Long> = emptySet(),
    val forecast: Map<String, BudgetForecastItem> = emptyMap(),
    val notes: List<NoteItem> = emptyList(),
    val netWorthHistory: List<NetWorthHistoryItem> = emptyList(),
    val filterConditions: List<FilterCondition> = emptyList(),
    val dashboardStartDate: String = "",
    val dashboardEndDate: String = "",
    val dashboardPeriod: String = "month", // "month" or "year"
    val balanceReviewYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val lastSyncedAt: String = "",
    val googleAccountEmail: String = "",
    val googleOAuthToken: String = "",
    val sha1Fingerprint: String = "",
    val packageName: String = "",
    val syncStatus: String = "",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val sqlResult: SqlResult = SqlResult.Empty
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase(application, "finance.db")
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkEncryptionState()
    }

    fun checkEncryptionState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = getApplication<Application>().getDatabasePath(db.dbPath)
                val meta = CryptoManager.loadMeta(dbFile)
                val isEnc = meta.optBoolean("encryption", false)
                val encFile = CryptoManager.getEncFile(dbFile)
                val sha1 = GoogleDriveSync.getCertificateSha1(getApplication())
                val pkg = getApplication<Application>().packageName

                if (isEnc && db.cryptoKey == null) {
                    // Close db before deleting plaintext file
                    db.close()
                    // If encrypted and not unlocked in this process, ensure plaintext db file is removed
                    if (dbFile.exists() && encFile.exists()) {
                        CryptoManager.secureDelete(dbFile)
                    }
                    _uiState.update {
                        it.copy(
                            isInitialized = true,
                            isEncrypted = true,
                            isUnlocked = false,
                            sha1Fingerprint = sha1,
                            packageName = pkg
                        )
                    }
                } else {
                    val email = GoogleDriveSync.getAccountEmail(db)
                    val token = GoogleDriveSync.getOAuthToken(getApplication(), db)
                    _uiState.update {
                        it.copy(
                            isInitialized = true,
                            isEncrypted = isEnc,
                            isUnlocked = true,
                            googleAccountEmail = email,
                            googleOAuthToken = token,
                            sha1Fingerprint = sha1,
                            packageName = pkg
                        )
                    }
                    loadAllData()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error checking encryption state", e)
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isUnlocked = false
                    )
                }
            }
        }
    }

    fun unlockDatabase(password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = getApplication<Application>().getDatabasePath(db.dbPath)
            val meta = CryptoManager.loadMeta(dbFile)
            val saltHex = meta.optString("salt", "")
            val verifier = meta.optString("verifier", "")
            val encFile = CryptoManager.getEncFile(dbFile)

            if (saltHex.isEmpty() || verifier.isEmpty() || !encFile.exists()) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Encrypted metadata or database file not found. Please verify existence.")
                }
                return@launch
            }

            try {
                val salt = CryptoManager.hexToByteArray(saltHex)
                val key = CryptoManager.deriveKey(password, salt)
                if (!CryptoManager.checkVerifier(key, verifier)) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Incorrect master password. Please try again.")
                    }
                    return@launch
                }

                // 1. Close current db connection cleanly before writing to disk
                db.close()

                // 2. Decrypt file to plaintext db
                CryptoManager.decryptFile(encFile, dbFile, key)
                db.cryptoKey = key

                // 3. Reopen fresh SQLite connection
                db.reopen()

                // 4. Read credentials safely from decrypted db
                val email = GoogleDriveSync.getAccountEmail(db)
                val token = GoogleDriveSync.getOAuthToken(getApplication(), db)

                // 5. Save master password for future biometric unlocks
                com.example.crypto.BiometricHelper.savePassword(getApplication(), password)

                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        isEncrypted = true,
                        googleAccountEmail = email,
                        googleOAuthToken = token
                    )
                }
                loadAllData()
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Unlock error", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Decryption or loading error: ${e.localizedMessage ?: "Please try again"}")
                }
            }
        }
    }

    fun lockDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbFile = getApplication<Application>().getDatabasePath(db.dbPath)
                val meta = CryptoManager.loadMeta(dbFile)
                val isEnc = meta.optBoolean("encryption", false)
                val key = db.cryptoKey

                if (isEnc && key != null) {
                    db.close()
                    val encFile = CryptoManager.getEncFile(dbFile)
                    CryptoManager.encryptFile(dbFile, encFile, key)
                    CryptoManager.secureDelete(dbFile)
                }
                db.cryptoKey = null
                _uiState.update {
                    it.copy(isUnlocked = false)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Lock error", e)
            }
        }
    }

    fun loadAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = db.getBaseCurrency()
                val curs = db.getCurrencies()
                val accs = db.getAccounts()
                val cats = db.getCategories()
                val txs = db.getTransactions()
                val debtsList = db.getDebts()
                val fundsList = db.getFunds()
                val budgetAccs = db.getBudgetAccountIds() ?: accs.mapNotNull { it.id }.toSet()
                val fc = db.getForecast()
                val notesList = db.getNotes()
                val lastSync = GoogleDriveSync.getLastSyncedAt(db)
                val token = GoogleDriveSync.getOAuthToken(getApplication(), db)

                // Default dates
                val cal = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val defaultStart = db.getSetting("dash_start_date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time))
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val defaultEnd = db.getSetting("dash_end_date", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time))

                // Fetch live FX rates first so the snapshot we write uses up-to-date rates.
                // Pass all currencies actually used (base + configured + accounts + debts) so
                // every currency gets a live direct rate and none falls back to the static table.
                CurrencyConverter.fetchLiveRates(base, allUsedCurrencies(base))

                // Auto-upsert today's live net worth snapshot (account balances + uncompleted
                // receivables owed to me, minus uncompleted debts owed by me). This mirrors Python's
                // refresh(): the snapshot for "today" is always the current live value, so the
                // Dashboard (which reads the snapshot for the current period) stays identical to the
                // Accounts/Budget screens (which compute live directly).
                try {
                    val totalAssets = accs.sumOf { CurrencyConverter.convert(it.balance, it.currency, base) } +
                        debtsList.filter { !it.completed && it.debtor == "other" }
                            .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, base) }
                    val totalLiab = debtsList.filter { !it.completed && it.debtor == "me" }
                        .sumOf { CurrencyConverter.convert(it.totalAmount - it.paidAmount, it.currency, base) }
                    db.upsertNetWorth(todayStr, totalAssets, totalLiab)
                } catch (e: Exception) {
                    // Ignore net worth calculation error
                }

                _uiState.update {
                    it.copy(
                        baseCurrency = base,
                        currencies = curs,
                        accounts = accs,
                        categories = cats,
                        transactions = txs,
                        debts = debtsList,
                        funds = fundsList,
                        budgetAccountIds = budgetAccs,
                        forecast = fc,
                        notes = notesList,
                        netWorthHistory = db.getNetWorthHistory(),
                        dashboardStartDate = defaultStart,
                        dashboardEndDate = defaultEnd,
                        lastSyncedAt = lastSync,
                        googleOAuthToken = token
                    )
                }
            } catch (e: Throwable) {
                android.util.Log.e("MainViewModel", "Error loading data", e)
            }
        }
    }

    // --- Base Currency & Rates ---
    // 收集所有實際會用到的幣別（base + 設定中 + 帳戶 + 債務），
    // 確保每個幣別都拿到即時直接匯率，與 Python 端 get_rate() 一致。
    private fun allUsedCurrencies(base: String): List<String> {
        val set = LinkedHashSet<String>()
        set.add(base.uppercase())
        set.addAll(db.getCurrencies().map { it.uppercase() })
        set.addAll(db.getAccounts().map { it.currency.uppercase() })
        set.addAll(db.getDebts().map { it.currency.uppercase() })
        return set.toList()
    }

    fun setBaseCurrency(currency: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setBaseCurrency(currency)
            CurrencyConverter.refreshCache()
            CurrencyConverter.fetchLiveRates(currency, allUsedCurrencies(currency))
            loadAllData()
        }
    }

    fun addCurrency(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addCurrency(code)
            loadAllData()
        }
    }

    fun removeCurrency(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.removeCurrency(code)
            loadAllData()
        }
    }

    fun refreshRates() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            CurrencyConverter.refreshCache()
            CurrencyConverter.fetchLiveRates(db.getBaseCurrency(), allUsedCurrencies(db.getBaseCurrency()))
            loadAllData()
            _uiState.update { it.copy(isLoading = false, statusMessage = "Exchange rates refreshed.") }
        }
    }

    // --- Accounts ---
    fun addAccount(account: Account, bookTx: TransactionItem? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = db.addAccount(account)
            if (bookTx != null && bookTx.amount != 0.0) {
                db.addTransaction(bookTx.copy(accountId = newId))
            }
            loadAllData()
        }
    }

    fun updateAccount(account: Account) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateAccount(account)
            loadAllData()
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteAccount(accountId)
            loadAllData()
        }
    }

    fun transferBetweenAccounts(fromId: Long, toId: Long, amount: Double, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.transferBetweenAccounts(fromId, toId, amount)
                loadAllData()
                withContext(Dispatchers.Main) { onResult(true, null) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.localizedMessage) }
            }
        }
    }

    // --- Categories ---
    fun addCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addCategory(category)
            loadAllData()
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateCategory(category)
            loadAllData()
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteCategory(categoryId)
            loadAllData()
        }
    }

    // --- Transactions ---
    fun addTransaction(transaction: TransactionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addTransaction(transaction)
            loadAllData()
        }
    }

    fun updateTransaction(transaction: TransactionItem) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateTransaction(transaction)
            loadAllData()
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteTransaction(transactionId)
            loadAllData()
        }
    }

    // --- Filter Conditions ---
    fun addFilterCondition(condition: FilterCondition) {
        _uiState.update {
            it.copy(filterConditions = it.filterConditions + condition)
        }
    }

    fun removeFilterCondition(index: Int) {
        _uiState.update {
            val list = it.filterConditions.toMutableList()
            if (index in list.indices) list.removeAt(index)
            it.copy(filterConditions = list)
        }
    }

    fun clearFilterConditions() {
        _uiState.update { it.copy(filterConditions = emptyList()) }
    }

    // --- Debts ---
    fun addDebt(debt: DebtItem) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addDebt(debt)
            loadAllData()
        }
    }

    fun updateDebt(debt: DebtItem) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateDebt(debt)
            loadAllData()
        }
    }

    fun deleteDebt(debtId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteDebt(debtId)
            loadAllData()
        }
    }

    // --- Budget & Funds ---
    fun toggleBudgetAccountId(accountId: Long, isChecked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val set = _uiState.value.budgetAccountIds.toMutableSet()
            if (isChecked) set.add(accountId) else set.remove(accountId)
            db.setBudgetAccountIds(set)
            _uiState.update { it.copy(budgetAccountIds = set) }
        }
    }

    fun addFund(name: String, amount: Double, categories: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addFund(name, amount, categories)
            loadAllData()
        }
    }

    fun updateFund(id: Long, name: String, amount: Double, categories: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateFund(id, name, amount, categories)
            loadAllData()
        }
    }

    fun deleteFund(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteFund(id)
            loadAllData()
        }
    }

    fun setForecastCell(month: String, field: String, value: Double?) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setForecastCell(month, field, value)
            loadAllData()
        }
    }

    // --- Dashboard Settings ---
    fun setDashboardRange(start: String, end: String, period: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.setSetting("dash_start_date", start)
            db.setSetting("dash_end_date", end)
            _uiState.update {
                it.copy(
                    dashboardStartDate = start,
                    dashboardEndDate = end,
                    dashboardPeriod = period
                )
            }
        }
    }

    fun setBalanceReviewYear(year: Int) {
        _uiState.update { it.copy(balanceReviewYear = year) }
    }

    fun updateNetWorthManual(dateStr: String, assets: Double, liabilities: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.upsertNetWorth(dateStr, assets, liabilities)
            loadAllData()
        }
    }

    // --- Notes ---
    fun addNote(content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.addNote(content)
            loadAllData()
        }
    }

    fun updateNote(id: Long, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.updateNote(id, content)
            loadAllData()
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.deleteNote(id)
            loadAllData()
        }
    }

    // --- SQL Console ---
    fun executeSql(sql: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = db.executeSql(sql)
                _uiState.update { it.copy(sqlResult = res) }
                if (res !is SqlResult.QuerySuccess) {
                    loadAllData()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sqlResult = SqlResult.Error(e.localizedMessage ?: "SQL Execution error")) }
            }
        }
    }

    // --- Settings & Encryption ---
    fun applyEncryption(enabled: Boolean, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = getApplication<Application>().getDatabasePath(db.dbPath)
            try {
                if (enabled) {
                    if (password.isEmpty()) {
                        withContext(Dispatchers.Main) { onResult(false, "Password cannot be empty") }
                        return@launch
                    }
                    val salt = CryptoManager.genSalt()
                    val key = CryptoManager.deriveKey(password, salt)
                    val verifier = CryptoManager.makeVerifier(key)
                    val meta = JSONObject().apply {
                        put("encryption", true)
                        put("salt", CryptoManager.byteArrayToHex(salt))
                        put("verifier", verifier)
                        put("schema_version", CryptoManager.SCHEMA_VERSION)
                    }
                    CryptoManager.saveMeta(dbFile, meta)
                    val encFile = CryptoManager.getEncFile(dbFile)
                    CryptoManager.encryptFile(dbFile, encFile, key)
                    db.cryptoKey = key
                    com.example.crypto.BiometricHelper.savePassword(getApplication(), password)
                    _uiState.update { it.copy(isEncrypted = true, isUnlocked = true) }
                    withContext(Dispatchers.Main) { onResult(true, "Encryption enabled with AES-256-GCM.") }
                } else {
                    val meta = JSONObject().apply { put("encryption", false) }
                    CryptoManager.saveMeta(dbFile, meta)
                    val encFile = CryptoManager.getEncFile(dbFile)
                    if (encFile.exists()) CryptoManager.secureDelete(encFile)
                    db.cryptoKey = null
                    com.example.crypto.BiometricHelper.clearSavedPassword(getApplication())
                    _uiState.update { it.copy(isEncrypted = false, isUnlocked = true) }
                    withContext(Dispatchers.Main) { onResult(true, "Encryption disabled.") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.localizedMessage) }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            db.clearAll()
            loadAllData()
            _uiState.update { it.copy(statusMessage = "All user data cleared.") }
        }
    }

    // --- Google Drive Sync ---
    fun handleGoogleSignInSuccess(account: android.accounts.Account?, email: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, syncStatus = "Obtaining Google Drive authorization...") }
            try {
                if (account == null) {
                    _uiState.update { it.copy(isLoading = false, syncStatus = "Google account not found") }
                    withContext(Dispatchers.Main) { onComplete(false, "Google account not found") }
                    return@launch
                }
                val res = GoogleDriveSync.fetchOAuthTokenForAccount(getApplication(), account)
                _uiState.update { it.copy(isLoading = false) }
                if (res.isSuccess) {
                    val token = res.getOrNull() ?: ""
                    GoogleDriveSync.setOAuthToken(db, token)
                    GoogleDriveSync.setAccountEmail(db, email)
                    _uiState.update {
                        it.copy(
                            googleOAuthToken = token,
                            googleAccountEmail = email,
                            syncStatus = "Successfully connected Google account: $email"
                        )
                    }
                    withContext(Dispatchers.Main) { onComplete(true, "Successfully connected Google account: $email") }
                } else {
                    val err = res.exceptionOrNull()?.localizedMessage ?: "Authorization failed"
                    _uiState.update { it.copy(syncStatus = "Authorization failed: $err") }
                    withContext(Dispatchers.Main) { onComplete(false, err) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, syncStatus = "Connection failed: ${e.localizedMessage}") }
                withContext(Dispatchers.Main) { onComplete(false, e.localizedMessage ?: "Connection failed") }
            }
        }
    }

    fun signOutGoogleAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            GoogleDriveSync.setOAuthToken(db, "")
            GoogleDriveSync.setAccountEmail(db, "")
            _uiState.update {
                it.copy(
                    googleOAuthToken = "",
                    googleAccountEmail = "",
                    syncStatus = "Signed out of Google account"
                )
            }
        }
    }

    fun setGoogleOAuthToken(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            GoogleDriveSync.setOAuthToken(db, token)
            _uiState.update { it.copy(googleOAuthToken = token.trim()) }
        }
    }

    fun uploadToGoogleDrive(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, syncStatus = "Uploading to Google Drive...") }
            val res = GoogleDriveSync.uploadToDrive(getApplication(), db, db.cryptoKey)
            _uiState.update { it.copy(isLoading = false) }
            if (res.isSuccess) {
                val time = res.getOrNull() ?: ""
                _uiState.update { it.copy(lastSyncedAt = time, syncStatus = "Upload successful ($time)") }
                withContext(Dispatchers.Main) { onResult(true, "Database successfully uploaded and backed up to Google Drive!\nTime: $time") }
            } else {
                val err = res.exceptionOrNull()?.localizedMessage ?: "Upload failed"
                _uiState.update { it.copy(syncStatus = "Upload failed: $err") }
                withContext(Dispatchers.Main) { onResult(false, err) }
            }
        }
    }

    fun downloadFromGoogleDrive(
        onRequiresPassword: (salt: String, verifier: String) -> Unit,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, syncStatus = "Downloading from Google Drive...") }
            val res = GoogleDriveSync.downloadFromDrive(getApplication(), db, db.cryptoKey)
            _uiState.update { it.copy(isLoading = false) }
            when (res) {
                is com.example.sync.DriveDownloadResult.Success -> {
                    val meta = res.meta
                    val time = meta.optString("updated_at", "")
                    loadAllData()
                    _uiState.update {
                        it.copy(
                            lastSyncedAt = time,
                            isEncrypted = res.isEncrypted,
                            isUnlocked = true,
                            syncStatus = "Successfully downloaded latest database from Google Drive."
                        )
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, "Download and sync successful! Local database updated to cloud version.")
                    }
                }
                is com.example.sync.DriveDownloadResult.RequiresPassword -> {
                    _uiState.update { it.copy(syncStatus = "Cloud database is encrypted. Master password required.") }
                    withContext(Dispatchers.Main) {
                        onRequiresPassword(res.salt, res.verifier)
                    }
                }
                is com.example.sync.DriveDownloadResult.Error -> {
                    _uiState.update { it.copy(syncStatus = "Download error: ${res.message}") }
                    withContext(Dispatchers.Main) {
                        onResult(false, res.message)
                    }
                }
            }
        }
    }

    fun decryptAndApplyDownloadedDb(
        password: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, syncStatus = "Verifying password and decrypting cloud database...") }
            val res = GoogleDriveSync.downloadFromDrive(getApplication(), db, null)
            _uiState.update { it.copy(isLoading = false) }
            if (res is com.example.sync.DriveDownloadResult.RequiresPassword) {
                try {
                    val salt = CryptoManager.hexToByteArray(res.salt)
                    val key = CryptoManager.deriveKey(password, salt)
                    if (!CryptoManager.checkVerifier(key, res.verifier)) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Incorrect master password. Unable to decrypt cloud database. Please try again.")
                        }
                        return@launch
                    }

                    val decrypted = CryptoManager.decryptBytes(res.encBytes, key)
                    val encId = db.getSetting("google_drive_enc_id", "")
                    val metaId = db.getSetting("google_drive_meta_id", "")
                    GoogleDriveSync.applyDownloadedDb(
                        getApplication(),
                        db,
                        decrypted,
                        res.encBytes,
                        res.meta,
                        key,
                        encId,
                        metaId
                    )
                    db.cryptoKey = key
                    com.example.crypto.BiometricHelper.savePassword(getApplication(), password)
                    loadAllData()
                    val time = res.meta.optString("updated_at", "")
                    _uiState.update {
                        it.copy(
                            isEncrypted = true,
                            isUnlocked = true,
                            lastSyncedAt = time,
                            syncStatus = "Successfully decrypted and cached database locally."
                        )
                    }
                    withContext(Dispatchers.Main) {
                        onResult(true, "Decryption successful. Database synced and cached locally.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Decryption error: ${e.localizedMessage}")
                    }
                }
            } else if (res is com.example.sync.DriveDownloadResult.Success) {
                loadAllData()
                _uiState.update { it.copy(isUnlocked = true) }
                withContext(Dispatchers.Main) {
                    onResult(true, "Database sync successful.")
                }
            } else if (res is com.example.sync.DriveDownloadResult.Error) {
                withContext(Dispatchers.Main) {
                    onResult(false, res.message)
                }
            }
        }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
