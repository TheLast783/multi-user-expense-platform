package com.expensetracker.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope

val NeonDarkPurple = Color(0xFF130026)
val NeonPurple = Color(0xFFB026FF)
val NeonPink = Color(0xFFFF26B0)

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    
    val serverClientId = com.expensetracker.BuildConfig.SERVER_CLIENT_ID

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val authCode = account.serverAuthCode ?: ""
            val email = account.email ?: "User"
            Log.d("OAUTH", "Auth Code: $authCode, Email: $email")
            
            if (authCode.isNotEmpty()) {
                onLoginSuccess(authCode, email)
            } else {
                isLoading = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(NeonDarkPurple, Color.Black)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "NEON EXPENSE",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp
            )
            Text(
                text = "Automate your wealth.",
                color = NeonPurple,
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(64.dp))
            
            if (isLoading) {
                CircularProgressIndicator(color = NeonPink)
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestServerAuthCode(serverClientId, true)
                            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.send"))
                            .build()
                            
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)
                        
                        // Always force account picker
                        googleSignInClient.signOut().addOnCompleteListener {
                            launcher.launch(googleSignInClient.signInIntent)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Secure Login with Google", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
