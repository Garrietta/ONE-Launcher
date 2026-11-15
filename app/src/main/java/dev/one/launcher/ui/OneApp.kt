package dev.one.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.one.launcher.data.PortMatcher
import dev.one.launcher.data.PortSettings
import dev.one.launcher.data.PremiumizeItem
import dev.one.launcher.model.Port
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
                                currentRoute?.startsWith("detail/") == true)
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
                                currentRoute?.startsWith("detail/") == true)
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
                        onOpenDetail = { id ->
                            if (unfolded) {
                                viewModel.selectPort(id)
                            } else {
                                navController.navigate("detail/$id")
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
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
                composable(OneDestination.Cloud.route) {
                    CloudScreen(viewModel = viewModel)
                }
                composable(OneDestination.Settings.route) {
                    SettingsScreen(viewModel = viewModel)
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
    onOpenDetail: (String) -> Unit
) {
    val statuses by viewModel.portStatuses.collectAsState()
    val selectedId by viewModel.selectedPortId.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshLibrary() }

    if (unfolded) {
        Row(modifier = Modifier.fillMaxSize()) {
            PortList(
                ports = viewModel.ports,
                statuses = statuses,
                selectedId = selectedId,
                onSelect = onOpenDetail,
                modifier = Modifier.weight(0.42f),
                onRefresh = { viewModel.refreshLibrary() }
            )
            val selected = selectedId?.let { viewModel.portById(it) }
                ?: viewModel.ports.firstOrNull()
            if (selected != null) {
                GameDetailContent(
                    viewModel = viewModel,
                    port = selected,
                    modifier = Modifier.weight(0.58f)
                )
            } else {
                Box(
                    modifier = Modifier.weight(0.58f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Select a game to see its details")
                }
            }
        }
    } else {
        PortList(
            ports = viewModel.ports,
            statuses = statuses,
            selectedId = selectedId,
            onSelect = onOpenDetail,
            modifier = Modifier.fillMaxSize(),
            onRefresh = { viewModel.refreshLibrary() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortList(
    ports: List<Port>,
    statuses: Map<String, PortStatus>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
    onRefresh: () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ONE",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh library")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(ports, key = { it.id }) { port ->
                val status = statuses[port.id]
                Card(
                    onClick = { onSelect(port.id) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (port.id == selectedId) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = port.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(text = status?.statusText ?: "Not installed")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = port.game, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${port.system} · ${port.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val fileName = status?.assignedFile?.name
                            ?: status?.downloadedFileName
                        if (fileName != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "File: $fileName",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
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

// ---- Game detail ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GameDetailContent(
    viewModel: OneViewModel,
    port: Port,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statuses by viewModel.portStatuses.collectAsState()
    val status = statuses[port.id]
    val launchError by viewModel.launchError.collectAsState()
    val settingsFlow = remember(port.id) { viewModel.settings(port.id) }
    val settings by settingsFlow.collectAsState(initial = PortSettings())

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
        Text(text = port.title, style = MaterialTheme.typography.headlineSmall)
        Text(text = port.game, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${port.system} · ${port.type} · ${port.repo}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        StatusChip(text = status?.statusText ?: "Not installed")

        if (launchError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = launchError!!, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { viewModel.dismissLaunchError() }) {
                Text("Dismiss")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Your file", style = MaterialTheme.typography.titleMedium)
        Text(text = port.requiredDump, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Accepted: ${port.fileExtensions.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Filename hints: ${port.fileNameHints.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        val assignedName = status?.assignedFile?.name ?: status?.downloadedFileName
        if (assignedName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = assignedName, modifier = Modifier.weight(1f))
            }
            if (status?.assignedFile != null) {
                TextButton(onClick = { viewModel.clearAssignment(port.id) }) {
                    Text("Clear my file")
                }
            }
        }
        OutlinedButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
            Text("Locate my file")
        }
        val pickedMatches = pickedName?.let { PortMatcher.matchingPorts(it, viewModel.ports) }
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
        if (port.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = port.notes, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Settings for this port", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Stored on this device. ONE does not enforce settings inside the port app.",
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

// ---- Cloud (Premiumize, own cloud only) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudScreen(viewModel: OneViewModel) {
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
                    ports = viewModel.ports,
                    progress = downloadProgress[item.id],
                    onOpenFolder = { viewModel.openFolder(item) },
                    onDownload = { viewModel.downloadItem(item) }
                )
            }
        }
    }
}

@Composable
private fun CloudItemRow(
    item: PremiumizeItem,
    ports: List<Port>,
    progress: Float?,
    onOpenFolder: () -> Unit,
    onDownload: () -> Unit
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
                val matches = PortMatcher.matchingPorts(item.name, ports)
                if (matches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        matches.forEach { match ->
                            AssistChip(
                                onClick = {},
                                label = { Text("Matches ${match.title}") }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
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

@Composable
private fun SettingsScreen(viewModel: OneViewModel) {
    val account by viewModel.pmAccount.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }

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
                "in ONE's own Downloads folder.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Matched files", style = MaterialTheme.typography.titleMedium)
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

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "About", style = MaterialTheme.typography.titleMedium)
        Text(text = "ONE 1.0.0 (debug build)", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = viewModel.catalogNote,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "ONE is not affiliated with Nintendo, HarbourMasters, " +
                "Premiumize, or any port author. The ports listed are the work " +
                "of their own authors — see each project's repository. Only use " +
                "game files you legally own and dumped yourself. Premiumize " +
                "access is your own cloud only: no transfers, no cache search, " +
                "no torrents, no hoster links.",
            style = MaterialTheme.typography.bodySmall
        )
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
