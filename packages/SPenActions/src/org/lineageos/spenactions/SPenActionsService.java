/*
 * SPDX-FileCopyrightText: 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import org.lineageos.spenactions.settings.SettingsUtils;

public class SPenActionsService extends Service {

    private static final String LOG_TAG = "SPenActions/Service";

    private BluetoothReceiver mBluetoothReceiver;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!SettingsUtils.isSPenBluetoothEnabled(this)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        if (mBluetoothReceiver == null) {
            Log.d(LOG_TAG, "Service started");
            BluetoothAdapter btAdapter = getSystemService(BluetoothManager.class).getAdapter();
            mBluetoothReceiver = new BluetoothReceiver(this);
            registerReceiver(mBluetoothReceiver,
                    new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            mBluetoothReceiver.onBluetoothStateChanged(this, btAdapter.getState());
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mBluetoothReceiver != null) {
            Log.d(LOG_TAG, "Service stopping");
            unregisterReceiver(mBluetoothReceiver);
            mBluetoothReceiver.close();
        }
        super.onDestroy();
    }
}
