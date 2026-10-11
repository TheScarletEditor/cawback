package com.thescarletcoder.lyrics

/** Settings in four tabs (0.4.1, Q1). Plain data, so the grouping and the way in are tested on the JVM. */
internal enum class SettingsTab(val label: String) {
    LYRICS("Lyrics"), PLAYER("Player"), LIBRARY("Library"), PRIVACY("Privacy");

    /** The sections on this tab, top to bottom. */
    val sections: List<SettingsSection> get() = SettingsSection.entries.filter { it.tab == this }
}

/** Every Settings section and the one tab it lives on. [personal]: only in personal builds (Tuning). */
internal enum class SettingsSection(val title: String, val tab: SettingsTab, val personal: Boolean = false) {
    DISPLAY("Display", SettingsTab.LYRICS),   // owner: "Rename it \"Display\"" (not "Lyrics" twice in a row)
    TIMING("Timing", SettingsTab.LYRICS),
    THEME("Theme", SettingsTab.LYRICS),
    MUSIC_APP("Your music app", SettingsTab.PLAYER),
    ACCESS("Notification access", SettingsTab.PLAYER),
    HISTORY("Listening history", SettingsTab.LIBRARY),
    BACKUP("Library backup", SettingsTab.LIBRARY),
    SAVED("Saved lyrics", SettingsTab.PRIVACY),
    ON_DEVICE("What's on this device", SettingsTab.PRIVACY),
    ABOUT("About", SettingsTab.PRIVACY),
    TUNING("Tuning", SettingsTab.PRIVACY, personal = true),
}

/**
 * How many tabs go on a row: all of them when their natural widths fit, else two (0.4.1, "Two rows when narrow"),
 * else one when a label is wider than half the row (a very large font), so no word is broken.
 */
internal object TabFit {
    fun perRow(widths: List<Int>, available: Int): Int = when {
        widths.sum() <= available -> widths.size
        widths.all { it <= available / 2 } -> 2
        else -> 1
    }
}

/** Where Settings was opened from, and the tab that fits it (0.4.1, Q5). */
internal enum class SettingsWayIn(val tab: SettingsTab) {
    MORE_SHEET(SettingsTab.LYRICS),
    CHOOSE_MUSIC_APP(SettingsTab.PLAYER),
    LIBRARY(SettingsTab.LIBRARY),
}
