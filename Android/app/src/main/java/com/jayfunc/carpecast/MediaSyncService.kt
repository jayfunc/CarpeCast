package com.jayfunc.carpecast

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
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
    private var commandPort = 5002

    @Volatile
    private var isRunning = false

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

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
                    android.util.Log.e("MediaSyncService", "Missing permission to control media", e)
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
        commandPort = prefs.getInt("command_port", 5002)

        android.content.IntentFilter("com.jayfunc.carpecast.RELOAD_SETTINGS").also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(settingsReceiver, it, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, it)
            }
        }

        startNetworkThreads()
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
            Log.e(TAG, "Missing permission to control media", e)
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
        var albumArtBase64 = ""

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
                val timeDelta =
                    android.os.SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime
                position += (timeDelta * playbackState.playbackSpeed).toLong()
            }
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

            val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

            if (albumArt != null) {
                try {
                    val maxDim = 500
                    var width = albumArt.width
                    var height = albumArt.height
                    if (width > maxDim || height > maxDim) {
                        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                        width = Math.round(ratio * width)
                        height = Math.round(ratio * height)
                        val scaled = android.graphics.Bitmap.createScaledBitmap(albumArt, width, height, true)
                        val stream = java.io.ByteArrayOutputStream()
                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
                        albumArtBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    } else {
                        val stream = java.io.ByteArrayOutputStream()
                        albumArt.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
                        albumArtBase64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                } catch (e: Exception) { }
            }

            MediaStateRepository.updateMediaState(
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                albumArt = albumArt,
                packageName = controller.packageName
            )
        } else {
            MediaStateRepository.updateMediaState(
                title = "", artist = "", album = "", isPlaying = false,
                position = 0L, duration = 0L, albumArt = null, packageName = null
            )
        }

        val ip = selectedPcIp ?: return
        val deviceName = prefs.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        val isTv =
            uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val isTablet =
            (resources.configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
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
            if (albumArtBase64.isNotEmpty()) {
                put("albumArt", albumArtBase64)
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
                                if (selectedPcIp == null) {
                                    selectedPcIp = packet.address
                                    MediaStateRepository.updateSelectedPcIp(ipString)
                                }
                                MediaStateRepository.updateAvailablePcs(
                                    discoveredPcs.keys().toList(), pcInfoMap.toMap()
                                )
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
                            discoveredPcs.remove(entry.key)
                            pcInfoMap.remove(entry.key)
                            iterator.remove()
                            changed = true

                            if (selectedPcIp?.hostAddress == entry.key) {
                                selectedPcIp = null
                                MediaStateRepository.updateSelectedPcIp(null)
                                discoveredPcs.values.firstOrNull()?.let { nextIp ->
                                    selectedPcIp = nextIp
                                    MediaStateRepository.updateSelectedPcIp(nextIp.hostAddress)
                                }
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
                        selectedPcIp = packet.address
                        MediaStateRepository.updateSelectedPcIp(packet.address.hostAddress)
                        Handler(Looper.getMainLooper()).post {
                            sendMediaState(activeControllers.firstOrNull())
                        }
                    } else if (cmd == "DISCONNECT_REQUEST") {
                        if (selectedPcIp == packet.address) {
                            selectedPcIp = null
                            MediaStateRepository.updateSelectedPcIp(null)
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
                        val packet = DatagramPacket(data, data.size, broadcastAddress, 5003)
                        broadcastSocket.send(packet)
                    } catch (e: Exception) {
                    }
                    Thread.sleep(2000)
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
                selectedPcIp = discoveredPcs[ipStr]
                MediaStateRepository.updateSelectedPcIp(ipStr)
                sendMediaState(activeControllers.firstOrNull())
            }
            return START_STICKY
        }

        if (action == "ACTION_DISCONNECT_PC") {
            sendDisconnectToPc()
            selectedPcIp = null
            MediaStateRepository.updateSelectedPcIp(null)
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
            }
        }
        return START_STICKY
    }

    private fun handleCommand(cmd: String) {
        Handler(Looper.getMainLooper()).post {
            if (cmd == "DISCONNECT") {
                selectedPcIp = null
                MediaStateRepository.updateSelectedPcIp(null)
                return@post
            }

            val controller = activeControllers.firstOrNull() ?: return@post
            val transportControls = controller.transportControls
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
        isRunning = false
        discoverySocket?.close()
        dataSocket?.close()
        commandSocket?.close()
        super.onDestroy()
    }
}
