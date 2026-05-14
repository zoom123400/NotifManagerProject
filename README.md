# Per-App Notification Manager

A minimal, highly-compatible Android app (Java) that lists all installed
applications and lets the user open the system notification settings for each
one.  Works from **Android 4.0 (API 14)** through **Android 14 (API 34)**.

---

## Project Structure

```
PerAppNotificationManager/
├── app/
│   ├── build.gradle                          ← ABI splits, deps, SDK versions
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/perappnotificationmanager/
│       │   ├── MainActivity.java             ← Core logic, async loading, permissions
│       │   ├── AppAdapter.java               ← RecyclerView Adapter + ViewHolder
│       │   └── AppInfo.java                  ← Data model
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml         ← Toolbar + SearchBar + RecyclerView
│           │   └── item_app.xml              ← Single app-row layout
│           ├── menu/
│           │   └── menu_main.xml             ← "Show/Hide System Apps" overflow item
│           ├── drawable/
│           │   ├── ic_search.xml             ← Vector search icon
│           │   ├── bg_search_field.xml       ← Rounded rect for the search EditText
│           │   ├── bg_system_badge.xml       ← Pill badge for SYSTEM apps
│           │   └── selector_item_bg.xml      ← Pressed-state selector for rows
│           └── values/
│               ├── strings.xml
│               ├── colors.xml
│               └── styles.xml                ← AppCompat.Light.NoActionBar theme
├── build.gradle
└── settings.gradle
```

---

## Features

| Feature | Details |
|---|---|
| App list | All user + system apps via `PackageManager` |
| Real-time search | Filters by app name **and** package name |
| System app toggle | "Show / Hide System Apps" in the overflow menu |
| Notification settings | Tapping a row / checkbox opens the correct system screen |
| API 26+ (Oreo) | Opens `ACTION_APP_NOTIFICATION_SETTINGS` → shows channels |
| API 21–25 | Opens `ACTION_APPLICATION_DETAILS_SETTINGS` |
| API 14–20 | Same details page (best available option) |
| API 33+ permission | `POST_NOTIFICATIONS` runtime request at startup |
| Async loading | `AsyncTask` on a background thread; UI never blocks |
| Low-RAM safe | Icons loaded on bg thread; `setHasFixedSize(true)` on RV |

---

## Architecture & Key Decisions

### Why AsyncTask (deprecated but compatible)?
`AsyncTask` is deprecated in API 30 but still functions through API 34.  For a
`minSdk 14` project it is the simplest primitive with automatic
`onPostExecute()` dispatch to the main thread.  If you prefer, replace it with:

```java
new Thread(() -> {
    List<AppInfo> result = loadAppsInBackground();
    runOnUiThread(() -> onAppsLoaded(result));
}).start();
```

### Why can't we programmatically disable notifications for other apps?
Android's permission model does not allow a third-party app to toggle another
app's notification settings programmatically — that requires
`android.permission.WRITE_SECURE_SETTINGS`, a signature-level permission
unavailable to normal apps.  The correct UX is to deep-link the user to the
system notification settings page (`ACTION_APP_NOTIFICATION_SETTINGS` on
API 26+), which is exactly what this app does.

### ABI Splits
`build.gradle` uses `splits { abi { … } }` to produce separate APKs for
`armeabi-v7a` (32-bit) and `arm64-v8a` (64-bit), plus a universal APK.
Smaller APKs → faster installs, critical for low-storage devices.

---

## Compatibility Matrix

| Android Version | API | Notification Settings Screen Used |
|---|---|---|
| 4.0 – 5.0 | 14 – 20 | App Details Settings |
| 5.0 – 7.1 | 21 – 25 | App Details Settings |
| 8.0 – 12  | 26 – 32 | `ACTION_APP_NOTIFICATION_SETTINGS` (channels) |
| 13+        | 33+     | Same + `POST_NOTIFICATIONS` runtime permission |

---

## How to Build

1. Open the project in **Android Studio Hedgehog (2023.1.1)** or later.
2. Let Gradle sync (requires internet for dependencies).
3. Run on a device or emulator with API 14+.

For a release APK with ABI splits:
```
./gradlew assembleRelease
```
Outputs land in `app/build/outputs/apk/release/`.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 | AppCompat theme + Toolbar on API 14+ |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Efficient scrolling list |
| `androidx.core:core` | 1.12.0 | `ContextCompat`, `ActivityCompat` |
| `com.google.android.material:material` | 1.11.0 | Optional Material widgets |
