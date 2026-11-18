/*
 * SPDX-FileCopyrightText: 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import org.lineageos.spenactions.settings.SettingsUtils;

public class BluetoothReceiver extends BroadcastReceiver {

    private static final String TAG = "SPenActions/BluetoothReceiver";

    private final SPenConnectionManager mConnectionManager;

    public BluetoothReceiver(Context context) {
        mConnectionManager = new SPenConnectionManager(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        onBluetoothStateChanged(context, intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
    }

    void onBluetoothStateChanged(Context context, int state) {
        try {
            if (state == BluetoothAdapter.STATE_ON) {
                if (SettingsUtils.isSPenBluetoothEnabled(context)) {
                    mConnectionManager.disconnect();
                    mConnectionManager.connect();
                }
            } else if (state == BluetoothAdapter.STATE_OFF) {
                mConnectionManager.disconnect();
            }
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to apply Bluetooth state for S Pen", ex);
        }
    }

    void close() {
        try {
            mConnectionManager.close();
        } catch (RemoteException ex) {
            Log.e(TAG, "Failed to disconnect S Pen", ex);
        }
    }
}
