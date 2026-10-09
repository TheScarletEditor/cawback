package com.thescarletcoder.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import com.thescarletcoder.scarlet.Motion
import com.thescarletcoder.scarlet.ThemeChoice
import com.thescarletcoder.scarlet.TuningPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Empty states (nothing playing, not found, offline): raven and halo, Fraunces headline, one washed button. */
@Composable
fun EmptyState(title: String, body: String?, action: String?, onAction: (() -> Unit)?, raven: Boolean = true) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
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
        Text("Lyrics for whatever's playing", fontFamily = Fraunces, fontSize = 30.sp, color = L.text, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp))
        Text(
            "It reads only the song title, artist and position. To find the words, it sends the song's title, artist, " +
                "album and length to lrclib.net. Nothing else leaves your device.",
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
fun Picker(t: Track, current: Shown.Found?, repo: LyricsRepo, onPick: (Candidate) -> Unit, onAuto: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    // TextFieldValue so the cursor starts at the end of the pre-filled search (Sam couldn't reach the end on the phone).
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

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        // The field gets the full width; the buttons go underneath (narrow phone screens).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(query, { query = it }, ::run)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Washed("Search", ::run)
                Washed("Close", onClose)
            }
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
private fun SearchField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onSearch: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        textStyle = TextStyle(color = L.text, fontSize = 17.sp, fontFamily = Inter), cursorBrush = SolidColor(L.text),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().background(L.wash, shape).border(1.dp, L.washEdge, shape).padding(14.dp)) { inner() }
        },
    )
}

/** Settings: theme, saved lyrics (count + clear), notification access, about, and Tuning in personal builds. */
@Composable
fun SettingsPanel(
    repo: LyricsRepo, hasAccess: Boolean, theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    onGrant: () -> Unit, onCleared: () -> Unit, onTuning: () -> Unit, onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf<Int?>(null) }
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { saved = repo.savedCount() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Settings", fontFamily = Fraunces, fontSize = 28.sp, color = L.text)

        SettingsSection("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(ThemeChoice.SYSTEM to "System", ThemeChoice.DARK to "Dark", ThemeChoice.LIGHT to "Light").forEach { (c, name) ->
                Choice(name, theme == c) { onTheme(c) }
            }
        }

        SettingsSection("Saved lyrics")
        Text(
            when (val n = saved) {
                null -> " "
                0 -> "Nothing saved on this device."
                1 -> "1 song saved on this device."
                else -> "$n songs saved on this device."
            },
            fontFamily = Inter, fontSize = 17.sp, color = L.text,
        )
        Text("Includes your \"Wrong lyrics?\" picks and timing nudges.", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.padding(top = 12.dp)) {
            when {
                confirming -> Column {
                    Text("Clear everything saved? This can't be undone.", fontFamily = Inter, fontSize = 16.sp, color = L.text)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Washed("Clear", {
                            scope.launch {
                                repo.clearSaved()
                                saved = repo.savedCount()
                                confirming = false; cleared = true
                                onCleared()
                            }
                        })
                        Washed("Cancel", { confirming = false })
                    }
                }
                cleared -> Text("Cleared.", fontFamily = Inter, fontSize = 16.sp, color = L.soft)
                saved != null && saved != 0 -> Washed("Clear saved lyrics", { confirming = true })
            }
        }

        SettingsSection("Notification access")
        Text(
            if (hasAccess) "On. Scarlet Lyrics can see the song title, artist and position. It never reads your notifications."
            else "Off. Scarlet Lyrics needs it to see what's playing.",
            fontFamily = Inter, fontSize = 17.sp, color = L.text,
        )
        Box(Modifier.padding(top = 12.dp)) { Washed("Open the setting", onGrant) }

        SettingsSection("About")
        Text("Scarlet Lyrics ${BuildConfig.VERSION_NAME}", fontFamily = Inter, fontSize = 17.sp, color = L.text)
        Text("Lyrics from LRCLIB (lrclib.net), the only place this app connects to.", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))
        Text("Free and open source (GPL-3.0). By The Scarlet Coder, thescarletcoder.com", fontFamily = Inter, fontSize = 15.sp, color = L.soft, modifier = Modifier.padding(top = 4.dp))

        if (BuildConfig.TUNING) {
            SettingsSection("Tuning")
            Text("Feel values for this build. Not in public releases.", fontFamily = Inter, fontSize = 15.sp, color = L.soft)
            Box(Modifier.padding(top = 12.dp)) { Washed("Open tuning", onTuning) }
        }

        Box(Modifier.padding(top = 28.dp, bottom = 24.dp)) { Washed("Close", onClose) }
    }
}

@Composable
fun TuningPage(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tuning", fontFamily = Fraunces, fontSize = 28.sp, color = L.text, modifier = Modifier.weight(1f))
            Washed("Back", onClose)
        }
        TuningPanel(L.p, Inter)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.clip(shape).background(if (selected) L.text.copy(alpha = 0.14f) else L.wash)
            .border(1.dp, if (selected) L.text.copy(alpha = 0.45f) else L.washEdge, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = L.text) }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = L.soft, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
}
