package com.honda.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal HOME launcher: lists every app that has a launcher entry and starts it.
 * It deliberately has no whitelist; that is the point of replacing the CHG launcher.
 */
public class LauncherActivity extends Activity {

    private PackageManager pm;
    private final List<ResolveInfo> apps = new ArrayList<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        pm = getPackageManager();

        GridView grid = findViewById(R.id.grid);
        final UpdateManager updater = new UpdateManager(this, (TextView) findViewById(R.id.status));
        findViewById(R.id.btnUpdate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updater.checkForUpdate();
            }
        });

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ResolveInfo info = apps.get(position);
                Intent launch = pm.getLaunchIntentForPackage(info.activityInfo.packageName);
                if (launch != null) {
                    startActivity(launch);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadApps();
    }

    private void reloadApps() {
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        // Hide ourselves so the grid does not show a launcher-inside-launcher.
        apps.clear();
        for (ResolveInfo ri : found) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) {
                apps.add(ri);
            }
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        adapter.notifyDataSetChanged();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(LauncherActivity.this).inflate(android.R.layout.simple_list_item_1, parent, false);
            }
            ResolveInfo info = apps.get(position);
            TextView label = (TextView) v;
            label.setText(info.loadLabel(pm));
            label.setCompoundDrawablesWithIntrinsicBounds(null, info.loadIcon(pm), null, null);
            label.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            label.setTextColor(0xFFFFFFFF);
            label.setCompoundDrawablePadding(8);
            return v;
        }
    }
}
