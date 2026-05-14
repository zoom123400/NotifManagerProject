package com.example.perappnotificationmanager;

import android.graphics.drawable.Drawable;

/**
 * AppInfo — lightweight data model representing one installed application.
 *
 * Kept simple (no Parcelable) to avoid overhead on low-RAM devices.
 * The Drawable icon is held here after being loaded on the background thread
 * so the RecyclerView can set it synchronously on bind without additional I/O.
 */
public class AppInfo {

    private final String   appName;
    private final String   packageName;
    private final Drawable icon;
    private final boolean  isSystemApp;

    /**
     * Notification enabled state.
     *
     * NOTE: This reflects the state AT THE TIME the list was loaded.
     * Because we cannot read another app's exact notification state without
     * a system/signature permission, this defaults to {@code true}.
     * The true source-of-truth is the system Settings page we direct the
     * user to when they tap a row.
     */
    private boolean notificationsEnabled;

    public AppInfo(String appName,
                   String packageName,
                   Drawable icon,
                   boolean isSystemApp,
                   boolean notificationsEnabled) {
        this.appName              = appName;
        this.packageName          = packageName;
        this.icon                 = icon;
        this.isSystemApp          = isSystemApp;
        this.notificationsEnabled = notificationsEnabled;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getAppName()           { return appName; }
    public String getPackageName()       { return packageName; }
    public Drawable getIcon()            { return icon; }
    public boolean isSystemApp()         { return isSystemApp; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    // -------------------------------------------------------------------------
    // Setter — toggled by adapter callback
    // -------------------------------------------------------------------------

    public void setNotificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
    }
}
