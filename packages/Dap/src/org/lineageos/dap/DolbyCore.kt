/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.dap

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioSystem
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import java.util.UUID

object DolbyCore {
    private const val PROFILE = "dolby_profile"
    private const val PARAM_PROFILE = 0
    private const val PARAM_PROFILE_ENABLED = 19
    private const val PARAM_AUX_GAIN_START_VOLUME = 22
    private const val GAME = 4
    private const val INACTIVE_SESSION_GRACE_MS = 1000L
    private val dapType = UUID.fromString("46d279d9-9be7-453d-9d7c-ef937f675587")
    private val volumeUuid = UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b")

    private class SessionEffect(
        val effect: AudioEffect,
        val volume: AudioEffect?,
        val usesSpeakerAuxGain: Boolean,
    ) {
        var pendingRelease: Runnable? = null

        fun release() {
            // Remove Dolby before returning volume control to the mixer.
            effect.release()
            volume?.release()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val effects = mutableMapOf<Int, SessionEffect>()
    private val speakerRoutes = mutableMapOf<Int, Boolean>()
    private var initialized = false
    private var speakerProfileSent: Int? = null
    private var playbackConfigs = emptyList<AudioPlaybackConfiguration>()

    fun initialize(context: Context) {
        if (initialized) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        val speakerHal = app.resources.getBoolean(R.bool.config_dolbySpeakerHal)
        audio.setAudioServerStateCallback(app.mainExecutor,
            object : AudioManager.AudioServerStateCallback() {
                override fun onAudioServerDown() {
                    speakerProfileSent = null
                    effects.keys.toList().forEach(::releaseSession)
                    speakerRoutes.clear()
                    playbackConfigs = emptyList()
                }

                override fun onAudioServerUp() {
                    speakerProfileSent = null
                    if (!speakerHal) playbackConfigs = audio.activePlaybackConfigurations
                    applySettings(app)
                }
            })
        if (!speakerHal) {
            audio.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                    playbackConfigs = configs
                    applySettings(app)
                }
            }, handler)
            playbackConfigs = audio.activePlaybackConfigurations
        }
        initialized = true
        applySettings(app)
    }

    private fun preferences(context: Context) =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getProfile(context: Context) = preferences(context).getInt(PROFILE, 0).coerceIn(0, 3)

    fun getProfileName(context: Context) = context.getString(
        when (getProfile(context)) {
            1 -> R.string.dolby_profile_movie
            2 -> R.string.dolby_profile_music
            3 -> R.string.dolby_profile_voice
            else -> R.string.dolby_profile_auto
        }
    )

    fun setProfile(context: Context, profile: Int) {
        preferences(context).edit().putInt(PROFILE, profile).apply()
        applySettings(context)
    }

    fun isEnabled(context: Context) =
        preferences(context).getBoolean(DolbyFragment.PREF_DOLBY_ENABLE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(DolbyFragment.PREF_DOLBY_ENABLE, enabled).apply()
        applySettings(context)
    }

    fun setGameEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(DolbyFragment.PREF_DOLBY_GAME, enabled).apply()
        applySettings(context)
    }

    private fun releaseSession(session: Int) {
        val effect = effects.remove(session) ?: return
        effect.pendingRelease?.let(handler::removeCallbacks)
        effect.release()
    }

    private fun applySettings(context: Context) {
        val enabled = isEnabled(context)
        val profile = getProfile(context)
        if (context.resources.getBoolean(R.bool.config_dolbySpeakerHal)) {
            val speakerProfile = if (enabled) profile else -1
            if (speakerProfileSent != speakerProfile) {
                val status = AudioSystem.setParameters("gts9u_speaker_dolby_profile=$speakerProfile")
                if (status == AudioSystem.SUCCESS) {
                    speakerProfileSent = speakerProfile
                } else {
                    Log.w("SamsungDAP", "Could not set the speaker Dolby profile: $status")
                }
            }
            return
        }
        val gameEnabled = preferences(context).getBoolean(DolbyFragment.PREF_DOLBY_GAME, false)
        val speakerAuxGain = context.resources.getInteger(R.integer.config_dolbySpeakerAuxGainQ24)
        val hasSpeakerAuxGain = speakerAuxGain >= 0
        val speakerPostVolume = context.resources.getBoolean(R.bool.config_dolbySpeakerPostVolume)
        val sessions = mutableMapOf<Int, Int>()
        val eligibleSessions = mutableSetOf<Int>()
        val reportedRoutes = mutableMapOf<Int, Boolean>()
        for (config in playbackConfigs) {
            val session = config.sessionId
            if (session <= 0) continue
            val usage = config.audioAttributes.usage
            val isGame = usage == AudioAttributes.USAGE_GAME
            val isMedia = usage == AudioAttributes.USAGE_MEDIA ||
                usage == AudioAttributes.USAGE_UNKNOWN ||
                usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
            if (!((isGame && (enabled || gameEnabled)) || (isMedia && enabled))) continue

            eligibleSessions.add(session)
            if (!config.isActive) continue
            sessions[session] = if (isGame && gameEnabled) GAME else profile
            if (hasSpeakerAuxGain || speakerPostVolume) {
                val devices = config.audioDeviceInfos
                if (devices.isNotEmpty()) {
                    // A session with any non-speaker output uses the regular path.
                    val isSpeaker = devices.all { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    reportedRoutes[session] = (reportedRoutes[session] ?: true) && isSpeaker
                }
            }
        }

        // STARTED can temporarily clear the routed device IDs. Keep the last
        // confirmed route for this session until a new route is reported.
        speakerRoutes.keys.retainAll(eligibleSessions)
        speakerRoutes.putAll(reportedRoutes)

        for (session in effects.keys.toList()) {
            val current = effects.getValue(session)
            when {
                session in sessions -> {
                    current.pendingRelease?.let(handler::removeCallbacks)
                    current.pendingRelease = null
                }
                session !in eligibleSessions -> releaseSession(session)
                current.pendingRelease == null -> {
                    // A player may pause and resume the same track at a loop boundary.
                    val release = Runnable { releaseSession(session) }
                    current.pendingRelease = release
                    handler.postDelayed(release, INACTIVE_SESSION_GRACE_MS)
                }
            }
        }
        for ((session, selectedProfile) in sessions) {
            try {
                val isSpeaker = speakerRoutes[session] == true
                val useSpeakerAuxGain = hasSpeakerAuxGain && isSpeaker
                val usePostVolume = speakerPostVolume && isSpeaker
                val current = effects[session]
                if (current != null && (current.usesSpeakerAuxGain != useSpeakerAuxGain ||
                            (current.volume != null) != usePostVolume)) {
                    // Recreate the effect so the new route starts with its own defaults.
                    releaseSession(session)
                }
                val engine = effects.getOrPut(session) {
                    // The final volume controller lets Dolby process unattenuated PCM.
                    // Applying stream volume first makes its leveler boost quiet playback.
                    val volume = if (usePostVolume) {
                        AudioEffect(AudioEffect.EFFECT_TYPE_NULL, volumeUuid, 0, session)
                    } else {
                        null
                    }
                    try {
                        volume?.checkStatus(volume.setEnabled(true))
                        SessionEffect(
                            AudioEffect(dapType, AudioEffect.EFFECT_TYPE_NULL, 0, session),
                            volume,
                            useSpeakerAuxGain,
                        )
                    } catch (error: RuntimeException) {
                        volume?.release()
                        throw error
                    }
                }.effect
                engine.checkStatus(engine.setParameter(PARAM_PROFILE, selectedProfile))
                if (useSpeakerAuxGain) {
                    engine.checkStatus(engine.setParameter(PARAM_AUX_GAIN_START_VOLUME, speakerAuxGain))
                }
                engine.checkStatus(engine.setParameter(PARAM_PROFILE_ENABLED, 1))
                engine.checkStatus(engine.setEnabled(true))
            } catch (error: RuntimeException) {
                releaseSession(session)
                Log.w("SamsungDAP", "Could not apply Dolby Atmos to session $session", error)
            }
        }
    }
}
