package com.example.warehouseapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.warehouseapp.reminder.Reminders
import com.example.warehouseapp.ui.MainApp
import com.example.warehouseapp.ui.SessionState
import com.example.warehouseapp.ui.SessionViewModel
import com.example.warehouseapp.ui.auth.AuthScreen
import com.example.warehouseapp.ui.components.EmptyState
import com.example.warehouseapp.ui.theme.ScanTheme

class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()
    private var openRouteId by mutableStateOf<String?>(null)

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { session.state.value == SessionState.Loading }
        enableEdgeToEdge()
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(this) && savedInstanceState == null) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by session.settings.collectAsStateWithLifecycle()
            val state by session.state.collectAsStateWithLifecycle()
            ScanTheme(settings) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (val s = state) {
                        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        SessionState.SignedOut -> AuthScreen()
                        SessionState.NoProfile -> Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                Icons.Rounded.PersonOff, "Профиль не найден",
                                "Аккаунт есть, но профиля в базе нет. Обратитесь к администратору.",
                            ) { Button(onClick = { session.logout() }) { Text("Выйти") } }
                        }
                        is SessionState.SignedIn -> Box(Modifier.statusBarsPadding()) {
                            MainApp(session, s.user, openRouteId) { openRouteId = null }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Reminders.EXTRA_ROUTE_ID)?.takeIf { it.isNotBlank() }?.let { openRouteId = it }
        intent?.removeExtra(Reminders.EXTRA_ROUTE_ID)
    }
}
