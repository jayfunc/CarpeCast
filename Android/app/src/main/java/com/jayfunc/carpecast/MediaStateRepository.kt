package com.jayfunc.carpecast

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PcInfo(
    val name: String,
    val type: String,
    val osVersion: String
)

data class MediaState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val albumArt: Bitmap? = null,
    val packageName: String? = null,
    val selectedPcIp: String? = null,
    val availablePcs: List<String> = emptyList(),
    val pcInfoMap: Map<String, PcInfo> = emptyMap(),
    val hasPermission: Boolean = false
)

object MediaStateRepository {
    private val _currentState = MutableStateFlow(MediaState())
    val currentState: StateFlow<MediaState> = _currentState.asStateFlow()

    fun updateMediaState(
        title: String,
        artist: String,
        album: String,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        albumArt: Bitmap?,
        packageName: String?
    ) {
        _currentState.value = _currentState.value.copy(
            title = title,
            artist = artist,
            album = album,
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            albumArt = albumArt,
            packageName = packageName
        )
    }

    fun updateAvailablePcs(pcs: List<String>, infoMap: Map<String, PcInfo>) {
        _currentState.value = _currentState.value.copy(availablePcs = pcs, pcInfoMap = infoMap)
    }

    fun updateSelectedPcIp(ip: String?) {
        _currentState.value = _currentState.value.copy(selectedPcIp = ip)
    }

    fun updatePermissionState(hasPermission: Boolean) {
        _currentState.value = _currentState.value.copy(hasPermission = hasPermission)
    }
}

