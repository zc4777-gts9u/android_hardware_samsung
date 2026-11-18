/*
 * SPDX-FileCopyrightText: 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;

import androidx.preference.Preference;

import com.android.settingslib.widget.ActionButtonsPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.spenactions.BluetoothUtils;
import org.lineageos.spenactions.BootReceiver;
import org.lineageos.spenactions.R;
import org.lineageos.spenactions.SPenActionsService;

public class SettingsFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener, View.OnClickListener {
    private Preference mBatteryPreference;

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SettingsUtils.ACTION_SPEN_BATTERY_CHANGED.equals(intent.getAction())) {
                int battery = intent.getIntExtra(SettingsUtils.EXTRA_BATTERY_LEVEL, -1);
                boolean charging = intent.getBooleanExtra(SettingsUtils.EXTRA_BATTERY_CHARGING,
                        false);
                updateBatterySummary(battery, charging);
            }
        }
    };

    private void updateBatterySummary(int battery, boolean charging) {
        if (mBatteryPreference == null) {
            return;
        }

        if (battery < 0) {
            mBatteryPreference.setSummary(R.string.spen_battery_not_connected);
        } else if (charging) {
            mBatteryPreference.setSummary(getString(R.string.spen_battery_level_charging, battery));
        } else {
            mBatteryPreference.setSummary(getString(R.string.spen_battery_level, battery));
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.spen_settings, rootKey);

        ActionButtonsPreference actionButtonsPreference =
                findPreference(SettingsUtils.ACTION_BUTTONS);
        actionButtonsPreference.setButton1Text(R.string.spen_reset);
        actionButtonsPreference.setButton1OnClickListener(this);

        MainSwitchPreference switchBar = findPreference(SettingsUtils.SPEN_BLUETOOTH_ENABLE);
        switchBar.setOnPreferenceChangeListener(this);

        mBatteryPreference = findPreference(SettingsUtils.SPEN_BATTERY_LEVEL);
        int battery = SettingsUtils.getIntPreference(
                getActivity(), SettingsUtils.SPEN_BATTERY_VALUE, -1);
        boolean charging = SettingsUtils.isEnabled(
                getActivity(), SettingsUtils.SPEN_BATTERY_CHARGING, false);
        updateBatterySummary(battery, charging);
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(SettingsUtils.ACTION_SPEN_BATTERY_CHANGED);
        getActivity().registerReceiver(mBatteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onStop() {
        getActivity().unregisterReceiver(mBatteryReceiver);
        super.onStop();
    }

    @Override
    public void onClick(View view) {
        BluetoothUtils.resetSPenMAC(getActivity());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (SettingsUtils.SPEN_BLUETOOTH_ENABLE.equals(preference.getKey())) {
            Context context = requireContext();
            boolean enabled = (Boolean) newValue;
            // The preference listener runs before MainSwitchPreference persists the value.
            SettingsUtils.setBooleanPreference(context, SettingsUtils.SPEN_BLUETOOTH_ENABLE,
                    enabled);
            BootReceiver.setEnabled(context, enabled);
            Intent service = new Intent(context, SPenActionsService.class);
            if (enabled) {
                context.startServiceAsUser(service, context.getUser());
            } else {
                context.stopServiceAsUser(service, context.getUser());
            }
        }

        return true;
    }
}
