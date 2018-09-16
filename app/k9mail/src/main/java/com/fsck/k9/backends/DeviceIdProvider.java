package com.fsck.k9.backends;


import java.util.UUID;

import android.content.Context;
import android.content.SharedPreferences;


public class DeviceIdProvider {
    private static final String PREFERENCES_NAME = "deviceId";
    private static final String KEY_DEVICE_ID = "deviceId";


    private final SharedPreferences sharedPreferences;
    private String deviceId;


    public static DeviceIdProvider newInstance(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        return new DeviceIdProvider(sharedPreferences);
    }

    DeviceIdProvider(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
    }

    public synchronized String getDeviceId() {
        if (deviceId == null) {
            deviceId = getOrCreateDeviceId();
        }

        return deviceId;
    }
    
    public synchronized void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        saveDeviceId(deviceId);
    }

    private String getOrCreateDeviceId() {
        String deviceId = readDeviceId();
        if (deviceId != null) {
            return deviceId;
        }

        String newDeviceId = createDeviceId();
        saveDeviceId(newDeviceId);

        return newDeviceId;
    }

    private String readDeviceId() {
        return sharedPreferences.getString(KEY_DEVICE_ID, null);
    }

    private String createDeviceId() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    private void saveDeviceId(String deviceId) {
        sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply();
    }
}
