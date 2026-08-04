# FastShare

A fully offline, privacy-focused local file sharing app for Android, built as an open-source alternative to LocalSend.

## Features

- **Device Discovery**: mDNS / DNS-SD + UDP multicast for instant peer discovery without internet
- **File Transfer**: Multi-file, multi-stream, resumable transfers up to 100GB+
- **End-to-end Encryption**: TLS 1.2/1.3 with pinned self-signed certificates; additional ECDH + AES-256-GCM session keys
- **Clipboard & Text sharing**: Quick-share text snippets and clipboard contents
- **QR Code pairing**: Direct connect via QR code deep link (`fastshare://connect`)
- **Manual IP connection**: Direct connect when discovery fails
- **Transfer History**: Room database records all transfers with checksums
- **Foreground Service**: Background transfers with progress notifications
- **Material 3 UI**: Jetpack Compose, dynamic color, light/dark themes
- **No internet, no cloud, no accounts**

## Tech Stack

| Layer            | Technology |
|-----------------|------------|
| Language         | Kotlin 2.0 |
| UI              | Jetpack Compose, Material 3 |
| Architecture     | Clean Architecture, MVVM + Repository |
| DI              | Hilt |
| Database         | Room |
| Async            | Coroutines, Flow, StateFlow |
| Networking       | OkHttp (client), Ktor CIO (server), mDNS / UDP multicast |
| Security         | BouncyCastle TLS, ECDH, AES-256-GCM |
| Storage          | SAF + Scoped Storage |
| QR               | ZXing |

## Project Structure

```
app/src/main/java/com/fastshare/app/
├── core/util/                # Formatters, Result wrapper, network utils
├── data/
│   ├── local/                # Room entities, DAOs, database, DataStore settings
│   ├── network/
│   │   ├── discovery/        # NsdDiscoveryEngine, MulticastDiscoveryEngine
│   │   └── protocol/        # FastShare wire protocol definitions
│   └── repository/           # TransferRepository
├── domain/
│   └── model/                # DeviceInfo, TransferItem, Settings, History models
├── di/                       # Hilt modules
├── services/
│   ├── discovery/            # DiscoveryCoordinator
│   ├── security/             # CryptoEngine, CertificateProvider, TlsFactory, PairingManager
│   └── transfer/             # TransferEngine, TransferHttpClient, InboundTransferServer, TransferStorage
└── presentation/
    ├── theme/                # Material 3 color, type, shapes, theme
    ├── components/            # DeviceCard, TransferProgressCard, etc.
    ├── navigation/            # NavHost, destinations, bottom nav
    ├── screens/             # Home, Send, Receive, Transfers, History, Settings, QR
    └── viewmodel/           # Discovery, Transfer, History, Settings ViewModels
```

## Build Instructions

### Prerequisites

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK with `compileSdk = 35`
- Kotlin 2.0.21
- KSP plugin (configured via version catalog)

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires keystore.properties at project root)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

### Keystore (release builds)

Create a `keystore.properties` at the project root:

```properties
storeFile=/path/to/your.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

Then:
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### APK Generation

```bash
# Debug APK (no signing needed)
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK
./gradlew :app:assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk

# App Bundle (for Play Store)
./gradlew :app:bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## Protocol

See `data/network/protocol/Protocol.kt` for the full wire protocol definition.

```
1. POST /v1/hello            -> identity + ECDH key exchange
2. POST /v1/transfer/request  -> files manifest, receiver approves
3. POST /v1/transfer/data    -> raw byte stream per item (resumable)
4. POST /v1/transfer/verify   -> SHA-256 checksum confirmation
5. WS   /v1/events           -> realtime progress + control
```

## License

MIT License. See LICENSE file.
