@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.ui.catalog.CatalogViewModel
import com.example.warehouseapp.ui.components.ConfirmDialog
import com.example.warehouseapp.ui.components.rememberBase64Bitmap
import kotlinx.coroutines.launch

/**
 * Создание/редактирование товара.
 * @param productId null — новый товар; presetSubId — подкатегория, из которой открыли «Добавить».
 */
@Composable
fun ProductEditorScreen(
    productId: String?,
    presetSubId: String?,
    catalog: CatalogViewModel,
    admin: AdminViewModel,
    onDeleted: () -> Unit = {},
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cats by catalog.categories.collectAsStateWithLifecycle()
    val brands by catalog.brands.collectAsStateWithLifecycle()
    val subs by catalog.subcategories.collectAsStateWithLifecycle()
    val products by catalog.products.collectAsStateWithLifecycle()
    val existing = productId?.let { id -> products?.firstOrNull { it.id == id } }

    var initialized by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cell by remember { mutableStateOf("") }
    var catId by remember { mutableStateOf<String?>(null) }
    var brandId by remember { mutableStateOf<String?>(null) }
    var subId by remember { mutableStateOf<String?>(null) }
    /** null — не меняли; "" — удалить; иначе новое фото */
    var newImage by remember { mutableStateOf<String?>(null) }
    var currentImage by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    // Заполняем поля один раз, когда данные пришли
    LaunchedEffect(existing, subs) {
        if (initialized) return@LaunchedEffect
        if (productId != null && existing == null) return@LaunchedEffect
        if (subs == null) return@LaunchedEffect
        if (existing != null) {
            name = existing.name; description = existing.description; cell = existing.cellNumber
            catId = existing.categoryId; brandId = existing.brandId; subId = existing.subcategoryId
            currentImage = runCatching { ServiceLocator.catalogRepo.loadImage(existing) }.getOrNull()
        } else if (presetSubId != null) {
            subs!!.firstOrNull { it.id == presetSubId }?.let { s -> subId = s.id; brandId = s.brandId; catId = s.categoryId }
        }
        initialized = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            processing = true
            runCatching { admin.prepareImage(ctx, uri) }.onSuccess { newImage = it }
            processing = false
        }
    }

    val category = cats?.firstOrNull { it.id == catId }
    val brand = brands?.firstOrNull { it.id == brandId }
    val sub = subs?.firstOrNull { it.id == subId }
    val valid = name.isNotBlank() && category != null && brand != null && sub != null
    val shownImage = when (newImage) { null -> currentImage; "" -> null; else -> newImage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Новый товар" else "Редактирование") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = {
                    if (productId != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = MaterialTheme.colorScheme.error) }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Фото штрихкода
            val bmp = rememberBase64Bitmap(shownImage)
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    processing -> CircularProgressIndicator()
                    bmp != null -> Image(bmp.asImageBitmap(), "Штрихкод", Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(40.dp), tint = Color(0xFF9AA1AE))
                        Text("Фото штрихкода не выбрано", color = Color(0xFF6B7280))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(6.dp)); Text(if (shownImage == null) "Выбрать фото" else "Заменить")
                }
                if (shownImage != null) OutlinedButton(onClick = { newImage = "" }) { Text("Убрать") }
            }

            OutlinedTextField(name, { name = it }, label = { Text("Название товара*") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                isError = showErrors && name.isBlank())
            OutlinedTextField(cell, { cell = it.uppercase() }, label = { Text("Номер ячейки") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.GridView, null) })
            OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Picker("Категория*", cats.orEmpty().map { it.id to it.name }, catId, showErrors && category == null) {
                if (it != catId) { catId = it; brandId = null; subId = null }
            }
            Picker("Бренд*", brands.orEmpty().filter { it.categoryId == catId }.map { it.id to it.name }, brandId, showErrors && brand == null, enabled = catId != null) {
                if (it != brandId) { brandId = it; subId = null }
            }
            Picker("Подкатегория*", subs.orEmpty().filter { it.brandId == brandId }.map { it.id to it.name }, subId, showErrors && sub == null, enabled = brandId != null) {
                subId = it
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (!valid || saving) return@Button
                    saving = true
                    admin.saveProduct(productId, name, description, cell, category!!, brand!!, sub!!, newImage,
                        onDone = onClose, onFail = { saving = false })
                },
                enabled = !saving && !processing,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Сохранить") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete && productId != null) ConfirmDialog("Удалить товар?", existing?.name.orEmpty(),
        onConfirm = { admin.deleteProduct(productId) { onDeleted() } }, onDismiss = { confirmDelete = false })
}

@Composable
fun Picker(label: String, options: List<Pair<String, String>>, selected: String?, isError: Boolean, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second.orEmpty()
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, enabled = enabled, isError = isError,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Нет вариантов") }, onClick = { expanded = false }, enabled = false)
            options.forEach { (id, n) ->
                DropdownMenuItem(text = { Text(n) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
