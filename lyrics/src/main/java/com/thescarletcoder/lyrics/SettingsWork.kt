package com.thescarletcoder.lyrics

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Settings work that must finish once started (backup, restore, deletes, clearing saved lyrics, resetting nudges).
 * It runs on a scope that outlives the screen, so switching tabs or leaving Settings never stops it halfway
 * (0.4.1, Q6). Each job has a key; while it runs, the same key can't start again (one action per tap). Its message
 * stays until Settings is closed after it finished, so leaving mid-run still shows the result on the way back.
 */
internal class WorkRunner(private val scope: CoroutineScope, private val onChange: () -> Unit = {}) {
    private val busy = HashSet<String>()
    private val results = HashMap<String, String>()

    @Synchronized fun busy(key: String) = key in busy
    @Synchronized fun anyBusy(keys: Collection<String>) = keys.any { it in busy }
    @Synchronized fun result(key: String): String? = results[key]

    /**
     * Start [block] under [key] unless it, or anything in [exclusive], is already running. Its returned text becomes
     * the result. False if refused.
     */
    fun run(key: String, exclusive: Collection<String> = emptyList(), block: suspend () -> String): Boolean {
        synchronized(this) {
            if (key in busy || exclusive.any { it in busy }) return false
            busy += key; results -= key
        }
        onChange()
        scope.launch {
            var message = FAILED
            try { message = block() } catch (e: CancellationException) { throw e } catch (_: Exception) { } finally {
                synchronized(this@WorkRunner) { busy -= key; results[key] = message }
                onChange()
            }
        }
        return true
    }

    @Synchronized fun clear(key: String) { results -= key }

    /** Settings closed: forget the results of finished work. Work still running keeps going, and its result shows next time. */
    fun forgetFinished() {
        synchronized(this) { results.clear() }
        onChange()
    }

    companion object {
        const val FAILED = "That didn't finish. Try again."
    }
}

/** The app's one runner. [version] changes whenever a job starts or ends, so Settings redraws. */
internal object SettingsWork {
    val version = mutableIntStateOf(0)

    /**
     * Bumped when saved lyrics or nudges change under the lyrics screen, which then loads the song again. App-level, so
     * it reaches the screen showing now even if the one that started the work was recreated (rotation, font size).
     */
    val lyricsChanges = mutableIntStateOf(0)
    fun lyricsChanged() { lyricsChanges.intValue++ }
    val runner by lazy { WorkRunner(MainScope()) { version.intValue++ } }

    const val NUDGES = "nudges"
    const val DELETE = "delete"
    const val SAVED = "saved"
    const val BACKUP = "backup"
    const val RESTORE = "restore"

    /** Work on the Library itself: one at a time, so a delete and a restore never race (owner: "One at a time"). */
    val LIBRARY = listOf(DELETE, BACKUP, RESTORE)
    fun libraryBusy() = runner.anyBusy(LIBRARY)
}
