package com.example.warehouseapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Запуск приложения на эмуляторе: экран входа, валидация и реальный ответ Firebase. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AuthSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitText(text: String, timeout: Long = 20_000) =
        rule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    @Test fun loginScreenShows() {
        waitText("ScanManager")
        rule.onNodeWithText("Войти").assertIsDisplayed()
        rule.onNodeWithText("Регистрация").assertIsDisplayed()
    }

    @Test fun invalidEmailValidation() {
        waitText("Войти")
        rule.onNode(hasText("Email") and hasSetTextAction()).performTextInput("not-an-email")
        rule.onNode(hasText("Пароль") and hasSetTextAction()).performTextInput("123456")
        rule.onNode(hasText("Войти") and hasClickAction() and !hasText("Вход")).performClick()
        waitText("Некорректный email", 5_000)
    }

    @Test fun registerValidation() {
        waitText("Регистрация")
        rule.onNodeWithText("Регистрация").performClick()
        waitText("Создать аккаунт")
        rule.onNodeWithText("Создать аккаунт").performClick()
        waitText("Введите имя", 5_000)
    }

    @Test fun wrongPasswordReachesFirebase() {
        waitText("Войти")
        rule.onNode(hasText("Email") and hasSetTextAction()).performTextInput("smoke-test-nobody@example.com")
        rule.onNode(hasText("Пароль") and hasSetTextAction()).performTextInput("wrong-password-123")
        rule.onNode(hasText("Войти") and hasClickAction() and !hasText("Вход")).performClick()
        // Любой из ожидаемых ответов сервера означает, что связь с Firebase есть и ошибка показана по-русски
        rule.waitUntil(30_000) {
            listOf("Неверный email или пароль", "Пользователь не найден", "Слишком много попыток").any {
                rule.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }
}
