package com.thescarletcoder.lyrics

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.thescarletcoder.lyrics.library.History
import com.thescarletcoder.lyrics.library.PlayRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Present so Android grants access to media sessions; notifications themselves are ignored. With listening history
 * set to "Always" it also follows what's playing while Lyrics is closed and records plays (the Library). In any other
 * mode it does nothing at all.
 */
class NowPlayingListener : NotificationListenerService() {
    private var follow: NowPlaying? = null
    private var recorder: PlayRecorder? = null
    private var scope: CoroutineScope? = null
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> sync() }

    override fun onListenerConnected() {
        History.prefs(this).registerOnSharedPreferenceChangeListener(prefsChanged)
        sync()
    }

    override fun onListenerDisconnected() {
        History.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsChanged)
        stopFollowing()
    }

    override fun onDestroy() { stopFollowing(); super.onDestroy() }

    /** Follow while history is "Always"; stop (finishing the song being timed) when it isn't. */
    private fun sync() {
        val always = History.mode(History.prefs(this)) == History.Mode.ALWAYS
        if (always && follow == null) {
            val np = NowPlaying(this) { LyricsSettings(this).rules() }   // fresh settings every pick
            val rec = PlayRecorder(this, History.Where.BACKGROUND)
            val s = MainScope()
            follow = np; recorder = rec; scope = s
            np.start()
            s.launch { combine(np.track, np.playing) { t, p -> t to p }.collect { (t, p) -> rec.onState(t, p) } }
        } else if (always) follow?.refresh()   // the chosen app or the ignore list may have changed
        else stopFollowing()
    }

    private fun stopFollowing() {
        scope?.cancel(); scope = null
        recorder?.close(); recorder = null
        follow?.stop(); follow = null
    }
}

data class Track(val title: String, val artist: String, val album: String?, val durationMs: Long?) {
    /** Stable key for caching and "Other versions" picks. Hashed before it touches disk. */
    val key get() = listOf(title, artist, album.orEmpty(), ((durationMs ?: 0) / 1000).toString()).joinToString("\u0001")
}

/**
 * What's playing, via MediaSessionManager. Follows the chosen music app (or whatever is playing), and never an app
 * on the ignore list ([rules], read fresh at every change). Holds no history: only the current track and its clock.
 */
class NowPlaying(private val context: Context, private val rules: () -> SessionPick.Rules) {
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, NowPlayingListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing
    /**
     * The cover the music app put in its media session, if any (never fetched from a URI). Separate from [track]:
     * apps often send the art a moment after the title, and that mustn't trigger a second lyrics lookup.
     */
    private val _art = MutableStateFlow<Bitmap?>(null)
    val art: StateFlow<Bitmap?> = _art

    private var controller: MediaController? = null
    private var state: PlaybackState? = null

    fun hasAccess(): Boolean {
        return context.getSystemService(android.app.NotificationManager::class.java).isNotificationListenerAccessGranted(listener)
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(s: PlaybackState?) { state = s; publish() }
        override fun onSessionDestroyed() { attach(null) }
    }

    fun start() {
        if (!hasAccess()) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            pick(msm.getActiveSessions(listener))
        } catch (_: SecurityException) { /* access was revoked between the check and the call */ }
    }

    /** The app whose session is being followed (null when nothing is). */
    val currentPackage: String? get() = controller?.packageName

    /** [pkg]'s session is the one followed, and it's playing (checked when Lyrics opens). */
    fun isPlaying(pkg: String): Boolean =
        controller?.packageName == pkg && controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    /** The choice of app or the ignore list changed: pick again. */
    fun refresh() {
        if (!hasAccess()) return
        runCatching { pick(msm.getActiveSessions(listener)) }
    }

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsChanged) }
        attach(null)
    }

    private fun pick(sessions: List<MediaController>) {
        val chosen = SessionPick.choose(
            sessions.map { SessionPick.Session(it.packageName, it.playbackState?.state == PlaybackState.STATE_PLAYING) }, rules(),
        )
        val best = chosen?.let { c -> sessions.firstOrNull { it.packageName == c.pkg } }
        if (best?.sessionToken != controller?.sessionToken) attach(best)
    }

    private fun attach(c: MediaController?) {
        controller?.unregisterCallback(callback)
        controller = c
        state = c?.playbackState
        c?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val md = controller?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        _track.value = if (title.isNullOrBlank() || artist.isNullOrBlank()) null else Track(
            title, artist,
            md.getString(MediaMetadata.METADATA_KEY_ALBUM),
            md.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
        )
        _playing.value = state?.state == PlaybackState.STATE_PLAYING
        _art.value = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    /** Current position, extrapolated from the last update so lines move smoothly between callbacks. */
    fun positionMs(): Long {
        val s = state ?: return 0
        if (s.state != PlaybackState.STATE_PLAYING) return s.position
        return s.position + ((SystemClock.elapsedRealtime() - s.lastPositionUpdateTime) * s.playbackSpeed).toLong()
    }

    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun playPause() { if (_playing.value) controller?.transportControls?.pause() else controller?.transportControls?.play() }
}
