package dev.one.launcher.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.one.launcher.data.CustomLink
import dev.one.launcher.data.CustomLinksStore
import dev.one.launcher.data.DownloadRepository
import dev.one.launcher.data.DownloadState
import dev.one.launcher.data.GitHubAsset
import dev.one.launcher.data.GitHubRelease
import dev.one.launcher.data.GitHubReleasesApi
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.MatchedFile
import dev.one.launcher.data.MatchedFilesStore
import dev.one.launcher.data.PortCatalogRepository
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.PortSettings
import dev.one.launcher.data.PortSettingsStore
import dev.one.launcher.data.PremiumizeAccount
import dev.one.launcher.data.PremiumizeApi
import dev.one.launcher.data.PremiumizeBreadcrumb
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.PremiumizeResult
import dev.one.launcher.data.ReleaseResult
import dev.one.launcher.data.SecureKeyStore
import dev.one.launcher.data.compareVersions
import dev.one.launcher.data.normalizeCustomLinkUrl
import dev.one.launcher.data.pickApkAsset
import dev.one.launcher.model.Port
import dev.one.launcher.model.PortCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PortStatus(
    val installedPackage: String? = null,
    val assignedFile: MatchedFile? = null,
    val downloadedFileName: String? = null,
    /** How the downloaded file relates to this port, if it is an archive. */
    val downloadedMatchKind: MatchKind? = null,
    /** For a confirmed zip: the matching ROM entry inside it. */
    val downloadedInnerEntry: String? = null
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

/** Latest-release fetch state for one port's detail page. */
sealed class ReleaseState {
    data object Loading : ReleaseState()
    data class Loaded(
        val release: GitHubRelease,
        /** The .apk to offer, or null when the release ships no Android APK. */
        val apk: GitHubAsset?,
        /** Installed versionName, or null when the port is not installed. */
        val installedVersion: String?
    ) : ReleaseState() {
        /** True when the release tag is newer than the installed version. */
        val updateAvailable: Boolean
            get() = installedVersion != null &&
                compareVersions(release.tag_name, installedVersion) > 0
    }
    data class Error(val message: String) : ReleaseState()
}

/** APK download/install state for one port's detail page. */
data class ApkInstallState(
    val downloading: Boolean = false,
    val progress: Float = 0f,
    /** APK file in Downloads/ONE, ready for the system installer. */
    val downloadedFile: File? = null,
    /** True when installs from unknown sources are not yet allowed for ONE. */
    val blockedByUnknownSources: Boolean = false,
    val error: String? = null
)

class OneViewModel(application: Application) : AndroidViewModel(application) {

    private val catalogRepo = PortCatalogRepository(application)
    private val secureKeyStore = SecureKeyStore(application)
    private val settingsStore = PortSettingsStore(application)
    private val matchedStore = MatchedFilesStore(application)
    private val downloadRepo = DownloadRepository(application)
    private val releasesApi = GitHubReleasesApi()
    private val customLinksStore = CustomLinksStore(application)

    // StateFlows must be declared before init: init calls refreshLibrary(),
    // and Kotlin initializes properties and init blocks in declaration order,
    // so a flow declared after init is still null when init runs (v1.0.2
    // launch crash: NPE writing _portStatuses from refreshLibrary).
    private val _portStatuses = MutableStateFlow<Map<String, PortStatus>>(emptyMap())
    val portStatuses: StateFlow<Map<String, PortStatus>> = _portStatuses.asStateFlow()

    private val _selectedPortId = MutableStateFlow<String?>(null)
    val selectedPortId: StateFlow<String?> = _selectedPortId.asStateFlow()

    private val _launchError = MutableStateFlow<String?>(null)
    val launchError: StateFlow<String?> = _launchError.asStateFlow()

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

    /** Assigned (picked) files per port, for the Settings matched-files list. */
    private val _assignedFiles = MutableStateFlow<Map<String, MatchedFile>>(emptyMap())
    val assignedFiles: StateFlow<Map<String, MatchedFile>> = _assignedFiles.asStateFlow()

    /**
     * Downloaded .zip files already inspected on this device:
     * zip file name -> (port id -> matching ROM entry inside the zip).
     * Lets the Cloud screen label a zip "contains .z64" once it has been
     * downloaded and confirmed, without re-opening the archive.
     */
    private val _confirmedArchives = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val confirmedArchives: StateFlow<Map<String, Map<String, String>>> =
        _confirmedArchives.asStateFlow()

    /** Latest-release state per port; cached for the session. */
    private val _releaseStates = MutableStateFlow<Map<String, ReleaseState>>(emptyMap())
    val releaseStates: StateFlow<Map<String, ReleaseState>> = _releaseStates.asStateFlow()

    /** APK download/install state per port. */
    private val _apkInstallStates = MutableStateFlow<Map<String, ApkInstallState>>(emptyMap())
    val apkInstallStates: StateFlow<Map<String, ApkInstallState>> =
        _apkInstallStates.asStateFlow()

    /** Extract result message per port, shown on the detail page. */
    private val _extractMessage = MutableStateFlow<Map<String, String>>(emptyMap())
    val extractMessage: StateFlow<Map<String, String>> = _extractMessage.asStateFlow()

    /** User-entered custom links (Settings), stored only on this device. */
    private val _customLinks = MutableStateFlow<List<CustomLink>>(emptyList())
    val customLinks: StateFlow<List<CustomLink>> = _customLinks.asStateFlow()

    /** Validation error from adding a custom link, if any. */
    private val _customLinkError = MutableStateFlow<String?>(null)
    val customLinkError: StateFlow<String?> = _customLinkError.asStateFlow()

    /** (folder id, display name), root first. */
    private val folderStack = mutableListOf<Pair<String?, String>>(null to "My cloud")

    /** Last non-empty cloud search query, so Refresh can re-run it. */
    private var lastSearchQuery: String = ""

    private val _folderTrail = MutableStateFlow<List<String>>(listOf("My cloud"))
    val folderTrail: StateFlow<List<String>> = _folderTrail.asStateFlow()

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
        // Keep the on-device custom links in state.
        viewModelScope.launch {
            customLinksStore.links.collect { _customLinks.value = it }
        }
        // Restore a saved Premiumize session, if the user linked one before.
        if (secureKeyStore.getApiKey() != null) {
            viewModelScope.launch { restoreSession() }
        }
    }

    // ---- Library ----

    fun selectPort(id: String?) {
        _selectedPortId.value = id
    }

    fun portById(id: String): Port? = ports.firstOrNull { it.id == id }

    fun refreshLibrary() {
        val pm = getApplication<Application>().packageManager
        val downloadedFiles = downloadRepo.listDownloadedFiles()
        val downloadedNames = downloadedFiles.map { it.name }
        val assigned = matchedStore.all()
        _assignedFiles.value = assigned
        // Inspect each downloaded .zip once, against every port it could
        // match by name. A zip is only confirmed when it contains an entry
        // that matches the port directly; .7z/.rar stay provisional.
        val confirmed = mutableMapOf<String, Map<String, String>>()
        for (file in downloadedFiles) {
            if (!file.name.lowercase().endsWith(".zip")) continue
            val perPort = mutableMapOf<String, String>()
            for (port in ports) {
                if (PortMatcher.matchKind(file.name, port) == MatchKind.ARCHIVE_PROVISIONAL) {
                    PortMatcher.inspectZip(file, port)?.let { perPort[port.id] = it }
                }
            }
            if (perPort.isNotEmpty()) confirmed[file.name] = perPort
        }
        _confirmedArchives.value = confirmed
        val statuses = ports.associate { port ->
            val installed = port.packageNames.firstOrNull { pkg ->
                try {
                    pm.getLaunchIntentForPackage(pkg) != null
                } catch (e: Exception) {
                    false
                }
            }
            // A direct ROM file wins; otherwise the first downloaded
            // archive whose name matches this port.
            val directMatch = downloadedNames.firstOrNull { PortMatcher.matches(it, port) }
            var matchName: String? = directMatch
            var matchKind: MatchKind? =
                if (directMatch != null) MatchKind.DIRECT else null
            var innerEntry: String? = null
            if (directMatch == null) {
                for (file in downloadedFiles) {
                    if (PortMatcher.matchKind(file.name, port) == MatchKind.ARCHIVE_PROVISIONAL) {
                        matchName = file.name
                        val entry = confirmed[file.name]?.get(port.id)
                        if (entry != null) {
                            matchKind = MatchKind.ARCHIVE_CONFIRMED
                            innerEntry = entry
                        } else {
                            matchKind = MatchKind.ARCHIVE_PROVISIONAL
                        }
                        break
                    }
                }
            }
            port.id to PortStatus(
                installedPackage = installed,
                assignedFile = assigned[port.id],
                downloadedFileName = matchName,
                downloadedMatchKind = matchKind,
                downloadedInnerEntry = innerEntry
            )
        }
        _portStatuses.value = statuses
    }

    /**
     * Extract the single confirmed ROM entry out of a downloaded .zip into
     * Downloads/ONE, then re-scan so the extracted ROM becomes the direct
     * match. The archive itself is left untouched.
     */
    fun extractMatchedArchive(portId: String) {
        val status = _portStatuses.value[portId] ?: return
        val fileName = status.downloadedFileName ?: return
        val entry = status.downloadedInnerEntry ?: return
        val zip = File(downloadRepo.downloadsDir(), fileName)
        viewModelScope.launch(Dispatchers.IO) {
            val extracted =
                PortMatcher.extractZipEntry(zip, entry, downloadRepo.downloadsDir())
            withContext(Dispatchers.Main) {
                if (extracted != null) {
                    _extractMessage.value = _extractMessage.value +
                        (portId to "Extracted ${extracted.name} to Downloads/ONE.")
                    refreshLibrary()
                } else {
                    _extractMessage.value = _extractMessage.value +
                        (portId to "Could not extract $entry from $fileName.")
                }
            }
        }
    }

    fun dismissExtractMessage(portId: String) {
        _extractMessage.value = _extractMessage.value - portId
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
        lastSearchQuery = trimmed
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

    /** Re-run the current cloud view: the active search, or the open folder. */
    fun refreshCloud() {
        if (_searchResults.value != null && lastSearchQuery.isNotBlank()) {
            searchCloud(lastSearchQuery)
        } else {
            browseFolder(folderStack.last().first)
        }
    }

    // ---- GitHub releases (update manager, detail page only) ----

    /**
     * Fetch the latest release for a port. Results are cached per port for
     * the session; pass force = true for "Check for updates". Fetches
     * metadata only — the APK downloads only when the user taps Download.
     */
    fun fetchRelease(portId: String, force: Boolean = false) {
        val port = portById(portId) ?: return
        if (!force && _releaseStates.value[portId] != null) return
        // A forced check re-reads what is installed first, so "Up to date"
        // is true right after an install from the system installer.
        if (force) refreshLibrary()
        _releaseStates.value = _releaseStates.value + (portId to ReleaseState.Loading)
        viewModelScope.launch {
            when (val result = releasesApi.latestRelease(port.repo)) {
                is ReleaseResult.Success -> {
                    val installedPkg = _portStatuses.value[portId]?.installedPackage
                    val installedVersion = installedPkg?.let { installedVersion(it) }
                    _releaseStates.value = _releaseStates.value + (
                        portId to ReleaseState.Loaded(
                            release = result.release,
                            apk = pickApkAsset(result.release),
                            installedVersion = installedVersion
                        )
                    )
                }
                is ReleaseResult.Failure -> {
                    _releaseStates.value = _releaseStates.value +
                        (portId to ReleaseState.Error(result.message))
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun installedVersion(packageName: String): String? {
        return try {
            getApplication<Application>().packageManager
                .getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    /** Download the picked release APK into Downloads/ONE. Install is a
     *  separate, explicit step — nothing installs silently. */
    fun downloadApk(portId: String) {
        val loaded = _releaseStates.value[portId] as? ReleaseState.Loaded ?: return
        val apk = loaded.apk ?: return
        if (apk.browser_download_url.isBlank()) return
        _apkInstallStates.value = _apkInstallStates.value +
            (portId to ApkInstallState(downloading = true))
        viewModelScope.launch {
            downloadRepo.download(apk.browser_download_url, apk.name).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        val fraction = if (state.totalBytes > 0) {
                            state.bytesRead.toFloat() / state.totalBytes.toFloat()
                        } else {
                            0f
                        }
                        _apkInstallStates.value = _apkInstallStates.value +
                            (portId to ApkInstallState(
                                downloading = true, progress = fraction
                            ))
                    }
                    is DownloadState.Done -> {
                        _apkInstallStates.value = _apkInstallStates.value +
                            (portId to ApkInstallState(downloadedFile = state.file))
                    }
                    is DownloadState.Error -> {
                        _apkInstallStates.value = _apkInstallStates.value +
                            (portId to ApkInstallState(error = state.message))
                    }
                }
            }
        }
    }

    /**
     * Hand the downloaded APK to the system installer. If ONE is not yet
     * allowed to install unknown apps, mark the state blocked instead —
     * the UI explains and offers the system settings screen.
     */
    fun installApk(portId: String) {
        val app = getApplication<Application>()
        val current = _apkInstallStates.value[portId] ?: return
        val file = current.downloadedFile ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            _apkInstallStates.value = _apkInstallStates.value +
                (portId to current.copy(blockedByUnknownSources = true))
            return
        }
        val uri = FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
    }

    /** Open the system screen where the user allows ONE to install apps. */
    fun openUnknownSourcesSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${app.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        app.startActivity(intent)
    }

    fun dismissApkError(portId: String) {
        _apkInstallStates.value = _apkInstallStates.value - portId
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

    // ---- Custom links (Settings; browser-only shortcuts) ----

    /**
     * Add a user-entered shortcut. Returns true when it was accepted.
     * The link is stored on this device only; tapping it later just opens
     * it in the external browser — ONE never downloads from it.
     */
    fun addCustomLink(name: String, rawUrl: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _customLinkError.value = "Give the link a name first."
            return false
        }
        val url = normalizeCustomLinkUrl(rawUrl)
        if (url == null) {
            _customLinkError.value =
                "That URL does not look right. Use an http:// or https:// link."
            return false
        }
        _customLinkError.value = null
        viewModelScope.launch { customLinksStore.add(CustomLink(cleanName, url)) }
        return true
    }

    fun removeCustomLink(link: CustomLink) {
        viewModelScope.launch { customLinksStore.remove(link) }
    }

    fun dismissCustomLinkError() {
        _customLinkError.value = null
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
