package com.example.jarvis.jarvis.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class MemorySummaryDao {
    @Transaction
    open suspend fun insertOrReplace(summary: MemorySummary) {
        insertOrReplaceInternal(summary)
        pruneOldestBeyond(MAX_ROWS)
    }

    @Query(
        """
        SELECT * FROM (
            SELECT * FROM memory_summaries
            ORDER BY timestamp DESC, id DESC
            LIMIT 7
        )
        ORDER BY timestamp ASC, id ASC
        """
    )
    abstract suspend fun getLastSevenOldestFirst(): List<MemorySummary>

    @Query("DELETE FROM memory_summaries")
    abstract suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertOrReplaceInternal(summary: MemorySummary)

    @Query(
        """
        DELETE FROM memory_summaries
        WHERE id NOT IN (
            SELECT id FROM memory_summaries
            ORDER BY timestamp DESC, id DESC
            LIMIT :maxRows
        )
        """
    )
    protected abstract suspend fun pruneOldestBeyond(maxRows: Int)

    companion object {
        const val MAX_ROWS = 90
    }
}
