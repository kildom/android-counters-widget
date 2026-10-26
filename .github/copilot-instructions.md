# Project Guidelines

## Project
- This is a native Android app written in Kotlin. Its primary feature is a home-screen widget.
- Keep changes beginner-friendly and focused; explain Android-specific concepts when useful.
- Preserve the widget's support for home-screen placement and follow Android App Widget conventions.
- The widget shows counters counting down to events.
- Counter information comes from a user-configured private HTTP or HTTPS URL returning JSON. HTTPS is recommended; HTTP is enabled for development, so do not claim that HTTP protects the secret URL.
- The widget should have optimized power consumption.
  - Android does not provide a reliable widget visibility callback. Do not add periodic background polling or fetch simply because the widget may be visible.
  - On user unlock, schedule one WorkManager refresh about 20 seconds later only if the last successful server response was at least two minutes ago. Recheck cooldown and interactive/unlocked state when the work runs; skip while locked. System scheduling can defer the work.
  - Automatic/stale refreshes are limited to once per minute, but the user's explicit Refresh action bypasses that throttle and starts a request immediately. Add/edit/delete commands are sent immediately after the user requests them.
  - Do not start or continue network requests while the display is off or the keyguard is locked. Cancel active requests and retry delays on screen-off; do not save a lock-cancellation as a server error.
- Widget should have as low as possible memory footprint and CPU usage.
- Keep widget rendering resilient to network and server errors; show a useful message while retaining the widget UI.

## Build and Validation
- The Android SDK and JDK are provided by Docker; do not require them to be installed on the host.
- Build from the project root with `./gradlew assembleDebug` inside the Dev Container, or use the Docker Compose workflow in [README.md](../README.md).
- Build the unsigned release APK with `./gradlew assembleRelease`; it is output at `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Run the build after changes that affect app code, resources, or Gradle configuration.

## Project Structure
- App code and widget resources live under `app/src/main/`.
- Keep the Docker-based development setup working when changing project configuration.

## HTTP Protocol
- The JSON file returned by the user-provided URL has the following format:
  ```json
  {
    "command": "get",
    "status": "ok",
    "counters": [
      {
        "id": "8f3a1",
        "name": "Counter 1",
        "period": 10,
        "targetDate": "2024-12-31"
      },
      {
        "id": "87b2c",
        "name": "Counter 2",
        "period": 5,
        "targetDate": "2024-11-30"
      }
    ]
  }
  ```
- In case of an error, the JSON file has the following format:
  ```json
  {
    "command": "get",
    "status": "error",
    "message": "Error message"
  }
  ```
- If app receives an error response, it should display the error message to the user.
- GET requests fetch counters. POST requests send commands to the same URL and return the same response structure.
- Treat the endpoint URL as a secret access key; do not log it or include it in error messages.
- Retry transient network failures and HTTP error responses up to five total attempts, waiting a few seconds between attempts. Do not retry valid JSON error responses from the backend or protocol/validation errors; show the final failure to the user.
- The response will have `command` field set to the command sent. The app should check if command in the response matches the command sent. If not, it should display an error message to the user.
- The commands are:
  - Edit command - edit the counter. If some fields are not provided, they should not be changed.
    ```json
    {
      "command": "edit",
      "id": "8f3a1",
      "name": "Counter 1",
      "period": 10,
      "targetDate": "2024-12-31"
    }
    ```
  - Add command - add a new counter to the list, the initial target date will be set to current date + period.
    ```json
    {
      "command": "add",
      "name": "Counter 3",
      "period": 7,
      "targetDate": "2025-01-15"
    }
    ```
    Response of add command will contain additional `id` field with the new counter's id.
  - Delete command - delete the counter.
    ```json
    {
      "command": "delete",
      "id": "8f3a1"
    }
    ```
    

## Widget Behavior
- The widget should display a toolbar at the top and a scrollable list of counters below it.
- The toolbar should have:
  - A compact Add button, which opens a dialog to add a counter with Name and Period fields. The backend sets the initial target date to current date + period.
  - A compact three-dot options button with Refresh and Setup menu items. Refresh fetches counters; Setup opens the configuration UI.
- Setup allows editing the endpoint URL. A separate widget-colors screen configures widget background, normal text, overdue percentage text, positive and negative progress bars, progress track, text on the track, text on each progress fill, and button background. Use the in-app RGB color picker and retain saved colors. All three progress-label text colors default to black. Toolbar button foreground is black or white based on contrast with the configured button background.
- Widget-triggered dialogs and configuration should appear over the home screen and close cleanly when dismissed or when navigating between widget actions.
- Each counter in the list should display:
  - The counter's name.
  - The percentage of time left until the target date, calculated as `((targetDate - currentDate) / period) * 100` and displayed with a percent sign.
    If percentage is less than 0, use the configurable overdue text color and retain the negative sign.
  - A tall horizontal progress bar under the name, sized so the overlaid day label fits within its edges. The filled portion is based on the absolute percentage clamped to 0-100%. Positive progress fills left-to-right; negative (overdue) progress fills right-to-left. Use the positive color for non-negative percentages and the negative color for overdue percentages. The day text overlays the bar, displays `remaining of period`, and aligns left for positive progress above 50% and right for negative progress below -50%; reverse those alignments at or below the 50% magnitude. Select track or fill text color to match the segment under the label.
  - The numeric percentage remains unclamped and is displayed with a percent sign.
  - Tapping anywhere on the counter row opens its action menu; do not add a separate per-row three-dot button.
  - The action menu contains:
    - Reset button that asks for confirmation, then executes an edit command with target date set to current date + period.
    - Edit button that opens a dialog to edit the counter's name and period.
    - Postpone button that show a prompt with a number of days to postpone the target date with default value of 1. If user enters a number, it executes an edit command with target date set to current "targetDate + number of days".
    - Speed up button that works the same as postpone button but with negative number of days.
    - Delete button that executes a delete command. User should be prompted to confirm the deletion.
- The list is sorted by percentage starting from the lowest percentage at the top.
- Keep toolbar dimensions compact and align icons visually within their buttons; verify changes in remote-view layouts.

## Web backend
- The web backend is a single PHP script that handles JSON commands and returns JSON responses as described above.
- It takes `uid` attribute in the URL query string to identify the database of counters (JSON file) to use.
- In the same directory as the PHP script, there is a `data` directory that contains a JSON files names as `[uid].json`.
- If `data/[uid].json` file does not exist or there is no `uid` in `GET` query string, the script should return 403 Forbidden error. Validate the `uid` to contain only alphanumeric characters. This is to prevent unauthorized access, so make sure that everything is properly validated and sanitized to avoid security issues.
- The script will edit the `data/[uid].json` file according to the commands received. Remember to use correct file locking to avoid race conditions when multiple requests are received at the same time.
- The JSON file contains an array of counters the same as in the response of the commands.
