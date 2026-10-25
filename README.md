# Hello World Android Widget

A minimal native Kotlin app that adds a resizable home-screen widget displaying **Hello World**. The Android SDK and build tools run in Docker; no host-side Android Studio or Java installation is needed.

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

Then long-press the phone's home screen, open the widget picker, and add **Hello World Widget**. If wireless debugging is unavailable, the APK can also be copied to the phone and opened there; Android will prompt to allow installation from that source.

## Project layout

- `app/src/main/java/.../HelloWorldWidgetProvider.kt` updates widget instances.
- `app/src/main/res/layout/hello_world_widget.xml` defines the widget view.
- `app/src/main/res/xml/hello_world_widget_info.xml` declares its size and home-screen behavior.
- `Dockerfile` and `docker-compose.yml` provide the containerized build environment.

## Web backend

The PHP command-line runtime is installed in the development container. Start the
backend from the `web/backend` directory with:

```sh
php -S 0.0.0.0:8000
```

The API is available at `http://localhost:8000/backend.php?uid=YOUR_UID`. Each
alphanumeric `uid` maps to `web/backend/data/YOUR_UID.json`, which must contain
a JSON array of counters. Run the HTTP integration test with:

```sh
php web/backend/tests/backend_test.php
```
