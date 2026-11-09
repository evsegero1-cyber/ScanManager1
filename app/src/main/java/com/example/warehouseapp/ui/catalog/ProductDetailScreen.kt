@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.catalog

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.Errors
import com.example.warehouseapp.domain.Product
import com.example.warehouseapp.domain.Route
import com.example.warehouseapp.reminder.Reminders
import com.example.warehouseapp.ui.SessionViewModel
import com.example.warehouseapp.ui.components.*
import com.example.warehouseapp.ui.routes.RoutesViewModel
import kotlinx.coroutines.launch

@Composable
fun ProductDetailScreen(
    productId: String,
    catalog: CatalogViewModel,
    routesVm: RoutesViewModel,
    session: SessionViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val products by catalog.products.collectAsStateWithLifecycle()
    val product = products?.firstOrNull { it.id == productId }
    var image by remember { mutableStateOf<String?>(null) }
    var imageLoading by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    // Счётчик просмотров — один раз на открытие карточки
    LaunchedEffect(productId) { session.onProductViewed() }
    LaunchedEffect(product?.id, product?.updatedAt, product?.hasImage, product?.barcodeImageUrl?.length) {
        if (product != null) {
            imageLoading = true
            image = runCatching { ServiceLocator.catalogRepo.loadImage(product) }.getOrNull()
            imageLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Товар") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = { if (isAdmin && product != null) IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Редактировать") } },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (product != null) Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, null); Spacer(Modifier.width(8.dp)); Text("Добавить в маршрут")
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (product == null) {
            if (products == null) LoadingBox(Modifier.padding(pad))
            else EmptyState(Icons.Rounded.SearchOff, "Товар не найден", "Возможно, его удалили", Modifier.padding(pad))
            return@Scaffold
        }
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Фото штрихкода
            val bmp = rememberBase64Bitmap(image)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .clickable(enabled = bmp != null) { fullscreen = true },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bmp != null -> Image(bmp.asImageBitmap(), "Штрихкод", Modifier.fillMaxWidth().padding(12.dp), contentScale = ContentScale.FillWidth)
                    imageLoading && product.hasAnyImage -> CircularProgressIndicator()
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Rounded.QrCode2, null, Modifier.size(48.dp), tint = Color(0xFF9AA1AE))
                        Text("Фото штрихкода нет", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (bmp != null) {
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Fullscreen, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text("На весь экран", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(product.name, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            if (product.cellNumber.isNotBlank()) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GridView, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Ячейка", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(product.cellNumber, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            InfoCard(product)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (fullscreen && image != null) FullscreenImage(image!!) { fullscreen = false }
    if (showAdd && product != null) AddToRouteSheet(product, routesVm, session) { showAdd = false }
}

@Composable
private fun InfoCard(p: Product) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoLine(Icons.Rounded.Category, "Категория", p.categoryName)
            InfoLine(Icons.Rounded.Sell, "Бренд", p.brandName)
            InfoLine(Icons.Rounded.Folder, "Подкатегория", p.subcategoryName)
            if (p.description.isNotBlank()) InfoLine(Icons.Rounded.Notes, "Описание", p.description)
        }
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun FullscreenImage(base64: String, onClose: () -> Unit) {
    val bmp = rememberBase64Bitmap(base64)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            if (bmp != null) Image(
                bmp.asImageBitmap(), "Штрихкод",
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                contentScale = ContentScale.Fit,
            )
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp)) {
                Icon(Icons.Rounded.Close, "Закрыть")
            }
        }
    }
}

private val REMINDER_OPTIONS = listOf(0 to "Без", 5 to "5 мин", 15 to "15 мин", 30 to "30 мин", 60 to "1 час", 120 to "2 часа")

@Composable
fun AddToRouteSheet(product: Product, routesVm: RoutesViewModel, session: SessionViewModel, onDismiss: () -> Unit) {
    val routes by routesVm.routes.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Route?>(null) }
    var qty by remember { mutableStateOf("1") }
    var reminder by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(routes) { if (selected == null && routes?.size == 1) selected = routes!!.first() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding()) {
            Text("Добавить в маршрут", style = MaterialTheme.typography.titleLarge)
            Text(product.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text("Маршрут", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            when {
                routes == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                routes!!.isEmpty() -> Text("Маршрутов пока нет — их создаёт администратор", color = MaterialTheme.colorScheme.error)
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(routes!!, key = { it.id }) { r ->
                        FilterChip(selected = selected?.id == r.id, onClick = { selected = r }, label = { Text(r.name) },
                            leadingIcon = if (selected?.id == r.id) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                qty, { qty = it.take(12) }, label = { Text("Количество") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Numbers, null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { qty = ((qty.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() }) { Icon(Icons.Rounded.Remove, "Меньше") }
                        IconButton(onClick = { qty = ((qty.toIntOrNull() ?: 0) + 1).toString() }) { Icon(Icons.Rounded.Add, "Больше") }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Напоминание", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REMINDER_OPTIONS) { (m, label) ->
                    FilterChip(selected = reminder == m && custom.isBlank(), onClick = { reminder = m; custom = "" }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                custom, { custom = it.filter(Char::isDigit).take(4) }, label = { Text("Своё время, минут") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Alarm, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = selected != null && qty.isNotBlank() && !saving,
                onClick = {
                    val r = selected ?: return@Button
                    val minutes = custom.toIntOrNull() ?: reminder
                    if (minutes > 0 && !Reminders.canNotify(ctx) && Build.VERSION.SDK_INT >= 33) {
                        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    saving = true; error = null
                    scope.launch {
                        runCatching { routesVm.addItem(ctx, r, product, qty, minutes) }
                            .onSuccess {
                                session.onAddedToRoute()
                                session.message(if (minutes > 0) "Добавлено в «${r.name}», напомню через $minutes мин" else "Добавлено в «${r.name}»")
                                sheet.hide(); onDismiss()
                            }
                            .onFailure { error = Errors.human(it); saving = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Добавить")
            }
        }
    }
}
