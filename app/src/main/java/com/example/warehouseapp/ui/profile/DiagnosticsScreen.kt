@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.warehouseapp.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.warehouseapp.ServiceLocator
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

private data class Check(val name: String, val ok: Boolean, val text: String)

/** Проверяет, что реально лежит в базе и что разрешают правила. Результат можно скопировать и отправить. */
@Composable
fun DiagnosticsDialog(onDismiss: () -> Unit) {
    var results by remember { mutableStateOf<List<Check>?>(null) }
    var header by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        val db = ServiceLocator.db
        val auth = ServiceLocator.auth
        val opts = FirebaseApp.getInstance().options
        header = "Проект: ${opts.projectId}\nUID: ${auth.currentUser?.uid}\nEmail: ${auth.currentUser?.email}"
        val list = mutableListOf<Check>()
        // Доступен ли сервер Firestore по обычному HTTPS (отличает блокировку сети от проблем приложения)
        list += kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val c = java.net.URL("https://firestore.googleapis.com/").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val code = c.responseCode; c.disconnect()
                Check("Сервер Firestore (HTTPS)", true, "отвечает, код $code")
            }.getOrElse { Check("Сервер Firestore (HTTPS)", false, "${it.javaClass.simpleName}: ${it.message}") }
        }
        val vpn = runCatching {
            val cm = ServiceLocator.appContext.getSystemService(android.net.ConnectivityManager::class.java)
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)
        list += Check("VPN", !vpn, if (vpn) "включён — может мешать соединению с базой" else "выключен")
        results = list.toList()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            list += runCatching {
                val d = db.collection("users").document(uid).get(Source.SERVER).await()
                Check("Мой профиль", d.exists(), if (d.exists()) "роль: ${d.getString("role")}" else "документа нет")
            }.getOrElse { Check("Мой профиль", false, it.message.orEmpty()) }
        }
        for (c in listOf("users", "categories", "brands", "subcategories", "products", "routes", "route_items", "achievements")) {
            list += runCatching {
                // count() стоит 1 чтение на 1000 документов, а не по чтению на каждый документ
                val n = kotlinx.coroutines.withTimeout(15_000) {
                    db.collection(c).count().get(com.google.firebase.firestore.AggregateSource.SERVER).await().count
                }
                Check(c, true, "документов: $n")
            }.getOrElse { Check(c, false, it.message.orEmpty().take(200)) }
            results = list.toList()
        }
        // Реальная причина сетевой ошибки — из журнала Firestore этого приложения
        val log = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "brief", "-t", "400", "Firestore:W", "*:S"))
                p.inputStream.bufferedReader().readLines()
                    .filter { "Status{" in it || "Exception" in it || "UNAVAILABLE" in it || "failed" in it.lowercase() }
                    .takeLast(4).joinToString("\n") { it.take(300) }
            }.getOrDefault("")
        }
        if (log.isNotBlank()) list += Check("Журнал Firestore", false, log)
        list += Check("Версия Firestore", true, com.google.firebase.firestore.BuildConfig.VERSION_NAME)
        results = list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Диагностика базы") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer { Text(header, style = MaterialTheme.typography.bodySmall) }
                if (results == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                results?.forEach { r ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (r.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                            tint = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(r.name, style = MaterialTheme.typography.labelLarge)
                            Text(r.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = results != null, onClick = {
                clipboard.setText(AnnotatedString(header + "\n" + results.orEmpty().joinToString("\n") { "${if (it.ok) "OK" else "ОШИБКА"} ${it.name}: ${it.text}" }))
            }) { Text("Скопировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
