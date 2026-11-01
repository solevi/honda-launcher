package com.honda.launcher;

import android.graphics.drawable.Drawable;

/** One launchable app: package, display label, icon. */
final class AppInfo {
    final String pkg;
    final String label;
    final Drawable icon;

    AppInfo(String pkg, String label, Drawable icon) {
        this.pkg = pkg;
        this.label = label;
        this.icon = icon;
    }
}
