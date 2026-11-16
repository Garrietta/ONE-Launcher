package dev.one.launcher.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import coil.compose.AsyncImage
import dev.one.launcher.data.DetectedFile
import dev.one.launcher.data.FindReplace
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.GameFile
import dev.one.launcher.data.GameFileSource
import dev.one.launcher.data.GameMagnetLink
import dev.one.launcher.data.LocalFileState
import dev.one.launcher.data.MatchKind
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.PortSettings
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.data.RemoteDisplayFormat
import dev.one.launcher.data.RemoteEntry
import dev.one.launcher.data.RemoteFolderSource
import dev.one.launcher.data.fileSourceLabel
import dev.one.launcher.data.filterGames
import dev.one.launcher.data.formatRemoteTitle
import dev.one.launcher.data.magnetLinkToCopy
import dev.one.launcher.data.matchCloudFilesForGame
import dev.one.launcher.data.matchLocalFilesForGame
import dev.one.launcher.data.matchMagnetLinksForGame
import dev.one.launcher.data.matchServerFilesForGame
import dev.one.launcher.data.mergeLiveAndCachedFiles
import dev.one.launcher.data.mergeLiveAndCachedMagnets
import dev.one.launcher.data.parseHiddenExtensions
import dev.one.launcher.data.posterSourceLabel
import dev.one.launcher.data.posterStatusText
import dev.one.launcher.data.rankPortsByStars
import dev.one.launcher.data.shouldShowRemoteEntry
import dev.one.launcher.data.unfoldedGridColumns
import dev.one.launcher.model.Port
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class OneDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Library : OneDestination("library", "Library", Icons.Filled.GridView)
    data object Cloud : OneDestination("cloud", "Cloud", Icons.Filled.Cloud)
    data object Settings : OneDestination("settings", "Settings", Icons.Filled.Settings)
}

/**
 * Folded vs unfolded decides the layout — never orientation alone.
 * Unfolded is a tablet-class width, or a separating fold is present.
 */
@Composable
private fun rememberUnfolded(activity: Activity, windowSizeClass: WindowSizeClass): Boolean {
    val layoutInfoFlow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity)
    }
    val layoutInfo by layoutInfoFlow.collectAsState(initial = null)
    val foldingFeature = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
    val separating = foldingFeature?.isSeparating == true
    return windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded || separating
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun OneApp(viewModel: OneViewModel) {
    val activity = LocalContext.current as Activity
    val windowSizeClass = calculateWindowSizeClass(activity)
    val unfolded = rememberUnfolded(activity, windowSizeClass)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val destinations = listOf(
        OneDestination.Library,
        OneDestination.Cloud,
        OneDestination.Settings
    )

    Scaffold(
        bottomBar = {
            if (!unfolded) {
                NavigationBar {
                    destinations.forEach { dest ->
                        val selected = currentRoute == dest.route ||
                            (dest == OneDestination.Library &&
                                (currentRoute?.startsWith("detail/") == true ||
                                    currentRoute?.startsWith("game/") == true)) ||
                            (dest == OneDestination.Settings &&
                                currentRoute?.startsWith("remote/") == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (unfolded) {
                NavigationRail {
                    destinations.forEach { dest ->
                        val selected = currentRoute == dest.route ||
                            (dest == OneDestination.Library &&
                                (currentRoute?.startsWith("detail/") == true ||
                                    currentRoute?.startsWith("game/") == true)) ||
                            (dest == OneDestination.Settings &&
                                currentRoute?.startsWith("remote/") == true)
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = OneDestination.Library.route,
                modifier = Modifier.weight(1f)
            ) {
                composable(OneDestination.Library.route) {
                    LibraryScreen(
                        viewModel = viewModel,
                        unfolded = unfolded,
                        onSelectGame = { viewModel.selectGame(it) },
                        onOpenGame = { key ->
                            if (!unfolded) navController.navigate("game/$key")
                        },
                        onOpenDetail = { id ->
                            if (unfolded) {
                                viewModel.selectPort(id)
                            } else {
                                navController.navigate("detail/$id")
                            }
                        },
                        onGoToCloud = {
                            navController.navigate(OneDestination.Cloud.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("detail/{portId}") { entry ->
                    val id = entry.arguments?.getString("portId") ?: return@composable
                    val port = viewModel.portById(id) ?: return@composable
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(port.title) },
                                navigationIcon = {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                            Icons.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        GameDetailContent(
                            viewModel = viewModel,
                            port = port,
                            onGoToCloud = {
                                navController.navigate(OneDestination.Cloud.route) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
                composable("game/{gameKey}") { entry ->
                    val key = entry.arguments?.getString("gameKey") ?: return@composable
                    val game = viewModel.gameByKey(key) ?: return@composable
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(game.title) },
                                navigationIcon = {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(
                                            Icons.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        GamePageContent(
                            viewModel = viewModel,
                            game = game,
                            onOpenDetail = { id -> navController.navigate("detail/$id") },
                            onGoToCloud = {
                                navController.navigate(OneDestination.Cloud.route) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
                composable(OneDestination.Cloud.route) {
                    CloudScreen(
                        viewModel = viewModel,
                        onOpenDetail = { id -> navController.navigate("detail/$id") }
                    )
                }
                composable(OneDestination.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onOpenRemoteFolder = { id ->
                            navController.navigate("remote/$id")
                        }
                    )
                }
                composable("remote/{sourceId}") { entry ->
                    val sourceId = entry.arguments?.getString("sourceId")
                        ?: return@composable
                    val source = viewModel.remoteFolders.collectAsState().value
                        .firstOrNull { it.id == sourceId } ?: return@composable
                    LaunchedEffect(sourceId) { viewModel.openRemoteFolder(sourceId) }
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(source.name) },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        if (!viewModel.navigateRemoteBack()) {
                                            navController.popBackStack()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.ArrowBack,
                                            contentDescription = "Back"
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { viewModel.refreshRemoteBrowser() }) {
                                        Icon(
                                            Icons.Filled.Refresh,
                                            contentDescription = "Refresh folder"
                                        )
                                    }
                                }
                            )
                        }
                    ) { innerPadding ->
                        RemoteBrowserScreen(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// ---- Library ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    viewModel: OneViewModel,
    unfolded: Boolean,
    onSelectGame: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onGoToCloud: () -> Unit
) {
    val statuses by viewModel.portStatuses.collectAsState()
    val selectedGameKey by viewModel.selectedGameKey.collectAsState()
    val ports by viewModel.portsFlow.collectAsState()
    val games by viewModel.gamesFlow.collectAsState()
    val gamesWithFiles by viewModel.gamesWithFilesFlow.collectAsState()
    val catalogState by viewModel.catalogState.collectAsState()
    val pairingCache by viewModel.pairingCache.collectAsState()
    val magnetPairings by viewModel.magnetPairings.collectAsState()

    // Search + platform filter (v1.8.2): the filter state lives in the
    // ViewModel, not in a composable's `remember` — so navigating into
    // a game page and back restores the previous Library state instead
    // of resetting to the default main screen. The platform options
    // come from the games themselves, so a curated-only port's
    // platform ("Super Nintendo") and its catalog sibling's platform
    // name always agree. "Has files" is a third, additional filter:
    // only games with at least one matching file (local, cloud, or
    // server). Off by default.
    val libraryFilter by viewModel.libraryFilter.collectAsState()
    val query = libraryFilter.query
    val platformFilter = libraryFilter.platform
    val matchesOnly = libraryFilter.matchesOnly
    LaunchedEffect(matchesOnly) {
        if (matchesOnly) viewModel.ensureAllFilesForFilter()
    }
    val platformOptions = remember(games) {
        games.map { it.platform }.distinct().sorted()
            .map { it to it }
    }
    val filteredGames = remember(games, libraryFilter, gamesWithFiles) {
        filterGames(games, libraryFilter, gamesWithFiles)
    }
    val catalogStatusText = when (val state = catalogState) {
        CatalogState.Loading -> "Loading the Recompa catalog…"
        is CatalogState.Ready -> {
            val date = state.generatedAt.take(10)
            val cached = if (state.fromCache) " (cached)" else ""
            "${games.size} games · ${state.portCount} ports · Recompa catalog, updated $date$cached"
        }
        is CatalogState.Unavailable -> state.message
    }

    LaunchedEffect(Unit) { viewModel.refreshLibrary() }

    // The selected game: the ViewModel's selection when one is set,
    // else the first game in the filtered list.
    val selectedGame = remember(filteredGames, selectedGameKey) {
        selectedGameKey?.let { key -> filteredGames.firstOrNull { it.key == key } }
            ?: filteredGames.firstOrNull()
    }

    if (unfolded) {
        // Unfolded: no vertical console rail — the consoles are a
        // horizontal row on top and the games are a vertical grid
        // (3 wide with the detail pane open, 5 wide when it is
        // closed). The detail pane keeps the game/port page, with an
        // X to close it.
        UnfoldedLibrary(
            viewModel = viewModel,
            games = filteredGames,
            statuses = statuses,
            pairingCache = pairingCache,
            magnetPairings = magnetPairings,
            selectedGame = selectedGame,
            onSelectGame = onSelectGame,
            onOpenDetail = onOpenDetail,
            onGoToCloud = onGoToCloud,
            onRefresh = { viewModel.refreshCatalog() },
            query = query,
            onQueryChange = { viewModel.setLibraryQuery(it) },
            platformOptions = platformOptions,
            selectedPlatform = platformFilter,
            onPlatformChange = { viewModel.setLibraryPlatform(it) },
            matchesOnly = matchesOnly,
            onMatchesOnlyChange = { viewModel.setLibraryMatchesOnly(it) },
            catalogStatusText = catalogStatusText
        )
    } else {
        GameList(
            games = filteredGames,
            statuses = statuses,
            pairingCache = pairingCache,
            magnetPairings = magnetPairings,
            onOpenGame = onOpenGame,
            modifier = Modifier.fillMaxSize(),
            onRefresh = { viewModel.refreshCatalog() },
            query = query,
            onQueryChange = { viewModel.setLibraryQuery(it) },
            platformOptions = platformOptions,
            selectedPlatform = platformFilter,
            onPlatformChange = { viewModel.setLibraryPlatform(it) },
            matchesOnly = matchesOnly,
            onMatchesOnlyChange = { viewModel.setLibraryMatchesOnly(it) },
            catalogStatusText = catalogStatusText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GameList(
    games: List<GameEntry>,
    statuses: Map<String, PortStatus>,
    pairingCache: Map<String, List<GameFile>>,
    magnetPairings: Map<String, List<GameMagnetLink>>,
    onOpenGame: (String) -> Unit,
    modifier: Modifier,
    onRefresh: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    platformOptions: List<Pair<String, String>>,
    selectedPlatform: String,
    onPlatformChange: (String) -> Unit,
    matchesOnly: Boolean,
    onMatchesOnlyChange: (Boolean) -> Unit,
    catalogStatusText: String
) {
    // Scroll position is saved across navigation (v1.8.2): returning
    // from a game page restores where the list was, instead of jumping
    // back to the top.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = modifier) {
        // The PORTSIDE header stays fixed at the top; everything
        // below it scrolls.
        LibraryHeader(onRefresh = onRefresh)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // The top bar — search, "Has files", the console chips,
            // and the catalog status — scrolls away on a scroll.
            item {
                LibrarySearchRow(
                    query = query,
                    onQueryChange = onQueryChange,
                    matchesOnly = matchesOnly,
                    onMatchesOnlyChange = onMatchesOnlyChange
                )
            }
            if (platformOptions.isNotEmpty()) {
                item {
                    ConsoleChipRow(
                        platformOptions = platformOptions,
                        selectedPlatform = selectedPlatform,
                        onPlatformChange = onPlatformChange
                    )
                }
            }
            item {
                Text(
                    text = catalogStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            // The shelf: larger posters in a horizontal row, one per
            // game. It pins (sticky) directly under the PORTSIDE header
            // once the top bar has scrolled away. There is no
            // selected-game preview below it (v1.8.2) — tapping a
            // poster opens the game page immediately.
            stickyHeader {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(288.dp)
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(games, key = { it.key }) { game ->
                        val anyInstalled = game.ports.any { statuses[it.id]?.isInstalled == true }
                        // The poster tag (v1.8.6): a matched magnet
                        // counts like a matched file — a magnet-only
                        // game ('Splosion Man's Triple Pack magnet)
                        // shows "Magnet matched" with the tag
                        // "json · Magnet". "Has files" is untouched
                        // (magnets still don't count there); this is
                        // the poster's local display only.
                        val matchedFiles = pairingCache[game.key].orEmpty()
                        val matchedMagnets = magnetPairings[game.key].orEmpty()
                        val anyFile = game.ports.any { statuses[it.id]?.hasFile == true } ||
                            matchedFiles.isNotEmpty()
                        val anyMagnet = matchedMagnets.isNotEmpty()
                        val statusText = posterStatusText(anyInstalled, anyFile, anyMagnet)
                        val sourceLabel = posterSourceLabel(matchedFiles, matchedMagnets)
                        GameSpine(
                            game = game,
                            isInstalled = anyInstalled,
                            statusText = statusText,
                            sourceLabel = sourceLabel,
                            onClick = { onOpenGame(game.key) }
                        )
                    }
                }
            }
            // Space below the shelf so the list can scroll (v1.7.0):
            // the top bar collapses and the shelf pins under the
            // header. The shelf is the Library's content now — there
            // is no hero below it — so without this the list would be
            // too short to scroll at all. It adds no visible content:
            // the empty space below the shelf is already there.
            item {
                Spacer(modifier = Modifier.fillParentMaxHeight(0.75f))
            }
        }
    }
}


/**
 * The PORTSIDE header, fixed at the top of the Library in both
 * postures. The refresh button re-fetches the catalog.
 */
@Composable
private fun LibraryHeader(onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PORTSIDE",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh library and catalog")
        }
    }
}

/**
 * The Library search row: the search box and the "Has files" chip
 * beside it. Shared by the folded and unfolded layouts.
 */
@Composable
private fun LibrarySearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    matchesOnly: Boolean,
    onMatchesOnlyChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search games") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // "Has files": only games with at least one matching file
        // (local, cloud, or server). Composes with the platform filter.
        FilterChip(
            selected = matchesOnly,
            onClick = { onMatchesOnlyChange(!matchesOnly) },
            label = { Text("Has files") }
        )
    }
}

/**
 * The console (platform) options as one horizontally scrolling chip
 * row. Folded it is the row under the search box; unfolded it is the
 * row at the top of the Library, replacing the old vertical console
 * rail. The options and the filtering behavior are the same in both
 * postures.
 */
@Composable
private fun ConsoleChipRow(
    platformOptions: List<Pair<String, String>>,
    selectedPlatform: String,
    onPlatformChange: (String) -> Unit
) {
    // One horizontally scrolling row, not a wrapping grid: on the
    // folded screen the old FlowRow ran to ~6 rows and left room for
    // about one port card below it.
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedPlatform.isEmpty(),
            onClick = { onPlatformChange("") },
            label = { Text("All") }
        )
        platformOptions.forEach { (id, name) ->
            FilterChip(
                selected = selectedPlatform == id,
                onClick = { onPlatformChange(id) },
                label = { Text(name) }
            )
        }
    }
}

/**
 * The unfolded Library (v1.7.0): no vertical console rail — the
 * consoles are a horizontal chip row at the top, above a vertically
 * scrolling GRID of games. The grid is 3 columns wide while the
 * detail pane is open and expands to 5 wide when the pane is closed
 * (see [unfoldedGridColumns]). The detail pane shows the selected
 * game's page (or a port's detail page, when one is open from it),
 * with an X in the corner to close it. Selecting a game in the grid
 * reopens the pane for that game.
 */
@Composable
private fun UnfoldedLibrary(
    viewModel: OneViewModel,
    games: List<GameEntry>,
    statuses: Map<String, PortStatus>,
    pairingCache: Map<String, List<GameFile>>,
    magnetPairings: Map<String, List<GameMagnetLink>>,
    selectedGame: GameEntry?,
    onSelectGame: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onGoToCloud: () -> Unit,
    onRefresh: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    platformOptions: List<Pair<String, String>>,
    selectedPlatform: String,
    onPlatformChange: (String) -> Unit,
    matchesOnly: Boolean,
    onMatchesOnlyChange: (Boolean) -> Unit,
    catalogStatusText: String
) {
    val selectedPortId by viewModel.selectedPortId.collectAsState()
    // The detail pane starts open (it shows the selected game) and
    // closes with its X. Selecting a game reopens it.
    var detailOpen by remember { mutableStateOf(true) }
    // Grid scroll position is saved across navigation (v1.8.2), like
    // the folded list's.
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val selectedPort = selectedPortId?.let { viewModel.portById(it) }
    // The pane is only visible when it has something to show; with
    // no game or port selected (an empty Library) the grid takes the
    // full width.
    val detailVisible = detailOpen && (selectedPort != null || selectedGame != null)

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryHeader(onRefresh = onRefresh)
        LibrarySearchRow(
            query = query,
            onQueryChange = onQueryChange,
            matchesOnly = matchesOnly,
            onMatchesOnlyChange = onMatchesOnlyChange
        )
        if (platformOptions.isNotEmpty()) {
            ConsoleChipRow(
                platformOptions = platformOptions,
                selectedPlatform = selectedPlatform,
                onPlatformChange = onPlatformChange
            )
        }
        Text(
            text = catalogStatusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Row(modifier = Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(unfoldedGridColumns(detailVisible)),
                state = gridState,
                modifier = Modifier.weight(if (detailVisible) 0.42f else 1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(games, key = { it.key }) { game ->
                    val anyInstalled = game.ports.any { statuses[it.id]?.isInstalled == true }
                    // The poster tags (v1.8.6): the unfolded grid
                    // posters carry the same status + source tag as
                    // the folded shelf — they were missing here.
                    // A matched magnet counts like a matched file
                    // for the tag (see GameList): a magnet-only game
                    // reads "Magnet matched" with "json · Magnet".
                    val matchedFiles = pairingCache[game.key].orEmpty()
                    val matchedMagnets = magnetPairings[game.key].orEmpty()
                    val anyFile = game.ports.any { statuses[it.id]?.hasFile == true } ||
                        matchedFiles.isNotEmpty()
                    val anyMagnet = matchedMagnets.isNotEmpty()
                    val statusText = posterStatusText(anyInstalled, anyFile, anyMagnet)
                    val sourceLabel = posterSourceLabel(matchedFiles, matchedMagnets)
                    GameGridCard(
                        game = game,
                        selected = game.key == selectedGame?.key,
                        isInstalled = anyInstalled,
                        statusText = statusText,
                        sourceLabel = sourceLabel,
                        onClick = {
                            detailOpen = true
                            onSelectGame(game.key)
                        }
                    )
                }
            }
            if (detailVisible) {
                Box(
                    modifier = Modifier.weight(0.58f).fillMaxHeight()
                ) {
                    if (selectedPort != null) {
                        GameDetailContent(
                            viewModel = viewModel,
                            port = selectedPort,
                            onGoToCloud = onGoToCloud,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (selectedGame != null) {
                        GamePageContent(
                            viewModel = viewModel,
                            game = selectedGame,
                            onOpenDetail = onOpenDetail,
                            onGoToCloud = onGoToCloud,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // The X: closes the detail pane; the grid expands
                    // from 3 wide to 5 wide. Selecting a game reopens it.
                    IconButton(
                        onClick = { detailOpen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close details",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * One card in the unfolded game grid: the game's cover art, portrait,
 * filling the grid column, with its title and platform underneath.
 * Mirrors [GameSpine] (the folded shelf), but fluid-width instead of
 * a fixed 112dp. The selected card carries a lime outline; a game
 * with any port installed carries a small lime play mark on the cover.
 * The status ("Not installed", "File matched", ...) and the file
 * source tag (Premiumize / the remote folder's name / Local) ride on
 * the art, like the shelf (v1.8.6 — they were missing here).
 */
@Composable
private fun GameGridCard(
    game: GameEntry,
    selected: Boolean,
    isInstalled: Boolean,
    statusText: String,
    sourceLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(112f / 160f),
            shape = RoundedCornerShape(8.dp),
            border = if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (game.coverArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = game.coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = game.title.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isInstalled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Status + source, stacked at the bottom of the art
                // (v1.8.6) — the same tags as the folded shelf.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (sourceLabel != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 3.dp
                                )
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = game.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = game.platform,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * One poster on the shelf (v1.8.2): the game's cover art, portrait
 * and larger than the old spine, with its title and platform
 * underneath. Tapping it opens the game page immediately — there is
 * no selected-game preview anymore. The status ("Not installed",
 * "File matched", ...) and the file source tag (Premiumize / the
 * remote folder's name / Local) ride on the art, so the shelf shows
 * at a glance which games have a file and where it lives. A game
 * with any port installed carries a small lime play mark on the cover.
 */
@Composable
private fun GameSpine(
    game: GameEntry,
    isInstalled: Boolean,
    statusText: String,
    sourceLabel: String?,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.width(160.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.size(width = 160.dp, height = 224.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (game.coverArtUrl.isNotBlank()) {
                    AsyncImage(
                        model = game.coverArtUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = game.title.take(1),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isInstalled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Installed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                // Status + source, stacked at the bottom of the art.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (sourceLabel != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.9f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = 6.dp,
                                    vertical = 3.dp
                                )
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = game.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = game.platform,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * File-only signal, next to the StatusChip — it never changes what
 * "Ready to play" means (installed + file). "File on device" when a
 * local, downloaded, or picked file exists for the port; "File in
 * cloud" when a matching file exists in the Premiumize cloud but no
 * file is on this device yet; nothing when neither.
 */
@Composable
private fun FileBadge(status: PortStatus?, hasCloudFile: Boolean) {
    val text = when {
        status?.hasFile == true -> "File on device"
        hasCloudFile -> "File in cloud"
        else -> return
    }
    Spacer(modifier = Modifier.width(4.dp))
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Short label for how a file relates to a port. */
private fun matchKindLabel(kind: MatchKind, innerEntry: String? = null): String =
    when (kind) {
        MatchKind.DIRECT -> "file"
        MatchKind.ARCHIVE_CONFIRMED -> "archive — contains $innerEntry"
        MatchKind.ARCHIVE_PROVISIONAL -> "archive"
        // Name-only is provisional by construction: the catalog lists
        // no formats for this port, so the format was never verified.
        MatchKind.NAME_ONLY -> "name only"
    }

/** One row in the detail page's Local section: name, where it is, how it matches. */
@Composable
private fun LocalFileRow(name: String, where: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "$where · $label",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- Game detail ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameDetailContent(
    viewModel: OneViewModel,
    port: Port,
    onGoToCloud: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statuses by viewModel.portStatuses.collectAsState()
    val status = statuses[port.id]
    val launchError by viewModel.launchError.collectAsState()
    val releaseStates by viewModel.releaseStates.collectAsState()
    val releaseState = releaseStates[port.id]
    val apkStates by viewModel.apkInstallStates.collectAsState()
    val apkState = apkStates[port.id]
    val extractMessages by viewModel.extractMessage.collectAsState()
    val settingsFlow = remember(port.id) { viewModel.settings(port.id) }
    val settings by settingsFlow.collectAsState(initial = PortSettings())
    val pmAccount by viewModel.pmAccount.collectAsState()
    val cloudAllFiles by viewModel.cloudAllFiles.collectAsState()
    val cloudFallback by viewModel.cloudFallback.collectAsState()
    val cloudAllLoading by viewModel.cloudAllLoading.collectAsState()
    val cloudAllLoaded by viewModel.cloudAllLoaded.collectAsState()
    val remoteFolders by viewModel.remoteFolders.collectAsState()
    val serverListings by viewModel.serverListings.collectAsState()
    val serverLoading by viewModel.serverLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val detectedFiles by viewModel.detectedLocalFiles.collectAsState()
    val localFolderUnavailable by viewModel.localFolderUnavailable.collectAsState()
    val copyMessages by viewModel.copyMessage.collectAsState()
    val confirmedArchives by viewModel.confirmedArchives.collectAsState()
    val allPorts by viewModel.portsFlow.collectAsState()
    val selectedForks by viewModel.selectedForks.collectAsState()
    val selectedFork = remember(port, selectedForks) { viewModel.selectedFork(port) }

    // Cloud files matching this port: the flat listAll cache first, the
    // per-port search fallback when listAll failed for this port.
    val cloudMatches = cloudAllFiles.mapNotNull { item ->
        PortMatcher.matchKind(item.name, port, item.link)?.let { item to it }
    }.ifEmpty {
        (cloudFallback[port.id].orEmpty()).mapNotNull { item ->
            PortMatcher.matchKind(item.name, port, item.link)?.let { item to it }
        }
    }

    // Server files matching this port: each configured remote folder's
    // cached listing, matched with the same matcher as the Cloud tab.
    // The detail page is the only place remote entries are matched.
    val serverMatches = remoteFolders.flatMap { source ->
        PortMatcher.matchRemoteEntries(serverListings[source.id].orEmpty(), port)
            .map { (entry, kind) -> Triple(source, entry, kind) }
    }

    // Fetch the latest release when this detail page opens; the ViewModel
    // caches per port for the session, so reopening does not re-hit GitHub.
    // Also make sure this port's cloud files are loaded for the
    // Premiumize section below, and its server files for the Server
    // library section.
    LaunchedEffect(port.id) {
        viewModel.fetchRelease(port.id)
        viewModel.ensureCloudFilesFor(port)
        viewModel.ensureServerFilesFor(port)
    }

    var fps by remember(settings) { mutableStateOf(settings.fpsCap) }
    var scale by remember(settings) { mutableStateOf(settings.resolutionScale) }
    var touchNote by remember(settings) { mutableStateOf(settings.touchLayoutNote) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var pickedName by remember(port.id) { mutableStateOf<String?>(null) }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "selected file"
            pickedName = name
            viewModel.assignFile(port.id, uri, name)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (port.coverArtUrl.isNotBlank()) {
            AsyncImage(
                model = port.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(text = port.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = port.game, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${port.system} · ${port.type} · ${port.repo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (port.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = port.description, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(text = status?.statusText ?: "Not installed")
            FileBadge(
                status = status,
                hasCloudFile = cloudMatches.isNotEmpty()
            )
        }

        if (launchError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = launchError!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.dismissLaunchError() }) {
                Text("Dismiss")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Your file",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refreshLibrary() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh matched files")
            }
        }
        Text(text = port.requiredDump, style = MaterialTheme.typography.bodyMedium)
        // The catalog's own instructions for the required files, shown
        // verbatim — for Pokopia this is the folder requirement (.nsp /
        // .xci not supported, copy the folder, Add game, Prepare).
        if (port.fileInstructions.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = port.fileInstructions,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        // "Not listed" only when there is truly no asset info — a port
        // with a label or instructions but no formats (Pokopia) has file
        // info; it just has no machine-readable formats.
        if (!port.hasFileInfo) {
            Text(
                text = "File info not listed in the catalog.",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (port.fileExtensions.isNotEmpty()) {
            Text(
                text = "Accepted: ${port.fileExtensions.joinToString(", ")}; " +
                    "archives .zip, .7z, .rar are matched by name",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Filename hints: ${port.fileNameHints.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ---- Premiumize: cloud files matching this port ----
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Premiumize", style = MaterialTheme.typography.titleSmall)
        if (pmAccount == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect Premiumize from the Cloud tab to see your cloud files here.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onGoToCloud) {
                Text("Go to Cloud")
            }
        } else if (cloudAllLoading && !cloudAllLoaded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Checking your cloud…", style = MaterialTheme.typography.bodyMedium)
        } else if (cloudMatches.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No file for this port in your Premiumize cloud yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            cloudMatches.forEach { (item, kind) ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, style = MaterialTheme.typography.bodyMedium)
                        val confirmedEntry =
                            if (kind == MatchKind.ARCHIVE_PROVISIONAL) {
                                confirmedArchives[item.name]?.get(port.id)
                            } else {
                                null
                            }
                        Text(
                            text = matchKindLabel(
                                if (confirmedEntry != null) {
                                    MatchKind.ARCHIVE_CONFIRMED
                                } else {
                                    kind
                                },
                                confirmedEntry
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                val progress = downloadProgress[item.id]
                val alreadyDownloaded = status?.downloadedFileName == item.name
                Spacer(modifier = Modifier.height(4.dp))
                when {
                    alreadyDownloaded -> {
                        Text(
                            text = "Downloaded — see Local below.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    progress != null -> {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Downloading… ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> {
                        OutlinedButton(onClick = { viewModel.downloadItem(item) }) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download to this device")
                        }
                    }
                }
            }
            if (downloadError != null) {
                Text(text = downloadError!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.dismissDownloadError() }) {
                    Text("Dismiss")
                }
            }
        }

        // ---- Server library: files in the user's own remote folders matching this port ----
        // Detail page only — remote entries are never matched anywhere
        // else, and nothing here downloads: the button opens the file's
        // own URL in the external browser.
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Server library", style = MaterialTheme.typography.titleSmall)
        if (remoteFolders.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add a remote folder in Settings to see files from your own server here.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (serverLoading.isNotEmpty() && serverListings.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Checking your server library…",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (serverMatches.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No file for this port in your server library yet.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            serverMatches.forEach { (source, entry, kind) ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = entry.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${source.name} · ${matchKindLabel(kind)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = { openUrl(context, entry.url) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in browser")
                }
            }
        }

        // ---- Local: files already on this device ----
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Local", style = MaterialTheme.typography.titleSmall)
        if (localFolderUnavailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Local folder unavailable — pick it again in Settings. " +
                    "Files found there before are still listed below.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        val detectedForPort = detectedFiles[port.id].orEmpty()
        val hasLocal = status?.downloadedFileName != null ||
            status?.assignedFile != null || detectedForPort.isNotEmpty()
        if (!hasLocal) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No file on this device yet — set a local folder in " +
                    "Settings, or browse for a file.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            // Downloaded file in Downloads/ONE.
            if (status?.downloadedFileName != null && status.assignedFile == null) {
                Spacer(modifier = Modifier.height(8.dp))
                LocalFileRow(
                    name = status.downloadedFileName,
                    where = "Downloads/ONE",
                    label = matchKindLabel(
                        status.downloadedMatchKind ?: MatchKind.DIRECT,
                        status.downloadedInnerEntry
                    )
                )
                if (status.downloadedMatchKind == MatchKind.ARCHIVE_CONFIRMED) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.extractMatchedArchive(port.id) }
                    ) {
                        Text("Extract ${status.downloadedInnerEntry} to Downloads/ONE")
                    }
                } else if (status.downloadedMatchKind == MatchKind.ARCHIVE_PROVISIONAL) {
                    Text(
                        text = "Archive — likely contains your file. " +
                            "Refresh to confirm what is inside.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            // Files found in the user's local game-files folder.
            detectedForPort.forEach { detected ->
                Spacer(modifier = Modifier.height(8.dp))
                LocalFileRow(
                    name = detected.name,
                    where = "Found in your folder",
                    label = matchKindLabel(detected.kind)
                )
                if (detected.kind != MatchKind.DIRECT) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { viewModel.copyDetectedToDownloads(port.id, detected) }
                    ) {
                        Text("Copy to Downloads/ONE")
                    }
                }
            }
            // Picked file.
            if (status?.assignedFile != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LocalFileRow(
                    name = status.assignedFile.name,
                    where = "Picked file",
                    label = "file"
                )
                TextButton(onClick = { viewModel.clearAssignment(port.id) }) {
                    Text("Clear my file")
                }
            }
            val extractMessage = extractMessages[port.id]
            if (extractMessage != null) {
                Text(text = extractMessage, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.dismissExtractMessage(port.id) }) {
                    Text("Dismiss")
                }
            }
            val copyMessage = copyMessages[port.id]
            if (copyMessage != null) {
                Text(text = copyMessage, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.dismissCopyMessage(port.id) }) {
                    Text("Dismiss")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
            Text("Browse…")
        }
        val pickedMatches = pickedName?.let { PortMatcher.matchingPorts(it, allPorts) }
            .orEmpty()
            .filter { it.id != port.id }
        if (pickedMatches.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "That file also matches:",
                style = MaterialTheme.typography.bodySmall
            )
            pickedMatches.forEach { other ->
                TextButton(
                    onClick = {
                        val uriString = status?.assignedFile?.uri
                        if (uriString != null) {
                            viewModel.assignFile(other.id, Uri.parse(uriString), pickedName!!)
                        }
                    }
                ) {
                    Text("Assign to ${other.title}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Port", style = MaterialTheme.typography.titleMedium)
        if (status?.isInstalled == true) {
            Text(
                text = "Installed (${status.installedPackage})",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.launchPort(port.id) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Launch ${port.title}")
            }
        } else {
            Text(
                text = if (port.packageNames.isEmpty()) {
                    "No verified Android package for this port yet — this entry is " +
                        "catalog and file-match only. Install from the project's " +
                        "Releases page when an Android build is available."
                } else {
                    "Not installed. Install the port from its Releases page, " +
                        "then come back here to launch it."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openUrl(context, port.releasesUrl) }
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Releases")
            }
        }
        // Latest release, fetched when this page opens (metadata only).
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Latest release", style = MaterialTheme.typography.titleMedium)
        if (port.forks.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Fork", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                port.forks.forEach { fork ->
                    FilterChip(
                        selected = selectedFork?.repo == fork.repo,
                        onClick = { viewModel.selectFork(port.id, fork.repo) },
                        label = {
                            Text(
                                fork.displayName +
                                    fork.latestTag.takeIf { it.isNotBlank() }
                                        ?.let { " · $it" }.orEmpty()
                            )
                        }
                    )
                }
            }
            if (selectedFork != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fork: ${selectedFork.repo}" +
                        selectedFork.packageName
                            ?.let { " · $it" }.orEmpty() +
                        selectedFork.verified
                            ?.let { if (it) " · verified in catalog" else "" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when (val release = releaseState) {
            null, ReleaseState.Loading -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Checking the latest release…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            is ReleaseState.Error -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = release.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.fetchRelease(port.id, force = true) }) {
                    Text("Retry")
                }
            }
            is ReleaseState.Loaded -> {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (release.installedVersion != null) {
                        "Installed: ${release.installedVersion}"
                    } else {
                        "Not installed"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Latest release: ${release.release.tag_name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (release.apk != null) {
                    Text(
                        text = "${release.apk.name} (${formatSize(release.apk.size)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        text = "No Android APK in the latest release.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (release.updateAvailable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Update available — ${release.installedVersion} → " +
                            release.release.tag_name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (release.installedVersion != null && release.apk != null) {
                    Text(text = "Up to date", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (apkState?.downloading == true) {
                    LinearProgressIndicator(
                        progress = { apkState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading… ${(apkState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (apkState?.downloadedFile != null && release.apk != null) {
                    if (apkState.shaVerified) {
                        Text(
                            text = "SHA-256 verified against the catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Button(onClick = { viewModel.installApk(port.id) }) {
                        Text("Install ${release.apk.name}")
                    }
                } else if (release.apk != null &&
                    (release.installedVersion == null || release.updateAvailable)
                ) {
                    Button(onClick = { viewModel.downloadApk(port.id) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download ${release.apk.name}")
                    }
                }
                if (apkState?.blockedByUnknownSources == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android blocked the install: Portside is not yet allowed " +
                            "to install apps. Allow it in system settings, then " +
                            "tap Install again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { viewModel.openUnknownSourcesSettings() }
                    ) {
                        Text("Open install settings")
                    }
                }
                if (apkState?.error != null) {
                    Text(text = apkState.error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.dismissApkError(port.id) }) {
                        Text("Dismiss")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openUrl(context, port.releasesUrl) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Releases")
                    }
                    TextButton(
                        onClick = { openUrl(context, release.release.html_url) }
                    ) {
                        Text("Release page")
                    }
                    TextButton(
                        onClick = { viewModel.fetchRelease(port.id, force = true) }
                    ) {
                        Text("Check for updates")
                    }
                }
            }
        }
        if (port.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = port.notes, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Settings for this port", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Stored on this device. Portside does not enforce settings inside the port app.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "FPS cap", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("30", "60", "120", "Uncapped").forEach { option ->
                FilterChip(
                    selected = fps == option,
                    onClick = { fps = option },
                    label = { Text(option) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Resolution scale", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("0.5x", "1.0x", "1.5x", "2.0x").forEach { option ->
                FilterChip(
                    selected = scale == option,
                    onClick = { scale = option },
                    label = { Text(option) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = touchNote,
            onValueChange = { touchNote = it },
            label = { Text("Touch-layout note") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                viewModel.saveSettings(port.id, PortSettings(fps, scale, touchNote))
                savedMessage = "Saved"
            }
        ) {
            Text("Save settings")
        }
        if (savedMessage != null) {
            Text(text = savedMessage!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---- Game detail (v1.6.0) ----

/**
 * One game's page: its files from all three owned sources, then its
 * recomps / decomps ranked by GitHub stars. The port detail page is
 * unchanged — tapping a port here opens it, exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamePageContent(
    viewModel: OneViewModel,
    game: GameEntry,
    onOpenDetail: (String) -> Unit,
    onGoToCloud: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statuses by viewModel.portStatuses.collectAsState()
    val pmAccount by viewModel.pmAccount.collectAsState()
    val cloudAllFiles by viewModel.cloudAllFiles.collectAsState()
    val cloudFallback by viewModel.cloudFallback.collectAsState()
    val cloudAllLoading by viewModel.cloudAllLoading.collectAsState()
    val cloudAllLoaded by viewModel.cloudAllLoaded.collectAsState()
    val remoteFolders by viewModel.remoteFolders.collectAsState()
    val serverListings by viewModel.serverListings.collectAsState()
    val serverLoading by viewModel.serverLoading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val detectedFiles by viewModel.detectedLocalFiles.collectAsState()
    val localFolderUnavailable by viewModel.localFolderUnavailable.collectAsState()
    val copyMessages by viewModel.copyMessage.collectAsState()
    val extractMessages by viewModel.extractMessage.collectAsState()
    val repoStars by viewModel.repoStars.collectAsState()
    val pairingCache by viewModel.pairingCache.collectAsState()
    val magnetPairings by viewModel.magnetPairings.collectAsState()

    // Ranked ports: GitHub stars when known, else the catalog's
    // download counts, ports with neither last.
    val rankedPorts = remember(game, repoStars) {
        rankPortsByStars(game.ports, repoStars)
    }

    // Your files, aggregated from all three owned sources and deduped
    // by file name (on-device first, then cloud, then server).
    //
    // The live matches are computed OFF the main thread (v1.8.6):
    // the page used to run matchServerFilesForGame +
    // matchMagnetLinksForGame in `remember` during composition — over
    // the full 26k-entry feed, on Main, on every server-listings
    // change. Measured on JVM: the magnet match alone is 379–837ms
    // per run (every magnet goes through the NAME_ONLY similarity
    // path), so a game page blocked the main thread for ~1–4s per
    // run on device, 2–3 runs per open (disk cache load, then fresh
    // fetch) — Garrett's ~10s page load. The page now shows the
    // pairing cache immediately (below) and the live match fills in
    // from the background when it lands; the stars fetch and cover
    // art were already async and never blocked the page.
    val liveCloudMatches by produceState(
        initialValue = emptyList<GameFile>(),
        cloudAllFiles, cloudFallback, game
    ) {
        value = withContext(Dispatchers.Default) {
            val items = if (cloudAllFiles.isNotEmpty()) {
                cloudAllFiles
            } else {
                game.ports.flatMap { cloudFallback[it.id].orEmpty() }
            }
            matchCloudFilesForGame(items, game.ports)
        }
    }
    val liveServerMatches by produceState(
        initialValue = emptyList<GameFile>(),
        remoteFolders, serverListings, game
    ) {
        value = withContext(Dispatchers.Default) {
            val sources = remoteFolders.map { it.name to serverListings[it.id].orEmpty() }
            matchServerFilesForGame(sources, game.ports)
        }
    }
    val localMatches = remember(detectedFiles, statuses, game) {
        val states = game.ports.associate { port ->
            val status = statuses[port.id]
            port.id to LocalFileState(
                downloadedFileName = status?.downloadedFileName,
                downloadedMatchKind = status?.downloadedMatchKind,
                downloadedInnerEntry = status?.downloadedInnerEntry,
                assignedFileName = status?.assignedFile?.name
            )
        }
        matchLocalFilesForGame(detectedFiles, states, game.ports)
    }
    // Cached files for this game (v1.8.2): the pairing cache
    // pre-populates the page before the live sources finish loading.
    // Live files win on a name clash — the cached copy only fills a
    // gap the live match has not produced yet. While the live match
    // is still computing in the background, the live lists are empty
    // and the page is exactly the cached list, so it shows instantly.
    val cachedForGame = pairingCache[game.key].orEmpty()
    val gameFiles = remember(liveCloudMatches, liveServerMatches, localMatches, cachedForGame) {
        mergeLiveAndCachedFiles(
            localMatches + liveCloudMatches + liveServerMatches, cachedForGame
        )
    }

    // Magnet links (v1.8.3): matched from the remote folders by name
    // through the same matcher — a magnet is a copyable link, not a
    // file, so it is shown in its own section below, never in "Your
    // files". Cached magnets pre-populate the section the same way
    // cached files pre-populate "Your files": the live match wins on
    // a magnet-URI clash, the cached copy only fills a gap. The live
    // magnet match is computed off Main like the file match above
    // (v1.8.6) — it is the expensive half of the page's matching.
    val liveServerMagnets by produceState(
        initialValue = emptyList<GameMagnetLink>(),
        remoteFolders, serverListings, game
    ) {
        value = withContext(Dispatchers.Default) {
            val sources = remoteFolders.map { it.name to serverListings[it.id].orEmpty() }
            matchMagnetLinksForGame(sources, game.ports)
        }
    }
    val cachedMagnetsForGame = magnetPairings[game.key].orEmpty()
    val gameMagnets = remember(liveServerMagnets, cachedMagnetsForGame) {
        mergeLiveAndCachedMagnets(liveServerMagnets, cachedMagnetsForGame)
    }

    // Fetch this game's star counts and make sure its cloud and server
    // files are loaded; both are cached, so reopening does not re-hit
    // either API.
    LaunchedEffect(game.key) {
        viewModel.fetchStarsForGame(game)
        viewModel.ensureGameFilesFor(game)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (game.coverArtUrl.isNotBlank()) {
            AsyncImage(
                model = game.coverArtUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(text = game.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = game.platform, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (game.ports.size == 1) "1 port" else "${game.ports.size} ports",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Your files ----
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Your files",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.refreshLibrary() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh matched files")
            }
        }
        if (localFolderUnavailable) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Local folder unavailable — pick it again in Settings. " +
                    "Files found there before are still listed below.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (pmAccount == null && remoteFolders.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Set a local folder in Settings, connect Premiumize " +
                    "from the Cloud tab, or add a remote folder to see " +
                    "your files for this game here.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onGoToCloud) {
                Text("Go to Cloud")
            }
        } else if (gameFiles.isNotEmpty()) {
            // Cached files show immediately (v1.8.4): gameFiles already
            // includes the pairing cache, so a game with cached files
            // never sits on "Checking your files…" while the live
            // sources (the 36 MB remote.json) are still loading — the
            // live match refreshes the list when it lands.
            gameFiles.forEach { file ->
                Spacer(modifier = Modifier.height(8.dp))
                GameFileRow(
                    file = file,
                    downloadProgress = downloadProgress[file.premiumizeItem?.id],
                    onDownload = { item -> viewModel.downloadItem(item) },
                    onOpenInBrowser = { url -> openUrl(context, url) },
                    onCopyDetected = { portId, detected ->
                        viewModel.copyDetectedToDownloads(portId, detected)
                    },
                    onExtract = { portId -> viewModel.extractMatchedArchive(portId) }
                )
            }
        } else if ((cloudAllLoading && !cloudAllLoaded) ||
            (serverLoading.isNotEmpty() && serverListings.isEmpty())
        ) {
            // Only when there is truly nothing yet — no cached files
            // and the sources are still loading — is the page still
            // checking.
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Checking your files…", style = MaterialTheme.typography.bodyMedium)
        } else {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No file for this game yet — in your local folder, " +
                    "your Premiumize cloud, or your server library.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (downloadError != null) {
            Text(text = downloadError!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.dismissDownloadError() }) {
                Text("Dismiss")
            }
        }
        game.ports.forEach { port ->
            copyMessages[port.id]?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.dismissCopyMessage(port.id) }) {
                    Text("Dismiss")
                }
            }
            extractMessages[port.id]?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { viewModel.dismissExtractMessage(port.id) }) {
                    Text("Dismiss")
                }
            }
        }

        // ---- Magnet links (v1.8.3) ----
        // Copyable links from the remote folders, matched to this game
        // by name through the same matcher. They are not files — no
        // download, never opened in the browser, and they never count
        // toward "Your files" or "Has files". Shown only when the
        // game has at least one; a game with magnets but no files
        // still reads "No file for this game yet" above, which is
        // accurate — its only links are magnets.
        if (gameMagnets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Magnet links", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Copy a link to use it elsewhere. Magnets are " +
                    "never downloaded by this app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            gameMagnets.forEach { magnet ->
                Spacer(modifier = Modifier.height(8.dp))
                GameMagnetRow(magnet = magnet)
            }
        }

        // ---- Recomps / decomps ----
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Recomps / decomps", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ranked by GitHub stars. When stars cannot be loaded, " +
                "the catalog's download counts decide the order instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        rankedPorts.forEach { port ->
            Spacer(modifier = Modifier.height(12.dp))
            val status = statuses[port.id]
            val stars = repoStars[port.repo]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = port.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = port.repo +
                            (stars?.let { " · $it stars" }
                                ?: port.catalogDownloads?.let { " · $it downloads" }
                                ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(text = status?.statusText ?: "Not installed")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status?.isInstalled == true) {
                    Button(onClick = { viewModel.launchPort(port.id) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play")
                    }
                }
                OutlinedButton(onClick = { onOpenDetail(port.id) }) {
                    Text("Details")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** One row in the game page's Your files section: name, source, match, action. */
@Composable
private fun GameFileRow(
    file: GameFile,
    downloadProgress: Float?,
    onDownload: (PremiumizeItem) -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onCopyDetected: (String, DetectedFile) -> Unit,
    onExtract: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (file.source) {
                GameFileSource.LOCAL -> Icons.Filled.Check
                GameFileSource.CLOUD -> Icons.Filled.Cloud
                GameFileSource.SERVER -> Icons.Filled.Storage
            },
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = file.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${file.where} · ${matchKindLabel(file.kind, file.downloadedInnerEntry)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // The source tag (v1.8.2): where the file comes from, as a
        // clear chip — Premiumize, the remote folder's name, or Local.
        // The match kind stays the secondary label in the subtitle.
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = fileSourceLabel(file),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    when (file.source) {
        GameFileSource.CLOUD -> {
            val item = file.premiumizeItem
            if (item != null) {
                if (downloadProgress != null) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading… ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    OutlinedButton(onClick = { onDownload(item) }) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download to this device")
                    }
                }
            }
        }
        GameFileSource.SERVER -> {
            val url = file.remoteUrl
            if (url != null) {
                OutlinedButton(onClick = { onOpenInBrowser(url) }) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open in browser")
                }
            }
        }
        GameFileSource.LOCAL -> {
            val detected = file.detected
            if (detected != null && file.kind != MatchKind.DIRECT) {
                OutlinedButton(onClick = { onCopyDetected(file.portId, detected) }) {
                    Text("Copy to Downloads/ONE")
                }
            } else if (file.downloadedInnerEntry != null) {
                OutlinedButton(onClick = { onExtract(file.portId) }) {
                    Text("Extract ${file.downloadedInnerEntry} to Downloads/ONE")
                }
            } else if (file.kind == MatchKind.ARCHIVE_PROVISIONAL) {
                Text(
                    text = "Archive — likely contains your file. " +
                        "Refresh to confirm what is inside.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** One row in the game page's Magnet links section: name, Magnet chip, source, Copy link. */
@Composable
private fun GameMagnetRow(magnet: GameMagnetLink) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = magnet.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${magnet.where} · ${matchKindLabel(magnet.kind)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // The Magnet chip (v1.8.0): a magnet is a copyable link, not
        // a file, so it is visibly different from a downloadable file.
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "Magnet",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    // Copy link only — never a Download button, never Open in browser.
    // (The clipboard write itself is UI and is not JVM-testable; the
    // link the button copies is the magnet URI itself.)
    OutlinedButton(onClick = {
        val link = magnet.magnetUrl.takeIf { it.isNotBlank() }
        if (link != null) {
            val clipboard = context.getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("Magnet link", link)
            )
            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Link unavailable", Toast.LENGTH_SHORT).show()
        }
    }) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Copy link")
    }
}

// ---- Cloud (Premiumize, own cloud only) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudScreen(
    viewModel: OneViewModel,
    onOpenDetail: (String) -> Unit
) {
    val account by viewModel.pmAccount.collectAsState()
    val connecting by viewModel.pmConnecting.collectAsState()
    val pmError by viewModel.pmError.collectAsState()
    val items by viewModel.cloudItems.collectAsState()
    val trail by viewModel.folderTrail.collectAsState()
    val loading by viewModel.cloudLoading.collectAsState()
    val cloudError by viewModel.cloudError.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val confirmedArchives by viewModel.confirmedArchives.collectAsState()
    val ports by viewModel.portsFlow.collectAsState()

    var apiKey by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(text = "Cloud", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Premiumize — your own cloud only. Browse, search, and pull " +
                "files you already have stored there to this device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (account == null) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Premiumize API key") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.connectPremiumize(apiKey) },
                enabled = !connecting
            ) {
                Text(if (connecting) "Testing…" else "Test connection")
            }
            Text(
                text = "Find your API key under Account on premiumize.me. " +
                    "It is stored only on this device, in encrypted storage.",
                style = MaterialTheme.typography.bodySmall
            )
            if (pmError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = pmError!!, color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Customer ${account!!.customer_id}")
                Text(
                    text = "Premium until ${formatPremiumUntil(account!!.premium_until)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            IconButton(onClick = { viewModel.refreshCloud() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh cloud")
            }
            OutlinedButton(onClick = { viewModel.disconnectPremiumize() }) {
                Text("Disconnect")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search my cloud") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.searchCloud(searchQuery) }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
        Text(
            text = "Searches only your Premiumize cloud.",
            style = MaterialTheme.typography.bodySmall
        )
        if (searchResults != null) {
            TextButton(onClick = {
                viewModel.clearSearch()
                searchQuery = ""
            }) {
                Text("Clear search — back to browse")
            }
        }

        if (searchResults == null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                trail.forEachIndexed { index, name ->
                    if (index > 0) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    TextButton(onClick = { viewModel.navigateToBreadcrumb(index) }) {
                        Text(name)
                    }
                }
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        if (cloudError != null) {
            Text(text = cloudError!!, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = {
                if (searchResults != null) viewModel.searchCloud(searchQuery)
                else viewModel.browseFolder(null)
            }) {
                Text("Retry")
            }
        }
        if (downloadError != null) {
            Text(text = downloadError!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.dismissDownloadError() }) {
                Text("Dismiss")
            }
        }

        val shown = searchResults ?: items
        if (!loading && cloudError == null && shown.isEmpty()) {
            Text(
                text = if (searchResults != null) {
                    "No files in your cloud matched that search."
                } else {
                    "This folder is empty."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.id + it.name }) { item ->
                CloudItemRow(
                    item = item,
                    ports = ports,
                    progress = downloadProgress[item.id],
                    confirmedArchives = confirmedArchives,
                    onOpenFolder = { viewModel.openFolder(item) },
                    onDownload = { viewModel.downloadItem(item) },
                    onOpenDetail = onOpenDetail
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudItemRow(
    item: PremiumizeItem,
    ports: List<Port>,
    progress: Float?,
    confirmedArchives: Map<String, Map<String, String>>,
    onOpenFolder: () -> Unit,
    onDownload: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.type == "folder") Icons.Filled.Folder else Icons.Filled.Cloud,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.type != "folder") {
                        Text(
                            text = formatSize(item.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.type == "folder") {
                    IconButton(onClick = onOpenFolder) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Open folder")
                    }
                }
            }
            if (item.type != "folder") {
                val matches = PortMatcher.matchKinds(item.name, ports, item.link)
                if (matches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // FlowRow, not a plain Row: a Row gives each chip past
                    // the first the leftover width (often ~0), so a long
                    // chip label wraps to one word per line and the row
                    // measures many lines tall — the card balloons to ~2x
                    // its content with a big empty region below the
                    // visible chips. Chips wrap onto real lines here.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        matches.forEach { match ->
                            val label = when (match.kind) {
                                MatchKind.DIRECT -> "Matches ${match.port.title}"
                                MatchKind.ARCHIVE_PROVISIONAL -> {
                                    // Already downloaded and confirmed on
                                    // this device? Say what it contains.
                                    val entry =
                                        confirmedArchives[item.name]?.get(match.port.id)
                                    if (entry != null) {
                                        "Matches ${match.port.title} " +
                                            "(contains .${entry.substringAfterLast('.', "")})"
                                    } else {
                                        "Matches ${match.port.title} (archive)"
                                    }
                                }
                                MatchKind.ARCHIVE_CONFIRMED ->
                                    "Matches ${match.port.title} (archive)"
                                // Name-only: the catalog lists no formats
                                // for this port, so say so on the chip.
                                MatchKind.NAME_ONLY ->
                                    "Matches ${match.port.title} (name only)"
                            }
                            AssistChip(
                                onClick = { onOpenDetail(match.port.id) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading… ${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    OutlinedButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download to this device")
                    }
                }
            }
        }
    }
}

// ---- Settings ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    viewModel: OneViewModel,
    onOpenRemoteFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val account by viewModel.pmAccount.collectAsState()
    val statuses by viewModel.portStatuses.collectAsState()
    val assignedFiles by viewModel.assignedFiles.collectAsState()
    val localFolder by viewModel.localFolder.collectAsState()
    val localFolderUnavailable by viewModel.localFolderUnavailable.collectAsState()
    val customLinks by viewModel.customLinks.collectAsState()
    val customLinkError by viewModel.customLinkError.collectAsState()
    val remoteFolders by viewModel.remoteFolders.collectAsState()
    val remoteFolderError by viewModel.remoteFolderError.collectAsState()
    val ports by viewModel.portsFlow.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var linkName by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var folderUrl by remember { mutableStateOf("") }
    var folderUser by remember { mutableStateOf("") }
    var folderPass by remember { mutableStateOf("") }
    /** The source whose display format is being edited, if any. */
    var formatEditingId by remember { mutableStateOf<String?>(null) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment ?: "Selected folder"
            viewModel.setLocalFolder(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(text = "Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Downloads", style = MaterialTheme.typography.titleMedium)
        Text(
            text = viewModel.downloadsDirPath(),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Files you pull from your Premiumize cloud land here, " +
                "in Portside's own Downloads folder.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Local game files folder", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Like a ROMs folder in an emulator: Portside pre-searches this " +
                "folder for files matching your ports and remembers what it " +
                "found, so the Library badges show up even before you open " +
                "a game's page.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (localFolderUnavailable) {
            Text(
                text = "Folder unavailable — pick it again. Files found there " +
                    "before are still shown, marked stale.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (localFolder != null) {
            Text(
                text = localFolder!!.displayName,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFolder.launch(null) }) {
                    Text("Change")
                }
                OutlinedButton(onClick = {
                    viewModel.clearLocalFolder()
                    message = "Local folder cleared. Picked files are untouched."
                }) {
                    Text("Clear")
                }
            }
        } else {
            OutlinedButton(onClick = { pickFolder.launch(null) }) {
                Icon(Icons.Filled.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose folder")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Matched files", style = MaterialTheme.typography.titleMedium)
        val matchedRows = ports.mapNotNull { port ->
            val status = statuses[port.id]
            val name = assignedFiles[port.id]?.name ?: status?.downloadedFileName
            if (name == null) null else port to name
        }
        if (matchedRows.isEmpty()) {
            Text(
                text = "No files matched yet. Pick a file on a game's detail " +
                    "page, or pull one from your cloud.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            matchedRows.forEach { (port, name) ->
                val status = statuses[port.id]
                val detail = when {
                    assignedFiles[port.id] != null -> "picked file"
                    status?.downloadedMatchKind == MatchKind.ARCHIVE_CONFIRMED ->
                        "archive — contains ${status.downloadedInnerEntry}"
                    status?.downloadedMatchKind == MatchKind.ARCHIVE_PROVISIONAL ->
                        "archive (likely match)"
                    status?.downloadedMatchKind == MatchKind.NAME_ONLY ->
                        "name-only match"
                    status?.downloadedMatchKind == MatchKind.DIRECT -> "downloaded file"
                    else -> "matched file"
                }
                Text(
                    text = "${port.title} — $name ($detail)",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.clearAllAssignments()
                message = "Matched files cleared."
            }
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear matched files")
        }
        Text(
            text = "This clears picked files only — files found in your " +
                "local folder stay detected.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Premiumize", style = MaterialTheme.typography.titleMedium)
        if (account != null) {
            Text(text = "Connected as customer ${account!!.customer_id}")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = {
                viewModel.disconnectPremiumize()
                message = "Premiumize disconnected. Your API key was removed from this device."
            }) {
                Text("Disconnect Premiumize")
            }
        } else {
            Text(
                text = "Not connected. Link your account from the Cloud tab.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message!!, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Custom links", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Shortcuts to your own pages. Tapping one opens it in " +
                "your browser — Portside never downloads from these links.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (customLinks.isEmpty()) {
            Text(
                text = "No custom links yet. Add shortcuts to your own pages — " +
                    "for example, your own self-hosted file archive. Portside only " +
                    "opens these in your browser; it never downloads from them.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            customLinks.forEach { link ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        TextButton(onClick = { openUrl(context, link.url) }) {
                            Text(link.name)
                        }
                        Text(
                            text = Uri.parse(link.url).host ?: link.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.removeCustomLink(link) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${link.name}"
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = linkName,
            onValueChange = { linkName = it },
            label = { Text("Name") },
            placeholder = { Text("My archive") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = linkUrl,
            onValueChange = { linkUrl = it },
            label = { Text("URL") },
            placeholder = { Text("https://example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (viewModel.addCustomLink(linkName, linkUrl)) {
                    linkName = ""
                    linkUrl = ""
                }
            }
        ) {
            Text("Add link")
        }
        if (customLinkError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = customLinkError!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Remote folders", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Your own file servers, browsed inside Portside. A folder " +
                "must be served as a standard directory listing or a " +
                "simple file list (a JSON array of {name, url}). Files " +
                "you download land in your local game-files folder when " +
                "one is set, otherwise in Downloads/ONE — and the usual " +
                "local-folder scan pairs them with their ports.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (remoteFolders.isEmpty()) {
            Text(
                text = "No remote folders yet. Add one to browse it from here.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            remoteFolders.forEach { source ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            TextButton(onClick = { onOpenRemoteFolder(source.id) }) {
                                Text(source.name)
                            }
                            Text(
                                text = Uri.parse(source.url).host ?: source.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                formatEditingId =
                                    if (formatEditingId == source.id) null else source.id
                            }
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = "Display format for ${source.name}"
                            )
                        }
                        IconButton(onClick = { viewModel.removeRemoteFolder(source) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${source.name}"
                            )
                        }
                    }
                    if (formatEditingId == source.id) {
                        key(source.id) {
                            RemoteDisplayFormatEditor(
                                source = source,
                                onSave = { format ->
                                    viewModel.updateRemoteFolderDisplayFormat(source.id, format)
                                    formatEditingId = null
                                },
                                onCancel = { formatEditingId = null }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = folderName,
            onValueChange = { folderName = it },
            label = { Text("Name") },
            placeholder = { Text("My server") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = folderUrl,
            onValueChange = { folderUrl = it },
            label = { Text("URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = folderUser,
            onValueChange = { folderUser = it },
            label = { Text("Username (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = folderPass,
            onValueChange = { folderPass = it },
            label = { Text("Password (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                if (viewModel.addRemoteFolder(folderName, folderUrl, folderUser, folderPass)) {
                    folderName = ""
                    folderUrl = ""
                    folderUser = ""
                    folderPass = ""
                }
            }
        ) {
            Text("Add folder")
        }
        if (remoteFolderError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = remoteFolderError!!, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "About", style = MaterialTheme.typography.titleMedium)
        Text(text = "Portside 1.6.0 (release build)", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { openUrl(context, "https://recompa.app") }) {
            Text(
                text = "Catalog data: Recompa (recompa.app), GPL-3.0 — ports are " +
                    "their authors' own projects; Portside lists them, it does " +
                    "not host them."
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = viewModel.catalogNote,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Portside is not affiliated with Nintendo, HarbourMasters, " +
                "Premiumize, or any port author. The ports listed are the work " +
                "of their own authors — see each project's repository. Only use " +
                "game files you legally own and dumped yourself. Premiumize " +
                "access is your own cloud only: no transfers, no cache search, " +
                "no torrents, no hoster links.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Per-source display-format editor, shown inline under a remote folder
 * in Settings. Display only: it reformats how entries are shown in
 * the browser; the files, the download, and any matching still use
 * the real file name.
 */
@Composable
private fun RemoteDisplayFormatEditor(
    source: RemoteFolderSource,
    onSave: (RemoteDisplayFormat) -> Unit,
    onCancel: () -> Unit
) {
    val format = source.displayFormat
    var stripBrackets by remember { mutableStateOf(format.stripBrackets) }
    var stripParens by remember { mutableStateOf(format.stripParens) }
    var underscoresToSpaces by remember { mutableStateOf(format.underscoresToSpaces) }
    var showSize by remember { mutableStateOf(format.showSize) }
    var hideTypes by remember {
        mutableStateOf(format.hideExtensions.joinToString(", "))
    }
    val pairs = remember {
        mutableStateListOf<FindReplace>().apply { addAll(format.replacements) }
    }
    var pairFind by remember { mutableStateOf("") }
    var pairReplace by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp)) {
        Text(
            text = "Display format — ${source.name}",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = "Changes how entries look in the browser only. Files, " +
                "downloads, and matching still use the real file name; " +
                "long-press a row in the browser to see it.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        FormatSwitchRow("Strip [brackets]", stripBrackets) { stripBrackets = it }
        FormatSwitchRow("Strip (short tags)", stripParens) { stripParens = it }
        FormatSwitchRow("Underscores → spaces", underscoresToSpaces) { underscoresToSpaces = it }
        FormatSwitchRow("Show file size", showSize) { showSize = it }
        Text(
            text = "Size shows only when the server's file list includes one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hideTypes,
            onValueChange = { hideTypes = it },
            label = { Text("Hide file types") },
            placeholder = { Text("txt, nfo, md") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Comma-separated extensions. Hidden files are not shown " +
                "in the browser; folders are never hidden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Find → replace", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Literal text, applied in order — not patterns. " +
                "Use it for your own cleanups, e.g. find \"v655360\".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        pairs.forEachIndexed { index, pair ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "\"${pair.find}\" → \"${pair.replace}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { pairs.removeAt(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove this replacement")
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pairFind,
                onValueChange = { pairFind = it },
                label = { Text("Find") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = pairReplace,
                onValueChange = { pairReplace = it },
                label = { Text("Replace with") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (pairFind.isNotEmpty()) {
                        pairs.add(FindReplace(pairFind, pairReplace))
                        pairFind = ""
                        pairReplace = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add replacement")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row {
            Button(
                onClick = {
                    onSave(
                        RemoteDisplayFormat(
                            stripBrackets = stripBrackets,
                            stripParens = stripParens,
                            underscoresToSpaces = underscoresToSpaces,
                            replacements = pairs.toList(),
                            showSize = showSize,
                            hideExtensions = parseHiddenExtensions(hideTypes)
                        )
                    )
                }
            ) {
                Text("Save format")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun FormatSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---- Remote folders ----

/**
 * The isolated remote-folder browser: one user-configured source's
 * directory listing, on its own screen. Remote entries never appear in
 * the Library or any port matching — a file only becomes visible to the
 * app once it lands on disk.
 */
@Composable
private fun RemoteBrowserScreen(viewModel: OneViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by viewModel.remoteBrowser.collectAsState()
    val format by viewModel.remoteBrowserFormat.collectAsState()
    val progress by viewModel.remoteDownloadProgress.collectAsState()
    val downloaded by viewModel.remoteDownloaded.collectAsState()
    val downloadError by viewModel.remoteDownloadError.collectAsState()
    /** The entry whose real file name is shown in the long-press dialog, if any. */
    var realNameEntry by remember { mutableStateOf<RemoteEntry?>(null) }
    Column(modifier = modifier.padding(16.dp)) {
        when (val s = state) {
            RemoteBrowserState.Idle -> {
                Text(
                    text = "Pick a remote folder from Settings to browse it here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            RemoteBrowserState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is RemoteBrowserState.Error -> {
                Text(text = s.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.refreshRemoteBrowser() }) {
                    Text("Retry")
                }
            }
            is RemoteBrowserState.Loaded -> {
                if (s.trail.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        s.trail.forEachIndexed { index, item ->
                            if (index > 0) {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            TextButton(onClick = { viewModel.navigateRemoteTrail(index) }) {
                                Text(item.name)
                            }
                        }
                    }
                }
                if (downloadError != null) {
                    Text(text = downloadError!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.dismissRemoteDownloadError() }) {
                        Text("Dismiss")
                    }
                }
                // Hide filtering is display only: the parser still read
                // every entry, and the download still uses the real name.
                val visibleEntries = s.entries.filter { shouldShowRemoteEntry(it, format) }
                if (s.entries.isEmpty()) {
                    Text(
                        text = "This folder is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else if (visibleEntries.isEmpty()) {
                    Text(
                        text = "All entries in this folder are hidden by this " +
                            "source's display format.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleEntries, key = { it.url }) { entry ->
                        RemoteBrowserRow(
                            entry = entry,
                            format = format,
                            progress = progress[entry.url],
                            savedTo = downloaded[entry.url],
                            onOpenFolder = { viewModel.navigateRemoteEntry(entry) },
                            onDownload = { viewModel.downloadRemoteFile(entry) },
                            onShowRealName = { realNameEntry = it }
                        )
                    }
                }
            }
        }
        val dialogEntry = realNameEntry
        if (dialogEntry != null) {
            AlertDialog(
                onDismissRequest = { realNameEntry = null },
                title = { Text(if (dialogEntry.isFolder) "Folder name" else "File name") },
                text = { Text(dialogEntry.name) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("File name", dialogEntry.name)
                            )
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            realNameEntry = null
                        }
                    ) {
                        Text("Copy")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { realNameEntry = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteBrowserRow(
    entry: RemoteEntry,
    format: RemoteDisplayFormat,
    progress: Float?,
    savedTo: String?,
    onOpenFolder: () -> Unit,
    onDownload: () -> Unit,
    onShowRealName: (RemoteEntry) -> Unit
) {
    // The displayed title is formatted; the download, the folder
    // navigation, and everything else still use entry.name verbatim.
    val context = LocalContext.current
    val formatted = formatRemoteTitle(entry.name, entry.isFolder, format)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { onShowRealName(entry) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entry.isFolder) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatted.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (entry.isMagnet) {
                    // A magnet entry is a copyable link, not a file:
                    // it gets a Magnet chip instead of an extension
                    // chip, so it is visibly different from a
                    // downloadable file.
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Magnet",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (formatted.extension != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = ".${formatted.extension}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (entry.isFolder) {
                    IconButton(onClick = onOpenFolder) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Open folder")
                    }
                }
            }
            if (!entry.isFolder && format.showSize && entry.sizeBytes != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatSize(entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!entry.isFolder) {
                Spacer(modifier = Modifier.height(8.dp))
                if (entry.isMagnet) {
                    // A magnet entry is a copyable link, not a file:
                    // tapping it copies the magnet URI to the
                    // clipboard. It never opens in the browser and
                    // never downloads. (The clipboard write itself
                    // is UI and is not JVM-testable; the link the
                    // button copies is magnetLinkToCopy, pure and
                    // unit-tested.)
                    OutlinedButton(onClick = {
                        val link = magnetLinkToCopy(entry)
                        if (link != null) {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Magnet link", link)
                            )
                            Toast.makeText(
                                context, "Link copied", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context, "Link unavailable",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy link")
                    }
                } else {
                    when {
                        savedTo != null -> {
                            Text(
                                text = savedTo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        progress != null -> {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Downloading… ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        else -> {
                            OutlinedButton(onClick = onDownload) {
                                Icon(Icons.Filled.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download to this device")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Helpers ----

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return "%.1f %s".format(value, units[index])
}

private fun formatPremiumUntil(unixSeconds: Long): String {
    if (unixSeconds <= 0) return "unknown"
    val date = Date(unixSeconds * 1000)
    return SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
}

