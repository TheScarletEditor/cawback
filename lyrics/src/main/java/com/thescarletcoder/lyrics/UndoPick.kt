package com.thescarletcoder.lyrics

/** "Undo my pick" in This song (owner, 0.5.1 device tests; wording 2026-11-06). Pure, so it is unit tested. */
object UndoPick {
    /** Only for a version picked by hand: an automatic match has nothing to undo. */
    fun offered(pinned: Boolean): Boolean = pinned

    /** Undoing drops the timing changes made to that version, so with any, it asks first. */
    fun asksFirst(offsetMs: Long, anchors: Int, skips: Int): Boolean = offsetMs != 0L || anchors > 0 || skips > 0
}
