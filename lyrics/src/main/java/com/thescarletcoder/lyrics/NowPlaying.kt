package com.thescarletcoder.lyrics

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Present only so Android grants access to media sessions. Notifications themselves are ignored. */
class NowPlayingListener : NotificationListenerService()

data class Track(val title: String, val artist: String, val album: String?, val durationMs: Long?) {
    /** Stable key for caching and "wrong lyrics" picks. Hashed before it touches disk. */
    val key get() = listOf(title, artist, album.orEmpty(), ((durationMs ?: 0) / 1000).toString()).joinToString("\u0001")
}

/**
 * What's playing, via MediaSessionManager. Prefers Spotify, otherwise whatever is playing.
 * Holds no history: only the current track and its playback clock.
 */
class NowPlaying(private val context: Context) {
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

    fun stop() {
        runCatching { msm.removeOnActiveSessionsChangedListener(sessionsChanged) }
        attach(null)
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.packageName == SPOTIFY }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
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

    companion object {
        const val SPOTIFY = "com.spotify.music"
    }
}
