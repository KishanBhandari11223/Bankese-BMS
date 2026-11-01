# BankEase BMS

A demo banking management system built with an Android app, a Spring Boot REST API, and a MySQL database. It demonstrates account management and common banking operations; it is not connected to real financial services.

## Features

- Customer registration with administrator approval
- Customer and administrator roles
- Savings and current accounts
- Balance checks with password verification
- Deposits, withdrawals, and account-to-account transfers
- Saved friends for transfers
- Transaction history and shareable receipts
- Notifications for money-related activity

## Project structure

```text
BMS/
├── android-client/BankEase/   Android application
├── spring-server/             Spring Boot API
└── sql/
    ├── schema.sql             Fresh database schema
    └── migrations/            Updates for existing databases
```

## Technologies

- Android app: Java, Android SDK
- Backend: Java 17, Spring Boot, Spring Data JPA
- Database: MySQL
- Authentication: JWT and BCrypt password hashing

## Requirements

- MySQL Server and MySQL Workbench
- Java 17 and Maven for the backend
- Android Studio with Android SDK 37 installed
- Internet access for the first Gradle build, if dependencies need to be downloaded

## Database setup

### Fresh database

1. Open `sql/schema.sql` in MySQL Workbench.
2. Select and execute the entire script. It creates the `bms_db` database and its tables.
3. Create or use a dedicated MySQL application user with access to `bms_db`. The backend should not connect as the MySQL root user.

### Existing database

Select the `bms_db` schema in Workbench and apply any migrations that have not already been applied, in numerical order:

```text
sql/migrations/001_allow_transfer_reference_pair.sql
sql/migrations/002_customer_account_approvals.sql
sql/migrations/003_create_friends.sql
```

Do not rerun a migration that has already been applied. Do not run `schema.sql` over an existing database containing data.

## Run the backend

Open PowerShell at the project root and run:

```powershell
Set-Location 'spring-server'

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()

$env:DB_USERNAME = 'bms_app'
$dbSecure = Read-Host 'Enter the bms_app database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecure).Password

mvn spring-boot:run
```

Keep this terminal open while using the app. The generated JWT secret applies to this server session; if you generate a new one after restarting, sign in again.

Check that the API is running at:

```text
http://localhost:8080/api/v1/health
```

## Run the Android app

1. Open `android-client/BankEase` in Android Studio.
2. Wait for Gradle sync to finish.
3. Start the Spring Boot backend.
4. Run the `app` configuration on an emulator or Android device.
5. On the server address screen, enter:
   - Android emulator: `10.0.2.2:8080`
   - Physical device: `<computer-LAN-IP>:8080`

Enter the address as `host:port`, without `http://`. For a physical device, the phone and computer must be able to reach each other on the network.

## API overview

All API routes are under `/api/v1`.

| Area | Routes |
|---|---|
| Health | `GET /health` |
| Authentication | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` |
| Accounts | `GET /accounts`, `POST /accounts`, `GET /accounts/{accountId}` |
| Money operations | `POST /accounts/{accountId}/balance`, `/deposit`, `/withdraw` |
| Transfers | `POST /accounts/{accountId}/transfers` |
| Transactions | `GET /transactions?page=0&size=50` |
| Saved friends | `GET /friends`, `POST /friends`, `DELETE /friends/{friendId}` |
| Administration | Routes under `/admin/customers` |

Most routes require a bearer token obtained by signing in.

## Tests and build

Run backend tests from the server folder:

```powershell
Set-Location spring-server
mvn test
```

Build the Android debug app from the Android project folder:

```powershell
Set-Location android-client/BankEase
.\gradlew.bat assembleDebug
```

## Demo and security notes

- Registration requires administrator approval before a customer can sign in.
- Deposits and withdrawals update demo balances; there is no connection to a real bank or payment network.
- Never commit database passwords, JWT secrets, personal credentials, or local configuration files.
- Use strong, private passwords for demo accounts and do not reuse personal passwords.
