package com.example.warehouseapp.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Коллекция, которая экономит лимит Firebase: с сервера скачивается не чаще чем раз в [maxAgeMs]
 * (или по кнопке «Обновить»), всё остальное время данные берутся из памяти телефона — это бесплатно.
 */
class CachedCollection<T>(
    private val key: String,
    private val query: () -> Query,
    private val map: (DocumentSnapshot) -> T,
    private val maxAgeMs: Long,
) {
    private val _items = MutableStateFlow<List<T>?>(null)
    val items: StateFlow<List<T>?> = _items
    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error
    private val mutex = Mutex()

    private val prefs get() = com.example.warehouseapp.ServiceLocator.appContext
        .getSharedPreferences("sync", Context.MODE_PRIVATE)

    val lastSync: Long get() = prefs.getLong(key, 0L)

    /** force=false: из кэша, если он свежий; force=true: всегда с сервера. */
    suspend fun load(force: Boolean = false) = mutex.withLock {
        val fresh = System.currentTimeMillis() - lastSync < maxAgeMs
        if (!force && fresh) {
            val cached = runCatching { query().get(Source.CACHE).await() }.getOrNull()
            if (cached != null && (!cached.isEmpty || _items.value == null)) {
                _items.value = cached.documents.map(map)
                if (!cached.isEmpty) return@withLock
            }
        }
        try {
            val snap = query().get(Source.SERVER).await()
            _items.value = snap.documents.map(map)
            _error.value = null
            prefs.edit().putLong(key, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            _error.value = e
            // Сервер недоступен (нет сети / исчерпан лимит) — показываем, что есть в памяти телефона
            val cached = runCatching { query().get(Source.CACHE).await() }.getOrNull()
            if (cached != null && !cached.isEmpty) _items.value = cached.documents.map(map)
            else if (_items.value == null) _items.value = emptyList()
        }
    }

    /** После собственных изменений (админка) — перечитать из кэша: там уже есть наши записи, чтение бесплатно. */
    suspend fun reloadFromCache() = mutex.withLock {
        runCatching { query().get(Source.CACHE).await() }.getOrNull()?.let { _items.value = it.documents.map(map) }
    }
}
