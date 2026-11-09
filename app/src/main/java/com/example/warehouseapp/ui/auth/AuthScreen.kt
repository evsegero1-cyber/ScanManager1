@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.warehouseapp.ServiceLocator
import com.example.warehouseapp.domain.Errors
import com.example.warehouseapp.domain.Validation
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var info by mutableStateOf<String?>(null); private set

    fun clear() { error = null; info = null }

    fun login(email: String, password: String) {
        val v = Validation.email(email) ?: if (password.isEmpty()) "Введите пароль" else null
        if (v != null) { error = v; return }
        run { ServiceLocator.authRepo.login(email, password) }
    }

    fun register(email: String, password: String, first: String, last: String) {
        val v = Validation.name(first, "имя") ?: Validation.name(last, "фамилию")
            ?: Validation.email(email) ?: Validation.password(password)
        if (v != null) { error = v; return }
        run { ServiceLocator.authRepo.register(email, password, first, last) }
    }

    fun reset(email: String) {
        Validation.email(email)?.let { error = it; return }
        run { ServiceLocator.authRepo.resetPassword(email); info = "Письмо для сброса пароля отправлено на $email" }
    }

    private fun run(block: suspend () -> Unit) {
        if (loading) return
        loading = true; error = null; info = null
        viewModelScope.launch {
            runCatching { block() }.onFailure { error = Errors.human(it) }
            loading = false
        }
    }
}

@Composable
fun AuthScreen(vm: AuthViewModel = viewModel()) {
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme

    fun submit() {
        focus.clearFocus()
        if (register) vm.register(email, password, first, last) else vm.login(email, password)
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.85f), cs.background), endY = 1400f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(cs.onPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(46.dp), tint = cs.onPrimary) }
            Spacer(Modifier.height(16.dp))
            Text("ScanManager", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimary)
            Text("Каталог склада и маршруты подпитки", style = MaterialTheme.typography.bodyMedium, color = cs.onPrimary.copy(alpha = 0.8f))
            Spacer(Modifier.height(28.dp))

            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLowest),
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = !register, onClick = { register = false; vm.clear() }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Вход") }
                        SegmentedButton(selected = register, onClick = { register = true; vm.clear() }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Регистрация") }
                    }
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(register, label = "mode") { reg ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (reg) {
                                OutlinedTextField(first, { first = it }, label = { Text("Имя") }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(last, { last = it }, label = { Text("Фамилия") }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
                            }
                            OutlinedTextField(email, { email = it.trim() }, label = { Text("Email") }, singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.AlternateEmail, null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, singleLine = true,
                                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                trailingIcon = {
                                    IconButton(onClick = { showPass = !showPass }) {
                                        Icon(if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать пароль")
                                    }
                                },
                                supportingText = if (reg) ({ Text("Минимум 6 символов") }) else null,
                                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth())
                        }
                    }
                    AnimatedVisibility(vm.error != null) {
                        Text(vm.error.orEmpty(), color = cs.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                    AnimatedVisibility(vm.info != null) {
                        Text(vm.info.orEmpty(), color = cs.primary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = ::submit, enabled = !vm.loading,
                        modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    ) {
                        if (vm.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                        else Text(if (register) "Создать аккаунт" else "Войти")
                    }
                    if (!register) {
                        TextButton(onClick = { vm.reset(email) }, enabled = !vm.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Забыли пароль?")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Данные хранятся в облаке и доступны с любого устройства",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
