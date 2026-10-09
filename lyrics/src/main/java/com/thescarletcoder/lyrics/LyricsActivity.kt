package com.thescarletcoder.lyrics

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.Tunables
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LyricsActivity : ComponentActivity() {
    private lateinit var nowPlaying: NowPlaying
    private val access = mutableStateOf(false)
    /** Set when Lyrics is opened fresh; the first start after that checks whether Spotify should join it. */
    private var checkSpotify = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LyricsTuning.init(this)
        val prefs = LyricsPrefs(this)
        nowPlaying = NowPlaying(this)
        val repo = LyricsRepo(LyricsStore(this))
        if (savedInstanceState == null) checkSpotify = !maybeOpenSpotify(intent)
        setContent {
            var choice by remember { mutableStateOf(prefs.theme) }
            val uiMode = LocalConfiguration.current.uiMode // the system flipping light/dark recomposes this
            val p = remember(choice, uiMode) { ScarletTokens.resolve(choice) }
            LaunchedEffect(p.dark) {
                val bars = if (p.dark) SystemBarStyle.dark(TRANSPARENT) else SystemBarStyle.light(TRANSPARENT, TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val scheme = if (p.dark) darkColorScheme(primary = Color(p.text), background = Color(p.bg), surface = Color(p.surface), onSurface = Color(p.text))
                else lightColorScheme(primary = Color(p.text), background = Color(p.bg), surface = Color(p.surface), onSurface = Color(p.text))
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalPalette provides p) {
                    LyricsScreen(
                        nowPlaying, repo, access.value, theme = choice,
                        onTheme = { c -> choice = c; prefs.theme = c; ScarletTokens.applyToSystem(this, c) },
                        onGrant = ::openAccessSettings, onSpotify = ::openSpotifyAlongside,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!maybeOpenSpotify(intent) && intent.action == Intent.ACTION_MAIN) checkSpotify = true   // opened again from the launcher
    }

    override fun onStart() {
        super.onStart()
        access.value = nowPlaying.hasAccess()
        nowPlaying.start()
        if (checkSpotify) {
            checkSpotify = false
            // Sam, 2026-10-09: opening Lyrics while Spotify is already playing brings Spotify up beside it, on the
            // phone and the tablet, unless the screen is already split. Checked once the window knows its mode.
            window.decorView.post { if (!isInMultiWindowMode && nowPlaying.spotifyPlaying()) openSpotifyAlongside() }
        }
    }

    override fun onStop() {
        nowPlaying.stop()
        super.onStop()
    }

    /** Scarlet Command's Listen button sends this extra: open Spotify in the other half of the screen. True if it did. */
    private fun maybeOpenSpotify(i: Intent?): Boolean {
        if (i?.getBooleanExtra(EXTRA_OPEN_SPOTIFY, false) != true) return false
        i.removeExtra(EXTRA_OPEN_SPOTIFY)
        openSpotifyAlongside()
        return true
    }

    private fun openSpotifyAlongside() {
        val launch = packageManager.getLaunchIntentForPackage(NowPlaying.SPOTIFY) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { startActivity(launch) }
    }

    private fun openAccessSettings() {
        val cn = ComponentName(this, NowPlayingListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
        runCatching { startActivity(detail) }.onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    companion object {
        const val EXTRA_OPEN_SPOTIFY = "com.thescarletcoder.lyrics.OPEN_SPOTIFY"
    }
}

private enum class Panel { NONE, PICKER, SETTINGS, TUNING }

@Composable
private fun LyricsScreen(
    np: NowPlaying, repo: LyricsRepo, hasAccess: Boolean, theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    onGrant: () -> Unit, onSpotify: () -> Unit,
) {
    val context = LocalContext.current
    val track by np.track.collectAsState()
    val playing by np.playing.collectAsState()
    val art by np.art.collectAsState()
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val tuned = Tunables.version.intValue // live tuning
    val p = L.p

    // null = nothing yet for this song (the old lyrics have faded out; "Finding lyrics..." only if it takes a while).
    var shown by remember { mutableStateOf<Shown?>(null) }
    var finding by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var sheet by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    // "You're set": access was just granted while Lyrics was showing the welcome screen.
    var sawWelcome by remember { mutableStateOf(!hasAccess) }
    var youreSet by remember { mutableStateOf(false) }
    LaunchedEffect(hasAccess) {
        if (!hasAccess) { sawWelcome = true; return@LaunchedEffect }
        if (sawWelcome) { sawWelcome = false; youreSet = true; delay(Motion.duration(context, Motion.LUX_MS + 450 + 1100).toLong()); youreSet = false }
    }

    // Reload only when the song itself changes (title and artist). Players update a song's details while it plays
    // (the length arrives late, the album art, a blank moment between updates); each of those used to drop the
    // lyrics and fetch them again, so a shaky connection showed "Can't reach LRCLIB" mid-song (Sam, 2026-10-09).
    val song = track?.let { it.title + "\u0001" + it.artist }
    var shownFor by remember { mutableStateOf<Pair<String, Int>?>(null) }   // the song and reload the lyrics belong to
    LaunchedEffect(hasAccess, song, reload) {
        if (!hasAccess) { shown = Shown.NoAccess; shownFor = null; return@LaunchedEffect }
        if (song == null) {
            delay(2_000)   // a blank moment between updates isn't "nothing playing": the lyrics stay meanwhile
            shown = Shown.NothingPlaying; shownFor = null
            finding = false; slow = false
            return@LaunchedEffect
        }
        if (shownFor == song to reload && shown is Shown.Found) return@LaunchedEffect   // the same song came back
        if (panel == Panel.PICKER) panel = Panel.NONE
        finding = false; slow = false
        shown = null
        shownFor = null
        var done = false
        val timer = launch {
            delay(Tunables.int(LyricsTuning.LOADING_DELAY_MS).toLong())
            if (!done) finding = true
            delay(3000L - Tunables.int(LyricsTuning.LOADING_DELAY_MS))
            if (!done) slow = true
        }
        var result = repo.load(track ?: return@LaunchedEffect)
        done = true; timer.cancel()
        finding = false; slow = false
        shown = result
        shownFor = song to reload
        // Still offline after the retries: keep trying quietly, so the lyrics appear once the connection is back.
        while (result is Shown.Offline) {
            delay(15_000)
            result = repo.load(track ?: return@LaunchedEffect)
            if (result !is Shown.Offline) shown = result
        }
    }

    // Album look for this track (thumbnail and tint), from the media session's own bitmap.
    var look by remember { mutableStateOf<AlbumLook?>(null) }
    val thumbPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    LaunchedEffect(track?.key, art) {
        val t = track; val a = art
        look = if (t == null || a == null) null else AlbumTint.load(t.key, a, thumbPx)
    }
    val songShowing = hasAccess && track != null
    val tint = look?.swatch?.takeIf { songShowing }?.let { TintMath.tint(p.dark, it, Tunables[LyricsTuning.TINT_STRENGTH], Tunables[LyricsTuning.TINT_SATURATION].toDouble()) }
    val bg by animateColorAsState(
        Color(0xFF000000.toInt() or (tint ?: (p.bg and 0xFFFFFF))),
        tween(Motion.duration(context, Tunables.int(LyricsTuning.TINT_FADE_MS)), easing = Motion.NAV_EASE), label = "tint",
    )

    // Header auto-hide (Sam's ruling): while a song plays with no sheet or panel open, fade 3 s after the last touch.
    var chrome by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val canHide = playing && songShowing && !sheet && panel == Panel.NONE && shown is Shown.Found
    LaunchedEffect(canHide, touches) {
        chrome = true
        if (canHide) { delay(Tunables.int(LyricsTuning.CHROME_HIDE_MS).toLong()); chrome = false }
    }
    val fadeMs = Motion.duration(context, Tunables.int(LyricsTuning.CHROME_FADE_MS))
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(fadeMs, easing = Motion.NAV_EASE), label = "chrome")
    val chromeLift by animateDpAsState(if (chrome) 0.dp else (-6).dp, tween(fadeMs, easing = Motion.NAV_EASE), label = "lift")

    BackHandler(enabled = sheet || panel != Panel.NONE) {
        when {
            sheet -> sheet = false
            panel == Panel.TUNING -> panel = Panel.SETTINGS
            else -> panel = Panel.NONE
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(bg)
            .pointerInput(Unit) {
                // Every touch resets the hide timer. While the header is hidden, a tap only brings it back
                // (it doesn't seek); a swipe still scrolls the lyrics.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touches++
                    if (chrome) return@awaitEachGesture
                    var moved = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (!ch.pressed) { if (!moved) { ch.consume(); chrome = true }; break }
                    }
                }
            }
            .safeDrawingPadding(),
    ) {
        val compact = maxHeight < 560.dp
        val inSplit = (context as? android.app.Activity)?.isInMultiWindowMode == true
        CompositionLocalProvider(LocalCompact provides compact) {
            Column(Modifier.fillMaxSize()) {
                // The header keeps its space while hidden, so nothing moves when it fades.
                Box(Modifier.fillMaxWidth().offset(y = chromeLift).graphicsLayer { alpha = chromeAlpha }) {
                    Header(track.takeIf { hasAccess }, look.takeIf { songShowing }, playing, np::playPause) { sheet = true }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val t = track
                    when {
                        panel == Panel.TUNING -> TuningPage { panel = Panel.SETTINGS }
                        panel == Panel.SETTINGS -> SettingsPanel(repo, hasAccess, theme, onTheme, onGrant, onCleared = { reload++ },
                            onTuning = { panel = Panel.TUNING }, onClose = { panel = Panel.NONE })
                        panel == Panel.PICKER && t != null -> Picker(t, shown as? Shown.Found, repo,
                            onPick = { c -> scope.launch { shown = repo.pin(t, c); panel = Panel.NONE } },
                            onAuto = { scope.launch { shown = null; shown = repo.unpin(t); panel = Panel.NONE } },
                            onClose = { panel = Panel.NONE })
                        youreSet -> YoureSet()
                        else -> Content(shown, finding, slow, np, onGrant, onSpotify, onPick = { panel = Panel.PICKER }, onRetry = { reload++ })
                    }
                }
            }
            MoreSheet(
                sheet, shown, hasTrack = track != null && hasAccess, showSpotify = !inSplit,
                onClose = { sheet = false }, onWrong = { panel = Panel.PICKER },
                onNudge = { d -> val t = track; val s = shown
                    if (t != null && s is Shown.Found) scope.launch { shown = s.copy(offsetMs = repo.nudge(t, d)) } },
                onSpotify = onSpotify, onSettings = { panel = Panel.SETTINGS },
            )
        }
    }
}

/** What fills the screen under the header. Old content fades out, then the new fades in. */
@Composable
private fun Content(
    shown: Shown?, finding: Boolean, slow: Boolean, np: NowPlaying,
    onGrant: () -> Unit, onSpotify: () -> Unit, onPick: () -> Unit, onRetry: () -> Unit,
) {
    val swap = Motion.duration(LocalContext.current, Tunables.int(LyricsTuning.SWAP_MS))
    AnimatedContent(
        targetState = shown, label = "content",
        contentKey = { s -> if (s is Shown.Found) "found:${s.candidate.id}:${s.synced}" else s?.javaClass?.simpleName ?: "none" },
        transitionSpec = { fadeIn(tween(swap, delayMillis = swap)) togetherWith fadeOut(tween(swap)) },
    ) { s ->
        when (s) {
            null -> if (finding) Finding(slow) else Box(Modifier.fillMaxSize())
            Shown.NoAccess -> Welcome(onGrant)
            Shown.NothingPlaying -> EmptyState("Nothing playing", "Start a song and the lyrics show up here.", "Open Spotify", onSpotify)
            Shown.Loading -> Finding(slow)
            Shown.NotFound -> EmptyState("No lyrics found", "LRCLIB doesn't have this one yet.", "Search by hand", onPick)
            Shown.Offline -> EmptyState(
                "Can't reach LRCLIB",
                "Check your internet connection. If you use a firewall app, let Scarlet Lyrics reach lrclib.net.",
                "Try again", onRetry,
            )
            is Shown.Found -> when {
                s.lines.isEmpty() -> EmptyState(if (s.candidate.instrumental) "Instrumental" else "No words for this one", null, null, null, raven = false)
                s.synced -> SyncedLyrics(s, np)
                else -> PlainLyrics(s)
            }
        }
    }
}
