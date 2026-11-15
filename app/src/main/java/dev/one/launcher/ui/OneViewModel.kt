package dev.one.launcher.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.one.launcher.data.DownloadRepository
import dev.one.launcher.data.DownloadState
import dev.one.launcher.data.MatchedFile
import dev.one.launcher.data.MatchedFilesStore
import dev.one.launcher.data.PortCatalog
import dev.one.launcher.data.PortCatalogRepository
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.PortSettings
import dev.one.launcher.data.PortSettingsStore
import dev.one.launcher.data.PremiumizeAccount
import dev.one.launcher.data.PremiumizeApi
import dev.one.launcher.data.PremiumizeBreadcrumb
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.PremiumizeResult
import dev.one.launcher.data.SecureKeyStore
import dev.one.launcher.model.Port
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PortStatus(
    val installedPackage: String? = null,
    val assignedFile: MatchedFile? = null,
    val downloadedFileName: String? = null
) {
    val isInstalled: Boolean get() = installedPackage != null
    val hasFile: Boolean get() = assignedFile != null || downloadedFileName != null
    val isReady: Boolean get() = isInstalled && hasFile
    val statusText: String
        get() = when {
            isReady -> "Ready to play"
            isInstalled -> "Installed"
            hasFile -> "File matched"
            else -> "Not installed"
        }
}

class OneViewModel(application: Application) : AndroidViewModel(application) {

    private val catalogRepo = PortCatalogRepository(application)
    private val secureKeyStore = SecureKeyStore(application)
    private val settingsStore = PortSettingsStore(application)
    private val matchedStore = MatchedFilesStore(application)
    private val downloadRepo = DownloadRepository(application)

    val catalogNote: String
    val ports: List<Port>

    init {
        val catalog = try {
            catalogRepo.load()
        } catch (e: Exception) {
            PortCatalog()
        }
        catalogNote = catalog.catalogNote
        ports = catalog.ports
        refreshLibrary()
        // Restore a saved Premiumize session, if the user linked one before.
        if (secureKeyStore.getApiKey() != null) {
            viewModelScope.launch { restoreSession() }
        }
    }

    // ---- Library ----

    private val _portStatuses = MutableStateFlow<Map<String, PortStatus>>(emptyMap())
    val portStatuses: StateFlow<Map<String, PortStatus>> = _portStatuses.asStateFlow()

    private val _selectedPortId = MutableStateFlow<String?>(null)
    val selectedPortId: StateFlow<String?> = _selectedPortId.asStateFlow()

    private val _launchError = MutableStateFlow<String?>(null)
    val launchError: StateFlow<String?> = _launchError.asStateFlow()

    fun selectPort(id: String?) {
        _selectedPortId.value = id
    }

    fun portById(id: String): Port? = ports.firstOrNull { it.id == id }

    fun refreshLibrary() {
        val pm = getApplication<Application>().packageManager
        val downloaded = downloadRepo.listDownloadedFiles().map { it.name }
        val assigned = matchedStore.all()
        val statuses = ports.associate { port ->
            val installed = port.packageNames.firstOrNull { pkg ->
                try {
                    pm.getLaunchIntentForPackage(pkg) != null
                } catch (e: Exception) {
                    false
                }
            }
            val downloadedMatch = downloaded.firstOrNull { PortMatcher.matches(it, port) }
            port.id to PortStatus(
                installedPackage = installed,
                assignedFile = assigned[port.id],
                downloadedFileName = downloadedMatch
            )
        }
        _portStatuses.value = statuses
    }

    fun launchPort(portId: String) {
        val port = portById(portId) ?: return
        val pkg = _portStatuses.value[portId]?.installedPackage
        if (pkg == null) {
            _launchError.value = "Install ${port.title} from its Releases page first."
            return
        }
        val intent = getApplication<Application>().packageManager
            .getLaunchIntentForPackage(pkg)
        if (intent == null) {
            _launchError.value = "Could not open ${port.title}. It may have been uninstalled."
            refreshLibrary()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        getApplication<Application>().startActivity(intent)
    }

    fun dismissLaunchError() {
        _launchError.value = null
    }

    // ---- Matched files ----

    fun assignFile(portId: String, uri: Uri, displayName: String) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Persisting the permission is best-effort; the name match still stands.
        }
        matchedStore.assign(portId, MatchedFile(name = displayName, uri = uri.toString()))
        refreshLibrary()
    }

    fun clearAssignment(portId: String) {
        matchedStore.clear(portId)
        refreshLibrary()
    }

    fun clearAllAssignments() {
        matchedStore.clearAll()
        refreshLibrary()
    }

    // ---- Per-port settings ----

    fun settings(portId: String): Flow<PortSettings> = settingsStore.settings(portId)

    fun saveSettings(portId: String, settings: PortSettings) {
        viewModelScope.launch { settingsStore.save(portId, settings) }
    }

    // ---- Premiumize (own cloud only) ----

    private val _pmAccount = MutableStateFlow<PremiumizeAccount?>(null)
    val pmAccount: StateFlow<PremiumizeAccount?> = _pmAccount.asStateFlow()

    private val _pmConnecting = MutableStateFlow(false)
    val pmConnecting: StateFlow<Boolean> = _pmConnecting.asStateFlow()

    private val _pmError = MutableStateFlow<String?>(null)
    val pmError: StateFlow<String?> = _pmError.asStateFlow()

    private val _cloudItems = MutableStateFlow<List<PremiumizeItem>>(emptyList())
    val cloudItems: StateFlow<List<PremiumizeItem>> = _cloudItems.asStateFlow()

    private val _cloudBreadcrumbs = MutableStateFlow<List<PremiumizeBreadcrumb>>(emptyList())
    val cloudBreadcrumbs: StateFlow<List<PremiumizeBreadcrumb>> = _cloudBreadcrumbs.asStateFlow()

    private val _cloudLoading = MutableStateFlow(false)
    val cloudLoading: StateFlow<Boolean> = _cloudLoading.asStateFlow()

    private val _cloudError = MutableStateFlow<String?>(null)
    val cloudError: StateFlow<String?> = _cloudError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PremiumizeItem>?>(null)
    val searchResults: StateFlow<List<PremiumizeItem>?> = _searchResults.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    /** (folder id, display name), root first. */
    private val folderStack = mutableListOf<Pair<String?, String>>(null to "My cloud")

    private val _folderTrail = MutableStateFlow<List<String>>(listOf("My cloud"))
    val folderTrail: StateFlow<List<String>> = _folderTrail.asStateFlow()

    val isCloudConnected: Boolean get() = _pmAccount.value != null

    private fun api(): PremiumizeApi? {
        val key = secureKeyStore.getApiKey() ?: return null
        return PremiumizeApi(key)
    }

    private suspend fun restoreSession() {
        val key = secureKeyStore.getApiKey() ?: return
        when (val result = PremiumizeApi(key).accountInfo()) {
            is PremiumizeResult.Success -> {
                if (result.value.status == "success" || result.value.customer_id.isNotEmpty()) {
                    _pmAccount.value = result.value
                    browseFolder(null)
                } else {
                    _pmError.value = "Your saved Premiumize key was not accepted. Re-enter it below."
                }
            }
            is PremiumizeResult.Failure -> {
                _pmError.value = result.message
            }
        }
    }

    fun connectPremiumize(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            _pmError.value = "Paste your Premiumize API key first."
            return
        }
        _pmConnecting.value = true
        _pmError.value = null
        viewModelScope.launch {
            when (val result = PremiumizeApi(trimmed).accountInfo()) {
                is PremiumizeResult.Success -> {
                    if (result.value.status == "success" || result.value.customer_id.isNotEmpty()) {
                        secureKeyStore.saveApiKey(trimmed)
                        _pmAccount.value = result.value
                        folderStack.clear()
                        folderStack.add(null to "My cloud")
                        _folderTrail.value = listOf("My cloud")
                        browseFolder(null)
                    } else {
                        _pmError.value = "That key was not accepted. Check it and try again."
                    }
                }
                is PremiumizeResult.Failure -> _pmError.value = result.message
            }
            _pmConnecting.value = false
        }
    }

    fun disconnectPremiumize() {
        secureKeyStore.clear()
        _pmAccount.value = null
        _pmError.value = null
        _cloudItems.value = emptyList()
        _cloudBreadcrumbs.value = emptyList()
        _searchResults.value = null
        _downloadProgress.value = emptyMap()
        _downloadError.value = null
        folderStack.clear()
        folderStack.add(null to "My cloud")
        _folderTrail.value = listOf("My cloud")
    }

    fun browseFolder(id: String?) {
        val api = api() ?: return
        _cloudLoading.value = true
        _cloudError.value = null
        _searchResults.value = null
        viewModelScope.launch {
            when (val result = api.listFolder(id)) {
                is PremiumizeResult.Success -> {
                    _cloudItems.value = result.value.content
                    _cloudBreadcrumbs.value = result.value.breadcrumbs
                }
                is PremiumizeResult.Failure -> _cloudError.value = result.message
            }
            _cloudLoading.value = false
        }
    }

    fun openFolder(item: PremiumizeItem) {
        folderStack.add(item.id to item.name)
        _folderTrail.value = _folderTrail.value + item.name
        browseFolder(item.id)
    }

    fun navigateToBreadcrumb(index: Int) {
        // index 0 is the root; breadcrumbs from the API mirror the stack.
        val target = index.coerceIn(0, folderStack.lastIndex)
        while (folderStack.lastIndex > target) folderStack.removeAt(folderStack.lastIndex)
        _folderTrail.value = _folderTrail.value.take(target + 1)
        browseFolder(folderStack[target].first)
    }

    fun searchCloud(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = null
            return
        }
        val api = api() ?: return
        _cloudLoading.value = true
        _cloudError.value = null
        viewModelScope.launch {
            when (val result = api.searchOwnCloud(trimmed)) {
                is PremiumizeResult.Success -> _searchResults.value = result.value.content
                is PremiumizeResult.Failure -> _cloudError.value = result.message
            }
            _cloudLoading.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = null
    }

    fun downloadItem(item: PremiumizeItem) {
        val api = api() ?: return
        _downloadError.value = null
        viewModelScope.launch {
            val link = when (val details = api.itemDetails(item.id)) {
                is PremiumizeResult.Success -> details.value.link
                is PremiumizeResult.Failure -> {
                    _downloadError.value = details.message
                    return@launch
                }
            }
            if (link.isNullOrBlank()) {
                _downloadError.value = "No download link was returned for ${item.name}."
                return@launch
            }
            downloadRepo.download(link, item.name).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        val fraction = if (state.totalBytes > 0) {
                            state.bytesRead.toFloat() / state.totalBytes.toFloat()
                        } else {
                            0f
                        }
                        _downloadProgress.value = _downloadProgress.value + (item.id to fraction)
                    }
                    is DownloadState.Done -> {
                        _downloadProgress.value = _downloadProgress.value - item.id
                        refreshLibrary()
                    }
                    is DownloadState.Error -> {
                        _downloadProgress.value = _downloadProgress.value - item.id
                        _downloadError.value = state.message
                    }
                }
            }
        }
    }

    fun dismissDownloadError() {
        _downloadError.value = null
    }

    fun downloadsDirPath(): String = downloadRepo.downloadsDir().absolutePath
}

class OneViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OneViewModel::class.java)) {
            return OneViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
