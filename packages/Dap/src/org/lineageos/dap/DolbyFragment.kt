/*
 * Copyright (C) 2022-2025 The LineageOS Project
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

import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

class DolbyFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var switchBar: MainSwitchPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.dolby_settings, rootKey)

        switchBar = findPreference<MainSwitchPreference>(PREF_DOLBY_ENABLE)!!
        switchBar.onPreferenceChangeListener = this
        DolbyCore.initialize(requireContext())
        switchBar.isChecked = DolbyCore.isEnabled(requireContext())
        val game = findPreference<Preference>(PREF_DOLBY_GAME)!!
        game.isVisible = !resources.getBoolean(R.bool.config_dolbySpeakerHal)
        game.onPreferenceChangeListener = this

        for ((key, value) in PREF_DOLBY_MODES) {
            val preference = findPreference<SelectorWithWidgetPreference>(key)!!
            preference.isChecked = value == DolbyCore.getProfile(requireContext())
            preference.setOnPreferenceClickListener {
                setProfile(value)
                true
            }
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference == switchBar) {
            DolbyCore.setEnabled(requireContext(), newValue as Boolean)
        } else if (preference.key == PREF_DOLBY_GAME) {
            DolbyCore.setGameEnabled(requireContext(), newValue as Boolean)
        }
        return true
    }

    private fun setProfile(profile: Int) {
        DolbyCore.setProfile(requireContext(), profile)

        for ((key, value) in PREF_DOLBY_MODES) {
            val preference = findPreference<SelectorWithWidgetPreference>(key)!!
            preference.isChecked = value == profile
        }
    }

    companion object {
        const val PREF_DOLBY_ENABLE = "dolby_enable"
        const val PREF_DOLBY_GAME = "dolby_game"
        val PREF_DOLBY_MODES = mapOf(
            "dolby_profile_auto" to 0,
            "dolby_profile_movie" to 1,
            "dolby_profile_music" to 2,
            "dolby_profile_voice" to 3,
        )
    }
}
