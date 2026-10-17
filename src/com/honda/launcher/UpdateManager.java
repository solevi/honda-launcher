package com.honda.launcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Self-update: fetch a small JSON manifest, download a newer APK, verify its SHA-256,
 * and hand it to PackageInstaller. Android still asks the user to confirm the update once.
 */
final class UpdateManager {

    private static final String TAG = "HondaUpdate";
    private static final String APK_NAME = "update.apk";

    private final Context context;
    private final TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    UpdateManager(Context context, TextView status) {
        this.context = context.getApplicationContext();
        this.status = status;
    }

    /** Runs the whole check/download/install on a background thread. */
    void checkForUpdate() {
        final String manifestUrl = context.getString(R.string.update_manifest_url).trim();
        if (manifestUrl.isEmpty()) {
            show("Self-update disabled: no manifest URL set");
            return;
        }
        show("Checking for updates...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runCheck(manifestUrl);
                } catch (Exception e) {
                    show("Update failed: " + e.getMessage());
                }
            }
        }, "launcher-update").start();
    }

    private void runCheck(String manifestUrl) throws Exception {
        JSONObject m = new JSONObject(readText(manifestUrl));
        int remoteCode = m.getInt("versionCode");
        String remoteName = m.optString("versionName", String.valueOf(remoteCode));
        int localCode = installedVersionCode();

        if (remoteCode <= localCode) {
            show("Up to date (version " + localCode + ")");
            return;
        }

        show("Downloading " + remoteName + "...");
        File apk = new File(context.getCacheDir(), APK_NAME);
        String expected = m.getString("sha256").toLowerCase();
        downloadAndVerify(m.getString("apkUrl"), apk, expected);

        show("Installing " + remoteName + "...");
        install(apk);
    }

    private int installedVersionCode() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionCode;
    }

    private void downloadAndVerify(String apkUrl, File dest, String expectedSha256) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
            }
            String got = hex(md.digest());
            if (!got.equals(expectedSha256)) {
                dest.delete();
                throw new SecurityException("sha256 mismatch: got " + got);
            }
        } finally {
            c.disconnect();
        }
    }

    private void install(File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }
            android.app.PendingIntent done = android.app.PendingIntent.getBroadcast(
                    context, sessionId, new android.content.Intent(context, UpdateReceiver.class),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(done.getIntentSender());
        } catch (Exception e) {
            session.abandon();
            throw e;
        }
    }

    private String readText(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private void show(final String text) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                status.setText(text);
            }
        });
    }
}
