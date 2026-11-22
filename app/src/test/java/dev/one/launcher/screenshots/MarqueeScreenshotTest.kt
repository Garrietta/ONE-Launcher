package dev.one.launcher.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.one.launcher.data.GameEntry
import dev.one.launcher.model.Port
import dev.one.launcher.ui.ShowcaseLibrary
import dev.one.launcher.ui.GlassPill
import dev.one.launcher.ui.PlatformBackdrop
import dev.one.launcher.ui.PrimaryPill
import dev.one.launcher.ui.Wordmark
import dev.one.launcher.ui.theme.MarqueeInkMuted
import dev.one.launcher.ui.theme.MarqueeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws Marquee's backdrop and its type on the JVM, so a change to the
 * look can be judged without a phone:
 *
 *     ./gradlew :app:recordRoborazziDebug --tests '*MarqueeScreenshotTest'
 *
 * writes PNGs to app/build/outputs/roborazzi. No network, so there is no
 * cover art; the shelf, motion and real insets still need the device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class MarqueeScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun hero(name: String, platform: String, title: String, meta: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MarqueeTheme {
                Box(Modifier.fillMaxSize()) {
                    PlatformBackdrop(platform = platform, coverUrl = "")
                    Wordmark(Modifier.padding(start = 20.dp, top = 40.dp))
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(8.dp))
                        Text(meta, style = MaterialTheme.typography.bodyMedium, color = MarqueeInkMuted)
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryPill("Play", Icons.Filled.PlayArrow, onClick = {})
                            GlassPill("Details", onClick = {})
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun hero_n64() = hero("hero_n64", "Nintendo 64", "Ocarina of Time", "Nintendo 64 · 2 ports · Installed · Your file")

    @Test fun hero_switch() = hero("hero_switch", "Switch", "Super Mario Odyssey", "Switch · 1 port · Not installed")

    @Test fun hero_ps2() = hero("hero_ps2", "PlayStation 2", "Shadow of the Colossus", "PlayStation 2 · 1 port · Installed")

    // ---- The Library page: fits the window, and the shelf steps ----

    private val titles = listOf(
        "'Splosion Man" to "Xbox 360", ".hack//versus" to "PlayStation 3",
        "007: Nightfire and 007: Agent Under Fire" to "GameCube", "0 A.D." to "PC",
        "2014 FIFA World Cup Brazil" to "Xbox 360", "3D Pinball Space Cadet" to "PC",
        "A Dance of Fire and Ice" to "PC", "A2 Racer II" to "PC", "Abuse" to "DOS",
        "Ocarina of Time" to "Nintendo 64", "Super Mario 64" to "Nintendo 64",
        "Shadow of the Colossus" to "PlayStation 2", "Super Metroid" to "Super Nintendo",
        "Sonic Adventure 2" to "Dreamcast", "Perfect Dark" to "Nintendo 64",
        "Banjo-Kazooie" to "Nintendo 64", "Jet Set Radio" to "Dreamcast"
    )

    private val games = titles.mapIndexed { i, (title, platform) ->
        GameEntry(
            title = title,
            platform = platform,
            ports = listOf(
                Port(
                    id = "p$i", title = title, game = title, system = platform, type = "native",
                    repo = "", releasesUrl = "", requiredDump = ""
                )
            )
        )
    }

    private fun library(name: String, start: Int, wide: Boolean = false, stepTo: Int? = null, frames: List<Long> = emptyList()) {
        compose.mainClock.autoAdvance = false
        var selected by mutableStateOf(games[start].key)
        compose.setContent {
            MarqueeTheme {
                ShowcaseLibrary(
                    games = games,
                    allGames = games,
                    statuses = emptyMap(),
                    pairingCache = emptyMap(),
                    magnetPairings = emptyMap(),
                    gamesWithFiles = games.take(6).map { it.key }.toSet(),
                    selectedGame = games.first { it.key == selected },
                    wide = wide,
                    query = "",
                    onQueryChange = {},
                    platformOptions = games.map { it.platform }.distinct().sorted(),
                    selectedPlatform = "",
                    onPlatformChange = {},
                    matchesOnly = false,
                    onMatchesOnlyChange = {},
                    catalogStatusText = "${games.size} games · ${games.size} ports · Recompa catalog, updated 2026-11-22",
                    onSelectGame = { selected = it },
                    onOpenGame = {},
                    onPlay = {},
                    onRefresh = {},
                    onOpenCloud = {},
                    onOpenSettings = {}
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
        if (stepTo != null) {
            selected = games[stepTo].key
            var at = 0L
            for (t in frames) {
                compose.mainClock.advanceTimeBy(t - at)
                at = t
                compose.onRoot().captureRoboImage("build/outputs/roborazzi/${name}_${t}ms.png")
            }
        }
    }

    /** The phone in the recording: 1080 x 1708 px, less the status and navigation bars. */
    @Config(qualifiers = "w411dp-h600dp-xxhdpi")
    @Test fun library_phone() = library("library_phone", start = 0)

    @Config(qualifiers = "w411dp-h600dp-xxhdpi")
    @Test fun library_phone_step() =
        library("library_phone_step", start = 5, stepTo = 6, frames = listOf(16L, 48L, 96L, 160L, 400L))

    /** Landscape phone: the page drops what does not fit. */
    @Config(qualifiers = "w780dp-h330dp-xxhdpi")
    @Test fun library_landscape() = library("library_landscape", start = 9, wide = true)

    /** Unfolded: everything fits, with the rail. */
    @Config(qualifiers = "w840dp-h660dp-xhdpi")
    @Test fun library_unfolded() = library("library_unfolded", start = 9, wide = true)
}
