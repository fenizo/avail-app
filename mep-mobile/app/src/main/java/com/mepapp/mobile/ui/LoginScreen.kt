package com.mepapp.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import android.util.Log
import com.mepapp.mobile.data.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(authRepository: AuthRepository, onLoginSuccess: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            onLoginSuccess()
        } else {
            onLoginSuccess()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF334155), // Lighter Slate for visibility
        unfocusedContainerColor = Color(0xFF334155),
        disabledContainerColor = Color(0xFF334155),
        cursorColor = Color(0xFF38BDF8),
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color.White, // Explicit White border
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color.LightGray
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("MEP Field Service", fontSize = 28.sp, style = MaterialTheme.typography.headlineLarge)
        Text("Enter your credentials to continue", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10) phone = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            colors = textFieldColors
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 4) pin = it },
            label = { Text("4-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            colors = textFieldColors
        )

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                isLoading = true
                scope.launch {
                    try {
                        isLoading = true
                        errorMessage = null
                        authRepository.login(phone, pin)
                        // Request permission after login validation
                        permissionLauncher.launch(android.Manifest.permission.READ_CALL_LOG)
                    } catch (e: Exception) {
                        Log.e("LoginScreen", "Login failed", e)
                        errorMessage = "Login Failed: ${e.message ?: "Unknown error"}"
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading && phone.length >= 10 && pin.length == 4
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Login")
            }
        }
    }
}
