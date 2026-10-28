package com.example.catering.service

import com.example.catering.model.user.User
import com.example.catering.repository.UserRepository
import com.example.catering.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher
) {

    suspend fun register(
        name: String,
        phone: String,
        password: String
    ): User {

        val existingUser = userRepository.findByPhone(phone)

        if (existingUser != null) {
            throw PhoneAlreadyRegisteredException()
        }

        val passwordHash = withContext(Dispatchers.Default) {
            passwordHasher.hash(password)
        }

        return userRepository.createUser(
            name = name,
            phone = phone,
            passwordHash = passwordHash,
            address = ""
        )
    }

    suspend fun login(
        phone: String,
        password: String
    ): User {

        val existingUser =
            userRepository.findByPhone(phone)
                ?: throw InvalidCredentialsException()

        val isPasswordCorrect = withContext(Dispatchers.Default) {
            passwordHasher.verify(
                password = password,
                passwordHash = existingUser.passwordHash
            )
        }

        if (!isPasswordCorrect) {
            throw InvalidCredentialsException()
        }
        if (!userRepository.isPhoneVerified(existingUser.id)) {
            throw PhoneNotVerifiedException()
        }
        return existingUser
    }



    suspend fun changePassword(
        userId: Int,
        currentPassword: String,
        newPassword: String
    ) {
        // Проверяем допустимый размер нового пароля.
        if (newPassword.length !in 6..128) {
            throw InvalidNewPasswordException()
        }

        // Получаем актуальные данные пользователя.
        val user = userRepository.findById(userId)
            ?: throw InvalidCredentialsException()

        // Проверяем действующий пароль.
        val isCurrentPasswordCorrect = withContext(
            Dispatchers.Default
        ) {
            passwordHasher.verify(
                password = currentPassword,
                passwordHash = user.passwordHash
            )
        }

        if (!isCurrentPasswordCorrect) {
            throw InvalidCredentialsException()
        }

        // Запрещаем повторное использование того же пароля.
        if (currentPassword == newPassword) {
            throw SamePasswordException()
        }

        // Создаём защищённый хеш нового пароля.
        val newPasswordHash = withContext(
            Dispatchers.Default
        ) {
            passwordHasher.hash(newPassword)
        }

        // Атомарно обновляем пароль и версию сессии.
        // expectedPasswordHash защищает от одновременной смены.
        val updated = userRepository.updatePasswordHash(
            userId = userId,
            expectedPasswordHash = user.passwordHash,
            passwordHash = newPasswordHash
        )

        if (!updated) {
            throw PasswordChangeConflictException()
        }
    }


}

class PhoneAlreadyRegisteredException :
    RuntimeException("Телефон уже зарегистрирован")

class InvalidCredentialsException :
    RuntimeException("Неверный телефон или пароль")

class InvalidNewPasswordException :
    RuntimeException("Пароль должен содержать от 6 до 128 символов")

class SamePasswordException :
    RuntimeException("Новый пароль должен отличаться от старого")

class PasswordChangeConflictException :
    RuntimeException(
        "Пароль уже был изменён. Повторите вход в аккаунт"
    )
class PhoneNotVerifiedException :
    RuntimeException("Номер телефона не подтверждён")