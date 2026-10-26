package io.github.showcode1024.oneframe.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Report advertised capabilities, not a claim that a specific stream can play in real time. */
object DeviceCapabilities {
    fun hevcDecoders(): JSONArray = JSONArray().apply {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }.forEach { codec ->
                val type = codec.supportedTypes.firstOrNull { it.equals("video/hevc", true) } ?: return@forEach
                runCatching {
                    val capabilities = codec.getCapabilitiesForType(type)
                    put(JSONObject().apply {
                        put("name", codec.name)
                        if (Build.VERSION.SDK_INT >= 29) {
                            put("hardwareAccelerated", codec.isHardwareAccelerated)
                            put("softwareOnly", codec.isSoftwareOnly)
                        }
                        put("hevcMain10Advertised", capabilities.profileLevels.any { profile -> profile.profile in setOf(
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                        ) || Build.VERSION.SDK_INT >= 29 && profile.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus })
                    })
                }
            }
        }
    }
}
