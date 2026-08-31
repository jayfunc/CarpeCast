package com.jayfunc.carpecast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.jayfunc.carpecast.FileLogger
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class MediaSyncService : NotificationListenerService() {
    private val TAG = "MediaSyncService"

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeControllers: List<MediaController> = emptyList()

    private var selectedPcIp: InetAddress? = null
    private val discoveredPcs = ConcurrentHashMap<String, InetAddress>()
    private val pcLastSeen = ConcurrentHashMap<String, Long>()

    private var discoverySocket: DatagramSocket? = null
    private var dataSocket: DatagramSocket? = null
    private var commandSocket: DatagramSocket? = null

    private val pcDataPorts = ConcurrentHashMap<String, Int>()
    private val pcInfoMap = ConcurrentHashMap<String, PcInfo>()
    private var discoveryPort = 5001
    private var senderDiscoveryPort = 5003
    private var commandPort = 0

    private var targetConnectedPcIp: String? = null

    // Album art caching: only compress + send when track changes
    private var lastSentTrackKey: String = ""
    private var cachedAlbumArtBase64: String = ""

    @Volatile
    private var isRunning = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "carpecast_bg_sync"
    }

    // When screen turns on, blast 3 rapid packets so Windows reconnects within milliseconds
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                val mainHandler = Handler(Looper.getMainLooper())
                repeat(3) { i ->
                    mainHandler.postDelayed({ sendMediaState(activeControllers.firstOrNull()) }, i * 300L)
                }
            }
        }
    }

    private val settingsReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.jayfunc.carpecast.RELOAD_SETTINGS") {
                val componentName = android.content.ComponentName(
                    this@MediaSyncService,
                    MediaSyncService::class.java
                )
                try {
                    updateControllers(mediaSessionManager.getActiveSessions(componentName))
                } catch (e: SecurityException) {
                    FileLogger.e("MediaSyncService", "Missing permission to control media", e)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        isRunning = true

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        discoveryPort = prefs.getInt("discovery_port", 5001)
        senderDiscoveryPort = prefs.getInt("sender_discovery_port", 5003)
        // commandPort is dynamically allocated now, no need to read it from prefs
        
        targetConnectedPcIp = prefs.getString("targetConnectedPcIp", null)
        
        targetConnectedPcIp?.let { ipStr ->
            try {
                selectedPcIp = InetAddress.getByName(ipStr)
                MediaStateRepository.updateSelectedPcIp(ipStr)
            } catch (e: Exception) {
                // Ignore invalid IP
            }
        }

        android.content.IntentFilter("com.jayfunc.carpecast.RELOAD_SETTINGS").also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(settingsReceiver, it, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, it)
            }
        }

        // Acquire a partial wake lock to keep CPU running in background/screen-off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CarpeCast::NetworkWakeLock").apply {
            acquire()
        }

        @Suppress("DEPRECATION")
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // WifiLock: keep WiFi chip from deep-sleeping (deprecated on API 34 but still functional)
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CarpeCast::WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        // MulticastLock: prevent WiFi driver from filtering multicast/broadcast frames.
        // Holding this lock forces the chip to process all incoming multicast traffic,
        // which typically keeps it in a more active power state.
        multicastLock = wm.createMulticastLock("CarpeCast::MulticastLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        // requestNetwork: explicitly signal to the OS that we need a WiFi network.
        // This can prevent the system from suspending the WiFi interface for our process.
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network came back (e.g. screen turned on) - immediately send state
                Handler(Looper.getMainLooper()).post {
                    sendMediaState(activeControllers.firstOrNull())
                }
            }
        }
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.requestNetwork(request, networkCallback!!)
        } catch (e: Exception) {
            FileLogger.w(TAG, "requestNetwork failed: $e")
        }

        // ACTION_SCREEN_ON: send burst of packets the moment screen turns on
        // so Windows connection restores within milliseconds regardless of screen-off behaviour
        registerReceiver(screenReceiver, android.content.IntentFilter(Intent.ACTION_SCREEN_ON))

        // Start as foreground service with a persistent notification
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(targetConnectedPcIp?.let { pcInfoMap[it]?.name }))

        startNetworkThreads()
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(connectedPcName: String?): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (connectedPcName != null) {
            getString(R.string.notification_title_connected, connectedPcName)
        } else {
            getString(R.string.notification_title_idle)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateForegroundNotification(connectedPcName: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(connectedPcName))
    }



    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val componentName = android.content.ComponentName(this, MediaSyncService::class.java)
            mediaSessionManager.addOnActiveSessionsChangedListener({ controllers ->
                updateControllers(controllers)
            }, componentName)

            updateControllers(mediaSessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            FileLogger.e(TAG, "Missing permission to control media", e)
        }
    }


    private fun updateControllers(controllers: List<MediaController>?) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val allowAll = prefs.getBoolean("allow_all_sources", true)
        val allowedSet = prefs.getStringSet("allowed_sources", emptySet()) ?: emptySet()

        // Anti-Loop: Filter out our own package, and enforce allow-list if enabled
        activeControllers = controllers?.filter {
            it.packageName != packageName && (allowAll || allowedSet.contains(it.packageName))
        } ?: emptyList()

        activeControllers.forEach { controller ->
            controller.registerCallback(object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    sendMediaState(controller)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    sendMediaState(controller)
                }
            })
        }

        sendMediaState(activeControllers.firstOrNull())
    }

    private fun sendMediaState(controller: MediaController?) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        var title = ""
        var artist = ""
        var album = ""
        var isPlaying = false
        var position = 0L
        var duration = 0L

        if (controller != null) {
            val allowAll = prefs.getBoolean("allow_all_sources", true)
            val allowedSet = prefs.getStringSet("allowed_sources", emptySet()) ?: emptySet()
            if (!allowAll && !allowedSet.contains(controller.packageName)) return

            val metadata = controller.metadata
            val playbackState = controller.playbackState
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            position = playbackState?.position ?: 0L
            if (playbackState?.state == PlaybackState.STATE_PLAYING) {
                val timeDelta = android.os.SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                position += (timeDelta * playbackState.playbackSpeed).toLong()
            }
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            MediaStateRepository.updateMediaState(
                title = title, artist = artist, album = album,
                isPlaying = isPlaying, position = position, duration = duration,
                albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                packageName = controller.packageName
            )
        } else {
            MediaStateRepository.updateMediaState(
                title = "", artist = "", album = "", isPlaying = false,
                position = 0L, duration = 0L, albumArt = null, packageName = null
            )
        }

        val ip = selectedPcIp ?: return

        // Use title + artist + art bitmap identity as the art key.
        // This catches: (a) track changes, (b) art arriving after title already changed,
        // (c) same title/artist but different cover (e.g. compilation albums).
        val albumArt = if (controller != null) {
            val metadata = controller.metadata
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        } else null
        val artId = albumArt?.generationId?.toString() ?: "noart"
        val trackKey = "$title|$artist|$artId"

        val isNewArt = trackKey != lastSentTrackKey
        if (isNewArt) {
            lastSentTrackKey = trackKey
            cachedAlbumArtBase64 = ""

            if (albumArt != null) {
                try {
                    val maxDim = 500
                    var width = albumArt.width
                    var height = albumArt.height
                    val scaled = if (width > maxDim || height > maxDim) {
                        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                        width = Math.round(ratio * width)
                        height = Math.round(ratio * height)
                        android.graphics.Bitmap.createScaledBitmap(albumArt, width, height, true)
                    } else albumArt

                    var quality = 80
                    while (quality >= 10) {
                        val stream = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                        val encoded = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                        if (encoded.length < 55000) {
                            cachedAlbumArtBase64 = encoded
                            break
                        }
                        quality -= 10
                    }
                } catch (e: Exception) { }
            }
        }

        val deviceName = prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        val isTv = uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val isTablet = (resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
        val devType = if (isTv) "TV" else if (isTablet) "Tablet" else "Phone"

        val json = JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("isPlaying", isPlaying)
            put("position", position)
            put("duration", duration)
            put("commandPort", commandPort)
            put("deviceName", deviceName)
            put("deviceType", devType)
            put("osVersion", "Android ${android.os.Build.VERSION.RELEASE}")
            // Only include albumArt key when art changed; absence = "keep cached art on receiver"
            if (isNewArt) {
                put("albumArt", cachedAlbumArtBase64)
            }
        }

        val targetDataPort = pcDataPorts[ip.hostAddress] ?: 5000

        Thread {
            try {
                val data = json.toString().toByteArray()
                val packet = DatagramPacket(data, data.size, ip, targetDataPort)
                dataSocket?.send(packet)
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun sendBackCommand(command: String) {
        val ip = selectedPcIp ?: return
        Thread {
            try {
                val data = command.toByteArray()
                val packet = DatagramPacket(data, data.size, ip, 5002)
                dataSocket?.send(packet)
            } catch (e: Exception) {
            }
        }.start()
    }

    private fun startNetworkThreads() {
        // 1. Discovery Listener Thread
        Thread {
            try {
                discoverySocket = DatagramSocket(discoveryPort)
                discoverySocket?.soTimeout = 2000
                val buffer = ByteArray(1024)
                while (isRunning) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        discoverySocket?.receive(packet)
                        val msg = String(packet.data, 0, packet.length)
                        if (msg.startsWith("CARPECAST_RECEIVER:")) {
                            val parts = msg.split(":")
                            val deviceName = if (parts.size > 1) parts[1] else "PC"
                            val dataPort =
                                if (parts.size > 2) parts[2].toIntOrNull() ?: 5000 else 5000
                            val deviceType = if (parts.size > 3) parts[3] else "Desktop"
                            val osVersion = if (parts.size > 4) parts[4] else "Windows"

                            val ipString = packet.address.hostAddress ?: continue
                            val isNew = !discoveredPcs.containsKey(ipString)
                            discoveredPcs[ipString] = packet.address
                            pcDataPorts[ipString] = dataPort
                            pcInfoMap[ipString] = PcInfo(deviceName, deviceType, osVersion)
                            pcLastSeen[ipString] = System.currentTimeMillis()

                            if (isNew || MediaStateRepository.currentState.value.pcInfoMap[ipString]?.name != deviceName) {
                                MediaStateRepository.updateAvailablePcs(
                                    discoveredPcs.keys().toList(), pcInfoMap.toMap()
                                )
                                if (targetConnectedPcIp == ipString) {
                                    Handler(Looper.getMainLooper()).post {
                                        updateForegroundNotification(deviceName)
                                    }
                                }
                            }
                            
                            if (selectedPcIp == null && targetConnectedPcIp == ipString) {
                                selectedPcIp = packet.address
                                MediaStateRepository.updateSelectedPcIp(ipString)
                            }

                            if (selectedPcIp == packet.address) {
                                Handler(Looper.getMainLooper()).post {
                                    sendMediaState(activeControllers.firstOrNull())
                                }
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // timeout, proceed to cleanup
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }

                    // Cleanup loop
                    val now = System.currentTimeMillis()
                    var changed = false
                    val iterator = pcLastSeen.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (now - entry.value > 5000) {
                            if (targetConnectedPcIp == entry.key) {
                                // Prevent timeout for explicitly connected PC
                                // It might just be missing broadcast packets due to Doze/locked screen
                                continue
                            }
                            
                            discoveredPcs.remove(entry.key)
                            pcInfoMap.remove(entry.key)
                            iterator.remove()
                            changed = true

                            if (selectedPcIp?.hostAddress == entry.key) {
                                selectedPcIp = null
                                MediaStateRepository.updateSelectedPcIp(null)
                            }
                        }
                    }
                    if (changed) {
                        MediaStateRepository.updateAvailablePcs(
                            discoveredPcs.keys().toList(),
                            pcInfoMap.toMap()
                        )
                    }
                }
            } catch (e: Exception) {
            }
        }.start()

        // 2. Data Socket (Send & Receive)
        try {
            dataSocket = DatagramSocket()
            Thread {
                try {
                    val buffer = ByteArray(1024)
                    while (isRunning) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        dataSocket?.receive(packet)
                        val cmd = String(packet.data, 0, packet.length)
                        handleCommand(cmd)
                    }
                } catch (e: Exception) {
                }
            }.start()
        } catch (e: Exception) {
        }

        // 3. Command Thread (For Sender Mode)
        Thread {
            try {
                commandSocket = DatagramSocket(0) // Bind to ephemeral port
                commandPort = commandSocket?.localPort ?: 5002
                val buffer = ByteArray(1024)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    commandSocket?.receive(packet)
                    val cmd = String(packet.data, 0, packet.length)
                    if (cmd == "CONNECT_REQUEST") {
                        val ipStr = packet.address.hostAddress
                        targetConnectedPcIp = ipStr
                        getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("targetConnectedPcIp", ipStr).apply()
                        selectedPcIp = packet.address
                        MediaStateRepository.updateSelectedPcIp(ipStr)
                        // Reset art key so the next send includes the current art for the new receiver
                        lastSentTrackKey = ""
                        val pcName = ipStr?.let { pcInfoMap[it]?.name }
                        Handler(Looper.getMainLooper()).post {
                            updateForegroundNotification(pcName)
                            sendMediaState(activeControllers.firstOrNull())
                        }
                    } else if (cmd == "DISCONNECT_REQUEST") {
                        targetConnectedPcIp = null
                        getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("targetConnectedPcIp").apply()
                        if (selectedPcIp?.hostAddress == packet.address.hostAddress) {
                            selectedPcIp = null
                            MediaStateRepository.updateSelectedPcIp(null)
                        }
                        Handler(Looper.getMainLooper()).post {
                            updateForegroundNotification(null)
                        }
                    } else {
                        handleCommand(cmd)
                    }
                }
            } catch (e: Exception) {
            }
        }.start()

        // 4. Sender Presence Broadcast Thread
        Thread {
            try {
                val broadcastSocket = DatagramSocket()
                broadcastSocket.broadcast = true
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
                
                while (isRunning) {
                    try {
                        val deviceName = prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
                        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
                        val isTv = uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                        val isTablet = (resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
                        val devType = if (isTv) "TV" else if (isTablet) "Tablet" else "Phone"
                        val osVersion = android.os.Build.VERSION.RELEASE
                        
                        val msg = "CARPECAST_SENDER:$deviceName:$commandPort:$devType:Android $osVersion"
                        val data = msg.toByteArray()
                        // Broadcast so new PCs can discover us
                        val packet = DatagramPacket(data, data.size, broadcastAddress, senderDiscoveryPort)
                        broadcastSocket.send(packet)
                        // Also send unicast to the connected PC if any, to pierce WiFi power-save
                        val connectedIp = selectedPcIp
                        if (connectedIp != null) {
                            val unicastPacket = DatagramPacket(data, data.size, connectedIp, senderDiscoveryPort)
                            broadcastSocket.send(unicastPacket)
                        }
                    } catch (e: Exception) {
                    }
                    // 1 second interval: Windows times out after 5 s, giving us 4 retries before dropout
                    Thread.sleep(1000)
                }
            } catch (e: Exception) {
            }
        }.start()

    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "ACTION_SELECT_PC") {
            val ipStr = intent.getStringExtra("ip")
            if (ipStr != null && discoveredPcs.containsKey(ipStr)) {
                targetConnectedPcIp = ipStr
                getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("targetConnectedPcIp", ipStr).apply()
                selectedPcIp = discoveredPcs[ipStr]
                MediaStateRepository.updateSelectedPcIp(ipStr)
                lastSentTrackKey = "" // Reset so art is included in the first packet to new receiver
                updateForegroundNotification(pcInfoMap[ipStr]?.name)
                sendMediaState(activeControllers.firstOrNull())
            }
            return START_STICKY
        }

        if (action == "ACTION_DISCONNECT_PC") {
            sendDisconnectToPc()
            targetConnectedPcIp = null
            getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("targetConnectedPcIp").apply()
            selectedPcIp = null
            MediaStateRepository.updateSelectedPcIp(null)
            updateForegroundNotification(null)
            return START_STICKY
        }

        if (action != null && activeControllers.isNotEmpty()) {
            val controller = activeControllers.firstOrNull()
            when (action) {
                "ACTION_PLAY_PAUSE" -> {
                    if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        controller.transportControls?.pause()
                    } else {
                        controller?.transportControls?.play()
                    }
                }

                "ACTION_NEXT" -> controller?.transportControls?.skipToNext()
                "ACTION_PREV" -> controller?.transportControls?.skipToPrevious()
                "ACTION_SEEK" -> {
                    val position = intent.getLongExtra("position", -1L)
                    if (position != -1L) {
                        controller?.transportControls?.seekTo(position)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun handleCommand(cmd: String) {
        Handler(Looper.getMainLooper()).post {
            if (cmd == "DISCONNECT") {
                targetConnectedPcIp = null
                getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("targetConnectedPcIp").apply()
                selectedPcIp = null
                MediaStateRepository.updateSelectedPcIp(null)
                updateForegroundNotification(null)
                return@post
            }

            val controller = activeControllers.firstOrNull() ?: return@post
            val transportControls = controller.transportControls
            
            if (cmd.startsWith("SEEK:")) {
                val posStr = cmd.removePrefix("SEEK:")
                val pos = posStr.toLongOrNull()
                if (pos != null) {
                    transportControls.seekTo(pos)
                }
                return@post
            }

            when (cmd) {
                "TOGGLE_PLAY" -> {
                    if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                        transportControls.pause()
                    } else {
                        transportControls.play()
                    }
                }

                "PLAY" -> transportControls.play()
                "PAUSE" -> transportControls.pause()
                "NEXT" -> transportControls.skipToNext()
                "PREV" -> transportControls.skipToPrevious()
            }
        }
    }

    private fun sendDisconnectToPc() {
        val targetIp = selectedPcIp ?: return
        val targetPort = pcDataPorts[targetIp.hostAddress] ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val json = org.json.JSONObject().apply {
                    put("command", "DISCONNECT")
                }.toString()
                val data = json.toByteArray()
                dataSocket?.send(DatagramPacket(data, data.size, targetIp, targetPort))
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(settingsReceiver)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        isRunning = false
        discoverySocket?.close()
        dataSocket?.close()
        commandSocket?.close()
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        multicastLock?.let { if (it.isHeld) it.release() }
        networkCallback?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (e: Exception) {}
        }
        super.onDestroy()
    }


}
