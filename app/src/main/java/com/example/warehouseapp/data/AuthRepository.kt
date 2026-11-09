package com.example.warehouseapp.data

import com.example.warehouseapp.domain.Roles
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) {

    val currentUid: String? get() = auth.currentUser?.uid

    val uidFlow: Flow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(l)
        awaitClose { auth.removeAuthStateListener(l) }
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String, firstName: String, lastName: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Не удалось создать пользователя")
        try {
            db.collection("users").document(user.uid).set(
                mapOf(
                    "email" to email.trim(),
                    "firstName" to firstName.trim(),
                    "lastName" to lastName.trim(),
                    "role" to Roles.USER,
                    "level" to 1,
                    "experience" to 0,
                    "viewedProductsCount" to 0,
                    "hasAddedToRoute" to false,
                    "photoBase64" to "",
                    "frameType" to "",
                )
            ).await()
        } catch (e: Exception) {
            // Не оставляем «полузарегистрированный» аккаунт без профиля
            runCatching { user.delete().await() }
            throw e
        }
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun logout() = auth.signOut()
}
