package com.bankease.mobile;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final int BLUE = 0xFF1266D5;
    private static final int INK = 0xFF102344;
    private static final int MUTED = 0xFF64748B;
    private static final String MONEY_CHANNEL_ID = "money_activity";
    private static final String ACTION_OPEN_RECEIPT =
            "com.bankease.mobile.action.OPEN_TRANSACTION_RECEIPT";
    private static final String EXTRA_TRANSACTION_REFERENCE = "transaction_reference";
    private static final String EXTRA_TRANSACTION_TYPE = "transaction_type";

    private TokenStore tokenStore;
    private ApiClient api;
    private DrawerLayout drawerLayout;
    private FrameLayout screenContainer;
    private View screen;
    private JSONArray accounts = new JSONArray();
    private JSONArray transactions = new JSONArray();
    private JSONObject profile = new JSONObject();
    private JSONObject currentReceipt;
    private String currentScreen = "dashboard";
    private String receiptReturnScreen = "dashboard";
    private long selectedAccountId = -1;
    private long totalTransactionCount;
    private boolean dataLoaded;
    private boolean moneyOperationInProgress;
    private boolean transferInProgress;
    private boolean recipientMutationInProgress;
    private boolean appPaused;
    private boolean historyRefreshInProgress;
    private String pendingReceiptReference;
    private String pendingReceiptType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tokenStore = new TokenStore(this);
        if (tokenStore.getToken() == null) {
            openLogin();
            return;
        }
        api = new ApiClient(this, tokenStore);
        createMoneyNotificationChannel();
        setContentView(R.layout.activity_main);
        applySystemBarInsets();
        drawerLayout = findViewById(R.id.mainDrawer);
        screenContainer = findViewById(R.id.mainFragmentContainer);
        applyDrawerInsets();
        findViewById(R.id.navDashboard).setOnClickListener(view -> navigateFromDrawer("dashboard"));
        findViewById(R.id.navAccounts).setOnClickListener(view -> navigateFromDrawer("accounts"));
        findViewById(R.id.navHistory).setOnClickListener(view -> navigateFromDrawer("history"));
        findViewById(R.id.navProfile).setOnClickListener(view -> navigateFromDrawer("profile"));
        findViewById(R.id.navSignOut).setOnClickListener(view -> {
            tokenStore.clearToken();
            openLogin();
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (moneyOperationInProgress || transferInProgress) {
                    toast("Transaction is processing. Please wait.");
                } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (currentScreen.equals("receipt")) {
                    returnFromReceipt();
                } else if (!currentScreen.equals("dashboard")) {
                    showDashboard();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
        showDashboard();
        loadBankingData();
        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appPaused && dataLoaded) {
            refreshTransactionHistory();
        }
        appPaused = false;
    }

    @Override
    protected void onPause() {
        appPaused = true;
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureReceiptIntent(intent);
        if (dataLoaded) {
            openPendingReceipt();
        }
    }

    private void navigateFromDrawer(String destination) {
        drawerLayout.closeDrawer(GravityCompat.START);
        switch (destination) {
            case "accounts":
                showAccounts();
                break;
            case "history":
                showHistory();
                break;
            case "profile":
                showProfile();
                break;
            default:
                showDashboard();
                break;
        }
    }

    private void applySystemBarInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.main);
        int initialLeft = root.getPaddingLeft();
        int initialTop = root.getPaddingTop();
        int initialRight = root.getPaddingRight();
        int initialBottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    initialLeft + systemBars.left,
                    initialTop + systemBars.top,
                    initialRight + systemBars.right,
                    initialBottom + systemBars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void applyDrawerInsets() {
        View drawer = findViewById(R.id.navigationDrawer);
        int initialLeft = drawer.getPaddingLeft();
        int initialTop = drawer.getPaddingTop();
        int initialRight = drawer.getPaddingRight();
        int initialBottom = drawer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(drawer, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(initialLeft + bars.left, initialTop + bars.top,
                    initialRight + bars.right, initialBottom + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(drawerLayout);
    }

    private void loadBankingData() {
        api.execute(() -> {
            JSONObject currentUser = api.currentUser();
            if ("ADMIN".equalsIgnoreCase(currentUser.optString("role"))) {
                return new Object[]{currentUser, null, null};
            }
            return new Object[]{currentUser, api.accounts(), api.transactions(0, 50)};
        },
                new ApiClient.Callback<Object[]>() {
                    @Override
                    public void onSuccess(Object[] result) {
                        profile = (JSONObject) result[0];
                        if ("ADMIN".equalsIgnoreCase(profile.optString("role"))) {
                            tokenStore.saveRole("ADMIN");
                            startActivity(new Intent(MainActivity.this, AdminActivity.class));
                            finish();
                            return;
                        }
                        accounts = (JSONArray) result[1];
                        JSONObject history = (JSONObject) result[2];
                        transactions = history.optJSONArray("transactions");
                        if (transactions == null) {
                            transactions = new JSONArray();
                        }
                        totalTransactionCount = history.optLong(
                                "totalElements", transactions.length());
                        processIncomingTransfers(transactions);
                        JSONObject selectedAccount = findAccount(selectedAccountId);
                        if (!isActive(selectedAccount)) {
                            selectedAccountId = firstActiveAccountId();
                        }
                        boolean firstLoad = !dataLoaded;
                        dataLoaded = true;
                        if (firstLoad || (!currentScreen.equals("transfer")
                            && !currentScreen.equals("balance"))) {
                            refreshCurrentScreen();
                        }
                        captureReceiptIntent(getIntent());
                        openPendingReceipt();
                    }

                    @Override
                    public void onFailure(String message) {
                        if (isUnauthorized(message)) {
                            tokenStore.clearToken();
                            openLogin();
                            return;
                        }
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void createMoneyNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    MONEY_CHANNEL_ID, "Money activity",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notifications for completed banking transactions");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    private void refreshTransactionHistory() {
        if (historyRefreshInProgress) {
            return;
        }
        historyRefreshInProgress = true;
        api.execute(() -> api.transactions(0, 50),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject history) {
                        historyRefreshInProgress = false;
                        JSONArray latest = history.optJSONArray("transactions");
                        transactions = latest == null ? new JSONArray() : latest;
                        totalTransactionCount = history.optLong(
                                "totalElements", transactions.length());
                        processIncomingTransfers(transactions);
                        if (currentScreen.equals("history")) {
                            showHistory();
                        } else if (currentScreen.equals("dashboard")) {
                            showDashboard();
                        }
                        openPendingReceipt();
                    }

                    @Override
                    public void onFailure(String message) {
                        historyRefreshInProgress = false;
                        if (isUnauthorized(message)) {
                            tokenStore.clearToken();
                            openLogin();
                        } else {
                            Toast.makeText(MainActivity.this, message,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void processIncomingTransfers(JSONArray records) {
        SharedPreferences preferences = getSharedPreferences(
                "money_activity_notifications", MODE_PRIVATE);
        String customerKey = profile.optString("userId",
                profile.optString("username", "customer"));
        String baselineKey = "incoming_baseline_" + customerKey;
        String seenKey = "incoming_seen_" + customerKey;
        Set<String> seen = new HashSet<>(
                preferences.getStringSet(seenKey, new HashSet<>()));
        if (!preferences.getBoolean(baselineKey, false)) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject transaction = records.optJSONObject(i);
                if (transaction != null
                        && "TRANSFER_IN".equals(transaction.optString("type"))) {
                    seen.add(transactionDeduplicationKey(transaction));
                }
            }
            preferences.edit()
                    .putStringSet(seenKey, seen)
                    .putBoolean(baselineKey, true)
                    .apply();
            return;
        }

        boolean changed = false;
        for (int i = 0; i < records.length(); i++) {
            JSONObject transaction = records.optJSONObject(i);
            if (transaction == null
                    || !"TRANSFER_IN".equals(transaction.optString("type"))) {
                continue;
            }
            String key = transactionDeduplicationKey(transaction);
            if (seen.contains(key)
                    || !"COMPLETED".equals(transaction.optString("status"))) {
                continue;
            }
            seen.add(key);
            changed = true;
            postMoneyNotification(transaction.optString("referenceNumber", ""),
                    transaction.optString("type", ""),
                    transaction.optString("amount", ""));
        }
        if (changed) {
            preferences.edit().putStringSet(seenKey, seen).apply();
        }
    }

    private String transactionDeduplicationKey(JSONObject transaction) {
        long id = transaction.optLong("id", -1);
        if (id > 0) {
            return "id:" + id;
        }
        String reference = transaction.optString("referenceNumber", "");
        return "ref:" + reference;
    }

    private void postMoneyNotification(String reference, String type, String amount) {
        if (reference.isEmpty() || amount.isEmpty()
                || !NotificationManagerCompat.from(this).areNotificationsEnabled()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED)) {
            return;
        }

        String title;
        String text;
        switch (type) {
            case "DEPOSIT":
                title = "Deposit completed";
                text = "INR " + amount + " deposited";
                break;
            case "WITHDRAWAL":
                title = "Withdrawal completed";
                text = "INR " + amount + " withdrawn";
                break;
            case "TRANSFER_OUT":
                title = "Transfer completed";
                text = "INR " + amount + " sent";
                break;
            case "TRANSFER_IN":
                title = "Transfer received";
                text = "INR " + amount + " received";
                break;
            default:
                return;
        }

        Intent receiptIntent = new Intent(this, MainActivity.class);
        receiptIntent.setAction(ACTION_OPEN_RECEIPT);
        receiptIntent.putExtra(EXTRA_TRANSACTION_REFERENCE, reference);
        receiptIntent.putExtra(EXTRA_TRANSACTION_TYPE, type);
        receiptIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, reference.hashCode(), receiptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                this, MONEY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        try {
            NotificationManagerCompat.from(this).notify(
                    reference.hashCode(), notification.build());
        } catch (SecurityException ignored) {
            // Permission may be revoked between checking and posting.
        }
    }

    private void captureReceiptIntent(Intent intent) {
        if (intent != null && ACTION_OPEN_RECEIPT.equals(intent.getAction())) {
            pendingReceiptReference = intent.getStringExtra(EXTRA_TRANSACTION_REFERENCE);
            pendingReceiptType = intent.getStringExtra(EXTRA_TRANSACTION_TYPE);
        }
    }

    private void openPendingReceipt() {
        if (pendingReceiptReference == null || pendingReceiptReference.isEmpty()) {
            return;
        }
        String reference = pendingReceiptReference;
        String expectedType = pendingReceiptType;
        pendingReceiptReference = null;
        pendingReceiptType = null;
        JSONObject transaction = findTransaction(reference, expectedType, transactions);
        if (transaction != null) {
            showReceipt(transaction, "dashboard");
            return;
        }
        api.execute(() -> api.transactions(0, 50),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject history) {
                        JSONArray records = history.optJSONArray("transactions");
                        JSONObject match = findTransaction(reference, expectedType, records);
                        if (match != null) {
                            showReceipt(match, "dashboard");
                        } else {
                            showHistory();
                            toast("This transaction receipt is no longer available.");
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        if (isUnauthorized(message)) {
                            tokenStore.clearToken();
                            openLogin();
                        } else {
                            showHistory();
                            toast("This transaction receipt is unavailable.");
                        }
                    }
                });
    }

    private JSONObject findTransaction(
            String reference, String expectedType, JSONArray records) {
        if (records == null) {
            return null;
        }
        JSONObject referenceMatch = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject transaction = records.optJSONObject(i);
            if (transaction == null || !reference.equals(
                    transaction.optString("referenceNumber", ""))) {
                continue;
            }
            if (expectedType == null || expectedType.isEmpty()
                    || expectedType.equals(transaction.optString("type"))) {
                return transaction;
            }
            referenceMatch = transaction;
        }
        return referenceMatch;
    }

    private void refreshCurrentScreen() {
        switch (currentScreen) {
            case "accounts":
                showAccounts();
                break;
            case "history":
                showHistory();
                break;
            case "profile":
                showProfile();
                break;
            case "transfer":
                showTransfer();
                break;
            case "deposit":
                showMoneyOperation(true);
                break;
            case "withdraw":
                showMoneyOperation(false);
                break;
            case "balance":
                showBalance(selectedAccountId);
                break;
            case "receipt":
                if (currentReceipt != null) {
                    showReceipt(currentReceipt, receiptReturnScreen);
                } else {
                    showDashboard();
                }
                break;
            default:
                showDashboard();
                break;
        }
    }

    private void showDashboard() {
        currentScreen = "dashboard";
        inflate(R.layout.fragment_dashboard);
        String fullName = profile.optString("fullName", profile.optString("username", "customer"));
        text(R.id.tvDashboardGreeting).setText("Hello, " + fullName);
        text(R.id.tvDashboardAvatar).setText(usernameInitial());
        screen.findViewById(R.id.btnCheckBalance).setOnClickListener(view -> {
            if (selectedAccountId < 0) {
                selectedAccountId = firstActiveAccountId();
            }
            if (selectedAccountId < 0) {
                toast("Open an account before checking a balance.");
            } else {
                showBalance(selectedAccountId);
            }
        });
        screen.findViewById(R.id.actionTransfer).setOnClickListener(view -> showTransfer());
        screen.findViewById(R.id.actionPayBills).setOnClickListener(view -> showAccounts());
        screen.findViewById(R.id.actionRecharge).setOnClickListener(view -> showProfile());
        screen.findViewById(R.id.actionHistory).setOnClickListener(view -> showHistory());
        screen.findViewById(R.id.actionDeposit).setOnClickListener(
                view -> showMoneyOperation(true));
        screen.findViewById(R.id.actionWithdraw).setOnClickListener(
                view -> showMoneyOperation(false));
        screen.findViewById(R.id.tvSeeAll).setOnClickListener(view -> showHistory());
        hideView(R.id.dashboardSampleTransaction1);
        hideView(R.id.dashboardSampleTransaction2);
        LinearLayout recent = screen.findViewById(R.id.dashboardRecentTransactions);
        addTransactions(recent, Math.min(transactions.length(), 3));
    }

    private void showAccounts() {
        currentScreen = "accounts";
        inflate(R.layout.fragment_accounts);
        text(R.id.tvAccountsAvatar).setText(usernameInitial());
        text(R.id.tvAccountsSummary).setText(activeAccountCount() + " Active Account(s)");
        hideView(R.id.sampleSavingsAccount);
        hideView(R.id.sampleSecondaryAccount);
        LinearLayout list = screen.findViewById(R.id.accountListContainer);
        if (visibleAccountCount() == 0) {
            addEmptyMessage(list, "No open or pending accounts.");
        }
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account != null && !"CLOSED".equals(account.optString("status"))) {
                addAccountRow(list, account);
            }
        }
        screen.findViewById(R.id.btnOpenSavings).setOnClickListener(
                view -> createAccount("SAVINGS"));
        screen.findViewById(R.id.btnOpenCurrent).setOnClickListener(
                view -> createAccount("CURRENT"));
    }

    private void showHistory() {
        currentScreen = "history";
        inflate(R.layout.fragment_transaction_history);
        text(R.id.tvTransactionCount).setText(totalTransactionCount + " Transactions");
        hideSampleHistoryRows();
        LinearLayout list = screen.findViewById(R.id.transactionListContainer);
        if (transactions.length() == 0) {
            addEmptyMessage(list, "No transactions yet.");
        } else {
            addTransactions(list, transactions.length());
        }
    }

    private void showReceipt(JSONObject transaction, String returnScreen) {
        currentScreen = "receipt";
        currentReceipt = transaction;
        receiptReturnScreen = returnScreen;
        inflate(R.layout.fragment_transaction_receipt);

        LinearLayout details = screen.findViewById(R.id.receiptDetails);
        ArrayList<String> shareLines = new ArrayList<>();
        addReceiptDetail(details, shareLines, "Transaction reference",
                receiptValue(transaction, "referenceNumber"));
        String type = transaction.optString("type", "");
        addReceiptDetail(details, shareLines, "Type", transactionTypeLabel(type));
        addReceiptDetail(details, shareLines, "Amount",
                receiptAmountValue(transaction));
        addReceiptDetail(details, shareLines, "Date and time",
                formatReceiptDate(transaction.optString("createdAt", "")));
        addReceiptDetail(details, shareLines, "Status",
                receiptValue(transaction, "status"));

        String accountNumber = transaction.optString("accountNumber", "");
        String relatedAccountNumber = transaction.optString("relatedAccountNumber", "");
        if ("TRANSFER_OUT".equals(type)) {
            addReceiptDetail(details, shareLines, "From account",
                    receiptAccountValue(transaction, accountNumber));
            addReceiptDetail(details, shareLines, "Recipient",
                    receiptAccountValue(null, relatedAccountNumber));
        } else if ("TRANSFER_IN".equals(type)) {
            addReceiptDetail(details, shareLines, "To account",
                    receiptAccountValue(transaction, accountNumber));
            addReceiptDetail(details, shareLines, "Sender",
                    receiptAccountValue(null, relatedAccountNumber));
        } else {
            addReceiptDetail(details, shareLines, "Account",
                    receiptAccountValue(transaction, accountNumber));
            if (!relatedAccountNumber.isEmpty()) {
                addReceiptDetail(details, shareLines, "Related account",
                        receiptAccountValue(null, relatedAccountNumber));
            }
        }

        String description = transaction.optString("description", "");
        if (!description.trim().isEmpty() && !"null".equals(description)) {
            addReceiptDetail(details, shareLines, "Description", description);
        }
        String balanceAfter = transaction.optString("balanceAfter", "");
        if (!balanceAfter.isEmpty() && !"null".equals(balanceAfter)) {
            addReceiptDetail(details, shareLines, "Balance after",
                    balanceAfter);
        }
        screen.findViewById(R.id.btnShareReceipt).setOnClickListener(view -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "BankEase transaction receipt");
            share.putExtra(Intent.EXTRA_TEXT, "BankEase transaction receipt\n\n"
                    + joinLines(shareLines));
            startActivity(Intent.createChooser(share, "Share receipt"));
        });
    }

    private void returnFromReceipt() {
        if ("history".equals(receiptReturnScreen)) {
            showHistory();
        } else {
            showDashboard();
        }
    }

    private void addReceiptDetail(
            LinearLayout parent, ArrayList<String> shareLines, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundColor(Color.WHITE);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(MUTED);
        labelView.setTextSize(12);
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(INK);
        valueView.setTextSize(16);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setPadding(0, dp(4), 0, 0);
        row.addView(valueView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        parent.addView(row, params);
        shareLines.add(label + ": " + value);
    }

    private String receiptValue(JSONObject transaction, String key) {
        String value = transaction.optString(key, "");
        return value.isEmpty() || "null".equals(value) ? "Unavailable" : value;
    }

    private String maskedAccountValue(String accountNumber) {
        return accountNumber == null || accountNumber.isEmpty()
                || "null".equals(accountNumber)
                ? "Unavailable" : maskAccountNumber(accountNumber);
    }

    private String receiptAccountValue(JSONObject transaction, String accountNumber) {
        String maskedNumber = maskedAccountValue(accountNumber);
        JSONObject account = transaction == null ? null
                : findAccount(transaction.optLong("accountId", -1));
        if (account == null) {
            account = findAccountByNumber(accountNumber);
        }
        String accountType = account == null ? "" : account.optString("accountType", "");
        return accountType.isEmpty() || "null".equals(accountType)
                ? maskedNumber : accountType + " · " + maskedNumber;
    }

    private String receiptAmountValue(JSONObject transaction) {
        String amount = receiptValue(transaction, "amount");
        long accountId = transaction.optLong("accountId", -1);
        JSONObject account = findAccount(accountId);
        if (account == null) {
            account = findAccountByNumber(transaction.optString("accountNumber", ""));
        }
        String currency = account == null ? "" : account.optString("currency", "");
        return currency.isEmpty() || "null".equals(currency)
                ? amount : currency + " " + amount;
    }

    private JSONObject findAccountByNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()
                || "null".equals(accountNumber)) {
            return null;
        }
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account != null && accountNumber.equals(
                    account.optString("accountNumber", ""))) {
                return account;
            }
        }
        return null;
    }

    private String transactionTypeLabel(String type) {
        switch (type) {
            case "DEPOSIT":
                return "Deposit";
            case "WITHDRAWAL":
                return "Withdrawal";
            case "TRANSFER_OUT":
                return "Transfer sent";
            case "TRANSFER_IN":
                return "Transfer received";
            default:
                return type.isEmpty() ? "Unavailable" : type.replace('_', ' ');
        }
    }

    private String formatReceiptDate(String createdAt) {
        return createdAt == null || createdAt.isEmpty() || "null".equals(createdAt)
                ? "Unavailable" : createdAt.replace('T', ' ');
    }

    private String joinLines(ArrayList<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line);
        }
        return text.toString();
    }

    private void showProfile() {
        currentScreen = "profile";
        inflate(R.layout.fragment_profile);
        String fullName = profile.optString("fullName", "");
        text(R.id.tvProfileAvatar).setText(usernameInitial());
        text(R.id.tvProfileName).setText(fullName);
        text(R.id.tvProfileType).setText(profile.optString("username", "Customer"));
        text(R.id.tvFullName).setText(fullName);
        text(R.id.tvProfileEmail).setText(profile.optString("email", ""));
        text(R.id.tvProfilePhone).setText(profile.optString("phone", "Not provided"));
        text(R.id.tvCustomerId).setText("BE-" + profile.optLong("userId"));
        text(R.id.tvPrimaryAccount).setText(primaryAccountDescription());
        screen.findViewById(R.id.btnEditProfile).setOnClickListener(view -> showEditProfile());
        screen.findViewById(R.id.btnSignOut).setOnClickListener(view -> {
            tokenStore.clearToken();
            openLogin();
        });
    }

    private void showBalance(long accountId) {
        currentScreen = "balance";
        selectedAccountId = accountId;
        inflate(R.layout.fragment_check_balance);
        JSONObject account = findAccount(accountId);
        if (account != null) {
            text(R.id.tvBalanceAccountName).setText(account.optString("accountType") + " Account");
            String number = account.optString("accountNumber", "");
            text(R.id.tvBalanceAccountNumber).setText(
                    number.isEmpty() ? "" : "Account ending in "
                            + number.substring(Math.max(0, number.length() - 4)));
        }
        text(R.id.tvBalanceAvatar).setText(usernameInitial());
        screen.findViewById(R.id.btnVerifyBalance).setOnClickListener(view -> verifyAndLoadBalance(accountId));
    }

    private void verifyAndLoadBalance(long accountId) {
        EditText password = screen.findViewById(R.id.etBalancePassword);
        TextView error = text(R.id.tvBalanceError);
        TextView result = text(R.id.tvBalanceResult);
        String value = password.getText().toString();
        if (value.isEmpty()) {
            error.setText("Enter your password to continue.");
            error.setVisibility(View.VISIBLE);
            return;
        }
        error.setVisibility(View.GONE);
        result.setVisibility(View.GONE);
        Button button = screen.findViewById(R.id.btnVerifyBalance);
        button.setEnabled(false);
        button.setText("Verifying...");
        api.execute(() -> api.balance(accountId, value),
            new ApiClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject balance) {
                button.setEnabled(true);
                button.setText("Verify & Check Balance");
                result.setText(balance.optString("currency", "INR") + " "
                        + balance.optString("balance", "0.00"));
                result.setVisibility(View.VISIBLE);
                password.setText("");
            }

            @Override
            public void onFailure(String message) {
                button.setEnabled(true);
                button.setText("Verify & Check Balance");
                error.setText(message);
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showTransfer() {
        currentScreen = "transfer";
        inflate(R.layout.fragment_transfer);
        RadioGroup sources = screen.findViewById(R.id.rgSourceAccounts);
        long preferredAccountId = isActive(findAccount(selectedAccountId))
                ? selectedAccountId : firstActiveAccountId();
        if (preferredAccountId < 0) {
            addEmptyMessage(sources, "Open an account before making a transfer.");
        }
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (!isActive(account)) {
                continue;
            }
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(account.optString("accountType") + " · ending in "
                    + lastFour(account.optString("accountNumber")));
            option.setTextColor(INK);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
            option.setTag(account.optLong("id"));
            sources.addView(option);
            if (account.optLong("id") == preferredAccountId) {
                option.setChecked(true);
                selectedAccountId = account.optLong("id");
            }
        }
        sources.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() instanceof Long) {
                selectedAccountId = (Long) selected.getTag();
            }
        });
        screen.findViewById(R.id.btnReviewTransfer).setOnClickListener(view -> reviewTransfer());
        screen.findViewById(R.id.btnSaveRecipient).setOnClickListener(
                view -> saveRecipientFromForm());
        screen.findViewById(R.id.btnRetryRecipients).setOnClickListener(
                view -> loadSavedRecipients(screen));
        loadSavedRecipients(screen);
    }

    private void reviewTransfer() {
        if (transferInProgress) {
            return;
        }
        RadioGroup sources = screen.findViewById(R.id.rgSourceAccounts);
        EditText destination = screen.findViewById(R.id.etRecipientAccountNumber);
        EditText amount = screen.findViewById(R.id.etTransferAmount);
        EditText description = screen.findViewById(R.id.etTransferDescription);
        TextView error = text(R.id.tvTransferError);
        RadioButton selected = screen.findViewById(sources.getCheckedRadioButtonId());
        String destinationNumber = destination.getText().toString().trim();
        String amountValue = amount.getText().toString().trim();
        if (selected == null || destinationNumber.isEmpty() || amountValue.isEmpty()) {
            error.setText("Choose a source account and enter the recipient account number and amount.");
            error.setVisibility(View.VISIBLE);
            return;
        }
        error.setVisibility(View.GONE);
        String summary = "Transfer INR " + amountValue + " to account ending in "
                + lastFour(destinationNumber) + "?";
        new AlertDialog.Builder(this)
                .setTitle("Review transfer")
                .setMessage(summary)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Transfer", (dialog, which) -> submitTransfer(
                        selectedAccountId, destinationNumber, amountValue,
                        description.getText().toString().trim()))
                .show();
    }

    private void submitTransfer(
            long sourceId, String destination, String amount, String description) {
        if (transferInProgress) {
            return;
        }
        transferInProgress = true;
        Button button = screen.findViewById(R.id.btnReviewTransfer);
        button.setEnabled(false);
        button.setText("Processing...");
        api.execute(() -> api.transfer(sourceId, destination, amount, description),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        transferInProgress = false;
                        if ("COMPLETED".equals(response.optString("status"))) {
                            postMoneyNotification(
                                    response.optString("referenceNumber", ""),
                                    "TRANSFER_OUT", response.optString("amount", ""));
                        }
                        JSONObject fallbackReceipt = transferReceiptFromResponse(response);
                        showReceipt(fallbackReceipt, "dashboard");
                        loadTransferReceiptDetails(response, fallbackReceipt);
                        toast("Transfer completed and recorded.");
                        loadBankingData();
                    }

                    @Override
                    public void onFailure(String message) {
                        transferInProgress = false;
                        button.setEnabled(true);
                        button.setText("Review Transfer  →");
                        TextView error = text(R.id.tvTransferError);
                        error.setText(message);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void loadSavedRecipients(View transferScreen) {
        TextView state = transferScreen.findViewById(R.id.tvRecipientsState);
        LinearLayout list = transferScreen.findViewById(R.id.savedRecipientsList);
        Button retry = transferScreen.findViewById(R.id.btnRetryRecipients);
        list.removeAllViews();
        state.setText("Loading saved recipients...");
        state.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        api.execute(api::savedRecipients, new ApiClient.Callback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray recipients) {
                if (screen != transferScreen || !currentScreen.equals("transfer")) {
                    return;
                }
                if (recipients.length() == 0) {
                    state.setText("No saved recipients yet.");
                    return;
                }
                state.setVisibility(View.GONE);
                for (int i = 0; i < recipients.length(); i++) {
                    JSONObject recipient = recipients.optJSONObject(i);
                    if (recipient != null) {
                        addSavedRecipientRow(list, recipient);
                    }
                }
            }

            @Override
            public void onFailure(String message) {
                if (screen != transferScreen || !currentScreen.equals("transfer")) {
                    return;
                }
                state.setText("Could not load saved recipients: " + message);
                retry.setVisibility(View.VISIBLE);
            }
        });
    }

    private void addSavedRecipientRow(LinearLayout list, JSONObject recipient) {
        String status = recipient.optString("accountStatus", "UNAVAILABLE");
        boolean available = "ACTIVE".equals(status);
        String accountNumber = recipient.optString("accountNumber", "");

        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        row.setBackgroundColor(Color.WHITE);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(recipient.optString("name", "Unavailable recipient"));
        name.setTextColor(INK);
        name.setTextSize(15);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        details.addView(name);
        TextView maskedNumber = new TextView(this);
        maskedNumber.setText(maskAccountNumber(accountNumber)
                + (available ? "" : " · Unavailable"));
        maskedNumber.setTextColor(MUTED);
        maskedNumber.setTextSize(13);
        details.addView(maskedNumber);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(details, detailParams);

        Button send = new Button(this);
        send.setText(available ? "Send" : "Unavailable");
        send.setAllCaps(false);
        send.setEnabled(available && !transferInProgress);
        send.setTextColor(available ? BLUE : MUTED);
        send.setOnClickListener(view -> {
            if (available) {
                showFriendTransfer(recipient);
            }
        });
        row.addView(send);

        Button remove = new Button(this);
        remove.setText("Remove");
        remove.setAllCaps(false);
        remove.setTextColor(0xFFB42318);
        remove.setOnClickListener(view -> confirmRemoveRecipient(recipient));
        row.addView(remove);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(8);
        list.addView(row, rowParams);
    }

    private void saveRecipientFromForm() {
        if (recipientMutationInProgress) {
            return;
        }
        EditText accountNumberField = screen.findViewById(
                R.id.etNewRecipientAccountNumber);
        String accountNumber = accountNumberField.getText().toString().trim();
        TextView error = text(R.id.tvRecipientManagementError);
        if (!accountNumber.matches("[0-9]{1,20}")) {
            error.setText("Enter a valid recipient account number.");
            error.setVisibility(View.VISIBLE);
            return;
        }

        recipientMutationInProgress = true;
        error.setVisibility(View.GONE);
        Button save = screen.findViewById(R.id.btnSaveRecipient);
        save.setEnabled(false);
        save.setText("Looking up account...");
        View transferScreen = screen;
        api.execute(() -> api.saveRecipient(accountNumber),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject recipient) {
                        recipientMutationInProgress = false;
                        if (screen != transferScreen) {
                            return;
                        }
                        save.setEnabled(true);
                        save.setText("Save recipient");
                        accountNumberField.setText("");
                        toast("Saved " + recipient.optString("name")
                                + " as a recipient.");
                        loadSavedRecipients(transferScreen);
                    }

                    @Override
                    public void onFailure(String message) {
                        recipientMutationInProgress = false;
                        if (screen != transferScreen) {
                            return;
                        }
                        save.setEnabled(true);
                        save.setText("Save recipient");
                        error.setText(message);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void confirmRemoveRecipient(JSONObject recipient) {
        long recipientId = recipient.optLong("id", -1);
        if (recipientId < 0 || recipientMutationInProgress) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Remove recipient")
                .setMessage("Remove " + recipient.optString("name")
                        + " from your saved recipients?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> removeRecipient(recipientId))
                .show();
    }

    private void removeRecipient(long recipientId) {
        if (recipientMutationInProgress) {
            return;
        }
        recipientMutationInProgress = true;
        View transferScreen = screen;
        TextView error = transferScreen.findViewById(R.id.tvRecipientManagementError);
        error.setVisibility(View.GONE);
        api.execute(() -> api.removeRecipient(recipientId),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject ignored) {
                        recipientMutationInProgress = false;
                        if (screen != transferScreen) {
                            return;
                        }
                        toast("Recipient removed.");
                        loadSavedRecipients(transferScreen);
                    }

                    @Override
                    public void onFailure(String message) {
                        recipientMutationInProgress = false;
                        if (screen != transferScreen) {
                            return;
                        }
                        error.setText(message);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showFriendTransfer(JSONObject recipient) {
        if (transferInProgress) {
            return;
        }
        String name = recipient.optString("name", "Unavailable recipient");
        String accountNumber = recipient.optString("accountNumber", "");
        String accountStatus = recipient.optString("accountStatus", "UNAVAILABLE");
        String maskedNumber = maskAccountNumber(accountNumber);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);
        TextView recipientDetails = new TextView(this);
        recipientDetails.setText(name + "\n" + maskedNumber);
        recipientDetails.setTextColor(INK);
        recipientDetails.setTextSize(16);
        form.addView(recipientDetails);

        TextView error = new TextView(this);
        error.setTextColor(0xFFB42318);
        error.setVisibility(View.GONE);
        EditText amount = editField("Amount (INR)", "",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText description = editField("Description (optional)", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        description.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(255)});
        if (!"ACTIVE".equals(accountStatus)) {
            TextView unavailable = new TextView(this);
            unavailable.setText("This recipient account is unavailable ("
                    + accountStatus.toLowerCase().replace('_', ' ') + ").");
            unavailable.setTextColor(0xFFB42318);
            unavailable.setPadding(0, dp(12), 0, 0);
            form.addView(unavailable);
        } else {
            form.addView(amount);
            form.addView(description);
            form.addView(error);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Send to friend")
                .setView(form)
                .setNegativeButton("Close", null)
                .setPositiveButton("Review transfer", null)
                .create();
        if (!"ACTIVE".equals(accountStatus)) {
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Remove recipient",
                    (dialogInterface, which) -> confirmRemoveRecipient(recipient));
        }
        dialog.setOnShowListener(ignored -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (!"ACTIVE".equals(accountStatus)) {
                return;
            }
            positive.setOnClickListener(view -> {
                String amountValue = amount.getText().toString().trim();
                String validationError = validateMoneyAmount(amountValue);
                if (validationError != null) {
                    error.setText(validationError);
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                RadioGroup sources = screen.findViewById(R.id.rgSourceAccounts);
                RadioButton selectedSource = screen.findViewById(
                        sources.getCheckedRadioButtonId());
                if (selectedSource == null || !(selectedSource.getTag() instanceof Long)) {
                    error.setText("Choose an active account in the transfer form first.");
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                error.setVisibility(View.GONE);
                long sourceId = (Long) selectedSource.getTag();
                String descriptionValue = description.getText().toString().trim();
                JSONObject source = findAccount(sourceId);
                String sourceName = source == null ? "your account"
                        : source.optString("accountType") + " ending in "
                                + lastFour(source.optString("accountNumber"));
                new AlertDialog.Builder(this)
                        .setTitle("Confirm transfer")
                        .setMessage("Send INR " + amountValue + " from " + sourceName
                                + " to " + name + " (" + maskedNumber + ")?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Send", (confirmation, which) ->
                                submitFriendTransfer(sourceId, accountNumber,
                                        amountValue, descriptionValue, dialog,
                                        positive, error))
                        .show();
            });
        });
        dialog.show();
    }

    private void submitFriendTransfer(
            long sourceId, String destination, String amount, String description,
            AlertDialog dialog, Button button, TextView error) {
        if (transferInProgress) {
            return;
        }
        transferInProgress = true;
        button.setEnabled(false);
        button.setText("Processing...");
        api.execute(() -> api.transfer(sourceId, destination, amount, description),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        transferInProgress = false;
                        dialog.dismiss();
                        if ("COMPLETED".equals(response.optString("status"))) {
                            postMoneyNotification(
                                    response.optString("referenceNumber", ""),
                                    "TRANSFER_OUT", response.optString("amount", ""));
                        }
                        JSONObject fallbackReceipt = transferReceiptFromResponse(response);
                        showReceipt(fallbackReceipt, "dashboard");
                        loadTransferReceiptDetails(response, fallbackReceipt);
                        toast("Transfer to friend completed and recorded.");
                        loadBankingData();
                    }

                    @Override
                    public void onFailure(String message) {
                        transferInProgress = false;
                        button.setEnabled(true);
                        button.setText("Review transfer");
                        error.setText(message);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    private String maskAccountNumber(String accountNumber) {
        return "•••• " + lastFour(accountNumber);
    }

    private JSONObject transferReceiptFromResponse(JSONObject response) {
        JSONObject receipt = new JSONObject();
        try {
            receipt.put("referenceNumber", response.opt("referenceNumber"));
            receipt.put("type", "TRANSFER_OUT");
            receipt.put("amount", response.opt("amount"));
            receipt.put("status", response.opt("status"));
            receipt.put("accountNumber", response.opt("sourceAccountNumber"));
            receipt.put("relatedAccountNumber",
                    response.opt("destinationAccountNumber"));
            receipt.put("createdAt", JSONObject.NULL);
            receipt.put("description", JSONObject.NULL);
            receipt.put("balanceAfter", response.opt("sourceBalance"));
        } catch (org.json.JSONException exception) {
            toast("Transfer completed, but receipt details are unavailable.");
        }
        return receipt;
    }

    private void loadTransferReceiptDetails(
            JSONObject response, JSONObject fallbackReceipt) {
        String reference = response.optString("referenceNumber", "");
        if (reference.isEmpty()) {
            return;
        }
        api.execute(() -> api.transactions(0, 50),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject history) {
                        JSONArray records = history.optJSONArray("transactions");
                        if (records == null) {
                            return;
                        }
                        JSONObject match = null;
                        for (int i = 0; i < records.length(); i++) {
                            JSONObject candidate = records.optJSONObject(i);
                            if (candidate == null || !reference.equals(
                                    candidate.optString("referenceNumber", ""))) {
                                continue;
                            }
                            match = candidate;
                            if ("TRANSFER_OUT".equals(candidate.optString("type"))) {
                                break;
                            }
                        }
                        if (match != null && currentScreen.equals("receipt")
                                && currentReceipt == fallbackReceipt) {
                            showReceipt(match, receiptReturnScreen);
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        if (isUnauthorized(message)) {
                            tokenStore.clearToken();
                            openLogin();
                        }
                    }
                });
    }

    private void showMoneyOperation(boolean deposit) {
        currentScreen = deposit ? "deposit" : "withdraw";
        inflate(R.layout.fragment_money_operation);
        String title = deposit ? "Deposit" : "Withdraw";
        text(R.id.tvMoneyTitle).setText(title);
        text(R.id.tvMoneySubtitle).setText(deposit
                ? "Add money to one of your active accounts."
                : "Withdraw money from one of your active accounts.");

        RadioGroup accountOptions = screen.findViewById(R.id.rgMoneyAccounts);
        long preferredAccountId = isActive(findAccount(selectedAccountId))
                ? selectedAccountId : -1;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (!isActive(account)) {
                continue;
            }
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(account.optString("accountType") + " · ending in "
                    + lastFour(account.optString("accountNumber")));
            option.setTextColor(INK);
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(BLUE));
            option.setTag(account.optLong("id"));
            accountOptions.addView(option);
            if (account.optLong("id") == preferredAccountId
                    || (preferredAccountId < 0
                    && accountOptions.getCheckedRadioButtonId() < 0)) {
                option.setChecked(true);
                selectedAccountId = account.optLong("id");
            }
        }
        if (accountOptions.getChildCount() == 0) {
            addEmptyMessage(accountOptions, "Open an active account before continuing.");
        }
        accountOptions.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null && selected.getTag() instanceof Long) {
                selectedAccountId = (Long) selected.getTag();
            }
        });

        Button reviewButton = screen.findViewById(R.id.btnReviewMoney);
        reviewButton.setText(moneyOperationInProgress ? "Processing..."
                : "Review " + title + "  →");
        reviewButton.setEnabled(!moneyOperationInProgress);
        reviewButton.setOnClickListener(view -> reviewMoneyOperation(deposit));
    }

    private void reviewMoneyOperation(boolean deposit) {
        if (moneyOperationInProgress) {
            return;
        }
        RadioGroup accountOptions = screen.findViewById(R.id.rgMoneyAccounts);
        RadioButton selected = screen.findViewById(accountOptions.getCheckedRadioButtonId());
        EditText amountField = screen.findViewById(R.id.etMoneyAmount);
        EditText descriptionField = screen.findViewById(R.id.etMoneyDescription);
        TextView error = text(R.id.tvMoneyError);
        String amountValue = amountField.getText().toString().trim();
        String description = descriptionField.getText().toString().trim();

        if (selected == null || !(selected.getTag() instanceof Long)) {
            error.setText("Choose an active account.");
            error.setVisibility(View.VISIBLE);
            return;
        }
        String amountError = validateMoneyAmount(amountValue);
        if (amountError != null) {
            error.setText(amountError);
            error.setVisibility(View.VISIBLE);
            return;
        }
        if (description.length() > 255) {
            error.setText("Description must be 255 characters or fewer.");
            error.setVisibility(View.VISIBLE);
            return;
        }

        error.setVisibility(View.GONE);
        long accountId = (Long) selected.getTag();
        JSONObject account = findAccount(accountId);
        String accountName = account == null
                ? selected.getText().toString()
                : account.optString("accountType") + " Account ending in "
                        + lastFour(account.optString("accountNumber"));
        String action = deposit ? "Deposit" : "Withdraw";
        new AlertDialog.Builder(this)
                .setTitle("Review " + action.toLowerCase())
                .setMessage(action + " INR " + amountValue + " "
                        + (deposit ? "to " : "from ") + accountName + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(action, (dialog, which) -> submitMoneyOperation(
                        deposit, accountId, amountValue, description, accountName))
                .show();
    }

    private String validateMoneyAmount(String value) {
        if (value.isEmpty()) {
            return "Enter an amount greater than zero.";
        }
        if (!value.matches("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")) {
            return "Enter a valid amount with no more than two decimal places.";
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return "Amount must be greater than zero.";
            }
            if (amount.precision() - amount.scale() > 17) {
                return "Amount exceeds the supported limit.";
            }
        } catch (NumberFormatException exception) {
            return "Enter a valid amount.";
        }
        return null;
    }

    private void submitMoneyOperation(
            boolean deposit, long accountId, String amount, String description,
            String accountName) {
        moneyOperationInProgress = true;
        View operationScreen = screen;
        Button button = operationScreen.findViewById(R.id.btnReviewMoney);
        button.setEnabled(false);
        button.setText("Processing...");
        api.execute(() -> deposit
                        ? api.deposit(accountId, amount, description)
                        : api.withdraw(accountId, amount, description),
                new ApiClient.Callback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        moneyOperationInProgress = false;
                        String expectedType = deposit ? "DEPOSIT" : "WITHDRAWAL";
                        if ("COMPLETED".equals(response.optString("status"))
                                && expectedType.equals(response.optString("type"))) {
                            postMoneyNotification(
                                    response.optString("referenceNumber", ""),
                                    expectedType, response.optString("amount", ""));
                        }
                        String operation = deposit ? "Deposit" : "Withdrawal";
                        String balanceAfter = response.optString("balanceAfter", "");
                        String message = operation + " completed for " + accountName + ".";
                        if (!balanceAfter.isEmpty()) {
                            message += " Updated balance: INR " + balanceAfter + ".";
                        }
                        showDashboard();
                        loadBankingData();
                        toast(message);
                    }

                    @Override
                    public void onFailure(String message) {
                        moneyOperationInProgress = false;
                        button.setEnabled(true);
                        button.setText("Review " + (deposit ? "Deposit" : "Withdraw")
                                + "  →");
                        TextView error = operationScreen.findViewById(R.id.tvMoneyError);
                        error.setText(message);
                        error.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showEditProfile() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);
        EditText fullName = editField("Full name", profile.optString("fullName", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email = editField("Email", profile.optString("email", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = editField("Phone (optional)", profile.optString("phone", ""),
                InputType.TYPE_CLASS_PHONE);
        form.addView(fullName);
        form.addView(email);
        form.addView(phone);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit profile")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String nameValue = fullName.getText().toString().trim();
                    String emailValue = email.getText().toString().trim();
                    if (nameValue.isEmpty() || emailValue.isEmpty()) {
                        toast("Name and email are required.");
                        return;
                    }
                    dialog.dismiss();
                    api.execute(() -> api.updateProfile(nameValue, emailValue,
                                    phone.getText().toString().trim()),
                            new ApiClient.Callback<JSONObject>() {
                                @Override
                                public void onSuccess(JSONObject updatedProfile) {
                                    profile = updatedProfile;
                                    showProfile();
                                    toast("Profile updated.");
                                }

                                @Override
                                public void onFailure(String message) {
                                    toast(message);
                                }
                            });
                }));
        dialog.show();
    }

    private void createAccount(String type) {
        api.execute(() -> api.createAccount(type), new ApiClient.Callback<JSONObject>() {
            @Override
            public void onSuccess(JSONObject account) {
                selectedAccountId = account.optLong("id", selectedAccountId);
                toast(type + " account request sent for admin approval.");
                loadBankingData();
            }

            @Override
            public void onFailure(String message) {
                toast(message);
            }
        });
    }

    private void addAccountRow(LinearLayout parent, JSONObject account) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(10));
        row.setBackgroundColor(Color.WHITE);
        TextView details = new TextView(this);
        details.setText(account.optString("accountType") + " Account · ending in "
                + lastFour(account.optString("accountNumber")) + "\n"
                + account.optString("status") + " · " + account.optString("currency"));
        details.setTextColor(INK);
        details.setTextSize(16);
        row.addView(details);
        if (isActive(account)) {
            Button balance = new Button(this);
            balance.setText("Check balance");
            balance.setAllCaps(false);
            balance.setTextColor(BLUE);
            balance.setOnClickListener(view -> showBalance(account.optLong("id")));
            row.addView(balance);
        } else if ("PENDING_APPROVAL".equals(account.optString("status"))) {
            TextView pending = new TextView(this);
            pending.setText("Awaiting admin approval");
            pending.setTextColor(MUTED);
            pending.setPadding(0, dp(10), 0, 0);
            row.addView(pending);
        } else {
            TextView unavailable = new TextView(this);
            unavailable.setText("Account is " + account.optString("status").toLowerCase());
            unavailable.setTextColor(MUTED);
            unavailable.setPadding(0, dp(10), 0, 0);
            row.addView(unavailable);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        parent.addView(row, params);
    }

    private void addTransactions(LinearLayout parent, int count) {
        for (int i = 0; i < count; i++) {
            JSONObject transaction = transactions.optJSONObject(i);
            if (transaction == null) {
                continue;
            }
            String type = transaction.optString("type", "TRANSACTION");
            boolean credit = type.equals("DEPOSIT") || type.endsWith("_IN");
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackgroundColor(Color.WHITE);
            TextView description = new TextView(this);
            String related = transaction.optString("relatedAccountNumber", "");
            String title = transaction.optString("description", "");
            if (title.isEmpty()) {
                title = type.replace('_', ' ');
            }
            if (!related.isEmpty()) {
                title += " · " + lastFour(related);
            }
            description.setText(title + "\n" + transaction.optString("createdAt", ""));
            description.setTextColor(INK);
            description.setTextSize(14);
            description.setMaxLines(3);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(description, descriptionParams);
            TextView amount = new TextView(this);
            amount.setText((credit ? "+" : "-") + "INR "
                    + transaction.optString("amount", "0.00"));
            amount.setTextColor(credit ? 0xFF16834A : 0xFFDC3545);
            amount.setTextSize(14);
            amount.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(amount);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(8);
            parent.addView(row, params);
            String returnScreen = "history".equals(currentScreen)
                    ? "history" : "dashboard";
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(view -> showReceipt(transaction, returnScreen));
        }
    }

    private void hideSampleHistoryRows() {
        for (int i = 1; i <= 10; i++) {
            int id = getResources().getIdentifier("transaction" + i, "id", getPackageName());
            if (id != 0) {
                View sample = screen.findViewById(id);
                if (sample != null) {
                    sample.setVisibility(View.GONE);
                }
            }
        }
    }

    private void addEmptyMessage(LinearLayout parent, String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(MUTED);
        empty.setTextSize(14);
        empty.setPadding(0, dp(12), 0, dp(12));
        parent.addView(empty);
    }

    private void inflate(int layout) {
        screenContainer.removeAllViews();
        screen = LayoutInflater.from(this).inflate(layout, screenContainer, false);
        screenContainer.addView(screen);
        updateToolbar();
    }

    private void updateToolbar() {
        boolean home = currentScreen.equals("dashboard");
        TextView navigationButton = screen.findViewById(R.id.screenNavigationButton);
        if (navigationButton != null) {
            navigationButton.setText(home ? "☰" : "‹");
            navigationButton.setContentDescription(home ? "Open navigation"
                    : currentScreen.equals("receipt") && "history".equals(receiptReturnScreen)
                            ? "Back to transaction history" : "Back to home");
            navigationButton.setOnClickListener(view -> {
                if (moneyOperationInProgress || transferInProgress) {
                    toast("Transaction is processing. Please wait.");
                } else if (home) {
                    drawerLayout.openDrawer(GravityCompat.START);
                } else if (currentScreen.equals("receipt")) {
                    returnFromReceipt();
                } else {
                    showDashboard();
                }
            });
        }
        String username = profile.optString("username", "");
        TextView drawerName = findViewById(R.id.drawerUsername);
        drawerName.setText(username.isEmpty() ? "BankEase Customer" : username);
        ((TextView) findViewById(R.id.drawerAvatar)).setText(usernameInitial());
    }

    private TextView text(int id) {
        return screen.findViewById(id);
    }

    private void hideView(int id) {
        View view = screen.findViewById(id);
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    private EditText editField(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private JSONObject findAccount(long accountId) {
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account != null && account.optLong("id") == accountId) {
                return account;
            }
        }
        return null;
    }

    private long firstActiveAccountId() {
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (isActive(account)) {
                return account.optLong("id");
            }
        }
        return -1;
    }

    private int activeAccountCount() {
        int count = 0;
        for (int i = 0; i < accounts.length(); i++) {
            if (isActive(accounts.optJSONObject(i))) {
                count++;
            }
        }
        return count;
    }

    private int visibleAccountCount() {
        int count = 0;
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.optJSONObject(i);
            if (account != null && !"CLOSED".equals(account.optString("status"))) {
                count++;
            }
        }
        return count;
    }

    private boolean isActive(JSONObject account) {
        return account != null && "ACTIVE".equals(account.optString("status"));
    }

    private String primaryAccountDescription() {
        JSONObject account = findAccount(firstActiveAccountId());
        if (account == null) {
            return "No active account";
        }
        return account.optString("accountType") + " Account · ending in "
                + lastFour(account.optString("accountNumber"));
    }

    private String initial(String name) {
        return name == null || name.trim().isEmpty()
                ? "?" : name.trim().substring(0, 1).toUpperCase();
    }

    private String usernameInitial() {
        return initial(profile.optString("username", "?"));
    }

    private String lastFour(String accountNumber) {
        return accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }

    private boolean isUnauthorized(String message) {
        String value = message.toLowerCase();
        return value.contains("401") || value.contains("session expired")
                || value.contains("please log in again");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}