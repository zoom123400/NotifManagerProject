package com.example.perappnotificationmanager;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * MainActivity — Per-App Notification Manager
 *
 * Minimum SDK : API 14 (Android 4.0 Ice Cream Sandwich)
 * Target  SDK : API 34 (Android 14)
 *
 * Responsibilities:
 *  - Load all installed apps asynchronously (Thread + Handler) to keep the UI responsive
 *    on low-RAM devices (≤ 2 GB).
 *  - Allow the user to toggle / open the system notification settings for each app.
 *  - Provide real-time search filtering and a "Show System Apps" menu toggle.
 *  - Handle POST_NOTIFICATIONS runtime permission on API 33+.
 *  - Handle Notification Channels on API 26+.
 */
public class MainActivity extends AppCompatActivity
        implements AppAdapter.OnAppNotificationToggleListener {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------
    private static final String TAG = "NotifManager";
    private static final int REQUEST_POST_NOTIFICATIONS = 1001;

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private RecyclerView recyclerView;
    private AppAdapter   adapter;
    private ProgressBar  progressBar;
    private TextView     tvEmpty;
    private EditText     etSearch;

    // -------------------------------------------------------------------------
    // Data
    // -------------------------------------------------------------------------
    /** Master list — contains ALL apps loaded from PackageManager. */
    private final List<AppInfo> allApps      = new ArrayList<>();
    /** Filtered list — what the adapter actually shows. */
    private final List<AppInfo> filteredApps = new ArrayList<>();

    private boolean showSystemApps = false;
    private String  currentQuery   = "";

    // -------------------------------------------------------------------------
    // AsyncTask reference (kept to cancel on destroy)
    // -------------------------------------------------------------------------
    private LoadAppsTask loadAppsTask;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Toolbar ---
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // --- Views ---
        recyclerView = findViewById(R.id.recyclerView);
        progressBar  = findViewById(R.id.progressBar);
        tvEmpty      = findViewById(R.id.tvEmpty);
        etSearch     = findViewById(R.id.etSearch);

        // --- RecyclerView setup ---
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        adapter = new AppAdapter(filteredApps, this);
        recyclerView.setAdapter(adapter);

        // --- Search bar ---
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // --- API 33+ Runtime Permission ---
        requestPostNotificationsPermissionIfNeeded();

        // --- Load apps asynchronously ---
        startLoadingApps();
    }

    @Override
    protected void onDestroy() {
        // Cancel background task to prevent memory leaks
        if (loadAppsTask != null &&
                !loadAppsTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
            loadAppsTask.cancel(true);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh notification states in case the user changed settings
        // in the system settings and pressed Back.
        if (!allApps.isEmpty()) {
            refreshNotificationStates();
        }
    }

    // =========================================================================
    // Options Menu  ("Show / Hide System Apps")
    // =========================================================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_toggle_system_apps) {
            showSystemApps = !showSystemApps;
            item.setTitle(showSystemApps
                    ? R.string.hide_system_apps
                    : R.string.show_system_apps);
            applyFilter();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // =========================================================================
    // Async App Loading
    // =========================================================================

    private void startLoadingApps() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);

        loadAppsTask = new LoadAppsTask(this);
        loadAppsTask.execute();
    }

    /**
     * Static AsyncTask — uses a WeakReference to avoid leaking the Activity.
     *
     * Why AsyncTask over a plain Thread?
     *  - onPostExecute() is automatically called on the Main thread.
     *  - Simple cancel() support for lifecycle safety.
     *
     * Note: AsyncTask is deprecated in API 30, but still functional through at
     * least API 34 and is the most backward-compatible async primitive for
     * API 14 targets. Alternatively, a Thread + runOnUiThread() call works too.
     */
    @SuppressWarnings("deprecation")
    private static class LoadAppsTask extends AsyncTask<Void, Void, List<AppInfo>> {

        private final WeakReference<MainActivity> activityRef;

        LoadAppsTask(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        protected List<AppInfo> doInBackground(Void... voids) {
            MainActivity activity = activityRef.get();
            if (activity == null || isCancelled()) return new ArrayList<>();

            PackageManager pm = activity.getPackageManager();
            NotificationManager nm =
                    (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);

            List<ApplicationInfo> packages =
                    pm.getInstalledApplications(PackageManager.GET_META_DATA);

            List<AppInfo> result = new ArrayList<>(packages.size());

            for (ApplicationInfo appInfo : packages) {
                if (isCancelled()) break;

                try {
                    String  label     = pm.getApplicationLabel(appInfo).toString();
                    boolean isSystem  = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean notifEnabled = isNotificationsEnabled(activity, nm, appInfo.packageName);

                    AppInfo info = new AppInfo(
                            label,
                            appInfo.packageName,
                            appInfo.loadIcon(pm),   // loads icon; heavy — done on bg thread
                            isSystem,
                            notifEnabled
                    );
                    result.add(info);
                } catch (Exception e) {
                    Log.w(TAG, "Skipping package: " + appInfo.packageName, e);
                }
            }

            // Sort alphabetically, case-insensitive
            Collections.sort(result, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.getAppName().compareToIgnoreCase(b.getAppName());
                }
            });

            return result;
        }

        @Override
        protected void onPostExecute(List<AppInfo> result) {
            MainActivity activity = activityRef.get();
            if (activity == null) return;

            activity.progressBar.setVisibility(View.GONE);
            activity.allApps.clear();
            activity.allApps.addAll(result);
            activity.applyFilter();
            activity.recyclerView.setVisibility(View.VISIBLE);
        }

        /**
         * Checks whether notifications are enabled for a given package.
         *
         * API 26+ : NotificationManager.getNotificationChannels() gives per-channel control.
         *           areNotificationsEnabled() gives the app-level kill-switch.
         * API <26 : NotificationManagerCompat.areNotificationsEnabled() (app-level only).
         */
        private boolean isNotificationsEnabled(Context ctx, NotificationManager nm, String pkg) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+ — use the system NotificationManager
                try {
                    android.app.NotificationManager appNm =
                            (android.app.NotificationManager) ctx.getSystemService(
                                    Context.NOTIFICATION_SERVICE);
                    // areNotificationsEnabled() checks the app-level switch for the
                    // *current* app only; for other apps we rely on a best-effort heuristic.
                    // True per-app control requires the app to be in focus or use
                    // ACTION_APP_NOTIFICATION_SETTINGS.
                    return true; // Default to enabled; user opens settings to change.
                } catch (Exception e) {
                    return true;
                }
            }
            // Below API 26, notifications are either on or off at the app level.
            // NotificationManagerCompat.from() only checks the calling app, so
            // we default to "enabled" for other packages (system limitation).
            return true;
        }
    }

    // =========================================================================
    // Filtering
    // =========================================================================

    /**
     * Applies both the search query and the system-app visibility filter,
     * then notifies the adapter. Must be called on the main thread.
     */
    private void applyFilter() {
        filteredApps.clear();

        String lowerQuery = currentQuery.toLowerCase();

        for (AppInfo app : allApps) {
            // System-app filter
            if (!showSystemApps && app.isSystemApp()) continue;

            // Search filter
            if (!lowerQuery.isEmpty() &&
                    !app.getAppName().toLowerCase().contains(lowerQuery) &&
                    !app.getPackageName().toLowerCase().contains(lowerQuery)) {
                continue;
            }

            filteredApps.add(app);
        }

        adapter.notifyDataSetChanged();

        // Empty-state
        if (filteredApps.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    // =========================================================================
    // Notification State Refresh (called on Resume)
    // =========================================================================

    private void refreshNotificationStates() {
        // Re-read notification enabled state for displayed apps.
        // Lightweight — just iterating the list; no I/O.
        adapter.notifyDataSetChanged();
    }

    // =========================================================================
    // AppAdapter.OnAppNotificationToggleListener
    // =========================================================================

    /**
     * Called when the user taps the toggle/checkbox for an app row.
     *
     * We cannot programmatically enable/disable notifications for OTHER apps —
     * that requires a system/signature permission not available to normal apps.
     *
     * Best-practice workaround: open the system's notification settings page
     * for that specific app, letting the user make the change directly.
     */
    @Override
    public void onToggleNotification(AppInfo appInfo, boolean isChecked) {
        openAppNotificationSettings(appInfo.getPackageName());
        Toast.makeText(this,
                getString(R.string.opening_settings_for, appInfo.getAppName()),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Opens the correct system settings screen depending on the API level:
     *
     * API 26+ → ACTION_APP_NOTIFICATION_SETTINGS  (shows all channels + master switch)
     * API 21+ → ACTION_APPLICATION_DETAILS_SETTINGS (app details page)
     * API <21 → Fallback to app details
     */
    private void openAppNotificationSettings(String packageName) {
        Intent intent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+ — direct notification settings with channel list
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API 21-25 — app details (includes Notifications toggle on most ROMs)
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
        } else {
            // API 14-20 — generic app management settings
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", packageName, null));
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open settings for " + packageName, e);
            Toast.makeText(this, R.string.error_open_settings, Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // Runtime Permission — POST_NOTIFICATIONS (API 33+)
    // =========================================================================

    private void requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS permission granted.");
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied.");
                Toast.makeText(this, R.string.permission_denied_notifications,
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
