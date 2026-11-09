@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.warehouseapp.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.warehouseapp.data.ImageUtils
import com.example.warehouseapp.data.ThemeMode
import com.example.warehouseapp.domain.Frames
import com.example.warehouseapp.domain.UserProfile
import com.example.warehouseapp.reminder.ReminderWorker
import com.example.warehouseapp.reminder.Reminders
import com.example.warehouseapp.ui.SessionViewModel
import com.example.warehouseapp.ui.components.*
import com.example.warehouseapp.ui.people.ProfileHeader
import com.example.warehouseapp.ui.people.StatsRow
import com.example.warehouseapp.ui.people.achievementItems
import com.example.warehouseapp.ui.theme.AccentPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun ProfileScreen(session: SessionViewModel, user: UserProfile) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val achievements by session.achievements.collectAsStateWithLifecycle()
    val earned by session.earned.collectAsStateWithLifecycle()
    val settings by session.settings.collectAsStateWithLifecycle()
    var photoMenu by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ImageUtils.uriToBase64(ctx, uri, maxSide = 320, maxBytes = 120_000) } }
                .onSuccess { session.setPhoto(it) }
                .onFailure { session.message("Не удалось загрузить фото") }
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        session.message(if (granted) "Уведомления разрешены" else "Уведомления запрещены — включите их в настройках Android")
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Профиль", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, "Изменить имя") }
            }
        }
        item {
            ProfileHeader(user, onAvatarClick = { photoMenu = true })
        }
        item { StatsRow(user, earned.size) }

        item { SectionTitle("Рамка аватара") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Frames.all.forEach { f ->
                    val unlocked = Frames.isUnlocked(f, user.level)
                    val selected = user.frameType == f.key
                    Column(
                        Modifier.width(78.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .clickable(enabled = unlocked && !selected) { session.setFrame(f.key) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(44.dp).then(
                                if (f.widthDp > 0) Modifier.border(f.widthDp.dp, Color(f.colorArgb), CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            ),
                            contentAlignment = Alignment.Center,
                        ) { if (!unlocked) Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(6.dp))
                        Text(f.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        if (!unlocked) Text("ур. ${f.requiredLevel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionTitle("Достижения · ${earned.count { id -> achievements.any { it.id == id } }} из ${achievements.size}") }
        achievementItems(achievements, earned)

        item { SectionTitle("Оформление") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Тема", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val modes = listOf(ThemeMode.SYSTEM to "Авто", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная")
                        modes.forEachIndexed { i, (m, label) ->
                            SegmentedButton(selected = settings.themeMode == m, onClick = { session.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Акцентный цвет", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AccentPresets.forEach { (c, name) ->
                            val sel = settings.accent == c && !settings.dynamicColor
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                    .clickable { session.setDynamic(false); session.setAccent(c) }
                                    .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Icons.Rounded.Check, name, tint = Color.White) }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 31) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Цвета обоев", style = MaterialTheme.typography.bodyLarge)
                                Text("Material You", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(settings.dynamicColor, { session.setDynamic(it) })
                        }
                    }
                    TextButton(onClick = { session.resetSettings() }) { Text("Сбросить оформление") }
                }
            }
        }

        item { SectionTitle("Прочее") }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column {
                    ListItem(
                        headlineContent = { Text("Проверить уведомления") },
                        supportingContent = { Text("Пришлёт тестовое напоминание через 5 секунд") },
                        leadingContent = { Icon(Icons.Rounded.NotificationsActive, null) },
                        modifier = Modifier.clickable {
                            if (!Reminders.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) {
                                notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                                    .setInitialDelay(5, TimeUnit.SECONDS)
                                    .setInputData(workDataOf("itemId" to "test", "product" to "Тестовый товар", "cell" to "A-01", "qty" to "1"))
                                    .build()
                                WorkManager.getInstance(ctx).enqueue(req)
                                session.message("Тестовое уведомление придёт через 5 секунд")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Диагностика базы") },
                        supportingContent = { Text("Проверить, какие данные видит приложение") },
                        leadingContent = { Icon(Icons.Rounded.MedicalServices, null) },
                        modifier = Modifier.clickable { diagnostics = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { confirmLogout = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            val v = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() }
            Text("ScanManager ${v.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (photoMenu) {
        AlertDialog(
            onDismissRequest = { photoMenu = false },
            title = { Text("Фото профиля") },
            text = { Text("Выберите новое фото или удалите текущее.") },
            confirmButton = {
                TextButton(onClick = {
                    photoMenu = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Выбрать фото") }
            },
            dismissButton = {
                if (user.photoBase64.isNotBlank()) TextButton(onClick = { photoMenu = false; session.removePhoto() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (editName) {
        var first by remember { mutableStateOf(user.firstName) }
        var last by remember { mutableStateOf(user.lastName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Имя и фамилия") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(first, { first = it }, label = { Text("Имя") }, singleLine = true)
                    OutlinedTextField(last, { last = it }, label = { Text("Фамилия") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = first.isNotBlank() && last.isNotBlank(), onClick = { session.setName(first, last); editName = false }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Отмена") } },
        )
    }
    if (diagnostics) DiagnosticsDialog { diagnostics = false }
    if (confirmLogout) ConfirmDialog("Выйти из аккаунта?", "Чтобы вернуться, понадобится снова ввести email и пароль.", "Выйти",
        onConfirm = { session.logout() }, onDismiss = { confirmLogout = false })
}
