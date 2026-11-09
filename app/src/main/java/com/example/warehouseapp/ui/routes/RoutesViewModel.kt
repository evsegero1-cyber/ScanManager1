package com.example.warehouseapp.ui.routes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.*
import com.example.warehouseapp.reminder.Reminders
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RoutesViewModel : ViewModel() {
    private val repo = ServiceLocator.routeRepo
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val routes: StateFlow<List<Route>?> = repo.routesFlow()
        .map { l -> l.sortedBy { it.name.lowercase() } }
        .catch { _error.value = Errors.human(it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000), null)

    val allItems: StateFlow<List<RouteItem>?> = repo.allItemsFlow()
        .catch { _error.value = Errors.human(it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(10_000), null)

    val query = MutableStateFlow("")

    fun itemsFor(routeId: String): Flow<List<RouteItem>> = allItems.map { l ->
        l.orEmpty().filter { it.routeId == routeId }
            .sortedWith(compareBy<RouteItem> { it.completed }.thenBy { it.cellNumber.lowercase() }.thenByDescending { it.addedAt })
    }

    suspend fun addItem(ctx: Context, route: Route, p: Product, qty: String, minutes: Int) {
        val item = repo.addItem(route.id, p, qty, minutes)
        if (minutes > 0) Reminders.schedule(ctx.applicationContext, item, route.name)
    }

    fun toggle(ctx: Context, item: RouteItem) = viewModelScope.launch {
        runCatching { repo.setCompleted(item.id, !item.completed) }
            .onSuccess {
                if (!item.completed) Reminders.cancel(ctx.applicationContext, item.id)
            }
            .onFailure { _error.value = Errors.human(it) }
    }

    fun delete(ctx: Context, item: RouteItem) = viewModelScope.launch {
        runCatching { repo.deleteItem(item.id) }
            .onSuccess { Reminders.cancel(ctx.applicationContext, item.id) }
            .onFailure { _error.value = Errors.human(it) }
    }

    fun clearCompleted(ctx: Context, routeId: String) = viewModelScope.launch {
        runCatching { repo.clearCompleted(routeId) }
            .onSuccess { ids -> ids.forEach { Reminders.cancel(ctx.applicationContext, it) } }
            .onFailure { _error.value = Errors.human(it) }
    }

    fun consumeError() { _error.value = null }
}
