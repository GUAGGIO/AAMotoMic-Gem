package com.valerio.aamotomic.pro;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Listens for Bluetooth intercom connection/disconnection events to automatically
 * start or stop AudioRouterService when Auto-Start is enabled.
 */
public class BtConnectionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            DiagLogger.log(context, "[BT-RECEIVER] Boot completato, pronto per eventi Bluetooth.");
            return;
        }

        String targetMac = AppPrefs.getTargetMac(context);
        if (targetMac.isEmpty()) return;

        BluetoothDevice device;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        } else {
            //noinspection deprecation
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
        if (device == null) return;

        String deviceMac = "";
        try {
            deviceMac = device.getAddress();
        } catch (SecurityException ignored) {
        }
        if (deviceMac == null || !deviceMac.equalsIgnoreCase(targetMac)) {
            return;
        }

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (AppPrefs.isAutoStartEnabled(context)) {
                DiagLogger.log(context, "[BT-RECEIVER] Interfono connesso (" + deviceMac + ") -> Avvio AudioRouterService.");
                Intent svc = new Intent(context, AudioRouterService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc);
                } else {
                    context.startService(svc);
                }
            } else {
                DiagLogger.log(context, "[BT-RECEIVER] Interfono connesso (" + deviceMac + "), ma Auto-Start è OFF.");
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            DiagLogger.log(context, "[BT-RECEIVER] Interfono disconnesso (" + deviceMac + ") -> Arresto AudioRouterService.");
            context.stopService(new Intent(context, AudioRouterService.class));
        }
    }
}
