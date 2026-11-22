package dev.one.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.one.launcher.data.BoxSize
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.PlatformBox
import dev.one.launcher.data.PlatformTint
import dev.one.launcher.ui.theme.MarqueeInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The shelf (v1.9.3), a rolodex. Games stand as spines and the one in the
 * slot, at the far left, is pulled out as its full cover in a white frame.
 * The shelf rolls under the thumb, and the games click past the slot one
 * at a time: between two games the motion bunches up around the halfway
 * point, so each game snaps into the slot rather than sliding, and a held
 * thumb rests close to a game. Whatever lands in the slot is selected at
 * once, opens with a small pop and gives a light haptic tick; the game it
 * replaced folds back into a spine and flips away to the left. A fling
 * rolls on and slows down to stop exactly on a game.
 *
 * Every box stands at its real size relative to the others (PlatformBox):
 * a 3DS case is shorter than a PlayStation case, and a Nintendo 64 box
 * opens wider than it is tall. A spine is as tall as its box's front.
 *
 * Everything on the shelf is placed from one number, the shown position:
 * 3.0 means game 3 is open in the slot, 3.4 means game 3 is folding shut
 * while game 4 opens. The thumb and flings move a raw position, and
 * shelfShown turns it into the shown one with a detent at every game. The
 * geometry is pure (shelfShown, shelfOpenness, shelfWidth, shelfLefts,
 * shelfVisibility) and tested in ShelfGeometryTest.
 */

/** Spine width on the shelf. */
internal val SpineWidth = 30.dp
private val SpineGap = 3.dp
private val ShelfPadStart = 20.dp
private val FrameWidth = 3.dp

/** Spines kept to the left of the open cover: none, the open game is the far left one. */
internal const val ShelfLead = 0

/** How sharply the motion between two games bunches up at the halfway point; 1 would be even. */
private const val DetentSharpness = 3f

/** A tap or a swipe across the title rolls the shelf this long per game, never longer than the cap. */
private const val StepMillis = 160
private const val LongestStepMillis = 520

/** The arriving cover jumps up a little larger and settles. */
private const val PopMillis = 280
private const val PopScale = 0.045f
private val PopLift = 6.dp

/** A fling hands over to the last settle below this many games a second. */
private const val FlingStopSpeed = 0.1f

// ---- Geometry ----

/** The shelf's sizes in pixels. */
internal data class ShelfMetrics(
    val spine: Float,
    val cover: Float,
    val gap: Float,
    val padStart: Float,
    val lead: Int = ShelfLead
) {
    val pitch: Float get() = spine + gap
}

/**
 * The shown position for a [raw] one: whole games stay whole, and between
 * two the motion is gathered around the halfway point, so the shelf seems
 * to click from game to game while still following the thumb.
 */
internal fun shelfShown(raw: Float): Float {
    val whole = floor(raw)
    val f = raw - whole
    if (f <= 0f) return raw
    val a = f.pow(DetentSharpness)
    val b = (1f - f).pow(DetentSharpness)
    return whole + a / (a + b)
}

/** How far game [index] is pulled out when the shelf shows [shown]: 1 in the slot, 0 a step or more away. */
internal fun shelfOpenness(index: Int, shown: Float): Float = (1f - abs(index - shown)).coerceIn(0f, 1f)

/** How visible game [index] is: games that have left the slot flip away to the left and fade. */
internal fun shelfVisibility(index: Int, shown: Float, lead: Int = ShelfLead): Float =
    (1f - (shown - lead - index)).coerceIn(0f, 1f)

/** Width of game [index]'s slot: a spine growing into the cover as it is pulled out. */
internal fun shelfWidth(index: Int, shown: Float, m: ShelfMetrics, cover: Float = m.cover): Float =
    m.spine + (cover - m.spine) * shelfOpenness(index, shown)

/**
 * Left edges, in pixels from the shelf's left edge, of games [first]..[last]
 * when the shelf shows [shown].
 *
 * The open game's left edge sits at the slot, [ShelfMetrics.lead] spines in
 * from the start; near the start of the list the slot moves left with the
 * position, so the first game is never pushed away from the edge. Between
 * two games the outgoing one moves a fraction of a spine left as it folds
 * and the incoming one grows into the slot behind it, so every edge moves
 * continuously.
 */
internal fun shelfLefts(
    first: Int,
    last: Int,
    shown: Float,
    m: ShelfMetrics,
    coverOf: (Int) -> Float = { m.cover }
): FloatArray {
    val out = FloatArray((last - first + 1).coerceAtLeast(0))
    if (out.isEmpty()) return out
    val s = shown.coerceAtLeast(0f)
    val anchor = floor(s).toInt()
    val fraction = s - anchor
    val anchorLeft = m.padStart + min(s, m.lead.toFloat()) * m.pitch - fraction * m.pitch

    var k = anchor
    var x = anchorLeft
    if (k in first..last) out[k - first] = x
    while (k < last) {
        x += shelfWidth(k, s, m, coverOf(k)) + m.gap
        k++
        if (k >= first) out[k - first] = x
    }
    k = anchor
    x = anchorLeft
    while (k > first) {
        k--
        x -= shelfWidth(k, s, m, coverOf(k)) + m.gap
        if (k <= last) out[k - first] = x
    }
    return out
}

/**
 * Pixels per millimetre of real box: as large as the shelf allows, with
 * the tallest box in the list standing the full [heightPx] and the widest
 * cover no wider than [coverCapPx]. One scale for every box, so they keep
 * their real sizes relative to each other.
 */
internal fun shelfScale(heightPx: Float, coverCapPx: Float, tallestMm: Float, widestMm: Float): Float =
    min(heightPx / tallestMm, coverCapPx / widestMm)

/** How long rolling [distance] games for a tap or a swipe across the title takes. */
internal fun stepMillis(distance: Float): Int =
    (StepMillis + 60f * (distance - 1f)).coerceIn(StepMillis.toFloat(), LongestStepMillis.toFloat()).roundToInt()

/**
 * Where a fling from [raw] at [velocity] games a second stops: the game the
 * fling would naturally coast to, within the shelf. The friction is then
 * chosen so the fling coasts to exactly there ([flingFriction]).
 */
internal fun flingEnd(raw: Float, velocity: Float, last: Int): Int =
    (raw + velocity / DECAY_RATE).roundToInt().coerceIn(0, last)

/** The friction multiplier that makes an exponential decay from [raw] at [velocity] coast to [end]. */
internal fun flingFriction(raw: Float, velocity: Float, end: Int): Float {
    val distance = end - raw
    if (distance == 0f || distance * velocity <= 0f) return 1f
    return (velocity / (DECAY_RATE * distance)).coerceAtLeast(0.05f)
}

/** Compose's exponential decay loses velocity at this rate times its friction multiplier. */
private const val DECAY_RATE = 4.2f

// ---- State ----

@Stable
internal class ShelfState {
    /** Where the thumb or a fling has rolled the shelf, in games; see [shelfShown]. */
    var raw by mutableFloatStateOf(0f)

    /** The game in the slot: the raw position rounded. */
    var selected by mutableIntStateOf(0)

    /** 1 the moment a game lands in the slot, settling to 0. */
    val pop = Animatable(0f)

    /** True while the thumb, a fling or a roll is moving the shelf, so a selection made elsewhere waits. */
    var moving by mutableStateOf(false)

    /** A roll asked for from outside the shelf, as a swipe across the title does. */
    var rollRequest by mutableStateOf(ShelfRoll(0, 0))
        private set

    fun rollTo(index: Int) {
        rollRequest = ShelfRoll(index, rollRequest.serial + 1)
    }
}

internal data class ShelfRoll(val index: Int, val serial: Int)

/** The real box of every game in the list, with the tallest and widest, which set the shelf's scale. */
private class ShelfBoxes(val sizes: List<BoxSize>) {
    val tallest: Float = sizes.maxOfOrNull { it.height } ?: PlatformBox.DVD.height
    val widest: Float = sizes.maxOfOrNull { it.width } ?: PlatformBox.DVD.width
}

/** The motion in progress, so a new one can stop it, and the list last shown. */
private class ShelfMotion {
    var job: Job? = null
    var serial = 0
    var list: List<GameEntry>? = null
    var pop: Job? = null
}

// ---- The shelf ----

/**
 * The shelf of [games]. [onSelect] is called with each game that lands in
 * the slot as the shelf rolls; [onOpen] when the open cover is tapped. A
 * change of [selectedIndex] from outside puts that game in the slot.
 */
@Composable
internal fun GameShelf(
    games: List<GameEntry>,
    selectedIndex: Int,
    state: ShelfState,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = remember { ShelfMotion() }
    val currentGames by rememberUpdatedState(games)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // One game per spine of thumb travel, so the spines keep up with the thumb.
    val stepPx = with(density) { (SpineWidth + SpineGap).toPx() }

    fun last() = currentGames.lastIndex.coerceAtLeast(0)

    // Every position change goes through here: whatever lands in the slot
    // is selected, ticks and pops.
    fun rollRaw(value: Float, announce: Boolean = true) {
        val raw = value.coerceIn(0f, last().toFloat())
        state.raw = raw
        val landed = raw.roundToInt()
        if (landed != state.selected) {
            state.selected = landed
            motion.pop?.cancel()
            motion.pop = scope.launch {
                state.pop.snapTo(1f)
                state.pop.animateTo(0f, tween(PopMillis, easing = FastOutSlowInEasing))
            }
            if (announce) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelect(landed)
            }
        }
    }

    // Starts a motion, stopping the one before it; moving stays true until
    // the last one started has finished.
    fun move(block: suspend () -> Unit) {
        motion.job?.cancel()
        val serial = ++motion.serial
        state.moving = true
        motion.job = scope.launch {
            try {
                block()
            } finally {
                if (motion.serial == serial) state.moving = false
            }
        }
    }

    suspend fun settle(to: Int, millis: Int) {
        val from = state.raw
        if (from == to.toFloat()) return
        animate(from, to.toFloat(), animationSpec = tween(millis, easing = LinearEasing)) { value, _ -> rollRaw(value) }
    }

    fun rollTo(index: Int) {
        val to = index.coerceIn(0, last())
        move { settle(to, stepMillis(abs(to - state.raw))) }
    }

    val boxes = remember(games) { ShelfBoxes(games.map { PlatformBox.forPlatform(it.platform) }) }

    // Follow a selection made elsewhere (a collection, a search, a new
    // filter) by putting it in the slot at once.
    LaunchedEffect(games, selectedIndex, state.moving) {
        if (state.moving || games.isEmpty()) return@LaunchedEffect
        val index = selectedIndex.coerceIn(0, games.lastIndex)
        val newList = motion.list !== games
        motion.list = games
        if (newList || index != state.selected || state.raw != index.toFloat()) {
            rollRaw(index.toFloat(), announce = false)
            state.raw = index.toFloat()
        }
    }

    // Rolls asked for from outside, as a swipe across the title does.
    LaunchedEffect(state) {
        snapshotFlow { state.rollRequest }.drop(1).collect { rollTo(it.index) }
    }

    val draggable = Modifier.draggable(
        state = rememberDraggableState { delta -> rollRaw(state.raw - delta / stepPx) },
        orientation = Orientation.Horizontal,
        onDragStarted = {
            motion.job?.cancel()
            motion.serial++
            state.moving = true
        },
        onDragStopped = { velocity ->
            val speed = -velocity / stepPx
            move {
                val end = flingEnd(state.raw, speed, last())
                if (abs(speed) > FlingStopSpeed && end != state.raw.roundToInt()) {
                    AnimationState(initialValue = state.raw, initialVelocity = speed).animateDecay(
                        exponentialDecay(flingFriction(state.raw, speed, end), FlingStopSpeed)
                    ) {
                        rollRaw(value)
                        if ((speed > 0f && value >= end) || (speed < 0f && value <= end)) cancelAnimation()
                    }
                }
                settle(end, StepMillis / 2)
            }
        }
    )

    BoxWithConstraints(
        modifier
            .then(draggable)
            // Clip the sides only: the pop may lift the cover above the shelf.
            .drawWithContent {
                clipRect(top = -size.height, bottom = size.height * 2f) { this@drawWithContent.drawContent() }
            }
    ) {
        // Every box at its real size relative to the others: a 3DS case
        // shorter than a PS2 case, an N64 box wider than it is tall.
        val widthPx = constraints.maxWidth.toFloat()
        val scale = shelfScale(
            heightPx = constraints.maxHeight.toFloat(),
            coverCapPx = widthPx * 0.5f,
            tallestMm = boxes.tallest,
            widestMm = boxes.widest
        )
        val coverOf = { k: Int -> boxes.sizes[k].width * scale }
        val heightOf = { k: Int -> boxes.sizes[k].height * scale }
        val metrics = with(density) {
            ShelfMetrics(SpineWidth.toPx(), coverOf(state.selected.coerceIn(0, games.lastIndex)), SpineGap.toPx(), ShelfPadStart.toPx())
        }

        // Compose only the games near the slot; the range follows the
        // rounded position, so it changes once per step, not per frame.
        val near by remember { derivedStateOf { state.raw.roundToInt() } }
        val toRight = ceil((widthPx - metrics.padStart - metrics.lead * metrics.pitch - metrics.cover) / metrics.pitch).toInt()
        val first = (near - metrics.lead - 2).coerceAtLeast(0)
        val last = (near + toRight.coerceAtLeast(0) + 3).coerceAtMost(games.lastIndex)

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                for (k in first..last) {
                    val game = games[k]
                    key(game.key) {
                        ShelfSlot(
                            game = game,
                            index = k,
                            selected = k == state.selected,
                            open = { shelfOpenness(k, shelfShown(state.raw)) },
                            visible = { shelfVisibility(k, shelfShown(state.raw)) },
                            pop = { state.pop.value },
                            coverWidth = with(density) { coverOf(k).toDp() },
                            coverHeight = with(density) { heightOf(k).toDp() },
                            onClick = { if (k == state.selected) onOpen(k) else rollTo(k) }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val shown = shelfShown(state.raw)
            val lefts = shelfLefts(first, last, shown, metrics, coverOf)
            // A box's spine is as tall as its front: pulling it out only widens it.
            val placeables = measurables.mapIndexed { j, measurable ->
                val k = first + j
                measurable.measure(
                    Constraints.fixed(
                        shelfWidth(k, shown, metrics, coverOf(k)).roundToInt(),
                        heightOf(k).roundToInt().coerceAtLeast(1)
                    )
                )
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { j, placeable ->
                    placeable.place(
                        x = lefts[j].roundToInt(),
                        y = constraints.maxHeight - placeable.height,
                        zIndex = shelfOpenness(first + j, shown)
                    )
                }
            }
        }
    }
}

private val SpineShade = Brush.horizontalGradient(
    listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))
)

/**
 * One game on the shelf, drawn for any openness between spine (0) and
 * cover (1). The cover art is always laid out at full cover size and the
 * slot reveals it, so pulling a game out uncovers its box rather than
 * stretching it. Openness and pop are read in the layer and draw phases,
 * so a step redraws without recomposing.
 */
@Composable
private fun ShelfSlot(
    game: GameEntry,
    index: Int,
    selected: Boolean,
    open: () -> Float,
    visible: () -> Float,
    pop: () -> Float,
    coverWidth: Dp,
    coverHeight: Dp,
    onClick: () -> Unit
) {
    val base = Color(PlatformTint.forPlatform(game.platform))
    val spineColor = lerp(base, Color.Black, if (index % 3 == 0) 0.45f else if (index % 3 == 1) 0.2f else 0.05f)
    val url = game.coverArtUrl

    Box(
        Modifier
            .graphicsLayer {
                val o = open()
                val p = if (selected) pop() else 0f
                val scale = 1f + PopScale * p
                scaleX = scale
                scaleY = scale
                translationY = -PopLift.toPx() * p
                transformOrigin = TransformOrigin(0.5f, 1f)
                shape = RoundedCornerShape((3f + 3f * o).dp)
                clip = true
                shadowElevation = 18.dp.toPx() * o
                alpha = visible()
            }
            .background(spineColor)
            .clickable(onClickLabel = if (selected) "Open ${game.title}" else "Select ${game.title}") { onClick() }
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .size(if (selected) 512 else 160)
                    .memoryCacheKey(if (selected) coverKey(url) else spineKey(url))
                    .placeholderMemoryCacheKey(spineKey(url))
                    .crossfade(200)
                    .build(),
                contentDescription = if (selected) game.title else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .coverSized(coverWidth, coverHeight, open)
                    .graphicsLayer {
                        alpha = 0.55f + 0.45f * open()
                        shape = RoundedCornerShape(4.dp)
                        clip = true
                    }
            )
        } else {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                color = MarqueeInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .coverSized(coverWidth, coverHeight, open)
                    .graphicsLayer { alpha = open() }
                    .padding(12.dp)
                    .wrapCenter()
            )
        }
        // The spine's shade and its title fade as the box comes out.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - open() }
                .background(SpineShade)
        )
        Text(
            text = game.title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
            color = MarqueeInk.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = 1f - open() }
                .verticalText()
                .rotate(90f)
                .padding(horizontal = 8.dp)
        )
        // The white frame. The stroke straddles the clipped edge, so
        // twice the width leaves the frame's width inside.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val o = open()
                    if (o > 0f) {
                        drawRoundRect(
                            color = MarqueeInk.copy(alpha = o),
                            cornerRadius = CornerRadius((3f + 3f * o).dp.toPx()),
                            style = Stroke(FrameWidth.toPx() * 2f)
                        )
                    }
                }
        )
    }
}

internal fun spineKey(url: String) = "$url#spine"
internal fun coverKey(url: String) = "$url#cover"

/**
 * Lays the child out at the cover's size inside the frame, whatever the
 * slot's size: from the left edge, centred vertically, the frame's inset
 * growing in as the box comes out.
 */
private fun Modifier.coverSized(coverWidth: Dp, coverHeight: Dp, open: () -> Float): Modifier =
    layout { measurable, constraints ->
        val frame = FrameWidth.roundToPx()
        val width = (coverWidth.roundToPx() - 2 * frame).coerceAtLeast(1)
        val height = (coverHeight.roundToPx() - 2 * frame).coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place((frame * open()).roundToInt(), (constraints.maxHeight - height) / 2)
        }
    }

/** Centres a child inside the space it is given. */
private fun Modifier.wrapCenter(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((constraints.maxWidth - placeable.width) / 2, (constraints.maxHeight - placeable.height) / 2)
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
