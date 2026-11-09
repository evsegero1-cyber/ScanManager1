package com.example.warehouseapp.domain

/** Правила уровней, рамок и автоматических достижений. Чистая логика — покрыта юнит-тестами. */
object Progression {
    const val XP_PER_LEVEL = 500

    fun levelFor(experience: Int): Int = experience.coerceAtLeast(0) / XP_PER_LEVEL + 1

    /** Опыт внутри текущего уровня и доля прогресса до следующего (0..1). */
    fun progressInLevel(experience: Int): Pair<Int, Float> {
        val xp = experience.coerceAtLeast(0) % XP_PER_LEVEL
        return xp to xp / XP_PER_LEVEL.toFloat()
    }

    /** Порог просмотров товаров -> id достижения (совпадает со старым приложением). */
    val VIEW_ACHIEVEMENTS: List<Pair<Int, String>> = listOf(10 to "13", 50 to "14", 100 to "15")
    const val FIRST_ROUTE_ACHIEVEMENT = "16"

    /**
     * Какие авто-достижения положены пользователю, но ещё не получены.
     * Возвращает в порядке порогов.
     */
    fun pendingAutoAchievements(
        viewedProducts: Int,
        hasAddedToRoute: Boolean,
        earned: Set<String>,
        existing: Set<String>,
    ): List<String> {
        val result = mutableListOf<String>()
        for ((threshold, id) in VIEW_ACHIEVEMENTS) {
            if (viewedProducts >= threshold && id !in earned && id in existing) result += id
        }
        if (hasAddedToRoute && FIRST_ROUTE_ACHIEVEMENT !in earned && FIRST_ROUTE_ACHIEVEMENT in existing) {
            result += FIRST_ROUTE_ACHIEVEMENT
        }
        return result
    }
}

data class Frame(val key: String, val name: String, val colorArgb: Long, val widthDp: Float, val requiredLevel: Int)

object Frames {
    val all = listOf(
        Frame("", "Без рамки", 0x00000000, 0f, 0),
        Frame("gold", "Золотая", 0xFFFFC107, 3f, 5),
        Frame("neon", "Неоновая", 0xFF00FF00, 4f, 10),
        Frame("ice", "Ледяная", 0xFF00BFFF, 5f, 15),
    )

    fun byKey(key: String): Frame = all.firstOrNull { it.key == key } ?: all.first()
    fun isUnlocked(frame: Frame, level: Int): Boolean = level >= frame.requiredLevel
}

object Search {
    private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim()

    /** Поиск по названию, бренду, подкатегории, категории, ячейке и описанию. Все слова должны совпасть. */
    fun matches(p: Product, query: String): Boolean {
        val words = norm(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = norm(listOf(p.name, p.brandName, p.subcategoryName, p.categoryName, p.cellNumber, p.description).joinToString(" "))
        return words.all { hay.contains(it) }
    }

    fun filter(products: List<Product>, query: String): List<Product> =
        products.filter { matches(it, query) }.sortedWith(compareBy({ !norm(it.name).startsWith(norm(query)) }, { norm(it.name) }))

    fun matchesRoute(r: Route, query: String): Boolean = norm(r.name).contains(norm(query))
}

object Validation {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun email(e: String): String? = when {
        e.isBlank() -> "Введите email"
        !EMAIL.matches(e.trim()) -> "Некорректный email"
        else -> null
    }
    fun password(p: String): String? = if (p.length < 6) "Минимум 6 символов" else null
    fun name(n: String, what: String): String? = if (n.isBlank()) "Введите $what" else null
    fun nonNegativeInt(s: String): Int? = s.trim().toIntOrNull()?.takeIf { it >= 0 }
}

/** Перевод кодов ошибок Firebase в понятный текст. */
object Errors {
    fun human(t: Throwable?): String {
        val msg = t?.message.orEmpty()
        return when {
            "INVALID_LOGIN_CREDENTIALS" in msg || "password is invalid" in msg || "credential is incorrect" in msg ->
                "Неверный email или пароль"
            "no user record" in msg -> "Пользователь не найден"
            "email address is already in use" in msg -> "Этот email уже зарегистрирован"
            "badly formatted" in msg -> "Некорректный email"
            "network error" in msg || "UNAVAILABLE" in msg -> "Нет подключения к интернету"
            "PERMISSION_DENIED" in msg -> "Недостаточно прав для этого действия"
            "blocked all requests" in msg || "TOO_MANY" in msg -> "Слишком много попыток, попробуйте позже"
            msg.isBlank() -> "Неизвестная ошибка"
            else -> msg
        }
    }
}
