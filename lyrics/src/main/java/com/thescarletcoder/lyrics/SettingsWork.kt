package com.thescarletcoder.lyrics

import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Settings work that must finish once started (backup, restore, deletes, clearing saved lyrics, resetting nudges).
 * It runs on a scope that outlives the screen, so switching tabs or leaving Settings never stops it halfway
 * (0.4.1, Q6). Each job has a key and a group: while anything in its group runs, it can't start (one action per tap,
 * and one Library job at a time), and starting it clears the group's older messages, which would be out of date.
 * Its result stays until Settings is closed after it finished, so leaving mid-run still shows it on the way back.
 */
internal class WorkRunner(
    private val scope: CoroutineScope,
    /** Called on every change; [finished] is true when a job has just ended (data may have changed). */
    private val onChange: (finished: Boolean) -> Unit = {},
) {
    /** What a job ended with. [ok] false keeps the button there, so it can be tried again. */
    data class Result(val ok: Boolean, val text: String)

    private val busy = HashSet<String>()
    private val results = HashMap<String, Result>()
    private val jobs = HashMap<String, kotlinx.coroutines.Job>()
    private val settled = HashMap<String, Result>()

    @Synchronized fun busy(key: String) = key in busy
    @Synchronized fun anyBusy(keys: Collection<String>) = keys.any { it in busy }
    @Synchronized fun result(key: String): Result? = results[key]

    /** Start [block] under [key] unless anything in its [group] is running. False if refused. */
    fun run(key: String, group: Collection<String> = listOf(key), block: suspend () -> Result): Boolean {
        synchronized(this) {
            if (key in busy || group.any { it in busy }) return false
            busy += key
            settled -= key
            results -= key; group.forEach { results -= it }
        }
        onChange(false)
        val job = scope.launch {
            var result: Result? = null
            var failed = false
            try { result = block() } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true } finally {
                synchronized(this@WorkRunner) {
                    busy -= key; jobs -= key
                    // A settled result wins over a later failure or Cancel; otherwise a Cancel by the user leaves no
                    // message, as if it never started.
                    (result ?: settled[key] ?: if (failed) Result(false, FAILED) else null)?.let { results[key] = it }
                    settled -= key
                }
                onChange(true)
            }
        }
        synchronized(this) { if (key in busy) jobs[key] = job }
        return true
    }

    /**
     * The job's result is now certain (the work can't be undone): it shows even if Cancel or an error comes before the
     * job ends.
     * Called from inside the job.
     */
    @Synchronized fun settle(key: String, result: Result) { if (key in busy) settled[key] = result }

    /** Stop the job under [key] (Share's Cancel); work that can't be undone halfway must not use this. */
    fun cancel(key: String) { synchronized(this) { jobs[key] }?.cancel() }

    @Synchronized fun clear(key: String) { results -= key }

    /** Settings closed: forget the results of finished work. Work still running keeps going, and its result shows next time. */
    fun forgetFinished() {
        val had = synchronized(this) { results.isNotEmpty().also { results.clear() } }
        if (had) onChange(false)
    }

    companion object {
        const val FAILED = "That didn't finish. Try again."
    }
}

/** The app's one runner. */
internal object SettingsWork {
    /** Changes whenever a job starts or ends or results are forgotten, so Settings redraws. */
    val version = mutableIntStateOf(0)

    /** Changes only when a job ends: what counts or checks what's stored (saved lyrics, the Library) reads it again. */
    val finished = mutableIntStateOf(0)

    /**
     * Bumped when saved lyrics or nudges change under the lyrics screen, which then loads the song again. App-level, so
     * it reaches the screen showing now even if the one that started the work was recreated (rotation, font size).
     */
    val lyricsChanges = mutableIntStateOf(0)
    fun lyricsChanged() { lyricsChanges.intValue++ }
    val runner by lazy { WorkRunner(MainScope()) { done -> version.intValue++; if (done) finished.intValue++ } }

    const val NUDGES = "nudges"
    const val DELETE = "delete"
    const val SAVED = "saved"
    const val BACKUP = "backup"
    const val RESTORE = "restore"
    const val SHARE = "share"

    /** The song (track key) the share job and its result belong to, so another song's page never shows them. */
    @Volatile var sharing: String? = null

    /** Work on the Library itself: one at a time, so a delete and a restore never race (owner: "One at a time"). */
    val LIBRARY = listOf(DELETE, BACKUP, RESTORE)
    fun libraryBusy() = runner.anyBusy(LIBRARY)
    fun clearLibraryResults() = LIBRARY.forEach(runner::clear)
}
