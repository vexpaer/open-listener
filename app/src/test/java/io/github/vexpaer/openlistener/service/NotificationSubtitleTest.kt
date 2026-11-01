package io.github.vexpaer.openlistener.service

import io.github.vexpaer.openlistener.data.AppPreferences
import org.junit.Assert.*
import org.junit.Test

class NotificationSubtitleTest {
    @Test fun defaultsKeepPrivateRecordingAndVisualOptionsOptIn() {
        assertEquals("original", AppPreferences().notificationSubtitle)
        assertFalse(AppPreferences().solidBackground)
    }

    @Test fun choosesOriginalOrTranslationFromLiveAndConfirmedTexts() {
        assertEquals("new words", NotificationSubtitle.select("original",
            "new words", "新词", "older words", "旧词"))
        assertEquals("新词", NotificationSubtitle.select("translation",
            "new words", "新词", "older words", "旧词"))
        assertEquals("旧词", NotificationSubtitle.select("translation",
            "new words", null, "older words", "旧词"))
        assertEquals("older words", NotificationSubtitle.select("original",
            "", "", "older words", "旧词"))
    }

    @Test fun volumeClampsAndStopsWhenPaused() {
        assertEquals(0, NotificationSubtitle.volume(0f, true))
        assertEquals(0, NotificationSubtitle.volume(.25f, false))
        assertEquals(100, NotificationSubtitle.volume(1f, true))
        assertEquals(0, NotificationSubtitle.volume(-3f, true))
        assertTrue(NotificationSubtitle.volume(.01f, true) in 1..100)
    }
}
