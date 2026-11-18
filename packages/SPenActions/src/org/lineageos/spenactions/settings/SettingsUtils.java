/*
 * SPDX-FileCopyrightText: 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions.settings;

import android.content.Context;

import androidx.preference.PreferenceManager;

public class SettingsUtils {

    public static final String ACTION_SPEN_BATTERY_CHANGED =
            "org.lineageos.spenactions.action.SPEN_BATTERY_CHANGED";
    public static final String EXTRA_BATTERY_LEVEL = "battery_level";
    public static final String EXTRA_BATTERY_CHARGING = "battery_charging";

    public static final String SPEN_BLUETOOTH_ENABLE = "spen_bluetooth_enable";
    public static final String SPEN_MODE = "spen_mode";
    public static final String ACTION_BUTTONS = "action_buttons";
    public static final String SPEN_BATTERY_LEVEL = "spen_battery_level";
    public static final String SPEN_BATTERY_VALUE = "spen_battery_value";
    public static final String SPEN_BATTERY_CHARGING = "spen_battery_charging";

    public static boolean isSPenBluetoothEnabled(Context context) {
        return isEnabled(context, SPEN_BLUETOOTH_ENABLE, false);
    }

    public static boolean isEnabled(Context context, String pref, boolean defValue) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(pref, defValue);
    }

    public static String getSwitchPreference(Context context, String pref, String defValue) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(pref, defValue);
    }

    public static int getIntPreference(Context context, String pref, int defValue) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(pref, defValue);
    }

    public static void setIntPreference(Context context, String pref, int value) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putInt(pref, value)
                .apply();
    }

    public static void setBooleanPreference(Context context, String pref, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(pref, value)
                .apply();
    }
}
