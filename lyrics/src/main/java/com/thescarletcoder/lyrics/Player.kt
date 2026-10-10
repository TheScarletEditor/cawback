package com.thescarletcoder.lyrics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** The settings, for screens deep in the tree (provided by LyricsActivity). */
val LocalSettings = staticCompositionLocalOf<LyricsSettings> { error("LyricsSettings not provided") }
/** Audio delay for the output in use right now, ms (positive = lyrics earlier). */
val LocalOutputDelay = compositionLocalOf { 0 }

/**
 * Lyrics 0.3 "Your player" settings (scope section 5, Sam's rulings 2026-10-09). Compose state, saved on every change.
 * Nothing here leaves the device; none of it is a history.
 */
class LyricsSettings(context: Context) {
    private val p = context.getSharedPreferences("lyrics_prefs", Context.MODE_PRIVATE)

    /** The music app Lyrics follows; "" = whatever is playing; null = not chosen yet (Spotify if it's installed). */
    var player by mutableStateOf(p.getString("player", null))
        private set
    /** Follow only [player] (the suggested default once one is chosen), or whatever plays except [ignored]. */
    var onlyPlayer by mutableStateOf(p.getBoolean("only_player", true))
        private set
    /** Apps Lyrics never follows: never looked up on lrclib.net, never anything. */
    var ignored by mutableStateOf(p.getStringSet("ignored", emptySet())!!.toSet())
        private set
    /** 0..3: Small, Medium, Large, Largest. */
    var textSize by mutableIntStateOf(p.getInt("text_size", 1))
        private set
    /** Keep the screen on while a synced song plays (the tablet on the nightstand). */
    var keepScreenOn by mutableStateOf(p.getBoolean("keep_screen_on", true))
        private set
    /** Lines already sung: dimmed (default) or hidden. */
    var hidePast by mutableStateOf(p.getBoolean("hide_past", false))
        private set
    /** After you scroll, the lyrics follow the song again after this many seconds. */
    var followDelaySec by mutableIntStateOf(p.getInt("follow_delay", 4))
        private set
    /** Audio delay per output, ms. Positive = lyrics earlier (the same sign as the per-song nudge). */
    var delaySpeaker by mutableIntStateOf(p.getInt("delay_speaker", 0)); private set
    var delayWired by mutableIntStateOf(p.getInt("delay_wired", 0)); private set
    var delayBluetooth by mutableIntStateOf(p.getInt("delay_bluetooth", 0)); private set

    fun choosePlayer(pkg: String) { player = pkg; p.edit().putString("player", pkg).apply() }
    fun followOnlyPlayer(on: Boolean) { onlyPlayer = on; p.edit().putBoolean("only_player", on).apply() }
    fun setIgnored(pkg: String, ignore: Boolean) {
        ignored = if (ignore) ignored + pkg else ignored - pkg
        p.edit().putStringSet("ignored", ignored).apply()
    }
    fun chooseTextSize(i: Int) { textSize = i.coerceIn(0, 3); p.edit().putInt("text_size", textSize).apply() }
    fun keepOn(on: Boolean) { keepScreenOn = on; p.edit().putBoolean("keep_screen_on", on).apply() }
    fun hidePastLines(on: Boolean) { hidePast = on; p.edit().putBoolean("hide_past", on).apply() }
    fun chooseFollowDelay(s: Int) { followDelaySec = s; p.edit().putInt("follow_delay", s).apply() }
    fun nudgeOutput(out: Output, deltaMs: Int) {
        val next = (delayFor(out) + deltaMs).coerceIn(-2000, 2000)
        when (out) {
            Output.SPEAKER -> { delaySpeaker = next; p.edit().putInt("delay_speaker", next).apply() }
            Output.WIRED -> { delayWired = next; p.edit().putInt("delay_wired", next).apply() }
            Output.BLUETOOTH -> { delayBluetooth = next; p.edit().putInt("delay_bluetooth", next).apply() }
        }
    }
    fun delayFor(out: Output) = when (out) { Output.SPEAKER -> delaySpeaker; Output.WIRED -> delayWired; Output.BLUETOOTH -> delayBluetooth }

    /** The app actually followed: the chosen one, or Spotify when nothing's chosen yet and it's installed. */
    fun effectivePlayer(context: Context): String? = when (val chosen = player) {
        null -> NowPlaying.SPOTIFY.takeIf { MediaApps.installed(context, NowPlaying.SPOTIFY) }
        "" -> null
        else -> chosen
    }

    fun rules(context: Context) = SessionPick.Rules(effectivePlayer(context), player != "" && onlyPlayer, ignored)

    companion object {
        val TEXT_SIZES = listOf("Small", "Medium", "Large", "Largest")
        /** Font scale for each text size step. */
        val TEXT_SCALE = listOf(0.85f, 1f, 1.18f, 1.36f)
        val FOLLOW_DELAYS = listOf(2, 4, 8)
    }
}

/** Which media session to follow. Pure, so it is unit tested. */
object SessionPick {
    data class Session(val pkg: String, val playing: Boolean)
    data class Rules(val player: String?, val onlyPlayer: Boolean, val ignored: Set<String>)

    fun choose(sessions: List<Session>, r: Rules): Session? {
        val allowed = sessions.filter { it.pkg !in r.ignored }
        if (r.player != null && r.onlyPlayer) return allowed.firstOrNull { it.pkg == r.player }
        return allowed.firstOrNull { it.pkg == r.player }
            ?: allowed.firstOrNull { it.playing }
            ?: allowed.firstOrNull()
    }
}

/** Installed music apps: anything that offers a media browser service (Spotify, YouTube Music, podcast apps...). */
object MediaApps {
    data class App(val pkg: String, val label: String)

    fun all(context: Context): List<App> {
        val pm = context.packageManager
        return pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
            .map { it.serviceInfo.packageName }.distinct()
            .filter { it != context.packageName }
            .map { App(it, label(context, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun installed(context: Context, pkg: String) =
        runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)
}

enum class Output(val label: String) { SPEAKER("Speaker"), WIRED("Wired headphones"), BLUETOOTH("Bluetooth") }

/** Where the sound is going right now, kept current by Android's audio device callbacks. */
class AudioRoute(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    var output by mutableStateOf(read())
        private set
    private val cb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { output = read() }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { output = read() }
    }

    fun start() { am.registerAudioDeviceCallback(cb, null); output = read() }
    fun stop() = am.unregisterAudioDeviceCallback(cb)

    private fun read(): Output {
        val types = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
        return when {
            types.any { it in BLUETOOTH } -> Output.BLUETOOTH
            types.any { it in WIRED } -> Output.WIRED
            else -> Output.SPEAKER
        }
    }

    private companion object {
        val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        val WIRED = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
    }
}
