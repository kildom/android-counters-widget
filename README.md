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

To build an unsigned release APK in the Dev Container, run:

```sh
./gradlew assembleRelease
```

The APK is created at `app/build/outputs/apk/release/app-release-unsigned.apk`.

To build without opening the Dev Container, generate the Gradle wrapper once and build either variant:

```sh
docker compose build
docker compose run --rm android gradle wrapper --gradle-version 8.11.1
docker compose run --rm android ./gradlew assembleDebug
```

For an unsigned release APK, replace the last command with:

```sh
docker compose run --rm android ./gradlew assembleRelease
```

The release APK is created at `app/build/outputs/apk/release/app-release-unsigned.apk`.

## Install on a phone

On the phone, enable **Developer options** and **Wireless debugging**. In a terminal inside the running Dev Container, pair and connect using the IP address and ports shown on the phone:

```sh
adb pair PHONE_IP:PAIRING_PORT
adb connect PHONE_IP:CONNECT_PORT
adb devices
./gradlew installDebug
```

Open **Event Counters** from the app list and enter the private HTTP or HTTPS URL for your backend. Use **Configure widget colors** to customize the widget's background, item text, overdue percentage, positive and negative progress bars, progress track, text colors on the track and both fills, and button background with the built-in RGB sliders. Progress-label text colors default to black. The widget's three-dot toolbar menu contains **Refresh** and **Setup**. Then long-press the phone's home screen, open the widget picker, and add **Event Counters**. The URL is a secret: anyone who obtains it can access that counter database. HTTPS is recommended; HTTP is enabled for development and sends the secret URL without encryption. If wireless debugging is unavailable, the APK can also be copied to the phone and opened there; Android will prompt to allow installation from that source.

Each counter's percentage remains uncapped as text. The progress bar uses the absolute percentage capped at 100%, blue for upcoming events and red for overdue events. Positive progress fills left-to-right; overdue progress fills right-to-left. Remaining days and the period appear over the bar as `remaining of period`, aligned toward the filled or unfilled segment.

Android's app-widget API does not provide a reliable callback when a widget becomes visible or hidden. It does not poll periodically in the background. After the phone is unlocked, it schedules a one-time refresh about 20 seconds later if the last successful server response was at least two minutes ago. Android may defer this work, and it is skipped if the phone is no longer awake and unlocked when it runs. Explicitly choosing **Refresh** starts a request immediately, regardless of when the previous refresh occurred; automatic/stale refreshes are limited to one per minute. Requests are not started while the display is off or the keyguard is locked, and an active request or retry wait is cancelled when the screen turns off. Add/edit/delete operations are sent immediately to the configured backend while the device is available.

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
