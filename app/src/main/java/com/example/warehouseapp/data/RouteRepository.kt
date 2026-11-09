package com.example.warehouseapp.data

import com.example.warehouseapp.domain.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class RouteRepository(private val db: FirebaseFirestore) {
    private val routes = db.collection("routes")
    private val items = db.collection("route_items")

    fun routesFlow(): Flow<List<Route>> = routes.listFlow { it.toRoute() }
    fun allItemsFlow(): Flow<List<RouteItem>> = items.listFlow { it.toRouteItem() }
    fun itemsFlow(routeId: String): Flow<List<RouteItem>> =
        items.whereEqualTo("routeId", routeId).listFlow { it.toRouteItem() }

    suspend fun saveRoute(id: String?, name: String) {
        if (id == null) routes.add(mapOf("name" to name.trim())).await()
        else routes.document(id).update("name", name.trim()).await()
    }

    /** Удаляет маршрут вместе с его товарами (раньше товары оставались «сиротами»). */
    suspend fun deleteRoute(id: String): List<String> {
        val docs = items.whereEqualTo("routeId", id).get().await().documents
        docs.chunked(400).forEach { chunk ->
            val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await()
        }
        routes.document(id).delete().await()
        return docs.map { it.id }
    }

    suspend fun addItem(routeId: String, p: Product, quantity: String, reminderMinutes: Int): RouteItem {
        val ref = items.document()
        val item = RouteItem(
            id = ref.id, routeId = routeId, productId = p.id, productName = p.name,
            brandName = p.brandName, categoryName = p.categoryName, subcategoryName = p.subcategoryName,
            cellNumber = p.cellNumber, quantity = quantity.trim(), reminderMinutes = reminderMinutes,
            completed = false, addedAt = System.currentTimeMillis(),
        )
        ref.set(
            mapOf(
                "routeId" to item.routeId, "productId" to item.productId, "productName" to item.productName,
                "brandName" to item.brandName, "categoryName" to item.categoryName,
                "subcategoryName" to item.subcategoryName, "cellNumber" to item.cellNumber,
                "quantity" to item.quantity, "reminderMinutes" to item.reminderMinutes,
                "completed" to false, "addedAt" to item.addedAt,
            )
        ).await()
        return item
    }

    suspend fun setCompleted(itemId: String, completed: Boolean) {
        items.document(itemId).update("completed", completed).await()
    }

    suspend fun deleteItem(itemId: String) {
        items.document(itemId).delete().await()
    }

    suspend fun clearCompleted(routeId: String): List<String> {
        val docs = items.whereEqualTo("routeId", routeId).whereEqualTo("completed", true).get().await().documents
        if (docs.isNotEmpty()) {
            val b = db.batch(); docs.forEach { b.delete(it.reference) }; b.commit().await()
        }
        return docs.map { it.id }
    }
}
