package com.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.expensetracker.ui.screens.HomeScreen
import com.expensetracker.ui.screens.RulesScreen
import com.expensetracker.ui.theme.DarkPrimary
import com.expensetracker.ui.theme.ExpenseTrackerTheme
import kotlinx.coroutines.launch

import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle Permission granted/rejected if necessary
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request SMS permissions on startup
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())

        val sharedPref = getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)

        setContent {
            ExpenseTrackerTheme {
                var isLoggedIn by remember { mutableStateOf(sharedPref.getBoolean("isLoggedIn", false)) }
                var userEmail by remember { mutableStateOf(sharedPref.getString("user_email", "Profile") ?: "Profile") }
                
                if (!isLoggedIn) {
                    val viewModel: com.expensetracker.ui.viewmodels.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    com.expensetracker.ui.screens.LoginScreen(
                        onLoginSuccess = { authCode, email ->
                            // Update local preferences
                            sharedPref.edit()
                                .putBoolean("isLoggedIn", true)
                                .putString("user_email", email)
                                .apply()
                            
                            // Immediately fetch history for this newly logged in user
                            viewModel.refreshDataFromServer()
                            
                            // Silent Backend Sync of Auth Code
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    val payload = com.expensetracker.data.network.AuthPayload(authCode)
                                    val result = com.expensetracker.data.network.RetrofitClient.api.authenticateGoogle(payload)
                                    android.util.Log.d("AUTH", "Backend auth result: $result")
                                } catch(e: Exception) {
                                    android.util.Log.e("AUTH", "Backend auth FAILED: ${e.message}", e)
                                }
                            }
                            userEmail = email
                            isLoggedIn = true
                        }
                    )
                } else {
                    val viewModel: com.expensetracker.ui.viewmodels.MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    MainScreen(
                        userEmail = userEmail,
                        onLogout = {
                            // WIPE ALL LOCAL DATA ON LOGOUT
                            viewModel.clearAllLocalData()
                            
                            sharedPref.edit()
                                .putBoolean("isLoggedIn", false)
                                .putString("user_email", "")
                                .apply()
                            isLoggedIn = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(userEmail: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(userEmail, style = androidx.compose.material3.MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = com.expensetracker.ui.theme.DarkSurface, 
                    titleContentColor = androidx.compose.ui.graphics.Color.Gray,
                    actionIconContentColor = androidx.compose.ui.graphics.Color.Red
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = com.expensetracker.ui.theme.DarkSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkPrimary,
                        unselectedIconColor = com.expensetracker.ui.theme.TextSecondary,
                        selectedTextColor = DarkPrimary,
                        unselectedTextColor = com.expensetracker.ui.theme.TextSecondary,
                        indicatorColor = com.expensetracker.ui.theme.DarkBackground
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Rules") },
                    label = { Text("Rules") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkPrimary,
                        unselectedIconColor = com.expensetracker.ui.theme.TextSecondary,
                        selectedTextColor = DarkPrimary,
                        unselectedTextColor = com.expensetracker.ui.theme.TextSecondary,
                        indicatorColor = com.expensetracker.ui.theme.DarkBackground
                    )
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = com.expensetracker.ui.theme.DarkBackground
        ) {
            when (selectedTab) {
                0 -> HomeScreen()
                1 -> RulesScreen()
            }
        }
    }
}
