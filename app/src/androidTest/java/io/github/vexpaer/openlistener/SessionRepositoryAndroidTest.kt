package io.github.vexpaer.openlistener

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.vexpaer.openlistener.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Real in-memory Room, not a production fake database. */
class SessionRepositoryAndroidTest {
    @Test fun upgradeFromVersionOneKeepsExistingTranscript() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = context.getDatabasePath("migration-v1-open_listener.db")
        context.deleteDatabase(file.name)
        file.parentFile!!.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, durationMs INTEGER NOT NULL, sourceLang TEXT NOT NULL, targetLang TEXT NOT NULL, asrProvider TEXT NOT NULL, translatorProvider TEXT NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_sessions_startedAt ON sessions(startedAt)")
            old.execSQL("CREATE INDEX index_sessions_state ON sessions(state)")
            old.execSQL("CREATE TABLE segments (sessionId TEXT NOT NULL, sequence INTEGER NOT NULL, startOffsetMs INTEGER NOT NULL, endOffsetMs INTEGER, original TEXT NOT NULL, translated TEXT, translationStatus TEXT NOT NULL, revision INTEGER NOT NULL, rowid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE UNIQUE INDEX index_segments_sessionId_sequence ON segments(sessionId, sequence)")
            old.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `segments_fts` USING FTS4(`original` TEXT, `translated` TEXT, content=`segments`)")
            old.execSQL("INSERT INTO sessions VALUES ('legacy', 'Old lecture', 100, 200, 100, 'en', 'zh-CN', 'local', 'mlkit', 'FINISHED', 100)")
            old.execSQL("INSERT INTO segments VALUES ('legacy', 1, 0, 100, 'Keep this text', '保留文字', 'COMPLETED', 0, 1)")
            old.version = 1
        }
        val database = TranscriptDatabase.create(context, file.name)
        try {
            val repository = SessionRepository(database.sessionDao(), database.segmentDao())
            assertEquals("Old lecture", repository.getSession("legacy")!!.title)
            assertNull(repository.getSession("legacy")!!.audioPath)
            assertEquals("Keep this text", repository.listSegments("legacy").single().original)
        } finally { database.close(); context.deleteDatabase(file.name) }
    }

    @Test fun revisionsTranslationSearchInterruptionAndCascadeDeletion() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TranscriptDatabase::class.java).build()
        try {
            val repository = SessionRepository(database.sessionDao(), database.segmentDao())
            val id = repository.startSession("课堂", "en", "zh-CN", "local", "mlkit")
            assertEquals("", repository.previews("").first().single().preview)
            repository.saveFinalSegment(id, 2, 1000, 2000, "second", 0)
            repository.saveFinalSegment(id, 1, 0, 1000, "draft", 0)
            repository.saveFinalSegment(id, 1, 0, 1100, "corrected", 1)
            repository.updateTranslation(id, 1, "过期译文", "COMPLETED", 0)
            assertNull(repository.listSegments(id).first().translated)
            repository.updateTranslation(id, 1, "确认译文", "COMPLETED", 1)
            assertEquals(listOf(1L, 2L), repository.listSegments(id).map { it.sequence })
            assertEquals("确认译文", repository.listSegments(id).first().translated)
            assertEquals(id, repository.search("确认").first().single().id)
            val preview = repository.previews("确认").first().single()
            assertEquals(id, preview.session.id)
            assertEquals("corrected", preview.preview)
            repository.saveFinalSegment(id, 1, 0, 1100, "updated preview", 2)
            assertEquals("updated preview", repository.previews("").first().single().preview)
            repository.renameSession(id, "New title")
            assertEquals("New title", repository.getSession(id)!!.title)
            repository.markInterruptedSessions()
            assertEquals("INTERRUPTED", repository.getSession(id)!!.state)
            repository.deleteSession(id)
            assertTrue(repository.listSegments(id).isEmpty())
        } finally { database.close() }
    }
}
