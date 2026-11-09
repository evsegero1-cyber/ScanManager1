package com.example.warehouseapp.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.*
import kotlinx.coroutines.flow.*

/** Общий для всего приложения кэш каталога (живые данные из Firestore). */
class CatalogViewModel : ViewModel() {
    private val repo = ServiceLocator.catalogRepo

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private fun <T> Flow<List<T>>.state(sort: (List<T>) -> List<T>): StateFlow<List<T>?> =
        map { sort(it) }
            .catch { _error.value = Errors.human(it); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000), null)

    private val ru = compareBy<String> { it.lowercase() }

    val categories = repo.categoriesFlow().state { l -> l.sortedWith(compareBy(ru) { it.name }) }
    val brands = repo.brandsFlow().state { l -> l.sortedWith(compareBy(ru) { it.name }) }
    val subcategories = repo.subcategoriesFlow().state { l -> l.sortedWith(compareBy(ru) { it.name }) }
    val products = repo.productsFlow().state { l -> l.sortedWith(compareBy(ru) { it.name }) }

    val query = MutableStateFlow("")

    val searchResults: StateFlow<List<Product>> = combine(products, query) { p, q ->
        if (q.isBlank() || p == null) emptyList() else Search.filter(p, q).take(200)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun productCount(subId: String): Int = products.value?.count { it.subcategoryId == subId } ?: 0
}
