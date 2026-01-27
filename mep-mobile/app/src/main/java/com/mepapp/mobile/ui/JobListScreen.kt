package com.mepapp.mobile.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.mepapp.mobile.R
import com.mepapp.mobile.network.MepApiService
import com.mepapp.mobile.network.NetworkModule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(userId: String?, token: String?, onJobClick: (String) -> Unit, onLogsClick: () -> Unit) {
    var isApiConnected by remember { mutableStateOf(false) }
    var nextSyncIn by remember { mutableStateOf(5) }
    var isCheckingApi by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isOnline by remember { mutableStateOf(true) }
    var consecutiveFailures by remember { mutableStateOf(0) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkModule.createService<MepApiService>() }
    val authRepository = remember { com.mepapp.mobile.data.AuthRepository(context) }

    // Check internet connectivity
    fun checkInternetConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Monitor internet connectivity
    LaunchedEffect(Unit) {
        while (true) {
            isOnline = checkInternetConnection()
            delay(2000) // Check every 2 seconds
        }
    }

    // Check API health every 10 seconds
    LaunchedEffect(Unit) {
        while (true) {
            isCheckingApi = true
            try {
                if (token != null && isOnline) {
                    NetworkModule.setAuthToken(token)
                    apiService.getMe() // Simple health check
                    isApiConnected = true
                    consecutiveFailures = 0 // Reset on success
                }
            } catch (e: Exception) {
                isApiConnected = false
                consecutiveFailures++
                
                // If API fails 3 times in a row (30 seconds), logout and return to login
                if (consecutiveFailures >= 3) {
                    scope.launch {
                        authRepository.logout()
                        // User will be navigated to login by MainNavigation observing auth state
                    }
                }
            }
            isCheckingApi = false
            delay(10000) // Check every 10 seconds
        }
    }

    // Sync timer countdown (5 second intervals)
    LaunchedEffect(Unit) {
        while (true) {
            for (i in 5 downTo 1) {
                nextSyncIn = i
                delay(1000)
            }
            nextSyncIn = 5
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).padding(end = 8.dp)
                        )
                        Text("MEP Billing", fontWeight = FontWeight.Bold, color = Color.Black) 
                    }
                },
                actions = {
                    // Status Indicators in Top Right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        // Call Logs Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            onClick = onLogsClick,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "\uD83D\uDCDE",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 16.sp
                            )
                        }

                        // Sync Timer
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "${nextSyncIn}s",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                        
                        // API Status Indicator (Red/Green Dot)
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isApiConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                    shape = CircleShape
                                )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                )
            )
        },
        floatingActionButton = {
            if (isOnline) {
                FloatingActionButton(
                    onClick = { webView?.reload() },
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color.White
                ) {
                    Text("🔄", fontSize = 24.sp)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!isOnline) {
                // Show offline animation
                NoInternetScreen()
            } else {
                // WebView showing billing page
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                
                                // Fix viewport and scaling issues
                                layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
                                setInitialScale(0) // Let WebView auto-scale
                            }
                            
                            // Enable vertical scrolling
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = true
                            
                            loadUrl("https://maduraielectriciansandplumbers.com/billing/")
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Force layout update when content changes
                        view.requestLayout()
                    }
                )
            }
        }
    }
}

@Composable
fun NoInternetScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated WiFi icon (using emoji)
        Text(
            text = "📡",
            fontSize = 80.sp,
            modifier = Modifier.scale(scale)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Internet Connection",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Please turn on your internet connection",
            fontSize = 16.sp,
            color = Color.Gray
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Animated loading indicator
        CircularProgressIndicator(
            color = Color(0xFF38BDF8),
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Waiting for connection...",
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
    }
}
