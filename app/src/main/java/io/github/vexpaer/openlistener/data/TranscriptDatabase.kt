package io.github.vexpaer.openlistener.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions", indices = [Index("startedAt"), Index("state")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMs: Long,
    val sourceLang: String,
    val targetLang: String,
    val asrProvider: String,
    val translatorProvider: String,
    val state: String,
    val createdAt: Long,
    val audioPath: String? = null
)

@Entity(tableName = "segments",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"],
        childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["sessionId", "sequence"], unique = true)])
data class SegmentEntity(
    val sessionId: String,
    val sequence: Long,
    val startOffsetMs: Long,
    val endOffsetMs: Long?,
    val original: String,
    val translated: String?,
    val translationStatus: String,
    val revision: Int,
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Long = 0
)

@Fts4(contentEntity = SegmentEntity::class)
@Entity(tableName = "segments_fts")
data class SegmentSearch(val original: String, val translated: String?)

data class SessionPreview(@Embedded val session: SessionEntity, val preview: String)

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsert(session: SessionEntity)
    @Update suspend fun update(session: SessionEntity)
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun get(id: String): SessionEntity?
    @Query("SELECT * FROM sessions WHERE title LIKE '%' || :query || '%' OR id IN (SELECT sessionId FROM segments WHERE original LIKE '%' || :query || '%' OR translated LIKE '%' || :query || '%') ORDER BY startedAt DESC")
    fun search(query: String): Flow<List<SessionEntity>>
    @Query("""SELECT sessions.*, COALESCE((SELECT original FROM segments
        WHERE sessionId = sessions.id AND trim(original) != '' ORDER BY sequence ASC LIMIT 1), '') AS preview
        FROM sessions WHERE :query = '' OR title LIKE '%' || :query || '%'
        OR id IN (SELECT sessionId FROM segments WHERE original LIKE '%' || :query || '%'
        OR translated LIKE '%' || :query || '%') ORDER BY startedAt DESC""")
    fun previews(query: String): Flow<List<SessionPreview>>
    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: String, title: String)
    @Query("UPDATE sessions SET audioPath = :path WHERE id = :id")
    suspend fun setAudioPath(id: String, path: String?)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE sessions SET state = 'INTERRUPTED', endedAt = :now WHERE state IN ('PREPARING','RECORDING','PAUSED','STOPPING')")
    suspend fun markOrphanedAsInterrupted(now: Long)
}

@Dao
abstract class SegmentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) abstract suspend fun insert(segment: SegmentEntity)
    @Update abstract suspend fun update(segment: SegmentEntity)
    @Query("SELECT * FROM segments WHERE sessionId = :sessionId AND sequence = :sequence LIMIT 1")
    abstract suspend fun get(sessionId: String, sequence: Long): SegmentEntity?
    @Transaction open suspend fun putFinal(segment: SegmentEntity): Boolean {
        val existing = get(segment.sessionId, segment.sequence)
        if (existing == null) { insert(segment); return true }
        if (segment.revision < existing.revision) return false
        if (segment.original == existing.original) {
            update(existing.copy(startOffsetMs = segment.startOffsetMs, endOffsetMs = segment.endOffsetMs))
            return false
        }
        update(segment.copy(rowId = existing.rowId))
        return true
    }
    @Transaction open suspend fun putBilingualFinal(segment: SegmentEntity) {
        val existing = get(segment.sessionId, segment.sequence)
        if (existing == null) insert(segment)
        else if (segment.revision >= existing.revision) update(segment.copy(rowId = existing.rowId))
    }
    @Query("SELECT * FROM segments WHERE sessionId = :sessionId AND translationStatus != 'COMPLETED'")
    abstract suspend fun incompleteForSession(sessionId: String): List<SegmentEntity>
    @Transaction open suspend fun markIncomplete(sessionId: String) {
        incompleteForSession(sessionId).forEach { update(it.copy(translationStatus = "INCOMPLETE")) }
    }
    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY sequence ASC")
    abstract fun observeForSession(sessionId: String): Flow<List<SegmentEntity>>
    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY sequence ASC")
    abstract suspend fun listForSession(sessionId: String): List<SegmentEntity>
    @Query("UPDATE segments SET translated = :translated, translationStatus = :status WHERE sessionId = :sessionId AND sequence = :sequence AND (:revision IS NULL OR revision = :revision)")
    abstract suspend fun updateTranslation(sessionId: String, sequence: Long, translated: String?, status: String, revision: Int? = null)
    @Query("DELETE FROM segments WHERE sessionId = :sessionId") abstract suspend fun deleteForSession(sessionId: String)
}

@Database(entities = [SessionEntity::class, SegmentEntity::class, SegmentSearch::class], version = 2, exportSchema = true)
abstract class TranscriptDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun segmentDao(): SegmentDao
    companion object {
        fun create(context: Context, name: String = "open_listener.db"): TranscriptDatabase = Room.databaseBuilder(
            context, TranscriptDatabase::class.java, name)
            .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE sessions ADD COLUMN audioPath TEXT DEFAULT NULL")
                }
            }).build()
    }
}
