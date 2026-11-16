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
import dev.one.launcher.data.CachedCatalog
import dev.one.launcher.data.CustomLink
import dev.one.launcher.data.CustomLinksStore
import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.DownloadRepository
import dev.one.launcher.data.DownloadState
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.GameFile
import dev.one.launcher.data.GameMagnetLink
import dev.one.launcher.data.gamesWithMatches
import dev.one.launcher.data.GitHubAsset
import dev.one.launcher.data.GitHubRelease
import dev.one.launcher.data.GitHubReleasesApi
import dev.one.launcher.data.GitHubStarsApi
import dev.one.launcher.data.LibraryFilter
import dev.one.launcher.data.LocalFileState
import dev.one.launcher.data.LocalFolder
import dev.one.launcher.data.LocalFolderScanner
import dev.one.launcher.data.LocalFolderStore
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.MatchedFile
import dev.one.launcher.data.MatchedFilesStore
import dev.one.launcher.data.PAIRING_CACHE_VERSION
import dev.one.launcher.data.PairingCache
import dev.one.launcher.data.PairingCacheStore
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
import dev.one.launcher.data.RemoteDisplayFormat
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.RemoteFolderCredentials
import dev.one.launcher.data.RemoteFolderSource
import dev.one.launcher.data.RemoteFoldersStore
import dev.one.launcher.data.RemoteListingResult
import dev.one.launcher.data.RemoteListingsCache
import dev.one.launcher.data.SecureKeyStore
import dev.one.launcher.data.StarCacheStore
import dev.one.launcher.data.StarEntry
import dev.one.launcher.data.compareVersions
import dev.one.launcher.data.filterRemoteEntriesToGameFiles
import dev.one.launcher.data.findGame
import dev.one.launcher.data.groupPortsByGame
import dev.one.launcher.data.isStarFresh
import dev.one.launcher.data.mapCatalogPort
import dev.one.launcher.data.mergeCatalogs
import dev.one.launcher.data.normalizeCustomLinkUrl
import dev.one.launcher.data.normalizeRemoteFolderUrl
import dev.one.launcher.data.pairGamesWithFiles
import dev.one.launcher.data.pairGamesWithMagnets
import dev.one.launcher.data.pairingMapsFingerprint
import dev.one.launcher.data.pickApkAsset
import dev.one.launcher.data.resolveDetected
import dev.one.launcher.data.sha256Matches
import dev.one.launcher.data.toCached
import dev.one.launcher.data.toGameFile
import dev.one.launcher.data.toGameMagnetLink
import dev.one.launcher.model.Port
import dev.one.launcher.model.PortCatalog
import dev.one.launcher.model.PortFork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import java.io.File
import java.net.URLConnection

/** How long the pairing-cache save waits for the sources to stop
 *  changing before running (v1.8.4): a burst of combine emissions
 *  during startup coalesces into one background pass. */
private const val PAIRING_CACHE_SAVE_DEBOUNCE_MS = 250L

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
    /** True when installs from unknown sources are not yet allowed for Portside. */
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

    /** Guards the background folder scan so refreshes cannot stack scans.
     *  Volatile: the pairing-cache settle check reads it from the
     *  combine's background thread. */
    @Volatile
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

    /**
     * The Library's game list (v1.6.0): the merged ports grouped by
     * game title + platform, emulators excluded. Rebuilt whenever the
     * port list changes (init, catalog apply).
     */
    private val _games = MutableStateFlow<List<GameEntry>>(emptyList())
    val gamesFlow: StateFlow<List<GameEntry>> = _games.asStateFlow()

    /** The game selected in the Library, by its stable key. Null = first game. */
    private val _selectedGameKey = MutableStateFlow<String?>(null)
    val selectedGameKey: StateFlow<String?> = _selectedGameKey.asStateFlow()

    /** GitHub star counts by repo ("owner/name"), for ranking a game's ports. */
    private val _repoStars = MutableStateFlow<Map<String, Int>>(emptyMap())
    val repoStars: StateFlow<Map<String, Int>> = _repoStars.asStateFlow()

    /**
     * Game keys with at least one matching file or magnet link, for
     * the Library's "Games with matches" filter (v1.8.8: magnets
     * count — see [gamesWithMatches]). Recomputed on a background
     * thread whenever the games or any file source changes.
     */
    private val _gamesWithFiles = MutableStateFlow<Set<String>>(emptySet())
    val gamesWithFilesFlow: StateFlow<Set<String>> = _gamesWithFiles.asStateFlow()

    /**
     * Library filter state (v1.8.2): the search query, platform
     * filter, and "Has files" toggle. They live here, not in a
     * composable's `remember` — so navigating into a game page and
     * back restores the previous Library state instead of resetting
     * to the default main screen.
     */
    private val _libraryFilter = MutableStateFlow(LibraryFilter())
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    fun setLibraryQuery(query: String) {
        _libraryFilter.value = _libraryFilter.value.copy(query = query)
    }

    fun setLibraryPlatform(platform: String) {
        _libraryFilter.value = _libraryFilter.value.copy(platform = platform)
    }

    fun setLibraryMatchesOnly(matchesOnly: Boolean) {
        _libraryFilter.value = _libraryFilter.value.copy(matchesOnly = matchesOnly)
    }

    /**
     * Pairing cache (v1.8.2): per-game matched files (game key ->
     * files), loaded from disk on startup and refreshed in the
     * background. The Library, the "Has files" filter, the poster
     * source tags, and the game pages read from it, so they populate
     * immediately instead of waiting on a re-run of the match.
     */
    private val _pairingCache = MutableStateFlow<Map<String, List<GameFile>>>(emptyMap())
    val pairingCache: StateFlow<Map<String, List<GameFile>>> =
        _pairingCache.asStateFlow()

    /** Replace the pairing cache state. */
    private fun setPairingCache(map: Map<String, List<GameFile>>) {
        _pairingCache.value = map
    }

    /**
     * Magnet pairings (v1.8.3): per-game matched magnet links (game
     * key -> links), loaded from disk on startup and refreshed in the
     * background alongside the file pairings. A separate channel from
     * the downloadable-file pairings — magnets never feed "Your
     * files", only the game page's Magnet links section, which
     * pre-populates from this instead of waiting on a re-run of the
     * match. They DO feed "Has files" (v1.8.8, via
     * [gamesWithMatches]).
     */
    private val _magnetPairings =
        MutableStateFlow<Map<String, List<GameMagnetLink>>>(emptyMap())
    val magnetPairings: StateFlow<Map<String, List<GameMagnetLink>>> =
        _magnetPairings.asStateFlow()

    /** Replace the magnet pairings state. */
    private fun setMagnetPairings(map: Map<String, List<GameMagnetLink>>) {
        _magnetPairings.value = map
    }

    private val pairingCacheStore = PairingCacheStore(application)

    /**
     * True once the pairing cache has been loaded (or found absent)
     * on startup. The background refresh must not overwrite the cache
     * before this — a settled live computation running before the
     * cache loads would wipe it with a partial result.
     */
    private var pairingCacheLoaded = false

    /**
     * The content fingerprint of the last pairing maps saved (or
     * loaded), so the background refresh can tell whether the
     * pairings changed with a cheap hash comparison instead of
     * rebuilding the cached forms and deep-comparing them on Main
     * (the v1.8.3 ANR: that rebuild + deep equality ran on the main
     * thread over ~24k entries, on every combine emission). 0 when
     * no cache has been loaded or saved yet.
     */
    private var lastPairingFingerprint: Long = 0L

    /** The pending pairing-cache save, if one is scheduled. Cancelled
     *  and rescheduled on every new combine emission, so a burst of
     *  emissions during startup (each source landing) coalesces into
     *  a single background pass instead of queuing up one expensive
     *  rebuild per emission. */
    private var pairingCacheSaveJob: Job? = null

    private val starsApi = GitHubStarsApi()
    private val starCacheStore = StarCacheStore(application)

    /** Repos with a star fetch already running, so opens cannot stack them. */
    private val starFetchInFlight = mutableSetOf<String>()

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

    /**
     * Display format of the source open in the remote browser.
     * Display only — the browser's entries are the parser's entries;
     * this only reformats how they are shown.
     */
    private val _remoteBrowserFormat = MutableStateFlow(RemoteDisplayFormat())
    val remoteBrowserFormat: StateFlow<RemoteDisplayFormat> =
        _remoteBrowserFormat.asStateFlow()

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

    /**
     * Remote listings for the detail page's Server library section:
     * source id -> file entries (root listing + one level of
     * subfolders). Fetched once per session per source. The detail
     * page is the only place these entries are matched to ports.
     */
    private val _serverListings = MutableStateFlow<Map<String, List<RemoteEntry>>>(emptyMap())
    val serverListings: StateFlow<Map<String, List<RemoteEntry>>> =
        _serverListings.asStateFlow()

    /** Source ids whose server listing is currently being fetched. */
    private val _serverLoading = MutableStateFlow<Set<String>>(emptySet())
    val serverLoading: StateFlow<Set<String>> = _serverLoading.asStateFlow()

    private val recompaApi = RecompaCatalogApi(application)
    private val remoteFoldersStore = RemoteFoldersStore(application)
    private val remoteCredentials = RemoteFolderCredentials(application)
    private val remoteDirectoryApi = RemoteDirectoryApi()
    /** On-disk cache for parsed remote listings (v1.8.5): a cold start
     *  serves the cached entries immediately and refreshes in the
     *  background, instead of re-fetching + re-parsing remote.json
     *  (11 MB) before anything can match. */
    private val remoteListingsCache = RemoteListingsCache(application.cacheDir)

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
        _games.value = groupPortsByGame(catalog.ports)
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
            remoteFoldersStore.sources.collect { sources ->
                _remoteFolders.value = sources
                // The open source may have had its display format edited
                // while the browser is open — follow the stored source.
                remoteSource?.let { open ->
                    sources.firstOrNull { it.id == open.id }?.let { updated ->
                        remoteSource = updated
                        _remoteBrowserFormat.value = updated.displayFormat
                    }
                }
                // The pairing cache's background refresh needs every
                // remote folder's listing, so fetch them eagerly once
                // per session instead of waiting for a game page open.
                ensureServerListings()
            }
        }
        // Restore a saved Premiumize session, if the user linked one before.
        // The keystore read runs on IO (v1.8.7): the first read also
        // initializes the lazy EncryptedSharedPreferences, which touches
        // the keystore and must not happen on the main thread.
        viewModelScope.launch {
            if (secureKeyStore.getApiKey() != null) restoreSession()
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
        // Pairing cache (v1.8.2): load the saved pairings first so the
        // Library and "Has files" populate immediately, then the
        // background refresh replaces them with fresh results. A
        // wrong-version or corrupt cache reads as null and is ignored.
        viewModelScope.launch {
            val cache = try {
                pairingCacheStore.load()
            } catch (e: Exception) {
                null
            }
            if (cache != null) {
                val map = cache.pairings.mapValues { (_, files) ->
                    files.mapNotNull { it.toGameFile() }
                }.filterValues { it.isNotEmpty() }
                setPairingCache(map)
                // Magnet pairings (v1.8.3): same restore, same
                // channel separation — a cache written by v1.8.2 has
                // no magnet field and decodes it as empty, and the
                // background refresh fills it in. The magnet map is
                // computed BEFORE the "Has files" set below: magnets
                // count toward "Has files" now (v1.8.8, see
                // [gamesWithMatches]) — a magnet-only game appears
                // under the filter as soon as its cached magnets
                // load, not only after the live refresh.
                val magnetMap = cache.magnetPairings.mapValues { (_, links) ->
                    links.mapNotNull { it.toGameMagnetLink() }
                }.filterValues { it.isNotEmpty() }
                _gamesWithFiles.value = gamesWithMatches(map.keys, magnetMap.keys)
                setMagnetPairings(magnetMap)
                // The fingerprint of the loaded cache, so the first
                // background refresh can tell whether the live
                // pairings changed without rebuilding and
                // deep-comparing the cached forms (the v1.8.3 ANR).
                // Computed off Main — it walks every cached entry.
                lastPairingFingerprint = withContext(Dispatchers.Default) {
                    pairingMapsFingerprint(map, magnetMap)
                }
            }
            pairingCacheLoaded = true
        }
        // "Games with matches" filter state: the game keys with at
        // least one matching file. Recomputed on a background thread
        // whenever the games or any file source changes, so the filter
        // is always in sync without blocking the UI. Seven flows is
        // past the typed combine overloads (they stop at five in
        // kotlinx-coroutines 1.8), so this is the vararg form: one
        // Array, in argument order. The casts are safe — the order is
        // fixed here.
        viewModelScope.launch {
            combine(
                _games, _cloudAllFiles, _cloudFallback, _serverListings,
                _detectedLocalFiles, _portStatuses, _remoteFolders
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val games = values[0] as List<GameEntry>
                @Suppress("UNCHECKED_CAST")
                val cloudFiles = values[1] as List<PremiumizeItem>
                @Suppress("UNCHECKED_CAST")
                val cloudFallback = values[2] as Map<String, List<PremiumizeItem>>
                @Suppress("UNCHECKED_CAST")
                val serverListings = values[3] as Map<String, List<RemoteEntry>>
                @Suppress("UNCHECKED_CAST")
                val detected = values[4] as Map<String, List<DetectedFile>>
                @Suppress("UNCHECKED_CAST")
                val statuses = values[5] as Map<String, PortStatus>
                @Suppress("UNCHECKED_CAST")
                val remoteFolders = values[6] as List<RemoteFolderSource>
                val serverSources =
                    remoteFolders.map { it.name to serverListings[it.id].orEmpty() }
                val localStates = games.flatMap { it.ports }.associate { port ->
                    val status = statuses[port.id]
                    port.id to LocalFileState(
                        downloadedFileName = status?.downloadedFileName,
                        downloadedMatchKind = status?.downloadedMatchKind,
                        downloadedInnerEntry = status?.downloadedInnerEntry,
                        assignedFileName = status?.assignedFile?.name
                    )
                }
                // The full pairing map (game key -> files); the "Has
                // files" set is its keys plus the magnet map's keys
                // (v1.8.8: magnets count — see [gamesWithMatches]).
                // Computing the map costs the same matching the set
                // did — the per-game files are the same work,
                // grouped. The magnet pairings (v1.8.3) ride
                // alongside in the same pass — same server sources,
                // separate channel, now feeding "Has files" too.
                //
                // Snapshot completeness (v1.8.7): whether the inputs
                // THIS computation ran from were complete — every
                // configured remote folder already had a listing in
                // the serverListings snapshot. A pairing computed
                // before the listings load (the first combine
                // emission, when _remoteFolders is still empty or
                // the listings have not landed) has no magnets at
                // all, and must never overwrite a good cache with
                // that empty result — that is the bug Garrett filmed
                // in v1.8.6: the game page kept showing "No file"
                // and the magnets re-appeared only after the live
                // match, because the cache had been wiped to
                // magnets-empty by a stale save. The live
                // isPairingRefreshSettled() check below cannot catch
                // this on its own: it reads the CURRENT state, which
                // is settled by the time a slow transform finishes,
                // while the result it guards was computed from the
                // old, empty snapshot. So the gate is on the snapshot
                // itself.
                val snapshotComplete = games.isNotEmpty() &&
                    remoteFolders.all { it.id in serverListings }
                Triple(
                    pairGamesWithFiles(
                        games, cloudFiles, cloudFallback, serverSources,
                        detected, localStates
                    ),
                    pairGamesWithMagnets(games, serverSources),
                    snapshotComplete
                )
            }
                .flowOn(Dispatchers.Default)
                .collect { (pairingMap, magnetMap, snapshotComplete) ->
                    // The cache pre-populates _gamesWithFiles on
                    // startup; the live result only replaces it once
                    // the refresh is settled (every source loaded), so
                    // a partial pass can never wipe the cached
                    // pairings — and the cache is never overwritten
                    // before it has even been loaded.
                    if (!pairingCacheLoaded) return@collect
                    // A pairing computed from an incomplete snapshot
                    // (listings not yet loaded) is partial — never
                    // let it overwrite the cache. The next combine
                    // emission, with complete inputs, saves the real
                    // result.
                    if (!snapshotComplete) return@collect
                    if (!isPairingRefreshSettled()) return@collect
                    schedulePairingCacheSave(pairingMap, magnetMap)
                }
        }
    }

    /**
     * True when every file source has finished loading (or is not
     * configured), so a pairing computation is a complete picture and
     * safe to cache. A source that failed still counts as settled —
     * it produced an empty result, not a pending one.
     */
    private fun isPairingRefreshSettled(): Boolean {
        if (_games.value.isEmpty()) return false
        if (isCloudConnected && !_cloudAllLoaded.value) return false
        if (_serverLoading.value.isNotEmpty()) return false
        if (_remoteFolders.value.any { it.id !in _serverListings.value }) return false
        if (localScanRunning) return false
        return true
    }

    /**
     * Overwrite the pairing cache with fresh pairing maps, when they
     * actually changed. Also moves the live "Has files" set to the
     * union of the new file map's keys and the new magnet map's keys
     * (v1.8.8: magnets count — see [gamesWithMatches]). The store
     * write is best-effort; the in-memory pairings stand even if it
     * fails. The file and magnet channels are compared and saved
     * together, in one cache record — a refresh that changes only
     * one still writes both, so the two channels can never drift
     * apart on disk.
     *
     * The expensive work never runs on Main (v1.8.4): the content
     * fingerprint is computed on Dispatchers.Default and compared in
     * O(1), the cached-form rebuild runs on Dispatchers.Default, and
     * the store write runs on Dispatchers.IO. Only the cheap
     * StateFlow updates post back to Main. The v1.8.3 version did the
     * cached-form rebuild plus a deep structural equality over ~24k
     * entries synchronously on Main, on every combine emission —
     * that is the freeze that ANR'd the app. Rapid successive
     * emissions coalesce: each new one cancels the pending save and
     * schedules a fresh one, so the work runs once, for the latest
     * maps, after the sources stop changing.
     */
    private fun schedulePairingCacheSave(
        map: Map<String, List<GameFile>>,
        magnetMap: Map<String, List<GameMagnetLink>>
    ) {
        pairingCacheSaveJob?.cancel()
        pairingCacheSaveJob = viewModelScope.launch {
            delay(PAIRING_CACHE_SAVE_DEBOUNCE_MS)
            // The cheap change check: a 64-bit content hash,
            // computed off Main. Unchanged pairings skip the
            // rebuild, the flow updates, and the store write.
            val fingerprint = withContext(Dispatchers.Default) {
                pairingMapsFingerprint(map, magnetMap)
            }
            if (fingerprint == lastPairingFingerprint) return@launch
            // Rebuild the cached forms off Main (the v1.8.3 ANR did
            // this plus the deep equality on Main, over ~24k entries).
            val (cachedForm, magnetCachedForm) = withContext(Dispatchers.Default) {
                map.mapValues { (_, files) -> files.map { it.toCached() } } to
                    magnetMap.mapValues { (_, links) -> links.map { it.toCached() } }
            }
            lastPairingFingerprint = fingerprint
            setPairingCache(map)
            setMagnetPairings(magnetMap)
            _gamesWithFiles.value = gamesWithMatches(map.keys, magnetMap.keys)
            withContext(Dispatchers.IO) {
                try {
                    pairingCacheStore.save(
                        PairingCache(
                            PAIRING_CACHE_VERSION,
                            System.currentTimeMillis(),
                            cachedForm,
                            magnetCachedForm
                        )
                    )
                } catch (e: Exception) {
                    // Cache write is best-effort; the in-memory pairings stand.
                }
            }
        }
    }

    // ---- Library ----

    fun selectPort(id: String?) {
        _selectedPortId.value = id
    }

    fun portById(id: String): Port? = ports.firstOrNull { it.id == id }

    /**
     * Select a game in the Library. Selecting a game clears the port
     * selection, so the unfolded detail pane shows the game page again
     * instead of the last port opened from it.
     */
    fun selectGame(key: String?) {
        _selectedGameKey.value = key
        if (key != null) _selectedPortId.value = null
    }

    fun gameByKey(key: String): GameEntry? = findGame(_games.value, key)

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
        applyMergedPorts(
            merged = merge.merged,
            platforms = index.platforms,
            generatedAt = index.generated_at,
            fromCache = fromCache,
            unmatchedCuratedIds = merge.unmatchedCuratedIds,
            portsWithoutFileInfo = merge.portsWithoutFileInfo
        )
        // Persist the merged list so the next cold start can skip the
        // re-parse + re-map entirely (v1.8.5 parsed catalog cache).
        viewModelScope.launch(Dispatchers.IO) {
            recompaApi.saveParsed(
                CachedCatalog(
                    ports = merge.merged,
                    platforms = index.platforms,
                    generatedAt = index.generated_at
                )
            )
        }
    }

    /**
     * Apply a previously merged catalog from the parsed cache
     * (v1.8.5): the merged port list is already in display form, so
     * the Library shows all 529 games immediately on cold start
     * without re-parsing the 5.5 MB index or re-mapping it. The
     * background refresh still re-parses and overwrites this cache.
     */
    private fun applyParsedCatalog(cached: CachedCatalog) {
        applyMergedPorts(
            merged = cached.ports,
            platforms = cached.platforms,
            generatedAt = cached.generatedAt,
            fromCache = true,
            unmatchedCuratedIds = emptyList(),
            portsWithoutFileInfo = emptyList()
        )
    }

    /**
     * Set the Library state from a merged port list — shared by
     * [applyCatalog] (freshly merged) and [applyParsedCatalog]
     * (loaded from the parsed cache).
     */
    private fun applyMergedPorts(
        merged: List<Port>,
        platforms: List<RecompaPlatform>,
        generatedAt: String,
        fromCache: Boolean,
        unmatchedCuratedIds: List<String>,
        portsWithoutFileInfo: List<String>
    ) {
        _ports.value = merged
        _games.value = groupPortsByGame(merged)
        // A selected game that no longer exists (catalog changed)
        // falls back to the first game.
        if (_selectedGameKey.value != null &&
            findGame(_games.value, _selectedGameKey.value!!) == null
        ) {
            _selectedGameKey.value = null
        }
        _catalogPlatforms.value = platforms
        _catalogState.value = CatalogState.Ready(
            portCount = merged.size,
            generatedAt = generatedAt,
            fromCache = fromCache,
            unmatchedCuratedIds = unmatchedCuratedIds,
            portsWithoutFileInfo = portsWithoutFileInfo
        )
        rebuildStatuses()
        scanLocalFolder()
    }

    /** Cached index first (cold start), then a background network refresh. */
    private suspend fun loadCatalog() {
        // The parsed cache is the fastest path: the merged port list
        // is already in display form, so the Library shows all 529
        // games immediately. Fall back to the raw JSON cache when
        // there is no parsed cache yet (first run after the update).
        val parsed = withContext(Dispatchers.IO) { recompaApi.loadParsedCached() }
        if (parsed != null) {
            applyParsedCatalog(parsed)
        } else {
            val cached = withContext(Dispatchers.IO) { recompaApi.loadCached() }
            if (cached != null) applyCatalog(cached, fromCache = true)
        }
        val fresh = recompaApi.refresh()
        if (fresh != null) {
            applyCatalog(fresh, fromCache = false)
        } else if (parsed == null) {
            val cached = withContext(Dispatchers.IO) { recompaApi.loadCached() }
            if (cached == null) {
                _catalogState.value = CatalogState.Unavailable(
                    "Recompa catalog unavailable — showing your curated ports."
                )
            }
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

    private suspend fun api(): PremiumizeApi? {
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
        viewModelScope.launch { secureKeyStore.clear() }
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
        viewModelScope.launch {
            val api = api() ?: return@launch
            _cloudLoading.value = true
            _cloudError.value = null
            _searchResults.value = null
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
        viewModelScope.launch {
            val api = api() ?: return@launch
            _cloudLoading.value = true
            _cloudError.value = null
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
        if (_cloudAllLoading.value) return
        _cloudAllLoading.value = true
        viewModelScope.launch {
            val api = api()
            if (api == null) {
                _cloudAllLoading.value = false
                return@launch
            }
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
            viewModelScope.launch {
                val api = api() ?: return@launch
                searchCloudFallback(port, api)
            }
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

    /**
     * Make sure this port's server files are available for its detail
     * page: fetch each configured remote folder's listing once per
     * session and cache the file entries. The detail page matches them
     * locally with the same matcher the Cloud tab uses, and the detail
     * page is the only place remote entries are matched to ports.
     */
    fun ensureServerFilesFor(port: Port) {
        ensureServerListings()
    }

    /**
     * Fetch each configured remote folder's listing once per session
     * and cache the file entries. Shared by the per-port detail page
     * and the Library's "Games with matches" filter.
     *
     * Cache-first (v1.8.5): a source's parsed entries are served from
     * the on-disk cache immediately, so pairing starts from disk data
     * with no network wait, and the fresh fetch runs in the
     * background. On a fetch error the cached entries stay in place —
     * the list is never overwritten with an empty list — so a game
     * page keeps showing its cached files and magnets while offline.
     * An empty successful listing (a genuinely empty folder) does
     * replace the cache, because [fetchServerEntries] returns null
     * for an error and an empty list for a successful empty fetch.
     */
    private fun ensureServerListings() {
        _remoteFolders.value.forEach { source ->
            if (_serverListings.value.containsKey(source.id)) return@forEach
            if (_serverLoading.value.contains(source.id)) return@forEach
            _serverLoading.value = _serverLoading.value + source.id
            viewModelScope.launch(Dispatchers.IO) {
                // Serve the disk cache first — pairing starts from
                // disk data while the fresh fetch runs, so a cold
                // start never waits on the network or on parsing
                // remote.json (11 MB). The load runs on IO, never
                // Main: the cached listing is a few MB of JSON.
                val cached = remoteListingsCache.load(source.id)
                if (cached != null) {
                    withContext(Dispatchers.Main) {
                        _serverListings.value = _serverListings.value + (source.id to cached)
                    }
                }
                val creds = remoteCredentials.load(source.id)
                val entries = fetchServerEntries(source, creds)
                withContext(Dispatchers.Main) {
                    if (entries != null) {
                        _serverListings.value = _serverListings.value + (source.id to entries)
                        viewModelScope.launch(Dispatchers.IO) {
                            remoteListingsCache.save(source.id, entries)
                        }
                    }
                    // On error (entries == null) the cached entries
                    // stay in place — never overwritten with nothing.
                    _serverLoading.value = _serverLoading.value - source.id
                }
            }
        }
    }

    /**
     * Make sure the file sources are loaded so the Library's "Games
     * with matches" filter can compute: the Premiumize flat list when
     * connected and not yet loaded, plus every remote folder's
     * listing. Local files are already scanned on startup. The
     * filter's game set recomputes itself when the data lands.
     */
    fun ensureAllFilesForFilter() {
        if (isCloudConnected && !_cloudAllLoaded.value) refreshCloudFiles()
        ensureServerListings()
    }

    /**
     * Blocking — call on Dispatchers.IO. One source's file entries:
     * its root listing plus one level of subfolder listings (home
     * servers usually group files in per-game folders). Folders are
     * dropped — the Server section lists files only. Uses only the
     * existing listing parser; an unreadable folder is skipped, never
     * an error for the whole source. Returns null when the ROOT fetch
     * fails (network error, HTTP error, unparseable) so the caller can
     * tell an error from a successful empty listing and keep the
     * cached entries; a subfolder that fails is just skipped.
     */
    private fun fetchServerEntries(
        source: RemoteFolderSource,
        creds: Pair<String, String>?
    ): List<RemoteEntry>? {
        val root = when (val result =
            remoteDirectoryApi.fetch(source.url, creds?.first, creds?.second)) {
            is RemoteListingResult.Listing -> result.entries
            is RemoteListingResult.Error -> return null
        }
        val files = root.filter { !it.isFolder }.toMutableList()
        root.filter { it.isFolder }.forEach { folder ->
            when (val result =
                remoteDirectoryApi.fetch(folder.url, creds?.first, creds?.second)) {
                is RemoteListingResult.Listing ->
                    files += result.entries.filter { !it.isFolder }
                is RemoteListingResult.Error -> { /* skip that folder */ }
            }
        }
        return files
    }

    /**
     * Make sure a game's files are available for its game page: cloud
     * and server files for every port in the game. The per-port
     * functions handle the once-per-session caching.
     */
    fun ensureGameFilesFor(game: GameEntry) {
        for (port in game.ports) {
            ensureCloudFilesFor(port)
            ensureServerFilesFor(port)
        }
    }

    /**
     * Fetch GitHub star counts for a game's ports, for the star
     * ranking. A fresh cached entry (~7 day TTL) is used instead of a
     * network call; a repo already being fetched is skipped. Any
     * failure just leaves that repo out of the map — the game page
     * falls back to the catalog's download counts. One request per
     * port at most, usually one per game page open.
     */
    fun fetchStarsForGame(game: GameEntry) {
        val repos = game.ports
            .mapNotNull { it.repo.takeIf { repo -> repo.isNotBlank() } }
            .distinct()
        if (repos.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = try {
                starCacheStore.load()
            } catch (e: Exception) {
                emptyMap()
            }
            val fresh = mutableMapOf<String, Int>()
            val toFetch = mutableListOf<String>()
            for (repo in repos) {
                val entry = cached[repo]
                if (entry != null && isStarFresh(entry.fetchedAtMs, now)) {
                    fresh[repo] = entry.stars
                } else if (repo !in starFetchInFlight) {
                    toFetch.add(repo)
                }
            }
            if (fresh.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _repoStars.value = _repoStars.value + fresh
                }
            }
            if (toFetch.isEmpty()) return@launch
            toFetch.forEach { starFetchInFlight.add(it) }
            val fetched = mutableMapOf<String, Int>()
            val updatedCache = cached.toMutableMap()
            for (repo in toFetch) {
                val stars = starsApi.starsForRepo(repo)
                if (stars != null) {
                    fetched[repo] = stars
                    updatedCache[repo] = StarEntry(stars, now)
                }
            }
            toFetch.forEach { starFetchInFlight.remove(it) }
            if (fetched.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _repoStars.value = _repoStars.value + fetched
                }
                try {
                    starCacheStore.save(updatedCache)
                } catch (e: Exception) {
                    // Cache write is best-effort; the in-memory stars stand.
                }
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
     * Hand the downloaded APK to the system installer. If Portside is not yet
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

    /** Open the system screen where the user allows Portside to install apps. */
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
        _downloadError.value = null
        viewModelScope.launch {
            val api = api() ?: return@launch
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
     * it in the external browser — Portside never downloads from it.
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
            _serverListings.value = _serverListings.value - source.id
            _serverLoading.value = _serverLoading.value - source.id
            // Drop the source's cached listing too, so a re-added
            // source with the same id never serves stale entries.
            remoteListingsCache.clear(source.id)
            if (remoteSource?.id == source.id) {
                remoteSource = null
                remoteTrail = emptyList()
                _remoteBrowser.value = RemoteBrowserState.Idle
                _remoteBrowserFormat.value = RemoteDisplayFormat()
            }
        }
    }

    /**
     * Save one source's display format. Display only — it never
     * touches the source's URL or credentials, and the browser's
     * entries are unchanged; the stored source flows back through the
     * collect above, so the browser picks the new format up live.
     */
    fun updateRemoteFolderDisplayFormat(sourceId: String, format: RemoteDisplayFormat) {
        viewModelScope.launch {
            remoteFoldersStore.updateDisplayFormat(sourceId, format)
        }
    }

    fun dismissRemoteFolderError() {
        _remoteFolderError.value = null
    }

    /** Open a remote folder source in its own browser screen. */
    fun openRemoteFolder(sourceId: String) {
        val source = _remoteFolders.value.firstOrNull { it.id == sourceId } ?: return
        remoteSource = source
        _remoteBrowserFormat.value = source.displayFormat
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

    /**
     * Fetch one remote folder for the browser screen. Only game files
     * and magnet entries are shown (v1.6.1, [filterRemoteEntriesToGameFiles]):
     * a folder full of collection indexes, Redump/TOSEC lists, or
     * firmware links shows as an empty folder, not as a wall of files
     * that are not games. Folders themselves are always kept — they are
     * navigation — and magnet entries are always kept, because they are
     * copyable links, not files. The filter is display only: the Server
     * library matching still runs on the full listing through the
     * matcher gate, so nothing that could match is lost here.
     */
    private fun fetchRemoteUrl(url: String) {
        val source = remoteSource ?: return
        _remoteBrowser.value = RemoteBrowserState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val creds = remoteCredentials.load(source.id)
            val result = remoteDirectoryApi.fetch(url, creds?.first, creds?.second)
            withContext(Dispatchers.Main) {
                _remoteBrowser.value = when (result) {
                    is RemoteListingResult.Listing ->
                        RemoteBrowserState.Loaded(
                            filterRemoteEntriesToGameFiles(result.entries),
                            remoteTrail
                        )
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
        // A magnet entry is a copyable link, not a file: it is never
        // downloaded. The browser row offers "Copy link" for it, so
        // this is only a guard against a stray call.
        if (entry.isMagnet) return
        val fileName = entry.name.substringAfterLast('/').substringAfterLast('\\').trim()
        if (fileName.isEmpty() || fileName == "." || fileName == "..") {
            _remoteDownloadError.value = "That file name cannot be saved on this device."
            return
        }
        _remoteDownloadError.value = null
        val app = getApplication<Application>()
        val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        viewModelScope.launch(Dispatchers.IO) {
            val creds = remoteCredentials.load(source.id)
            val request = DownloadManager.Request(Uri.parse(entry.url)).apply {
                setTitle(fileName)
                setDescription("Portside remote folder")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalFilesDir(
                    app, Environment.DIRECTORY_DOWNLOADS, "ONE/$fileName"
                )
                if (creds != null) {
                    addRequestHeader("Authorization", Credentials.basic(creds.first, creds.second))
                }
            }
            val downloadId = try {
                dm.enqueue(request)
            } catch (e: Exception) {
                _remoteDownloadError.value =
                    "Could not start the download: ${e.message ?: "unknown error"}"
                return@launch
            }
            _remoteDownloadProgress.value = _remoteDownloadProgress.value + (entry.url to 0f)
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

