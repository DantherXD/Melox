package com.melox.player.ui.component.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSelectionTest {
    @Test
    fun toggleSelectionAddsAndRemovesOneStableKey() {
        assertEquals(setOf("a", "b"), toggleTrackSelection(setOf("a"), "b"))
        assertEquals(emptySet<String>(), toggleTrackSelection(setOf("a"), "a"))
    }

    @Test
    fun toggleAllUsesOnlyTheDisplayedCollection() {
        assertEquals(
            setOf("a", "b"),
            toggleAllTrackSelection(setOf("outside"), listOf("a", "b")),
        )
        assertEquals(
            emptySet<String>(),
            toggleAllTrackSelection(setOf("a", "b", "outside"), listOf("a", "b")),
        )
    }

    @Test
    fun selectedItemsRetainDisplayedOrder() {
        assertEquals(
            listOf("third", "first"),
            selectedItemsInDisplayedOrder(
                items = listOf("third", "second", "first"),
                selectedKeys = setOf("first", "third"),
                keyOf = { it },
            ),
        )
    }
}
