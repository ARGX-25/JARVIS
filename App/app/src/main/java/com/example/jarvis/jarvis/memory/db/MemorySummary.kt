package com.example.jarvis.jarvis.memory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_summaries",
    indices = [Index(value = ["sessionId"], unique = true)]
)
data class MemorySummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val timestamp: String,
    val summary: String,
    val tokenEstimate: Int,
    val generatedBy: String
)
