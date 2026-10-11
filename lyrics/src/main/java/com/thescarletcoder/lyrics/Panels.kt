package com.thescarletcoder.lyrics

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.LibraryDatabase
import com.thescarletcoder.lyrics.library.LibraryKey
import com.thescarletcoder.lyrics.library.LibraryRepo
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.TuningPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

/** Empty states (nothing playing, not found, offline): raven and halo, Fraunces headline, one washed button. */
@Composable
fun EmptyState(title: String, body: String?, action: String?, onAction: (() -> Unit)?, raven: Boolean = true) {
    // Scrolls, like Welcome: a phone in landscape is too short for the raven, text and button.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        if (raven) RavenHalo(96.dp)
        Text(title, fontFamily = Fraunces, fontSize = 28.sp, color = L.text, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        if (body != null) Text(body, fontFamily = Inter, fontSize = 17.sp, color = L.soft, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).widthIn(max = 460.dp))
        if (action != null && onAction != null) Box(Modifier.padding(top = 20.dp)) { Washed(action, onAction) }
    }
}

/** A quiet line while a lookup is slow (only after the loading delay, so cached songs never flash it). */
@Composable
fun Finding(slow: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(if (slow) "Still looking..." else "Finding lyrics...", fontFamily = Inter, fontSize = 17.sp, color = L.soft)
    }
}

/** First run, before notification access. */
@Composable
fun Welcome(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        RavenHalo()
        Text("Lyrics for whatever's playing", fontFamily = Fraunces, fontSize = 30.sp, lineHeight = 38.sp, color = L.text, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp))
        Text(
            "It sees what's playing: the song's title, artist, album, length, position and cover (the cover only tints " +
                "the screen and is never saved). To find the words, it sends the title, artist, album and length to " +
                "lrclib.net, plus anything you type into a lyrics search. Nothing else leaves your device.",
            fontFamily = Inter, fontSize = 17.sp, color = L.soft, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 480.dp),
        )
        Box(Modifier.padding(top = 24.dp)) { Washed("Turn on access", onGrant) }
    }
}

/** Access just granted: the heading glides in, the line under it follows about 450 ms later (LUX). */
@Composable
fun YoureSet() {
    val context = LocalContext.current
    val ms = Motion.duration(context, Motion.LUX_MS)
    val heading = remember { MutableTransitionState(false).apply { targetState = true } }
    val line = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { delay(Motion.duration(context, 450).toLong()); line.targetState = true }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        RavenHalo()
        AnimatedVisibility(heading, enter = fadeIn(tween(ms, easing = Motion.LUX_EASE)) + slideInVertically(tween(ms, easing = Motion.LUX_EASE)) { it / 2 }) {
            Text("You're set", fontFamily = Fraunces, fontSize = 32.sp, color = L.text, modifier = Modifier.padding(top = 12.dp))
        }
        AnimatedVisibility(line, enter = fadeIn(tween(ms, easing = Motion.LUX_EASE)) + slideInVertically(tween(ms, easing = Motion.LUX_EASE)) { it / 2 }) {
            Text("Play a song and the words follow along.", fontFamily = Inter, fontSize = 17.sp, color = L.soft, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun Picker(t: Track, current: Shown.Found?, repo: LyricsRepo, onPick: (Candidate) -> Unit, onAuto: () -> Unit) {
    val scope = rememberCoroutineScope()
    // TextFieldValue so the cursor starts at the end of the pre-filled search (the end was out of reach on a phone).
    var query by remember {
        val start = "${Matching.cleanTitle(t.title)} ${t.artist}"
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    var results by remember { mutableStateOf<List<Candidate>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    fun run() {
        status = "Searching..."; results = null
        scope.launch {
            try {
                var r = repo.search(query.text.trim())
                // Nothing for title + artist? Try the title alone: some songs are filed under a label or composer.
                if (r.isEmpty()) r = repo.searchTitle(Matching.cleanTitle(t.title))
                results = r
                status = if (r.isEmpty()) "Nothing found. Try fewer words." else null
            } catch (_: Exception) { status = "Can't reach LRCLIB. Check your internet connection." }
        }
    }
    LaunchedEffect(Unit) { run() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Other versions", fontFamily = Fraunces, fontSize = 28.sp, color = L.text, modifier = Modifier.padding(bottom = 12.dp))
        // The field gets the full width; the button goes underneath (narrow phone screens).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, ::run)
            Washed("Search", ::run)
        }
        if (current?.pinned == true) Box(Modifier.padding(top = 10.dp)) { Washed("Go back to the automatic match", onAuto) }
        status?.let { Text(it, fontFamily = Inter, fontSize = 16.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp)) }
        LazyColumn(Modifier.fillMaxSize().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(results.orEmpty()) { _, c ->
                val chosen = current?.candidate?.id == c.id
                val durDiff = t.durationMs?.let { Math.abs(c.durationSec - it / 1000.0) }
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape)
                        .background(if (chosen) L.wash else L.surface)
                        .border(1.dp, if (chosen) L.washEdge else Color.Transparent, shape)
                        .clickable { onPick(c) }.padding(16.dp),
                ) {
                    Text(c.track, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = L.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            c.artist, c.album.ifBlank { null },
                            "%d:%02d".format(c.durationSec.toInt() / 60, c.durationSec.toInt() % 60),
                            when { c.synced != null -> "Synced"; c.plain != null -> "Words only"; c.instrumental -> "Instrumental"; else -> null },
                            durDiff?.takeIf { it > 3 }?.let { "length differs by ${it.toInt()}s" },
                            if (chosen) "Showing now" else null,
                        ).joinToString("  ·  "),
                        fontFamily = Inter, fontSize = 15.sp, color = L.soft, maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onSearch: () -> Unit, hint: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = TextStyle(color = L.text, fontSize = 17.sp, fontFamily = Inter), cursorBrush = SolidColor(L.text),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(L.wash, shape).border(1.dp, L.washEdge, shape).padding(14.dp)) {
                if (value.text.isEmpty() && hint != null) Text(hint, fontFamily = Inter, fontSize = 17.sp, color = L.faint)
                inner()
            }
        },
    )
}

/**
 * Settings in four tabs (0.4.1): Lyrics (display, timing, theme), Player (your music app, notification access),
 * Library (listening history, backup) and Privacy (saved lyrics, what's on this device, about, and Tuning in personal
 * builds). The heading and the tabs stay put; each tab scrolls on its own. Work that must finish (backup, restore,
 * deletes, clearing, resetting) runs on [SettingsWork], so a tab change or leaving Settings never stops it halfway.
 */
@Composable
internal fun SettingsPanel(
    repo: LyricsRepo, hasAccess: Boolean, theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    onGrant: () -> Unit, output: Output, onFollowChanged: () -> Unit, onPinShortcut: () -> Unit,
    onTuning: () -> Unit, tab: SettingsTab, onTab: (SettingsTab) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Settings", fontFamily = Fraunces, fontSize = 28.sp, color = L.text)
        SettingsTabs(tab, onTab)
        key(tab) {
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                tab.sections.filter { !it.personal || BuildConfig.TUNING }.forEach { s ->
                    SettingsSection(s.title)
                    when (s) {
                        SettingsSection.DISPLAY -> LyricsDisplaySettings()
                        SettingsSection.TIMING -> TimingSettings(repo, output)
                        SettingsSection.THEME -> ThemeSettings(theme, onTheme)
                        SettingsSection.MUSIC_APP -> MusicAppSettings(onFollowChanged, onPinShortcut)
                        SettingsSection.ACCESS -> AccessSettings(hasAccess, onGrant)
                        SettingsSection.HISTORY -> HistorySettings()
                        SettingsSection.BACKUP -> LibraryBackupSection()
                        SettingsSection.SAVED -> SavedLyricsSettings(repo)
                        SettingsSection.ON_DEVICE -> OnThisDevice(repo, refresh = SettingsWork.version.intValue)
                        SettingsSection.ABOUT -> AboutSettings()
                        SettingsSection.TUNING -> {
                            Text("Feel values for this build. Not in public releases.", fontFamily = Inter, fontSize = 15.sp, color = L.soft)
                            Box(Modifier.padding(top = 12.dp)) { Washed("Open tuning", onTuning) }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The tab row: 48dp tall tabs that say which one is on. One row when all four fit; in a narrow split or with a large
 * font they wrap into two rows of two, so no tab is ever hidden (0.4.1, owner: "Two rows when narrow").
 */
@Composable
private fun SettingsTabs(tab: SettingsTab, onTab: (SettingsTab) -> Unit) {
    Layout(
        content = {
            SettingsTab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .selectable(selected = on, role = Role.Tab) { if (!on) onTab(t) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(t.label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = if (on) L.text else L.soft)
                    Box(Modifier.padding(top = 6.dp).width(24.dp).height(2.dp).background(if (on) L.text else Color.Transparent, RoundedCornerShape(1.dp)))
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
    ) { tabs, c ->
        val natural = tabs.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val perRow = TabFit.perRow(natural, c.maxWidth)
        val cell = c.maxWidth / perRow
        val placed = tabs.mapIndexed { i, m ->
            m.measure(if (perRow == tabs.size) Constraints(maxWidth = natural[i]) else Constraints(maxWidth = cell))
        }
        val rows = placed.chunked(perRow)
        val heights = rows.map { r -> r.maxOf { it.height } }
        layout(c.maxWidth, heights.sum()) {
            var y = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.forEach { p -> p.placeRelative(x, y); x += if (perRow == tabs.size) p.width else cell }
                y += heights[r]
            }
        }
    }
}

@Composable
private fun LyricsDisplaySettings() {
    val settings = LocalSettings.current
    Text("Text size", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(bottom = 8.dp))
    TextSizePreview()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LyricsSettings.TEXT_SIZES.forEachIndexed { i, n -> Choice(n, settings.textSize == i) { settings.chooseTextSize(i) } }
    }
    Text("Lines already sung", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("Dimmed", !settings.hidePast) { settings.hidePastLines(false) }
        Choice("Hidden", settings.hidePast) { settings.hidePastLines(true) }
    }
    Text("After you scroll, follow the song again after", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    // Wraps: on a small phone the three side by side squeezed "8 seconds" to one letter per line.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LyricsSettings.FOLLOW_DELAYS.forEach { s -> Choice("$s seconds", settings.followDelaySec == s) { settings.chooseFollowDelay(s) } }
    }
    Text("Keep the screen on while synced lyrics play", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Choice("On", settings.keepScreenOn) { settings.keepOn(true) }
        Choice("Off", !settings.keepScreenOn) { settings.keepOn(false) }
    }
}

@Composable
private fun TimingSettings(repo: LyricsRepo, output: Output) {
    val settings = LocalSettings.current
    val runner = SettingsWork.runner
    @Suppress("UNUSED_VARIABLE") val work = SettingsWork.version.intValue   // redraw when work starts or ends
    var confirming by remember { mutableStateOf(false) }
    Text(
        "Some headphones play a little late. Set each one once and Lyrics applies it whenever it's connected. Playing through: ${output.label}.",
        fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(bottom = 8.dp),
    )
    Output.entries.forEach { out ->
        Column(Modifier.padding(top = 8.dp)) {
            Text(out.label + if (out == output) "  (now)" else "", fontFamily = Inter, fontSize = 16.sp, color = L.text)
            Text(timingLabel(settings.delayFor(out).toLong()), fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Washed("Earlier", { settings.nudgeOutput(out, 50) }, small = true)
                Washed("Later", { settings.nudgeOutput(out, -50) }, small = true)
            }
        }
    }
    Box(Modifier.padding(top = 14.dp)) {
        val done = runner.result(SettingsWork.NUDGES)
        when {
            done != null && done != WorkRunner.FAILED -> Text(done, fontFamily = Inter, fontSize = 16.sp, color = L.soft)
            runner.busy(SettingsWork.NUDGES) -> Unit
            confirming -> Column {
                Text("Reset the timing nudges on every song? Lyrics you picked by hand stay.", fontFamily = Inter, fontSize = 16.sp, color = L.text)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Reset", {
                        confirming = false
                        runner.run(SettingsWork.NUDGES) { repo.resetNudges(); SettingsWork.lyricsChanged(); "Every song is back to its own timing." }
                    })
                    Washed("Cancel", { confirming = false })
                }
            }
            // After a failure the message shows and the button stays, so it can be tried again.
            else -> Column {
                if (done != null) Text(done, fontFamily = Inter, fontSize = 16.sp, color = L.text, modifier = Modifier.padding(bottom = 10.dp))
                Washed("Reset all timing nudges", { runner.clear(SettingsWork.NUDGES); confirming = true })
            }
        }
    }
}

@Composable
private fun ThemeSettings(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.DARK to "Dark", ThemeChoice.LIGHT to "Light").forEach { (c, name) ->
            Choice(name, theme == c) { onTheme(c) }
        }
    }
}

@Composable
private fun MusicAppSettings(onFollowChanged: () -> Unit, onPinShortcut: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val apps = remember { MediaApps.all(context) }
    val chosen = settings.effectivePlayer() ?: ""
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { a -> Choice(a.label, chosen == a.pkg) { settings.choosePlayer(a.pkg); onFollowChanged() } }
        Choice("Whatever is playing", chosen == "") { settings.choosePlayer(""); onFollowChanged() }
    }
    if (chosen != "") {
        val name = MediaApps.label(context, chosen)
        Text(
            if (settings.onlyPlayer) "Lyrics follows only $name. Podcasts and other apps are never looked up."
            else "Lyrics prefers $name, and follows other apps when they play.",
            fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 10.dp),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("Only $name", settings.onlyPlayer) { settings.followOnlyPlayer(true); onFollowChanged() }
            Choice("Other apps too", !settings.onlyPlayer) { settings.followOnlyPlayer(false); onFollowChanged() }
        }
        Box(Modifier.padding(top = 12.dp)) { Washed("Add \"Lyrics + $name\" to the home screen", onPinShortcut) }
    }
    if (chosen == "" || !settings.onlyPlayer) {
        Text("Never follow", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Text("Ignored apps are never looked up on lrclib.net.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            apps.filter { it.pkg != chosen }.forEach { a ->
                val off = a.pkg in settings.ignored
                Choice(if (off) "${a.label}: ignored" else a.label, off) { settings.setIgnored(a.pkg, !off); onFollowChanged() }
            }
        }
    }
}

@Composable
private fun AccessSettings(hasAccess: Boolean, onGrant: () -> Unit) {
    Text(
        if (hasAccess) "On. Scarlet Lyrics can see the song's title, artist, album, length, position and cover. It never reads your notifications."
        else "Off. Scarlet Lyrics needs it to see what's playing.",
        fontFamily = Inter, fontSize = 17.sp, color = L.text,
    )
    Box(Modifier.padding(top = 12.dp)) { Washed("Open the setting", onGrant) }
}

@Composable
private fun HistorySettings() {
    val context = LocalContext.current
    val settings = LocalSettings.current
    val runner = SettingsWork.runner
    val work = SettingsWork.version.intValue
    var deleting by remember { mutableStateOf<String?>(null) }   // "history" or "library" while confirming
    val hasLibrary = remember(work) { LibraryDatabase.exists(context) }
    Text(
        "Counts the songs you listen to, for your Library. Kept encrypted on this device and never sent anywhere. " +
            "Apps you've ignored are never counted, and no lyrics are kept.",
        fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(bottom = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        History.Mode.entries.forEach { m -> Choice(m.label, settings.historyMode == m) { settings.chooseHistory(m) } }
    }
    if (settings.historyMode == History.Mode.ALWAYS) {
        Text("Counts what you play even while Lyrics is closed.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 8.dp))
    }
    if (settings.historyMode != History.Mode.OFF) {
        val until = settings.historyPausedUntil
        if (settings.historyPaused()) {
            Text(
                if (until == History.PAUSED_UNTIL_ON) "Paused until you turn it back on."
                else "Paused until " + DateFormat.getTimeFormat(context).format(Date(until)) + ".",
                fontFamily = Inter, fontSize = 16.sp, color = L.text, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )
            Washed("Resume", { settings.pauseHistory(0) })
        } else {
            Text("Pause", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Washed("For 1 hour", { settings.pauseHistory(System.currentTimeMillis() + 3_600_000L) }, small = true)
                Washed("Until I turn it back on", { settings.pauseHistory(History.PAUSED_UNTIL_ON) }, small = true)
            }
        }
        Text("Keep plays for", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            History.KEEP_DAYS.forEach { d -> Choice(History.keepLabel(d), settings.keepPlaysDays == d) { settings.chooseKeepPlays(d) } }
        }
        if (settings.keepPlaysDays > 0) {
            Text("Older plays are removed when Lyrics opens. Songs, notes and moments stay.", fontFamily = Inter, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 8.dp))
        }
    }
    if (hasLibrary && !runner.busy(SettingsWork.DELETE)) {
        Box(Modifier.padding(top = 14.dp)) {
            when (val d = deleting) {
                null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Delete listening history", { deleting = "history"; runner.clear(SettingsWork.DELETE) })
                    Washed("Delete the Library", { deleting = "library"; runner.clear(SettingsWork.DELETE) })
                }
                else -> Column {
                    Text(
                        if (d == "history") "Delete every play? Your songs, notes, moments and favorites stay. This can't be undone."
                        else "Delete the whole Library: every song, play, note and moment, and their saved lyrics? This can't be undone.",
                        fontFamily = Inter, fontSize = 16.sp, color = L.text,
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Washed("Delete", {
                            deleting = null
                            runner.clear(SettingsWork.BACKUP); runner.clear(SettingsWork.RESTORE)   // older messages would be out of date
                            val app = context.applicationContext
                            runner.run(SettingsWork.DELETE) {
                                val failed = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val lib = LibraryRepo(app)
                                        if (d == "history") lib.deleteHistory() else lib.deleteAll()
                                    }.exceptionOrNull()
                                }
                                if (d == "library" && failed == null) SettingsWork.lyricsChanged()
                                when (failed) {
                                    null -> if (d == "history") "Listening history deleted." else "Library deleted."
                                    is LibraryKey.Gone -> KEY_GONE
                                    else -> "That didn't finish. Nothing was deleted."
                                }
                            }
                        })
                        Washed("Cancel", { deleting = null })
                    }
                }
            }
        }
    }
    runner.result(SettingsWork.DELETE)?.let {
        Text(it, fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SavedLyricsSettings(repo: LyricsRepo) {
    val runner = SettingsWork.runner
    val work = SettingsWork.version.intValue
    var saved by remember { mutableStateOf<Int?>(null) }
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(work) { saved = repo.savedCount() }   // again after a clear, restore or delete
    Text(
        when (val n = saved) {
            null -> " "
            0 -> "Nothing saved on this device."
            1 -> "1 song saved on this device."
            else -> "$n songs saved on this device."
        },
        fontFamily = Inter, fontSize = 17.sp, color = L.text,
    )
    Text("Includes your \"Other versions\" picks and timing nudges.", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))
    Box(Modifier.padding(top = 12.dp)) {
        val done = runner.result(SettingsWork.SAVED)
        when {
            done != null && done != WorkRunner.FAILED -> Text(done, fontFamily = Inter, fontSize = 16.sp, color = L.soft)
            runner.busy(SettingsWork.SAVED) -> Unit
            confirming -> Column {
                Text("Clear everything saved? This can't be undone.", fontFamily = Inter, fontSize = 16.sp, color = L.text)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Washed("Clear", {
                        confirming = false
                        runner.run(SettingsWork.SAVED) { repo.clearSaved(); SettingsWork.lyricsChanged(); "Cleared." }
                    })
                    Washed("Cancel", { confirming = false })
                }
            }
            // After a failure the message shows (whatever the count says now) and the button stays, so it can be tried again.
            else -> Column {
                if (done != null) Text(done, fontFamily = Inter, fontSize = 16.sp, color = L.text, modifier = Modifier.padding(bottom = 10.dp))
                if (saved != null && saved != 0) Washed("Clear saved lyrics", { runner.clear(SettingsWork.SAVED); confirming = true })
            }
        }
    }
}

@Composable
private fun AboutSettings() {
    Text("Scarlet Lyrics ${BuildConfig.VERSION_NAME}", fontFamily = Inter, fontSize = 17.sp, color = L.text)
    Text("Lyrics from LRCLIB (lrclib.net), the only place this app connects to.", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))
    Text("Free and open source (GPL-3.0). By The Scarlet Coder, thescarletcoder.com", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun TuningPage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tuning", fontFamily = Fraunces, fontSize = 28.sp, color = L.text)
        TuningPanel(L.p, Inter)
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.clip(shape).background(if (selected) L.text.copy(alpha = 0.14f) else L.wash)
            .border(1.dp, if (selected) L.text.copy(alpha = 0.45f) else L.washEdge, shape)
            .selectable(selected = selected, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),   // TalkBack says which is on
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = L.text) }
}

@Composable
internal fun SettingsSection(title: String) {
    Text(title, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
}
