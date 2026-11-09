package com.example.warehouseapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {
    @Test fun levels() {
        assertEquals(1, Progression.levelFor(0))
        assertEquals(1, Progression.levelFor(499))
        assertEquals(2, Progression.levelFor(500))
        assertEquals(5, Progression.levelFor(2000))
        assertEquals(1, Progression.levelFor(-10))
    }

    @Test fun progress() {
        assertEquals(250 to 0.5f, Progression.progressInLevel(750))
        assertEquals(0 to 0f, Progression.progressInLevel(1000))
    }

    @Test fun autoAchievements() {
        val all = setOf("13", "14", "15", "16")
        assertEquals(emptyList<String>(), Progression.pendingAutoAchievements(9, false, emptySet(), all))
        assertEquals(listOf("13"), Progression.pendingAutoAchievements(10, false, emptySet(), all))
        assertEquals(listOf("14", "16"), Progression.pendingAutoAchievements(55, true, setOf("13"), all))
        assertEquals(listOf("13", "14", "15"), Progression.pendingAutoAchievements(100, false, emptySet(), all))
        // Не выдаём то, чего нет в базе
        assertEquals(listOf("13"), Progression.pendingAutoAchievements(100, true, emptySet(), setOf("13")))
        // Уже полученное не выдаём повторно
        assertEquals(emptyList<String>(), Progression.pendingAutoAchievements(100, true, all, all))
    }

    @Test fun frames() {
        assertTrue(Frames.isUnlocked(Frames.byKey(""), 1))
        assertFalse(Frames.isUnlocked(Frames.byKey("gold"), 4))
        assertTrue(Frames.isUnlocked(Frames.byKey("gold"), 5))
        assertFalse(Frames.isUnlocked(Frames.byKey("ice"), 14))
        assertEquals("", Frames.byKey("unknown").key)
    }

    @Test fun reminderTime() {
        assertNull(RouteItem(reminderMinutes = 0, addedAt = 1000).reminderAt)
        assertEquals(1000 + 15 * 60_000L, RouteItem(reminderMinutes = 15, addedAt = 1000).reminderAt)
    }

    @Test fun userNames() {
        assertEquals("Иван Петров", UserProfile(firstName = "Иван", lastName = "Петров").fullName)
        assertEquals("ivan", UserProfile(email = "ivan@mail.ru").fullName)
        assertEquals("ИП", UserProfile(firstName = "иван", lastName = "петров").initials)
        assertTrue(UserProfile(role = "ADMIN").isAdmin)
        assertFalse(UserProfile().isAdmin)
    }
}

class SearchTest {
    private val p = Product(id = "1", name = "Пакет майка 30x60", brandName = "Ёлка", cellNumber = "A-12-3", categoryName = "Bags")

    @Test fun byWordsAnyOrder() {
        assertTrue(Search.matches(p, "майка пакет"))
        assertTrue(Search.matches(p, "a-12"))
        assertTrue(Search.matches(p, "елка"))   // ё == е
        assertTrue(Search.matches(p, "  "))
        assertFalse(Search.matches(p, "пакет коробка"))
    }

    @Test fun sortPrefixFirst() {
        val list = listOf(Product(id = "a", name = "Большой пакет"), Product(id = "b", name = "Пакет малый"))
        assertEquals(listOf("b", "a"), Search.filter(list, "пакет").map { it.id })
    }

    @Test fun validation() {
        assertNull(Validation.email("a@b.ru"))
        assertEquals("Некорректный email", Validation.email("ab.ru"))
        assertEquals("Минимум 6 символов", Validation.password("123"))
        assertEquals(5, Validation.nonNegativeInt(" 5 "))
        assertNull(Validation.nonNegativeInt("-1"))
        assertNull(Validation.nonNegativeInt("x"))
    }

    @Test fun errors() {
        assertEquals("Неверный email или пароль", Errors.human(Exception("INVALID_LOGIN_CREDENTIALS")))
        assertEquals("Недостаточно прав для этого действия", Errors.human(Exception("PERMISSION_DENIED: Missing")))
    }
}
