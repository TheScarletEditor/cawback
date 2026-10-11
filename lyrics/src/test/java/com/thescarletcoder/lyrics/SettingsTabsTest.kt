package com.thescarletcoder.lyrics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings in four tabs (0.4.1, Q1 and Q5) and work that finishes once started (Q6). */
class SettingsTabsTest {
    @Test fun theFourTabsHoldWhatWasDecided() {
        assertEquals(listOf("Lyrics", "Player", "Library", "Privacy"), SettingsTab.entries.map { it.label })
        assertEquals(listOf("Display", "Timing", "Theme"), SettingsTab.LYRICS.sections.map { it.title })
        assertEquals(listOf("Your music app", "Notification access"), SettingsTab.PLAYER.sections.map { it.title })
        assertEquals(listOf("Listening history", "Library backup"), SettingsTab.LIBRARY.sections.map { it.title })
        assertEquals(listOf("Saved lyrics", "What's on this device", "About", "Tuning"), SettingsTab.PRIVACY.sections.map { it.title })
    }

    @Test fun everySectionIsOnExactlyOneTab() {
        val placed = SettingsTab.entries.flatMap { it.sections }
        assertEquals(SettingsSection.entries.toSet(), placed.toSet())
        assertEquals(placed.size, placed.toSet().size)
        assertEquals(listOf(SettingsSection.TUNING), SettingsSection.entries.filter { it.personal })   // only Tuning hides in public builds
    }

    @Test fun tabsWrapIntoTwoRowsWhenTheyDontFit() {
        val widths = listOf(130, 140, 150, 160)   // 580 px of tabs
        assertEquals(4, TabFit.perRow(widths, 624))   // a phone: one row
        assertEquals(4, TabFit.perRow(widths, 580))   // exactly fits
        assertEquals(2, TabFit.perRow(widths, 579))   // a narrow split or a large font: two rows of two
        assertEquals(2, TabFit.perRow(widths, 320))   // still two: every label fits half a row (160)
        assertEquals(1, TabFit.perRow(widths, 319))   // "Privacy" (160) wouldn't fit half: one per row, never "Privac / y"
    }

    @Test fun settingsOpensOnTheTabForTheWayIn() {
        assertEquals(SettingsTab.LYRICS, SettingsWayIn.MORE_SHEET.tab)
        assertEquals(SettingsTab.PLAYER, SettingsWayIn.CHOOSE_MUSIC_APP.tab)
        assertEquals(SettingsTab.LIBRARY, SettingsWayIn.LIBRARY.tab)
    }

    @Test fun workFinishesWhenTheScreenGoesAway() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = WorkRunner(app)
        val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val steps = mutableListOf<String>()
        // Like "Delete the Library": the database part, then (after a pause) the saved lyrics.
        screen.launch {
            runner.run("delete") { steps += "database"; gate.await(); steps += "saved lyrics"; "Library deleted." }
        }.join()
        screen.cancel()   // the user switched tabs or left Settings mid-run
        gate.complete(Unit)
        withTimeout(5_000) { while (runner.busy("delete")) kotlinx.coroutines.delay(5) }
        assertEquals(listOf("database", "saved lyrics"), steps)
        assertEquals("Library deleted.", runner.result("delete"))
        app.cancel()
    }

    @Test fun libraryJobsRunOneAtATime() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val runner = WorkRunner(app)
        val gate = CompletableDeferred<Unit>()
        val library = listOf("delete", "backup", "restore")
        assertTrue(runner.run("restore", exclusive = library) { gate.await(); "Restored." })
        assertFalse(runner.run("delete", exclusive = library) { "Library deleted." })   // refused while the restore runs
        assertTrue(runner.anyBusy(library))
        gate.complete(Unit)
        withTimeout(5_000) { while (runner.anyBusy(library)) kotlinx.coroutines.delay(5) }
        assertNull(runner.result("delete"))
        assertTrue(runner.run("delete", exclusive = library) { "Library deleted." })   // free again afterwards
        app.cancel()
    }

    @Test fun oneRunAtATimeAndAFailureIsNeverSuccess() = runBlocking {
        val app = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var changes = 0
        val runner = WorkRunner(app) { changes++ }
        val gate = CompletableDeferred<Unit>()
        assertTrue(runner.run("saved") { gate.await(); "Cleared." })
        assertFalse(runner.run("saved") { "Cleared." })   // a quick second tap
        gate.complete(Unit)
        withTimeout(5_000) { while (runner.busy("saved")) kotlinx.coroutines.delay(5) }
        assertEquals("Cleared.", runner.result("saved"))
        assertTrue(changes >= 2)

        assertTrue(runner.run("nudges") { throw IllegalStateException("disk full") })
        withTimeout(5_000) { while (runner.busy("nudges")) kotlinx.coroutines.delay(5) }
        assertEquals(WorkRunner.FAILED, runner.result("nudges"))

        runner.forgetFinished()   // Settings closed
        assertNull(runner.result("saved")); assertNull(runner.result("nudges"))
        app.cancel()
    }
}
