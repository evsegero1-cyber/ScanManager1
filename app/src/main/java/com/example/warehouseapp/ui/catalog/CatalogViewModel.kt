package com.example.warehouseapp.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Каталог: скачивается с сервера не чаще раза в сутки (или по кнопке «Обновить»).
 * Остальное время — из памяти телефона, чтобы не тратить лимит бесплатного Firebase.
 */
class CatalogViewModel : ViewModel() {
    private val repo = ServiceLocator.catalogRepo
    private val ru = compareBy<String> { it.lowercase() }

    val categories = repo.categoriesCache.items.sorted { it.name }
    val brands = repo.brandsCache.items.sorted { it.name }
    val subcategories = repo.subcategoriesCache.items.sorted { it.name }
    val products = repo.productsCache.items.sorted { it.name }

    private fun <T> StateFlow<List<T>?>.sorted(by: (T) -> String): StateFlow<List<T>?> =
        map { l -> l?.sortedWith(compareBy(ru, by)) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val error: StateFlow<String?> = repo.productsCache.error.map { it?.let(Errors::human) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    val lastSync: Long get() = repo.lastSync

    val query = MutableStateFlow("")

    val searchResults: StateFlow<List<Product>> = combine(products, query) { p, q ->
        if (q.isBlank() || p == null) emptyList() else Search.filter(p, q).take(200)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repo.loadAll(force = false) } }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch { _refreshing.value = true; repo.loadAll(force = true); _refreshing.value = false }
    }

    /** После правок в админке — бесплатно перечитываем из кэша (там уже наши изменения). */
    fun afterLocalChange() { viewModelScope.launch { repo.reloadFromCache() } }
}
