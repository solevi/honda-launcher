package com.honda.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Home launcher for the Honda head unit.
 *
 * Shows every launchable app (no whitelist), a pinned-favorites row, a clock and the
 * CHG activation status, plus self-update. Long-press an app to pin/unpin it.
 */
public class LauncherActivity extends Activity {

    private static final String PREFS = "launcher";
    private static final String KEY_FAVS = "fav_order";
    private static final Uri ACTIVATION_URI =
            Uri.parse("content://com.chg.settings.appcontrol/activation");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<AppInfo> apps = new ArrayList<>();       // all launchable, sorted
    private final Map<String, AppInfo> byPkg = new LinkedHashMap<>();

    private PackageManager pm;
    private GridView grid;
    private AppAdapter adapter;
    private TextView clock, status, favHeader;
    private View favScroll;
    private LinearLayout favRow;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            ui.postDelayed(this, 15000);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadAppsAsync();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        pm = getPackageManager();

        grid = (GridView) findViewById(R.id.grid);
        clock = (TextView) findViewById(R.id.clock);
        status = (TextView) findViewById(R.id.status);
        favHeader = (TextView) findViewById(R.id.favHeader);
        favScroll = findViewById(R.id.favScroll);
        favRow = (LinearLayout) findViewById(R.id.favRow);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                launch(apps.get(pos).pkg);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                toggleFavorite(apps.get(pos).pkg);
                return true;
            }
        });

        final UpdateManager updater = new UpdateManager(this, status);
        Button btn = (Button) findViewById(R.id.btnUpdate);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { updater.checkForUpdate(); }
        });

        loadAppsAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableNavBar();
        updateClock();
        updateStatus();
        buildFavorites();
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 15000);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        try { registerReceiver(packageReceiver, f); } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
    }

    // --- app loading -------------------------------------------------------

    private void loadAppsAsync() {
        new Thread(new Runnable() {
            @Override public void run() {
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
                final List<AppInfo> loaded = new ArrayList<>();
                for (ResolveInfo ri : found) {
                    String p = ri.activityInfo.packageName;
                    if (p.equals(getPackageName())) continue;       // hide ourselves
                    loaded.add(new AppInfo(p, ri.loadLabel(pm).toString(), ri.loadIcon(pm)));
                }
                Collections.sort(loaded, new Comparator<AppInfo>() {
                    @Override public int compare(AppInfo a, AppInfo b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
                ui.post(new Runnable() {
                    @Override public void run() {
                        apps.clear(); byPkg.clear();
                        for (AppInfo a : loaded) { apps.add(a); byPkg.put(a.pkg, a); }
                        adapter.notifyDataSetChanged();
                        buildFavorites();
                    }
                });
            }
        }, "load-apps").start();
    }

    // --- favorites ---------------------------------------------------------

    private List<String> getFavorites() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = sp.getString(KEY_FAVS, "");
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void saveFavorites(List<String> favs) {
        StringBuilder sb = new StringBuilder();
        for (String s : favs) { if (sb.length() > 0) sb.append('\n'); sb.append(s); }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_FAVS, sb.toString()).apply();
    }

    private void toggleFavorite(String pkg) {
        List<String> favs = getFavorites();
        AppInfo info = byPkg.get(pkg);
        String label = info != null ? info.label : pkg;
        if (favs.remove(pkg)) {
            Toast.makeText(this, label + " unpinned", Toast.LENGTH_SHORT).show();
        } else {
            favs.add(pkg);
            Toast.makeText(this, label + " pinned", Toast.LENGTH_SHORT).show();
        }
        saveFavorites(favs);
        buildFavorites();
    }

    private void buildFavorites() {
        if (favRow == null) return;
        favRow.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        int shown = 0;
        for (final String pkg : getFavorites()) {
            final AppInfo info = byPkg.get(pkg);
            if (info == null) continue;                 // not installed / not loaded yet
            View cell = inf.inflate(R.layout.app_cell, favRow, false);
            ((ImageView) cell.findViewById(R.id.icon)).setImageDrawable(info.icon);
            ((TextView) cell.findViewById(R.id.label)).setText(info.label);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { launch(pkg); }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) { toggleFavorite(pkg); return true; }
            });
            favRow.addView(cell);
            shown++;
        }
        int vis = shown > 0 ? View.VISIBLE : View.GONE;
        favHeader.setVisibility(vis);
        favScroll.setVisibility(vis);
    }

    // --- status / clock ----------------------------------------------------

    private void updateClock() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
    }

    private void updateStatus() {
        String text = "";
        try {
            Cursor c = getContentResolver().query(ACTIVATION_URI, null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex("activated");
                        boolean on = idx >= 0 && c.getInt(idx) == 1;
                        text = on ? "● Activated" : "○ Not activated";
                    }
                } finally { c.close(); }
            }
        } catch (Exception ignored) {
            // provider absent (e.g. emulator) — leave status blank
        }
        status.setText(text);
    }

    // --- system nav bar ----------------------------------------------------

    // SystemUI2 keeps the car nav bar disabled until the launcher enables it.
    private void enableNavBar() {
        sendBroadcast(new Intent("com.android.systemui.action.MX_ACTION_SYSTEMUI_VIEW_CONTROL_EVENT")
                .putExtra("mx_systemui_view_component_type", 1)
                .putExtra("mx_systemui_view_component_visibility", 0));
        sendBroadcast(new Intent("com.android.systemui.action.MX_NAVIBAR_VIEW_CONTROL")
                .putExtra("mx_navibar_view_control_display_value", true));
        sendBroadcast(new Intent("android.intent.aciton.MX_NAVIBAR_ENABLE")
                .putExtra("bitFlag", 0xFFFF));
    }

    // --- launching ---------------------------------------------------------

    private void launch(String pkg) {
        Intent launch = pm.getLaunchIntentForPackage(pkg);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(launch); return; } catch (Exception ignored) {}
        }
        Toast.makeText(this, "Can't open " + pkg, Toast.LENGTH_SHORT).show();
    }

    // --- grid adapter ------------------------------------------------------

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int pos) { return apps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(LauncherActivity.this)
                        .inflate(R.layout.app_cell, parent, false);
            }
            AppInfo info = apps.get(pos);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(info.icon);
            ((TextView) v.findViewById(R.id.label)).setText(info.label);
            return v;
        }
    }
}
