package com.bankease.mobile;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdminActivity extends AppCompatActivity {

    private static final int BLUE = 0xFF1266D5;
    private static final int INK = 0xFF102344;
    private static final int MUTED = 0xFF64748B;

    private TokenStore tokenStore;
    private ApiClient api;
    private EditText searchInput;
    private TextView statusText;
    private LinearLayout customerList;
    private JSONArray customers = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tokenStore = new TokenStore(this);
        if (tokenStore.getToken() == null) {
            openLogin();
            return;
        }
        api = new ApiClient(this, tokenStore);
        setContentView(R.layout.activity_admin);
        searchInput = findViewById(R.id.adminSearchInput);
        statusText = findViewById(R.id.adminStatus);
        customerList = findViewById(R.id.adminCustomerList);
        TextView welcome = findViewById(R.id.adminWelcome);
        welcome.setText("Administrator review queue");
        findViewById(R.id.adminSearchButton).setOnClickListener(view -> loadCustomers());
        findViewById(R.id.adminLogout).setOnClickListener(view -> signOut());
        loadCustomers();
    }

    private void loadCustomers() {
        statusText.setText("Loading customers...");
        String query = searchInput.getText().toString().trim();
        api.execute(() -> api.adminCustomers(query), new ApiClient.Callback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray result) {
                customers = result;
                renderCustomers();
            }

            @Override
            public void onFailure(String message) {
                handleFailure(message);
            }
        });
    }

    private void renderCustomers() {
        customerList.removeAllViews();
        statusText.setText(customers.length() + " customer(s)");
        if (customers.length() == 0) {
            addMessage("No matching customers.");
            return;
        }
        for (int i = 0; i < customers.length(); i++) {
            JSONObject customer = customers.optJSONObject(i);
            if (customer != null) {
                addCustomerCard(customer);
            }
        }
    }

    private void addCustomerCard(JSONObject customer) {
        long customerId = customer.optLong("userId");
        String status = customer.optString("status", "UNKNOWN");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(Color.WHITE);
        TextView name = new TextView(this);
        name.setText(customer.optString("fullName") + "  ·  " + status);
        name.setTextColor(INK);
        name.setTextSize(16);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);
        TextView identity = new TextView(this);
        JSONArray accountRows = customer.optJSONArray("accounts");
        identity.setText("@" + customer.optString("username") + "\n"
                + customer.optString("email") + " · "
            + (accountRows == null ? 0 : accountRows.length()) + " account(s)");
        identity.setTextColor(MUTED);
        identity.setTextSize(13);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        identityParams.topMargin = dp(5);
        card.addView(identity, identityParams);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button manage = actionButton("Manage");
        manage.setOnClickListener(view -> showCustomerDetails(customer));
        actions.addView(manage);
        if ("PENDING".equals(status)) {
            Button approve = actionButton("Approve");
            approve.setTextColor(0xFF16834A);
            approve.setOnClickListener(view -> approveCustomer(customerId));
            actions.addView(approve);
        }
        card.addView(actions);
        addCard(card);
    }

    private void showCustomerDetails(JSONObject customer) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView summary = new TextView(this);
        summary.setText("@" + customer.optString("username") + " · "
                + customer.optString("status") + "\n"
                + customer.optString("email") + "\n"
                + customer.optString("phone", "Phone not provided"));
        summary.setTextColor(INK);
        summary.setTextSize(14);
        content.addView(summary);
        Button edit = actionButton("Edit customer details");
        edit.setOnClickListener(view -> editCustomer(customer));
        content.addView(edit);

        String customerStatus = customer.optString("status");
        if ("PENDING".equals(customerStatus)) {
            Button approve = actionButton("Approve customer and create default accounts");
            approve.setTextColor(0xFF16834A);
            approve.setOnClickListener(view -> approveCustomer(customer.optLong("userId")));
            content.addView(approve);
        } else {
            Button status = actionButton("Change customer status");
            status.setOnClickListener(view -> chooseCustomerStatus(customer));
            content.addView(status);
        }

        TextView accountsHeading = new TextView(this);
        accountsHeading.setText("Accounts");
        accountsHeading.setTextColor(INK);
        accountsHeading.setTextSize(17);
        accountsHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(14);
        content.addView(accountsHeading, headingParams);
        JSONArray accounts = customer.optJSONArray("accounts");
        if (accounts != null) {
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject account = accounts.optJSONObject(i);
                if (account != null) {
                    addAccountControls(content, customer.optLong("userId"), account);
                }
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new AlertDialog.Builder(this)
                .setTitle(customer.optString("fullName"))
            .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void addAccountControls(LinearLayout parent, long customerId, JSONObject account) {
        String accountStatus = account.optString("status");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView details = new TextView(this);
        details.setText(account.optString("accountType") + " · ending "
                + lastFour(account.optString("accountNumber")) + " · " + accountStatus);
        details.setTextColor(MUTED);
        details.setTextSize(13);
        row.addView(details, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if ("PENDING_APPROVAL".equals(accountStatus)) {
            Button approve = actionButton("Approve");
            approve.setTextColor(0xFF16834A);
            approve.setOnClickListener(view -> updateAccountStatus(
                    customerId, account.optLong("id"), "ACTIVE"));
            row.addView(approve);
        } else if (!"CLOSED".equals(accountStatus)) {
            Button options = actionButton("Status");
            options.setOnClickListener(view -> chooseAccountStatus(customerId, account));
            row.addView(options);
        }
        parent.addView(row);
    }

    private void editCustomer(JSONObject customer) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText fullName = editField("Full name", customer.optString("fullName"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = editField("Email", customer.optString("email"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = editField("Phone", customer.optString("phone", ""),
                InputType.TYPE_CLASS_PHONE);
        form.addView(fullName);
        form.addView(email);
        form.addView(phone);
        new AlertDialog.Builder(this)
                .setTitle("Edit customer")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> api.execute(
                        () -> api.adminUpdateCustomer(customer.optLong("userId"),
                                fullName.getText().toString().trim(),
                                email.getText().toString().trim(),
                                phone.getText().toString().trim()),
                        callback("Customer details updated.")))
                .show();
    }

    private void chooseCustomerStatus(JSONObject customer) {
        String[] options = {"ACTIVE", "LOCKED", "DISABLED"};
        new AlertDialog.Builder(this)
                .setTitle("Set customer status")
                .setItems(options, (dialog, index) -> api.execute(
                        () -> api.adminUpdateCustomerStatus(
                                customer.optLong("userId"), options[index]),
                        callback("Customer status updated.")))
                .show();
    }

    private void chooseAccountStatus(long customerId, JSONObject account) {
        String[] options = {"ACTIVE", "FROZEN", "CLOSED"};
        new AlertDialog.Builder(this)
                .setTitle("Set account status")
                .setItems(options, (dialog, index) -> updateAccountStatus(
                        customerId, account.optLong("id"), options[index]))
                .show();
    }

    private void approveCustomer(long customerId) {
        api.execute(() -> api.adminApproveCustomer(customerId),
                callback("Customer approved with savings and current accounts."));
    }

    private void updateAccountStatus(long customerId, long accountId, String status) {
        api.execute(() -> api.adminUpdateAccountStatus(customerId, accountId, status),
                callback("Account status updated."));
    }

    private ApiClient.Callback<JSONObject> callback(String successMessage) {
        return new ApiClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject response) {
                toast(successMessage);
                loadCustomers();
            }

            @Override
            public void onFailure(String message) {
                handleFailure(message);
            }
        };
    }

    private void handleFailure(String message) {
        String normalized = message.toLowerCase();
        if (normalized.contains("401") || normalized.contains("forbidden")
            || normalized.contains("403") || normalized.contains("not permitted")) {
            signOut();
            return;
        }
        statusText.setText(message);
        toast(message);
    }

    private void addMessage(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(MUTED);
        empty.setTextSize(14);
        empty.setPadding(0, dp(16), 0, dp(16));
        customerList.addView(empty);
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        customerList.addView(card, params);
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(BLUE);
        button.setAllCaps(false);
        button.setMinHeight(dp(42));
        return button;
    }

    private EditText editField(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private String lastFour(String number) {
        return number.substring(Math.max(0, number.length() - 4));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void signOut() {
        tokenStore.clearToken();
        openLogin();
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
