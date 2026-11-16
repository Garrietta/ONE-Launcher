package dev.one.launcher.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.one.launcher.data.CustomLink
import dev.one.launcher.data.CustomLinksStore
import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.DownloadRepository
import dev.one.launcher.data.DownloadState
import dev.one.launcher.data.GitHubAsset
import dev.one.launcher.data.GitHubRelease
import dev.one.launcher.data.GitHubReleasesApi
import dev.one.launcher.data.LocalFolder
import dev.one.launcher.data.LocalFolderScanner
import dev.one.launcher.data.LocalFolderStore
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
import dev.one.launcher.data.RecompaCatalogApi
import dev.one.launcher.data.RecompaIndex
import dev.one.launcher.data.RecompaPlatform
import dev.one.launcher.data.ReleaseResult
import dev.one.launcher.data.RemoteDirectoryApi
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.RemoteFolderCredentials
import dev.one.launcher.data.RemoteFolderSource
import dev.one.launcher.data.RemoteFoldersStore
import dev.one.launcher.data.RemoteListingResult
import dev.one.launcher.data.SecureKeyStore
import dev.one.launcher.data.compareVersions
import dev.one.launcher.data.mapCatalogPort
import dev.one.launcher.data.mergeCatalogs
import dev.one.launcher.data.normalizeCustomLinkUrl
import dev.one.launcher.data.normalizeRemoteFolderUrl
import dev.one.launcher.data.pickApkAsset
import dev.one.launcher.data.resolveDetected
import dev.one.launcher.data.sha256Matches
import dev.one.launcher.model.Port
import dev.one.launcher.model.PortCatalog
import dev.one.launcher.model.PortFork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import java.io.File
import java.net.URLConnection

data class PortStatus(
    val installedPackage: String? = null,
    val assignedFile: MatchedFile? = null,
    val downloadedFileName: String? = null,
    /** How the downloaded file relates to this port, if it is an archive. */
    val downloadedMatchKind: MatchKind? = null,
    /** For a confirmed zip: the matching ROM entry inside it. */
    val downloadedInnerEntry: String? = null,
    /**
     * First file found for this port in the user's local game-files
     * folder, if any. Detection only — it is not a user assignment, and
     * clearing assignments never clears it.
     */
    val detectedFile: DetectedFile? = null
) {
    val isInstalled: Boolean get() = installedPackage != null
    val hasFile: Boolean get() =
        assignedFile != null || downloadedFileName != null || detectedFile != null
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
        val installedVersion: String?,
        /** Catalog SHA-256 of the APK, or null when unknown (GitHub fallback). */
        val expectedSha256: String? = null
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
    val error: String? = null,
    /** True when the downloaded APK matched the catalog SHA-256. */
    val shaVerified: Boolean = false
)

/** Recompa catalog load state. */
sealed class CatalogState {
    data object Loading : CatalogState()
    data class Ready(
        val portCount: Int,
        val generatedAt: String,
        val fromCache: Boolean,
        /** Curated port ids with no catalog match — reported, never guessed. */
        val unmatchedCuratedIds: List<String>,
        /** Catalog port ids whose assets[] is missing — file matching off. */
        val portsWithoutFileInfo: List<String>
    ) : CatalogState()
    data class Unavailable(val message: String) : CatalogState()
}

/** One step in the open remote-folder browser: a folder URL + display name. */
data class RemoteTrailItem(val url: String, val name: String)

/**
 * The open remote-folder browser's state. Isolated from the Library by
 * design: remote entries are never merged into the port list, its
 * search, or any port matching. A downloaded file only becomes visible
 * to the app once it lands on disk, through the local-folder scan.
 */
sealed class RemoteBrowserState {
    data object Idle : RemoteBrowserState()
    data object Loading : RemoteBrowserState()
    data class Loaded(val entries: List<RemoteEntry>, val trail: List<RemoteTrailItem>) : RemoteBrowserState()
    data class Error(val message: String) : RemoteBrowserState()
}

class OneViewModel(application: Application) : AndroidViewModel(application) {

    private val catalogRepo = PortCatalogRepository(application)
    private val secureKeyStore = SecureKeyStore(application)
    private val settingsStore = PortSettingsStore(application)
    private val matchedStore = MatchedFilesStore(application)
    private val downloadRepo = DownloadRepository(application)
    private val releasesApi = GitHubReleasesApi()
    private val customLinksStore = CustomLinksStore(application)
    private val localFolderStore = LocalFolderStore(application)

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

    /**
     * Flat list of every file in the user's Premiumize cloud
     * (/item/listall), fetched once per connected session. Detail pages
     * and Library badges match against it locally with PortMatcher, so
     * one call covers every port.
     */
    private val _cloudAllFiles = MutableStateFlow<List<PremiumizeItem>>(emptyList())
    val cloudAllFiles: StateFlow<List<PremiumizeItem>> = _cloudAllFiles.asStateFlow()

    private val _cloudAllLoading = MutableStateFlow(false)
    val cloudAllLoading: StateFlow<Boolean> = _cloudAllLoading.asStateFlow()

    /** True once a listAll attempt has finished (success or failure). */
    private val _cloudAllLoaded = MutableStateFlow(false)
    val cloudAllLoaded: StateFlow<Boolean> = _cloudAllLoaded.asStateFlow()

    /**
     * Per-port fallback when listAll fails: search results for that
     * port's first filename hint, keyed by port id.
     */
    private val _cloudFallback = MutableStateFlow<Map<String, List<PremiumizeItem>>>(emptyMap())
    val cloudFallback: StateFlow<Map<String, List<PremiumizeItem>>> =
        _cloudFallback.asStateFlow()

    /** The user's chosen local game-files folder, if one is set. */
    private val _localFolder = MutableStateFlow<LocalFolder?>(null)
    val localFolder: StateFlow<LocalFolder?> = _localFolder.asStateFlow()

    /**
     * True when a folder is set but could not be read on the last scan
     * (permission lost / folder gone). The cached pairs stay visible
     * while this is true — they are stale, never dropped.
     */
    private val _localFolderUnavailable = MutableStateFlow(false)
    val localFolderUnavailable: StateFlow<Boolean> = _localFolderUnavailable.asStateFlow()

    /** Detected file/port pairs from the local folder (port id -> files). */
    private val _detectedLocalFiles =
        MutableStateFlow<Map<String, List<DetectedFile>>>(emptyMap())
    val detectedLocalFiles: StateFlow<Map<String, List<DetectedFile>>> =
        _detectedLocalFiles.asStateFlow()

    /** Copy-to-Downloads result message per port, shown on the detail page. */
    private val _copyMessage = MutableStateFlow<Map<String, String>>(emptyMap())
    val copyMessage: StateFlow<Map<String, String>> = _copyMessage.asStateFlow()

    /** Guards the background folder scan so refreshes cannot stack scans. */
    private var localScanRunning = false

    /** (folder id, display name), root first. */
    private val folderStack = mutableListOf<Pair<String?, String>>(null to "My cloud")

    /** Last non-empty cloud search query, so Refresh can re-run it. */
    private var lastSearchQuery: String = ""

    private val _folderTrail = MutableStateFlow<List<String>>(listOf("My cloud"))
    val folderTrail: StateFlow<List<String>> = _folderTrail.asStateFlow()

    /**
     * The Library's port list: curated ports.json first, then merged
     * with the Recompa catalog (curated stays canonical). `ports` is a
     * plain read of this flow for ViewModel-internal use; composables
     * collect [portsFlow] so the list updates when the catalog lands.
     */
    private val _ports = MutableStateFlow<List<Port>>(emptyList())
    val portsFlow: StateFlow<List<Port>> = _ports.asStateFlow()

    /** Recompa catalog load state (Loading until the first merge/failure). */
    private val _catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalogState: StateFlow<CatalogState> = _catalogState.asStateFlow()

    /** Platforms from the catalog, for the Library platform filter. */
    private val _catalogPlatforms = MutableStateFlow<List<RecompaPlatform>>(emptyList())
    val catalogPlatforms: StateFlow<List<RecompaPlatform>> =
        _catalogPlatforms.asStateFlow()

    /** Selected fork repo per port id, for ports with more than one fork. */
    private val _selectedForks = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedForks: StateFlow<Map<String, String>> = _selectedForks.asStateFlow()

    /** User-configured remote folder sources (Settings), stored only on this device. */
    private val _remoteFolders = MutableStateFlow<List<RemoteFolderSource>>(emptyList())
    val remoteFolders: StateFlow<List<RemoteFolderSource>> = _remoteFolders.asStateFlow()

    /** Validation error from adding a remote folder source, if any. */
    private val _remoteFolderError = MutableStateFlow<String?>(null)
    val remoteFolderError: StateFlow<String?> = _remoteFolderError.asStateFlow()

    /** The open remote-folder browser: one source's directory listing. */
    private val _remoteBrowser = MutableStateFlow<RemoteBrowserState>(RemoteBrowserState.Idle)
    val remoteBrowser: StateFlow<RemoteBrowserState> = _remoteBrowser.asStateFlow()

    /** Download progress for remote files, keyed by entry URL (0f..1f). */
    private val _remoteDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val remoteDownloadProgress: StateFlow<Map<String, Float>> =
        _remoteDownloadProgress.asStateFlow()

    /** Finished remote downloads: entry URL -> where the file landed. */
    private val _remoteDownloaded = MutableStateFlow<Map<String, String>>(emptyMap())
    val remoteDownloaded: StateFlow<Map<String, String>> = _remoteDownloaded.asStateFlow()

    /** Error from the last remote download, if any. */
    private val _remoteDownloadError = MutableStateFlow<String?>(null)
    val remoteDownloadError: StateFlow<String?> = _remoteDownloadError.asStateFlow()

    private val recompaApi = RecompaCatalogApi(application)
    private val remoteFoldersStore = RemoteFoldersStore(application)
    private val remoteCredentials = RemoteFolderCredentials(application)
    private val remoteDirectoryApi = RemoteDirectoryApi()

    /** The source currently open in the remote browser, if any. */
    private var remoteSource: RemoteFolderSource? = null

    /** Folder stack for the open remote browser, root first. */
    private var remoteTrail: List<RemoteTrailItem> = emptyList()

    val catalogNote: String
    val ports: List<Port> get() = _ports.value

    /** The curated ports.json entries, kept for every catalog re-merge. */
    private lateinit var curatedPorts: List<Port>

    init {
        val catalog = try {
            catalogRepo.load()
        } catch (e: Exception) {
            PortCatalog()
        }
        catalogNote = catalog.catalogNote
        curatedPorts = catalog.ports
        _ports.value = catalog.ports
        refreshLibrary()
        // Recompa catalog: serve the cache immediately, then refresh in
        // the background (stale-while-revalidate). Failure with no cache
        // leaves the curated list in place with a plain note.
        viewModelScope.launch { loadCatalog() }
        // Keep the on-device custom links in state.
        viewModelScope.launch {
            customLinksStore.links.collect { _customLinks.value = it }
        }
        // Keep the user-configured remote folder sources in state.
        viewModelScope.launch {
            remoteFoldersStore.sources.collect { _remoteFolders.value = it }
        }
        // Restore a saved Premiumize session, if the user linked one before.
        if (secureKeyStore.getApiKey() != null) {
            viewModelScope.launch { restoreSession() }
        }
        // Local game-files folder: show the cached pairs immediately so
        // badges render on a cold start, then re-scan in the background
        // and replace them with fresh results.
        viewModelScope.launch {
            val folder = localFolderStore.loadFolder()
            _localFolder.value = folder
            val cached = localFolderStore.loadCachedDetected()
            _detectedLocalFiles.value =
                resolveDetected(cached, fresh = null, folderAvailable = folder != null)
            rebuildStatuses()
            if (folder != null) scanLocalFolder()
        }
    }

    // ---- Library ----

    fun selectPort(id: String?) {
        _selectedPortId.value = id
    }

    fun portById(id: String): Port? = ports.firstOrNull { it.id == id }

    fun refreshLibrary() {
        rebuildStatuses()
        // Re-scan the local folder in the background when one is set, so
        // a file the user just added shows up without re-picking it.
        scanLocalFolder()
    }

    // ---- Recompa catalog ----

    /** Merge one fetched index into the Library. Curated stays canonical. */
    private fun applyCatalog(index: RecompaIndex, fromCache: Boolean) {
        val platformNames = index.platforms.associate { it.id to it.name }
        val catalogPorts = index.ports.map { mapCatalogPort(it, platformNames[it.original_platform].orEmpty()) }
        val merge = mergeCatalogs(curatedPorts, catalogPorts)
        _ports.value = merge.merged
        _catalogPlatforms.value = index.platforms
        _catalogState.value = CatalogState.Ready(
            portCount = merge.merged.size,
            generatedAt = index.generated_at,
            fromCache = fromCache,
            unmatchedCuratedIds = merge.unmatchedCuratedIds,
            portsWithoutFileInfo = merge.portsWithoutFileInfo
        )
        rebuildStatuses()
        scanLocalFolder()
    }

    /** Cached index first (cold start), then a background network refresh. */
    private suspend fun loadCatalog() {
        val cached = withContext(Dispatchers.IO) { recompaApi.loadCached() }
        if (cached != null) applyCatalog(cached, fromCache = true)
        val fresh = recompaApi.refresh()
        if (fresh != null) {
            applyCatalog(fresh, fromCache = false)
        } else if (cached == null) {
            _catalogState.value = CatalogState.Unavailable(
                "Recompa catalog unavailable — showing your curated ports."
            )
        }
    }

    /**
     * Manual catalog refresh from the Library. Fetches the index; on
     * failure the last cached index (or the curated list) stays in place.
     */
    fun refreshCatalog() {
        viewModelScope.launch {
            _catalogState.value = CatalogState.Loading
            val fresh = recompaApi.refresh()
            if (fresh != null) {
                applyCatalog(fresh, fromCache = false)
            } else {
                val cached = withContext(Dispatchers.IO) { recompaApi.loadCached() }
                if (cached != null) {
                    applyCatalog(cached, fromCache = true)
                } else {
                    _catalogState.value = CatalogState.Unavailable(
                        "Recompa catalog unavailable — showing your curated ports."
                    )
                }
            }
            refreshLibrary()
        }
    }

    /**
     * The fork driving a port's "Latest release" block: the user's pick,
     * else the fork matching the curated repo, else the first fork.
     * Null when the port has no catalog forks.
     */
    fun selectedFork(port: Port): PortFork? {
        if (port.forks.isEmpty()) return null
        val picked = _selectedForks.value[port.id]
        if (picked != null) {
            port.forks.firstOrNull { it.repo == picked }?.let { return it }
        }
        port.forks.firstOrNull { it.repo == port.repo }?.let { return it }
        return port.forks.first()
    }

    /** Pick a fork for a port with more than one; reloads its release. */
    fun selectFork(portId: String, repo: String) {
        _selectedForks.value = _selectedForks.value + (portId to repo)
        fetchRelease(portId, force = true)
    }

    /** Rebuilds every port's status from the current on-device state. */
    fun rebuildStatuses() {
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
            // archive whose name matches this port, or a name-only match
            // for a port the catalog lists no formats for.
            val directMatch = downloadedNames.firstOrNull { PortMatcher.matches(it, port) }
            var matchName: String? = directMatch
            var matchKind: MatchKind? =
                if (directMatch != null) MatchKind.DIRECT else null
            var innerEntry: String? = null
            if (directMatch == null) {
                for (file in downloadedFiles) {
                    val kind = PortMatcher.matchKind(file.name, port)
                    if (kind == MatchKind.ARCHIVE_PROVISIONAL) {
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
                    if (kind == MatchKind.NAME_ONLY) {
                        matchName = file.name
                        matchKind = MatchKind.NAME_ONLY
                        break
                    }
                }
            }
            port.id to PortStatus(
                installedPackage = installed,
                assignedFile = assigned[port.id],
                downloadedFileName = matchName,
                downloadedMatchKind = matchKind,
                downloadedInnerEntry = innerEntry,
                detectedFile = _detectedLocalFiles.value[port.id]?.firstOrNull()
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

    // ---- Local game-files folder (emulator-style ROMs folder) ----

    fun setLocalFolder(uri: Uri, displayName: String) {
        try {
            // Persist read AND write: remote downloads are moved into
            // this folder after they land, which needs the write grant.
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Persisting the permission is best-effort; the scan below
            // reports the folder unavailable if it cannot be read.
        }
        val folder = LocalFolder(uri = uri.toString(), displayName = displayName)
        viewModelScope.launch {
            localFolderStore.saveFolder(folder)
            _localFolder.value = folder
            _localFolderUnavailable.value = false
            scanLocalFolder()
        }
    }

    /**
     * Clears the folder choice and its cached pairs. Detection only —
     * user assignments are untouched by this.
     */
    fun clearLocalFolder() {
        viewModelScope.launch {
            localFolderStore.saveFolder(null)
            localFolderStore.clearDetected()
            _localFolder.value = null
            _localFolderUnavailable.value = false
            _detectedLocalFiles.value = emptyMap()
            rebuildStatuses()
        }
    }

    /**
     * Background re-scan of the chosen folder. A null scan result means
     * the folder could not be read: the cached pairs stay visible and
     * the folder is flagged unavailable instead of being emptied.
     */
    fun scanLocalFolder() {
        val folder = _localFolder.value ?: return
        if (localScanRunning) return
        localScanRunning = true
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = LocalFolderScanner.scan(
                getApplication<Application>(),
                Uri.parse(folder.uri),
                ports
            )
            withContext(Dispatchers.Main) {
                localScanRunning = false
                if (fresh == null) {
                    _localFolderUnavailable.value = true
                    _detectedLocalFiles.value = resolveDetected(
                        cached = _detectedLocalFiles.value,
                        fresh = null,
                        folderAvailable = false
                    )
                } else {
                    _localFolderUnavailable.value = false
                    _detectedLocalFiles.value = resolveDetected(
                        cached = _detectedLocalFiles.value,
                        fresh = fresh,
                        folderAvailable = true
                    )
                    viewModelScope.launch { localFolderStore.saveDetected(fresh) }
                    rebuildStatuses()
                }
            }
        }
    }

    /**
     * Copy a file found in the local folder into Downloads/ONE, so the
     * existing inspect/extract path applies to it. The copy moves the
     * user's own file between two places on their own device.
     */
    fun copyDetectedToDownloads(portId: String, detected: DetectedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                val target = File(downloadRepo.downloadsDir(), detected.name)
                app.contentResolver.openInputStream(Uri.parse(detected.uri))?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Could not open ${detected.name}.")
                withContext(Dispatchers.Main) {
                    _copyMessage.value = _copyMessage.value +
                        (portId to "Copied ${detected.name} to Downloads/ONE.")
                    refreshLibrary()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _copyMessage.value = _copyMessage.value +
                        (portId to "Could not copy ${detected.name}: ${e.message ?: "unknown error"}")
                }
            }
        }
    }

    fun dismissCopyMessage(portId: String) {
        _copyMessage.value = _copyMessage.value - portId
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
                    refreshCloudFiles()
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
                        refreshCloudFiles()
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
        _cloudAllFiles.value = emptyList()
        _cloudAllLoaded.value = false
        _cloudFallback.value = emptyMap()
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

    /**
     * Fetch the flat list of every file in the user's own Premiumize
     * cloud (/item/listall), once per connected session. Detail pages
     * and Library badges match against it locally, so one call covers
     * every port. Folders are dropped — only files can match a port.
     */
    fun refreshCloudFiles() {
        val api = api() ?: return
        if (_cloudAllLoading.value) return
        _cloudAllLoading.value = true
        viewModelScope.launch {
            when (val result = api.listAll()) {
                is PremiumizeResult.Success -> {
                    _cloudAllFiles.value =
                        result.value.files.filter { it.type != "folder" }
                    _cloudFallback.value = emptyMap()
                }
                is PremiumizeResult.Failure -> {
                    _cloudAllFiles.value = emptyList()
                }
            }
            _cloudAllLoaded.value = true
            _cloudAllLoading.value = false
        }
    }

    /**
     * Make sure this port's cloud files are available for its detail
     * page: fetch the flat list if it has not been fetched yet, or fall
     * back to a per-port own-cloud search when listAll failed (or came
     * back empty) for this port.
     */
    fun ensureCloudFilesFor(port: Port) {
        if (!isCloudConnected) return
        if (!_cloudAllLoaded.value) {
            refreshCloudFiles()
            return
        }
        if (_cloudAllFiles.value.isEmpty() && _cloudFallback.value[port.id] == null) {
            val api = api() ?: return
            viewModelScope.launch { searchCloudFallback(port, api) }
        }
    }

    private suspend fun searchCloudFallback(port: Port, api: PremiumizeApi) {
        val query = port.fileNameHints.firstOrNull() ?: port.title
        when (val result = api.searchOwnCloud(query)) {
            is PremiumizeResult.Success -> {
                _cloudFallback.value = _cloudFallback.value +
                    (port.id to result.value.content.filter { it.type != "folder" })
            }
            is PremiumizeResult.Failure -> {
                // Leave the fallback empty: the detail page shows the
                // "no file in your cloud yet" line rather than an error.
            }
        }
    }

    // ---- GitHub releases (update manager, detail page only) ----

    /**
     * Fetch the latest release for a port. Results are cached per port for
     * the session; pass force = true for "Check for updates" or a fork
     * change. The catalog's release data is preferred (tag + asset URL +
     * size + SHA-256, no extra network call); the GitHub releases/latest
     * API is only a fallback when the selected fork has no release asset
     * in the catalog. Fetches metadata only — the APK downloads only when
     * the user taps Download.
     */
    fun fetchRelease(portId: String, force: Boolean = false) {
        val port = portById(portId) ?: return
        if (!force && _releaseStates.value[portId] != null) return
        // A forced check re-reads what is installed first, so "Up to date"
        // is true right after an install from the system installer.
        if (force) refreshLibrary()
        val installedPkg = _portStatuses.value[portId]?.installedPackage
        val installedVersion = installedPkg?.let { installedVersion(it) }
        val fork = selectedFork(port)
        if (fork != null && fork.latestAssetUrl.isNotBlank()) {
            val asset = GitHubAsset(
                name = fork.latestAssetName,
                browser_download_url = fork.latestAssetUrl,
                size = fork.latestAssetSize
            )
            _releaseStates.value = _releaseStates.value + (
                portId to ReleaseState.Loaded(
                    release = GitHubRelease(
                        tag_name = fork.latestTag,
                        name = fork.latestTag,
                        html_url = "https://github.com/${fork.repo}/releases",
                        assets = listOf(asset)
                    ),
                    apk = asset,
                    installedVersion = installedVersion,
                    expectedSha256 = fork.latestAssetSha256.takeIf { it.isNotBlank() }
                )
            )
            return
        }
        _releaseStates.value = _releaseStates.value + (portId to ReleaseState.Loading)
        viewModelScope.launch {
            when (val result = releasesApi.latestRelease(port.repo)) {
                is ReleaseResult.Success -> {
                    val installedPkgNow = _portStatuses.value[portId]?.installedPackage
                    val installedVersionNow = installedPkgNow?.let { installedVersion(it) }
                    _releaseStates.value = _releaseStates.value + (
                        portId to ReleaseState.Loaded(
                            release = result.release,
                            apk = pickApkAsset(result.release),
                            installedVersion = installedVersionNow
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
     *  separate, explicit step — nothing installs silently. When the
     *  catalog gave a SHA-256 for the asset, the file is hashed after the
     *  download and only offered for install when it matches; on a
     *  mismatch the file is deleted and the state carries a plain error.
     *  (Signer verification is a follow-up, not this round.) */
    fun downloadApk(portId: String) {
        val loaded = _releaseStates.value[portId] as? ReleaseState.Loaded ?: return
        val apk = loaded.apk ?: return
        if (apk.browser_download_url.isBlank()) return
        val expectedSha = loaded.expectedSha256
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
                        if (!expectedSha.isNullOrBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                val ok = sha256Matches(state.file, expectedSha)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        _apkInstallStates.value = _apkInstallStates.value +
                                            (portId to ApkInstallState(
                                                downloadedFile = state.file,
                                                shaVerified = true
                                            ))
                                    } else {
                                        state.file.delete()
                                        _apkInstallStates.value = _apkInstallStates.value +
                                            (portId to ApkInstallState(
                                                error = "Download did not match the catalog " +
                                                    "checksum — not installing."
                                            ))
                                    }
                                }
                            }
                        } else {
                            _apkInstallStates.value = _apkInstallStates.value +
                                (portId to ApkInstallState(downloadedFile = state.file))
                        }
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

    // ---- Remote folders (Settings; isolated generic directory browser) ----

    /**
     * Add a user-configured remote folder source. Returns true when it
     * was accepted. The source is stored on this device only; its
     * entries are never merged into the Library, its search, or any
     * port matching — the browser is its own screen.
     */
    fun addRemoteFolder(name: String, rawUrl: String, username: String, password: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _remoteFolderError.value = "Give the folder a name first."
            return false
        }
        val url = normalizeRemoteFolderUrl(rawUrl)
        if (url == null) {
            _remoteFolderError.value =
                "That URL does not look right. Use an http:// or https:// link."
            return false
        }
        val source = RemoteFolderSource(
            id = java.util.UUID.randomUUID().toString(),
            name = cleanName,
            url = url
        )
        _remoteFolderError.value = null
        viewModelScope.launch {
            if (username.isNotBlank()) {
                remoteCredentials.save(source.id, username.trim(), password)
            }
            remoteFoldersStore.add(source)
        }
        return true
    }

    fun removeRemoteFolder(source: RemoteFolderSource) {
        viewModelScope.launch {
            remoteCredentials.clear(source.id)
            remoteFoldersStore.remove(source)
            if (remoteSource?.id == source.id) {
                remoteSource = null
                remoteTrail = emptyList()
                _remoteBrowser.value = RemoteBrowserState.Idle
            }
        }
    }

    fun dismissRemoteFolderError() {
        _remoteFolderError.value = null
    }

    /** Open a remote folder source in its own browser screen. */
    fun openRemoteFolder(sourceId: String) {
        val source = _remoteFolders.value.firstOrNull { it.id == sourceId } ?: return
        remoteSource = source
        remoteTrail = listOf(RemoteTrailItem(source.url, source.name))
        fetchRemoteUrl(source.url)
    }

    /** Open a subfolder inside the open remote browser. */
    fun navigateRemoteEntry(entry: RemoteEntry) {
        if (!entry.isFolder) return
        remoteTrail = remoteTrail + RemoteTrailItem(entry.url, entry.name)
        fetchRemoteUrl(entry.url)
    }

    /** Jump to a breadcrumb in the open remote browser. */
    fun navigateRemoteTrail(index: Int) {
        val target = index.coerceIn(0, remoteTrail.lastIndex)
        remoteTrail = remoteTrail.take(target + 1)
        fetchRemoteUrl(remoteTrail[target].url)
    }

    /**
     * Back from the browser: pop one folder level. Returns false when
     * already at the source root, so the caller can leave the screen.
     */
    fun navigateRemoteBack(): Boolean {
        if (remoteTrail.size <= 1) return false
        remoteTrail = remoteTrail.dropLast(1)
        fetchRemoteUrl(remoteTrail.last().url)
        return true
    }

    /** Re-fetch the folder the browser is showing. */
    fun refreshRemoteBrowser() {
        val current = remoteTrail.lastOrNull() ?: return
        fetchRemoteUrl(current.url)
    }

    private fun fetchRemoteUrl(url: String) {
        val source = remoteSource ?: return
        _remoteBrowser.value = RemoteBrowserState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val creds = remoteCredentials.load(source.id)
            val result = remoteDirectoryApi.fetch(url, creds?.first, creds?.second)
            withContext(Dispatchers.Main) {
                _remoteBrowser.value = when (result) {
                    is RemoteListingResult.Listing ->
                        RemoteBrowserState.Loaded(result.entries, remoteTrail)
                    is RemoteListingResult.Error ->
                        RemoteBrowserState.Error(result.message)
                }
            }
        }
    }

    /**
     * Download a remote file with the system DownloadManager into
     * Downloads/ONE, then move it into the configured local game-files
     * folder when one is set (DownloadManager cannot write into a SAF
     * tree directly, so the move is the step that puts it there). When
     * the move is not possible the file stays in Downloads/ONE, where
     * the existing downloaded-file matching already sees it. Either
     * way refreshLibrary() runs afterwards, so the existing local-folder
     * prescan pairs the file with its port — no new matching logic.
     */
    fun downloadRemoteFile(entry: RemoteEntry) {
        val source = remoteSource ?: return
        if (entry.isFolder) return
        val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fileName.isEmpty() || fileName == "." || fileName == "..") {
            _remoteDownloadError.value = "That file name cannot be saved on this device."
            return
        }
        _remoteDownloadError.value = null
        val app = getApplication<Application>()
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(entry.url)).apply {
            setTitle(fileName)
            setDescription("ONE remote folder")
            setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            setDestinationInExternalFilesDir(
                app, Environment.DIRECTORY_DOWNLOADS, "ONE/$fileName"
            )
            val creds = remoteCredentials.load(source.id)
            if (creds != null) {
                addRequestHeader("Authorization", Credentials.basic(creds.first, creds.second))
            }
        }
        val downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            _remoteDownloadError.value =
                "Could not start the download: ${e.message ?: "unknown error"}"
            return
        }
        _remoteDownloadProgress.value = _remoteDownloadProgress.value + (entry.url to 0f)
        viewModelScope.launch(Dispatchers.IO) {
            var done = false
            while (!done) {
                delay(500)
                val cursor = try {
                    dm.query(DownloadManager.Query().setFilterById(downloadId))
                } catch (e: Exception) {
                    null
                }
                if (cursor == null) {
                    done = true
                    continue
                }
                cursor.use {
                    if (!it.moveToFirst()) {
                        done = true
                        return@use
                    }
                    val status = it.getInt(
                        it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    val soFar = it.getLong(
                        it.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                        )
                    )
                    val total = it.getLong(
                        it.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                        )
                    )
                    val fraction = if (total > 0) {
                        (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    withContext(Dispatchers.Main) {
                        _remoteDownloadProgress.value =
                            _remoteDownloadProgress.value + (entry.url to fraction)
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            done = true
                            finishRemoteDownload(entry, fileName)
                        }
                        DownloadManager.STATUS_FAILED -> {
                            done = true
                            withContext(Dispatchers.Main) {
                                _remoteDownloadProgress.value =
                                    _remoteDownloadProgress.value - entry.url
                                _remoteDownloadError.value =
                                    "Download failed for $fileName."
                            }
                        }
                    }
                }
            }
        }
    }

    fun dismissRemoteDownloadError() {
        _remoteDownloadError.value = null
    }

    private suspend fun finishRemoteDownload(entry: RemoteEntry, fileName: String) {
        val app = getApplication<Application>()
        val staged = File(downloadRepo.downloadsDir(), fileName)
        var destination = "Downloads/ONE"
        if (staged.exists()) {
            val folder = _localFolder.value
            if (folder != null && moveIntoLocalFolder(app, folder, staged, fileName)) {
                destination = folder.displayName
            }
        }
        withContext(Dispatchers.Main) {
            _remoteDownloadProgress.value = _remoteDownloadProgress.value - entry.url
            _remoteDownloaded.value = _remoteDownloaded.value +
                (entry.url to "Saved to $destination")
            refreshLibrary()
        }
    }

    /**
     * Move a downloaded file into the user's local game-files folder via
     * SAF. Returns false when the folder cannot be written (permission
     * lost, folder gone) — the caller then leaves the file in
     * Downloads/ONE instead.
     */
    private fun moveIntoLocalFolder(
        app: Application,
        folder: LocalFolder,
        staged: File,
        name: String
    ): Boolean {
        return try {
            val treeUri = Uri.parse(folder.uri)
            try {
                app.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Best effort: the folder may already be writable; the
                // createFile call below decides.
            }
            val tree = DocumentFile.fromTreeUri(app, treeUri) ?: return false
            val mime = URLConnection.guessContentTypeFromName(name)
                ?: "application/octet-stream"
            tree.findFile(name)?.delete()
            val dest = tree.createFile(mime, name) ?: return false
            app.contentResolver.openOutputStream(dest.uri)?.use { out ->
                staged.inputStream().use { input -> input.copyTo(out) }
            } ?: return false
            staged.delete()
            true
        } catch (e: Exception) {
            false
        }
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
