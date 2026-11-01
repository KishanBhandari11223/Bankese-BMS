package com.bankease.mobile;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class ServerAddressActivity extends AppCompatActivity {

    private static final String LOCAL_NETWORK_PERMISSION =
            "android.permission.ACCESS_LOCAL_NETWORK";

    private EditText addressInput;
    private TextView errorText;
    private ServerAddressStore addressStore;
    private String pendingAddress;
    private final ActivityResultLauncher<String> localNetworkPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingAddress != null) {
                    connectToServer(pendingAddress);
                } else {
                    showError("Allow local network access so BMS can connect to your server.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addressStore = new ServerAddressStore(this);
        setContentView(R.layout.activity_server_address);

        addressInput = findViewById(R.id.etServerAddress);
        errorText = findViewById(R.id.tvServerAddressError);
        String savedAddress = addressStore.getAddress();
        if (!savedAddress.isEmpty()) {
            addressInput.setText(savedAddress);
            addressInput.setSelection(savedAddress.length());
        }

        Button connectButton = findViewById(R.id.btnConnectServer);
        connectButton.setOnClickListener(view -> connect());
    }

    private void connect() {
        String address = addressInput.getText().toString().trim();
        String validationError = validateAddress(address);
        if (validationError != null) {
            errorText.setText(validationError);
            errorText.setVisibility(View.VISIBLE);
            return;
        }

        pendingAddress = address;
        if (Build.VERSION.SDK_INT >= 37
                && checkSelfPermission(LOCAL_NETWORK_PERMISSION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION);
            return;
        }
        connectToServer(address);
    }

    private void connectToServer(String address) {
        addressStore.saveAddress(address);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    static String validateAddress(String address) {
        if (address == null || address.isEmpty()) {
            return "Enter a server address and port.";
        }
        if (address.contains("://") || address.contains("/")
                || address.contains(" ") || address.indexOf(':') != address.lastIndexOf(':')) {
            return "Use the format 192.168.29.251:8080.";
        }

        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            return "Include both the server address and port.";
        }

        String host = address.substring(0, separator);
        String portValue = address.substring(separator + 1);
        if (!isValidHost(host)) {
            return "Enter a valid IPv4 address or hostname.";
        }
        if (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("0.0.0.0")) {
            return "This address points to the Android device. For the emulator, "
                    + "use 10.0.2.2:" + portValue
                    + "; for a physical device, use your computer's LAN IP.";
        }
        if (!portValue.matches("[0-9]{1,5}")) {
            return "Enter a port from 1 to 65535.";
        }

        int port = Integer.parseInt(portValue);
        if (port < 1 || port > 65535) {
            return "Enter a port from 1 to 65535.";
        }
        return null;
    }

    private static boolean isValidHost(String host) {
        if (host.matches("[0-9.]+")) {
            String[] octets = host.split("\\.", -1);
            if (octets.length != 4) {
                return false;
            }
            for (String octet : octets) {
                if (!octet.matches("[0-9]{1,3}")
                        || Integer.parseInt(octet) > 255) {
                    return false;
                }
            }
            return true;
        }
        if (host.length() > 253) {
            return false;
        }
        return host.matches("(?i)(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    }
}
