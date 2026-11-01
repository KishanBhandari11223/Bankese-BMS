package com.bankease.mobile;

import android.content.Context;
import android.content.SharedPreferences;

public final class ServerAddressStore {

    private static final String PREFERENCES = "server_configuration";
    private static final String ADDRESS = "server_address";
    private static final String DEFAULT_ADDRESS = "fameless.duckdns.org:64010";

    private final SharedPreferences preferences;

    public ServerAddressStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public String getAddress() {
        String address = preferences.getString(ADDRESS, DEFAULT_ADDRESS);
        return address == null || address.trim().isEmpty()
            ? DEFAULT_ADDRESS : address;
    }

    public void saveAddress(String address) {
        if (!preferences.edit().putString(ADDRESS, address).commit()) {
            throw new IllegalStateException("Unable to save server address");
        }
    }

    public String getApiBaseUrl() {
        return "http://" + getAddress() + "/api/v1";
    }
}