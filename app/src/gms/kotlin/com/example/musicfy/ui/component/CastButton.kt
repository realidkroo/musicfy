// castbutton kt
// what is this for you ask its for cast button ofc

package com.example.musicfy.ui.component

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.example.musicfy.LocalPlayerConnection
import com.example.musicfy.R
import com.example.musicfy.constants.EnableGoogleCastKey
import com.example.musicfy.utils.rememberPreference
import timber.log.Timber

// a composable cast button that shows available cast devices uses the app s menustate to show a styled bottom sheet
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    
    var castAvailable by remember { mutableStateOf(false) }
    var mediaRouter by remember { mutableStateOf<MediaRouter?>(null) }
    var routeSelector by remember { mutableStateOf<MediaRouteSelector?>(null) }
    var availableRoutes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
    
    val (enableGoogleCast) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )
    
    // get cast state from service
    val castHandler = playerConnection?.service?.castConnectionHandler
    val isCasting by castHandler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val isConnecting by castHandler?.isConnecting?.collectAsState() ?: remember { mutableStateOf(false) }
    val castDeviceName by castHandler?.castDeviceName?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // get current media metadata
    val currentMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

    // check if cast is available and disconnect if disabled while casting
    LaunchedEffect(enableGoogleCast) {
        if (!enableGoogleCast) {
            // disconnect from cast if currently casting
            if (isCasting) {
                playerConnection?.service?.castConnectionHandler?.disconnect()
            }
            castAvailable = false
            mediaRouter = null
            routeSelector = null
            availableRoutes = emptyList()
            return@LaunchedEffect
        }
        try {
            CastContext.getSharedInstance(context)
            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()
            // reinitialize the cast handler to ensure it s ready
            playerConnection?.service?.castConnectionHandler?.initialize()
            castAvailable = true
        } catch (e: Exception) {
            Timber.d("Cast not available: ${e.message}")
            castAvailable = false
        }
    }
    
    // listen for route changes to discover devices
    DisposableEffect(mediaRouter, routeSelector) {
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes(router, routeSelector) { availableRoutes = it }
            }
            
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes(router, routeSelector) { availableRoutes = it }
            }
            
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes(router, routeSelector) { availableRoutes = it }
            }
        }
        
        routeSelector?.let { selector ->
            mediaRouter?.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            // initial update
            updateRoutes(mediaRouter, selector) { availableRoutes = it }
        }
        
        onDispose {
            mediaRouter?.removeCallback(callback)
        }
    }

    // show the button if cast is enabled and sdk is available
    if (enableGoogleCast && castAvailable) {
        Box(
            modifier = modifier
        ) {
            // shadow background for cast button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // cast button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                    if (currentMetadata == null && !isCasting) {
                        Toast.makeText(context, "Play a song first to cast", Toast.LENGTH_SHORT).show()
                        return@clickable
                    }
                    
                    // get current connected route if casting
                    val currentRoute = if (isCasting) {
                        mediaRouter?.routes?.find { route ->
                            routeSelector?.let { selector -> 
                                route.matchesSelector(selector) && route.isSelected
                            } == true
                        }
                    } else null
                    
                    // show bottom sheet with cast picker
                    menuState.show {
                        CastPickerSheet(
                            routes = availableRoutes,
                            isConnecting = isConnecting,
                            currentlyConnectedRoute = currentRoute,
                            onRouteSelected = { route ->
                                castHandler?.connectToRoute(route)
                                menuState.dismiss()
                            },
                            onDisconnect = {
                                castHandler?.disconnect()
                                menuState.dismiss()
                            }
                        )
                    }
                }
            ) {
                Image(
                    painter = painterResource(
                        if (isCasting) R.drawable.cast_connected else R.drawable.cast
                    ),
                    contentDescription = if (isCasting) "Stop casting" else "Cast",
                    colorFilter = ColorFilter.tint(
                        if (isCasting) MaterialTheme.colorScheme.primary else tintColor
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun updateRoutes(
    router: MediaRouter?,
    selector: MediaRouteSelector?,
    onUpdate: (List<MediaRouter.RouteInfo>) -> Unit
) {
    if (router == null || selector == null) {
        onUpdate(emptyList())
        return
    }
    val routes = router.routes.filter { route ->
        route.matchesSelector(selector) && !route.isDefault
    }
    onUpdate(routes)
}
