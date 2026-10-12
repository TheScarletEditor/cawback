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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.LibraryDatabase
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.lyrics.library.PlayRecorder
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ScarletTokens
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.Tunables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LyricsActivity : ComponentActivity() {
    private lateinit var nowPlaying: NowPlaying
    private lateinit var settings: LyricsSettings
    private lateinit var route: AudioRoute
    /** Times plays while Lyrics is on screen; records only in "only while Lyrics is open" (History.records). */
    private lateinit var recorder: PlayRecorder
    private val access = mutableStateOf(false)
    /** Flip half-folded on a table: where the fold is, in this window (px from the top), else null (Flex mode). */
    private val foldTop = mutableStateOf<Int?>(null)
    /** Set when Lyrics is opened fresh; the first start after that checks whether your music app should join it. */
    private var checkPlayer = false
    // OPEN_PLAYER arrived: open the music app in onStart, once what's playing is known ("whatever is playing" needs it).
    private var openPlayerPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LyricsTuning.init(this)
        val prefs = LyricsPrefs(this)
        settings = LyricsSettings(this)
        route = AudioRoute(this)
        nowPlaying = NowPlaying(this) { settings.rules() }
        recorder = PlayRecorder(this, History.Where.SCREEN)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(nowPlaying.track, nowPlaying.playing) { t, p -> t to p }.collect { (t, p) -> recorder.onState(t, p) }
            }
        }
        if (savedInstanceState == null && settings.keepPlaysDays > 0 && LibraryDatabase.exists(this)) {
            lifecycleScope.launch(Dispatchers.IO) { runCatching { LibraryRepo(this@LyricsActivity).prune(settings.keepPlaysDays) } }
        }
        if (savedInstanceState == null) lifecycleScope.launch(Dispatchers.IO) {
            runCatching { LyricsStore(this@LyricsActivity).sealOldFiles() }   // 0.2.0's plain cache files, encrypted
            // A shared export only needs to outlive the share itself.
            File(cacheDir, "exports").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        }
        val repo = LyricsRepo(LyricsStore(this))
        if (savedInstanceState == null) checkPlayer = !maybeOpenPlayer(intent)
        watchFold()
        updateShortcuts()
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
                CompositionLocalProvider(
                    LocalPalette provides p, LocalSettings provides settings,
                    LocalOutputDelay provides settings.delayFor(route.output),
                ) {
                    LyricsScreen(
                        nowPlaying, repo, access.value, theme = choice,
                        onTheme = { c -> choice = c; prefs.theme = c; ScarletTokens.applyToSystem(this, c) },
                        onGrant = ::openAccessSettings, onPlayer = ::openPlayerAlongside,
                        playerLabel = settings.effectivePlayer()?.let { MediaApps.label(this, it) },
                        output = route.output, foldTop = foldTop.value,
                        onFollowChanged = { nowPlaying.refresh(); updateShortcuts() },
                        onPinShortcut = ::pinShortcut,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!maybeOpenPlayer(intent) && intent.action == Intent.ACTION_MAIN) checkPlayer = true   // opened again from the launcher
    }

    override fun onStart() {
        super.onStart()
        access.value = nowPlaying.hasAccess()
        nowPlaying.start()
        route.start()
        if (openPlayerPending) {
            openPlayerPending = false
            window.decorView.post { openPlayerAlongside() }
        }
        if (checkPlayer) {
            checkPlayer = false
            // Ruling of 2026-10-09: opening Lyrics while your music app is already playing brings it up beside Lyrics, on phones
            // and tablets, unless the screen is already split. Checked once the window knows its mode.
            window.decorView.post {
                val pkg = settings.effectivePlayer() ?: nowPlaying.currentPackage   // no app picked: whatever is playing
                if (!isInMultiWindowMode && pkg != null && nowPlaying.isPlaying(pkg)) openPlayerAlongside()
            }
        }
    }

    override fun onStop() {
        recorder.close()
        nowPlaying.stop()
        route.stop()
        super.onStop()
    }

    /**
     * Scarlet Command's Listen button and the "Lyrics + your app" shortcuts send this: open your music app in the other
     * half of the screen. True if it did. (OPEN_SPOTIFY is the older name, still understood.)
     */
    private fun maybeOpenPlayer(i: Intent?): Boolean {
        if (i == null) return false
        val asked = i.getBooleanExtra(EXTRA_OPEN_PLAYER, false) || i.getBooleanExtra(EXTRA_OPEN_SPOTIFY, false)
        if (!asked) return false
        i.removeExtra(EXTRA_OPEN_PLAYER); i.removeExtra(EXTRA_OPEN_SPOTIFY)
        // Already on screen: what's playing is known now. Otherwise onStart opens it once it is.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) openPlayerAlongside() else openPlayerPending = true
        return true
    }

    /** Your music app (or, with "whatever is playing", the app playing now) in the other half of the screen. */
    private fun openPlayerAlongside() {
        val pkg = settings.effectivePlayer() ?: nowPlaying.currentPackage ?: return
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { startActivity(launch) }
    }

    /** "Lyrics + <your app>": opens Lyrics with [pkg] alongside. */
    private fun playerShortcut(pkg: String) = android.content.pm.ShortcutInfo.Builder(this, SHORTCUT_ID)
        .setShortLabel("Lyrics + " + MediaApps.label(this, pkg))
        .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
        .setIntent(Intent(this, LyricsActivity::class.java).setAction(ACTION_WITH_PLAYER).putExtra(EXTRA_OPEN_PLAYER, true))
        .build()

    /** The long-press shortcut, kept in step with the chosen app. */
    private fun updateShortcuts() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        val pkg = settings.effectivePlayer()
        if (pkg == null) { runCatching { sm.removeAllDynamicShortcuts() }; return }
        runCatching { sm.dynamicShortcuts = listOf(playerShortcut(pkg)) }
    }

    /** Settings: put "Lyrics + <your app>" on the home screen (Android asks first). */
    private fun pinShortcut() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        val pkg = settings.effectivePlayer() ?: return
        if (!sm.isRequestPinShortcutSupported) return
        runCatching { sm.requestPinShortcut(playerShortcut(pkg), null) }
    }

    /** Flex mode on flip phones: half-folded with the fold across the screen, lyrics above it and controls below. */
    private fun watchFold() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@LyricsActivity).windowLayoutInfo(this@LyricsActivity).collect { info ->
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull {
                        it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL
                    }
                    foldTop.value = fold?.bounds?.top
                }
            }
        }
    }

    private fun openAccessSettings() {
        val cn = ComponentName(this, NowPlayingListener::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString())
        runCatching { startActivity(detail) }.onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    companion object {
        const val EXTRA_OPEN_SPOTIFY = "com.thescarletcoder.lyrics.OPEN_SPOTIFY"
        const val EXTRA_OPEN_PLAYER = "com.thescarletcoder.lyrics.OPEN_PLAYER"
        private const val ACTION_WITH_PLAYER = "com.thescarletcoder.lyrics.WITH_PLAYER"
        private const val SHORTCUT_ID = "with_player"
    }
}

internal enum class Panel {
    NONE, PICKER, SETTINGS, TUNING, LIBRARY, SONG, EXPORT;

    /** Where one step back goes (system Back and the now-playing bar). Pure, so it is unit tested. */
    fun back(songFromLibrary: Boolean): Panel = when {
        this == TUNING -> SETTINGS
        this == SONG && songFromLibrary -> LIBRARY
        this == EXPORT -> LIBRARY
        else -> NONE
    }
}

@Composable
private fun LyricsScreen(
    np: NowPlaying, repo: LyricsRepo, hasAccess: Boolean, theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    onGrant: () -> Unit, onPlayer: () -> Unit, playerLabel: String?, output: Output, foldTop: Int?,
    onFollowChanged: () -> Unit, onPinShortcut: () -> Unit,
) {
    val settings = LocalSettings.current
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
    // Saveable: a font size or keyboard change recreates the activity, and the open page should survive it.
    var panel by rememberSaveable { mutableStateOf(Panel.NONE) }
    // The Settings tab: kept through Tuning and a recreate; set from the way in when Settings opens (0.4.1, Q5).
    var settingsTab by rememberSaveable { mutableStateOf(SettingsTab.LYRICS) }
    fun openSettings(way: SettingsWayIn) { settingsTab = way.tab; panel = Panel.SETTINGS }
    // The step out of Settings (not to Tuning; a recreate isn't a step): results of finished work have been seen.
    // Running work keeps going, and its result waits for the next visit.
    var lastPanel by remember { mutableStateOf(panel) }
    LaunchedEffect(panel) {
        fun inSettings(p: Panel) = p == Panel.SETTINGS || p == Panel.TUNING
        if (inSettings(lastPanel) && !inSettings(panel)) SettingsWork.runner.forgetFinished()
        lastPanel = panel
    }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var songSheet by rememberSaveable { mutableStateOf(false) }   // This song (0.5.0)
    var sheetSong by rememberSaveable { mutableStateOf<String?>(null) }   // the song it was opened for
    var reload by remember { mutableIntStateOf(0) }
    // The Library: which song's page is open, where Back goes from it, and the playing song's uid (moments, seeking).
    var songUid by rememberSaveable { mutableStateOf<String?>(null) }
    var songFromLibrary by rememberSaveable { mutableStateOf(false) }
    var songPending by remember { mutableStateOf<Track?>(null) }   // the playing song, not in the Library yet
    var playingUid by remember { mutableStateOf<String?>(null) }
    // A lyric line was long-pressed: the time to bookmark (the sheet is open while this is set).
    var momentAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var momentTrack by remember { mutableStateOf<Track?>(null) }   // the song that line belongs to

    // "You're set": access was just granted while Lyrics was showing the welcome screen.
    var sawWelcome by remember { mutableStateOf(!hasAccess) }
    var youreSet by remember { mutableStateOf(false) }
    LaunchedEffect(hasAccess) {
        if (!hasAccess) { sawWelcome = true; return@LaunchedEffect }
        if (sawWelcome) { sawWelcome = false; youreSet = true; delay(Motion.duration(context, Motion.LUX_MS + 450 + 1100).toLong()); youreSet = false }
    }

    // Reload only when the song itself changes (title and artist). Players update a song's details while it plays
    // (the length arrives late, the album art, a blank moment between updates); each of those used to drop the
    // lyrics and fetch them again, so a shaky connection showed "Can't reach LRCLIB" mid-song (2026-10-09).
    val song = track?.let { it.title + "\u0001" + it.artist }
    var shownFor by remember { mutableStateOf<Pair<String, Int>?>(null) }   // the song and reload the lyrics belong to
    // A clear, reset or delete in Settings counts as a reload too (SettingsWork.lyricsChanges).
    val reloads = reload + SettingsWork.lyricsChanges.intValue
    LaunchedEffect(hasAccess, song, reloads) {
        if (!hasAccess) { shown = Shown.NoAccess; shownFor = null; return@LaunchedEffect }
        if (song == null) {
            delay(2_000)   // a blank moment between updates isn't "nothing playing": the lyrics stay meanwhile
            shown = Shown.NothingPlaying; shownFor = null
            finding = false; slow = false
            return@LaunchedEffect
        }
        if (shownFor == song to reloads && shown is Shown.Found) return@LaunchedEffect   // the same song came back
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
        shownFor = song to reloads
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

    // Which Library song is playing, for its page (only looked up while a song page is open, and never creates one).
    LaunchedEffect(song, panel) {
        val t = track
        playingUid = if (panel != Panel.SONG || t == null || !LibraryDatabase.exists(context)) null
            else withContext(Dispatchers.IO) { runCatching { LibraryRepo(context).songFor(t, create = false)?.uid }.getOrNull() }
    }

    // Header auto-hide (owner's ruling): while a song plays with no sheet or panel open, fade 3 s after the last touch.
    var chrome by remember { mutableStateOf(true) }
    var touches by remember { mutableIntStateOf(0) }
    val canHide = playing && songShowing && !sheet && !songSheet && momentAt == null && panel == Panel.NONE && shown is Shown.Found
    LaunchedEffect(canHide, touches) {
        chrome = true
        if (canHide) { delay(Tunables.int(LyricsTuning.CHROME_HIDE_MS).toLong()); chrome = false }
    }
    val fadeMs = Motion.duration(context, Tunables.int(LyricsTuning.CHROME_FADE_MS))
    val chromeAlpha by animateFloatAsState(if (chrome) 1f else 0f, tween(fadeMs, easing = Motion.NAV_EASE), label = "chrome")
    val chromeLift by animateDpAsState(if (chrome) 0.dp else (-6).dp, tween(fadeMs, easing = Motion.NAV_EASE), label = "lift")

    // Keep the screen on while synced lyrics play (Settings, default on: a tablet as a bedside display).
    val keepOn = settings.keepScreenOn && playing && (shown as? Shown.Found)?.synced == true
    LaunchedEffect(keepOn) {
        val w = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (keepOn) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // One step back from a page: the system Back and the now-playing bar (Header with onBack) both use it.
    fun pageBack() { panel = panel.back(songFromLibrary) }
    BackHandler(enabled = sheet || songSheet || momentAt != null || panel != Panel.NONE) {
        when {
            sheet -> sheet = false
            songSheet -> songSheet = false
            momentAt != null -> momentAt = null
            else -> pageBack()
        }
    }
    val onBack: (() -> Unit)? = if (panel != Panel.NONE) ::pageBack else null
    // This song: on the lyrics screen whenever a song's lyrics (or "No lyrics found") are showing, and while the next
    // song's lyrics load, so the title doesn't jump (owner Q20).
    val songButton: (() -> Unit)? = if (panel == Panel.NONE && songShowing && (shown is Shown.Found || shown is Shown.NotFound || shown == null)) {
        { songSheet = true; sheetSong = song }
    } else null
    // The sheet belongs to the song it was opened for: a new song, or nothing to show, closes it (a recreate doesn't).
    val songGone = songButton == null || song != sheetSong
    LaunchedEffect(songGone) { if (songGone) songSheet = false }

    val inSplit = (context as? android.app.Activity)?.isInMultiWindowMode == true
    // The sheets sit outside the safe-area padding, so their dimming covers the whole screen, status bar included.
    Box(
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
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 560.dp
            val density = LocalDensity.current
            // Flex mode: the fold's position inside this box (it's measured in window pixels).
            var boxTop by remember { mutableIntStateOf(0) }
            var headerPx by remember { mutableIntStateOf(0) }
            val flexTop = foldTop?.let { with(density) { (it - boxTop).toDp() } }?.takeIf { it > 120.dp && it < maxHeight - 120.dp }
            CompositionLocalProvider(LocalCompact provides (compact || flexTop != null)) {
                Column(Modifier.fillMaxSize().onGloballyPositioned { boxTop = it.positionInWindow().y.toInt() }) {
                    // The header keeps its space while hidden, so nothing moves when it fades. In Flex mode the controls
                    // sit below the fold instead.
                    // In Flex mode a page still gets the now-playing bar on top, so the way back is always in view; the page
                    // gives up the bar's height so it still ends at the fold.
                    val barOnTop = flexTop != null && onBack != null
                    if (flexTop == null || barOnTop) Box(Modifier.fillMaxWidth().offset(y = chromeLift).graphicsLayer { alpha = chromeAlpha }
                        .onSizeChanged { headerPx = it.height }) {
                        Header(track.takeIf { hasAccess }, look.takeIf { songShowing }, playing, np::playPause, onMore = { sheet = true }, onBack = onBack,
                            onSong = songButton, unsure = (shown as? Shown.Found)?.unsure == true,
                            backTo = when (panel.back(songFromLibrary)) { Panel.SETTINGS -> "Settings"; Panel.LIBRARY -> "Library"; else -> "Lyrics" })
                    }
                    val pageTop = if (barOnTop) with(density) { headerPx.toDp() } else 0.dp
                    Box(if (flexTop != null) Modifier.height((flexTop - pageTop).coerceAtLeast(0.dp)).fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()) {
                        val t = track
                        when {
                            panel == Panel.TUNING -> TuningPage()
                            panel == Panel.SETTINGS -> SettingsPanel(repo, hasAccess, theme, onTheme, onGrant,
                                output = output, onFollowChanged = onFollowChanged, onPinShortcut = onPinShortcut,
                                onTuning = { panel = Panel.TUNING }, tab = settingsTab, onTab = { settingsTab = it })
                            panel == Panel.PICKER && t != null -> Picker(t, shown as? Shown.Found, repo,
                                onPick = { c -> scope.launch { shown = repo.pin(t, c); panel = Panel.NONE } },
                                onAuto = { scope.launch { shown = null; shown = repo.unpin(t); panel = Panel.NONE } })
                            panel == Panel.LIBRARY -> LibraryPanel(
                                onSong = { songUid = it; songPending = null; songFromLibrary = true; panel = Panel.SONG },
                                onExport = { panel = Panel.EXPORT },
                                onSettings = { openSettings(SettingsWayIn.LIBRARY) })
                            panel == Panel.EXPORT -> ExportPanel()
                            panel == Panel.SONG && (songUid != null || songPending != null) -> {
                                val here = t != null && if (songUid != null) songUid == playingUid else songPending?.let { it.title == t.title && it.artist == t.artist } == true
                                SongPage(songUid, songPending, position = if (here) np::positionMs else null, onSeek = if (here) np::seekTo else null,
                                    onClose = { panel = if (songFromLibrary) Panel.LIBRARY else Panel.NONE })
                            }
                            youreSet -> YoureSet()
                            else -> Content(shown, finding, slow, np, onGrant, playerLabel, onPlayer, onChoosePlayer = { openSettings(SettingsWayIn.CHOOSE_MUSIC_APP) },
                                onPick = { panel = Panel.PICKER }, onRetry = { reload++ }, onMoment = { momentAt = it; momentTrack = track })
                        }
                    }
                    if (flexTop != null) FlexControls(track.takeIf { hasAccess }, look.takeIf { songShowing }, playing, np::playPause,
                        onSong = songButton, unsure = (shown as? Shown.Found)?.unsure == true) { sheet = true }
                }
            }
        }
        SongSheet(
            songSheet, shown, onClose = { songSheet = false }, onWrong = { panel = Panel.PICKER },
            // Applied to what shows now, and only if it's still the same version: a song that changed meanwhile keeps its
            // own, and quick taps all count.
            onNudge = { d -> val t = track; val s = shown
                if (t != null && s is Shown.Found) scope.launch {
                    val next = repo.nudge(t, d)
                    val cur = shown
                    if (cur is Shown.Found && cur.candidate.id == s.candidate.id) shown = cur.copy(offsetMs = next)
                } },
            onClear = { val t = track; val s = shown
                if (t != null && s is Shown.Found) scope.launch {
                    repo.clearTiming(t)
                    val cur = shown
                    if (cur is Shown.Found && cur.candidate.id == s.candidate.id) shown = cur.copy(offsetMs = 0, anchors = emptyList())
                } },
        )
        MoreSheet(
            sheet, shown, hasTrack = track != null && hasAccess, playerLabel = playerLabel?.takeIf { !inSplit },
            onClose = { sheet = false },
            onPlayer = onPlayer, onSettings = { openSettings(SettingsWayIn.MORE_SHEET) },
            onSongPage = {
                val t = track
                if (t != null) scope.launch {
                    // Opening the page never adds the song to the Library: saving a note, favorite or moment does.
                    songUid = withContext(Dispatchers.IO) { runCatching { LibraryRepo(context).songFor(t, create = false)?.uid }.getOrNull() }
                    songPending = if (songUid == null) t else null
                    songFromLibrary = false
                    panel = Panel.SONG
                }
            },
            onLibrary = { panel = Panel.LIBRARY },
        )
        MomentSheet(momentAt, onClose = { momentAt = null }, onSave = { at, label ->
            val t = momentTrack ?: track
            t != null && withContext(Dispatchers.IO) {
                runCatching { LibraryRepo(context).run { songFor(t, create = true)?.let { addMoment(it.uid, at, label) } != null } }.getOrDefault(false)
            }
        })
    }
}

/** What fills the screen under the header. Old content fades out, then the new fades in. */
@Composable
private fun Content(
    shown: Shown?, finding: Boolean, slow: Boolean, np: NowPlaying,
    onGrant: () -> Unit, playerLabel: String?, onPlayer: () -> Unit, onChoosePlayer: () -> Unit, onPick: () -> Unit, onRetry: () -> Unit,
    onMoment: (Long) -> Unit,
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
            Shown.NothingPlaying ->
                if (playerLabel != null) EmptyState("Nothing playing", "Start a song and the lyrics show up here.", "Open $playerLabel", onPlayer)
                else EmptyState("Nothing playing", "Start a song in any music app and the lyrics show up here.", "Choose your music app", onChoosePlayer)
            Shown.NotFound -> EmptyState("No lyrics found", "LRCLIB doesn't have this one yet.", "Search by hand", onPick)
            Shown.Offline -> EmptyState(
                "Can't reach LRCLIB",
                "Check your internet connection. If you use a firewall app, let Scarlet Lyrics reach lrclib.net.",
                "Try again", onRetry,
            )
            is Shown.Found -> when {
                s.lines.isEmpty() -> EmptyState(if (s.candidate.instrumental) "Instrumental" else "No words for this one", null, null, null, raven = false)
                s.synced -> SyncedLyrics(s, np, onMoment)
                else -> PlainLyrics(s)
            }
        }
    }
}
