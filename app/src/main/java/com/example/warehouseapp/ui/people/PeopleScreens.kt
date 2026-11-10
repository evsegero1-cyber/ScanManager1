@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.people

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.Achievement
import com.example.warehouseapp.domain.UserProfile
import com.example.warehouseapp.ui.catalog.ListPage
import com.example.warehouseapp.ui.components.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.warehouseapp.data.toUser

private fun com.google.firebase.firestore.DocumentSnapshot.toUserCompat() = toUser()

class PeopleViewModel : ViewModel() {
    // Список сборщиков с фото — тяжёлый, поэтому тоже с сервера не чаще раза в 6 часов (или по «Обновить»)
    private val cache = com.example.warehouseapp.data.CachedCollection(
        "users", { ServiceLocator.db.collection("users") }, { it.toUserCompat() }, 6 * 60 * 60 * 1000L,
    )
    val error: StateFlow<String?> = cache.error.map { it?.let(com.example.warehouseapp.domain.Errors::human) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val users: StateFlow<List<UserProfile>?> = cache.items
        .map { l -> l?.sortedWith(compareByDescending<UserProfile> { it.experience }.thenBy { it.fullName.lowercase() }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init { viewModelScope.launch { cache.load() } }
    fun refresh() = viewModelScope.launch { cache.load(force = true) }
    fun reloadFromCache() = viewModelScope.launch { cache.reloadFromCache() }

    fun earnedOf(uid: String): Flow<Set<String>> =
        ServiceLocator.userRepo.earnedFlow(uid).map { it.toSet() }.catch { emit(emptySet()) }
}

@Composable
fun CollectorsScreen(vm: PeopleViewModel, me: UserProfile?, onUser: (String) -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val list = users?.filter { q.isBlank() || it.fullName.contains(q, true) || it.email.contains(q, true) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сборщики", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.refresh() }) { Icon(Icons.Rounded.Refresh, "Обновить") }
            }
            Text("Рейтинг по опыту", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SearchField(q, { q = it }, "Поиск по имени")
        }
        when {
            list == null -> LoadingBox()
            err != null && users.isNullOrEmpty() -> EmptyState(Icons.Rounded.CloudOff, "Не удалось загрузить список", err)
            list.isEmpty() -> EmptyState(Icons.Rounded.Groups, "Никого не найдено")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(list, key = { _, u -> u.uid }) { i, u ->
                    val rank = users!!.indexOf(u) + 1
                    Card(
                        onClick = { onUser(u.uid) }, shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (u.uid == me?.uid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RankMark(rank)
                            Spacer(Modifier.width(10.dp))
                            UserAvatar(u, 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                                    if (u.isAdmin) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.Shield, "Администратор", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
                                }
                                Text("${u.experience} XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LevelBadge(u.level)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankMark(rank: Int) {
    val color = when (rank) { 1 -> Color(0xFFFFC107); 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32); else -> null }
    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
        if (color != null) Icon(Icons.Rounded.EmojiEvents, "Место $rank", tint = color)
        else Text("$rank", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun UserProfileScreen(vm: PeopleViewModel, uid: String, achievements: List<Achievement>, onBack: () -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    val u = users?.firstOrNull { it.uid == uid }
    val earned by remember(uid) { vm.earnedOf(uid) }.collectAsStateWithLifecycle(emptySet())
    ListPage("Профиль сборщика", null, onBack) { pad ->
        if (u == null) { LoadingBox(Modifier.padding(pad)); return@ListPage }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ProfileHeader(u) }
            item { StatsRow(u, earned.size) }
            item { SectionTitle("Достижения") }
            achievementItems(achievements, earned)
        }
    }
}

@Composable
fun ProfileHeader(u: UserProfile, onAvatarClick: (() -> Unit)? = null) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                UserAvatar(u, 104.dp)
                if (onAvatarClick != null) SmallFloatingActionButton(
                    onClick = onAvatarClick, modifier = Modifier.align(Alignment.BottomEnd),
                    containerColor = MaterialTheme.colorScheme.primary,
                ) { Icon(Icons.Rounded.PhotoCamera, "Изменить фото", Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(u.fullName, style = MaterialTheme.typography.titleLarge)
            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBadge(u.level)
                if (u.isAdmin) AssistChip(onClick = {}, label = { Text("Админ") }, leadingIcon = { Icon(Icons.Rounded.Shield, null, Modifier.size(16.dp)) })
            }
            Spacer(Modifier.height(14.dp))
            XpBar(u.experience, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun StatsRow(u: UserProfile, earnedCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Опыт", "${u.experience}", Icons.Rounded.Bolt, Modifier.weight(1f))
        StatTile("Просмотров", "${u.viewedProductsCount}", Icons.Rounded.Visibility, Modifier.weight(1f))
        StatTile("Награды", "$earnedCount", Icons.Rounded.EmojiEvents, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

fun androidx.compose.foundation.lazy.LazyListScope.achievementItems(all: List<Achievement>, earned: Set<String>) {
    if (all.isEmpty()) {
        item { EmptyState(Icons.Rounded.EmojiEvents, "Список достижений пуст") }
        return
    }
    val sorted = all.sortedWith(compareBy<Achievement> { it.id !in earned }.thenBy { it.title.lowercase() })
    items(sorted.size, key = { "ach_" + sorted[it].id }) { i -> AchievementRow(sorted[i], sorted[i].id in earned) }
}

@Composable
fun AchievementRow(a: Achievement, earned: Boolean) {
    val secret = a.hidden && !earned
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (earned) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when { earned -> Icons.Rounded.EmojiEvents; secret -> Icons.Rounded.HelpOutline; else -> Icons.Rounded.Lock },
                null, Modifier.size(30.dp),
                tint = if (earned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (secret) "Секретное достижение" else a.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (secret) "Откроется по мере выполнения" else a.description,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (a.experienceReward > 0) Text("+${a.experienceReward} XP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
