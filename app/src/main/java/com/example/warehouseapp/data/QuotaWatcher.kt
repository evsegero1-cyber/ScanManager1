package com.example.warehouseapp.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Firestore при исчерпании квоты не возвращает ошибку в слушатели — он молча уходит «офлайн».
 * Поэтому отслеживаем это по журналу Firestore своего процесса и показываем понятную плашку.
 */
object QuotaWatcher {
    private val _exhausted = MutableStateFlow(false)
    val exhausted: StateFlow<Boolean> = _exhausted

    fun check() {
        val found = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "200", "Firestore:W", "*:S"))
            p.inputStream.bufferedReader().readLines().takeLast(30).any { "RESOURCE_EXHAUSTED" in it }
        }.getOrDefault(false)
        _exhausted.value = found
    }
}
