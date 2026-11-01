package com.bankease.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private EditText identityInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView errorText;
    private TokenStore tokenStore;
    private ApiClient api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tokenStore = new TokenStore(this);
        api = new ApiClient(this, tokenStore);
        if (tokenStore.getToken() != null) {
            String savedRole = tokenStore.getRole();
            if (savedRole.isEmpty()) {
                api.execute(api::currentUser, new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject profile) {
                        String role = profile.optString("role", "CUSTOMER");
                        tokenStore.saveRole(role);
                        openRoleHome(role);
                    }

                    @Override
                    public void onFailure(String message) {
                        tokenStore.clearToken();
                        showLogin();
                    }
                });
            } else {
                openRoleHome(savedRole);
            }
            return;
        }
        showLogin();
    }

    private void showLogin() {
        setContentView(R.layout.activity_login);
        identityInput = findViewById(R.id.etUsername);
        passwordInput = findViewById(R.id.etPassword);
        loginButton = findViewById(R.id.btnLogin);
        errorText = findViewById(R.id.tvLoginError);
        TextView signUp = findViewById(R.id.tvSignUp);

        loginButton.setOnClickListener(view -> submitLogin());
        signUp.setOnClickListener(view -> startActivity(
                new Intent(this, RegistrationActivity.class)));
    }

    private void submitLogin() {
        String identity = identityInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (identity.isEmpty() || password.isEmpty()) {
            showError("Enter your username or email and password.");
            return;
        }
        hideKeyboard();
        setLoading(true);
        api.execute(() -> api.login(identity, password),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        String token = response.optString("accessToken", "");
                        if (token.isEmpty()) {
                            setLoading(false);
                            showError("The server did not return an access token.");
                            return;
                        }
                        tokenStore.saveToken(token);
                        String role = response.optString("role", "CUSTOMER");
                        tokenStore.saveRole(role);
                        openRoleHome(role);
                    }

                    @Override
                    public void onFailure(String message) {
                        setLoading(false);
                        showError(message);
                    }
                });
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Signing in..." : "Login");
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (manager != null && focused != null) {
            manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void openRoleHome(String role) {
        Class<?> destination = "ADMIN".equalsIgnoreCase(role)
                ? AdminActivity.class : MainActivity.class;
        startActivity(new Intent(this, destination));
        finish();
    }
}