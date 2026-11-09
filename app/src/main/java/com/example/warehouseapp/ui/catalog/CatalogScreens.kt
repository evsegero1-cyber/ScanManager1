@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.domain.Product
import com.example.warehouseapp.ui.components.*

@Composable
fun CatalogHomeScreen(
    vm: CatalogViewModel,
    onCategory: (String) -> Unit,
    onProduct: (String) -> Unit,
) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Text("Каталог", style = MaterialTheme.typography.headlineMedium)
            val total = products?.size
            if (total != null) {
                Text("$total товаров", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            SearchField(query, { vm.query.value = it }, "Товар, бренд, ячейка…")
        }
        Spacer(Modifier.height(8.dp))
        when {
            query.isNotBlank() -> {
                if (results.isEmpty()) EmptyState(Icons.Rounded.SearchOff, "Ничего не найдено", "Попробуйте другое слово или номер ячейки")
                else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Найдено: ${results.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(results, key = { it.id }) { p -> ProductRow(p, showPath = true) { onProduct(p.id) } }
                }
            }
            cats == null -> LoadingBox()
            error != null && cats.isNullOrEmpty() -> EmptyState(Icons.Rounded.CloudOff, "Не удалось загрузить каталог", error)
            cats!!.isEmpty() -> EmptyState(Icons.Rounded.Inventory2, "Каталог пуст", "В базе нет категорий. Проверьте: Профиль → Диагностика базы")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cats!!, key = { it.id }) { c ->
                    val bCount = brands?.count { it.categoryId == c.id } ?: 0
                    val pCount = products?.count { it.categoryId == c.id } ?: 0
                    CategoryTile(c.name, categoryIcon(c.name), "$bCount брендов · $pCount товаров") { onCategory(c.id) }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(title: String, icon: ImageVector, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().heightIn(min = 132.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, Modifier.padding(10.dp).size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProductRow(p: Product, showPath: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    if (p.hasAnyImage) Icons.Rounded.QrCode2 else Icons.Rounded.Inventory2, null,
                    Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val path = if (showPath) listOf(p.brandName, p.subcategoryName).filter { it.isNotBlank() }.joinToString(" · ") else p.description
                if (path.isNotBlank()) Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            CellChip(p.cellNumber)
        }
    }
}

@Composable
fun ListPage(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = actions,
                scrollBehavior = scroll,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad -> content(pad) }
}

@Composable
private fun NavRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BrandsScreen(vm: CatalogViewModel, categoryId: String, onBack: () -> Unit, onBrand: (String) -> Unit) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val subs by vm.subcategories.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val cat = cats?.firstOrNull { it.id == categoryId }
    val list = brands?.filter { it.categoryId == categoryId }
    ListPage(cat?.name ?: "Бренды", "Выберите бренд", onBack) { pad ->
        when {
            list == null -> LoadingBox(Modifier.padding(pad))
            list.isEmpty() -> EmptyState(Icons.Rounded.Sell, "Брендов нет", modifier = Modifier.padding(pad))
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { b ->
                    val s = subs?.count { it.brandId == b.id } ?: 0
                    val p = products?.count { it.brandId == b.id } ?: 0
                    NavRow(b.name, "$s подкатегорий · $p товаров", Icons.Rounded.Sell) { onBrand(b.id) }
                }
            }
        }
    }
}

@Composable
fun SubcategoriesScreen(vm: CatalogViewModel, brandId: String, onBack: () -> Unit, onSub: (String) -> Unit) {
    val brands by vm.brands.collectAsStateWithLifecycle()
    val subs by vm.subcategories.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    val brand = brands?.firstOrNull { it.id == brandId }
    val list = subs?.filter { it.brandId == brandId }
    ListPage(brand?.name ?: "Подкатегории", "Выберите подкатегорию", onBack) { pad ->
        when {
            list == null -> LoadingBox(Modifier.padding(pad))
            list.isEmpty() -> EmptyState(Icons.Rounded.FolderOpen, "Подкатегорий нет", modifier = Modifier.padding(pad))
            else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { s ->
                    val p = products?.count { it.subcategoryId == s.id } ?: 0
                    NavRow(s.name, "$p товаров", Icons.Rounded.Folder) { onSub(s.id) }
                }
            }
        }
    }
}

@Composable
fun ProductsScreen(vm: CatalogViewModel, subId: String, isAdmin: Boolean, onBack: () -> Unit, onProduct: (String) -> Unit, onAdd: () -> Unit) {
    val subs by vm.subcategories.collectAsStateWithLifecycle()
    val products by vm.products.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    val sub = subs?.firstOrNull { it.id == subId }
    val list = products?.filter { it.subcategoryId == subId }?.let { l -> if (filter.isBlank()) l else l.filter { com.example.warehouseapp.domain.Search.matches(it, filter) } }
    Scaffold(
        floatingActionButton = {
            if (isAdmin) ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Товар") })
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { outer ->
        Box(Modifier.padding(outer)) {
            ListPage(sub?.name ?: "Товары", list?.let { "${it.size} товаров" }, onBack) { pad ->
                Column(Modifier.padding(top = pad.calculateTopPadding())) {
                    SearchField(filter, { filter = it }, "Фильтр в подкатегории", Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    when {
                        list == null -> LoadingBox()
                        list.isEmpty() -> EmptyState(Icons.Rounded.Inventory2, if (filter.isBlank()) "Товаров нет" else "Ничего не найдено")
                        else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(list, key = { it.id }) { p -> ProductRow(p) { onProduct(p.id) } }
                        }
                    }
                }
            }
        }
    }
}
