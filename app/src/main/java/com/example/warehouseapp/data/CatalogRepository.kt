package com.example.warehouseapp.data

import com.example.warehouseapp.domain.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class CatalogRepository(private val db: FirebaseFirestore) {
    private val categories = db.collection("categories")
    private val brands = db.collection("brands")
    private val subcategories = db.collection("subcategories")
    private val products = db.collection("products")
    private val images = db.collection("product_images")

    fun categoriesFlow(): Flow<List<Category>> = categories.listFlow { it.toCategory() }
    fun brandsFlow(): Flow<List<Brand>> = brands.listFlow { it.toBrand() }
    fun subcategoriesFlow(): Flow<List<Subcategory>> = subcategories.listFlow { it.toSubcategory() }
    fun productsFlow(): Flow<List<Product>> = products.listFlow { it.toProduct() }
    fun productFlow(id: String): Flow<Product?> = products.document(id).docFlow { it.toProduct() }

    /** Фото штрихкода: новое хранится в product_images, старое — в самом товаре. */
    suspend fun loadImage(p: Product): String? {
        if (p.hasImage) {
            val d = images.document(p.id).get().await()
            d.getString("data")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return p.barcodeImageUrl.takeIf { it.isNotBlank() }
    }

    // ---------- Категории ----------
    suspend fun saveCategory(id: String?, name: String) {
        if (id == null) { categories.add(mapOf("name" to name.trim())).await(); return }
        val batch = db.batch()
        batch.update(categories.document(id), "name", name.trim())
        // Название категории продублировано в товарах — обновляем, чтобы не было «старых» имён
        products.whereEqualTo("categoryId", id).get().await().documents.forEach {
            batch.update(it.reference, "categoryName", name.trim())
        }
        batch.commit().await()
    }

    suspend fun deleteCategory(id: String) {
        val hasChildren = !brands.whereEqualTo("categoryId", id).limit(1).get().await().isEmpty
        require(!hasChildren) { "Сначала удалите бренды этой категории" }
        categories.document(id).delete().await()
    }

    // ---------- Бренды ----------
    suspend fun saveBrand(id: String?, name: String, categoryId: String) {
        val data = mapOf("name" to name.trim(), "categoryId" to categoryId)
        if (id == null) { brands.add(data).await(); return }
        val batch = db.batch()
        batch.update(brands.document(id), data)
        products.whereEqualTo("brandId", id).get().await().documents.forEach {
            batch.update(it.reference, mapOf("brandName" to name.trim()))
        }
        batch.commit().await()
    }

    suspend fun deleteBrand(id: String) {
        val hasChildren = !subcategories.whereEqualTo("brandId", id).limit(1).get().await().isEmpty
        require(!hasChildren) { "Сначала удалите подкатегории этого бренда" }
        brands.document(id).delete().await()
    }

    // ---------- Подкатегории ----------
    suspend fun saveSubcategory(id: String?, name: String, brandId: String, categoryId: String) {
        val data = mapOf("name" to name.trim(), "brandId" to brandId, "categoryId" to categoryId)
        if (id == null) { subcategories.add(data).await(); return }
        val batch = db.batch()
        batch.update(subcategories.document(id), data)
        products.whereEqualTo("subcategoryId", id).get().await().documents.forEach {
            batch.update(it.reference, "subcategoryName", name.trim())
        }
        batch.commit().await()
    }

    suspend fun deleteSubcategory(id: String) {
        val hasChildren = !products.whereEqualTo("subcategoryId", id).limit(1).get().await().isEmpty
        require(!hasChildren) { "Сначала удалите товары этой подкатегории" }
        subcategories.document(id).delete().await()
    }

    // ---------- Товары ----------
    /**
     * @param imageBase64 null — фото не менять, "" — удалить фото, иначе новое фото.
     */
    suspend fun saveProduct(
        id: String?,
        name: String,
        description: String,
        cellNumber: String,
        category: Category,
        brand: Brand,
        sub: Subcategory,
        imageBase64: String?,
    ): String {
        val ref = if (id == null) products.document() else products.document(id)
        val data = mutableMapOf<String, Any>(
            "name" to name.trim(),
            "description" to description.trim(),
            "cellNumber" to cellNumber.trim(),
            "categoryId" to category.id, "categoryName" to category.name,
            "brandId" to brand.id, "brandName" to brand.name,
            "subcategoryId" to sub.id, "subcategoryName" to sub.name,
            "updatedAt" to System.currentTimeMillis(),
        )
        val batch = db.batch()
        when {
            imageBase64 == null -> if (id == null) { data["hasImage"] = false; data["barcodeImageUrl"] = "" }
            imageBase64.isEmpty() -> {
                data["hasImage"] = false; data["barcodeImageUrl"] = ""
                batch.delete(images.document(ref.id))
            }
            else -> {
                data["hasImage"] = true; data["barcodeImageUrl"] = ""
                batch.set(images.document(ref.id), mapOf("data" to imageBase64))
            }
        }
        if (id == null) batch.set(ref, data) else batch.update(ref, data as Map<String, Any>)
        batch.commit().await()
        return ref.id
    }

    suspend fun deleteProduct(id: String) {
        val batch = db.batch()
        batch.delete(products.document(id))
        batch.delete(images.document(id))
        batch.commit().await()
    }

    /** Переносит старые фото (base64 внутри товара) в product_images — список товаров грузится быстрее. */
    suspend fun migrateLegacyImages(): Int {
        val legacy = products.get().await().documents.filter { !it.getString("barcodeImageUrl").isNullOrBlank() }
        legacy.forEach { d ->
            val batch = db.batch()
            batch.set(images.document(d.id), mapOf("data" to d.getString("barcodeImageUrl")))
            batch.update(d.reference, mapOf("barcodeImageUrl" to "", "hasImage" to true, "updatedAt" to FieldValue.serverTimestamp()))
            batch.commit().await()
        }
        return legacy.size
    }

    @Suppress("unused")
    fun productsOrdered(): Query = products.orderBy("name")
}
