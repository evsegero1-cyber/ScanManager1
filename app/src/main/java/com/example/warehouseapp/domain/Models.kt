package com.example.warehouseapp.domain

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = Roles.USER,
    val level: Int = 1,
    val experience: Int = 0,
    val viewedProductsCount: Int = 0,
    val hasAddedToRoute: Boolean = false,
    val photoBase64: String = "",
    val frameType: String = "",
) {
    val isAdmin: Boolean get() = role == Roles.ADMIN
    val fullName: String
        get() = "$firstName $lastName".trim().ifEmpty { email.substringBefore('@') }
    val initials: String
        get() = listOf(firstName, lastName).mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { email.take(1).uppercase() }
}

object Roles {
    const val USER = "USER"
    const val ADMIN = "ADMIN"
}

data class Category(val id: String = "", val name: String = "")
data class Brand(val id: String = "", val name: String = "", val categoryId: String = "")
data class Subcategory(
    val id: String = "", val name: String = "", val brandId: String = "", val categoryId: String = "",
)

data class Product(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val cellNumber: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val brandId: String = "",
    val brandName: String = "",
    val subcategoryId: String = "",
    val subcategoryName: String = "",
    /** Старое поле: фото штрихкода в base64 прямо в документе товара. */
    val barcodeImageUrl: String = "",
    /** Новое поле: фото лежит в отдельном документе product_images/{id}. */
    val hasImage: Boolean = false,
    val updatedAt: Long? = null,
) {
    val hasAnyImage: Boolean get() = hasImage || barcodeImageUrl.isNotBlank()
}

data class Route(val id: String = "", val name: String = "")

data class RouteItem(
    val id: String = "",
    val routeId: String = "",
    val productId: String = "",
    val productName: String = "",
    val brandName: String = "",
    val categoryName: String = "",
    val subcategoryName: String = "",
    val cellNumber: String = "",
    val quantity: String = "",
    val reminderMinutes: Int = 0,
    val completed: Boolean = false,
    val addedAt: Long = 0L,
) {
    /** Время срабатывания напоминания (мс) или null, если напоминания нет. */
    val reminderAt: Long? get() = if (reminderMinutes > 0 && addedAt > 0) addedAt + reminderMinutes * 60_000L else null
}

data class Achievement(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val hidden: Boolean = false,
    val experienceReward: Int = 0,
)
