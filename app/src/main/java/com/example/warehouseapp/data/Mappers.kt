package com.example.warehouseapp.data

import com.example.warehouseapp.domain.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

private fun DocumentSnapshot.str(k: String) = getString(k).orEmpty()
private fun DocumentSnapshot.int(k: String) = (get(k) as? Number)?.toInt() ?: 0
private fun DocumentSnapshot.long(k: String): Long? = when (val v = get(k)) {
    is Number -> v.toLong()
    is Timestamp -> v.toDate().time
    else -> null
}

fun DocumentSnapshot.toUser() = UserProfile(
    uid = id,
    email = str("email"),
    firstName = str("firstName"),
    lastName = str("lastName"),
    role = getString("role") ?: Roles.USER,
    level = int("level").coerceAtLeast(1),
    experience = int("experience"),
    viewedProductsCount = int("viewedProductsCount"),
    hasAddedToRoute = getBoolean("hasAddedToRoute") ?: false,
    photoBase64 = str("photoBase64"),
    frameType = str("frameType"),
)

fun DocumentSnapshot.toCategory() = Category(id, str("name"))
fun DocumentSnapshot.toBrand() = Brand(id, str("name"), str("categoryId"))
fun DocumentSnapshot.toSubcategory() = Subcategory(id, str("name"), str("brandId"), str("categoryId"))
fun DocumentSnapshot.toRoute() = Route(id, str("name"))

fun DocumentSnapshot.toProduct() = Product(
    id = id,
    name = str("name"),
    description = str("description"),
    cellNumber = str("cellNumber"),
    categoryId = str("categoryId"),
    categoryName = str("categoryName"),
    brandId = str("brandId"),
    brandName = str("brandName"),
    subcategoryId = str("subcategoryId"),
    subcategoryName = str("subcategoryName"),
    barcodeImageUrl = str("barcodeImageUrl"),
    hasImage = getBoolean("hasImage") ?: false,
    updatedAt = long("updatedAt"),
)

fun DocumentSnapshot.toRouteItem() = RouteItem(
    id = id,
    routeId = str("routeId"),
    productId = str("productId"),
    productName = str("productName"),
    brandName = str("brandName"),
    categoryName = str("categoryName"),
    subcategoryName = str("subcategoryName"),
    cellNumber = str("cellNumber"),
    quantity = get("quantity")?.toString().orEmpty(),
    reminderMinutes = int("reminderMinutes"),
    completed = getBoolean("completed") ?: false,
    addedAt = long("addedAt") ?: 0L,
)

fun DocumentSnapshot.toAchievement() = Achievement(
    id = id,
    title = str("title"),
    description = str("description"),
    hidden = getBoolean("hidden") ?: false,
    experienceReward = int("experienceReward"),
)
