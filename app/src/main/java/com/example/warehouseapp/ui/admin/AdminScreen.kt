@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.domain.*
import com.example.warehouseapp.ui.SessionViewModel
import com.example.warehouseapp.ui.catalog.CatalogViewModel
import com.example.warehouseapp.ui.components.*
import com.example.warehouseapp.ui.people.PeopleViewModel
import com.example.warehouseapp.ui.routes.RoutesViewModel

private enum class AdminTab(val title: String, val icon: ImageVector) {
    CATALOG("Каталог", Icons.Rounded.Inventory2),
    ROUTES("Маршруты", Icons.Rounded.Route),
    ACHIEVEMENTS("Достижения", Icons.Rounded.EmojiEvents),
    USERS("Сборщики", Icons.Rounded.Groups),
}

@Composable
fun AdminScreen(
    admin: AdminViewModel,
    catalog: CatalogViewModel,
    routesVm: RoutesViewModel,
    people: PeopleViewModel,
    session: SessionViewModel,
    onAddProduct: (subId: String) -> Unit,
    onEditProduct: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AdminTab.CATALOG) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Text("Админ-панель", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AdminTab.entries) { t ->
                FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t.title) },
                    leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            AdminTab.CATALOG -> AdminCatalog(admin, catalog, onAddProduct, onEditProduct)
            AdminTab.ROUTES -> AdminRoutes(admin, routesVm)
            AdminTab.ACHIEVEMENTS -> AdminAchievements(admin, session)
            AdminTab.USERS -> AdminUsers(admin, people, session)
        }
    }
}

// ------------------------------- Каталог -------------------------------

private sealed interface Level {
    data object Categories : Level
    data class Brands(val categoryId: String) : Level
    data class Subs(val brandId: String) : Level
    data class Products(val subId: String) : Level
}

@Composable
private fun AdminCatalog(admin: AdminViewModel, catalog: CatalogViewModel, onAddProduct: (String) -> Unit, onEditProduct: (String) -> Unit) {
    val cats by catalog.categories.collectAsStateWithLifecycle()
    val brands by catalog.brands.collectAsStateWithLifecycle()
    val subs by catalog.subcategories.collectAsStateWithLifecycle()
    val products by catalog.products.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<Level>(Level.Categories)) }
    val level = stack.last()
    var editing by remember { mutableStateOf<Pair<String?, String>?>(null) } // id to name
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    var maintenance by remember { mutableStateOf(false) }

    BackHandler(stack.size > 1) { stack = stack.dropLast(1) }
    fun push(l: Level) { stack = stack + l }

    val (title, rows) = when (level) {
        Level.Categories -> "Категории" to cats.orEmpty().map { c ->
            Row4(c.id, c.name, "${brands.orEmpty().count { it.categoryId == c.id }} брендов", categoryIcon(c.name)) { push(Level.Brands(c.id)) }
        }
        is Level.Brands -> (cats?.firstOrNull { it.id == level.categoryId }?.name ?: "Бренды") to
            brands.orEmpty().filter { it.categoryId == level.categoryId }.map { b ->
                Row4(b.id, b.name, "${subs.orEmpty().count { it.brandId == b.id }} подкатегорий", Icons.Rounded.Sell) { push(Level.Subs(b.id)) }
            }
        is Level.Subs -> (brands?.firstOrNull { it.id == level.brandId }?.name ?: "Подкатегории") to
            subs.orEmpty().filter { it.brandId == level.brandId }.map { s ->
                Row4(s.id, s.name, "${products.orEmpty().count { it.subcategoryId == s.id }} товаров", Icons.Rounded.Folder) { push(Level.Products(s.id)) }
            }
        is Level.Products -> (subs?.firstOrNull { it.id == level.subId }?.name ?: "Товары") to
            products.orEmpty().filter { it.subcategoryId == level.subId }.map { p ->
                Row4(p.id, p.name, listOf(p.cellNumber, if (p.hasAnyImage) "есть фото" else "без фото").filter { it.isNotBlank() }.joinToString(" · "), Icons.Rounded.QrCode2) { onEditProduct(p.id) }
            }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (level is Level.Products) onAddProduct(level.subId) else editing = null to "" },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = {
                    Text(when (level) { Level.Categories -> "Категория"; is Level.Brands -> "Бренд"; is Level.Subs -> "Подкатегория"; is Level.Products -> "Товар" })
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stack.size > 1) IconButton(onClick = { stack = stack.dropLast(1) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = if (stack.size > 1) 0.dp else 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (level == Level.Categories) IconButton(onClick = { maintenance = true }) { Icon(Icons.Rounded.Build, "Обслуживание") }
                }
            }
            if (rows.isEmpty()) item { EmptyState(Icons.Rounded.FolderOpen, "Пусто", "Нажмите кнопку «+», чтобы добавить") }
            items(rows, key = { it.id }) { r ->
                Card(onClick = r.onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(r.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (r.subtitle.isNotBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (level !is Level.Products) {
                            IconButton(onClick = { editing = r.id to r.name }) { Icon(Icons.Rounded.Edit, "Изменить") }
                            IconButton(onClick = { deleting = r.id to r.name }) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error) }
                        } else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(end = 10.dp))
                    }
                }
            }
        }
    }

    editing?.let { (id, initial) ->
        NameDialog(
            title = when {
                id != null -> "Переименовать"
                level == Level.Categories -> "Новая категория"
                level is Level.Brands -> "Новый бренд"
                else -> "Новая подкатегория"
            },
            initial = initial,
            hint = when (level) { Level.Categories -> "Название категории"; is Level.Brands -> "Название бренда"; else -> "Название подкатегории" },
            onDismiss = { editing = null },
        ) { name ->
            when (level) {
                Level.Categories -> admin.saveCategory(id, name)
                is Level.Brands -> admin.saveBrand(id, name, level.categoryId)
                is Level.Subs -> brands?.firstOrNull { it.id == level.brandId }?.let { admin.saveSub(id, name, it) }
                is Level.Products -> Unit
            }
        }
    }
    deleting?.let { (id, name) ->
        ConfirmDialog("Удалить «$name»?", "Удалить можно только пустой раздел — без вложенных брендов, подкатегорий или товаров.",
            onConfirm = {
                when (level) {
                    Level.Categories -> admin.deleteCategory(id)
                    is Level.Brands -> admin.deleteBrand(id)
                    is Level.Subs -> admin.deleteSub(id)
                    is Level.Products -> Unit
                }
            }, onDismiss = { deleting = null })
    }
    if (maintenance) ConfirmDialog(
        "Оптимизировать фото?",
        "Перенесёт старые фото штрихкодов в отдельное хранилище. Каталог станет загружаться быстрее. Действие безопасно и выполняется один раз.",
        confirm = "Запустить", destructive = false,
        onConfirm = { admin.migrateImages() }, onDismiss = { maintenance = false },
    )
}

private data class Row4(val id: String, val name: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun NameDialog(title: String, initial: String, hint: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text); onDismiss() }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------- Маршруты -------------------------------

@Composable
private fun AdminRoutes(admin: AdminViewModel, routesVm: RoutesViewModel) {
    val ctx = LocalContext.current
    val routes by routesVm.routes.collectAsStateWithLifecycle()
    val items by routesVm.allItems.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Route?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Route?>(null) }
    Scaffold(
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Маршрут") }) },
        contentWindowInsets = WindowInsets(0), containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (routes.isNullOrEmpty()) item { EmptyState(Icons.Rounded.Route, "Маршрутов нет") }
            items(routes.orEmpty(), key = { it.id }) { r ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Route, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall)
                            Text("${items.orEmpty().count { it.routeId == r.id }} товаров", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { editing = r }) { Icon(Icons.Rounded.Edit, "Изменить") }
                        IconButton(onClick = { deleting = r }) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (creating) NameDialog("Новый маршрут", "", "Название маршрута", { creating = false }) { admin.saveRoute(null, it) }
    editing?.let { r -> NameDialog("Переименовать", r.name, "Название маршрута", { editing = null }) { admin.saveRoute(r.id, it) } }
    deleting?.let { r ->
        ConfirmDialog("Удалить маршрут «${r.name}»?", "Все товары из этого маршрута тоже будут удалены.",
            onConfirm = { admin.deleteRoute(ctx, r.id) }, onDismiss = { deleting = null })
    }
}

// ------------------------------- Достижения -------------------------------

@Composable
private fun AdminAchievements(admin: AdminViewModel, session: SessionViewModel) {
    val list by session.achievements.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Achievement?>(null) }
    var deleting by remember { mutableStateOf<Achievement?>(null) }
    val autoIds = Progression.VIEW_ACHIEVEMENTS.map { it.second } + Progression.FIRST_ROUTE_ACHIEVEMENT
    Scaffold(
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editing = Achievement() }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Достижение") }) },
        contentWindowInsets = WindowInsets(0), containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Достижения с id 13, 14, 15 (10/50/100 просмотров) и 16 (первый товар в маршруте) выдаются автоматически.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            if (list.isEmpty()) item { EmptyState(Icons.Rounded.EmojiEvents, "Достижений нет") }
            items(list.sortedBy { it.title.lowercase() }, key = { it.id }) { a ->
                Card(onClick = { editing = a }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (a.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull("+${a.experienceReward} XP", if (a.hidden) "скрытое" else null, if (a.id in autoIds) "авто" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { deleting = a }) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    editing?.let { a -> AchievementDialog(a, onDismiss = { editing = null }) { admin.saveAchievement(it) } }
    deleting?.let { a ->
        ConfirmDialog("Удалить «${a.title}»?", if (a.id in autoIds) "Это авто-достижение: после удаления его перестанут получать." else "Достижение исчезнет из списка.",
            onConfirm = { admin.deleteAchievement(a.id) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun AchievementDialog(initial: Achievement, onDismiss: () -> Unit, onSave: (Achievement) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var desc by remember { mutableStateOf(initial.description) }
    var xp by remember { mutableStateOf(initial.experienceReward.toString()) }
    var hidden by remember { mutableStateOf(initial.hidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "Новое достижение" else "Достижение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(desc, { desc = it }, label = { Text("Описание") }, minLines = 2)
                OutlinedTextField(xp, { xp = it.filter(Char::isDigit).take(5) }, label = { Text("Награда, XP") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Скрытое", Modifier.weight(1f)); Switch(hidden, { hidden = it })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onSave(initial.copy(title = title, description = desc, experienceReward = xp.toIntOrNull() ?: 0, hidden = hidden)); onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------- Сборщики -------------------------------

@Composable
private fun AdminUsers(admin: AdminViewModel, people: PeopleViewModel, session: SessionViewModel) {
    val users by people.users.collectAsStateWithLifecycle()
    val me by session.user.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (users == null) item { LoadingBox() }
        items(users.orEmpty(), key = { it.uid }) { u ->
            Card(onClick = { selected = u.uid }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(u, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (u.isAdmin) Icon(Icons.Rounded.Shield, "Админ", Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.primary)
                    LevelBadge(u.level)
                }
            }
        }
    }
    val u = users?.firstOrNull { it.uid == selected }
    if (u != null) UserAdminSheet(u, isMe = u.uid == me?.uid, admin, people, session) { selected = null }
}

@Composable
private fun UserAdminSheet(u: UserProfile, isMe: Boolean, admin: AdminViewModel, people: PeopleViewModel, session: SessionViewModel, onDismiss: () -> Unit) {
    val achievements by session.achievements.collectAsStateWithLifecycle()
    val earned by remember(u.uid) { people.earnedOf(u.uid) }.collectAsStateWithLifecycle(emptySet())
    var xp by remember(u.uid, u.experience) { mutableStateOf(u.experience.toString()) }
    var confirmRole by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(u, 56.dp); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.fullName, style = MaterialTheme.typography.titleLarge)
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LevelBadge(u.level)
                }
            }
            item {
                val parsed = Validation.nonNegativeInt(xp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(xp, { xp = it.filter(Char::isDigit).take(7) }, label = { Text("Опыт (XP)") }, singleLine = true,
                        supportingText = { Text("Уровень станет: ${Progression.levelFor(parsed ?: 0)}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(enabled = parsed != null && parsed != u.experience, onClick = { admin.setXp(u.uid, parsed!!) }) { Text("Сохранить") }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Администратор", style = MaterialTheme.typography.bodyLarge)
                        if (isMe) Text("Нельзя снять права с себя", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(u.isAdmin, { confirmRole = true }, enabled = !isMe)
                }
            }
            item { HorizontalDivider(); SectionTitle("Достижения") }
            items(achievements.sortedBy { it.title.lowercase() }, key = { "a" + it.id }) { a ->
                val has = a.id in earned
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (has) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock, null,
                        tint = if (has) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.title, style = MaterialTheme.typography.bodyLarge)
                        Text("+${a.experienceReward} XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (has) TextButton(onClick = { admin.revoke(u.uid, a.id) }) { Text("Отозвать", color = MaterialTheme.colorScheme.error) }
                    else FilledTonalButton(onClick = { admin.grant(u.uid, a) }) { Text("Выдать") }
                }
            }
        }
    }
    if (confirmRole) ConfirmDialog(
        if (u.isAdmin) "Снять права администратора?" else "Сделать администратором?",
        if (u.isAdmin) "${u.fullName} больше не сможет редактировать каталог." else "${u.fullName} получит доступ к админ-панели.",
        confirm = "Подтвердить", destructive = u.isAdmin,
        onConfirm = { admin.setRole(u.uid, !u.isAdmin) }, onDismiss = { confirmRole = false },
    )
}
