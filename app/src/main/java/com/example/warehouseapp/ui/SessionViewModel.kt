package com.example.warehouseapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.data.AppSettings
import com.example.warehouseapp.data.ThemeMode
import com.example.warehouseapp.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: UserProfile) : SessionState
    /** Аккаунт есть, а профиля в базе нет (например, удалён админом). */
    data object NoProfile : SessionState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SessionViewModel : ViewModel() {
    private val authRepo = ServiceLocator.authRepo
    private val userRepo = ServiceLocator.userRepo
    private val settingsStore = ServiceLocator.settings

    val settings: StateFlow<AppSettings> =
        settingsStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val state: StateFlow<SessionState> = authRepo.uidFlow
        .flatMapLatest { uid ->
            if (uid == null) flowOf(SessionState.SignedOut)
            else userRepo.userFlow(uid)
                .map { u -> if (u == null) SessionState.NoProfile else SessionState.SignedIn(u) }
                .catch { emit(SessionState.NoProfile) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    val user: StateFlow<UserProfile?> =
        state.map { (it as? SessionState.SignedIn)?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val achievements: StateFlow<List<Achievement>> = authRepo.uidFlow
        .flatMapLatest { if (it == null) flowOf(emptyList()) else userRepo.achievementsFlow().catch { emit(emptyList()) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val earned: StateFlow<Set<String>> = authRepo.uidFlow
        .flatMapLatest { if (it == null) flowOf(emptyList()) else userRepo.earnedFlow(it).catch { emit(emptyList()) } }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _toast = MutableStateFlow<String?>(null)
    val achievementToast: StateFlow<String?> = _toast

    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _message

    private val autoMutex = Mutex()

    init {
        // Как только данные пользователя меняются — проверяем, не положены ли авто-достижения
        viewModelScope.launch {
            combine(user, earned, achievements) { u, e, a -> Triple(u, e, a) }
                .debounce(400)
                .collect { (u, e, a) -> if (u != null && a.isNotEmpty()) checkAuto(u, e, a) }
        }
    }

    private suspend fun checkAuto(u: UserProfile, earned: Set<String>, all: List<Achievement>) = autoMutex.withLock {
        val pending = Progression.pendingAutoAchievements(
            u.viewedProductsCount, u.hasAddedToRoute, earned, all.map { it.id }.toSet(),
        )
        for (id in pending) {
            val a = all.first { it.id == id }
            val granted = runCatching { userRepo.grantAchievement(u.uid, a, auto = true) }.getOrDefault(false)
            if (granted) showToast(a.title)
        }
    }

    private fun showToast(title: String) {
        viewModelScope.launch { _toast.value = title; delay(3000); _toast.value = null }
    }

    fun message(text: String) { _message.tryEmit(text) }

    fun onProductViewed() {
        val uid = user.value?.uid ?: return
        viewModelScope.launch { runCatching { userRepo.incrementViewed(uid) } }
    }

    fun onAddedToRoute() {
        val u = user.value ?: return
        if (u.hasAddedToRoute) return
        viewModelScope.launch { runCatching { userRepo.markAddedToRoute(u.uid) } }
    }

    fun setPhoto(base64: String) = launchSafe("Фото обновлено") { userRepo.updatePhoto(it, base64) }
    fun removePhoto() = launchSafe("Фото удалено") { userRepo.updatePhoto(it, "") }
    fun setFrame(key: String) = launchSafe(null) { userRepo.updateFrame(it, key) }
    fun setName(first: String, last: String) = launchSafe("Имя сохранено") { userRepo.updateName(it, first, last) }

    private fun launchSafe(ok: String?, block: suspend (uid: String) -> Unit) {
        val uid = user.value?.uid ?: return
        viewModelScope.launch {
            runCatching { block(uid) }
                .onSuccess { ok?.let(::message) }
                .onFailure { message(Errors.human(it)) }
        }
    }

    fun setTheme(m: ThemeMode) = viewModelScope.launch { settingsStore.setTheme(m) }
    fun setAccent(c: Long) = viewModelScope.launch { settingsStore.setAccent(c) }
    fun setDynamic(v: Boolean) = viewModelScope.launch { settingsStore.setDynamic(v) }
    fun resetSettings() = viewModelScope.launch { settingsStore.reset() }

    fun logout() = authRepo.logout()
}
