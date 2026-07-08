package com.roshdeandishe.student.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.roshdeandishe.student.data.repository.Enrollment
import com.roshdeandishe.student.data.repository.Membership
import com.roshdeandishe.student.data.repository.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) {
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("داشبورد") },
                actions = {
                    TextButton(onClick = viewModel::logout) {
                        Text("خروج")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    text = uiState.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error
                )
                else -> DashboardContent(
                    profile = uiState.profile,
                    memberships = uiState.memberships,
                    enrollment = uiState.enrollment
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    profile: Profile?,
    memberships: List<Membership>,
    enrollment: Enrollment?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(profile?.fullName ?: "", style = MaterialTheme.typography.titleLarge)
                    Text(profile?.email ?: "", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (enrollment != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("کلاس من", style = MaterialTheme.typography.titleMedium)
                        Text(enrollment.className)
                        enrollment.section?.let { Text("بخش: $it") }
                    }
                }
            }
        }

        item {
            Text("عضویت‌های من", style = MaterialTheme.typography.titleMedium)
        }

        items(memberships) { membership ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(RoleLabels.forCode(membership.roleCode), style = MaterialTheme.typography.bodyLarge)
                    Text("مدرسه #${membership.schoolId}", style = MaterialTheme.typography.bodySmall)
                    if (!membership.active) {
                        Text("غیرفعال", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
