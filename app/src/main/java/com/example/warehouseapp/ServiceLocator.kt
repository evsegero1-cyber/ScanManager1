package com.example.warehouseapp

import android.content.Context
import com.example.warehouseapp.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/** Простейший DI: по одному экземпляру репозиториев на приложение. */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            // Офлайн-кэш: каталог открывается мгновенно и работает без сети
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().setSizeBytes(100L * 1024 * 1024).build())
                .build()
        }
    }
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val authRepo by lazy { AuthRepository(auth, db) }
    val catalogRepo by lazy { CatalogRepository(db) }
    val routeRepo by lazy { RouteRepository(db) }
    val userRepo by lazy { UserRepository(db) }
    val settings by lazy { SettingsStore(appContext) }

    fun init(context: Context) { appContext = context.applicationContext }
}
