# Minimal Telegram

A minimal Android Telegram client built with TDLib (Telegram Database Library). Designed for personal use — function over form.

## Features

- Login with phone number
- OTP verification
- Two-step verification (2FA) support
- View chat list
- Open and read chats
- Send text messages
- Send photos, videos, and documents
- Receive new messages in real-time
- Logout / switch account

## Prerequisites

1. **Android Studio** (latest stable)
2. **Android SDK** (API 26+)
3. **Telegram API credentials** — Get your `api_id` and `api_hash` from [https://my.telegram.org/](https://my.telegram.org/)

## Setup

### Step 1: Get API Credentials

1. Go to [https://my.telegram.org/](https://my.telegram.org/)
2. Log in with your phone number
3. Go to **API development tools**
4. Create a new application
5. Note your **api_id** (integer) and **api_hash** (string)

### Step 2: Set Your API Credentials

Open `app/src/main/java/com/minimaltelegram/TdClient.kt` and replace:

```kotlin
private const val API_ID = 0        // <-- Your api_id here
private const val API_HASH = ""     // <-- Your api_hash here
```

### Step 3: Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# APK location
# app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and click **Run**.

## Project Structure

```
MinimalTelegram/
├── app/src/main/
│   ├── java/
│   │   └── com/minimaltelegram/    ← App code
│   │       ├── App.kt             ← Application init
│   │       ├── TdClient.kt        ← TDLib wrapper
│   │       ├── LoginActivity.kt   ← Phone number
│   │       ├── OtpActivity.kt     ← OTP code
│   │       ├── PasswordActivity.kt← 2FA password
│   │       ├── ChatListActivity.kt← Chat list
│   │       └── ChatActivity.kt    ← Chat room
│   └── res/layouts + values
├── app/libs/
│   └── tdlib.aar                  ← Prebuilt TDLib AAR
├── build.gradle.kts
└── settings.gradle.kts
```

## Tech Stack

| Component | Version |
|:---|:---|
| Kotlin | 2.0.0 |
| AGP | 8.5.2 |
| Gradle | 8.7 |
| compileSdk | 34 |
| minSdk | 26 |
| TDLib | Prebuilt AAR (v0.1.0) |

**Dependencies:**
- `androidx.core:core-ktx:1.13.1`
- `androidx.appcompat:appcompat:1.7.0`
- `androidx.recyclerview:recyclerview:1.3.2`
- `tdlib.aar` (local library)

## Notes

- This is a personal-use client. Do not distribute widely.

## License

MIT — see [LICENSE](LICENSE)
