package com.honda.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

/** Receives the installer's result for a self-update session. */
public class UpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "HondaUpdate";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // Android needs the user to confirm the update on screen, once.
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "update installed");
                break;
            default:
                Log.e(TAG, "update failed: " + status + " " + message);
                Toast.makeText(context, "Update failed: " + message, Toast.LENGTH_LONG).show();
        }
    }
}
