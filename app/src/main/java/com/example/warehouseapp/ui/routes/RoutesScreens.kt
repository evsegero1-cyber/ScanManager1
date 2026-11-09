@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.routes

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.domain.RouteItem
import com.example.warehouseapp.domain.Search
import com.example.warehouseapp.ui.catalog.ListPage
import com.example.warehouseapp.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoutesHomeScreen(vm: RoutesViewModel, onRoute: (String) -> Unit) {
    val routes by vm.routes.collectAsStateWithLifecycle()
    val items by vm.allItems.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val list = routes?.filter { Search.matchesRoute(it, query) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Text("Маршруты", style = MaterialTheme.typography.headlineMedium)
            val open = items?.count { !it.completed }
            if (open != null) Text("Осталось подпитать: $open", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SearchField(query, { vm.query.value = it }, "Поиск маршрута")
        }
        when {
            list == null -> LoadingBox()
            routes!!.isEmpty() -> EmptyState(Icons.Rounded.Route, "Маршрутов пока нет", error ?: "Администратор создаёт маршруты в админ-панели")
            list.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { r ->
                    val ri = items.orEmpty().filter { it.routeId == r.id }
                    val done = ri.count { it.completed }
                    Card(
                        onClick = { onRoute(r.id) }, shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Icon(Icons.Rounded.Route, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (ri.isEmpty()) "Пусто" else "${ri.size - done} осталось · $done готово",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (ri.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(progress = { done / ri.size.toFloat() }, modifier = Modifier.fillMaxWidth(), drawStopIndicator = {})
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteDetailScreen(vm: RoutesViewModel, routeId: String, onBack: () -> Unit, onProduct: (String) -> Unit) {
    val ctx = LocalContext.current
    val routes by vm.routes.collectAsStateWithLifecycle()
    val items by remember(routeId) { vm.itemsFor(routeId) }.collectAsStateWithLifecycle(null)
    val all by vm.allItems.collectAsStateWithLifecycle()
    val route = routes?.firstOrNull { it.id == routeId }
    var confirmClear by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<RouteItem?>(null) }
    val done = items?.count { it.completed } ?: 0

    ListPage(
        route?.name ?: "Маршрут",
        items?.let { "${it.size - done} осталось · $done готово" },
        onBack,
        actions = {
            if (done > 0) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.CleaningServices, "Убрать выполненные") }
        },
    ) { pad ->
        when {
            all == null -> LoadingBox(Modifier.padding(pad))
            items.isNullOrEmpty() -> EmptyState(
                Icons.Rounded.PlaylistAdd, "В маршруте пусто",
                "Откройте товар в каталоге и нажмите «Добавить в маршрут»", Modifier.padding(pad),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items!!, key = { it.id }) { item ->
                    RouteItemCard(
                        item,
                        onToggle = { vm.toggle(ctx, item) },
                        onDelete = { toDelete = item },
                        onOpen = { if (item.productId.isNotBlank()) onProduct(item.productId) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Убрать выполненные?", "Из маршрута будут удалены $done отмеченных товаров.", "Убрать",
        onConfirm = { vm.clearCompleted(ctx, routeId) }, onDismiss = { confirmClear = false })
    toDelete?.let { it2 ->
        ConfirmDialog("Удалить товар из маршрута?", it2.productName, onConfirm = { vm.delete(ctx, it2) }, onDismiss = { toDelete = null })
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale("ru"))

@Composable
private fun RouteItemCard(item: RouteItem, onToggle: () -> Unit, onDelete: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onOpen, modifier = modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.completed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.completed, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).alpha(if (item.completed) 0.55f else 1f)) {
                Text(
                    item.productName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                )
                val sub = listOf(item.brandName, item.subcategoryName).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CellChip(item.cellNumber)
                    if (item.quantity.isNotBlank()) AssistChipSmall(Icons.Rounded.Numbers, "× ${item.quantity}")
                    val at = item.reminderAt
                    if (at != null && !item.completed) {
                        val past = at <= System.currentTimeMillis()
                        AssistChipSmall(if (past) Icons.Rounded.NotificationsActive else Icons.Rounded.Alarm, if (past) "Пора!" else timeFmt.format(Date(at)))
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun AssistChipSmall(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
