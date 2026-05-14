# proguard-rules.pro
# Per-App Notification Manager

# --------------------------------------------------------------------------
# AppCompat / AndroidX
# --------------------------------------------------------------------------
-keep class androidx.appcompat.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.recyclerview.** { *; }

# --------------------------------------------------------------------------
# App data model — must not be obfuscated so reflection still works
# --------------------------------------------------------------------------
-keep class com.example.perappnotificationmanager.AppInfo { *; }

# --------------------------------------------------------------------------
# Keep all Activity / Fragment / Adapter classes
# --------------------------------------------------------------------------
-keep class com.example.perappnotificationmanager.** { *; }

# --------------------------------------------------------------------------
# Suppress warnings for legacy APIs we intentionally use
# --------------------------------------------------------------------------
-dontwarn android.os.AsyncTask
