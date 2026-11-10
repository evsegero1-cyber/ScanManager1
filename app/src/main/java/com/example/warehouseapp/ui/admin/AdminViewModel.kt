package com.example.warehouseapp.ui.admin

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.data.ImageUtils
import com.example.warehouseapp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdminViewModel : ViewModel() {
    private val catalog = ServiceLocator.catalogRepo
    private val routes = ServiceLocator.routeRepo
    private val users = ServiceLocator.userRepo

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private fun run(ok: String?, onDone: () -> Unit = {}, onFail: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess {
                // Наши изменения уже в кэше — обновляем экраны без лишних чтений с сервера
                catalog.reloadFromCache()
                ok?.let { _messages.tryEmit(it) }; onDone()
            }
            .onFailure { _messages.tryEmit(if (it is IllegalArgumentException) it.message.orEmpty() else Errors.human(it)); onFail() }
    }

    fun saveCategory(id: String?, name: String) = run(if (id == null) "Категория добавлена" else "Сохранено") { catalog.saveCategory(id, name) }
    fun deleteCategory(id: String) = run("Категория удалена") { catalog.deleteCategory(id) }
    fun saveBrand(id: String?, name: String, catId: String) = run(if (id == null) "Бренд добавлен" else "Сохранено") { catalog.saveBrand(id, name, catId) }
    fun deleteBrand(id: String) = run("Бренд удалён") { catalog.deleteBrand(id) }
    fun saveSub(id: String?, name: String, brand: Brand) = run(if (id == null) "Подкатегория добавлена" else "Сохранено") {
        catalog.saveSubcategory(id, name, brand.id, brand.categoryId)
    }
    fun deleteSub(id: String) = run("Подкатегория удалена") { catalog.deleteSubcategory(id) }

    fun saveProduct(
        id: String?, name: String, description: String, cell: String,
        category: Category, brand: Brand, sub: Subcategory, imageBase64: String?, onDone: () -> Unit, onFail: () -> Unit,
    ) = run(if (id == null) "Товар добавлен" else "Товар сохранён", onDone, onFail) {
        catalog.saveProduct(id, name, description, cell, category, brand, sub, imageBase64)
    }
    fun deleteProduct(id: String, onDone: () -> Unit) = run("Товар удалён", onDone) { catalog.deleteProduct(id) }

    suspend fun prepareImage(ctx: Context, uri: Uri): String =
        withContext(Dispatchers.IO) { ImageUtils.uriToBase64(ctx, uri, maxSide = 1200, maxBytes = 700_000) }

    fun migrateImages() = run(null) {
        val n = catalog.migrateLegacyImages()
        _messages.tryEmit(if (n == 0) "Старых фото не найдено — всё уже оптимизировано" else "Перенесено фото: $n")
    }

    fun saveRoute(id: String?, name: String) = run(if (id == null) "Маршрут создан" else "Сохранено") { routes.saveRoute(id, name) }
    fun deleteRoute(ctx: Context, id: String) = run("Маршрут удалён") {
        routes.deleteRoute(id).forEach { com.example.warehouseapp.reminder.Reminders.cancel(ctx.applicationContext, it) }
    }

    fun saveAchievement(a: Achievement) = run("Достижение сохранено") { users.saveAchievement(a) }
    fun deleteAchievement(id: String) = run("Достижение удалено") { users.deleteAchievement(id) }

    fun grant(uid: String, a: Achievement) = run(null) {
        val ok = users.grantAchievement(uid, a, auto = false)
        _messages.tryEmit(if (ok) "Выдано: ${a.title} (+${a.experienceReward} XP)" else "Это достижение уже получено")
    }
    fun revoke(uid: String, id: String) = run("Достижение отозвано") { users.revokeAchievement(uid, id) }

    fun setXp(uid: String, experience: Int) =
        run("Опыт сохранён") { users.setLevelAndExperience(uid, Progression.levelFor(experience), experience) }

    fun setRole(uid: String, admin: Boolean) = run(if (admin) "Назначен администратором" else "Права администратора сняты") {
        users.setRole(uid, if (admin) Roles.ADMIN else Roles.USER)
    }
}
