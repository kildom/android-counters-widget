# Project Guidelines

## Project
- This is a native Android app written in Kotlin. Its primary feature is a home-screen widget.
- Keep changes beginner-friendly and focused; explain Android-specific concepts when useful.
- Preserve the widget's support for home-screen placement and follow Android App Widget conventions.
- Widget shows counters that counts down to some event.
- The counters information is taken from internet over HTTP using user-provided secret URL that returns JSON.
- The widget should have optimized power consumption.
  - It never executes any code when not needed, especially if phone is locked or widget is not visible.
  - The counters information is fetched not often than once per minute and only if widget is visible.
  - If widget is not visible, it should not fetch any information from internet.
  - If widget becomes visible, it should fetch the information immediately if not fetched in the last minute.
- Widget should have as low as possible memory footprint and CPU usage.

## Build and Validation
- The Android SDK and JDK are provided by Docker; do not require them to be installed on the host.
- Build from the project root with `./gradlew assembleDebug` inside the Dev Container, or use the Docker Compose workflow in [README.md](../README.md).
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
- Over HTTP, the app can send a POST requests (commands) using the same URL and the response is the same as above.
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
  - Add button with "+" icon and "Add" label, which opens a dialog to add a new counter. It contains a Name field and a Period field. The initial target date will be set to current date + period.
  - Refresh button with "⟳" icon no label, which fetches the counters information from the user-provided URL.
- Each counter in the list should display:
  - The counter's name.
  - The percentage of time left until the target date, calculated as `(targetDate - currentDate) / period`.
    If percentage is less than 0 it should be displayed as red with minimum sign.
  - The days remaining until the target date using smaller font size and inside brackets.
  - Three dots that shows a menu with:
    - Reset button that executes an edit command with target date set to current date + period.
    - Edit button that opens a dialog to edit the counter's name and period.
    - Postpone button that show a prompt with a number of days to postpone the target date with default value of 1. If user enters a number, it executes an edit command with target date set to current "targetDate + number of days".
    - Speed up button that works the same as postpone button but with negative number of days.
    - Delete button that executes a delete command. User should be prompted to confirm the deletion.
- The list is sorted by percentage starting from the lowest percentage at the top.

## Web backend
- The web backend is a single PHP script that handles JSON commands and returns JSON responses as described above.
- It takes `uid` attribute in the URL query string to identify the database of counters (JSON file) to use.
- In the same directory as the PHP script, there is a `data` directory that contains a JSON files names as `[uid].json`.
- If `data/[uid].json` file does not exist or there is no `uid` in `GET` query string, the script should return 403 Forbidden error. Validate the `uid` to contain only alphanumeric characters. This is to prevent unauthorized access, so make sure that everything is properly validated and sanitized to avoid security issues.
- The script will edit the `data/[uid].json` file according to the commands received. Remember to use correct file locking to avoid race conditions when multiple requests are received at the same time.
- The JSON file contains an array of counters the same as in the response of the commands.
