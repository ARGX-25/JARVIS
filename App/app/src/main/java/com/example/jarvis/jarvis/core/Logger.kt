package com.example.jarvis.jarvis.core

import android.content.Context
import com.example.jarvis.jarvis.models.Exchange

class Logger(@Suppress("UNUSED_PARAMETER") context: Context) : ExchangeLogger {

    override fun log(exchange: Exchange) {
        // Phase 1 stores compressed summaries only. Existing Phase 0 JSONL is
        // imported by MemoryManager at startup and then removed.
    }
}
