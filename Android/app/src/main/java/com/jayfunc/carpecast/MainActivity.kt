package com.jayfunc.carpecast

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != currentTheme) {
            AppCompatDelegate.setDefaultNightMode(currentTheme)
        }

        enableEdgeToEdge()
        setContent {
            val isDark = when (currentTheme) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> isSystemInDarkTheme()
            }
            CarpeCastTheme(darkTheme = isDark) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this, MediaSyncService::class.java))
        sendBroadcast(Intent("com.jayfunc.carpecast.RELOAD_SETTINGS").apply {
            setPackage(packageName)
        })
    }
}

@Composable
fun CarpeCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val navController = rememberNavController()
    val mediaState by MediaStateRepository.currentState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController, startDestination = "main", modifier = Modifier.fillMaxSize()
    ) {
        composable(
            "main",
            enterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(tween(300)) },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(
                    tween(
                        300
                    )
                )
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(
                    tween(
                        300
                    )
                )
            }) {
            Scaffold(topBar = {
                @OptIn(ExperimentalMaterial3Api::class) TopAppBar(
                    title = {
                        val titleRes = when (selectedTab) {
                            0 -> R.string.nav_player
                            1 -> R.string.nav_devices
                            2 -> R.string.settings_title
                            else -> R.string.app_name
                        }
                        Text(stringResource(titleRes))
                    })
            }, bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_play), contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_player)) },
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 })
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_devices),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_devices)) },
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 })
                    NavigationBarItem(
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 })
                }
            }) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(200),
                        label = "TabCrossfade"
                    ) { tab ->
                        when (tab) {
                            0 -> PlayerScreen(mediaState)
                            1 -> DevicesScreen(mediaState)
                            2 -> SettingsScreen(onNavigateToSources = { navController.navigate("sources") })
                        }
                    }
                }
            }
        }
        composable(
            "sources",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(300)) }) {
            AllowedSourcesScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(state: MediaState) {
    val context = LocalContext.current
    val trackId = "${state.title}-${state.artist}"

    var hasPermission by remember { mutableStateOf(true) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val listeners = Settings.Secure.getString(
                    context.contentResolver, "enabled_notification_listeners"
                )
                hasPermission = listeners?.contains(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!hasPermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clip(CardDefaults.shape)
                        .clickable {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.grant_service_permission),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }



            Crossfade(
                targetState = trackId, animationSpec = tween(500), label = "TrackCrossfade",
                modifier = Modifier.weight(1f)
            ) { _ ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(0.8f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)) {
                            if (state.albumArt != null) {
                                Image(
                                    bitmap = state.albumArt!!.asImageBitmap(),
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = MaterialTheme.colorScheme.primary,
                                            spotColor = MaterialTheme.colorScheme.primary
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_album_placeholder),
                                        contentDescription = "Placeholder",
                                        modifier = Modifier.size(96.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            if (state.packageName != null) {
                                val pm = context.packageManager
                                val appDetails = remember(state.packageName) {
                                    try {
                                        val appInfo = pm.getApplicationInfo(state.packageName, 0)
                                        val appLabel = pm.getApplicationLabel(appInfo).toString()
                                        val icon = pm.getApplicationIcon(appInfo).toBitmap()
                                            .asImageBitmap()
                                        Pair(appLabel, icon)
                                    } catch (e: PackageManager.NameNotFoundException) {
                                        null
                                    }
                                }

                                if (appDetails != null) {
                                    val (appLabel, icon) = appDetails
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp)
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                val intent =
                                                    pm.getLaunchIntentForPackage(state.packageName)
                                                if (intent != null) {
                                                    context.startActivity(intent)
                                                }
                                            },
                                        shadowElevation = 0.dp
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 6.dp
                                            )
                                        ) {
                                            Image(
                                                bitmap = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = appLabel,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(96.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.Center
                    ) {
                        FadingMarqueeText(
                            text = state.title.ifEmpty { stringResource(R.string.no_media_playing) },
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FadingMarqueeText(
                            text = state.artist.ifEmpty { stringResource(R.string.unknown_artist) },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.album.isNotEmpty()) {
                            FadingMarqueeText(
                                text = state.album,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth(0.8f)) {
                val progress =
                    if (state.duration > 0) state.position.toFloat() / state.duration else 0f
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(state.position),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatTime(state.duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { sendCommand(context, "ACTION_PREV") },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Prev",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { sendCommand(context, "ACTION_PLAY_PAUSE") },
                    shadowElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { sendCommand(context, "ACTION_NEXT") },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun DevicesScreen(state: MediaState) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (state.availablePcs.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_desktop),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.waiting_for_pc),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(state.availablePcs) { index, ip ->
                    val pcInfo = state.pcInfoMap[ip]
                    val pcName = pcInfo?.name ?: "Unknown PC"
                    val osVersion = pcInfo?.osVersion ?: "Windows"
                    val deviceType = pcInfo?.type ?: "Desktop"
                    val isSelected = ip == state.selectedPcIp

                    val iconRes = if (deviceType.equals(
                            "Laptop", true
                        )
                    ) R.drawable.ic_laptop else if (deviceType.equals(
                            "Phone", true
                        )
                    ) R.drawable.ic_devices else R.drawable.ic_desktop

                    val position = when {
                        state.availablePcs.size == 1 -> SettingItemPosition.Single
                        index == 0 -> SettingItemPosition.Top
                        index == state.availablePcs.size - 1 -> SettingItemPosition.Bottom
                        else -> SettingItemPosition.Middle
                    }
                    val shape = when (position) {
                        SettingItemPosition.Top -> RoundedCornerShape(
                            topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp
                        )

                        SettingItemPosition.Middle -> RoundedCornerShape(4.dp)
                        SettingItemPosition.Bottom -> RoundedCornerShape(
                            topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp
                        )

                        SettingItemPosition.Single -> RoundedCornerShape(24.dp)
                    }

                    Card(
                        shape = shape, colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = 0.5f
                            )
                        ), modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .clickable {
                                if (!isSelected) {
                                    val intent =
                                        Intent(context, MediaSyncService::class.java).apply {
                                            action = "ACTION_SELECT_PC"
                                            putExtra("ip", ip)
                                        }
                                    context.startService(intent)
                                }
                            }) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 8.dp, end = 8.dp)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(text = pcName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "$osVersion • $ip",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                OutlinedButton(
                                    onClick = {
                                        val intent =
                                            Intent(context, MediaSyncService::class.java).apply {
                                                action = "ACTION_DISCONNECT_PC"
                                            }
                                        context.startService(intent)
                                    }) {
                                    Text(
                                        stringResource(R.string.disconnect),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateToSources: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var deviceName by remember {
        mutableStateOf(
            prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        )
    }
    var discoveryPort by remember { mutableIntStateOf(prefs.getInt("discovery_port", 5001)) }
    var commandPort by remember { mutableIntStateOf(prefs.getInt("command_port", 5002)) }

    var currentTheme by remember {
        mutableIntStateOf(
            prefs.getInt(
                "theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        )
    }
    var currentLanguageTag by remember {
        mutableStateOf(
            AppCompatDelegate.getApplicationLocales().get(0)?.language ?: ""
        )
    }

    var showNameDialog by remember { mutableStateOf(false) }
    var showDiscoveryPortDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "CarpeCast",
                style = MaterialTheme.typography.titleLarge
            )
            val uriHandler = LocalUriHandler.current
            val annotatedString = buildAnnotatedString {
                append("v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_DATE} - ")
                pushStringAnnotation(
                    tag = "URL",
                    annotation = "https://github.com/jayfunc/CarpeCast/commit/${BuildConfig.GIT_HASH}"
                )
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(BuildConfig.GIT_HASH)
                }
                pop()
                append(")")
            }
            ClickableText(
                text = annotatedString,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            var themeExpanded by remember { mutableStateOf(false) }
            val themeOptions = listOf(
                stringResource(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                stringResource(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
                stringResource(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES
            )

            SettingItem(
                title = stringResource(R.string.settings_theme),
                value = themeOptions.find { it.second == currentTheme }?.first
                    ?: stringResource(R.string.theme_system),
                icon = R.drawable.ic_settings,
                position = SettingItemPosition.Top
            ) { themeExpanded = true }

            if (themeExpanded) {
                SelectionDialog(
                    title = stringResource(R.string.settings_theme),
                    options = themeOptions,
                    selectedValue = currentTheme,
                    onDismiss = { themeExpanded = false },
                    onSelect = { selected ->
                        prefs.edit().putInt("theme_mode", selected).apply()
                        currentTheme = selected
                        AppCompatDelegate.setDefaultNightMode(selected)
                        themeExpanded = false
                    })
            }

            var langExpanded by remember { mutableStateOf(false) }
            val langOptions = listOf(
                stringResource(R.string.language_system) to "",
                stringResource(R.string.language_english) to "en",
                stringResource(R.string.language_chinese) to "zh",
                stringResource(R.string.language_chinese_traditional) to "zh-Hant",
                stringResource(R.string.language_japanese) to "ja"
            )

            SettingItem(
                title = stringResource(R.string.settings_language),
                value = langOptions.find { it.second == currentLanguageTag }?.first
                    ?: stringResource(R.string.language_system),
                icon = R.drawable.ic_language,
                position = SettingItemPosition.Bottom
            ) { langExpanded = true }

            if (langExpanded) {
                SelectionDialog(
                    title = stringResource(R.string.settings_language),
                    options = langOptions,
                    selectedValue = currentLanguageTag,
                    onDismiss = { langExpanded = false },
                    onSelect = { selected ->
                        currentLanguageTag = selected
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(
                                selected
                            )
                        )
                        langExpanded = false
                    })
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_allowed_sources)) {
            SettingItem(
                title = stringResource(R.string.settings_allowed_sources),
                desc = stringResource(R.string.settings_allowed_sources_desc),
                icon = R.drawable.ic_music
            ) { onNavigateToSources() }
        }

        SettingsGroup(title = stringResource(R.string.settings_network)) {
            val uiModeManager =
                context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            val isTv =
                uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            val isTablet =
                (context.resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
            val deviceIcon =
                if (isTv) R.drawable.ic_desktop else if (isTablet) R.drawable.ic_laptop else R.drawable.ic_devices

            SettingItem(
                title = stringResource(R.string.settings_device_name),
                value = deviceName,
                icon = deviceIcon,
                position = SettingItemPosition.Top
            ) { showNameDialog = true }

            SettingItem(
                title = stringResource(R.string.settings_discovery_port),
                desc = stringResource(R.string.settings_discovery_port_desc),
                value = discoveryPort.toString(),
                icon = R.drawable.ic_settings,
                position = SettingItemPosition.Bottom
            ) { showDiscoveryPortDialog = true }
        }

        SettingsGroup(title = stringResource(R.string.settings_recommended)) {
            SettingItem(
                title = stringResource(R.string.settings_download_windows_title),
                desc = stringResource(R.string.settings_download_windows_desc),
                icon = R.drawable.ic_carpecast_logo,
                tintIcon = false,
                secondaryIcon = R.drawable.ic_windows11_logo,
                tintSecondaryIcon = false,
                position = SettingItemPosition.Top
            ) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/jayfunc/CarpeCast")
                )
                context.startActivity(intent)
            }

            SettingItem(
                title = "BetterLyrics",
                desc = stringResource(R.string.settings_betterlyrics_desc),
                icon = R.drawable.ic_betterlyrics_logo,
                tintIcon = false,
                position = SettingItemPosition.Bottom
            ) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/jayfunc/BetterLyrics")
                )
                context.startActivity(intent)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showNameDialog) {
        InputDialog(
            title = stringResource(R.string.settings_device_name),
            initialValue = deviceName,
            defaultValue = android.os.Build.MODEL,
            onDismiss = { showNameDialog = false },
            onConfirm = {
                prefs.edit().putString("device_name", it).apply()
                deviceName = it
                context.sendBroadcast(Intent("com.jayfunc.carpecast.RELOAD_SETTINGS").apply {
                    setPackage(
                        context.packageName
                    )
                })
                showNameDialog = false
            })
    }

    if (showDiscoveryPortDialog) {
        InputDialog(
            title = stringResource(R.string.settings_discovery_port),
            initialValue = discoveryPort.toString(),
            isNumber = true,
            onDismiss = { showDiscoveryPortDialog = false },
            onConfirm = {
                val port = it.toIntOrNull()
                if (port != null && port in 1..65535) {
                    prefs.edit().putInt("discovery_port", port).apply()
                    discoveryPort = port
                    context.sendBroadcast(Intent("com.jayfunc.carpecast.RELOAD_SETTINGS").apply {
                        setPackage(
                            context.packageName
                        )
                    })
                }
                showDiscoveryPortDialog = false
            })
    }


}

@Composable
fun <T> SelectionDialog(
    title: String,
    options: List<Pair<String, T>>,
    selectedValue: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = value == selectedValue, onClick = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FadingMarqueeText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color = Color.Unspecified
) {
    BoxWithConstraints {
        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
        val textWidth = textMeasurer.measure(
            text = text,
            style = style,
            maxLines = 1
        ).size.width

        val isOverflowing = textWidth > constraints.maxWidth

        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            modifier = Modifier
                .then(
                    if (isOverflowing) {
                        Modifier
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        0.05f to Color.Black,
                                        0.95f to Color.Black,
                                        1f to Color.Transparent
                                    ), blendMode = BlendMode.DstIn
                                )
                            }
                    } else Modifier
                )
                .basicMarquee()
        )
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

enum class SettingItemPosition { Top, Middle, Bottom, Single }

@Composable
fun SettingItem(
    title: String,
    desc: String? = null,
    value: String? = null,
    icon: Int,
    tintIcon: Boolean = true,
    secondaryIcon: Int? = null,
    tintSecondaryIcon: Boolean = true,
    position: SettingItemPosition = SettingItemPosition.Single,
    onClick: () -> Unit
) {
    val subtitle = if (value != null && desc != null) "$value\n$desc" else value ?: desc
    val shape = when (position) {
        SettingItemPosition.Top -> RoundedCornerShape(
            topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp
        )

        SettingItemPosition.Middle -> RoundedCornerShape(4.dp)
        SettingItemPosition.Bottom -> RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp
        )

        SettingItemPosition.Single -> RoundedCornerShape(24.dp)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() }) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = if (subtitle != null) {
                {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else null,
            leadingContent = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                ) {
                    if (tintIcon) {
                        Icon(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(id = icon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (secondaryIcon != null) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            val secModifier = Modifier.size(12.dp).offset(x = 4.dp, y = 4.dp)
                            if (tintSecondaryIcon) {
                                Icon(
                                    painter = painterResource(id = secondaryIcon),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = secModifier
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = secondaryIcon),
                                    contentDescription = null,
                                    modifier = secModifier
                                )
                            }
                        }
                    }
                }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedSourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val pm = context.packageManager

    var mediaApps by remember {
        mutableStateOf<List<Triple<String, String, androidx.compose.ui.graphics.ImageBitmap>>>(
            emptyList()
        )
    }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val apps = pm.getInstalledPackages(PackageManager.GET_META_DATA).filter {
                it.applicationInfo != null && pm.getLaunchIntentForPackage(it.packageName) != null
            }.map {
                val appInfo = it.applicationInfo!!
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo).toBitmap().asImageBitmap()
                Triple(it.packageName, label, icon)
            }.sortedBy { it.second }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                mediaApps = apps
                isLoading = false
            }
        }
    }

    var allowAll by remember { mutableStateOf(prefs.getBoolean("allow_all_sources", true)) }
    var allowedSet by remember {
        mutableStateOf(
            prefs.getStringSet("allowed_sources", emptySet()) ?: emptySet()
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val updateSources: (Boolean, Set<String>) -> Unit = { newAllowAll, newAllowedSet ->
        allowAll = newAllowAll
        allowedSet = newAllowedSet
        val finalSet = if (newAllowedSet.size == mediaApps.size) emptySet() else newAllowedSet
        val finalAllowAll = if (newAllowedSet.size == mediaApps.size) true else newAllowAll
        prefs.edit().putBoolean("allow_all_sources", finalAllowAll)
            .putStringSet("allowed_sources", finalSet).apply()
        context.sendBroadcast(Intent("com.jayfunc.carpecast.RELOAD_SETTINGS").apply {
            setPackage(
                context.packageName
            )
        })
    }

    val filteredApps = remember(mediaApps, searchQuery) {
        mediaApps.filter {
            searchQuery.isEmpty() || it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                AnimatedContent(
                    targetState = isSearchExpanded, label = "SearchTitleAnimation"
                ) { expanded ->
                    if (expanded) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(stringResource(R.string.settings_allowed_sources))
                    }
                }
            }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }, actions = {
                AnimatedContent(
                    targetState = isSearchExpanded, label = "SearchActionsAnimation"
                ) { expanded ->
                    if (expanded) {
                        IconButton(onClick = { isSearchExpanded = false; searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Search"
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSearchExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search"
                                )
                            }
                        }
                    }
                }
            })
        }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            val newAllowAll = !allowAll
                            val newSet = if (newAllowAll) emptySet() else allowedSet
                            updateSources(newAllowAll, newSet)
                        }) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                stringResource(R.string.allow_all_sources),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        leadingContent = {
                            Checkbox(
                                checked = allowAll, onCheckedChange = { checked ->
                                    val newSet = if (checked) emptySet() else allowedSet
                                    updateSources(checked, newSet)
                                })
                        })
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = filteredApps, key = { _, app -> app.first }) { index, app ->
                        val (pkgName, label, icon) = app
                        val isChecked = allowAll || allowedSet.contains(pkgName)

                        val position = when {
                            filteredApps.size == 1 -> SettingItemPosition.Single
                            index == 0 -> SettingItemPosition.Top
                            index == filteredApps.size - 1 -> SettingItemPosition.Bottom
                            else -> SettingItemPosition.Middle
                        }
                        val shape = when (position) {
                            SettingItemPosition.Top -> RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 4.dp
                            )

                            SettingItemPosition.Middle -> RoundedCornerShape(4.dp)
                            SettingItemPosition.Bottom -> RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 4.dp,
                                bottomStart = 24.dp,
                                bottomEnd = 24.dp
                            )

                            SettingItemPosition.Single -> RoundedCornerShape(24.dp)
                        }

                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .clickable {
                                    val newSet = allowedSet.toMutableSet()
                                    if (!isChecked) newSet.add(pkgName) else newSet.remove(pkgName)
                                    updateSources(false, newSet)
                                }) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(label) },
                                supportingContent = {
                                    Text(
                                        pkgName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isChecked, onCheckedChange = { checked ->
                                                val newSet = allowedSet.toMutableSet()
                                                if (checked) newSet.add(pkgName) else newSet.remove(
                                                    pkgName
                                                )
                                                updateSources(false, newSet)
                                            })
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Image(
                                            bitmap = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                })
                        }
                    }
                }
            } // Close else block
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    initialValue: String,
    defaultValue: String? = null,
    isNumber: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
                )
                if (defaultValue != null) {
                    TextButton(
                        onClick = { text = defaultValue },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.restore_default))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

private fun sendCommand(context: Context, actionStr: String) {
    val intent = Intent(context, MediaSyncService::class.java).apply {
        action = actionStr
    }
    context.startService(intent)
}
