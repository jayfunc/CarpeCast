package com.jayfunc.carpecast

import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.transition.Fade
import android.transition.TransitionManager
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var textTitle: TextView
    private lateinit var textArtist: TextView
    private lateinit var textAlbum: TextView
    private lateinit var textTime: TextView
    private lateinit var textPlaybackState: TextView
    private lateinit var textAppName: TextView
    private lateinit var chipStatus: Chip
    private lateinit var listDevices: LinearLayout
    private lateinit var scrollDevices: ScrollView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var btnPermission: Button
    private lateinit var imageAlbumArt: ImageView
    private lateinit var imageAppIcon: ImageView
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnPrev: MaterialButton



    private lateinit var layoutPlayer: View
    private lateinit var layoutDevices: View
    private lateinit var layoutSettings: View
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var textThemeValue: TextView
    private lateinit var textLanguageValue: TextView

    private var currentTrackId = ""
    private var currentAlbumArt: android.graphics.Bitmap? = null
    
    private var lastAvailablePcs: List<String> = emptyList()
    private var lastSelectedPcIp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved theme before setContentView
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != currentTheme) {
            AppCompatDelegate.setDefaultNightMode(currentTheme)
        }

        // Enable Edge-to-Edge and Dynamic Colors
        WindowCompat.setDecorFitsSystemWindows(window, false)
        DynamicColors.applyToActivityIfAvailable(this)
        
        setContentView(R.layout.activity_main)

        // Adjust for system insets (status bar, nav bar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContainer)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        layoutPlayer = findViewById(R.id.layoutPlayer)
        layoutDevices = findViewById(R.id.layoutDevices)
        layoutSettings = findViewById(R.id.layoutSettings)
        bottomNav = findViewById(R.id.bottomNav)

        textTitle = findViewById(R.id.textTitle)
        textArtist = findViewById(R.id.textArtist)
        textAlbum = findViewById(R.id.textAlbum)
        textTime = findViewById(R.id.textTime)
        textPlaybackState = findViewById(R.id.textPlaybackState)
        textAppName = findViewById(R.id.textAppName)
        chipStatus = findViewById(R.id.chipStatus)
        listDevices = findViewById(R.id.listDevices)
        scrollDevices = findViewById(R.id.scrollDevices)
        progressIndicator = findViewById(R.id.progressIndicator)
        btnPermission = findViewById(R.id.btnPermission)
        imageAlbumArt = findViewById(R.id.imageAlbumArt)
        imageAppIcon = findViewById(R.id.imageAppIcon)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        


        textThemeValue = findViewById(R.id.textThemeValue)
        textLanguageValue = findViewById(R.id.textLanguageValue)

        // Ensure layouts match restored bottomNav state
        when (bottomNav.selectedItemId) {
            R.id.nav_player -> {
                layoutPlayer.visibility = View.VISIBLE
                layoutDevices.visibility = View.GONE
                layoutSettings.visibility = View.GONE
            }
            R.id.nav_devices -> {
                layoutPlayer.visibility = View.GONE
                layoutDevices.visibility = View.VISIBLE
                layoutSettings.visibility = View.GONE
            }
            R.id.nav_settings -> {
                layoutPlayer.visibility = View.GONE
                layoutDevices.visibility = View.GONE
                layoutSettings.visibility = View.VISIBLE
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            val container = findViewById<ViewGroup>(R.id.fragmentContainer)
            val transition = Fade()
            transition.duration = 200
            TransitionManager.beginDelayedTransition(container, transition)
            
            when (item.itemId) {
                R.id.nav_player -> {
                    layoutPlayer.visibility = View.VISIBLE
                    layoutDevices.visibility = View.GONE
                    layoutSettings.visibility = View.GONE
                    true
                }
                R.id.nav_devices -> {
                    layoutPlayer.visibility = View.GONE
                    layoutDevices.visibility = View.VISIBLE
                    layoutSettings.visibility = View.GONE
                    true
                }
                R.id.nav_settings -> {
                    layoutPlayer.visibility = View.GONE
                    layoutDevices.visibility = View.GONE
                    layoutSettings.visibility = View.VISIBLE
                    true
                }
                else -> false
            }
        }

        btnPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Set up local controls
        btnPlayPause.setOnClickListener { sendCommand("ACTION_PLAY_PAUSE") }
        btnNext.setOnClickListener { sendCommand("ACTION_NEXT") }
        btnPrev.setOnClickListener { sendCommand("ACTION_PREV") }

        // Start listening to the StateFlow repository
        lifecycleScope.launch {
            MediaStateRepository.currentState.collect { state ->
                updateUi(state)
            }
        }

        setupSettings()
        checkBatteryOptimizations()
    }

    private fun setupSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val currentTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        textThemeValue.text = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        val locales = AppCompatDelegate.getApplicationLocales()
        textLanguageValue.text = if (locales.isEmpty) {
            getString(R.string.language_system)
        } else {
            val tag = locales.get(0)?.language
            if (tag == "zh") getString(R.string.language_chinese) else getString(R.string.language_english)
        }

        findViewById<View>(R.id.cardTheme).setOnClickListener {
            val themes = arrayOf(getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark))
            val values = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
            val currentIdx = values.indexOf(currentTheme).takeIf { it >= 0 } ?: 0

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(themes, currentIdx) { dialog, which ->
                    prefs.edit().putInt("theme_mode", values[which]).apply()
                    AppCompatDelegate.setDefaultNightMode(values[which])
                    dialog.dismiss()
                }
                .show()
        }

        findViewById<View>(R.id.cardLanguage).setOnClickListener {
            val langs = arrayOf(getString(R.string.language_system), getString(R.string.language_english), getString(R.string.language_chinese))
            val currentIdx = if (locales.isEmpty) 0 else if (locales.get(0)?.language == "zh") 2 else 1

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(langs, currentIdx) { dialog, which ->
                    val tag = when (which) {
                        1 -> "en"
                        2 -> "zh"
                        else -> ""
                    }
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    dialog.dismiss()
                }
                .show()
        }

        val textDeviceNameValue = findViewById<TextView>(R.id.textDeviceNameValue)
        val textDiscoveryPortValue = findViewById<TextView>(R.id.textDiscoveryPortValue)
        val textCommandPortValue = findViewById<TextView>(R.id.textCommandPortValue)

        var deviceName = prefs.getString("device_name", null)
        if (deviceName.isNullOrEmpty()) {
            deviceName = android.provider.Settings.Global.getString(contentResolver, android.provider.Settings.Global.DEVICE_NAME)
            if (deviceName.isNullOrEmpty()) {
                deviceName = android.os.Build.MODEL
            }
            if (deviceName.isNullOrEmpty()) {
                deviceName = "Android Device"
            }
            prefs.edit().putString("device_name", deviceName).apply()
        }
        val discoveryPort = prefs.getInt("discovery_port", 5001)
        val commandPort = prefs.getInt("command_port", 5002)

        textDeviceNameValue.text = deviceName
        textDiscoveryPortValue.text = discoveryPort.toString()
        textCommandPortValue.text = commandPort.toString()

        findViewById<View>(R.id.cardDeviceName).setOnClickListener {
            showInputSettingsDialog(R.string.settings_device_name, deviceName) { newValue ->
                if (newValue.isNotBlank()) {
                    prefs.edit().putString("device_name", newValue).apply()
                    textDeviceNameValue.text = newValue
                }
            }
        }

        findViewById<View>(R.id.cardDiscoveryPort).setOnClickListener {
            showInputSettingsDialog(R.string.settings_discovery_port, discoveryPort.toString(), true) { newValue ->
                newValue.toIntOrNull()?.let { newPort ->
                    if (newPort != discoveryPort) {
                        prefs.edit().putInt("discovery_port", newPort).apply()
                        restartApp()
                    }
                }
            }
        }

        findViewById<View>(R.id.cardCommandPort).setOnClickListener {
            showInputSettingsDialog(R.string.settings_command_port, commandPort.toString(), true) { newValue ->
                newValue.toIntOrNull()?.let { newPort ->
                    if (newPort != commandPort) {
                        prefs.edit().putInt("command_port", newPort).apply()
                        restartApp()
                    }
                }
            }
        }

        findViewById<View>(R.id.cardAllowedSources).setOnClickListener {
            showAllowedSourcesDialog()
        }
    }

    private fun showAllowedSourcesDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val allowAll = prefs.getBoolean("allow_all_sources", true)
        val allowedSet = prefs.getStringSet("allowed_sources", emptySet())?.toMutableSet() ?: mutableSetOf()

        val pm = packageManager
        
        val intent1 = Intent(Intent.ACTION_MEDIA_BUTTON)
        val list1 = pm.queryBroadcastReceivers(intent1, 0)
        
        val intent2 = Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE)
        val list2 = pm.queryIntentServices(intent2, 0)
        
        val packageSet = mutableSetOf<String>()
        list1.forEach { packageSet.add(it.activityInfo.packageName) }
        list2.forEach { packageSet.add(it.serviceInfo.packageName) }
        packageSet.addAll(allowedSet)
        
        try {
            val mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val componentName = android.content.ComponentName(this, MediaSyncService::class.java)
            val activeSessions = mediaSessionManager.getActiveSessions(componentName)
            activeSessions.forEach { packageSet.add(it.packageName) }
        } catch (e: Exception) {
            // Ignore if permission not granted
        }
        
        val appList = packageSet.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                Pair(pkg, pm.getApplicationLabel(appInfo).toString())
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.second }

        val labels = appList.map { it.second }.toTypedArray()
        val pkgs = appList.map { it.first }
        
        val checkedItems = BooleanArray(appList.size) { i ->
            allowedSet.contains(pkgs[i])
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val px = (24 * resources.displayMetrics.density).toInt()
            setPadding(px, px/2, px, px/2)
        }
        
        val switchAllowAll = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = getString(R.string.allow_all_sources)
            isChecked = allowAll
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
        }
        container.addView(switchAllowAll)
        
        val listView = android.widget.ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_multiple_choice, labels)
            for (i in checkedItems.indices) {
                setItemChecked(i, checkedItems[i])
            }
            isEnabled = !allowAll
            alpha = if (allowAll) 0.5f else 1.0f
        }
        
        switchAllowAll.setOnCheckedChangeListener { _, isChecked ->
            listView.isEnabled = !isChecked
            listView.alpha = if (isChecked) 0.5f else 1.0f
        }
        
        if (appList.isEmpty()) {
            listView.visibility = View.GONE
            val noApps = TextView(this).apply {
                text = getString(R.string.no_media_apps_found)
                setPadding(0, 32, 0, 0)
            }
            container.addView(noApps)
        } else {
            container.addView(listView)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_allowed_sources_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newAllowAll = switchAllowAll.isChecked
                val newAllowedSet = mutableSetOf<String>()
                val checkedPositions = listView.checkedItemPositions
                for (i in 0 until appList.size) {
                    if (checkedPositions.get(i)) {
                        newAllowedSet.add(pkgs[i])
                    }
                }
                prefs.edit()
                    .putBoolean("allow_all_sources", newAllowAll)
                    .putStringSet("allowed_sources", newAllowedSet)
                    .apply()
                    
                sendBroadcast(Intent("com.jayfunc.carpecast.RELOAD_SETTINGS"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInputSettingsDialog(titleResId: Int, defaultValue: String, isNumber: Boolean = false, onSave: (String) -> Unit) {
        val textInputLayout = com.google.android.material.textfield.TextInputLayout(
            this, null, com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val margin = (20 * resources.displayMetrics.density).toInt()
                setMargins(margin, (16 * resources.displayMetrics.density).toInt(), margin, 0)
            }
        }

        val input = com.google.android.material.textfield.TextInputEditText(textInputLayout.context).apply {
            setText(defaultValue)
            if (isNumber) {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            setSelection(defaultValue.length)
        }

        textInputLayout.addView(input)

        val container = android.widget.FrameLayout(this)
        container.addView(textInputLayout)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(titleResId)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                onSave(input.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    private fun checkBatteryOptimizations() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun sendCommand(action: String) {
        val intent = Intent(this, MediaSyncService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun updateUi(state: MediaState) {
        // Drop momentary blank states during track switches to prevent flash glitches,
        // BUT allow the very first initialization to pass through so the default UI renders correctly.
        if (state.title.isBlank() && state.artist.isBlank() && currentTrackId.isNotEmpty()) {
            return
        }

        val newTrackId = "${state.title}-${state.artist}"
        
        if (newTrackId != currentTrackId && currentTrackId.isNotEmpty()) {
            // Animate transition
            currentTrackId = newTrackId
            currentAlbumArt = state.albumArt
            animateTrackChange()
        } else if (newTrackId != currentTrackId) {
            // First load, no animation
            currentTrackId = newTrackId
            currentAlbumArt = state.albumArt
            updateTrackInfo(state)
        } else {
            // Same track. Check if album art arrived late.
            // We compare against null to avoid false positives from new Bitmap references
            val artJustArrived = currentAlbumArt == null && state.albumArt != null
            
            if (artJustArrived) {
                currentAlbumArt = state.albumArt
                imageAlbumArt.animate().cancel()
                imageAlbumArt.animate().alpha(0f).setDuration(150).withEndAction {
                    val latestState = MediaStateRepository.currentState.value
                    if (latestState.albumArt != null) {
                        imageAlbumArt.setImageBitmap(latestState.albumArt)
                    } else {
                        imageAlbumArt.setImageResource(R.drawable.ic_album_placeholder)
                    }
                    imageAlbumArt.animate().alpha(1f).setDuration(150).start()
                }.start()
            }
            // We intentionally do NOT call updateTrackInfo() here.
            // If the track is the same, the texts and package name are already correct.
            // This prevents expensive redundant PackageManager lookups and setImageBitmap calls.
        }

        updatePlaybackState(state)
    }

    private fun animateTrackChange() {
        val viewsToAnimate = listOf(textTitle, textArtist, textAlbum, imageAlbumArt, textAppName, imageAppIcon)
        val duration = 300L

        viewsToAnimate.forEach { it.animate().cancel() }

        // Fade out other views
        listOf(textArtist, textAlbum, imageAlbumArt, textAppName, imageAppIcon).forEach { view ->
            view.animate().alpha(0f).setDuration(duration / 2).start()
        }

        // Use textTitle as the master animator to trigger content swap ONLY ONCE
        textTitle.animate().alpha(0f).setDuration(duration / 2).withEndAction {
            updateTrackInfo(MediaStateRepository.currentState.value)
            
            // Fade all views back in
            viewsToAnimate.forEach { view ->
                view.animate().alpha(1f).setDuration(duration / 2).start()
            }
        }.start()
    }

    private fun updateTrackInfo(state: MediaState) {
        textTitle.text = state.title.ifEmpty { getString(R.string.no_media_playing) }
        textArtist.text = state.artist.ifEmpty { getString(R.string.unknown_artist) }
        textAlbum.text = state.album.ifEmpty { getString(R.string.unknown_album) }

        if (state.albumArt != null) {
            imageAlbumArt.setImageBitmap(state.albumArt)
        } else {
            imageAlbumArt.setImageResource(R.drawable.ic_album_placeholder)
        }

        if (state.packageName != null) {
            try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(state.packageName, 0)
                textAppName.text = pm.getApplicationLabel(appInfo)
                imageAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
                findViewById<View>(R.id.cardSourceApp).visibility = View.VISIBLE
            } catch (e: PackageManager.NameNotFoundException) {
                findViewById<View>(R.id.cardSourceApp).visibility = View.INVISIBLE
            }
        } else {
            findViewById<View>(R.id.cardSourceApp).visibility = View.INVISIBLE
        }
    }

    private fun updatePlaybackState(state: MediaState) {
        val isPlaying = state.isPlaying
        textPlaybackState.text = if (isPlaying) getString(R.string.status_playing) else getString(R.string.status_paused)
        
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        progressIndicator.max = if (state.duration > 0) state.duration.toInt() else 100
        progressIndicator.setProgressCompat(state.position.toInt(), true)
        
        textTime.text = "${formatTime(state.position)} / ${formatTime(state.duration)}"

        if (state.availablePcs.isEmpty()) {
            chipStatus.visibility = View.VISIBLE
            scrollDevices.visibility = View.GONE
            chipStatus.text = getString(R.string.waiting_for_pc)
            val colorWaiting = MaterialColors.getColor(chipStatus, com.google.android.material.R.attr.colorSurfaceVariant)
            chipStatus.chipBackgroundColor = ColorStateList.valueOf(colorWaiting)
            lastAvailablePcs = emptyList()
            lastSelectedPcIp = null
        } else {
            chipStatus.visibility = View.GONE
            scrollDevices.visibility = View.VISIBLE
            
            // Only re-populate list if the IPs have changed to prevent flickering and battery drain
            if (state.availablePcs != lastAvailablePcs || state.selectedPcIp != lastSelectedPcIp) {
                lastAvailablePcs = state.availablePcs
                lastSelectedPcIp = state.selectedPcIp
                
                listDevices.removeAllViews()
                for (ip in state.availablePcs) {
                    val itemView = layoutInflater.inflate(R.layout.item_device, listDevices, false)
                    val textIp = itemView.findViewById<TextView>(R.id.textDeviceIp)
                    val textDesc = itemView.findViewById<TextView>(R.id.textDeviceDesc)
                    val iconDeviceType = itemView.findViewById<ImageView>(R.id.iconDeviceType)
                    val iconCheck = itemView.findViewById<ImageView>(R.id.iconCheck)
                    
                    val pcInfo = state.pcInfoMap[ip]
                    val pcName = pcInfo?.name ?: "Unknown PC"
                    val osVersion = pcInfo?.osVersion ?: "Windows"
                    val deviceType = pcInfo?.type ?: "Desktop"
                    
                    textIp.text = "$pcName ($ip)"
                    textDesc.text = osVersion
                    
                    if (deviceType.equals("Laptop", ignoreCase = true)) {
                        iconDeviceType.setImageResource(R.drawable.ic_laptop)
                    } else if (deviceType.equals("Phone", ignoreCase = true)) {
                        iconDeviceType.setImageResource(R.drawable.ic_devices) // Phone icon
                    } else {
                        iconDeviceType.setImageResource(R.drawable.ic_desktop)
                    }
                    
                    if (ip == state.selectedPcIp) {
                        iconCheck.visibility = View.VISIBLE
                        itemView.background.setTint(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer))
                    } else {
                        iconCheck.visibility = View.GONE
                        itemView.background.setTintList(null) // Remove tint
                    }
                    
                    itemView.setOnClickListener {
                        val intent = Intent(this, MediaSyncService::class.java).apply {
                            if (ip == state.selectedPcIp) {
                                action = "ACTION_DISCONNECT_PC"
                            } else {
                                action = "ACTION_SELECT_PC"
                                putExtra("ip", ip)
                            }
                        }
                        startService(intent)
                    }
                    
                    listDevices.addView(itemView)
                }
            }
        }

        val listeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val hasPermission = listeners?.contains(packageName) == true
        btnPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        // Start service if not running
        startService(Intent(this, MediaSyncService::class.java))
    }
}
