package com.roshdeandishe.student.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roshdeandishe.student.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    var email by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        email = value
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("ایمیل و رمز عبور را وارد کنید.")
            return
        }

        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                authRepository.login(email.trim(), password)
                _uiState.value = LoginUiState.Success
            } catch (e: HttpException) {
                val message = if (e.code() == 401) {
                    "ایمیل یا رمز عبور نادرست است."
                } else {
                    "خطایی در ورود رخ داد. دوباره تلاش کنید."
                }
                _uiState.value = LoginUiState.Error(message)
            } catch (e: IOException) {
                _uiState.value = LoginUiState.Error("اتصال به سرور برقرار نشد.")
            }
        }
    }
}
