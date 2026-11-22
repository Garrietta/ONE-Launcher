package dev.one.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.one.launcher.data.GameEntry
import dev.one.launcher.data.PlatformTint
import dev.one.launcher.ui.theme.MarqueeInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * The shelf (v1.9.2). Games stand as spines and the one in the slot is
 * pulled out as its full cover in a white frame. The shelf moves one game
 * at a time, like a wheel with detents: a drag or a fling ticks through
 * the games, every game that reaches the slot is selected at once and
 * opens with a short hard-landing step and a small pop, and the one it
 * replaced folds back into a spine. Nothing slides past unselected, and
 * each step gives a light haptic tick.
 *
 * Everything on the shelf is placed from one number, the shown position:
 * 3.0 means game 3 is open in the slot, 3.4 means game 3 is folding shut
 * while game 4 opens. The geometry is pure (shelfOpenness, shelfWidth,
 * shelfLefts) and tested in ShelfGeometryTest.
 */

/** Spine width on the shelf. */
internal val SpineWidth = 30.dp
internal const val CoverAspect = 0.70f
private val SpineGap = 3.dp
private val ShelfPadStart = 20.dp
private val FrameWidth = 3.dp

/** Spines kept to the left of the open cover once the shelf has moved past the start. */
internal const val ShelfLead = 3

/** Finger travel that moves the shelf one game. */
private val StepDrag = 40.dp

/** One step: most of the travel early, then a hard stop. */
private const val StepMillis = 160
private val StepEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A tapped spine a few games away takes a little longer, never more than this. */
private const val LongestStepMillis = 420

/** The arriving cover jumps up a little larger and settles. */
private const val PopMillis = 280
private const val PopScale = 0.045f
private val PopLift = 6.dp

/** A selection made elsewhere further than this jumps instead of running the shelf through every game between. */
private const val FarJump = 8

/** A fling ends once it is slower than this many games a second. */
private const val FlingStopSpeed = 0.5f

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

/** How far game [index] is pulled out when the shelf shows [shown]: 1 in the slot, 0 a step or more away. */
internal fun shelfOpenness(index: Int, shown: Float): Float = (1f - abs(index - shown)).coerceIn(0f, 1f)

/** Width of game [index]'s slot: a spine growing into the cover as it is pulled out. */
internal fun shelfWidth(index: Int, shown: Float, m: ShelfMetrics): Float =
    m.spine + (m.cover - m.spine) * shelfOpenness(index, shown)

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
internal fun shelfLefts(first: Int, last: Int, shown: Float, m: ShelfMetrics): FloatArray {
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
        x += shelfWidth(k, s, m) + m.gap
        k++
        if (k >= first) out[k - first] = x
    }
    k = anchor
    x = anchorLeft
    while (k > first) {
        k--
        x -= shelfWidth(k, s, m) + m.gap
        if (k <= last) out[k - first] = x
    }
    return out
}

/** Spines vary a little in height, like real boxes on a shelf. */
internal fun spineHeightRatio(game: GameEntry): Float =
    0.80f + ((game.key.hashCode() and 0x7) / 7f) * 0.12f

/** How long a step from [distance] games away takes. */
internal fun stepMillis(distance: Float): Int =
    (StepMillis + 60f * (distance - 1f)).coerceIn(StepMillis.toFloat(), LongestStepMillis.toFloat()).roundToInt()

// ---- State ----

/** Where the shelf is going; [jump] lands there without running through the games between. */
internal data class ShelfMove(val index: Int, val jump: Boolean, val serial: Int)

@Stable
internal class ShelfState {
    /** The position drawn: whole numbers are a game open in the slot. */
    val shown = Animatable(0f)

    /** 1 the moment a game arrives in the slot, settling to 0. */
    val pop = Animatable(0f)

    var move by mutableStateOf(ShelfMove(0, jump = true, serial = 0))
        private set

    /** The game the shelf is on, or heading to. */
    val target: Int get() = move.index

    /** True while a finger or a fling is moving the shelf, so selections arriving from outside wait. */
    var steering by mutableStateOf(false)

    fun moveTo(index: Int, jump: Boolean = false) {
        if (!jump && index == move.index) return
        move = ShelfMove(index, jump, move.serial + 1)
    }
}

/** The drag in progress: where it started, how many games it has moved, and its fling. */
private class ShelfDrag {
    var from = 0
    var steps = 0f
    var serial = 0
    var fling: Job? = null
    var list: List<GameEntry>? = null
}

// ---- The shelf ----

/**
 * The shelf of [games]. [onStep] is called with the game a drag, fling or
 * tap moves the shelf to (the caller selects it and calls
 * [ShelfState.moveTo]); [onOpen] when the open cover is tapped. A change of
 * [selectedIndex] from outside moves the shelf to it.
 */
@Composable
internal fun GameShelf(
    games: List<GameEntry>,
    selectedIndex: Int,
    state: ShelfState,
    onStep: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val drag = remember { ShelfDrag() }
    val currentGames by rememberUpdatedState(games)
    val currentOnStep by rememberUpdatedState(onStep)
    val stepPx = with(density) { StepDrag.toPx() }

    // Follow a selection made elsewhere: a collection, a search, a new filter.
    LaunchedEffect(games, selectedIndex, state.steering) {
        if (state.steering || games.isEmpty()) return@LaunchedEffect
        val index = selectedIndex.coerceIn(0, games.lastIndex)
        val newList = drag.list !== games
        drag.list = games
        if (newList || abs(index - state.target) > FarJump) state.moveTo(index, jump = true)
        else state.moveTo(index)
    }

    // Run the shelf to each new target in one hard-landing step, and pop
    // the cover that arrives. collectLatest drops a step still running
    // when the next one comes, so a fast fling flows and a slow one clacks.
    LaunchedEffect(state) {
        snapshotFlow { state.move }.collectLatest { move ->
            coroutineScope {
                launch {
                    state.pop.snapTo(1f)
                    state.pop.animateTo(0f, tween(PopMillis, easing = FastOutSlowInEasing))
                }
                val to = move.index.toFloat()
                if (move.jump) {
                    state.shown.snapTo(to)
                } else {
                    val distance = abs(to - state.shown.value)
                    if (distance > 0.001f) state.shown.animateTo(to, tween(stepMillis(distance), easing = StepEasing))
                }
            }
        }
    }

    val draggable = Modifier.draggable(
        state = rememberDraggableState { delta ->
            drag.steps -= delta / stepPx
            currentOnStep(drag.from + drag.steps.roundToInt())
        },
        orientation = Orientation.Horizontal,
        onDragStarted = {
            drag.fling?.cancel()
            drag.serial++
            state.steering = true
            drag.from = state.target
            drag.steps = 0f
        },
        onDragStopped = { velocity ->
            val serial = drag.serial
            drag.fling = scope.launch {
                try {
                    val lastIndex = currentGames.lastIndex
                    AnimationState(initialValue = drag.steps, initialVelocity = -velocity / stepPx).animateDecay(
                        exponentialDecay(absVelocityThreshold = FlingStopSpeed)
                    ) {
                        val index = drag.from + value.roundToInt()
                        currentOnStep(index)
                        if ((index <= 0 && velocity > 0f) || (index >= lastIndex && velocity < 0f)) cancelAnimation()
                    }
                } finally {
                    if (drag.serial == serial) state.steering = false
                }
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
        val coverWidth: Dp = min(maxHeight.value * CoverAspect, maxWidth.value * 0.5f).dp
        val coverHeight: Dp = coverWidth / CoverAspect
        val metrics = with(density) {
            ShelfMetrics(SpineWidth.toPx(), coverWidth.toPx(), SpineGap.toPx(), ShelfPadStart.toPx())
        }
        val coverHeightPx = with(density) { coverHeight.toPx() }
        val widthPx = constraints.maxWidth.toFloat()

        // Compose only the games near the slot; the range follows the
        // rounded position, so it changes once per step, not per frame.
        val near by remember { derivedStateOf { state.shown.value.roundToInt() } }
        val toRight = ceil((widthPx - metrics.padStart - metrics.lead * metrics.pitch - metrics.cover) / metrics.pitch).toInt()
        val first = (near - metrics.lead - 2).coerceAtLeast(0)
        val last = (near + toRight.coerceAtLeast(0) + 2).coerceAtMost(games.lastIndex)

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                for (k in first..last) {
                    val game = games[k]
                    key(game.key) {
                        ShelfSlot(
                            game = game,
                            index = k,
                            selected = k == state.target,
                            open = { shelfOpenness(k, state.shown.value) },
                            pop = { state.pop.value },
                            coverWidth = coverWidth,
                            coverHeight = coverHeight,
                            onClick = { if (k == state.target) onOpen(k) else onStep(k) }
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val shown = state.shown.value
            val lefts = shelfLefts(first, last, shown, metrics)
            val placeables = measurables.mapIndexed { j, measurable ->
                val k = first + j
                val open = shelfOpenness(k, shown)
                val spineHeight = coverHeightPx * spineHeightRatio(games[k])
                val height = spineHeight + (coverHeightPx - spineHeight) * open
                measurable.measure(
                    Constraints.fixed(shelfWidth(k, shown, metrics).roundToInt(), height.roundToInt())
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
