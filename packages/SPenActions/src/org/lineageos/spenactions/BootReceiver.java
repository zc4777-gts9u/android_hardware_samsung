/*
 * SPDX-FileCopyrightText: 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import org.lineageos.spenactions.settings.SettingsUtils;

public class BootReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "SPenActions/BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        if (!SettingsUtils.isSPenBluetoothEnabled(context)) {
            // Reconcile existing installs once, then skip this receiver at boot.
            setEnabled(context, false);
            return;
        }

        Log.d(LOG_TAG, "Received boot completed intent");
        context.startServiceAsUser(new Intent(context, SPenActionsService.class),
                context.getUser());
    }

    public static void setEnabled(Context context, boolean enabled) {
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        context.getPackageManager().setComponentEnabledSetting(
                new ComponentName(context, BootReceiver.class), state, PackageManager.DONT_KILL_APP);
    }
}
