# Event Counters Android Widget

A native Kotlin app with a resizable home-screen widget that displays countdown counters from a private HTTP or HTTPS endpoint. The Android SDK and build tools run in Docker; no host-side Android Studio or Java installation is needed.

## Requirements

- Docker with the Compose plugin
- VS Code with the **Dev Containers** extension (optional, but recommended)
- An Android phone for testing; a Docker-hosted emulator is intentionally not included

## Open and build

1. In VS Code, open this folder and choose **Reopen in Container** when prompted. The first build downloads the Android SDK and may take a while.
2. In the VS Code container terminal, run:

   ```sh
   ./gradlew assembleDebug
   ```

   The APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

To build without opening the Dev Container, generate the Gradle wrapper once and build:

```sh
docker compose build
docker compose run --rm android gradle wrapper --gradle-version 8.11.1
docker compose run --rm android ./gradlew assembleDebug
```

## Install on a phone

On the phone, enable **Developer options** and **Wireless debugging**. In a terminal inside the running Dev Container, pair and connect using the IP address and ports shown on the phone:

```sh
adb pair PHONE_IP:PAIRING_PORT
adb connect PHONE_IP:CONNECT_PORT
adb devices
./gradlew installDebug
```

Open **Event Counters** from the app list and enter the private HTTP or HTTPS URL for your backend. Tap each color sample to choose widget background, normal text, and overdue text colors with the built-in RGB sliders. The widget's three-dot toolbar menu contains **Refresh** and **Setup**. Then long-press the phone's home screen, open the widget picker, and add **Event Counters**. The URL is a secret: anyone who obtains it can access that counter database. HTTPS is recommended; HTTP is enabled for development and sends the secret URL without encryption. If wireless debugging is unavailable, the APK can also be copied to the phone and opened there; Android will prompt to allow installation from that source.

Android's app-widget API does not provide a reliable callback when a widget becomes visible or hidden. To avoid network requests while the widget may be hidden, it does not poll in the background: use the refresh button to load counters, and refresh attempts are limited to one per minute. Add/edit/delete operations are sent immediately to the configured backend.

Transient network failures and HTTP error responses are retried up to five times, with a three-second delay between attempts. Backend JSON errors and invalid responses are shown without retrying.

## Project layout

- `app/src/main/java/.../CounterWidgetProvider.kt` updates widget instances and handles explicit refresh requests.
- `app/src/main/java/.../CounterRepository.kt` performs throttled network requests and caches counters locally.
- `app/src/main/res/layout/counter_widget.xml` defines the widget toolbar and scrollable list.
- `web/backend/backend.php` implements the JSON API and stores each UID's counters in `web/backend/data/`.
- `Dockerfile` and `docker-compose.yml` provide the containerized build environment.

## Web backend

The PHP command-line runtime is installed in the development container. Start the
backend from the `web/backend` directory with:

```sh
php -S 0.0.0.0:8000
```

For local development, the API is available at `http://localhost:8000/backend.php?uid=YOUR_UID`.
The Android app accepts HTTP and HTTPS endpoints; use HTTPS outside development
because the URL contains a private key. Each alphanumeric `uid` maps to
`web/backend/data/YOUR_UID.json`, which must contain a JSON array of counters.
Run the HTTP integration test with:

```sh
php web/backend/tests/backend_test.php
```
