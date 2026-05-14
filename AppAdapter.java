package com.example.perappnotificationmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * AppAdapter — RecyclerView Adapter for the Per-App Notification Manager.
 *
 * Design goals:
 *  - ViewHolder pattern (mandatory for RecyclerView) minimises findViewById calls,
 *    which is important on old Dalvik VMs (API 14–20).
 *  - The CheckBox listener is set to null BEFORE updating its checked state so we
 *    never trigger a false toggle event during bind (a classic RecyclerView gotcha).
 *  - Icon Drawables are already loaded by the AsyncTask; no additional I/O here.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    // -------------------------------------------------------------------------
    // Callback interface
    // -------------------------------------------------------------------------

    /**
     * Called when the user taps the notification toggle for a specific app.
     *
     * @param appInfo   The app whose toggle was tapped.
     * @param isChecked The NEW state of the checkbox after the tap.
     */
    public interface OnAppNotificationToggleListener {
        void onToggleNotification(AppInfo appInfo, boolean isChecked);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final List<AppInfo>                  appList;
    private final OnAppNotificationToggleListener listener;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public AppAdapter(@NonNull List<AppInfo> appList,
                      @NonNull OnAppNotificationToggleListener listener) {
        this.appList  = appList;
        this.listener = listener;
    }

    // =========================================================================
    // RecyclerView.Adapter overrides
    // =========================================================================

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo appInfo = appList.get(position);

        // --- App icon ---
        if (appInfo.getIcon() != null) {
            holder.ivAppIcon.setImageDrawable(appInfo.getIcon());
        } else {
            holder.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon);
        }

        // --- App name ---
        holder.tvAppName.setText(appInfo.getAppName());

        // --- Package name (subtitle) ---
        holder.tvPackageName.setText(appInfo.getPackageName());

        // --- System app badge ---
        holder.tvSystemBadge.setVisibility(
                appInfo.isSystemApp() ? View.VISIBLE : View.GONE);

        // --- CheckBox —
        // IMPORTANT: Remove listener first to avoid triggering onCheckedChanged
        // with a stale value when RecyclerView recycles a ViewHolder.
        holder.cbNotifications.setOnCheckedChangeListener(null);
        holder.cbNotifications.setChecked(appInfo.isNotificationsEnabled());

        // Re-attach the listener after setting the checked state.
        holder.cbNotifications.setOnCheckedChangeListener(
                new CheckBox.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        // Update the data model
                        appInfo.setNotificationsEnabled(isChecked);
                        // Notify the activity — it will open the system settings
                        listener.onToggleNotification(appInfo, isChecked);
                    }
                });

        // Make the entire row clickable (acts as a secondary tap target for the checkbox)
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Toggle the checkbox programmatically to fire the listener above
                holder.cbNotifications.toggle();
            }
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // =========================================================================
    // ViewHolder
    // =========================================================================

    /**
     * AppViewHolder — caches view references to avoid repeated calls to
     * {@code findViewById()} during scroll, which is expensive on old hardware.
     */
    static class AppViewHolder extends RecyclerView.ViewHolder {

        final ImageView ivAppIcon;
        final TextView  tvAppName;
        final TextView  tvPackageName;
        final TextView  tvSystemBadge;
        final CheckBox  cbNotifications;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon        = itemView.findViewById(R.id.ivAppIcon);
            tvAppName        = itemView.findViewById(R.id.tvAppName);
            tvPackageName    = itemView.findViewById(R.id.tvPackageName);
            tvSystemBadge    = itemView.findViewById(R.id.tvSystemBadge);
            cbNotifications  = itemView.findViewById(R.id.cbNotifications);
        }
    }
}
