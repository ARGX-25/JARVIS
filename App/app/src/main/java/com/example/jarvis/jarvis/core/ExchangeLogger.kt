package com.example.jarvis.jarvis.core

import com.example.jarvis.jarvis.models.Exchange

interface ExchangeLogger {
    fun log(exchange: Exchange)
}
