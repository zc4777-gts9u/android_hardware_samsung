/*
 * SPDX-FileCopyrightText: 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.spenactions;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import org.lineageos.spenactions.settings.SettingsUtils;

import vendor.samsung.hardware.spen.ISPen;

public class SPenConnectionManager extends BroadcastReceiver {

    private static final String LOG_TAG = "SPenActions/SPenConnectionManager";

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ISPen mSPenHAL;
    private BluetoothDevice mSpen;
    private BluetoothGatt mGatt;
    private String mCurrentBleSpenAddr;

    public SPenConnectionManager(Context context) {
        mContext = context;
        mSPenHAL = ISPen.Stub.asInterface(ServiceManager.waitForDeclaredService(
                ISPen.DESCRIPTOR + "/default"));

        context.registerReceiver(this, new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST));
    }

    private void reportBatteryState(int level, boolean charging) {
        SettingsUtils.setIntPreference(mContext, SettingsUtils.SPEN_BATTERY_VALUE, level);
        SettingsUtils.setBooleanPreference(mContext, SettingsUtils.SPEN_BATTERY_CHARGING, charging);

        Intent intent = new Intent(SettingsUtils.ACTION_SPEN_BATTERY_CHANGED);
        intent.setPackage(mContext.getPackageName());
        intent.putExtra(SettingsUtils.EXTRA_BATTERY_LEVEL, level);
        intent.putExtra(SettingsUtils.EXTRA_BATTERY_CHARGING, charging);
        mContext.sendBroadcast(intent);
    }

    public void connect() throws RemoteException {
        BluetoothAdapter btAdapter = mContext.getSystemService(BluetoothManager.class)
                .getAdapter();
        if (!SettingsUtils.isSPenBluetoothEnabled(mContext) || !btAdapter.isEnabled()
                || mGatt != null) {
            return;
        }

        mCurrentBleSpenAddr = mSPenHAL.getMACAddress();
        Log.i(LOG_TAG, "Connecting! SPen BLE address: " + mCurrentBleSpenAddr);

        // Try to reset the pen if HAL fails to retrieve MAC
        if (mCurrentBleSpenAddr.startsWith("00:00:00:00:00:00")) {
            Log.i(LOG_TAG, "Trying to reset pen!");
            BluetoothUtils.resetSPenMAC(mContext);
            return;
        }

        mSpen = btAdapter.getRemoteDevice(mCurrentBleSpenAddr);

        if (!mSPenHAL.isCharging()) {
            mSPenHAL.setCharging(true);
        }

        // Serialize GATT callbacks with service start, disconnect and shutdown.
        mGatt = mSpen.connectGatt(mContext, false, new SPenGattCallback(this, mContext),
                BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, mHandler);
    }

    public void onConnected() {
        try {
            mSPenHAL.setMACAddress(mCurrentBleSpenAddr);
        } catch (RemoteException ex) {
            ex.printStackTrace();
        }
    }

    boolean isCurrentGatt(BluetoothGatt gatt) {
        return mGatt == gatt && SettingsUtils.isSPenBluetoothEnabled(mContext);
    }

    void onDisconnected(BluetoothGatt gatt) throws RemoteException {
        mGatt = null;
        gatt.close();
        reportBatteryState(-1, false);
        connect();
    }

    public void disconnect() throws RemoteException {
        BluetoothUtils.cancelSPenReset(mContext);
        BluetoothGatt gatt = mGatt;
        mGatt = null;
        if (gatt != null) {
            gatt.disconnect();
            gatt.close();
        }
        if (mSpen != null) {
            mSpen.disconnect();
            mSpen = null;
        }

        mSPenHAL.setCharging(false);
        reportBatteryState(-1, false);
    }

    void close() throws RemoteException {
        mContext.unregisterReceiver(this);
        disconnect();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (mSpen != null && mSpen.getAddress().equals(device.getAddress())) {
            device.setPairingConfirmation(true);
            abortBroadcast(); // Prevent Settings from receiving the broadcast message
        }
    }

}
