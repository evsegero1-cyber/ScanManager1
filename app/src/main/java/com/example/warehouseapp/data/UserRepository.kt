package com.example.warehouseapp.data

import com.example.warehouseapp.domain.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class UserRepository(private val db: FirebaseFirestore) {
    private val users = db.collection("users")
    private val achievements = db.collection("achievements")
    private fun earnedCol(uid: String) = users.document(uid).collection("achievements")

    fun userFlow(uid: String): Flow<UserProfile?> = users.document(uid).docFlow { it.toUser() }
    fun usersFlow(): Flow<List<UserProfile>> = users.listFlow { it.toUser() }
    fun achievementsFlow(): Flow<List<Achievement>> = achievements.listFlow { it.toAchievement() }
    fun earnedFlow(uid: String): Flow<List<String>> =
        earnedCol(uid).whereEqualTo("earned", true).listFlow { it.getString("achievementId") ?: it.id }

    suspend fun updatePhoto(uid: String, base64: String) = users.document(uid).update("photoBase64", base64).await()
    suspend fun updateFrame(uid: String, key: String) = users.document(uid).update("frameType", key).await()
    suspend fun updateName(uid: String, first: String, last: String) =
        users.document(uid).update(mapOf("firstName" to first.trim(), "lastName" to last.trim())).await()

    suspend fun incrementViewed(uid: String) =
        users.document(uid).update("viewedProductsCount", FieldValue.increment(1)).await()

    suspend fun markAddedToRoute(uid: String) = users.document(uid).update("hasAddedToRoute", true).await()

    /**
     * Выдача достижения с начислением опыта — одной транзакцией.
     * Исправлена ошибка старой версии: опыт не накапливался (обнулялся при каждой выдаче).
     * Возвращает false, если достижение уже было получено.
     */
    suspend fun grantAchievement(uid: String, achievement: Achievement, auto: Boolean): Boolean {
        val userRef = users.document(uid)
        val earnedRef = earnedCol(uid).document(achievement.id)
        return db.runTransaction { tx ->
            val earned = tx.get(earnedRef)
            if (earned.exists() && earned.getBoolean("earned") == true) return@runTransaction false
            val user = tx.get(userRef)
            val exp = ((user.get("experience") as? Number)?.toInt() ?: 0) + achievement.experienceReward
            tx.set(
                earnedRef,
                mapOf("earned" to true, "achievementId" to achievement.id, "earnedAt" to FieldValue.serverTimestamp())
            )
            val upd = mutableMapOf<String, Any>("experience" to exp, "level" to Progression.levelFor(exp))
            if (auto) upd["lastAutoAchievement"] = achievement.id
            tx.update(userRef, upd)
            true
        }.await()
    }

    suspend fun revokeAchievement(uid: String, achievementId: String) =
        earnedCol(uid).document(achievementId).delete().await()

    suspend fun setLevelAndExperience(uid: String, level: Int, experience: Int) =
        users.document(uid).update(mapOf("level" to level, "experience" to experience)).await()

    suspend fun setRole(uid: String, role: String) = users.document(uid).update("role", role).await()

    suspend fun saveAchievement(a: Achievement) {
        val data = mapOf(
            "title" to a.title.trim(), "description" to a.description.trim(),
            "hidden" to a.hidden, "experienceReward" to a.experienceReward,
        )
        if (a.id.isBlank()) achievements.add(data).await() else achievements.document(a.id).set(data).await()
    }

    suspend fun deleteAchievement(id: String) = achievements.document(id).delete().await()
}
