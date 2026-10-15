package com.thescarletcoder.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which apps the music app lists show, and where (0.5.1, owner Q51 and Q60). Made-up package names. */
class MediaAppsTest {
    private fun app(pkg: String) = MediaApps.App(pkg, pkg.substringAfterLast('.'))
    private val player = app("com.example.player")
    private val podcasts = app("com.example.podcasts")
    private val reader = app("com.example.reader")
    private val service = app("com.example.service")   // a media service with no home-screen icon, like Bluetooth's
    private val google = app("com.google.android.googlequicksearchbox")
    private val all = listOf(player, podcasts, reader, service, google)
    private val icons = setOf(player.pkg, podcasts.pkg, reader.pkg, google.pkg)

    @Test fun playingAppsComeFirstTheRestAreFolded() {
        val l = MediaApps.lists(all, playing = setOf(player.pkg), set = emptySet(), launchable = icons)
        assertEquals(listOf(player), l.top)
        assertEquals(listOf(podcasts, reader), l.more)
    }

    @Test fun noIconAndTheGoogleAppAreNeverListed() {
        val l = MediaApps.lists(all, playing = setOf(service.pkg, google.pkg), set = emptySet(), launchable = icons)
        assertEquals(emptyList<MediaApps.App>(), l.top)
        assertEquals(listOf(player, podcasts, reader), l.more)
    }

    @Test fun anAppTheUserSetAlwaysStaysInView() {
        // Chosen or ignored before 0.5.1: listed first even with no icon or nothing playing, so it can be changed back.
        val l = MediaApps.lists(all, playing = emptySet(), set = setOf(service.pkg, reader.pkg), launchable = icons)
        assertEquals(listOf(reader, service), l.top)
        assertEquals(listOf(player, podcasts), l.more)
    }

    @Test fun theGoogleAppSetBeforeStaysToo() {
        val l = MediaApps.lists(all, playing = emptySet(), set = setOf(google.pkg), launchable = icons)
        assertEquals(listOf(google), l.top)
    }

    @Test fun aPlayingAppWithNoIconIsInNeitherGroup() {
        val l = MediaApps.lists(all, playing = setOf(service.pkg), set = emptySet(), launchable = icons)
        assertEquals(false, service in l.top || service in l.more)
    }

    @Test fun theFirstGroupKeepsItsOrder() {
        val l = MediaApps.lists(all, playing = setOf(reader.pkg), set = setOf(player.pkg), launchable = icons)
        assertEquals(listOf(player, reader), l.top)
    }

    @Test fun onlyAppsWithAMediaServiceAreEverListed() {
        val l = MediaApps.lists(all, playing = setOf("com.example.other"), set = emptySet(), launchable = icons + "com.example.other")
        assertEquals(emptyList<MediaApps.App>(), l.top)
        assertEquals(listOf(player, podcasts, reader), l.more)
    }
}
