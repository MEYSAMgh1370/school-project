package com.roshdeandishe.student.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roshdeandishe.student.data.repository.AuthRepository
import com.roshdeandishe.student.data.repository.Enrollment
import com.roshdeandishe.student.data.repository.Membership
import com.roshdeandishe.student.data.repository.Profile
import com.roshdeandishe.student.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val memberships: List<Membership> = emptyList(),
    val enrollment: Enrollment? = null,
    val error: String? = null,
    val loggedOut: Boolean = false
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val profile = profileRepository.loadProfile()
                val memberships = profileRepository.loadMemberships()

                // فقط اگر نقش فعال STUDENT در مدرسه‌ی انتخاب‌شده باشد،
                // ثبت‌نام کلاس هم خوانده می‌شود - برای TEACHER/SCHOOL_ADMIN
                // این endpoint همیشه ۲۰۴ برمی‌گرداند، پس اصلاً صدا زده نمی‌شود.
                val enrollment = memberships
                    .firstOrNull { it.active && it.roleCode == "STUDENT" }
                    ?.let { membership -> profileRepository.loadEnrollment(membership.schoolId) }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        profile = profile,
                        memberships = memberships,
                        enrollment = enrollment
                    )
                }
            } catch (e: HttpException) {
                // /api/me، /api/me/memberships و /api/me/enrollment هیچ‌کدام
                // @RequiresPermission ندارند (فقط authenticated بودن لازم
                // است) - پس ۴۰۱/۴۰۳ اینجا فقط یک معنا دارد: توکن نامعتبر/
                // منقضی شده، نه رد یک مجوز خاص.
                if (e.code() == 401 || e.code() == 403) {
                    logout()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "خطایی رخ داد. دوباره تلاش کنید.") }
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = "اتصال به سرور برقرار نشد.") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(loggedOut = true) }
        }
    }
}
