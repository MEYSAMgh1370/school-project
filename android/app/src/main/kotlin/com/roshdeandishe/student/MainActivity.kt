package com.roshdeandishe.student

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.roshdeandishe.student.data.repository.AuthRepository
import com.roshdeandishe.student.ui.dashboard.DashboardScreen
import com.roshdeandishe.student.ui.login.LoginScreen
import com.roshdeandishe.student.ui.theme.RoshdeandisheTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ROUTE_LOGIN = "login"
private const val ROUTE_DASHBOARD = "dashboard"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoshdeandisheTheme {
                RootNavigation(authRepository)
            }
        }
    }
}

@Composable
private fun RootNavigation(authRepository: AuthRepository) {
    var sessionRestored by remember { mutableStateOf(false) }
    var startDestination by remember { mutableStateOf(ROUTE_LOGIN) }

    // تصمیم صفحه‌ی شروع (لاگین یا داشبورد) باید تا وقتی که کش توکن از
    // دیسک خوانده نشده صبر کند - TokenStore.loadIntoCache معادل غیرهمزمان
    // "این کاربر از قبل واردشده؟" است.
    LaunchedEffect(Unit) {
        authRepository.restoreSession()
        startDestination = if (authRepository.isLoggedIn()) ROUTE_DASHBOARD else ROUTE_LOGIN
        sessionRestored = true
    }

    if (!sessionRestored) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(ROUTE_DASHBOARD) {
                    popUpTo(ROUTE_LOGIN) { inclusive = true }
                }
            })
        }
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(onLoggedOut = {
                navController.navigate(ROUTE_LOGIN) {
                    popUpTo(ROUTE_DASHBOARD) { inclusive = true }
                }
            })
        }
    }
}
