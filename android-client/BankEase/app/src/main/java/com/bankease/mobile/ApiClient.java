package com.bankease.mobile;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ApiClient {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int TIMEOUT_MILLIS = 15000;

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String message);
    }

    private final String baseUrl;
    private final TokenStore tokenStore;

    public ApiClient(Context context, TokenStore tokenStore) {
        this.baseUrl = new ServerAddressStore(context).getApiBaseUrl();
        this.tokenStore = tokenStore;
    }

    public JSONObject login(String identity, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("identity", identity);
        body.put("password", password);
        return request("POST", "/auth/login", body, false);
    }

    public JSONObject register(
            String fullName, String username, String email,
            String password, String phone) throws Exception {
        JSONObject body = new JSONObject();
        body.put("fullName", fullName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("phone", phone == null || phone.trim().isEmpty()
                ? JSONObject.NULL : phone.trim());
        return request("POST", "/auth/register", body, false);
    }

    public JSONObject currentUser() throws Exception {
        return request("GET", "/auth/me", null, true);
    }

    public JSONObject updateProfile(String fullName, String email, String phone)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("phone", phone == null || phone.trim().isEmpty()
                ? JSONObject.NULL : phone.trim());
        return request("PUT", "/auth/me", body, true);
    }

    public JSONArray accounts() throws Exception {
        return requestArray("GET", "/accounts", null, true);
    }

    public JSONObject createAccount(String accountType) throws Exception {
        JSONObject body = new JSONObject().put("accountType", accountType);
        return request("POST", "/accounts", body, true);
    }

    public JSONObject balance(long accountId, String password) throws Exception {
        JSONObject body = new JSONObject().put("password", password);
        return request("POST", "/accounts/" + accountId + "/balance", body, true);
    }

    public JSONObject deposit(long accountId, String amount, String description)
            throws Exception {
        return money("/accounts/" + accountId + "/deposit", amount, description);
    }

    public JSONObject withdraw(long accountId, String amount, String description)
            throws Exception {
        return money("/accounts/" + accountId + "/withdraw", amount, description);
    }

    public JSONObject transfer(
            long sourceAccountId, String destinationAccountNumber,
            String amount, String description) throws Exception {
        JSONObject body = new JSONObject();
        body.put("destinationAccountNumber", destinationAccountNumber);
        body.put("amount", amount);
        body.put("description", description == null ? JSONObject.NULL : description);
        return request("POST", "/accounts/" + sourceAccountId + "/transfers",
                body, true);
    }

    public JSONArray savedRecipients() throws Exception {
        return requestArray("GET", "/friends", null, true);
    }

    public JSONObject saveRecipient(String accountNumber) throws Exception {
        JSONObject body = new JSONObject().put("accountNumber", accountNumber);
        return request("POST", "/friends", body, true);
    }

    public JSONObject removeRecipient(long recipientId) throws Exception {
        return request("DELETE", "/friends/" + recipientId, null, true);
    }

    public JSONObject transactions(int page, int size) throws Exception {
        return request("GET", "/transactions?page=" + page + "&size=" + size,
                null, true);
    }

    public JSONArray adminCustomers(String query) throws Exception {
        String path = "/admin/customers";
        if (query != null && !query.trim().isEmpty()) {
            path += "?query=" + URLEncoder.encode(query.trim(), "UTF-8");
        }
        return requestArray("GET", path, null, true);
    }

    public JSONObject adminUpdateCustomer(
            long customerId, String fullName, String email, String phone)
            throws Exception {
        JSONObject body = new JSONObject();
        body.put("fullName", fullName);
        body.put("email", email);
        body.put("phone", phone == null || phone.trim().isEmpty()
                ? JSONObject.NULL : phone.trim());
        return request("PUT", "/admin/customers/" + customerId, body, true);
    }

    public JSONObject adminUpdateCustomerStatus(long customerId, String status)
            throws Exception {
        JSONObject body = new JSONObject().put("status", status);
        return request("PUT", "/admin/customers/" + customerId + "/status",
                body, true);
    }

    public JSONObject adminApproveCustomer(long customerId) throws Exception {
        return request("POST", "/admin/customers/" + customerId + "/approve",
                new JSONObject(), true);
    }

    public JSONObject adminUpdateAccountStatus(
            long customerId, long accountId, String status) throws Exception {
        JSONObject body = new JSONObject().put("status", status);
        return request("PUT", "/admin/customers/" + customerId
                + "/accounts/" + accountId + "/status", body, true);
    }

    public <T> void execute(Callable<T> work, Callback<T> callback) {
        EXECUTOR.execute(() -> {
            try {
                T result = work.call();
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception ex) {
                String message = ex.getMessage();
                MAIN.post(() -> callback.onFailure(
                        message == null || message.isEmpty()
                                ? "The request could not be completed" : message));
            }
        });
    }

    private JSONObject money(String path, String amount, String description)
            throws JSONException, Exception {
        JSONObject body = new JSONObject();
        body.put("amount", amount);
        body.put("description", description == null ? JSONObject.NULL : description);
        return request("POST", path, body, true);
    }

    private JSONObject request(
            String method, String path, JSONObject body, boolean authenticated)
            throws Exception {
        String response = perform(method, path, body, authenticated);
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    private JSONArray requestArray(
            String method, String path, JSONObject body, boolean authenticated)
            throws Exception {
        return new JSONArray(perform(method, path, body, authenticated));
    }

    private String perform(
            String method, String path, JSONObject body, boolean authenticated)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setRequestProperty("Accept", "application/json");
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setDoOutput(true);
        }
        if (authenticated) {
            String token = tokenStore.getToken();
            if (token == null || token.isEmpty()) {
                connection.disconnect();
                throw new ApiException(401, "Please log in again");
            }
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }

        try {
            if (body != null) {
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(data);
                }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : readResponse(stream);
            if (status < 200 || status >= 300) {
                throw new ApiException(status, errorMessage(status, response, path));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String readResponse(InputStream stream) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private String errorMessage(int status, String response, String path) {
        try {
            JSONObject error = new JSONObject(response);
            String message = error.optString("message",
                    error.optString("detail", error.optString("error", "")));
            if (!message.isEmpty()) {
                return message;
            }
        } catch (JSONException ignored) {
        }
        if (status == 401) {
            if (path.equals("/auth/login")) {
                return "Invalid username/email or password.";
            }
            return "Session expired. Please log in again.";
        }
        if (status == 403) {
            return "This account may be awaiting administrator approval, or this action is not permitted.";
        }
        if (status == 409 && path.contains("/transfers")) {
            return "Transfer declined: the balance may be insufficient or an account is inactive.";
        }
        if (status == 409) {
            return "Request conflicts with the current account state.";
        }
        if (status == 400) {
            return "The request contains invalid or incomplete information.";
        }
        if (status >= 500) {
            return "The banking server could not complete the request. Try again later.";
        }
        return "Request failed (HTTP " + status + ")";
    }

    private static final class ApiException extends Exception {
        ApiException(int status, String message) {
            super(message);
        }
    }
}