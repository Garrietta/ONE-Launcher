package dev.one.launcher.ui

import dev.one.launcher.ui.theme.GRAIN_AMOUNT
import dev.one.launcher.ui.theme.grainBrush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.GameFile
import dev.one.launcher.data.GameMagnetLink
import dev.one.launcher.data.PlatformTint
import dev.one.launcher.data.posterSourceLabel
import dev.one.launcher.data.posterStatusText
import dev.one.launcher.ui.theme.MarqueeGlass
import dev.one.launcher.ui.theme.MarqueeInk
import dev.one.launcher.ui.theme.MarqueeInkMuted
import kotlin.math.abs

/*
 * The Marquee showcase (v1.9.0) — the Library, redesigned after the
 * "ONE" handheld concept video, for touch screens with no gamepad:
 *
 *  - the whole screen is washed in the selected game's platform color;
 *  - games stand on a shelf as spines, and the selected one is pulled
 *    forward as a full cover with a white frame;
 *  - the selected game's title is set huge underneath, with one solid
 *    Play/Open pill and a translucent Details pill;
 *  - small collections ("Ready to play", "Has files") sit at the bottom.
 *
 * Touch, not buttons: tap a spine to pull it forward, tap the cover to
 * open the game, swipe left/right across the title to step through the
 * shelf. There are no button hints and no bottom navigation bar — Cloud
 * and Settings live as icons in the top bar. Wide windows (tablets,
 * unfolded foldables, phones in landscape) get a console rail on the
 * left like the video; narrow ones get a swipeable row of console tabs.
 */

/** Spine width on the shelf. */
private val SpineWidth = 30.dp
private const val CoverAspect = 0.70f

// ---- Backdrop ----

/**
 * Full-bleed platform-tinted backdrop, the screen's moment: the
 * platform's color as light falling from above the shelf, deepening
 * toward the floor, with a faint blurred wash of the cover art in it and
 * film grain over all of it, as on a cinema screen. The floor darkens so
 * text at the bottom stays readable. The color animates when the
 * platform changes. Nothing else on the screen carries color.
 */
@Composable
fun PlatformBackdrop(
    platform: String?,
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    val target = Color(PlatformTint.forPlatform(platform))
    val tint by animateColorAsState(target, tween(650), label = "platformTint")
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        // The light: the platform color, brightest where the shelf stands.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val light = Brush.radialGradient(
                        0f to lerp(tint, MarqueeInk, 0.16f),
                        0.45f to tint,
                        1f to lerp(tint, Color.Black, 0.5f),
                        center = Offset(w * 0.32f, h * 0.18f),
                        radius = maxOf(w, h) * 0.95f
                    )
                    onDrawBehind {
                        drawRect(lerp(tint, Color.Black, 0.5f))
                        drawRect(light)
                    }
                }
        )
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .size(128)
                    .memoryCacheKey("$coverUrl#ambient")
                    .placeholderMemoryCacheKey(coverUrl)
                    .crossfade(400)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(72.dp)
                    .alpha(0.14f)
            )
        }
        // The floor, then the grain over everything.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val floor = Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f)
                    )
                    val grain = grainBrush()
                    onDrawBehind {
                        drawRect(floor)
                        drawRect(grain, alpha = GRAIN_AMOUNT, blendMode = BlendMode.Overlay)
                    }
                }
        )
    }
}

// ---- Library ----

@Composable
fun ShowcaseLibrary(
    games: List<GameEntry>,
    allGames: List<GameEntry>,
    statuses: Map<String, PortStatus>,
    pairingCache: Map<String, List<GameFile>>,
    magnetPairings: Map<String, List<GameMagnetLink>>,
    gamesWithFiles: Set<String>,
    selectedGame: GameEntry?,
    wide: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    platformOptions: List<String>,
    selectedPlatform: String,
    onPlatformChange: (String) -> Unit,
    matchesOnly: Boolean,
    onMatchesOnlyChange: (Boolean) -> Unit,
    catalogStatusText: String,
    onSelectGame: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onPlay: (portId: String) -> Unit,
    onRefresh: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var searchOpen by remember { mutableStateOf(query.isNotEmpty()) }

    Box(Modifier.fillMaxSize()) {
        PlatformBackdrop(
            platform = selectedGame?.platform,
            coverUrl = selectedGame?.coverArtUrl.orEmpty()
        )
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ShowcaseTopBar(
                searchOpen = searchOpen || query.isNotEmpty(),
                onToggleSearch = {
                    if (searchOpen || query.isNotEmpty()) {
                        onQueryChange("")
                        searchOpen = false
                    } else {
                        searchOpen = true
                    }
                },
                onRefresh = onRefresh,
                onOpenCloud = onOpenCloud,
                onOpenSettings = onOpenSettings
            )
            AnimatedVisibility(
                visible = searchOpen || query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SearchField(query = query, onQueryChange = onQueryChange)
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    PlatformRail(
                        options = platformOptions,
                        selected = selectedPlatform,
                        onSelect = onPlatformChange,
                        modifier = Modifier.width(176.dp).fillMaxHeight()
                    )
                    ShowcaseBody(
                        games = games,
                        allGames = allGames,
                        statuses = statuses,
                        pairingCache = pairingCache,
                        magnetPairings = magnetPairings,
                        gamesWithFiles = gamesWithFiles,
                        selectedGame = selectedGame,
                        selectedPlatform = selectedPlatform,
                        matchesOnly = matchesOnly,
                        onMatchesOnlyChange = onMatchesOnlyChange,
                        catalogStatusText = catalogStatusText,
                        onSelectGame = onSelectGame,
                        onOpenGame = onOpenGame,
                        onPlay = onPlay,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                PlatformTabs(
                    options = platformOptions,
                    selected = selectedPlatform,
                    onSelect = onPlatformChange
                )
                ShowcaseBody(
                    games = games,
                    allGames = allGames,
                    statuses = statuses,
                    pairingCache = pairingCache,
                    magnetPairings = magnetPairings,
                    gamesWithFiles = gamesWithFiles,
                    selectedGame = selectedGame,
                    selectedPlatform = selectedPlatform,
                    matchesOnly = matchesOnly,
                    onMatchesOnlyChange = onMatchesOnlyChange,
                    catalogStatusText = catalogStatusText,
                    onSelectGame = onSelectGame,
                    onOpenGame = onOpenGame,
                    onPlay = onPlay,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ShowcaseTopBar(
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCloud: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Wordmark(Modifier.weight(1f))
        TopIcon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
            if (searchOpen) "Close search" else "Search games", onToggleSearch)
        TopIcon(Icons.Filled.Refresh, "Refresh library and catalog", onRefresh)
        TopIcon(Icons.Filled.Cloud, "Cloud", onOpenCloud)
        TopIcon(Icons.Filled.Settings, "Settings", onOpenSettings)
    }
}

/** The MARQUEE wordmark: Archivo Black, tight, with a small red light. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "MARQUEE",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            ),
            color = MarqueeInk
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(6.dp)
                .background(Color(0xFFE5202E), CircleShape)
        )
    }
}

@Composable
private fun TopIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MarqueeInk)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search games and ports") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MarqueeGlass,
            unfocusedContainerColor = MarqueeGlass,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MarqueeInk
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** The console rail (wide windows): text labels, a white bar on the selected one. */
@Composable
private fun PlatformRail(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item { RailItem("All games", selected.isEmpty()) { onSelect("") } }
        options.forEach { option ->
            item(key = option) { RailItem(option, selected == option) { onSelect(option) } }
        }
    }
}

@Composable
private fun RailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (selected) MarqueeInk else Color.Transparent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) MarqueeInk else MarqueeInk.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The console tabs (narrow windows): one swipeable row of words. */
@Composable
private fun PlatformTabs(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TabWord("All games", selected.isEmpty()) { onSelect("") }
        options.forEach { option -> TabWord(option, selected == option) { onSelect(option) } }
    }
}

@Composable
private fun TabWord(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) MarqueeInk else MarqueeInk.copy(alpha = 0.5f),
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(18.dp)
                .height(3.dp)
                .background(if (selected) MarqueeInk else Color.Transparent, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun ShowcaseBody(
    games: List<GameEntry>,
    allGames: List<GameEntry>,
    statuses: Map<String, PortStatus>,
    pairingCache: Map<String, List<GameFile>>,
    magnetPairings: Map<String, List<GameMagnetLink>>,
    gamesWithFiles: Set<String>,
    selectedGame: GameEntry?,
    selectedPlatform: String,
    matchesOnly: Boolean,
    onMatchesOnlyChange: (Boolean) -> Unit,
    catalogStatusText: String,
    onSelectGame: (String) -> Unit,
    onOpenGame: (String) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shelfState = rememberLazyListState()
    val density = LocalDensity.current
    val selectedIndex = games.indexOfFirst { it.key == selectedGame?.key }

    // Keep the selected cover in view with a few spines to its left,
    // like a hand pulling one box off the shelf. Spines are all the
    // same width, so "three items earlier" is a stable anchor.
    LaunchedEffect(selectedGame?.key, games.size) {
        if (selectedIndex >= 0) {
            val target = (selectedIndex - 3).coerceAtLeast(0)
            val visible = shelfState.layoutInfo.visibleItemsInfo.any { it.index == selectedIndex }
            if (visible) shelfState.animateScrollToItem(target) else shelfState.scrollToItem(target)
        }
    }

    fun step(delta: Int) {
        if (games.isEmpty()) return
        val next = (if (selectedIndex < 0) 0 else selectedIndex + delta)
            .coerceIn(0, games.lastIndex)
        onSelectGame(games[next].key)
    }

    val readyGames = remember(allGames, statuses, selectedPlatform) {
        allGames.filter { g ->
            (selectedPlatform.isEmpty() || g.platform == selectedPlatform) &&
                g.ports.any { statuses[it.id]?.isInstalled == true }
        }
    }
    val fileGames = remember(allGames, gamesWithFiles, selectedPlatform) {
        allGames.filter { g ->
            (selectedPlatform.isEmpty() || g.platform == selectedPlatform) && g.key in gamesWithFiles
        }
    }

    BoxWithConstraints(modifier) {
        // The shelf takes ~40% of the height, within sensible bounds,
        // so it fits a landscape phone and still fills a tablet.
        val shelfHeight = (maxHeight * 0.42f).coerceIn(150.dp, 300.dp)
        val titleStyle = if (maxWidth > 700.dp) {
            MaterialTheme.typography.displayMedium
        } else {
            MaterialTheme.typography.displaySmall
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Section line: what this shelf is, and the "Has files" toggle.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (selectedPlatform.isEmpty()) "All games" else selectedPlatform,
                    style = MaterialTheme.typography.titleLarge,
                    color = MarqueeInk
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${games.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarqueeInkMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                TogglePill(
                    label = "Has files",
                    selected = matchesOnly,
                    onClick = { onMatchesOnlyChange(!matchesOnly) }
                )
            }

            if (games.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().height(shelfHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (matchesOnly) "No games with files here yet." else catalogStatusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MarqueeInkMuted,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyRow(
                    state = shelfState,
                    modifier = Modifier.fillMaxWidth().height(shelfHeight),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    itemsIndexed(games, key = { _, g -> g.key }) { index, game ->
                        val isSelected = game.key == selectedGame?.key
                        ShelfItem(
                            game = game,
                            index = index,
                            selected = isSelected,
                            height = shelfHeight,
                            onClick = {
                                if (isSelected) onOpenGame(game.key) else onSelectGame(game.key)
                            }
                        )
                    }
                }
            }

            if (selectedGame != null) {
                val installedPort = selectedGame.ports.firstOrNull {
                    statuses[it.id]?.isInstalled == true
                }
                val matchedFiles = pairingCache[selectedGame.key].orEmpty()
                val matchedMagnets = magnetPairings[selectedGame.key].orEmpty()
                val anyFile = selectedGame.ports.any { statuses[it.id]?.hasFile == true } ||
                    matchedFiles.isNotEmpty()
                val status = posterStatusText(installedPort != null, anyFile)
                val source = posterSourceLabel(matchedFiles, matchedMagnets)
                val portsText = if (selectedGame.ports.size == 1) "1 port" else "${selectedGame.ports.size} ports"

                // Hero: swipe sideways here to step through the shelf.
                var drag by remember { mutableStateOf(0f) }
                val threshold = with(density) { 56.dp.toPx() }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(selectedIndex, games.size) {
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = {
                                    if (abs(drag) > threshold) step(if (drag < 0) 1 else -1)
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f }
                            ) { change, amount ->
                                change.consume()
                                drag += amount
                            }
                        }
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp)
                ) {
                    Text(
                        text = selectedGame.title,
                        style = titleStyle,
                        color = MarqueeInk,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOfNotNull(selectedGame.platform, portsText, status, source)
                            .joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MarqueeInkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (installedPort != null) {
                            PrimaryPill(
                                label = "Play",
                                icon = Icons.Filled.PlayArrow,
                                onClick = { onPlay(installedPort.id) }
                            )
                            GlassPill(label = "Details", onClick = { onOpenGame(selectedGame.key) })
                        } else {
                            PrimaryPill(
                                label = if (anyFile) "Set up" else "Open",
                                icon = null,
                                onClick = { onOpenGame(selectedGame.key) }
                            )
                        }
                    }
                }
            }

            // Collections, like the video's bottom row.
            if (readyGames.isNotEmpty() || fileGames.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    if (readyGames.isNotEmpty()) {
                        Collection("Ready to play", readyGames, onSelectGame)
                    }
                    if (fileGames.isNotEmpty()) {
                        Collection("Has files", fileGames, onSelectGame)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = catalogStatusText,
                style = MaterialTheme.typography.labelSmall,
                color = MarqueeInk.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * One slot on the shelf: a narrow spine, or — when selected — the full
 * cover in a white frame. The width animates between the two.
 */
@Composable
private fun ShelfItem(
    game: GameEntry,
    index: Int,
    selected: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    val coverWidth = height * CoverAspect
    val width by animateDpAsState(
        if (selected) coverWidth else SpineWidth,
        tween(280),
        label = "shelfWidth"
    )
    // Spines vary a little in height, like real boxes on a shelf.
    val spineHeight = height * (0.80f + ((game.key.hashCode() and 0x7) / 7f) * 0.12f)
    val itemHeight by animateDpAsState(if (selected) height else spineHeight, tween(280), label = "shelfHeight")
    val shape = RoundedCornerShape(if (selected) 6.dp else 3.dp)
    val base = Color(PlatformTint.forPlatform(game.platform))
    val spineColor = lerp(base, Color.Black, if (index % 3 == 0) 0.45f else if (index % 3 == 1) 0.2f else 0.05f)

    Box(
        modifier = Modifier
            .width(width)
            .height(itemHeight)
            .then(if (selected) Modifier.shadow(18.dp, shape) else Modifier)
            .clip(shape)
            .background(spineColor)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MarqueeInk), shape) else Modifier
            )
            .clickable(onClickLabel = if (selected) "Open ${game.title}" else "Select ${game.title}") { onClick() }
    ) {
        if (game.coverArtUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(game.coverArtUrl)
                    .size(if (selected) 512 else 160)
                    .placeholderMemoryCacheKey(game.coverArtUrl)
                    .crossfade(200)
                    .build(),
                contentDescription = if (selected) game.title else null,
                contentScale = ContentScale.Crop,
                alignment = if (selected) Alignment.Center else Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (selected) Modifier.padding(3.dp).clip(RoundedCornerShape(4.dp)) else Modifier.alpha(0.55f))
            )
        }
        if (!selected) {
            // Darken the spine so its title reads.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            Text(
                text = game.title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                color = MarqueeInk.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .verticalText()
                    .rotate(90f)
                    .padding(horizontal = 8.dp)
            )
        } else if (game.coverArtUrl.isBlank()) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                color = MarqueeInk,
                modifier = Modifier.align(Alignment.Center).padding(12.dp)
            )
        }
    }
}

/** Lays a child out with swapped width/height so a 90° rotation fits a narrow slot. */
private fun Modifier.verticalText(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth)
    )
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = (placeable.height - placeable.width) / 2,
            y = (placeable.width - placeable.height) / 2
        )
    }
}

@Composable
private fun Collection(title: String, games: List<GameEntry>, onSelect: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MarqueeInk)
            Spacer(Modifier.width(6.dp))
            Text("${games.size}", style = MaterialTheme.typography.labelSmall, color = MarqueeInkMuted)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            games.take(8).forEach { g ->
                Box(
                    Modifier
                        .width(38.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(PlatformTint.forPlatform(g.platform)))
                        .clickable(onClickLabel = "Select ${g.title}") { onSelect(g.key) }
                ) {
                    if (g.coverArtUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(g.coverArtUrl)
                                .size(128)
                                .placeholderMemoryCacheKey(g.coverArtUrl)
                                .build(),
                            contentDescription = g.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// ---- Pills ----

@Composable
fun PrimaryPill(label: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MarqueeInk,
        contentColor = Color(0xFF111114),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
    }
}

@Composable
fun GlassPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MarqueeGlass,
        contentColor = MarqueeInk,
        border = BorderStroke(1.dp, MarqueeInk.copy(alpha = 0.18f)),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
        }
    }
}

@Composable
private fun TogglePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MarqueeInk else MarqueeGlass,
        contentColor = if (selected) Color(0xFF111114) else MarqueeInk,
        border = if (selected) null else BorderStroke(1.dp, MarqueeInk.copy(alpha = 0.18f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
