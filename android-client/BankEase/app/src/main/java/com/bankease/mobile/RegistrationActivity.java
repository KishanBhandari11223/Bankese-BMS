package com.bankease.mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class RegistrationActivity extends AppCompatActivity {

    private EditText fullNameInput;
    private EditText usernameInput;
    private EditText emailInput;
    private EditText phoneInput;
    private EditText passwordInput;
    private Button registerButton;
    private TextView errorText;
    private TokenStore tokenStore;
    private ApiClient api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tokenStore = new TokenStore(this);
        api = new ApiClient(this, tokenStore);
        setContentView(R.layout.activity_register);
        fullNameInput = findViewById(R.id.etFullName);
        usernameInput = findViewById(R.id.etRegisterUsername);
        emailInput = findViewById(R.id.etRegisterEmail);
        phoneInput = findViewById(R.id.etPhone);
        passwordInput = findViewById(R.id.etRegisterPassword);
        registerButton = findViewById(R.id.btnRegister);
        errorText = findViewById(R.id.tvRegisterError);

        registerButton.setOnClickListener(view -> submitRegistration());
        findViewById(R.id.btnRegisterBack).setOnClickListener(view -> finish());
        findViewById(R.id.tvBackToLogin).setOnClickListener(view -> finish());
    }

    private void submitRegistration() {
        String fullName = value(fullNameInput);
        String username = value(usernameInput);
        String email = value(emailInput);
        String phone = value(phoneInput);
        String password = passwordInput.getText().toString();
        if (fullName.isEmpty() || username.isEmpty() || email.isEmpty()
                || password.isEmpty()) {
            showError("Complete the required fields.");
            return;
        }
        if (password.length() < 12) {
            showError("Use a password with at least 12 characters.");
            return;
        }
        setLoading(true);
        api.execute(() -> api.register(fullName, username, email, password, phone),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject ignored) {
                        setLoading(false);
                        showError("Registration submitted for administrator approval. "
                                + "You can sign in after approval.");
                    }

                    @Override
                    public void onFailure(String message) {
                        setLoading(false);
                        showError(message);
                    }
                });
    }

    private String value(EditText input) {
        return input.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        registerButton.setText(loading ? "Creating account..." : "Create account");
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}